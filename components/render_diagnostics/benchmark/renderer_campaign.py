"""Frozen, no-Voxy city investigation; resource observations are distinct from throughput."""
import argparse
import csv
import datetime
import json
from pathlib import Path
import subprocess
import time
import tomllib

import profile_quality
import vanilla_performance as city
from takeover_experiment import ROOT, TARGET, WORKSPACE_SOFT_LIMIT_GIB, digest, write


def observe_wddm(case):
    """A separate, bounded memory observer; never opens or controls another process."""
    case = Path(case).resolve()
    if not any(case.is_relative_to(path.resolve()) for path in (
            ROOT/'validations/performance-sources/result',
            ROOT/'validations/conformance-takeover/result/world-materials-134')):
        raise ValueError('Owned city result directory required')
    pid = int(json.loads((case/'process.json').read_text())['pid'])
    query = (f"$rows=@(Get-CimInstance -ClassName Win32_PerfFormattedData_GPUPerformanceCounters_GPUProcessMemory "
             f"-Filter \"Name LIKE 'pid_{pid}_%'\" | Select-Object Name,DedicatedUsage,SharedUsage,TotalCommitted); "
             "ConvertTo-Json -InputObject $rows -Compress")
    deadline = time.monotonic()+1200
    with (case/'wddm.jsonl').open('x',encoding='utf8') as out:
        while time.monotonic()<deadline:
            rows=json.loads(subprocess.check_output(['powershell','-NoProfile','-Command',query],text=True,timeout=10))
            if not rows: break
            source = case/'lifecycle.jsonl'
            lines=source.read_text(encoding='utf8').splitlines() if source.is_file() else []
            out.write(json.dumps(dict(time=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                phase=json.loads(lines[-1])['phase'] if lines else None,
                adapters=rows,dedicated=sum(r['DedicatedUsage'] for r in rows),shared=sum(r['SharedUsage'] for r in rows),
                committed=sum(r['TotalCommitted'] for r in rows)))+'\n')
            out.flush(); time.sleep(5)


def analyze_minimal(case,width,height,distance):
    settings=json.loads((case/'minimal-settings.json').read_text())
    if (settings['width'],settings['height'],settings['distance'],settings['nativeCollector']) != (width,height,distance,False):
        raise RuntimeError('Minimal control configuration differs')
    with (case/'minimal-frames.csv').open() as source:frames=list(csv.DictReader(source))
    if len(frames)<50:raise RuntimeError('Too few completed minimal control frames')
    data=dict(intervalMs=profile_quality.distribution([int(r['start_interval_ns'])/1e6 for r in frames if int(r['start_interval_ns'])>=0]),
              frameCount=len(frames),settings=settings,
              limitations=['One runTick control/clock hook, no source/render/native collector hooks; same packaged renderer.',
                           'No per-stage GPU timings in this control. CPU-side device-loss breadcrumbs remain enabled.'])
    source=json.loads((case/'source-observations.json').read_text());city.validate_sources(source,allow_unobserved=True)
    write(case/'analysis.json',dict(windows={'CONTROLLER_ONLY':data},source=source))
    return {'CONTROLLER_ONLY':data}


def analyze_lifecycle(case, width, height, distance):
    observations = [json.loads(line) for line in (case / 'lifecycle.jsonl').read_text().splitlines()]
    external = []
    if (case / 'gpu-throughput-window.json').is_file():
        window = json.loads((case / 'gpu-throughput-window.json').read_text())
        if window['end'] < window['start']:
            raise ValueError('Invalid external profiler interval')
        external.append(window)
    required = {'title-before','loading','steady','flying','post-route-steady','entities-still',
                'entities-moving','after-remove','title-after','reenter-loading','reenter-steady','flushing'}
    if not required.issubset({row['phase'] for row in observations}):
        raise RuntimeError('Incomplete lifecycle phases')
    noise = [json.loads(line) for line in (case / 'noise.jsonl').read_text().splitlines()]
    directory, = (case / 'profiles').iterdir()
    data = profile_quality.analyze(directory, allow_absent_world=True)
    with (directory / 'frames.csv').open() as source:
        frames = list(csv.DictReader(source))
    if any((int(f['width']),int(f['height']),int(f['render_distance']),f['sdk_generated_fps']) !=
           (width,height,distance,'0') for f in frames):
        raise RuntimeError('Lifecycle configuration differs')
    # Millisecond epoch alignment, not accumulated frame durations: pauses between
    # frames and blocking world-open/disconnect must not silently shift phases.
    offsets = [int(frame['start_epoch_ms']) / 1000 for frame in frames]
    elapsed = max(offsets[-1] + int(frames[-1]['wall_ns']) / 1e9,
                  datetime.datetime.fromisoformat(observations[-1]['time'].replace('Z','+00:00')).timestamp())
    phases = []
    for i,row in enumerate(observations):
        if i and row['phase'] == observations[i-1]['phase']: continue
        end = next((r for r in observations[i+1:] if r['phase']!=row['phase']),None)
        begin_t = datetime.datetime.fromisoformat(row['time'].replace('Z','+00:00')).timestamp()
        end_t = datetime.datetime.fromisoformat(end['time'].replace('Z','+00:00')).timestamp() if end else elapsed
        memory = [r for r in observations if begin_t <= datetime.datetime.fromisoformat(r['time'].replace('Z','+00:00')).timestamp() < end_t]
        samples = [r for r in noise if begin_t <= datetime.datetime.fromisoformat(r['time']).timestamp() < end_t]
        eligible = [f for f,t in zip(frames,offsets) if t>=begin_t and t+int(f['wall_ns'])/1e9 <= end_t]
        selected = [f for f in eligible if not any(
            int(f['start_epoch_ms'])/1000 < w['end'] and int(f['start_epoch_ms'])/1000+int(f['wall_ns'])/1e9 > w['start']
            for w in external)]
        phases.append(dict(phase=row['phase'],start=begin_t,end=end_t,seconds=end_t-begin_t,
            externalProfilerFramesExcluded=len(eligible)-len(selected),
            intervalMs=profile_quality.distribution([int(f['start_interval_ns'])/1e6 for f in selected if int(f['start_interval_ns'])>=0]),
            cpuActiveMs=profile_quality.distribution([int(f['render_thread_cpu_ns'])/1e6 for f in selected]),
            gpuMiB=profile_quality.distribution([r['gpu'][0] for r in samples]),
            privateBytes=profile_quality.distribution([r['processMemory']['privateBytes'] for r in samples if r['processMemory']]),
            heapUsed=profile_quality.distribution([r['heapUsed'] for r in memory]),
            directBytes=profile_quality.distribution([r.get('buffer-direct',{}).get('used',0) for r in memory]),
            observations=memory))
    frame_end=offsets[-1]+int(frames[-1]['wall_ns'])/1e9
    write(case/'lifecycle-analysis.json',dict(phases=phases,status=data['status'],externalProfilerWindows=external,
        frameCoverage=dict(start=offsets[0],end=frame_end,observedEnd=elapsed,
                           uncoveredTailSeconds=max(0,elapsed-frame_end)),
        limitations=['FRAME_ONLY lifecycle includes 1 Hz census and allocation tracking; not a clean throughput baseline.',
                     'NVML is whole-card, VMA allocations include host heaps. No forced GC or cache purge.',
                     'Phase rows have 1 Hz precision; do not attribute boundary long frames to entity removal or world simulation.',
                     'Captured frames must fit the observed phase bin. Raw long frames remain retained; uncovered tail is explicit.']))
    return {'FRAME_ONLY':data}


def run(args):
    if not args.series or len(args.series) > 32 or any(c not in 'abcdefghijklmnopqrstuvwxyz0123456789_-' for c in args.series):
        raise ValueError('Series must be a bounded directory label')
    manifest = json.loads(args.manifest.read_text(encoding='utf8'))
    for key in ('core','extra','probe'):
        if digest(Path(manifest[key]['path'])) != manifest[key]['sha256']:
            raise RuntimeError('Frozen artifact mismatch: ' + key)
    city.VALIDATION = ROOT / 'validations/performance-sources'
    city.RESULT = city.VALIDATION / 'result' / args.series
    city.CLIENT = city.VALIDATION / 'instance' / TARGET / 'client'
    probe = args.probe or Path(manifest['probe']['path'])
    if args.lifecycle and not args.probe:
        raise ValueError('Lifecycle requires the rebuilt, explicitly frozen diagnostic probe')
    settings = None
    if args.settings_input:
        registry = tomllib.loads((ROOT/'inputs.toml').read_text(encoding='utf8'))
        local = tomllib.loads((ROOT/'local.toml').read_text(encoding='utf8'))
        settings = Path(local['inputs'][args.settings_input])
        if digest(settings) not in registry[args.settings_input]['sha256']:
            raise RuntimeError('Changed registered settings input')
    return city.run(f'r32-{args.resolution}',plan=args.plan,product=manifest['core']['path'],group=args.label,
        seconds=args.seconds,frozen_probe=probe,extra_product=manifest['extra']['path'],workspace_limit_gib=args.workspace_limit,
        settings=settings,
        memory_max=args.memory_max,memory_min=args.memory_min,
        lifecycle=args.lifecycle,allocation_census=args.census,loading_movement=args.loading_movement,movement=args.moving,
        controller_only=args.controller_only,aftermath=not args.without_aftermath,
        baseline=dict(core=manifest['core'],extra=manifest['extra'],probe=dict(path=str(probe),sha256=digest(probe)),
                      fixtureSha256=digest(city.FIXTURE),voxy=False))


if __name__ == '__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--manifest',type=Path,required=True)
    p.add_argument('--label',required=True)
    p.add_argument('--series',default='novoxy-20261029')
    p.add_argument('--probe',type=Path)
    p.add_argument('--resolution',type=int,choices=(720,1080,1440,2160),default=1080)
    p.add_argument('--plan',choices=('takeover','throughput','attribution','loading'),default='takeover')
    p.add_argument('--seconds',type=int,default=20)
    p.add_argument('--workspace-limit',type=int,default=WORKSPACE_SOFT_LIMIT_GIB)
    p.add_argument('--lifecycle',action='store_true')
    p.add_argument('--census',action='store_true')
    p.add_argument('--loading-movement',action='store_true')
    p.add_argument('--moving',action='store_true')
    p.add_argument('--controller-only',action='store_true')
    p.add_argument('--without-aftermath',action='store_true')
    p.add_argument('--settings-input')
    p.add_argument('--memory-max',default='8G')
    p.add_argument('--memory-min')
    run(p.parse_args())
