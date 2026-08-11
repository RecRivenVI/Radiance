"""One frozen configuration, from world open through queue quiescence. Never retries."""
import argparse
import collections
import csv
import datetime
import json
import io
import re
from pathlib import Path
import shutil
import tomllib
import zipfile

import profile_quality
import vanilla_performance as vp
from takeover_experiment import ROOT, TARGET, digest, probe_jar, storage_preflight, write, archive, recycle
from takeover_instancing import archive_closed_case

MANIFEST = vp.RESULT / 'loading-chain-manifest.json'


def prepare(distance=None, resolution=1080, runtime=False):
    storage_preflight()
    if MANIFEST.exists():
        raise RuntimeError('Do not overwrite a frozen loading baseline')
    declared = tomllib.loads((ROOT/'inputs.toml').read_text(encoding='utf8'))['prism_rendering_baseline']
    settings = Path(tomllib.loads((ROOT/'local.toml').read_text(encoding='utf8'))['inputs']['prism_rendering_baseline'])
    if digest(settings) not in declared['sha256']:
        raise RuntimeError('Settings input identity mismatch')
    with zipfile.ZipFile(settings) as source:
        options = dict(line.split(':', 1) for line in source.read('options.txt').decode().splitlines() if ':' in line)
        receipt = json.loads(settings.with_suffix(settings.suffix+'.json').read_text(encoding='utf8'))
        for item in receipt['files']:
            import hashlib
            if hashlib.sha256(source.read(item['path'])).hexdigest().lower() != item['sha256'].lower():
                raise RuntimeError('Settings ZIP readback mismatch')
    distance = int(options['renderDistance']) if distance is None else distance
    if not 2 <= distance <= 32 or resolution not in (1080,1440,2160):
        raise ValueError('Invalid explicit loading baseline')
    runtime_info = None
    if runtime:
        inputs = tomllib.loads((ROOT/'local.toml').read_text())['inputs']
        runtime_path = Path(inputs['prism_runtime_baseline'])
        allowed = tomllib.loads((ROOT/'inputs.toml').read_text())['prism_runtime_baseline']['sha256']
        if digest(runtime_path) not in allowed: raise RuntimeError('Runtime baseline input changed')
        runtime_info = dict(input='prism_runtime_baseline',sha256=digest(runtime_path),values=json.loads(runtime_path.read_text()))
        probe = ROOT/'components/render_diagnostics/build/loading-chain-r32-2160/probe.jar'
        if digest(probe)!='8b2ee75ba73b05c1ff1e5c405c70a59e42876c5e83d86561e46aa2961bbecb28':
            raise RuntimeError('Reuse only the already validated loading probe')
    else:
        output = ROOT/f'components/render_diagnostics/build/loading-chain-r{distance}-{resolution}'
        output.mkdir(parents=True, exist_ok=True)
        probe = output/'probe.jar'
        if probe.exists():
            raise RuntimeError('Keep the frozen loading probe')
        shutil.copy2(probe_jar(), probe)
    product = ROOT/'components/render_diagnostics/build/takeover-incremental-final/radiance.jar'
    fixture = vp.RESULT/'instancing/city-fixture.zip'
    write(MANIFEST, dict(product=dict(path=product.relative_to(ROOT).as_posix(), sha256=digest(product)),
        probe=dict(path=probe.relative_to(ROOT).as_posix(), sha256=digest(probe)),
        fixture=dict(path=fixture.relative_to(ROOT).as_posix(), sha256=digest(fixture)),
        settings=dict(input='prism_rendering_baseline', sha256=digest(settings)),
        sourceSettings=receipt, runtime=runtime_info, distance=distance, resolution=resolution, simulation=int(options['simulationDistance']),
        positionCommand='tp @s 0 512 0 facing 0 0 0',
        overrides=[f'renderDistance={distance}', f'framebuffer-height={resolution}', 'master volume=0', 'pauseOnLostFocus=false', 'fullscreen=false'],
        limitations=['World is the existing frozen vanilla city, not a new Prism save copy.',
                    'No driver/OS disk-cache flush; not a cold-storage benchmark.',
                    'Per-section native trace stays disabled because it changes scene preparation.']))


def phase_ranges(events):
    opened = next(r for r in events if r['phase']=='open-world')
    fixed = next(r for r in events if r['phase']=='camera-fixed')
    stopped = next(r for r in reversed(events) if r['phase']=='normal-stop')
    def final_quiet(low, high):
        # Only the final uninterrupted period within this stationary phase qualifies.
        interrupted = max((r['frame'] for r in events if r['phase']=='quiet-interrupted'
                           and low <= r['frame'] <= high), default=low-1)
        return next((r for r in reversed(events) if r['phase']=='quiet-confirmed'
                     and max(low,interrupted+1) <= r['frame'] <= high), None)
    route = next((r for r in events if r['phase']=='route-start'), None)
    interaction = next((r for r in events if r['phase']=='interactions-start'), None)
    stationary_end = route['frame'] if route else interaction['frame'] if interaction else stopped['frame']
    quiet = final_quiet(fixed['frame'],stationary_end)
    ranges = [('entry-and-teleport',opened['frame'],fixed['frame'])]
    ranges.append(('loading-and-settling',fixed['frame'],quiet['frame'] if quiet else stopped['frame']))
    if quiet:
        ranges.append(('queue-quiet',quiet['frame'],stationary_end))
    if interaction:
        ranges.append(('interactions',interaction['frame'],stopped['frame']))
    if route:
        end = next((r for r in events if r['phase']=='route-finished'), None)
        ranges.append(('streaming-route',route['frame'],end['frame'] if end else stopped['frame']))
        quiet = final_quiet(end['frame'],stopped['frame']) if end else None
        if end:
            ranges.append(('route-settling',end['frame'],quiet['frame'] if quiet else stopped['frame']))
        if quiet:
            ranges.append(('post-route-quiet',quiet['frame'],stopped['frame']))
    return ranges, quiet


def summarize(frames, timings, low, high):
    selected = [r for r in frames if low < int(r['frame']) <= high]
    tokens = {int(r['frame']) for r in selected}
    grouped = collections.defaultdict(lambda: collections.defaultdict(int))
    for r in timings:
        if int(r['frame']) not in tokens or r['domain'] in ('8','9'):
            continue
        field = 'self_ns' if r['domain'] in ('java','0','observer') else 'inclusive_ns'
        grouped[r['domain']+'/'+r['stage']][int(r['frame'])] += int(r[field])
    stages = {}
    for name, rows in grouped.items():
        cpu = name.startswith(('java/','0/','observer/'))
        values = [rows.get(t,0)/1e6 for t in tokens] if cpu else [v/1e6 for v in rows.values()]
        stages[name] = dict(profile_quality.distribution(values), returnedFrames=len(rows))
    workers = collections.defaultdict(list)
    for row in timings:
        if int(row['frame']) in tokens and row['domain'] in ('1','java-worker'):
            workers[row['domain']+'/'+row['stage']].append(int(row['inclusive_ns'])/1e6)
    return dict(frameCount=len(selected), frameRange=[low,high],
        intervalMs=profile_quality.distribution([int(r['start_interval_ns'])/1e6 for r in selected if int(r['start_interval_ns'])>=0]),
        wallMs=profile_quality.distribution([int(r['wall_ns'])/1e6 for r in selected]),
        cpuActiveMs=profile_quality.distribution([int(r['render_thread_cpu_ns'])/1e6 for r in selected if int(r['render_thread_cpu_ns'])>=0]),
        stagesMs=stages,
        workerInvocationsMs={name:dict(profile_quality.distribution(values), accumulatedMs=sum(values))
                             for name,values in workers.items()})


def loading_progress(events):
    rows = [e for e in events if e['phase'] in ('loading','steady')]
    totals = collections.Counter()
    last_activity = {}
    previous = {}
    for row in rows:
        seconds = row['elapsed_ns']/1e9
        for name in ('disk_loads','new_server_chunks','client_columns'):
            if row[name] != previous.get(name,0):
                last_activity[name] = seconds
            previous[name] = row[name]
        fields = {}
        for field in row.get('state',{}).get('chunk.native','').split(';'):
            key,sep,value = field.strip().partition('=')
            if sep:
                fields[key] = int(value)
        for key,value in fields.items():
            if key in ('queued_builds','pending_batch_builds','active_gpu_batches'):
                if value: last_activity[key] = seconds
            else:
                totals[key] += value
                if value: last_activity[key] = seconds
    return dict(nativeIntervalTotals=dict(totals),lastObservedActivitySeconds=last_activity,
        counters=previous,
        scope='Native counters are consumed once per sample; totals are accumulated work. Activity timestamps are one-second sampling bounds, not precise per-section latency.')


def base_gpu_window(log, start_ms, end_ms):
    values = []
    for line in log.splitlines():
        stamp = re.match(r'\[(\d+月\d+ \d+:\d+:\d+\.\d+)\]', line)
        metric = re.search(r'window-s=([\d.]+).*metric10-ms=([\d.]+)/([\d.]+)/(\d+)', line)
        if not stamp or not metric:
            continue
        when = datetime.datetime.strptime(stamp[1], '%d%m月%Y %H:%M:%S.%f').astimezone().timestamp() * 1000
        if when - float(metric[1]) * 1000 >= start_ms and when <= end_ms:
            values.append((float(metric[2]), float(metric[3]), int(metric[4])))
    frames = sum(row[2] for row in values)
    return dict(completedFrames=frames, buckets=len(values),
                meanMs=sum(row[0] * row[2] for row in values) / frames if frames else None,
                maximumMs=max((row[1] for row in values), default=None),
                scope='Existing main-queue GPU timestamp buckets wholly inside this UTC window; not per-frame pairing, not hardware busy counters.')


def analyze_case(case, width, height, distance):
    directory, = (case/'profiles').iterdir()
    quality = profile_quality.analyze(directory, allow_absent_world=True)
    with (directory/'frames.csv').open() as stream:
        frames = list(csv.DictReader(stream))
    with (directory/'timings.csv').open() as stream:
        timings = list(csv.DictReader(stream))
    events = [json.loads(line) for line in (case/'loading-chain.jsonl').read_text().splitlines()]
    ranges, quiet = phase_ranges(events)
    if any((int(r['width']),int(r['height'])) != (width,height) for r in frames):
        raise RuntimeError('Loading framebuffer mismatch')
    fixed = next(r for r in events if r['phase']=='camera-fixed')
    route = next((r for r in events if r['phase']=='route-start'), None)
    end_route = next((r for r in events if r['phase']=='route-finished'), None)
    interaction = next((r for r in events if r['phase']=='interactions-start'), None)
    for r in frames:
        if int(r['frame'])<=fixed['frame'] or r['world']!='true':
            continue
        if int(r['render_distance'])!=distance or r['sdk_generated_fps']!='0':
            raise RuntimeError('Unexpected view distance or generated frames')
        if interaction and int(r['frame']) > interaction['frame']:
            continue
        moving = route and int(r['frame']) > route['frame'] and (not end_route or int(r['frame']) <= end_route['frame']+2)
        if ((not moving and abs(float(r['camera_x'])-fixed['x'])>1e-5)
                or abs(float(r['camera_z'])-fixed['z'])>1e-5):
            raise RuntimeError('Camera moved after teleport')
        if abs(float(r['camera_yaw'])-fixed['yaw'])>1e-4 or abs(float(r['camera_pitch'])-fixed['pitch'])>1e-4:
            raise RuntimeError('Camera rotated after teleport')
    source = json.loads((case/'source-observations.json').read_text())
    if any(not source['coverage'].get(k,{}).get('compatible') for k in ('PARTS','BAKED','STATIC_STATE')):
        raise RuntimeError('Loading probe changed producer qualification')
    phases = {name:summarize(frames,timings,low,high) for name,low,high in ranges}
    log = (case / 'logs/latest.log').read_text(encoding='utf8', errors='replace')
    for name, low, high in ranges:
        start = max(r['utc_ms'] for r in events if r['frame'] <= low)
        end = min(r['utc_ms'] for r in events if r['frame'] >= high)
        phases[name]['baseGpu'] = base_gpu_window(log, start, end)
    output = dict(quality=quality,phases=phases,events=events,stableAchieved=quiet is not None,
        loadingProgress=loading_progress(events),
        source=source, configuration=json.loads((case/'frozen-inputs.json').read_text()) if (case/'frozen-inputs.json').exists() else json.loads(MANIFEST.read_text()),
        limitations=['CPU wall stages include waits. Java/native and GPU ranges overlap; never sum them.',
            'Worker time is accumulated work across threads, not elapsed loading duration.',
            'Queue-quiet requires empty queues and no disk/generation/client-arrival/native-batch activity for ten seconds, followed by a twenty-second observation; interruptions reset it.',
            'Queue-quiet does not prove denoiser/cache visual convergence or display scanout.',
            'Native chunk submit-to-completion wall time includes queueing and polling; no independent chunk GPU duration.',
            'Section admission-to-first-use identities and exact server request/I/O/decode split are not instrumented by this capture.',
            'Missing GPU samples are not zero. Whole-card telemetry is not process-exclusive.'])
    write(case/'loading-chain-analysis.json',output)
    return phases


def run():
    manifest=json.loads(MANIFEST.read_text())
    for key in ('product','probe','fixture'):
        if digest(ROOT/manifest[key]['path'])!=manifest[key]['sha256']:
            raise RuntimeError('Frozen '+key+' changed')
    settings=Path(tomllib.loads((ROOT/'local.toml').read_text())['inputs'][manifest['settings']['input']])
    if digest(settings)!=manifest['settings']['sha256']:
        raise RuntimeError('Frozen settings changed')
    heap = dict(memory_max='8G')
    if manifest.get('runtime'):
        runtime = manifest['runtime']
        path=Path(tomllib.loads((ROOT/'local.toml').read_text())['inputs'][runtime['input']])
        if digest(path)!=runtime['sha256']: raise RuntimeError('Runtime input changed')
        values=runtime['values']
        if values['additionalJvmArgs']: raise RuntimeError('Unexpected extra JVM arguments')
        heap=dict(memory_max=str(values['maximumMemoryMiB'])+'M',memory_min=str(values['minimumMemoryMiB'])+'M')
    case=vp.run(f'r{manifest["distance"]}-{manifest["resolution"]}',plan='loading',product=ROOT/manifest['product']['path'],
        frozen_probe=ROOT/manifest['probe']['path'],fixture=ROOT/manifest['fixture']['path'],
        group='loading-chain-runtime' if manifest.get('runtime') else 'loading-chain',settings=settings,baseline=manifest,**heap)
    archive_closed_case(case)
    write(vp.RESULT/'loading-chain-current.json',dict(case=case.relative_to(ROOT).as_posix()))


def report(case):
    case = case.resolve()
    if not case.is_relative_to(vp.RESULT.resolve()):
        raise ValueError('Report only owned validation evidence')
    data = json.loads((case/'loading-chain-analysis.json').read_text())
    values = collections.defaultdict(list)
    with zipfile.ZipFile(case/'raw-evidence.zip') as source:
        name, = (n for n in source.namelist() if n.endswith('/timings.csv'))
        with source.open(name) as raw, io.TextIOWrapper(raw,encoding='utf8') as text:
            for row in csv.DictReader(text):
                if row['domain'] in ('1','java-worker'):
                    values[row['domain']+'/'+row['stage']].append(int(row['inclusive_ns'])/1e6)
    workers = {name:dict(profile_quality.distribution(v),accumulatedMs=sum(v),
        scope='Whole capture, wall durations across workers; frame 0 has no render-frame or phase attribution. Do not sum overlapping stages.') for name,v in values.items()}
    write(case/'worker-whole-capture.json',workers)
    data['workerWholeCaptureMs'] = workers
    write(case/'loading-chain-analysis.json',data)


def discard(case):
    from takeover_city import assert_stopped
    assert_stopped()
    case=case.resolve()
    if not case.is_relative_to(vp.RESULT.resolve()) or not (case/'DISCARDED.json').is_file():
        raise RuntimeError('Explicit discarded-case identity required')
    log=(case/'console.log').read_text(encoding='utf8',errors='replace')
    if 'VK_ERROR_DEVICE_LOST' in log:
        raise RuntimeError('Preserve device loss and stop, do not start another test')
    if 'All dimensions are saved' not in log or 'LOADING_CHAIN normal-stop' not in log:
        raise RuntimeError('Do not discard a live or unclosed client')
    for name in ('radiance-device-loss-trace.log','radiance-gpu-capabilities.log','radiance-as-lifetime-close.log'):
        if (vp.CLIENT/name).is_file(): shutil.copy2(vp.CLIENT/name,case/name)
    raw=[p for p in case.rglob('*') if p.is_file() and p.suffix in ('.log','.csv')]
    archive(raw,case,case/'discarded-raw-evidence.zip')
    # Keep the raw originals too until ordinary evidence cleanup; only the generated client retires.
    if vp.CLIENT.exists(): recycle(vp.CLIENT,vp.VALIDATION)
    stop=vp.RESULT/'STOP.json'
    if stop.is_file():
        data=json.loads(stop.read_text())
        if Path(data['case']).resolve()!=case:
            raise RuntimeError('Another failure owns STOP.json')
        stop.rename(case/'analysis-failure-stop.json')
    if MANIFEST.is_file(): MANIFEST.rename(case/'discarded-manifest.json')
    write(case/'discard-resolution.json',dict(userDiscarded=True,performanceValid=False,
        normalSave=True,deviceLost=False,reason='User requested 32 distance / 3840x2160; old probe coordinate and writer-boundary defects corrected before new capture'))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command',choices=('prepare','run','discard','report'))
    parser.add_argument('--distance',type=int)
    parser.add_argument('--resolution',type=int,default=1080)
    parser.add_argument('--case',type=Path)
    parser.add_argument('--manifest',type=Path)
    parser.add_argument('--runtime',action='store_true')
    args=parser.parse_args()
    if args.manifest:
        MANIFEST=args.manifest.resolve()
        if not MANIFEST.is_relative_to(vp.RESULT.resolve()): raise ValueError('Manifest must stay inside owned result directory')
    if args.command=='prepare': prepare(args.distance,args.resolution,args.runtime)
    elif args.command=='discard': discard(args.case)
    elif args.command=='report': report(args.case)
    else: run()
