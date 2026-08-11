"""128 closeout: a frozen copy, bounded pose observation, and immutable user artifacts."""
from pathlib import Path
import argparse
import json
import os
import subprocess
import time
import zipfile

from takeover_experiment import ROOT, TARGET, digest, preserve, recycle, storage_preflight, write
from takeover_city import loaded_core

VALIDATION = ROOT / 'validations/conformance-takeover'
CLIENT = VALIDATION / 'instance' / TARGET / 'closeout'
RESULT = VALIDATION / 'result/platform-closeout'
BUILD = ROOT / 'components/render_diagnostics/build/platform-128'
USER = VALIDATION / 'instance' / TARGET / 'client'
MANUAL = VALIDATION / 'result/platform-reorganization/manual/prepared.json'


def frozen_inputs():
    archive = BUILD / 'fixture-complete.zip'
    if archive.exists():
        return archive
    # Only read the source after its own normal save and process exit; never launch it.
    log = (USER / 'logs/latest.log').read_text(encoding='utf8', errors='replace')
    if 'All dimensions are saved' not in log or 'Stopping!' not in log:
        raise RuntimeError('Source world has not recorded a normal save/exit')
    processes = subprocess.check_output(['powershell', '-NoProfile', '-Command',
        'Get-CimInstance Win32_Process | Where-Object {$_.Name -eq "java.exe"} | Select-Object -ExpandProperty CommandLine'], text=True)
    if str(MANUAL.parent) in processes:
        raise RuntimeError('User instance is still active; only read logs, retain it')
    paths = [USER / 'options.txt']
    for name in ('saves/Audit Benchmark', 'radiance', 'config',
                 'CustomSkinLoader/caches', 'CustomSkinLoader/LocalSkin', 'CustomSkinLoader/ProfileCache'):
        paths.extend(p for p in (USER / name).rglob('*') if p.is_file()
                     and p.suffix.lower() not in ('.dll', '.bin', '.spv', '.exe'))
    # Runtime SDK/shader copies are not scene/configuration inputs.
    paths = [p for p in paths if 'radiance/dlss/' not in p.relative_to(USER).as_posix()
             and 'radiance/cache/' not in p.relative_to(USER).as_posix()
             and 'radiance/shaders/' not in p.relative_to(USER).as_posix()]
    with zipfile.ZipFile(archive, 'x', zipfile.ZIP_DEFLATED) as output:
        for path in paths:
            output.write(path, path.relative_to(USER).as_posix())
    skin_config = USER / 'CustomSkinLoader/CustomSkinLoader.json'
    if skin_config.is_file():
        with zipfile.ZipFile(archive, 'a', zipfile.ZIP_DEFLATED) as output:
            output.write(skin_config, skin_config.relative_to(USER).as_posix())
    write(BUILD / 'fixture-complete.json', dict(source=str(USER), sha256=digest(archive),
                                      sourceReadOnly=True, normalSaveObserved=True))
    return archive


def run(label, core=None, extra=None, effects_only=False):
    if (RESULT / 'DEVICE_LOST.json').exists():
        raise RuntimeError('Device-loss evidence retained; no automatic retry')
    if CLIENT.exists():
        raise RuntimeError('Prior closeout instance retained; investigate before another run')
    occupancy = storage_preflight(validation_limit_gib=8)
    if occupancy['validations'] + 450_000_000 > 8 * 1024**3 or occupancy['workspace'] + 450_000_000 > 16 * 1024**3:
        raise RuntimeError('Insufficient bounded runtime reserve')
    case = RESULT / (label + '-' + time.strftime('%Y%m%d-%H%M%S'))
    case.mkdir(parents=True, exist_ok=False)
    CLIENT.mkdir(parents=True)
    with zipfile.ZipFile(frozen_inputs()) as archive:
        archive.extractall(CLIENT)
    (CLIENT / '.radiance-platform-closeout').write_text('Owned 128 functional/pose observation\n')
    (CLIENT / '.radiance-audit-test-instance').write_text('Explicit isolated audit instance\n')
    options = CLIENT / 'options.txt'
    overrides = dict(overrideWidth='0', overrideHeight='0', fullscreen='false',
                     soundCategory_master='0.0', soundCategory_music='0.0', pauseOnLostFocus='false')
    rows = [line for line in options.read_text().splitlines() if line.split(':', 1)[0] not in overrides]
    options.write_text('\n'.join(rows + [f'{k}:{v}' for k, v in overrides.items()]) + '\n', encoding='utf8')
    original = json.loads(MANUAL.read_text())
    products = [Path(p['path']) for p in original['products']]
    if core:
        products[0] = core.resolve()
    if extra:
        products[1] = extra.resolve()
    probe = next((ROOT / f'versions/{TARGET}/build/intermediates/probe').glob('Radiance-probe-*.jar'))
    products.append(probe)
    mods = CLIENT / 'mods'
    mods.mkdir()
    for path in products:
        (mods / path.name).symlink_to(path)
    args = list(original['args'])
    args[args.index('--width') + 1] = '1920'
    args[args.index('--height') + 1] = '1080'
    agent = ROOT / 'components/render_diagnostics/build/benchmark-support/radiance-benchmark-agent.jar'
    flags = dict(experiments='true', correctnessOnly='true', unattended='true', benchmark='true',
                 nativePerformance='true', vanillaPerformance='true', profileControl='true',
                 crouchRegression='true', crouchMode='TIMED', crouchWorld='Audit Benchmark',
                 platformCloseout='true',
                 screenSpirv=str(ROOT / 'components/native_renderer/build/runtime/shaders/overlay/post/invert_frag.spv'),
                 cityWidth='1920', cityHeight='1080', cityBorderless='true')
    if effects_only:
        flags['platformEffectsOnly'] = 'true'
    args[1:1] = [f'-javaagent:{agent}', f'-Dvalidation.result={case}',
                 '-Dradiance.audit.benchmark.version=' + TARGET,
                 *[f'-Dradiance.audit.{key}={value}' for key, value in flags.items()]]
    env = dict(os.environ, MCVR_DEVICE_LOSS_TRACE='1', MCVR_LOCAL_AFTERMATH='1',
               RADIANCE_SHARC_AUDIT='1', RADIANCE_CHUNK_TRACE='0', RADIANCE_CHUNK_PERF='1')
    env.pop('MOD_CLASSES', None)
    write(case / 'launch.json', dict(args=args, storage=occupancy, scene=str(BUILD / 'fixture-complete.json'),
          products=[dict(path=str(p), sha256=digest(p)) for p in products],
          purpose='Same-scene standing/crouching diagnosis, not an optimization benchmark'))
    print('Launching owned closeout pose observation:', case, flush=True)
    with (case / 'console.log').open('x', encoding='utf8') as console:
        process = subprocess.Popen(args, cwd=CLIENT, env=env, stdout=console, stderr=subprocess.STDOUT)
        write(case / 'process.json', dict(pid=process.pid))
        deadline = time.monotonic() + 350
        verified = False
        while process.poll() is None and time.monotonic() < deadline:
            log = (case / 'console.log').read_text(encoding='utf8', errors='replace')
            if 'VK_ERROR_DEVICE_LOST' in log:
                write(RESULT / 'DEVICE_LOST.json', dict(case=str(case), automaticRetry=False))
                deadline = min(deadline, time.monotonic() + 30)
            if not verified and 'CROUCH world-ready' in log:
                loaded_core(process.pid, CLIENT, products[0], case / 'loaded-core.json')
                verified = True
            time.sleep(1)
        if process.poll() is None:
            raise RuntimeError('Bounded observation timed out; retain instance, do not restart')
    evidence = preserve(VALIDATION, CLIENT, case.name + '-evidence')
    log = (case / 'console.log').read_text(encoding='utf8', errors='replace')
    if process.returncode or not verified or 'CROUCH PASS normal-stop' not in log or 'All dimensions are saved' not in log:
        write(case / 'failure.json', dict(exit=process.returncode, evidence=str(evidence)))
        raise RuntimeError('Pose observation failed; retain instance and evidence')
    write(case / 'receipt.json', dict(normalSave=True, normalExit=True, evidence=str(evidence),
                                     automaticRetry=False, visualAcceptance='pending'))
    recycle(CLIENT, VALIDATION, marker='.radiance-platform-closeout')
    print('Pose observation complete:', case, flush=True)


def prepare_manual(core, extra):
    if core is None or extra is None:
        raise RuntimeError('Explicit paired candidates required')
    storage_preflight(validation_limit_gib=8)
    client = VALIDATION / 'instance' / TARGET / 'closeout-manual'
    if client.exists():
        raise RuntimeError('Retain the existing user handoff; do not replace it')
    client.mkdir(parents=True)
    with zipfile.ZipFile(frozen_inputs()) as source:
        source.extractall(client)
    options = client / 'options.txt'
    changes = dict(overrideWidth='0', overrideHeight='0', fullscreen='false',
                   soundCategory_master='0.5', soundCategory_music='0.0', pauseOnLostFocus='false')
    rows = [row for row in options.read_text().splitlines() if row.split(':', 1)[0] not in changes]
    options.write_text('\n'.join(rows + [f'{k}:{v}' for k, v in changes.items()]) + '\n', encoding='utf8')
    original = json.loads(MANUAL.read_text())
    products = [Path(row['path']) for row in original['products']]
    products[0], products[1] = core.resolve(), extra.resolve()
    mods = client / 'mods'
    mods.mkdir()
    for product in products:
        (mods / product.name).symlink_to(product)
    args = list(original['args'])
    args[args.index('--width') + 1] = '2560'
    args[args.index('--height') + 1] = '1440'
    write(RESULT / 'manual.json', dict(client=str(client), args=args,
          products=[str(p) for p in products], identity=str(RESULT / 'candidate.json'),
          profile=dict(width=2560, height=1440, masterVolume=.5, musicVolume=0,
                       username='RecRivenVI'), launched=False, probeInstalled=False))


def manual():
    storage_preflight(validation_limit_gib=8)
    prepared = json.loads((RESULT / 'manual.json').read_text())
    candidate = json.loads(Path(prepared['identity']).read_text())
    for product in candidate['products'].values():
        if digest(Path(product['path'])) != product['sha256']:
            raise RuntimeError('Frozen handoff changed; retain the user instance and do not launch')
    env = dict(os.environ, MCVR_DEVICE_LOSS_TRACE='1', MCVR_LOCAL_AFTERMATH='1')
    env.pop('MOD_CLASSES', None)
    subprocess.run(prepared['args'], cwd=prepared['client'], env=env, check=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('label')
    parser.add_argument('--core', type=Path)
    parser.add_argument('--extra', type=Path)
    parser.add_argument('--effects-only', action='store_true')
    args = parser.parse_args()
    if args.label == 'prepare-manual':
        prepare_manual(args.core, args.extra)
    elif args.label == 'manual':
        manual()
    else:
        run(args.label, args.core, args.extra, args.effects_only)
