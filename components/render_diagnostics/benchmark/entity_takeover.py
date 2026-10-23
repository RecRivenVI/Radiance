"""Stage-3 functional source comparisons in a disposable, packaged vanilla fixture."""
from pathlib import Path
import argparse
import json
import os
import shutil
import subprocess
import time
import tomllib
import zipfile

from takeover_experiment import ROOT, TARGET, digest, preserve, probe_jar, recycle, storage_preflight, write
from takeover_city import assert_stopped, loaded_core

VALIDATION = ROOT / 'validations/conformance-entity_takeover'
RESULT = VALIDATION / 'result'
CLIENT = VALIDATION / 'instance' / TARGET / 'client'
TEMPLATES = ROOT / 'validations/conformance-takeover/result'
CONFIG = tomllib.loads((VALIDATION / 'validation.toml').read_text(encoding='utf8'))


def freeze_small_fixture(name='small-fixture.zip'):
    """Save only a normally closed fixture, never copy a world while the server writes it."""
    destination = RESULT / name
    if destination.exists(): return
    entries = []
    with zipfile.ZipFile(destination, 'x', zipfile.ZIP_DEFLATED) as output:
        for source in sorted((CLIENT / 'saves/Audit Benchmark').rglob('*')):
            if source.is_file():
                name = 'world/' + source.relative_to(CLIENT / 'saves/Audit Benchmark').as_posix()
                output.write(source, name)
                entries.append(dict(path=name, sha256=digest(source), bytes=source.stat().st_size))
        with zipfile.ZipFile(TEMPLATES / 'settings-template.zip') as settings:
            for name in settings.namelist():
                data = settings.read(name)
                output.writestr('settings/' + name, data)
                entries.append(dict(path='settings/' + name,
                    sha256=__import__('hashlib').sha256(data).hexdigest(), bytes=len(data)))
    with zipfile.ZipFile(destination) as result:
        for entry in entries:
            if __import__('hashlib').sha256(result.read(entry['path'])).hexdigest() != entry['sha256']:
                raise RuntimeError('Small fixture verification failed; retain client')
    write(destination.with_suffix('.json'), dict(sha256=digest(destination), files=entries,
          scope='Normally saved stage-3 vanilla sources, original small-fixture graphics settings'))


def extract(source, destination):
    with zipfile.ZipFile(source) as z:
        for name in z.namelist():
            output = destination / name
            if not output.resolve().is_relative_to(destination.resolve()):
                raise RuntimeError('Template path leaves owned instance')
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(z.read(name))
        for name in z.namelist():
            if (destination / name).read_bytes() != z.read(name):
                raise RuntimeError('Template extraction differs: ' + name)


def run(label, product_override=None):
    global VALIDATION, CLIENT, RESULT
    if label == 'replacement':
        VALIDATION = ROOT / 'validations/conformance-renderer'
        CLIENT = VALIDATION / 'instance' / TARGET / 'client'
        RESULT = VALIDATION / 'result'
    assert_stopped()
    occupancy = storage_preflight()
    if occupancy['workspace'] + 600_000_000 > 16 * 1024**3:
        raise RuntimeError('Insufficient owned-client storage reserve')
    if occupancy['validations'] + 600_000_000 > 4 * 1024**3:
        raise RuntimeError('Insufficient validation storage reserve')
    if CLIENT.exists(): raise RuntimeError('Prior client retained; inspect before continuing')
    if (RESULT / 'DEVICE_LOST.json').exists(): raise RuntimeError('Device loss stopped this group')
    directory = ROOT / f'versions/{TARGET}/build/libs'
    candidates = [p for p in directory.glob('radiance-*.jar') if not p.name.endswith('-sources.jar')]
    if len(candidates) != 1: raise RuntimeError('Ambiguous packaged release')
    product = candidates[0]
    frozen = ROOT/'components/render_diagnostics/build/takeover-stage3/radiance.jar'
    if frozen.is_file() and label != 'replacement' and product_override is None: product = frozen
    if product_override is not None:
        product=Path(product_override).resolve()
        if not product.is_relative_to(ROOT) or not product.is_file():
            raise ValueError('Candidate is not a repository artifact')
    probe = probe_jar()
    case = RESULT / (label + '-' + time.strftime('%Y%m%d-%H%M%S'))
    case.mkdir(parents=True, exist_ok=False)
    CLIENT.mkdir(parents=True)
    if label != 'replacement':
        extract(TEMPLATES / 'world-template.zip', CLIENT / 'saves')
    extract(TEMPLATES / 'settings-template.zip', CLIENT)
    if label == 'replacement':
        fixture = ROOT / 'validations/conformance-entity_takeover/result/small-vanilla-fixture.zip'
        with zipfile.ZipFile(fixture) as z:
            for name in z.namelist():
                if not name.startswith('world/') or name.endswith('/'): continue
                output = CLIENT / 'saves/Audit Benchmark' / name.removeprefix('world/')
                if not output.resolve().is_relative_to(CLIENT.resolve()): raise ValueError(name)
                output.parent.mkdir(parents=True, exist_ok=True)
                output.write_bytes(z.read(name))
    for marker in ('.radiance-entity-takeover', '.radiance-takeover-stages',
                   '.radiance-audit-test-instance', '.radiance-acceptance'):
        (CLIENT / marker).write_text('Owned entity source fixture\n', encoding='utf8')
    mods = CLIENT / 'mods'; mods.mkdir()
    shutil.copy2(product, mods / 'radiance.jar')
    shutil.copy2(probe, mods / 'radiance-probe.jar')
    mod_inputs = {}
    if label == 'modded':
        local = tomllib.loads((ROOT / 'local.toml').read_text(encoding='utf8'))
        inputs = tomllib.loads((ROOT / 'inputs.toml').read_text(encoding='utf8'))
        for name in CONFIG['compatibility']['mods']:
            source = Path(local['inputs'][name])
            h = digest(source)
            if h not in inputs[name]['sha256']: raise RuntimeError('Local mod input changed: ' + name)
            shutil.copy2(source, mods / source.name)
            mod_inputs[name] = h
    cp = json.loads((ROOT / 'build/takeover-stages/packaged-launch-classpath.json').read_text())['files']
    if any('/classes/java/main' in p.replace('\\', '/') or '/classes/java/probe' in p.replace('\\', '/') for p in cp):
        raise RuntimeError('Development classes would replace release')
    argument = os.pathsep.join(cp).replace('\\', '\\\\').replace('"', '\\"')
    (case / 'classpath.args').write_text('-cp\n"' + argument + '"\n', encoding='utf8')
    java = next((Path.home() / '.gradle/jdks').glob('eclipse_adoptium-21*/bin/java.exe'))
    moddev = ROOT / f'versions/{TARGET}/build/moddev'
    agent = ROOT / 'components/render_diagnostics/build/benchmark-support/radiance-benchmark-agent.jar'
    aftermath = ROOT / 'components/render_diagnostics/build/native/aftermath/RelWithDebInfo/radiance-aftermath-agent.dll'
    args = [str(java), f'-javaagent:{agent}', f'-agentpath:{aftermath}=gpu-diagnostics',
            '-Dradiance.audit.experiments=true', '-Dradiance.audit.entityTakeover=true',
            '-Dradiance.audit.entityFixture=true', '-Dradiance.audit.partVerify=true',
            '-Dradiance.audit.takeoverCompare=true', '-Dradiance.audit.paintingVerify=true',
            '-Dradiance.audit.partVerifyAll=true', '-Dradiance.audit.benchmark=true',
            '-Dradiance.audit.correctnessOnly=true', '-Dradiance.audit.unattended=true',
            '-Dradiance.audit.benchmark.version=' + TARGET,
            '-Dradiance.audit.rendererReplacement=' + str(label in ('replacement','lifecycle')).lower(),
            '-Dradiance.audit.vanillaPerformance=' + str(label in ('replacement','lifecycle')).lower(),
            '-Dradiance.audit.unregisteredSource=' + str(label == 'foreign').lower(),
            '-Dradiance.audit.partLifecycle=' + str(label == 'lifecycle').lower(),
            '-Dradiance.audit.routeCensus=' + str(label == 'modded').lower(),
            '-Dradiance.audit.cityWidth=1920', '-Dradiance.audit.cityHeight=1080',
            '-Dradiance.audit.cityBorderless=true', f'-Dvalidation.result={case}',
            f'@{case / "classpath.args"}', f'@{moddev / "packagedClientRunVmArgs.txt"}',
            '-Xmx4G', f'@{moddev / "packagedClientRunProgramArgs.txt"}',
            '--username', 'RecRivenVI', '--width', '1920', '--height', '1080']
    env = dict(os.environ, MCVR_DEVICE_LOSS_TRACE='1', MCVR_LOCAL_AFTERMATH='1', RADIANCE_CHUNK_TRACE='0')
    env.pop('MOD_CLASSES', None)
    write(case / 'launch.json', dict(args=args, product=digest(product), probe=digest(probe),
          world=digest(TEMPLATES / 'world-template.zip'), settings=digest(TEMPLATES / 'settings-template.zip'),
          storage=occupancy, mods=mod_inputs, performanceMeasured=False))
    process = None
    try:
        with (case / 'console.log').open('x', encoding='utf8') as console:
            process = subprocess.Popen(args, cwd=CLIENT, env=env, stdout=console, stderr=subprocess.STDOUT)
            write(case / 'process.json', dict(pid=process.pid))
            until = time.monotonic() + 450
            verified = False
            while process.poll() is None and time.monotonic() < until:
                log = CLIENT / 'logs/latest.log'
                content = log.read_text(encoding='utf8', errors='replace') if log.is_file() else ''
                if 'VK_ERROR_DEVICE_LOST' in content and not (RESULT / 'DEVICE_LOST.json').exists():
                    write(RESULT / 'DEVICE_LOST.json', dict(case=str(case), pid=process.pid, automaticRetry=False))
                    until = min(until, time.monotonic() + 30)
                if not verified and 'ENTITY_TAKEOVER world-ready' in content:
                    loaded_core(process.pid, CLIENT, product, case / 'loaded-core.json')
                    verified = True
                time.sleep(1)
            if process.poll() is None: raise RuntimeError('Bounded fixture timed out; retain without restart')
            code = process.returncode
        saved = preserve(VALIDATION, CLIENT, case.name + '-evidence')
        log = (CLIENT / 'logs/latest.log').read_text(encoding='utf8', errors='replace')
        if 'VK_ERROR_DEVICE_LOST' in log:
            write(RESULT / 'DEVICE_LOST.json', dict(case=str(case), evidence=str(saved), automaticRetry=False))
        passed = ('PART_LIFECYCLE PASS' in log if label == 'lifecycle'
                  else 'ENTITY_TAKEOVER PASS normal-stop' in log)
        if (code or not verified or not passed
                or 'All dimensions are saved' not in log or 'ENTITY_TAKEOVER FAIL' in log
                or 'PART_LIFECYCLE FAIL' in log
                or 'VK_ERROR_DEVICE_LOST' in log):
            raise RuntimeError('Functional fixture failed; inspect retained evidence: ' + str(case))
        if digest(product) != json.loads((case / 'launch.json').read_text())['product']:
            raise RuntimeError('Release changed during functional test')
        write(case / 'receipt.json', dict(passed=True, exitCode=code, loadedCoreVerified=verified,
              evidence=str(saved), normalSave=True, visualAcceptance='pending'))
        if label == 'modded': freeze_small_fixture()
        if label == 'lifecycle': freeze_small_fixture('small-vanilla-fixture.zip')
        recycle(CLIENT, VALIDATION)
        print('Functional fixture passed:', case, flush=True)
    except BaseException as failure:
        if CLIENT.exists() and not (RESULT / (case.name+'-evidence')).exists():
            preserve(VALIDATION, CLIENT, case.name+'-evidence')
        write(case / 'failure.json', dict(error=str(failure), pid=process.pid if process else None,
                                        automaticRetry=False))
        raise


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('case', choices=('functional', 'foreign', 'modded', 'lifecycle', 'replacement'))
    parser.add_argument('--product',type=Path)
    args=parser.parse_args()
    run(args.case,args.product)
