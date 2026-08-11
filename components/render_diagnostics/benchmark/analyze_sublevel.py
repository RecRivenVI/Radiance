"""Per-window 129 CPU/GPU evidence; overlapping domains are never subtracted or summed."""
from pathlib import Path
import argparse
import csv
import datetime
import json
import math
import subprocess

from jfr_cpu import summarize


def percentile(values, fraction):
    return sorted(values)[max(0, math.ceil(len(values) * fraction) - 1)] if values else None


def distribution(values):
    return dict(samples=len(values), mean=sum(values) / len(values) if values else None,
                p50=percentile(values, .5), p95=percentile(values, .95), p99=percentile(values, .99),
                maximum=max(values, default=None))


def analyze(case, jfr_tool):
    windows = []
    for profile in sorted((case / 'profiles').iterdir()):
        metadata = dict(line.split('=', 1) for line in (profile / 'METADATA.txt').read_text().splitlines() if '=' in line)
        status = dict(line.split('=', 1) for line in (profile / 'STATUS.txt').read_text().splitlines() if '=' in line)
        if status.get('valid-cpu-sample') != 'true':
            raise RuntimeError('Incomplete/invalid profiler window: ' + str(profile))
        with (profile / 'frames.csv').open(encoding='utf-8-sig', newline='') as file:
            frames = list(csv.DictReader(file))
        with (profile / 'summary.csv').open(encoding='utf-8-sig', newline='') as file:
            summary = list(csv.DictReader(file))
        n = len(frames)
        intervals = [int(r['start_interval_ns']) / 1e6 for r in frames if int(r['start_interval_ns']) >= 0]
        wall = [int(r['wall_ns']) / 1e6 for r in frames]
        cpu = [int(r['render_thread_cpu_ns']) / 1e6 for r in frames if int(r['render_thread_cpu_ns']) >= 0]
        def stages(domain):
            rows = [dict(stage=r['stage'], totalMs=float(r['total_ms']),
                         msPerRecordedFrame=float(r['total_ms']) / n, calls=int(r['samples']),
                         invocationP95Ms=float(r['p95_ms']))
                    for r in summary if r['domain'] == domain and r['measure'] == 'self']
            return sorted(rows, key=lambda r: -r['msPerRecordedFrame'])
        start = datetime.datetime.fromisoformat(metadata['time'].replace('Z', '+00:00'))
        end = start + datetime.timedelta(milliseconds=sum(intervals) + wall[-1])
        window = dict(profile=str(profile), scene=metadata['scene'], mode=metadata['profile-mode'],
                      frames=n, status=status, firstTime=start.isoformat(), endTime=end.isoformat(),
                      intervalMs=distribution(intervals), frameWallMs=distribution(wall),
                      renderThreadCpuMeanMs=sum(cpu) / len(cpu),
                      over50ms=sum(v > 50 for v in intervals), over100ms=sum(v > 100 for v in intervals),
                      viewports=sorted(set((r['width'], r['height']) for r in frames)),
                      generatedFps=sorted(set(r['sdk_generated_fps'] for r in frames)),
                      javaSelf=stages('java'), nativeRenderSelf=stages('0'), nativeWorkerSelf=stages('1'),
                      gpu=[r for r in summary if r['domain'] == '2' and r['measure'] == 'self'],
                      cameraStart={k:frames[0][k] for k in ('camera_x', 'camera_y', 'camera_z', 'camera_yaw', 'camera_pitch')},
                      cameraEnd={k:frames[-1][k] for k in ('camera_x', 'camera_y', 'camera_z', 'camera_yaw', 'camera_pitch')})
        windows.append(window)
    if jfr_tool:
        # JFR can retain the startup name "main" for Minecraft's render thread. Identify it
        # by sampled call stacks before expanding shared metadata, avoiding huge JSON exports.
        exporter = Path(__file__).resolve().parents[1] / 'src/benchmark/java/com/radiance/audit/tools/RenderJfrExport.java'
        java = jfr_tool.with_name('java' + jfr_tool.suffix)
        exported = subprocess.check_output([str(java), str(exporter),
                                           str(case / 'client.jfr')], timeout=60)
        recording = json.loads(exported)
        events = recording['recording']['events']
        render_id = recording['renderThreadId']
        render_name = next(e['values']['eventThread']['javaName'] for e in events
                           if e['values'].get('eventThread', {}).get('javaThreadId') == render_id)
        if not any(e['type'] in ('jdk.ExecutionSample', 'jdk.NativeMethodSample') for e in events):
            raise RuntimeError('No render-thread JFR samples; do not present an empty attribution as valid')
        for window in windows:
            start = datetime.datetime.fromisoformat(window['firstTime'])
            end = datetime.datetime.fromisoformat(window['endTime'])
            selected = [e for e in events if start <= datetime.datetime.fromisoformat(
                e['values']['startTime'].replace('Z', '+00:00')) < end]
            window['jfr'] = summarize(selected, render_thread=render_name)
            window['jfr']['renderThread'] = dict(javaName=render_name, javaThreadId=render_id,
                                                identifiedBy='Minecraft.runTick sampled call stacks')
            if not window['jfr']['sampledThreads'].get(render_name):
                raise RuntimeError('No render-thread JFR coverage in window: ' + window['scene'])
            examples = {}
            for event in selected:
                if event['type'] not in ('jdk.ExecutionSample', 'jdk.NativeMethodSample'):
                    continue
                stack = (event['values'].get('stackTrace') or {}).get('frames', [])
                names = [f['method']['type']['name'] + '.' + f['method']['name'] for f in stack]
                if names and names[0] not in examples:
                    examples[names[0]] = names[:18]
            window['jfr']['topLeafStacks'] = [dict(leaf=leaf, samples=count, stack=examples.get(leaf, []))
                for leaf, count in window['jfr']['sampledThreads'][render_name]['topLeaves'][:15]]
    result = dict(case=str(case), windows=windows, limitations=[
        'TIMED and JFR are attribution instruments, not a probe-free FPS benchmark.',
        'Thread CPU values use a 15.625ms observed quantum; report their aggregate mean, not per-frame waits.',
        'Java and native scopes overlap; GPU execution overlaps CPU and is not subtracted from wall time.',
        'Native worker totals are throughput, not necessarily render-thread critical-path cost.',
        'Per-stage total/N frames is distinct from mean invocation time and avoids overstating rare stages.',
        'Missing-geometry baseline is only fault evidence, not an equal-content performance comparison.',
        'JFR stack hit fractions are samples, not wall milliseconds; native locks may be absent from JVM events.'])
    (case / 'analysis.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    for w in windows:
        print(w['scene'], 'frames', w['frames'], 'interval', w['intervalMs'], 'CPU mean', w['renderThreadCpuMeanMs'])
        print('Java', [(r['stage'], round(r['msPerRecordedFrame'], 3)) for r in w['javaSelf'][:6]])
        print('Native', [(r['stage'], round(r['msPerRecordedFrame'], 3)) for r in w['nativeRenderSelf'][:6]])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('case', type=Path)
    parser.add_argument('--jfr-tool', type=Path)
    args = parser.parse_args()
    analyze(args.case, args.jfr_tool)
