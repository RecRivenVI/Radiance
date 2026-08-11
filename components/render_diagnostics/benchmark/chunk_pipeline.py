"""Frozen 1080p chunk-pipeline loading comparisons. One explicit process per command; no retries."""
import argparse
import json
import shutil
from pathlib import Path
import tomllib
import csv
import io
import re
import zipfile

import loading_chain
import vanilla_performance as vp
from takeover_experiment import ROOT, archive, digest, probe_jar, recycle, storage_preflight, write
from takeover_city import assert_stopped
from takeover_instancing import archive_closed_case

RESULT = vp.RESULT / 'chunk-pipeline'


def freeze(name, product=None, detail=False, movement=False, interactions=False):
    if not name.replace('-', '').isalnum():
        raise ValueError('Use an explicit simple checkpoint name')
    storage_preflight()
    destination = RESULT / (name + '.json')
    if destination.exists():
        raise RuntimeError('Do not replace a frozen checkpoint')
    data = json.loads((vp.RESULT / 'loading-chain-runtime-manifest.json').read_text())
    data['resolution'] = 1080
    data['profileMode'] = 'TIMED' if detail else 'FRAME_ONLY'
    if sum((detail, movement, interactions)) > 1:
        raise ValueError('Movement and bounded detail are independent captures')
    data['movement'] = movement
    data['interactions'] = interactions
    data['overrides'] = ['renderDistance=32', 'framebuffer=1920x1080', 'master volume=0',
                         'pauseOnLostFocus=false', 'fullscreen=false']
    if (RESULT / 'probe.json').is_file():
        data['probe'] = json.loads((RESULT / 'probe.json').read_text())
    if product is not None:
        product = product.resolve()
        if not product.is_relative_to(ROOT) or not product.is_file():
            raise ValueError('Use an existing repository product')
        data['product'] = dict(path=product.relative_to(ROOT).as_posix(), sha256=digest(product))
    for key in ('product', 'probe', 'fixture'):
        if digest(ROOT / data[key]['path']) != data[key]['sha256']:
            raise RuntimeError('Frozen input changed: ' + key)
    write(destination, data)


def run(name, label):
    manifest = json.loads((RESULT / (name + '.json')).read_text())
    receipt = RESULT / (label + '.json')
    if receipt.exists():
        raise RuntimeError('Do not reuse a run label')
    local = tomllib.loads((ROOT / 'local.toml').read_text())['inputs']
    for key in ('product', 'probe', 'fixture'):
        if digest(ROOT / manifest[key]['path']) != manifest[key]['sha256']:
            raise RuntimeError('Checkpoint changed: ' + key)
    settings = Path(local[manifest['settings']['input']])
    runtime = manifest['runtime']
    if digest(settings) != manifest['settings']['sha256'] or digest(Path(local[runtime['input']])) != runtime['sha256']:
        raise RuntimeError('Configuration input changed')
    write(receipt, dict(checkpoint=name, started=True, passed=False, automaticRetry=False))
    case = vp.run('r32-1080', plan='loading', product=ROOT / manifest['product']['path'],
                  frozen_probe=ROOT / manifest['probe']['path'], fixture=ROOT / manifest['fixture']['path'],
                  settings=settings, memory_min=str(runtime['values']['minimumMemoryMiB']) + 'M',
                  memory_max=str(runtime['values']['maximumMemoryMiB']) + 'M', baseline=manifest,
                  group='chunk-pipeline-' + label, loading_detail=manifest.get('profileMode') == 'TIMED',
                  loading_movement=manifest.get('movement', False),
                  loading_interactions=manifest.get('interactions', False))
    archive_closed_case(case)
    loading_chain.report(case)
    write(receipt, dict(checkpoint=name, case=case.relative_to(ROOT).as_posix(), passed=True, automaticRetry=False))


def freeze_probe(name):
    destination = ROOT / ('components/render_diagnostics/build/chunk-pipeline-probe-' + name) / 'probe.jar'
    if destination.exists():
        raise RuntimeError('Do not replace a frozen probe')
    storage_preflight()
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(probe_jar(), destination)
    write(RESULT / 'probe.json', dict(path=destination.relative_to(ROOT).as_posix(), sha256=digest(destination)))


def preserve_cap_failure():
    assert_stopped()
    stop = vp.RESULT / 'STOP.json'
    failure = json.loads(stop.read_text())
    case = Path(failure['case']).resolve()
    if not case.is_relative_to(vp.RESULT.resolve()):
        raise RuntimeError('Foreign failure')
    log = (case / 'console.log').read_text(encoding='utf8', errors='replace')
    if any(x in log for x in ('VK_ERROR_DEVICE_LOST', 'MCVR fatal', 'LOADING_CHAIN FAIL')) or 'All dimensions are saved' not in log:
        raise RuntimeError('Do not resolve a GPU fault or unclosed client')
    status, = (case / 'profiles').glob('*/STATUS.txt')
    fields = dict(line.split('=', 1) for line in status.read_text().splitlines() if '=' in line)
    if int(fields['output-cap-dropped-batches']) <= 0:
        raise RuntimeError('This resolver handles only a proven diagnostic output-cap failure')
    for name in ('radiance-device-loss-trace.log', 'radiance-gpu-capabilities.log', 'radiance-as-lifetime-close.log'):
        if (vp.CLIENT / name).is_file():
            shutil.copy2(vp.CLIENT / name, case / name)
    write(case / 'capture-failure.json', dict(performanceValid=False, normalSave=True, deviceLost=False,
          reason='Full TIMED loading exceeded its unchanged 128 MiB output cap; use FRAME_ONLY for whole-load comparison and bounded TIMED detail.'))
    paths = [p for p in case.rglob('*') if p.is_file() and p.suffix in ('.csv', '.log')]
    archive(paths, case, case / 'raw-evidence.zip')
    import subprocess
    for path in paths:
        subprocess.run(['pwsh', '-NoProfile', '-Command',
            "Add-Type -AssemblyName Microsoft.VisualBasic; [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile('" +
            str(path).replace("'", "''") + "','OnlyErrorDialogs','SendToRecycleBin')"], check=True)
    recycle(vp.CLIENT, vp.VALIDATION)
    stop.rename(case / 'analysis-failure-stop.json')


def retain_product(name, product):
    product = product.resolve()
    if product.parent != (ROOT / 'versions/1.21.1-neoforge/build/libs').resolve():
        raise ValueError('Retain only a generated Target distribution')
    destination = ROOT / ('components/render_diagnostics/build/chunk-pipeline-' + name) / 'radiance.jar'
    if destination.exists():
        raise RuntimeError('Keep the prior product identity')
    destination.parent.mkdir(parents=True, exist_ok=True)
    checksum = digest(product)
    product.rename(destination)
    if digest(destination) != checksum:
        raise RuntimeError('Retained product identity changed')
    write(RESULT / (name + '-product.json'), dict(path=destination.relative_to(ROOT).as_posix(), sha256=checksum))


def comparison(baseline, candidate):
    rows = {}
    identities = []
    for role, label in [('baseline', baseline), ('candidate', candidate)]:
        receipt = json.loads((RESULT/(label+'.json')).read_text())
        if not receipt['passed']: raise RuntimeError('Unclosed comparison case')
        case = ROOT/receipt['case']
        data = json.loads((case/'loading-chain-analysis.json').read_text())
        manifest = data['configuration']
        identities.append({key:manifest[key] for key in ('probe','fixture','settings','runtime','distance','resolution','movement')})
        events = data['events']
        route = next(e for e in events if e['phase']=='route-start')
        returned = next(e for e in events if e['phase']=='route-finished')
        initial = [e for e in events if e['frame'] <= route['frame']]
        final_ready = initial[-1]['ready_sections']
        with zipfile.ZipFile(case/'raw-evidence.zip') as raw:
            frame_file, = [name for name in raw.namelist() if name.endswith('/frames.csv')]
            with raw.open(frame_file) as stream:
                frames = list(csv.DictReader(io.TextIOWrapper(stream)))
            log = raw.read('logs/latest.log').decode('utf8',errors='replace')
        phases = {}
        for name, phase in data['phases'].items():
            low, high = phase['frameRange']
            intervals = [int(f['start_interval_ns'])/1e6 for f in frames if low < int(f['frame']) <= high]
            phases[name] = dict(intervalMs=phase['intervalMs'],cpuActiveMs=phase['cpuActiveMs'],baseGpu=phase['baseGpu'],
                longFrames={str(ms):sum(value>ms for value in intervals) for ms in (33.333333,50,100,250)},frames=len(intervals))
        noise = [json.loads(line) for line in (case/'noise.jsonl').read_text().splitlines()]
        peaks = {key:max((r['processMemory'][key] for r in noise if r.get('processMemory')), default=None)
                 for key in ('peakWorkingSet','privateBytes','peakCommit')}
        rows[role] = dict(label=label,case=receipt['case'],product=manifest['product'],
            phases=phases,initialReadySections=final_ready,
            firstReachedFinalReadyCountSeconds=next(e['elapsed_ns']/1e9 for e in initial if e.get('ready_sections')==final_ready),
            finalInitialQuietConfirmedSeconds=next(e['elapsed_ns']/1e9 for e in reversed(initial) if e['phase']=='quiet-confirmed'),
            initialProgress=loading_chain.loading_progress(initial),
            returnedReadySections=events[-1]['ready_sections'],postRouteQuiet=data['stableAchieved'],
            routeDurationSeconds=(returned['elapsed_ns']-route['elapsed_ns'])/1e9,
            waypoints=sum(e['phase']=='route-waypoint' for e in events),processPeaks=peaks,
            allocatorPeakBytes=max(map(int,re.findall(r'vma-bytes=(\d+)',log)),default=None),
            deviceWideMemoryPeakMiB=max((r['gpu'][0] for r in noise),default=None),
            gpuUtilizationRange=[min(r['gpu'][2] for r in noise),max(r['gpu'][2] for r in noise)],
            otherGameObservations=[dict(time=r['time'],games=r['otherGames']) for r in noise if r['otherGames']])
    if identities[0] != identities[1]: raise RuntimeError('Product-only comparison inputs differ')
    output = RESULT/f'comparison-{baseline}-{candidate}.json'
    if output.exists(): raise RuntimeError('Do not overwrite comparison evidence')
    write(output,dict(inputs=identities[0],rows=rows,limitations=[
        'Each row is a separately frozen process, without disk cache flushing; no pooled process windows.',
        'Ready count is CPU publication after GPU build completion, not completed display scanout.',
        'Route is acknowledged server teleports, not continuous player motion; route duration is retained.',
        'Post-route quiet can remain unachieved. Retain timeout, activity, and actual geometry counts.',
        'Allocator peak includes host-visible VMA storage; NVML is device-wide. Process memory and GPU memory are separate.',
        'The performance probe stays FRAME_ONLY; interaction latency comes from separate instrumented cases.']))
    print(output,flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('freeze', 'run', 'freeze-probe', 'preserve-cap', 'retain-product','compare'))
    parser.add_argument('name', nargs='?')
    parser.add_argument('--label')
    parser.add_argument('--product', type=Path)
    parser.add_argument('--detail', action='store_true')
    parser.add_argument('--movement', action='store_true')
    parser.add_argument('--interactions', action='store_true')
    parser.add_argument('--baseline')
    args = parser.parse_args()
    if args.command == 'compare':
        comparison(args.baseline,args.name)
    elif args.command == 'preserve-cap':
        preserve_cap_failure()
    elif args.command == 'retain-product':
        retain_product(args.name, args.product)
    elif args.command == 'freeze-probe':
        freeze_probe(args.name)
    elif args.command == 'freeze':
        freeze(args.name, args.product, args.detail, args.movement, args.interactions)
    else:
        if not args.label or not args.label.replace('-', '').isalnum():
            raise ValueError('Each process needs a unique explicit label')
        run(args.name, args.label)
