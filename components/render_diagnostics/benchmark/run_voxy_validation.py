"""One owned Voxy correctness session. No performance sampling, desktop input or automatic retry."""
from pathlib import Path
import argparse
import json
import os
import subprocess
import time
import tomllib
import zipfile
from takeover_experiment import ROOT, TARGET, WORKSPACE_SOFT_LIMIT_GIB, WORKSPACE_HARD_LIMIT_GIB, digest, preserve, recycle, storage_preflight, write
from takeover_city import loaded_core
from platform_integration import extra_probe
from prepare_voxy_validation import main as prepare

VALIDATION = ROOT / 'validations/regression-voxy_integration'
CLIENT = VALIDATION / 'instance' / TARGET / 'client'
GROUP = VALIDATION / 'result/water-history-20261029'


def run(core, extra, workspace_limit=WORKSPACE_SOFT_LIMIT_GIB):
    occupancy = storage_preflight(workspace_limit_gib=workspace_limit)
    if occupancy['workspace'] + 450_000_000 > WORKSPACE_HARD_LIMIT_GIB * 1024**3 or occupancy['validations'] + 450_000_000 > 8 * 1024**3:
        raise RuntimeError('Insufficient bounded-client reserve; retain unique evidence')
    if CLIENT.exists():
        raise RuntimeError('Existing Voxy instance is retained; inspect before another launch')
    if list(GROUP.glob('*/DEVICE_LOST.json')):
        raise RuntimeError('Device loss stopped this group; no automatic retry')
    case = GROUP / time.strftime('functional-%Y%m%d-%H%M%S')
    case.mkdir(parents=True, exist_ok=False)
    local = tomllib.loads((ROOT / 'local.toml').read_text(encoding='utf8'))
    registry = tomllib.loads((ROOT / 'inputs.toml').read_text(encoding='utf8'))
    source = Path(local['inputs']['voxy'])
    if digest(source) not in registry['voxy']['sha256']:
        raise RuntimeError('Source Voxy build changed')
    probe = next((ROOT / f'versions/{TARGET}/build/intermediates/probe').glob('Radiance-probe*.jar'))
    jars = [core.resolve(), extra.resolve(), probe, extra_probe(), source]
    def packages(path):
        with zipfile.ZipFile(path) as jar:
            return {name.rsplit('/', 1)[0] for name in jar.namelist()
                    if name.endswith('.class') and '/' in name and not name.startswith('META-INF/')}
    if packages(jars[1]) & packages(jars[3]):
        raise RuntimeError('Extra probe and product have split module packages')
    cp = json.loads((ROOT / 'build/takeover-stages/packaged-launch-classpath.json').read_text())['files']
    # The installed outer product already owns this resource-only component. It has no Java classes.
    # Never require an exploded build JAR in addition to the exact distributed product under test.
    native_component=(ROOT/'components/native_renderer/build/libs/native_renderer.jar').resolve()
    cp=[p for p in cp if Path(p).resolve()!=native_component]
    if any('/classes/java/main' in p.replace('\\', '/') or '/classes/java/probe' in p.replace('\\', '/') for p in cp):
        raise RuntimeError('Development classes shadow packaged products')
    for path in cp:
        if Path(path).suffix == ".jar" and not Path(path).is_file():
            raise FileNotFoundError(path)
    prepare()
    mods = CLIENT / 'mods'
    mods.mkdir()
    for path in jars:
        if not path.is_file():
            raise FileNotFoundError(path)
        (mods / path.name).symlink_to(path)
    argument = os.pathsep.join(cp).replace('\\', '\\\\').replace('"', '\\"')
    (case / 'classpath.args').write_text('-cp\n"' + argument + '"\n', encoding='utf8')
    java = next((Path.home() / '.gradle/jdks').glob('eclipse_adoptium-21*/bin/java.exe'))
    moddev = ROOT / f'versions/{TARGET}/build/moddev'
    agent = ROOT / 'components/render_diagnostics/build/benchmark-support/radiance-benchmark-agent.jar'
    aftermath = ROOT / 'components/render_diagnostics/build/native/aftermath/RelWithDebInfo/radiance-aftermath-agent.dll'
    args = [str(java), f'-javaagent:{agent}', f'-agentpath:{aftermath}=gpu-diagnostics',
            '-Dradiance.audit.experiments=true', '-Dradiance.audit.voxy=true', '-Dradiance.audit.voxy.motion=true',
            '-Dradiance.audit.benchmark=true', '-Dradiance.audit.correctnessOnly=true',
            '-Dradiance.audit.unattended=true', '-Dradiance.audit.benchmark.version=' + TARGET,
            f'-Dvalidation.result={case}', f'@{case / "classpath.args"}',
            f'@{moddev / "packagedClientRunVmArgs.txt"}', '-Xmx4G',
            f'@{moddev / "packagedClientRunProgramArgs.txt"}', '--username', 'RecRivenVI',
            '--width', '1920', '--height', '1080', '--quickPlaySingleplayer', 'Audit Benchmark']
    env = dict(os.environ, MCVR_DEVICE_LOSS_TRACE='1', MCVR_LOCAL_AFTERMATH='1', RADIANCE_CHUNK_TRACE='0', MCVR_LOD_HISTORY_VERIFY='1')
    env.pop('MOD_CLASSES', None)
    write(case / 'launch.json', dict(args=args, storage=occupancy,
          mods=[dict(path=str(p), sha256=digest(p)) for p in jars], performanceMeasured=False))
    verified = False
    lost = False
    with (case / 'console.log').open('x', encoding='utf8') as out:
        process = subprocess.Popen(args, cwd=CLIENT, env=env, stdout=out, stderr=subprocess.STDOUT)
        write(case / 'process.json', dict(pid=process.pid))
        deadline = time.monotonic() + 900
        while process.poll() is None and time.monotonic() < deadline:
            path = CLIENT / 'logs/latest.log'
            log = path.read_text(encoding='utf8', errors='replace') if path.is_file() else ''
            if not lost and ('VK_ERROR_DEVICE_LOST' in log or 'VkResult=-4' in log):
                lost = True
                write(case / 'DEVICE_LOST.json', dict(pid=process.pid, automaticRetry=False))
                deadline = min(deadline, time.monotonic() + 30)
            if not verified and 'VOXY_WORLD ready' in log:
                loaded_core(process.pid, CLIENT, core, case / 'loaded-core.json')
                verified = True
            time.sleep(1)
        if process.poll() is None:
            # This is only the exact process launched above, with repository paths in its command.
            process.terminate()
            process.wait(timeout=30)
    evidence = preserve(VALIDATION, CLIENT, case.name + '-evidence')
    log = (CLIENT / 'logs/latest.log').read_text(encoding='utf8', errors='replace')
    # The process can exit between polls; the final log remains authoritative.
    lost = lost or 'VK_ERROR_DEVICE_LOST' in log or 'VkResult=-4' in log
    if lost:
        write(case / 'DEVICE_LOST.json', dict(pid=process.pid, automaticRetry=False,
                                             finalLogChecked=True))
    report_path = case / 'voxy-functional.json'
    passed = process.returncode == 0 and verified and not lost and report_path.is_file()
    if passed:
        report = json.loads(report_path.read_text(encoding='utf8'))
        passed = (report.get('runtimeStagesPassed') and len(report.get('stages', [])) == 7
                  and report.get('cameraMotionDistance', 0) >= 1
                  and 'previous-shader-camera=PASS' in (case / 'console.log').read_text(encoding='utf8', errors='replace'))
    passed = passed and 'All dimensions are saved' in log and 'VOXY_WORLD functional stages passed' in log
    write(case / 'receipt.json', dict(passed=bool(passed), exitCode=process.returncode, deviceLost=lost,
          evidence=str(evidence.relative_to(ROOT)), actualCoreVerified=verified,
          performanceMeasured=False, userVisual='pending', automaticRetry=False))
    if not passed:
        raise RuntimeError('Functional failure preserved; inspect before any further run: ' + str(case))
    recycle(CLIENT, VALIDATION, marker='.radiance-voxy-validation')
    print(case, flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--core', type=Path, required=True)
    parser.add_argument('--extra', type=Path, required=True)
    parser.add_argument('--workspace-limit', type=int, choices=range(1, WORKSPACE_HARD_LIMIT_GIB+1), default=WORKSPACE_SOFT_LIMIT_GIB,
                        help='Defaults to the soft limit; above-soft use requires cleanup and user approval')
    values = parser.parse_args()
    run(values.core, values.extra, values.workspace_limit)
