"""Join real rendered frames with native frame tokens; never add overlapping clock domains."""
import csv
import json
from collections import defaultdict
from pathlib import Path
import shutil


def distribution(values):
    values = sorted(values)
    if not values:
        return {'samples': 0}
    return {'samples': len(values), 'mean_ms': sum(values) / len(values) / 1e6,
            **{f'p{q}_ms': values[min(len(values)-1, int((len(values)-1)*q/100))]/1e6
               for q in (50, 95, 99)}, 'max_ms': values[-1]/1e6}


def shutdown_evidence(events, logs, failed_frames, native_trace=''):
    stop_at = logs.rfind('Stopping!')
    # Native stdout need not enter latest.log. A completed device-close trace is
    # independent evidence from this fresh, single-profile diagnostic instance.
    normal_close = (native_trace.startswith('BEGIN CPU_ONLY cause=normal-device-close result=0 ')
                    and native_trace.rstrip().endswith('\nEND')
                    and ' normal-device-close result=0 ' in native_trace.splitlines()[-2])
    result = {
        'runtime_errors_in_java_log': any(word in logs for word in
            ('VK_ERROR_DEVICE_LOST', 'Rendering failure recorded', 'Reported exception thrown')),
        'normal_stop': any(row['phase'] == 'normal-stop' for row in events) and stop_at >= 0,
        'all_dimensions_saved': 'All dimensions are saved' in logs,
        'normal_scene_release_logged': stop_at >= 0 and 'Scene released;' in logs[stop_at:],
        'normal_native_close_traced': normal_close}
    result['completed_run'] = (result['normal_stop'] and result['all_dimensions_saved']
                               and (result['normal_scene_release_logged'] or normal_close)
                               and not result['runtime_errors_in_java_log'] and not failed_frames)
    return result


def measurement_coverage(events, frames):
    """A flushed CSV can still omit a frame that outlived the capture window."""
    intervals = {int(row['frame']): int(row['start_interval_ns']) for row in frames}
    gaps = []
    for before, after in zip(events, events[1:]):
        low, high = before['frame'], after['frame']
        if high <= low:
            continue
        selected = [value for frame, value in intervals.items() if low < frame <= high]
        elapsed = after['elapsed_ns'] - before['elapsed_ns']
        measured = sum(max(0, value) for value in selected)
        missing = high - low - len(selected)
        # A short unfinished tail is normal at stopCapture. A missing interval
        # exceeding one second must not silently disappear from performance data.
        if missing > 0 and elapsed - measured > 1_000_000_000:
            gaps.append({'after_frame': low, 'through_frame': high,
                         'event_elapsed_ns': elapsed, 'recorded_interval_ns': measured,
                         'unmeasured_ns': elapsed - measured, 'missing_frames': missing})
    return {'complete_for_elapsed_window': bool(events) and bool(frames) and not gaps,
            'event_elapsed_ns': events[-1]['elapsed_ns'] - events[0]['elapsed_ns'] if events else 0,
            'recorded_interval_ns': sum(max(0, value) for value in intervals.values()),
            'large_unmeasured_gaps': gaps}


def analyze(repository, partial=False):
    validation = repository / 'validations/performance-chunk_loading'
    instance = validation / 'instance/1.21.1-neoforge/client'
    result = validation / 'result'
    events = [json.loads(row) for row in (result / 'chunk-loading.jsonl').read_text(encoding='utf-8').splitlines()]
    rebuild_request = next((row for row in events if row['phase'] == 'rebuild-request'), None)
    rebuild = rebuild_request['frame'] if rebuild_request else 2**63
    profiles = sorted((instance / 'radiance-audit/profiles').glob('*'))
    if len(profiles) != 1:
        raise ValueError('A fresh instance with exactly one profile is required')
    profile = profiles[0]
    status = (profile / 'STATUS.txt').read_text(encoding='utf-8')
    metadata = (profile / 'METADATA.txt').read_text(encoding='utf-8')
    if ('native-profile=true' not in metadata or 'finished=true' not in status
            or any(line.split('=')[1] != '0' for line in status.splitlines()
                   if line.startswith(('dropped-', 'output-cap-dropped-')))):
        raise ValueError('Incomplete profile or missing native observer; preserve raw evidence')
    all_frames = list(csv.DictReader((profile / 'frames.csv').open(encoding='utf-8')))
    frames = [row for row in all_frames if row['failed'] == 'false']
    timings = list(csv.DictReader((profile / 'timings.csv').open(encoding='utf-8')))
    by_frame = defaultdict(list)
    stages = defaultdict(list)
    for row in timings:
        owner = int(row['frame'])
        phase = 'unowned-worker' if not owner else ('initial' if owner <= rebuild else 'rebuild')
        stages[(phase, row['domain'], row['stage'])].append(row)
        if row['domain'] == '0':
            by_frame[owner].append(row)
    output = {'profile': str(profile), 'rebuild_after_frame': rebuild if rebuild_request else None,
              'rebuild_requested': rebuild_request is not None,
              'status': status, 'failed_frames': [row for row in all_frames if row['failed'] != 'false'],
              'configuration_differences':
              ['Target uses Java 21 / NeoForge 21.1.259; source Prism Java 25.0.4 / NeoForge 21.1.252',
               'Zume is not loaded; source fixed camera/graphics retained, muted, no focus/input'],
              'phases': {}, 'stages': [], 'longest_frames': [],
              'probe_sample': distribution([row['sample_ns'] for row in events]),
              'events': events,
              'limits': ['Worker times overlap frames; no main-thread percentage assigned',
                         'GPU query intervals exclude separate chunk queue and SDK generated frames',
                         'Batch queue GPU wall includes queueing and CPU completion polling',
                         'No previous-product A/B; these are current-product loading measurements']}
    for phase in ('initial', 'rebuild'):
        selected = [row for row in frames if (int(row['frame']) <= rebuild) == (phase == 'initial')]
        output['phases'][phase] = {key: distribution([int(row[key]) for row in selected if int(row[key]) >= 0])
                                   for key in ('wall_ns', 'start_interval_ns', 'render_thread_cpu_ns')}
    quiet = []
    for event in events:
        state = event['state']
        native = dict(item.strip().split('=') for item in state['chunk.native'].split(';'))
        empty = all(state[f'chunk.java.{name}'] == '0' for name in
                    ('columnQueue', 'rebuildQueue', 'importantBuildsInFlight', 'normalBuildsInFlight'))
        empty &= all(native[key] == '0' for key in
                     ('build_input_count', 'queued_builds', 'pending_batch_builds', 'active_gpu_batches'))
        quiet.append(empty)
    boundaries = {}
    for label, low, high in (('initial', 0, rebuild), ('rebuild', rebuild + 1, 2**63)):
        for i in range(3, len(events)):
            if all(quiet[i-3:i+1]) and all(low <= event['frame'] <= high for event in events[i-3:i+1]):
                boundaries[label] = events[i]
                break
    output['sustained_quiet'] = boundaries
    output['windows'] = {}
    for label, event in boundaries.items():
        for state in ('loading', 'quiet'):
            selected = [row for row in frames
                        if (int(row['frame']) <= rebuild) == (label == 'initial')
                        and (int(row['frame']) <= event['frame']) == (state == 'loading')]
            output['windows'][label + '-' + state] = {
                key: distribution([int(row[key]) for row in selected if int(row[key]) >= 0])
                for key in ('wall_ns', 'start_interval_ns', 'render_thread_cpu_ns')}
    for (phase, domain, name), rows in stages.items():
        output['stages'].append({'phase': phase, 'domain': domain, 'stage': name,
                                'inclusive': distribution([int(row['inclusive_ns']) for row in rows]),
                                'self_total_ms': sum(int(row['self_ns']) for row in rows)/1e6})
    for frame in sorted(frames, key=lambda row: int(row['wall_ns']), reverse=True)[:20]:
        own = sorted(by_frame[int(frame['frame'])], key=lambda row: int(row['self_ns']), reverse=True)
        output['longest_frames'].append({'frame': frame, 'native_self': own[:8]})
    logs = (instance / 'logs/latest.log').read_text(encoding='utf-8', errors='replace')
    trace = instance / 'radiance-device-loss-trace.log'
    native_trace = trace.read_text(encoding='utf-8', errors='replace') if trace.is_file() else ''
    output.update(shutdown_evidence(events, logs, output['failed_frames'], native_trace))
    output['measurement_coverage'] = measurement_coverage(events, frames)
    output['performance_measurement_complete'] = output['measurement_coverage']['complete_for_elapsed_window']
    if not output['performance_measurement_complete']:
        output['limits'].append('Missing long frame intervals: distributions cover recorded rows only, not the full elapsed run')
    shutil.copytree(profile, result / 'profile', dirs_exist_ok=True)
    shutil.copy2(instance / 'logs/latest.log', result / 'latest.log')
    (result / 'analysis.json').write_text(json.dumps(output, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'phases': output['phases'], 'longest_ms': int(output['longest_frames'][0]['frame']['wall_ns']) / 1e6,
                      'completed_run': output['completed_run'],
                      'performance_measurement_complete': output['performance_measurement_complete'],
                      'result': str(result / 'analysis.json')}, indent=2))
    if (not output['completed_run'] or not output['performance_measurement_complete']) and not partial:
        raise ValueError('Failed/incomplete run or frame coverage: raw evidence preserved; no validation pass')


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('--partial', action='store_true', help='Analyze pre-failure data without accepting the run')
    analyze(Path(__file__).resolve().parents[3], parser.parse_args().partial)
