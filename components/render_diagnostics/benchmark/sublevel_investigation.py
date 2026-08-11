"""129: immutable Prism inputs and an owned large-sublevel/CPU diagnostic case."""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import os
import subprocess
import time
import tomllib
import zipfile

from takeover_experiment import ROOT, TARGET, EXTRA_ROOT, archive, digest, preserve, recycle, storage_preflight, write
from takeover_city import loaded_core

BUILD = ROOT / 'components/render_diagnostics/build/platform-129'
VALIDATION = ROOT / 'validations/conformance-takeover'
RESULT = VALIDATION / 'result/platform-sublevel'
# Native shader file I/O still uses Win32 MAX_PATH; keep this owned client path short.
CLIENT = VALIDATION / 'instance' / TARGET / 's129'
SOURCE = Path('E:/Minecraft/PrismLauncherDev/instances/Radiance 1.21.1-neoforge')
WORLD = 'Newport City'


def powershell(command):
    return subprocess.check_output(['powershell', '-NoProfile', '-NonInteractive', '-Command', command],
                                   text=True, encoding='utf8').strip()


def source_stopped():
    escaped = str(SOURCE).replace("'", "''")
    count = powershell("$p=@(Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^javaw?\\.exe$' "
                       "-and $_.CommandLine -like '*" + escaped + "*' }); $p.Count")
    if count != '0':
        raise RuntimeError('Source Prism client is active; do not copy a changing world')
    log = (SOURCE / 'minecraft/logs/latest.log').read_text(encoding='utf8', errors='replace')
    if log.rfind('Stopping!') < log.rfind('All dimensions are saved') or 'All dimensions are saved' not in log:
        raise RuntimeError('No source normal-save/exit evidence')
    return log


def freeze():
    if (BUILD / 'fixture.json').exists():
        raise RuntimeError('Frozen source already exists; do not replace it')
    occupancy = storage_preflight(validation_limit_gib=8)
    source_stopped()
    lock = SOURCE.parents[1] / '.agent-locks' / (SOURCE.name + '.lock')
    run_id = 'radiance-129-source-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
    now = datetime.datetime.now(datetime.timezone.utc).isoformat()
    owner = dict(agent_id='Radiance GPT', task_id='129 read-only source copy', instance_id=SOURCE.name,
                 run_id=run_id, owner_pid=os.getpid(),
                 owner_creation_time=powershell(f'(Get-Process -Id {os.getpid()}).StartTime.ToUniversalTime().ToString("o")'),
                 acquired_at=now, heartbeat_at=now)
    with lock.open('x', encoding='utf8') as out:
        json.dump(owner, out)
    try:
        log = source_stopped()
        game = SOURCE / 'minecraft'
        registry = tomllib.loads((ROOT / 'inputs.toml').read_text(encoding='utf8'))
        local = tomllib.loads((ROOT / 'local.toml').read_text(encoding='utf8'))
        candidate = json.loads((VALIDATION / 'result/platform-closeout/candidate.json').read_text())
        immutable = {p['sha256']: Path(p['path']) for p in candidate['products'].values()}
        for name in ('create', 'sable', 'aeronautics_bundled', 'customskinloader-bootstrap', 'zume', 'voxy'):
            path = Path(local['inputs'][name])
            identity = digest(path)
            if identity not in registry[name]['sha256']:
                raise RuntimeError('Registered input changed: ' + name)
            immutable[identity] = path
        mods = []
        for path in sorted((game / 'mods').glob('*.jar')):
            identity = digest(path)
            if identity not in immutable:
                raise RuntimeError('Unregistered source mod; freeze identity before use: ' + path.name)
            owned = immutable[identity]
            if digest(owned) != identity:
                raise RuntimeError('Immutable mod input changed: ' + str(owned))
            mods.append(dict(name=path.name, sha256=identity, source=str(path), immutableInput=str(owned)))
        paths = [game / 'options.txt']
        for name in ('saves/' + WORLD, 'config', 'defaultconfigs', 'CustomSkinLoader'):
            directory = game / name
            if directory.exists():
                paths.extend(p for p in directory.rglob('*') if p.is_file())
        paths.extend(p for p in (game / 'radiance').glob('*') if p.is_file())
        paths = sorted(set(paths))
        for path in paths:
            if path.is_symlink() or not path.resolve().is_relative_to(game.resolve()):
                raise RuntimeError('Source input escapes game directory: ' + str(path))
        size = sum(p.stat().st_size for p in paths)
        if occupancy['workspace'] + size > 16 * 1024**3:
            raise RuntimeError('Insufficient workspace reserve for source freeze')
        BUILD.mkdir(parents=True, exist_ok=True)
        RESULT.mkdir(parents=True, exist_ok=True)
        index = []
        archive = BUILD / 'prism-fixture.zip'
        with zipfile.ZipFile(archive, 'x', zipfile.ZIP_DEFLATED, compresslevel=2) as out:
            for path in paths:
                stat = path.stat()
                identity = digest(path)
                name = path.relative_to(game).as_posix()
                out.write(path, name)
                if (path.stat().st_size, path.stat().st_mtime_ns, digest(path)) != (stat.st_size, stat.st_mtime_ns, identity):
                    raise RuntimeError('Source changed during freeze: ' + name)
                index.append(dict(path=name, bytes=stat.st_size, mtime_ns=stat.st_mtime_ns, sha256=identity))
        with zipfile.ZipFile(archive) as check:
            for entry in index:
                if hashlib.sha256(check.read(entry['path'])).hexdigest() != entry['sha256']:
                    raise RuntimeError('Frozen input readback failed')
        source_stopped()
        write(BUILD / 'fixture.json', dict(source=str(SOURCE), world=WORLD, sourceReadOnly=True,
              normalSaveObserved=True, archive=str(archive), sha256=digest(archive), files=index, mods=mods,
              mmcPack=json.loads((SOURCE / 'mmc-pack.json').read_text()), storageBefore=occupancy,
              modReuse='Exact source bytes via immutable registered inputs, never links into Prism'))
        (BUILD / 'source-latest.log').write_text(log, encoding='utf8')
        (BUILD / 'source-instance.cfg').write_bytes((SOURCE / 'instance.cfg').read_bytes())
        write(RESULT / 'source.json', dict(fixture=str(BUILD / 'fixture.json'),
              archiveSha256=digest(archive), sourceReadOnly=True, normalSaveObserved=True))
        print(json.dumps(dict(files=len(index), uncompressedBytes=size, archiveBytes=archive.stat().st_size,
                             mods=len(mods), fixture=str(BUILD / 'fixture.json'))), flush=True)
    finally:
        current = json.loads(lock.read_text())
        if current.get('run_id') != run_id or current.get('owner_pid') != os.getpid():
            raise RuntimeError('Source lock ownership changed; do not release')
        quoted = str(lock).replace("'", "''")
        powershell("Add-Type -AssemblyName Microsoft.VisualBasic; [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile('"
                   + quoted + "', 'OnlyErrorDialogs', 'SendToRecycleBin')")


def validate_completion(snapshots, expected_ready, expected_source=None):
    if expected_ready < 0 or not snapshots:
        raise RuntimeError('Missing/invalid section completion expectation')
    handoffs = snapshots[-1].get('handoffs')
    if handoffs is None or len(handoffs) < 3 or handoffs[2] != 0:
        raise RuntimeError('Missing or unsettled source publication state: ' + repr(handoffs))
    candidates = snapshots[-1].get('clientSublevels', [])
    if expected_source is not None:
        candidates = [row for row in candidates if row.get('uuid') == expected_source]
        if len(candidates) != 1:
            raise RuntimeError('Expected exactly one observed source: ' + expected_source)
    if not candidates:
        raise RuntimeError('No observed sublevel for section completion assertion')
    source = max(candidates, key=lambda row: row.get('sections', -1))
    if (source.get('dirty') != 0 or source.get('registered') != source.get('sections')
            or expected_ready > source.get('sections', -1)
            or source.get('ready') != expected_ready):
        raise RuntimeError('Source did not complete expected content sections: ' + repr(
            {key: source.get(key) for key in ('uuid','sections','registered','dirty','ready')})
            + '; expected-ready=' + str(expected_ready))
    return dict(passed=True, source=source['uuid'], sections=source['sections'], ready=source['ready'],
                emptyWithoutBlas=source['sections']-expected_ready, visualAcceptance='separate')


def run(label, width, height, core_override=None, extra=None, city_view=False, trace=False, frame_only=False,
        expected_ready=None, expected_source=None, ordering_only=False):
    if (RESULT / 'DEVICE_LOST.json').exists():
        raise RuntimeError('Device-loss boundary retained; do not automatically retry')
    if CLIENT.exists():
        raise RuntimeError('Retain the prior case; do not overwrite it')
    occupancy = storage_preflight(validation_limit_gib=8)
    if occupancy['validations'] + 470_000_000 > 8 * 1024**3:
        raise RuntimeError('Insufficient bounded runtime reserve')
    # Soft-limit cleanup happens at admission; active copies must remain below the hard cap.
    if occupancy['workspace'] + 470_000_000 > 25 * 1024**3:
        raise RuntimeError('Insufficient workspace runtime reserve; recycle generated copies first')
    frozen = json.loads((BUILD / 'fixture.json').read_text())
    archive = Path(frozen['archive'])
    if digest(archive) != frozen['sha256']:
        raise RuntimeError('Frozen Prism input changed')
    case = RESULT / (label + '-' + time.strftime('%Y%m%d-%H%M%S'))
    case.mkdir(parents=True, exist_ok=False)
    CLIENT.mkdir(parents=True)
    with zipfile.ZipFile(archive) as source:
        source.extractall(CLIENT)
    for marker in ('.radiance-sublevel-investigation', '.radiance-audit-test-instance'):
        (CLIENT / marker).write_text('Owned 129 copy; no operations on the source Prism instance\n')
    options = CLIENT / 'options.txt'
    overrides = dict(overrideWidth='0', overrideHeight='0', fullscreen='false',
                     soundCategory_master='0.0', soundCategory_music='0.0', pauseOnLostFocus='false')
    rows = [line for line in options.read_text(encoding='utf8').splitlines() if line.split(':', 1)[0] not in overrides]
    options.write_text('\n'.join(rows + [f'{k}:{v}' for k, v in overrides.items()]) + '\n', encoding='utf8')
    products = []
    for entry in frozen['mods']:
        product = Path(entry['immutableInput'])
        if core_override is not None and entry['name'].startswith('radiance-'):
            product = core_override.resolve()
        elif extra is not None and entry['name'].startswith('radiance_extra-'):
            product = extra.resolve()
        elif digest(product) != entry['sha256']:
            raise RuntimeError('Frozen mod identity changed: ' + entry['name'])
        products.append(product)
    core = next(p for p in products if p.name.startswith('radiance-'))
    products.append(next((ROOT / f'versions/{TARGET}/build/intermediates/probe').glob('Radiance-probe-*.jar')))
    # Packaging probe classes is an existing diagnostic operation; the source mod remains separate.
    extra_root = EXTRA_ROOT
    extra_probe = BUILD / 'probes' / (case.name + '.jar')
    extra_probe.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(extra_probe, 'x', zipfile.ZIP_DEFLATED) as output:
        for directory in ('classes/java/probe', 'resources/probe'):
            base = extra_root / f'versions/{TARGET}/build' / directory
            for path in base.rglob('*'):
                if path.is_file(): output.write(path, path.relative_to(base).as_posix())
    products.append(extra_probe)
    mods = CLIENT / 'mods'
    mods.mkdir()
    for product in products:
        (mods / product.name).symlink_to(product)
    runtime = BUILD / 'runtime'
    runtime_identity = json.loads((runtime / 'runtime.json').read_text())
    jars = [Path(p) for p in runtime_identity['files'] if p.endswith('.jar')]
    if any(not p.is_file() for p in jars):
        raise RuntimeError('Source-matched runtime JARs were not prepared')
    if not any('net/minecraft/server/MinecraftServer.class' in zipfile.ZipFile(p).namelist() for p in jars):
        raise RuntimeError('Source-matched runtime lacks Minecraft classes')
    classpath = os.pathsep.join(p for p in runtime_identity['files'] if Path(p).exists())
    (case / 'classpath.args').write_text('-cp\n"' + classpath.replace('\\', '\\\\').replace('"', '\\"') + '"\n', encoding='utf8')
    java = next((Path.home() / '.gradle/jdks').glob('eclipse_adoptium-21*/bin/java.exe'))
    aftermath = ROOT / 'components/render_diagnostics/build/native/aftermath/RelWithDebInfo/radiance-aftermath-agent.dll'
    agent = ROOT / 'components/render_diagnostics/build/benchmark-support/radiance-benchmark-agent.jar'
    flags = dict(experiments='true', unattended='true', correctnessOnly='true', benchmark='true',
                 nativePerformance='true', vanillaPerformance='true', profileControl='true', largeSublevel='true',
                 sublevelCityView=str(city_view).lower(), cityWidth=str(width), cityHeight=str(height),
                 cityBorderless='true')
    if ordering_only:
        flags.update(nativePerformance='false', vanillaPerformance='false', profileControl='false', sectionOrderingOnly='true')
        if expected_ready is not None:
            flags['orderingExpectedReady'] = str(expected_ready)
            if expected_source is not None:
                flags['orderingExpectedSource'] = expected_source
    args = [str(java), '-Xmx16G', '-Xms512M', f'-javaagent:{agent}', f'-agentpath:{aftermath}=gpu-diagnostics',
            '-XX:StartFlightRecording=settings=profile,maxsize=32m,dumponexit=true,filename=' + str(case / 'client.jfr'),
            f'-Dvalidation.result={case}', '-Dradiance.audit.benchmark.version=' + TARGET,
            *[f'-Dradiance.audit.{k}={v}' for k, v in flags.items()],
            '@' + str(case / 'classpath.args'), '@' + str(runtime / 'moddev/diagnosticRunVmArgs.txt'),
            '@' + str(runtime / 'moddev/diagnosticRunProgramArgs.txt'),
            '--username', 'RecRivenVI', '--width', str(width), '--height', str(height)]
    if frame_only or ordering_only:
        args=[arg for arg in args if not arg.startswith('-XX:StartFlightRecording=')]
        args.insert(1,'-Dradiance.audit.defaultProfileMode=FRAME_ONLY')
    env = dict(os.environ, MCVR_DEVICE_LOSS_TRACE='1', MCVR_LOCAL_AFTERMATH='1',
               RADIANCE_CHUNK_TRACE='1' if trace or ordering_only else '0', RADIANCE_CHUNK_PERF='0' if ordering_only else '1', RADIANCE_SHARC_AUDIT='0' if ordering_only else '1')
    if ordering_only:
        env.pop('RADIANCE_CHUNK_PERF', None)
        env.pop('RADIANCE_SHARC_AUDIT', None)
    env.pop('MOD_CLASSES', None)
    write(case / 'launch.json', dict(args=args, storage=occupancy, fixture=str(BUILD / 'fixture.json'),
          fixtureSha256=frozen['sha256'], products=[dict(path=str(p), sha256=digest(p)) for p in products],
          viewport=dict(width=width, height=height), loader=runtime_identity['loader'],
          cameraTurnFromSavedDegrees=180 if city_view else 0,
          qualitySettingsUnchanged=True, purpose='Section ordering correctness only' if ordering_only else 'Large sublevel visibility and CPU/wait attribution'))
    print('Launching owned 129 diagnostic:', case, flush=True)
    with (case / 'console.log').open('x', encoding='utf8') as console:
        process = subprocess.Popen(args, cwd=CLIENT, env=env, stdout=console, stderr=subprocess.STDOUT)
        write(case / 'process.json', dict(pid=process.pid))
        deadline = time.monotonic() + 480
        verified = False
        while process.poll() is None and time.monotonic() < deadline:
            log = (case / 'console.log').read_text(encoding='utf8', errors='replace')
            if 'VK_ERROR_DEVICE_LOST' in log:
                write(RESULT / 'DEVICE_LOST.json', dict(case=str(case), automaticRetry=False))
                deadline = min(deadline, time.monotonic() + 30)
            if not verified and 'LARGE_SUBLEVEL world-ready' in log:
                loaded_core(process.pid, CLIENT, core, case / 'loaded-core.json')
                verified = True
            time.sleep(1)
        if process.poll() is None:
            raise RuntimeError('Bounded case timed out; retain it, do not automatically restart')
    evidence = preserve(VALIDATION, CLIENT, case.name + '-evidence')
    log = (case / 'console.log').read_text(encoding='utf8', errors='replace')
    success = (process.returncode == 0 and verified and 'LARGE_SUBLEVEL observation-complete normal-stop' in log
               and 'All dimensions are saved' in log and not (RESULT / 'DEVICE_LOST.json').exists())
    completion = dict(passed=None, reason='Content assertion not requested; normal exit is not geometry proof')
    if success and expected_ready is not None:
        try:
            completion = validate_completion(
                [json.loads(line) for line in (case/'sublevels.jsonl').read_text().splitlines()],
                expected_ready, expected_source)
        except RuntimeError as failure:
            completion = dict(passed=False, error=str(failure))
            success = False
    write(case / 'content-completion.json', completion)
    write(case / 'receipt.json', dict(success=success, exit=process.returncode, evidence=str(evidence),
          loadedProductVerified=verified, normalSave='All dimensions are saved' in log,
          visualAcceptance='not implied by counters', automaticRetry=False))
    if not success:
        raise RuntimeError('Diagnostic failed; retain the instance and evidence')
    recycle(CLIENT, VALIDATION, marker='.radiance-sublevel-investigation')
    print('Observation preserved:', case, flush=True)


def finalize():
    """Freeze the exact paired deliverable and both dirty source trees without staging either."""
    if (RESULT / 'candidate.json').exists():
        raise RuntimeError('Candidate identity already frozen; do not overwrite it')
    occupancy = storage_preflight(validation_limit_gib=8)
    previous = json.loads((VALIDATION / 'result/platform-closeout/candidate.json').read_text())
    products = {}
    for name, base in (('core', ROOT), ('extra', EXTRA_ROOT)):
        for old in (previous['products'][name],):
            if digest(Path(old['path'])) != old['sha256']:
                raise RuntimeError('Protected 128 product changed: ' + name)
        jars = list((base / 'versions' / TARGET / 'build/candidates/platform-129').glob('*.jar'))
        if len(jars) != 1:
            raise RuntimeError('Expected exactly one 129 product for ' + name)
        products[name] = dict(path=str(jars[0]), sha256=digest(jars[0]))
    cases = [RESULT / name for name in ('baseline-1080-complete-20261025-144740',
             'candidate-1080-20261025-154453', 'candidate-2160-city-20261025-160545')]
    for case in cases:
        if not json.loads((case / 'receipt.json').read_text())['success']:
            raise RuntimeError('Runtime case not successful: ' + str(case))
    with zipfile.ZipFile(products['core']['path']) as jar:
        game = jar.read('META-INF/radiance/Radiance-0.1.5-alpha.jar')
        native = jar.read('core.dll')
    # Main-source delta is checked against the immutable 128 snapshot, not against a dirty HEAD.
    changed = []
    with zipfile.ZipFile(previous['source']['core']['path']) as old:
        for name in old.namelist():
            if '/src/main/' in name or name.startswith('components/native_renderer/src/'):
                path = ROOT / name
                if not path.is_file() or hashlib.sha256(old.read(name)).hexdigest() != digest(path):
                    changed.append(name)
    expected = {'versions/1.21.1-neoforge/src/main/java/com/radiance/client/proxy/world/' + n
                for n in ('ChunkProxy.java', 'ExternalSectionBuildScheduler.java')}
    if set(changed) != expected:
        raise RuntimeError('Unexpected main-source changes since 128: ' + repr(changed))
    sources = {}
    for name, base in (('core', ROOT), ('extra', EXTRA_ROOT)):
        paths = subprocess.check_output(['git', '-C', str(base), 'ls-files', '--cached', '--others',
                                         '--exclude-standard', '-z']).decode('utf8').split('\0')
        files = sorted({base / p for p in paths if p and (base / p).is_file()
                        and not p.startswith('components/native_renderer/third_party/')})
        output = RESULT / ('source-' + name + '.zip')
        if output.exists():
            raise RuntimeError('Source snapshot already exists: ' + str(output))
        index = archive(files, base, output)
        sources[name] = dict(path=str(output), sha256=digest(output), files=len(index))
    write(RESULT / 'candidate.json', dict(created='2026-10-25', apiVersion=2,
          products=products, embeddedGameSha256=hashlib.sha256(game).hexdigest(),
          nativeSha256=hashlib.sha256(native).hexdigest(), source=sources,
          baseHead=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
          submodules=subprocess.check_output(['git', 'submodule', 'status'], cwd=ROOT, text=True).splitlines(),
          preserves128Products=True, mainSourceDeltaFrom128=changed,
          runtimeCases=[str(p) for p in cases], cpuAnalysis=str(cases[-1] / 'analysis.json'),
          inputFixture=str(RESULT / 'source.json'),
          runtimeLoader='21.1.264', runtimeJava='21.0.11',
          coreChecks=str(BUILD / 'candidate-check-release.log'),
          extraChecks=str(BUILD / 'extra-check-release.log'),
          finalRepositoryChecks=str(BUILD / 'final-repository.log'),
          storageBefore=occupancy, storageAfter=storage_preflight(validation_limit_gib=8),
          storagePolicy='User approved persistent 8 GiB validation budget on 2026-10-26',
          visualAcceptance='Structure visible in automatic screenshots; full user acceptance pending',
          prismDeployed=False, committed=False, pushed=False))
    print('Frozen candidate:', RESULT / 'candidate.json')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=['freeze', 'run', 'finalize'])
    parser.add_argument('--label', default='baseline')
    parser.add_argument('--width', type=int, default=1920)
    parser.add_argument('--height', type=int, default=1080)
    parser.add_argument('--extra', type=Path)
    parser.add_argument('--core', type=Path)
    parser.add_argument('--city-view', action='store_true')
    parser.add_argument('--frame-only', action='store_true')
    parser.add_argument('--ordering-only', action='store_true')
    parser.add_argument('--fixture-build', type=Path)
    parser.add_argument('--expected-ready', type=int)
    parser.add_argument('--expected-source')
    args = parser.parse_args()
    if args.fixture_build is not None:
        if args.command != 'run' or not args.fixture_build.resolve().is_relative_to(ROOT / 'components/render_diagnostics/build'):
            raise ValueError('Fixture override must be an owned prepared build used by run')
        BUILD = args.fixture_build.resolve()
    if args.command == 'freeze': freeze()
    elif args.command == 'finalize': finalize()
    else: run(args.label, args.width, args.height, args.core, args.extra, args.city_view, frame_only=args.frame_only,
              expected_ready=args.expected_ready, expected_source=args.expected_source, ordering_only=args.ordering_only)
