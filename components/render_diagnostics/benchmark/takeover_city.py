"""Pinned city comparisons; owns only this validation's disposable client."""
from pathlib import Path
import argparse
import csv
import io
import json
import os
import re
import shutil
import subprocess
import threading
import time
import tomllib
import zipfile

import gpu_monitor
from takeover_experiment import (ROOT, TARGET, archive, digest, preserve, probe_jar,
                                  profile_summary, recycle, storage_preflight, write)

VALIDATION = ROOT / 'validations/performance-entity_takeover'
RESULT = VALIDATION / 'result'
CLIENT = VALIDATION / 'instance' / TARGET / 'client'
OLD = ROOT / 'validations/performance-scene_diagnosis'
INPUTS = OLD / 'result' / 'retained-city-inputs'
FIXTURE = ROOT / 'validations/migration-city_world/result/vanilla-city-fixture.zip'
CONFIG = tomllib.loads((VALIDATION / 'validation.toml').read_text(encoding='utf8'))


def route_summary(rows, frame_adjustment=0):
    frames = {int(r['frames']) for r in rows}
    if len(frames)!=1 or min(frames)<=0: raise RuntimeError('Incomplete route census')
    frames = frames.pop()
    reported_frames = frames
    if frame_adjustment:
        import math
        calls = [int(r['renderer_calls']) for r in rows if int(r['renderer_calls'])>0]
        if frame_adjustment!=1 or math.gcd(*calls)!=frames+1:
            raise RuntimeError('Cannot confirm legacy RETURN-boundary frame correction')
        frames += frame_adjustment
    totals = dict(directDraws=0, capturedDraws=0, legacyPersistentDraws=0,
                  directReferencedVertices=0, capturedGeneratedVertices=0,
                  legacyPersistentReferencedVertices=0, generatedBytes=0)
    sources = dict(directOnly=0, captureOnly=0, mixed=0, noGeometry=0)
    unattributed = 0
    reasons = {}
    detail = []
    for row in rows:
        direct = int(row['direct_source_draws'])
        legacy = int(row['persistent_draws'])-direct
        capture = int(row['geometries'])
        if min(direct,legacy,capture)<0: raise RuntimeError('Inconsistent route draw counts')
        if not int(row['renderer_calls']) and not (direct or legacy or capture): continue
        if row['producer'].startswith(('entity/', 'blockentity/')):
            sources['mixed' if direct and (capture or legacy) else 'directOnly' if direct
                    else 'captureOnly' if capture or legacy else 'noGeometry'] += 1
        else: unattributed += 1
        if capture:
            key = row.get('capture_policies') or 'actual generic capture; policy unavailable'
            reasons[key] = reasons.get(key,0) + capture
        if legacy:
            key = 'legacy recipe capture/cache; not declared source-intent takeover'
            reasons[key] = reasons.get(key,0) + legacy
        values = dict(directDraws=direct, capturedDraws=capture,
                      legacyPersistentDraws=legacy,
                      directReferencedVertices=int(row['direct_source_vertices']),
                      capturedGeneratedVertices=int(row['vertices']),
                      legacyPersistentReferencedVertices=int(row['persistent_vertices'])-int(row['direct_source_vertices']),
                      generatedBytes=int(row['generated_bytes']))
        for key,value in values.items(): totals[key] += value
        detail.append(dict(producer=row['producer'], rendererCallsPerFrame=int(row['renderer_calls'])/frames,
                           capturePolicies=row['capture_policies'].split(';') if row.get('capture_policies') else [],
                           **{k:v/frames for k,v in values.items()}))
    result = dict(frames=frames, reportedFrames=reported_frames, frameAdjustment=frame_adjustment,
                  sourceCounts=sources, unattributedOutputPools=unattributed,
                  captureReasonDrawsPerFrame={k:v/frames for k,v in reasons.items()},
                  perFrame={k:v/frames for k,v in totals.items()}, producers=detail,
                  scope='Separate 3-second counts-only window, not a performance result. '
                  'Draws count actual geometry submissions. Vertices distinguish persistent references '
                  'from actually generated output; legacy recipe caching is not source-intent takeover. '
                  'Zero-output visited sources are separate. No model/baked producer wrappers are woven.')
    return result


def analyze_routes(case, frame_adjustment=0):
    path = case / 'route-census/producers.csv'
    with path.open(encoding='utf8') as stream:
        result = route_summary(list(csv.DictReader(stream)),frame_adjustment)
    write(case/'route-census.json', result)
    return result


def analyze_case(case, saved, width, height):
    windows = json.loads((case / 'analysis.json').read_text(encoding='utf8'))
    with zipfile.ZipFile(saved / 'evidence.zip') as z:
        for name in z.namelist():
            if not name.endswith('/frames.csv'): continue
            parent = name.rsplit('/', 1)[0]
            metadata = dict(row.split('=', 1) for row in
                            z.read(parent + '/METADATA.txt').decode().splitlines() if '=' in row)
            scene = metadata['scene']
            frames = list(csv.DictReader(io.StringIO(z.read(name).decode())))
            seconds = sum(int(row['start_interval_ns']) for row in frames
                          if int(row['start_interval_ns']) > 0) / 1e9
            if not 58 <= seconds <= 62: raise RuntimeError('Not a 60-second city window: ' + scene)
            for row in frames:
                if (row['width'] != str(width) or row['height'] != str(height)
                        or row['render_distance'] != '32' or row['failed'] != 'false'
                        or row['world'] != 'true' or row['sdk_generated_fps'] != '0'):
                    raise RuntimeError('City/frame conditions differ: ' + scene)
            fixed = ('dimension', 'camera_x', 'camera_y', 'camera_z', 'camera_yaw',
                     'camera_pitch', 'day_time', 'rain', 'thunder')
            signatures = {tuple(row[k] for k in fixed) for row in frames}
            if len(signatures) != 1: raise RuntimeError('Camera/weather/time changed: ' + scene)
            status = dict(row.split('=', 1) for row in
                          z.read(parent + '/STATUS.txt').decode().splitlines() if '=' in row)
            for key in ('dropped-writer-batches', 'dropped-native-events',
                        'output-cap-dropped-batches', 'failed-frames', 'java-partition-errors'):
                if status.get(key) != '0': raise RuntimeError('Invalid profile: ' + key)
            stages = list(csv.DictReader(io.StringIO(z.read(parent + '/summary.csv').decode())))
            counters = {}
            frame_ids = {row['frame'] for row in frames}
            unmatched = {}
            for row in csv.DictReader(io.StringIO(z.read(parent + '/counters.csv').decode())):
                stage, frame = row['stage'], row['frame']
                if frame not in frame_ids:
                    unmatched[stage] = unmatched.get(stage, 0) + 1
                    continue
                prior = counters.setdefault(stage, {}).get(frame, (0, 0))
                counters[stage][frame] = (prior[0] + int(row['count']), prior[1] + int(row['bytes']))
            windows[scene].pop('subStages', None)
            windows[scene].update(sceneState=dict(zip(fixed, next(iter(signatures)))),
                observedSeconds=seconds,
                focusedFrames=sum(row['focused'] == 'true' for row in frames),
                focusMetricScope='Minecraft cached windowActive, not OS foreground or GLFW focus',
                screenCounts={screen: sum(row['screen'] == screen for row in frames)
                              for screen in sorted({row['screen'] for row in frames})}, status=status,
                stages=stages, counters={stage: dict(
                    reportedFrames=len(data), windowFrames=len(frames),
                    recordedTotalCount=sum(v[0] for v in data.values()),
                    recordedTotalBytes=sum(v[1] for v in data.values()),
                    eventMeanCountPerCompletedFrame=sum(v[0] for v in data.values()) / len(frames),
                    eventMeanBytesPerCompletedFrame=sum(v[1] for v in data.values()) / len(frames),
                    unmatchedFrameRows=unmatched.get(stage, 0),
                    meanCount=sum(v[0] for v in data.values()) / len(data),
                    peakCount=max(v[0] for v in data.values()),
                    meanBytes=sum(v[1] for v in data.values()) / len(data),
                    peakBytes=max(v[1] for v in data.values())) for stage, data in counters.items()},
                unmatchedCounterRows=unmatched,
                counterNormalization='Event means include completed frames with no event; '
                                     'gauge values use meanCount on reportedFrames, not missing-as-zero.')
        logs = '\n'.join(z.read(n).decode('utf8', errors='replace') for n in z.namelist()
                         if n.endswith('latest.log'))
    vma = [int(value) for value in re.findall(r'vma-bytes=(\d+)', logs)]
    write(case / 'detail.json', dict(windows=windows, resources=dict(
        sampledVmaPeakBytes=max(vma) if vma else None, samples=len(vma),
        scope='1-second allocator observations; not instantaneous peak or whole-card usage'),
        sourceContract=re.findall(r'CITY_COMPARISON source-contract[^\r\n]*', logs),
        routes=analyze_routes(case)))


def report(series):
    order = ['A', 'B', 'B', 'A'] if series == 'initial' else CONFIG['comparison']['order']
    width, height = (2560, 1440) if series == 'final1440' else (1920, 1080)
    runs = []
    for ordinal, label in enumerate(order, 1):
        case = RESULT / series / f'{ordinal}-{label}'
        saved = RESULT / f'{series}-{ordinal}-{label}-evidence'
        analyze_case(case, saved, width, height)
        runs.append(dict(label=label, ordinal=ordinal,
                         launch=json.loads((case / 'launch.json').read_text()),
                         **json.loads((case / 'detail.json').read_text())))
    if len({r['launch']['probe'] for r in runs}) != 1:
        raise RuntimeError('Probe changed between comparison processes')
    if len({r['launch']['fixture'] for r in runs}) != 1:
        raise RuntimeError('City fixture changed between comparison processes')
    signatures = {json.dumps(w['sceneState'], sort_keys=True)
                  for r in runs for w in r['windows'].values()}
    if len(signatures) != 1: raise RuntimeError('Fixed scene differs between windows')
    if any(set(w['screenCounts']) != {'ChatScreen'} for r in runs for w in r['windows'].values()):
        raise RuntimeError('Input-isolation screen differs between comparison windows')
    labels = {}
    for label in set(order):
        values = [w['mean'] for r in runs if r['label'] == label
                  for name, w in r['windows'].items() if name.endswith('FRAME_ONLY')]
        labels[label] = dict(meanMs=sum(values) / len(values),
                             repeatedMeanRangeMs=max(values) - min(values), runs=values)
    write(RESULT / series / 'comparison.json', dict(order=order, labels=labels, runs=runs,
          scope='Observed repeated-run variation, not a confidence interval. Nested CPU/GPU intervals overlap.'))
    print(json.dumps(labels, indent=2), flush=True)


def assert_stopped():
    query = r"""$repo='D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance';
@(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -in @('java.exe','javaw.exe') -and $_.CommandLine -and
    $_.CommandLine.Contains($repo) -and
    $_.CommandLine -match 'BootstrapLauncher|net\.neoforged\.devlaunch\.Main'
} | Select-Object -ExpandProperty ProcessId) | ConvertTo-Json -Compress"""
    p = subprocess.run(['pwsh', '-NoProfile', '-Command', query], capture_output=True,
                       text=True, check=True)
    if p.stdout.strip() not in ('', '[]', 'null'):
        raise RuntimeError('A repository game client is still active: ' + p.stdout)


def prepare():
    assert_stopped()
    if digest(FIXTURE) != json.loads(FIXTURE.with_suffix('.json').read_text())['sha256']:
        raise RuntimeError('Pinned city fixture changed')
    RESULT.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(FIXTURE) as z:
        if z.testzip() is not None:
            raise RuntimeError('Fixture CRC failed')
        write(RESULT / 'fixture.json', dict(path=str(FIXTURE), sha256=digest(FIXTURE),
              files=[dict(path=n, bytes=z.getinfo(n).file_size,
                          sha256=__import__('hashlib').sha256(z.read(n)).hexdigest())
                     for n in z.namelist()]))
    print('Pinned city fixture:', digest(FIXTURE), flush=True)


def products():
    c = CONFIG['comparison']
    result = {}
    for label, name in (('A', 'baseline'), ('B', 'stage2')):
        path = ROOT / c[name]
        if digest(path) != c[name + '-sha256']:
            raise RuntimeError('Pinned artifact changed: ' + label)
        result[label] = path
    final = ROOT / 'components/render_diagnostics/build/takeover-stage3/radiance.jar'
    if final.is_file(): result['C'] = final
    return result


def loaded_core(pid, instance, product, output):
    query = f"(Get-Process -Id {pid}).Modules | Where-Object {{$_.ModuleName -eq 'core.dll'}} | Select-Object ModuleName,FileName | ConvertTo-Json -Compress"
    p = subprocess.run(['pwsh', '-NoProfile', '-Command', query], capture_output=True,
                       text=True, check=True, timeout=15)
    data = json.loads(p.stdout)
    if isinstance(data, list): data = data[0] if len(data) == 1 else None
    if not data: raise RuntimeError('No unique actual loaded core.dll')
    core = Path(data['FileName']).resolve()
    if not core.is_relative_to(instance.resolve()):
        raise RuntimeError('Loaded core is outside owned instance')
    with zipfile.ZipFile(product) as z:
        expected = __import__('hashlib').sha256(z.read('core.dll')).hexdigest()
    if digest(core) != expected: raise RuntimeError('Actual loaded core differs from release')
    write(output, dict(path=str(core), sha256=expected, verified=True))


def run_case(series, ordinal, label, width, height, plan, probe, known_products):
    assert_stopped()
    occupancy = storage_preflight()
    # Retire each generated client after verified evidence; reserve its worst-case copies.
    reserve = 760_000_000
    if occupancy['workspace'] + reserve > 16 * 1024**3 or occupancy['validations'] + reserve > 4 * 1024**3:
        raise RuntimeError('Insufficient storage reserve; no game launched')
    if CLIENT.exists(): raise RuntimeError('Previous client retained; inspect before continuing')
    if (RESULT / 'STOP.json').exists(): raise RuntimeError('Prior failure stopped this experiment group')
    case = RESULT / series / f'{ordinal}-{label}'
    case.mkdir(parents=True, exist_ok=False)
    CLIENT.mkdir(parents=True)
    with zipfile.ZipFile(FIXTURE) as z:
        for n in z.namelist():
            category, relative = n.split('/', 1)
            target = (CLIENT / 'saves/Newport City' / relative if category == 'world'
                      else CLIENT / relative)
            if not target.resolve().is_relative_to(CLIENT.resolve()):
                raise RuntimeError('Fixture path leaves client')
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(z.read(n))
        for n in z.namelist():
            category, relative = n.split('/', 1)
            target = CLIENT / 'saves/Newport City' / relative if category == 'world' else CLIENT / relative
            if target.read_bytes() != z.read(n): raise RuntimeError('Restored fixture mismatch: ' + n)
    options = (CLIENT / 'options.txt').read_text(encoding='utf8')
    if 'soundCategory_master:0.0' not in options: raise RuntimeError('Fixture is not muted')
    for name in ('.radiance-takeover-stages', '.radiance-audit-test-instance', '.radiance-acceptance'):
        (CLIENT / name).write_text('Owned stage 3 city comparison\n', encoding='utf8')
    mods = CLIENT / 'mods'; mods.mkdir()
    product = known_products[label]
    shutil.copy2(product, mods / 'radiance.jar')
    shutil.copy2(probe, mods / 'radiance-probe.jar')
    local = tomllib.loads((ROOT / 'local.toml').read_text(encoding='utf8'))
    inputs = tomllib.loads((ROOT / 'inputs.toml').read_text(encoding='utf8'))
    mod_inputs = {}
    for name in CONFIG['instances']['client']['mods']:
        source = Path(local['inputs'][name])
        h = digest(source)
        if h not in inputs[name]['sha256']: raise RuntimeError('Local mod input changed: ' + name)
        shutil.copy2(source, mods / source.name); mod_inputs[name] = h
    cp = json.loads((ROOT / 'build/takeover-stages/packaged-launch-classpath.json').read_text())['files']
    if any('/classes/java/main' in p.replace('\\', '/') or '/classes/java/probe' in p.replace('\\', '/') for p in cp):
        raise RuntimeError('Development classes would replace pinned products')
    classpath = os.pathsep.join(cp).replace('\\', '\\\\').replace('"', '\\"')
    (case / 'classpath.args').write_text('-cp\n"' + classpath + '"\n', encoding='utf8')
    java = next((Path.home() / '.gradle/jdks').glob('eclipse_adoptium-21*/bin/java.exe'))
    moddev = ROOT / f'versions/{TARGET}/build/moddev'
    agent = ROOT / 'components/render_diagnostics/build/benchmark-support/radiance-benchmark-agent.jar'
    aftermath = ROOT / 'components/render_diagnostics/build/native/aftermath/RelWithDebInfo/radiance-aftermath-agent.dll'
    args = [str(java), f'-javaagent:{agent}', f'-agentpath:{aftermath}=gpu-diagnostics',
            '-Dradiance.audit.experiments=true', '-Dradiance.audit.entityTakeover=true',
            '-Dradiance.audit.cityComparison=true', '-Dradiance.audit.correctnessOnly=true',
            '-Dradiance.audit.benchmark=true', '-Dradiance.audit.unattended=true',
            '-Dradiance.audit.benchmark.version=1.21.1-neoforge',
            '-Dradiance.audit.nativePerformance=true',
            '-Dradiance.rigidParts=' + ('true' if label == 'C' else 'false'),
            f'-Dradiance.audit.cityProfilePlan={plan}',
            '-Dradiance.audit.cityProfileSeconds=' + ('20' if series == 'small' else '60'),
            f'-Dradiance.audit.cityLabel=stage3-{series}-{ordinal}-{label}',
            f'-Dradiance.audit.cityWidth={width}', f'-Dradiance.audit.cityHeight={height}',
            '-Dradiance.audit.cityBorderless=true', f'-Dvalidation.result={case}',
            f'@{case / "classpath.args"}', f'@{moddev / "packagedClientRunVmArgs.txt"}',
            '-Xmx8G', f'@{moddev / "packagedClientRunProgramArgs.txt"}',
            '--username', 'RecRivenVI', '--width', str(width), '--height', str(height)]
    env = dict(os.environ, MCVR_DEVICE_LOSS_TRACE='1', MCVR_LOCAL_AFTERMATH='1',
               RADIANCE_CHUNK_TRACE='0', RADIANCE_CHUNK_PERF='1')
    env.pop('MOD_CLASSES', None)
    started = time.time()
    write(case / 'launch.json', dict(label=label, product=digest(product), probe=digest(probe),
          fixture=digest(FIXTURE), mods=mod_inputs, width=width, height=height, args=args,
          storage=occupancy, started=started))
    observed = subprocess.run(['nvidia-smi', '--query-gpu=driver_version,memory.used,utilization.gpu',
                               '--format=csv'], capture_output=True, text=True, timeout=15)
    (case / 'gpu-before.txt').write_text(observed.stdout, encoding='utf8')
    stop = threading.Event()
    def monitor():
        reader = None
        try:
            reader = gpu_monitor.Reader()
            with (case / 'gpu.csv').open('x', newline='') as f:
                gpu_monitor.collect(reader, f, stop.is_set, 1000)
        except Exception as e: write(case / 'gpu-monitor-error.json', dict(error=str(e)))
        finally:
            if reader: reader.close()
    thread = threading.Thread(target=monitor); thread.start()
    process = None
    try:
        with (case / 'console.log').open('x', encoding='utf8') as console:
            process = subprocess.Popen(args, cwd=CLIENT, env=env, stdout=console, stderr=subprocess.STDOUT)
            write(case / 'process.json', dict(pid=process.pid, started=started))
            until = time.monotonic() + 660
            verified = False
            while process.poll() is None and time.monotonic() < until:
                log = CLIENT / 'logs/latest.log'
                if not verified and log.is_file() and 'CITY_COMPARISON world-ready' in log.read_text(encoding='utf8', errors='replace'):
                    loaded_core(process.pid, CLIENT, product, case / 'loaded-core.json')
                    verified = True
                stop.wait(1)
            if process.poll() is None: raise RuntimeError('Bounded city process timed out; retain without restart')
            code = process.returncode
        saved = preserve(VALIDATION, CLIENT, series + '-' + str(ordinal) + '-' + label + '-evidence')
        log = (CLIENT / 'logs/latest.log').read_text(encoding='utf8', errors='replace') + (case / 'console.log').read_text(encoding='utf8', errors='replace')
        faults = [s for s in ('VK_ERROR_DEVICE_LOST', 'MCVR fatal', 'Crashed!', 'CITY_COMPARISON FAIL') if s in log]
        if code or faults or not verified or 'All dimensions are saved' not in log or 'CITY_COMPARISON reload-complete normal-stop' not in log:
            raise RuntimeError('City process failed: ' + repr(dict(code=code, faults=faults, verified=verified)))
        count = 2 if plan == 'takeover' else 1
        with zipfile.ZipFile(saved / 'evidence.zip') as z:
            summary = profile_summary(z, count, prefix='stage3-')
        write(case / 'analysis.json', summary)
        if plan == 'takeover' and series != 'small': analyze_case(case, saved, width, height)
        if digest(product) != json.loads((case / 'launch.json').read_text())['product']:
            raise RuntimeError('Frozen artifact changed during process')
        recycle(CLIENT, VALIDATION)
        print(series, ordinal, label, {k:round(v['mean'], 3) for k,v in summary.items()}, flush=True)
    except BaseException as e:
        write(RESULT / 'STOP.json', dict(case=str(case), error=str(e), pid=process.pid if process else None,
                                       automaticRetry=False))
        raise
    finally:
        stop.set(); thread.join(timeout=5)


def main():
    global FIXTURE
    parser = argparse.ArgumentParser()
    parser.add_argument('series', choices=('prepare', 'initial', 'producers', 'small', 'report-small', 'final1080', 'final1440',
                                         'clean1080', 'report-clean1080', 'final1080r2', 'report-final1080r2',
                                         'report-initial', 'report-final1080', 'report-final1440'))
    series = parser.parse_args().series
    if series == 'report-small':
        runs = []
        for ordinal, label in enumerate(('B', 'C', 'C', 'B'), 1):
            case = RESULT / 'small' / f'{ordinal}-{label}'
            runs.append(dict(label=label, launch=json.loads((case/'launch.json').read_text()),
                             windows=json.loads((case/'analysis.json').read_text())))
        if len({r['launch']['probe'] for r in runs}) != 1:
            raise RuntimeError('Small comparison probe changed')
        write(RESULT/'small/comparison.json', dict(order=['B','C','C','B'],runs=runs,
              scope='Limited small-fixture samples, not city or visual acceptance'))
        return
    if series.startswith('report-'): report(series.removeprefix('report-')); return
    if series == 'prepare': prepare(); return
    if series == 'small':
        FIXTURE = ROOT / 'validations/conformance-entity_takeover/result/small-vanilla-fixture.zip'
        if digest(FIXTURE) != json.loads(FIXTURE.with_suffix('.json').read_text())['sha256']:
            raise RuntimeError('Small fixture ZIP differs')
    else:
        if digest(FIXTURE) != json.loads(FIXTURE.with_suffix('.json').read_text())['sha256']: raise RuntimeError('City ZIP differs')
        integrity = json.loads((FIXTURE.parent/'frozen-integrity.json').read_text())
        if not integrity['passed'] or integrity['sha256']!=digest(FIXTURE):
            raise RuntimeError('Original-height city integrity not verified')
    known = products()
    order = (['A', 'B', 'B', 'A'] if series == 'initial' else ['B'] if series == 'producers'
             else ['B', 'C', 'C', 'B'] if series == 'small' else CONFIG['comparison']['order'])
    if any(label not in known for label in order): raise RuntimeError('Final C product is not frozen')
    probe = probe_jar()
    width, height = (2560, 1440) if series == 'final1440' else (1920, 1080)
    plan = 'producers' if series == 'producers' else 'takeover'
    for i, label in enumerate(order, 1): run_case(series, i, label, width, height, plan, probe, known)


if __name__ == '__main__': main()
