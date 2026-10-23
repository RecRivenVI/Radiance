"""Frozen vanilla city diagnosis. Owns one disposable client; never retries a failure."""
from pathlib import Path
import argparse
import csv
import datetime
import json
import os
import re
import shutil
import subprocess
import threading
import time
import zipfile

import gpu_monitor
import profile_quality
from takeover_experiment import ROOT, TARGET, digest, probe_jar, recycle, storage_preflight, write
from takeover_city import assert_stopped, loaded_core

VALIDATION = ROOT / 'validations/performance-vanilla'
RESULT = VALIDATION / 'result'
CLIENT = VALIDATION / 'instance' / TARGET / 'client'
FIXTURE = ROOT / 'validations/migration-city_world/result/vanilla-city-fixture.zip'
PRODUCT = ROOT / 'components/render_diagnostics/build/takeover-instancing/radiance.jar'


def other_games(pid):
    # Record project identities, never access tokens or complete launch command lines.
    query = r"""@(Get-CimInstance Win32_Process | Where-Object {
      $_.Name -in @('java.exe','javaw.exe') -and $_.ProcessId -ne OWN_PID -and
      $_.CommandLine -match 'BootstrapLauncher|devlaunch.Main|net.minecraft|KnotClient|net.neoforged.fml.startup.Client|--quickPlaySingleplayer|--graphicsBackend'
    } | ForEach-Object {
      $project = if ($_.CommandLine -match 'D:[\\/]+Workspaces[\\/]+Repositories[\\/]+GitHub[\\/]+[^\\/ ]+[\\/]+([^\\/ ]+)') {$Matches[1]} else {'unattributed-game'}
      [pscustomobject]@{pid=$_.ProcessId;project=$project;started=$_.CreationDate.ToString('o')}
    }) | ConvertTo-Json -Compress""".replace('OWN_PID', str(pid))
    value = subprocess.check_output(['pwsh', '-NoProfile', '-Command', query], text=True, timeout=15).strip()
    data = json.loads(value) if value else []
    return data if isinstance(data, list) else [data]


def monitor(case, stop, own_pid):
    reader = None
    try:
        reader = gpu_monitor.Reader()
        with (case / 'noise.jsonl').open('x', encoding='utf8') as stream:
            games, next_games = [], 0
            while not stop.is_set():
                if time.monotonic() >= next_games:
                    games = other_games(own_pid)
                    next_games = time.monotonic() + 5
                stream.write(json.dumps(dict(time=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                                            gpu=reader.sample(), otherGames=games)) + '\n')
                stream.flush()
                stop.wait(1)
    except Exception as error:
        write(case / 'noise-error.json', dict(error=str(error)))
    finally:
        if reader:
            reader.close()


def analyze(case, width, height, distance):
    windows = {}
    noise = [json.loads(line) for line in (case / 'noise.jsonl').read_text().splitlines()]
    base_gpu, allocations = [], []
    for line in (case / 'logs/latest.log').read_text(encoding='utf8', errors='replace').splitlines():
        stamp = re.match(r'\[(\d+月\d+ \d+:\d+:\d+\.\d+)\]', line)
        metric = re.search(r'window-s=([\d.]+).*metric10-ms=([\d.]+)/([\d.]+)/(\d+)', line)
        if stamp and metric:
            base_gpu.append((datetime.datetime.strptime(stamp[1], '%d%m月%Y %H:%M:%S.%f').astimezone(),
                             *map(float, metric.groups()[:3]), int(metric[4])))
            memory = re.search(r'vma-bytes=(\d+) vma-count=(\d+)', line)
            if memory:
                allocations.append((base_gpu[-1][0], int(memory[1]), int(memory[2])))
    for directory in sorted((case / 'profiles').iterdir()):
        data = profile_quality.analyze(directory)
        meta = dict(line.split('=', 1) for line in (directory / 'METADATA.txt').read_text().splitlines() if '=' in line)
        with (directory / 'frames.csv').open() as stream:
            frames = list(csv.DictReader(stream))
        if any((r['width'], r['height'], r['render_distance'], r['sdk_generated_fps']) !=
               (str(width), str(height), str(distance), '0') for r in frames):
            raise RuntimeError('Frame configuration differs from requested case')
        signature = ('dimension', 'camera_x', 'camera_y', 'camera_z', 'camera_yaw', 'camera_pitch',
                     'day_time', 'rain', 'thunder')
        states = {tuple(r[k] for k in signature) for r in frames}
        if len(states) != 1:
            raise RuntimeError('Camera/weather/time changed during sampling')
        start = datetime.datetime.fromisoformat(meta['time'].replace('Z', '+00:00'))
        seconds = sum(int(r['wall_ns']) for r in frames) / 1e9
        end = start + datetime.timedelta(seconds=seconds)
        observed = [r for r in noise if start <= datetime.datetime.fromisoformat(r['time']) <= end]
        if not observed:
            raise RuntimeError('No concurrent process/GPU observations for window')
        buckets = [r for r in base_gpu if r[0] - datetime.timedelta(seconds=r[1]) >= start and r[0] <= end]
        completed = sum(r[4] for r in buckets)
        memory = [r for r in allocations if start <= r[0] <= end]
        data.update(sceneState=dict(zip(signature, next(iter(states)))),
                    nativeAllocationBytes=profile_quality.distribution([r[1] for r in memory]),
                    nativeAllocationCount=profile_quality.distribution([r[2] for r in memory]),
                    baseGpu=dict(completedFrames=completed, buckets=len(buckets),
                        meanMs=sum(r[2] * r[4] for r in buckets) / completed if completed else None,
                        reportedMaximumMs=max((r[3] for r in buckets), default=None),
                        scope='Existing main-queue timestamp pair, also active in FRAME_ONLY; completion-time buckets wholly inside wall window. Not per-frame token pairing or busy hardware counters.'),
                    cpuActiveMs=profile_quality.distribution([int(r['render_thread_cpu_ns']) / 1e6 for r in frames]),
                    seconds=seconds, noise=dict(samples=len(observed),
                        wholeGpuPercent=profile_quality.distribution([r['gpu'][2] for r in observed]),
                        wholeGpuMemoryMiB=profile_quality.distribution([r['gpu'][0] for r in observed]),
                        otherGames=list({(g['pid'], g['project']): g for r in observed for g in r['otherGames']}.values()),
                        scope='Whole-card NVML; process list does not prove each process GPU share'))
        windows[meta['profile-mode']] = data
    source = json.loads((case / 'source-observations.json').read_text())
    for family in ('PARTS', 'BAKED', 'STATIC_STATE'):
        if not source['coverage'].get(family, {}).get('compatible'):
            raise RuntimeError('Performance probe changed or failed to observe source contract: ' + family)
    if any(not value['compatible'] and any('radiance_probe' in evidence for evidence in value['evidence'])
           for value in source['coverage'].values()):
        raise RuntimeError('Probe changed a producer source; do not interpret its performance')
    write(case / 'analysis.json', dict(windows=windows, source=source))
    return windows


def run(label, plan='vanilla', product=None, group=None, seconds=30, frozen_probe=None, fixture=None):
    global PRODUCT
    if product is not None:
        PRODUCT=Path(product).resolve()
        if not PRODUCT.is_relative_to(ROOT) or not PRODUCT.is_file(): raise ValueError('Use a built/frozen repository artifact')
    distance, resolution = map(int, label.removeprefix('r').split('-'))
    width, height = (1920, 1080) if resolution == 1080 else (2560, 1440)
    fixture = Path(fixture).resolve() if fixture is not None else FIXTURE
    assert_stopped()
    if (RESULT / 'STOP.json').exists() or CLIENT.exists():
        raise RuntimeError('Previous retained failure/client requires analysis; no automatic retry')
    occupancy = storage_preflight()
    # Account the actual packaged payload and fixed fixture, not a second full
    # client distribution: libraries/classpath are already shared read-only.
    with zipfile.ZipFile(PRODUCT) as bundle, zipfile.ZipFile(fixture) as world:
        reserve = PRODUCT.stat().st_size + sum(v.file_size for v in bundle.infolist()) + sum(v.file_size for v in world.infolist()) + 100_000_000
    if occupancy['workspace'] + reserve > 16 * 1024**3 or occupancy['validations'] + reserve > 4 * 1024**3:
        raise RuntimeError('Insufficient owned-client storage reserve')
    probe = Path(frozen_probe).resolve() if frozen_probe is not None else probe_jar()
    if not probe.is_relative_to(ROOT) or not probe.is_file(): raise ValueError('Frozen probe is not a repository artifact')
    case = RESULT / ((group+'-' if group else '')+label + '-' + time.strftime('%Y%m%d-%H%M%S'))
    case.mkdir(parents=True, exist_ok=False)
    CLIENT.mkdir(parents=True)
    with zipfile.ZipFile(fixture) as source:
        for name in source.namelist():
            if name.endswith('/'):
                continue
            category, relative = name.split('/', 1)
            target = CLIENT / 'saves/Newport City' / relative if category == 'world' else CLIENT / relative
            if not target.resolve().is_relative_to(CLIENT.resolve()):
                raise ValueError('Fixture path leaves owned client')
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(source.read(name))
    options = CLIENT / 'options.txt'
    values = options.read_text(encoding='utf8').splitlines()
    values = [f'renderDistance:{distance}' if line.startswith('renderDistance:')
              else 'pauseOnLostFocus:false' if line.startswith('pauseOnLostFocus:') else line for line in values]
    if 'soundCategory_master:0.0' not in values:
        raise RuntimeError('Frozen automatic fixture must be muted')
    options.write_text('\n'.join(values) + '\n', encoding='utf8')
    for marker in ('.radiance-takeover-stages', '.radiance-audit-test-instance'):
        (CLIENT / marker).write_text('Owned vanilla performance diagnosis\n')
    mods = CLIENT / 'mods'
    mods.mkdir()
    shutil.copy2(PRODUCT, mods / 'radiance.jar')
    shutil.copy2(probe, mods / 'radiance-probe.jar')
    cp = json.loads((ROOT / 'build/takeover-stages/packaged-launch-classpath.json').read_text())['files']
    if any('/classes/java/main' in p.replace('\\', '/') or '/classes/java/probe' in p.replace('\\', '/') for p in cp):
        raise RuntimeError('Development classes would replace frozen products')
    (case / 'classpath.args').write_text('-cp\n"' + os.pathsep.join(cp).replace('\\', '\\\\').replace('"', '\\"') + '"\n')
    java = next((Path.home() / '.gradle/jdks').glob('eclipse_adoptium-21*/bin/java.exe'))
    moddev = ROOT / f'versions/{TARGET}/build/moddev'
    agent = ROOT / 'components/render_diagnostics/build/benchmark-support/radiance-benchmark-agent.jar'
    aftermath = ROOT / 'components/render_diagnostics/build/native/aftermath/RelWithDebInfo/radiance-aftermath-agent.dll'
    args = [str(java), f'-javaagent:{agent}', f'-agentpath:{aftermath}=gpu-diagnostics']
    flags = dict(experiments='true', cityComparison='true', correctnessOnly='true', benchmark='true',
                 unattended='true', nativePerformance='true', vanillaPerformance='true', profileControl='true',
                 cityProfilePlan=plan, cityProfileSeconds=str(seconds), cityLabel=label,
                 cityWidth=str(width), cityHeight=str(height), cityBorderless='true')
    args += [f'-Dradiance.audit.{key}={value}' for key, value in flags.items()]
    args += ['-Dradiance.audit.benchmark.version=1.21.1-neoforge', f'-Dvalidation.result={case}',
             f'@{case / "classpath.args"}', f'@{moddev / "packagedClientRunVmArgs.txt"}', '-Xmx8G',
             f'@{moddev / "packagedClientRunProgramArgs.txt"}', '--username', 'RecRivenVI',
             '--width', str(width), '--height', str(height)]
    env = dict(os.environ, MCVR_DEVICE_LOSS_TRACE='1', MCVR_LOCAL_AFTERMATH='1',
               RADIANCE_CHUNK_TRACE='0', RADIANCE_CHUNK_PERF='1')
    env.pop('MOD_CLASSES', None)
    write(case / 'launch.json', dict(args=args, frozenProduct=str(PRODUCT.relative_to(ROOT)),product=digest(PRODUCT),group=group,
          probe=digest(probe), fixture=str(fixture.relative_to(ROOT)), distance=distance,
          width=width, height=height, storage=occupancy, normalPriority=True, affinity='unchanged'))
    stop = threading.Event()
    observer = None
    process = None
    try:
        with (case / 'console.log').open('x', encoding='utf8') as console:
            process = subprocess.Popen(args, cwd=CLIENT, env=env, stdout=console, stderr=subprocess.STDOUT)
            write(case / 'process.json', dict(pid=process.pid, started=time.time()))
            observer = threading.Thread(target=monitor, args=(case, stop, process.pid))
            observer.start()
            until, verified = time.monotonic() + 660, False
            while process.poll() is None and time.monotonic() < until:
                latest = CLIENT / 'logs/latest.log'
                log = latest.read_text(encoding='utf8', errors='replace') if latest.is_file() else ''
                log += (case / 'console.log').read_text(encoding='utf8', errors='replace')
                if 'VK_ERROR_DEVICE_LOST' in log:
                    write(RESULT / 'STOP.json', dict(case=str(case), deviceLost=True, automaticRetry=False))
                    until = min(until, time.monotonic() + 30)
                if not verified and 'CITY_COMPARISON world-ready' in log:
                    loaded_core(process.pid, CLIENT, PRODUCT, case / 'loaded-core.json')
                    verified = True
                stop.wait(1)
            if process.poll() is None:
                raise RuntimeError('Bounded run timed out; retain client, no retry')
        log = (case / 'console.log').read_text(encoding='utf8', errors='replace')
        if process.returncode or not verified or 'CITY_COMPARISON reload-complete normal-stop' not in log or 'All dimensions are saved' not in log or any(
                token in log for token in ('VK_ERROR_DEVICE_LOST', 'CITY_COMPARISON FAIL', 'MCVR fatal')):
            raise RuntimeError('Client failed; preserve evidence and stop group')
        # Retain only raw data used by this diagnosis; generated clients and shader copies retire.
        shutil.copytree(CLIENT / 'logs', case / 'logs')
        windows = analyze(case, width, height, distance)
        for name in ('radiance-device-loss-trace.log', 'radiance-gpu-capabilities.log', 'radiance-as-lifetime-close.log'):
            if (CLIENT / name).is_file():
                shutil.copy2(CLIENT / name, case / name)
        write(case / 'result.json', dict(passed=True, normalSave=True, automaticRetry=False,
              windows={key: value['intervalMs'] for key, value in windows.items()}))
        recycle(CLIENT, VALIDATION)
        print(json.dumps(dict(case=str(case), windows={key: value['intervalMs'] for key, value in windows.items()})), flush=True)
        return case
    except BaseException as error:
        write(RESULT / 'STOP.json', dict(case=str(case), error=str(error), automaticRetry=False,
                                       pid=process.pid if process else None))
        raise
    finally:
        stop.set()
        if observer:
            observer.join(timeout=16)


def report_stage6():
    """Keep ABBA process windows separate; pair means, never pool their unequal frame counts."""
    report = {'resolutions': {}, 'limitations': [
        'Two paired differences per resolution; concurrent process/GPU observations are not process-specific GPU counters.',
        'FRAME_ONLY and TIMED remain separate. Native/Java ranges nest and cannot be added.',
        'The baseline and candidate use the same frozen probe. This is not a long-term stability result.']}
    identities = {'A': set(), 'B': set()}
    probes = set()
    for resolution in (1080, 1440):
        rows = {}
        for label in ('A1', 'B1', 'B2', 'A2'):
            case, = RESULT.glob(f'stage6-final-{label}-r32-{resolution}-*')
            launch = json.loads((case / 'launch.json').read_text())
            result = json.loads((case / 'result.json').read_text())
            if not result['passed']:
                raise RuntimeError('Unsuccessful comparison process: ' + case.name)
            data = json.loads((case / 'analysis.json').read_text())
            if set(data['windows']) != {'FRAME_ONLY', 'TIMED'}:
                raise RuntimeError('Comparison plan differs: ' + case.name)
            probes.add(launch['probe'])
            identities[label[0]].add(launch['product'])
            rows[label] = dict(case=case.name, windows=data['windows'])
        pairs = []
        for a, b in (('A1', 'B1'), ('A2', 'B2')):
            modes = {}
            for mode in ('FRAME_ONLY', 'TIMED'):
                aw, bw = rows[a]['windows'][mode], rows[b]['windows'][mode]
                if aw['sceneState'] != bw['sceneState']:
                    raise RuntimeError('Paired camera/time/weather differs')
                values = {}
                metrics = {'frameMeanMs': (aw['intervalMs']['mean'], bw['intervalMs']['mean']),
                           'cpuMeanMs': (aw['cpuActiveMs']['mean'], bw['cpuActiveMs']['mean']),
                           'gpuMeanMs': (aw['baseGpu']['meanMs'], bw['baseGpu']['meanMs'])}
                if mode == 'TIMED':
                    for key in sorted(set(aw['stagesMs']) | set(bw['stagesMs'])):
                        metrics[key] = (aw['stagesMs'].get(key, {}).get('mean'),
                                        bw['stagesMs'].get(key, {}).get('mean'))
                for key, (av, bv) in metrics.items():
                    values[key] = dict(baseline=av, candidate=bv,
                        candidateMinusBaseline=None if av is None or bv is None else bv-av)
                modes[mode] = values
            pairs.append(dict(baseline=a, candidate=b, modes=modes))
        report['resolutions'][str(resolution)] = dict(order=['A1','B1','B2','A2'], rows=rows, pairs=pairs)
    if len(probes) != 1 or any(len(v) != 1 for v in identities.values()):
        raise RuntimeError('Mixed product or probe identities in final comparison')
    report.update(probe=probes.pop(), products={k: v.pop() for k,v in identities.items()})
    write(RESULT / 'stage6-comparison.json', report)
    print('Final ABBA evidence:', RESULT / 'stage6-comparison.json', flush=True)


def report():
    supplement = json.loads((RESULT / 'gpu-process-supplement.json').read_text())
    own_analysis = json.loads((RESULT / 'analysis-interference.json').read_text())
    cases, probes = [], set()
    for path in sorted(RESULT.glob('*/analysis.json')):
        case = path.parent
        if (case / 'preflight-invalid.json').exists():
            continue
        launch = json.loads((case / 'launch.json').read_text())
        pid = json.loads((case / 'process.json').read_text())['pid']
        probes.add(launch['probe'])
        data = json.loads(path.read_text())
        windows = {}
        for mode, window in data['windows'].items():
            directory = Path(window['directory'])
            meta = dict(line.split('=', 1) for line in (directory / 'METADATA.txt').read_text().splitlines() if '=' in line)
            start = datetime.datetime.fromisoformat(meta['time'].replace('Z', '+00:00'))
            end = start + datetime.timedelta(seconds=window['seconds'])
            concurrent = [p for p in supplement if p['pid'] != pid
                          and datetime.datetime.fromisoformat(p['started']) <= start
                          and datetime.datetime.fromisoformat(p['snapshot']) >= end]
            overlapping = (datetime.datetime.fromisoformat(own_analysis['started']) < end
                           and datetime.datetime.fromisoformat(own_analysis['ended']) > start)
            windows[mode] = dict(intervalMs=window['intervalMs'], wallMs=window['wallMs'],
                cpuActiveMs=window['cpuActiveMs'], baseGpu=window['baseGpu'], noise=window['noise'],
                supplementalConcurrentProcesses=concurrent, ownAnalysisOverlap=overlapping,
                scope='Supplemental process overlap comes from creation time and still-live snapshot; not reconstructed GPU utilization.')
        cases.append(dict(case=case.name, width=launch['width'], height=launch['height'], distance=launch['distance'], windows=windows))
    if len(probes) != 1:
        raise RuntimeError('Valid cases used different probe builds')
    write(RESULT / 'diagnosis-summary.json', dict(cases=cases, probe=probes.pop(),
        productIdentity='documents/project/status-current.md; frozen stage67 delivery',
        invalidPilot='r32-1080-20261020-135054/preflight-invalid.json',
        limitations=['Sequential noisy processes, not a repeated quiet benchmark or an optimization comparison.',
                     'Native/GPU ranges nest; stage wall times are not additional CPU-active durations.']))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('case', choices=('r32-1080', 'r32-1440', 'r24-1080', 'r16-1080', 'report', 'stage6-report'))
    parser.add_argument('--plan', choices=('vanilla', 'throughput', 'takeover'), default='vanilla')
    parser.add_argument('--product',type=Path)
    parser.add_argument('--group')
    parser.add_argument('--seconds',type=int,default=30)
    parser.add_argument('--probe',type=Path)
    args = parser.parse_args()
    if args.case == 'stage6-report':
        report_stage6()
    elif args.case == 'report':
        report()
    else:
        run(args.case,args.plan,args.product,args.group,args.seconds,args.probe)
