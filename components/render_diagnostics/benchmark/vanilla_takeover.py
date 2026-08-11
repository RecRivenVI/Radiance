"""Pure-vanilla drawing census and stage-6/7 correctness; never measures performance."""
from pathlib import Path
import argparse
import csv
import json
import os
import shutil
import subprocess
import time
import zipfile

from takeover_experiment import ROOT, TARGET, archive, digest, preserve, probe_jar, recycle, storage_preflight, write
from takeover_city import assert_stopped, loaded_core
from entity_takeover import extract
from takeover_acceptance import stage67_fixture

VALIDATION = ROOT / 'validations/conformance-vanilla'
CLIENT = VALIDATION / 'instance' / TARGET / 'client'
RESULT = VALIDATION / 'result'


def run(label, product_override=None, capture=False):
    assert_stopped()
    occupancy = storage_preflight()
    if occupancy['workspace'] + 700_000_000 > 16 * 1024**3:
        raise RuntimeError('Insufficient owned-instance reserve')
    if CLIENT.exists(): raise RuntimeError('Prior client retained; inspect before another run')
    if (RESULT / 'DEVICE_LOST.json').exists(): raise RuntimeError('Device loss stopped this group')
    if product_override is None:
        product, = [p for p in (ROOT / f'versions/{TARGET}/build/libs').glob('radiance-*.jar') if not p.name.endswith('-sources.jar')]
    else:
        product = Path(product_override).resolve()
        if not product.is_relative_to(ROOT) or not product.is_file():
            raise ValueError('Use a frozen repository artifact')
    probe = probe_jar()
    case = RESULT / (label + '-' + time.strftime('%Y%m%d-%H%M%S'))
    case.mkdir(parents=True, exist_ok=False)
    CLIENT.mkdir(parents=True)
    templates = ROOT / 'validations/conformance-takeover/result'
    extract(templates / 'settings-template.zip', CLIENT)
    if label == 'gui-policy-native':
        # The unattended RGB/lifecycle pass does not attempt generated-frame acceptance.
        options = CLIENT / 'radiance/options.properties'
        lines = options.read_text(encoding='utf8').splitlines() if options.exists() else []
        lines = [line for line in lines if not line.startswith('dlssFrameGeneration=')]
        options.parent.mkdir(parents=True, exist_ok=True)
        options.write_text('\n'.join([*lines, 'dlssFrameGeneration=false'])+'\n', encoding='utf8')
    fixtures = {}
    if label == 'full':
        for world, fixture in {
            'Audit Benchmark': ROOT / 'validations/conformance-entity_takeover/result/small-vanilla-fixture.zip',
            'Newport City': ROOT / 'validations/migration-city_world/result/vanilla-city-fixture.zip',
        }.items():
            expected = json.loads(fixture.with_suffix('.json').read_text())['sha256']
            if digest(fixture) != expected: raise RuntimeError('Fixture identity differs: ' + world)
            with zipfile.ZipFile(fixture) as z:
                for name in z.namelist():
                    if not name.startswith('world/') or name.endswith('/'): continue
                    target = CLIENT / 'saves' / world / name.removeprefix('world/')
                    if not target.resolve().is_relative_to(CLIENT.resolve()): raise ValueError('Unsafe fixture path')
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(z.read(name))
            fixtures[world] = expected
    else:
        extract(templates / 'world-template.zip', CLIENT / 'saves')
        fixtures['Audit Benchmark'] = digest(templates / 'world-template.zip')
    controls = stage67_fixture(CLIENT/'saves/Audit Benchmark') if label == 'full' else {}
    (CLIENT / '.radiance-takeover-stages').write_text('Owned vanilla request census\n')
    (CLIENT / '.radiance-audit-test-instance').write_text('Owned vanilla request census\n')
    mods = CLIENT / 'mods'; mods.mkdir()
    shutil.copy2(product, mods / 'radiance.jar')
    shutil.copy2(probe, mods / 'radiance-probe.jar')
    product = mods / 'radiance.jar'
    cp = json.loads((ROOT / 'build/takeover-stages/packaged-launch-classpath.json').read_text())['files']
    if any('/classes/java/main' in p.replace('\\', '/') or '/classes/java/probe' in p.replace('\\', '/') for p in cp):
        raise RuntimeError('Development classes would replace the release')
    (case / 'classpath.args').write_text('-cp\n"' + os.pathsep.join(cp).replace('\\', '\\\\').replace('"', '\\"') + '"\n')
    java = next((Path.home() / '.gradle/jdks').glob('eclipse_adoptium-21*/bin/java.exe'))
    agent = ROOT / 'components/render_diagnostics/build/benchmark-support/radiance-benchmark-agent.jar'
    aftermath = ROOT / 'components/render_diagnostics/build/native/aftermath/RelWithDebInfo/radiance-aftermath-agent.dll'
    moddev = ROOT / f'versions/{TARGET}/build/moddev'
    args = [str(java), f'-javaagent:{agent}', f'-agentpath:{aftermath}=gpu-diagnostics',
            '-Dradiance.audit.experiments=true', '-Dradiance.audit.drawingCensus=true',
            '-Dradiance.audit.vanillaRoute=' + str(label in ('procedural', 'full')).lower(),
            '-Dradiance.audit.particleVerify=' + str(label in ('procedural', 'full')).lower(),
            '-Dradiance.audit.fullCensus=' + str(label == 'full').lower(),
            '-Dradiance.audit.screenEffects=' + str(label == 'effects').lower(),
            '-Dradiance.audit.guiReview=' + str(label.startswith('gui-')).lower(),
            '-Dradiance.audit.guiPolicy=' + str(label.startswith('gui-policy')).lower(),
            '-Dradiance.audit.guiNativeOnly=' + str(label == 'gui-policy-native').lower(),
            '-Dradiance.audit.capture=' + str(capture).lower(),
            '-Dradiance.audit.correctnessOnly=true', '-Dradiance.audit.unattended=true',
            '-Dradiance.audit.cityWidth=1920', '-Dradiance.audit.cityHeight=1080',
            '-Dradiance.audit.cityBorderless=true', f'-Dvalidation.result={case}',
            f'@{case / "classpath.args"}', f'@{moddev / "packagedClientRunVmArgs.txt"}',
            '-Xmx4G', f'@{moddev / "packagedClientRunProgramArgs.txt"}',
            '--username', 'RecRivenVI', '--width', '1920', '--height', '1080']
    env = dict(os.environ, MCVR_DEVICE_LOSS_TRACE='1', MCVR_LOCAL_AFTERMATH='1', RADIANCE_CHUNK_TRACE='0')
    env.pop('MOD_CLASSES', None)
    write(case / 'launch.json', dict(args=args, product=digest(product), probe=digest(probe),
          worlds=fixtures, settings=digest(templates / 'settings-template.zip'),
          storage=occupancy, proceduralFixture=controls, performanceMeasured=False, mods=['radiance', 'radiance_probe']))
    process = None
    try:
        with (case / 'console.log').open('x', encoding='utf8') as console:
            process = subprocess.Popen(args, cwd=CLIENT, env=env, stdout=console, stderr=subprocess.STDOUT,
                                       creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
            subprocess.run(['pwsh', '-NoProfile', '-Command',
                            f'[Diagnostics.Process]::GetProcessById({process.pid}).ProcessorAffinity = 255'], check=True)
            until = time.monotonic() + (900 if label == 'full' else 360)
            verified = False
            while process.poll() is None and time.monotonic() < until:
                log = CLIENT / 'logs/latest.log'
                content = log.read_text(encoding='utf8', errors='replace') if log.is_file() else ''
                content += (case / 'console.log').read_text(encoding='utf8', errors='replace')
                if 'VK_ERROR_DEVICE_LOST' in content and not (RESULT / 'DEVICE_LOST.json').exists():
                    write(RESULT / 'DEVICE_LOST.json', dict(case=str(case), pid=process.pid, automaticRetry=False))
                    until = min(until, time.monotonic() + 30)
                if not verified and 'DRAWING_CENSUS world-ready' in content:
                    loaded_core(process.pid, CLIENT, product, case / 'loaded-core.json')
                    verified = True
                time.sleep(1)
            if process.poll() is None: raise RuntimeError('Bounded correctness run timed out; no retry')
        log = (CLIENT / 'logs/latest.log').read_text(encoding='utf8', errors='replace') + (case / 'console.log').read_text(encoding='utf8', errors='replace')
        passed = (process.returncode == 0 and verified and 'DRAWING_CENSUS BASELINE_COMPLETE' in log
                  and 'DRAWING_CENSUS FAIL' not in log and 'VK_ERROR_DEVICE_LOST' not in log
                  and 'All dimensions are saved' in log
                  and (label != 'effects' or 'VANILLA_SCREEN_FIXTURE PASS' in log)
                  and (not label.startswith('gui-') or 'GUI_REVIEW PASS' in log)
                  and (label != 'full' or 'STAGE67_FIXTURE_READY' in log))
        rows = list(csv.DictReader((case / 'drawing-census-baseline.csv').open())) if passed else []
        totals = {key: sum(int(r[key]) for r in rows) for key in ('requests', 'consumed', 'unowned', 'duplicate', 'unknown')}
        write(case / 'receipt.json', dict(passed=passed, exitCode=process.returncode, loadedCoreVerified=verified,
              totals=totals, performanceMeasured=False, scope={'full': 'R0-R5/R7 fixed vanilla correctness route',
              'effects': 'Three entity camera post effects and block overlay supplement'}.get(label,
              'GUI composition slice; not generated-frame acceptance' if label.startswith('gui-') else
              'Particle and raster correctness slice; not complete R0-R7 coverage')))
        if not passed:
            preserve(VALIDATION, CLIENT, case.name + '-failure')
            raise RuntimeError('Census failed; evidence retained: ' + str(case))
        if label.startswith('gui-'):
            capture = [p for p in (CLIENT / 'radiance-fg-captures').rglob('*') if p.is_file()]
            if capture:
                archive(capture, CLIENT, case / 'fg-inputs.zip')
            preserve(VALIDATION, CLIENT, case.name + '-evidence')
        recycle(CLIENT, VALIDATION)
        print('Census baseline:', case, totals, flush=True)
    except BaseException as failure:
        write(case / 'failure.json', dict(error=str(failure), pid=process.pid if process else None, automaticRetry=False))
        raise


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('case')
    parser.add_argument('--product',type=Path)
    parser.add_argument('--capture',action='store_true')
    args=parser.parse_args()
    run(args.case,args.product,args.capture)
