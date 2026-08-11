"""One bounded indoor pose experiment in the owned stage acceptance fixture, never Prism."""
from pathlib import Path
import argparse
import json
import os
import shutil
import subprocess
import threading
import time

from takeover_experiment import ROOT, TARGET, digest, probe_jar, storage_preflight, write
from takeover_city import assert_stopped, loaded_core
from takeover_acceptance import CLIENT, RESULT
from vanilla_performance import monitor


def recycle_generated(path):
    path = path.resolve()
    if not path.is_relative_to(CLIENT.resolve()):
        raise ValueError('Generated data leaves owned validation client')
    if path.exists():
        literal = str(path).replace("'", "''")
        subprocess.run(['pwsh', '-NoProfile', '-Command',
            "Add-Type -AssemblyName Microsoft.VisualBasic; "
            f"[Microsoft.VisualBasic.FileIO.FileSystem]::Delete{'Directory' if path.is_dir() else 'File'}("
            f"'{literal}', 'OnlyErrorDialogs', 'SendToRecycleBin')"], check=True)


def run(product, mode, indoor=False):
    assert_stopped()
    if (RESULT/'crouch-STOP.json').exists():
        raise RuntimeError('Retained failure requires analysis, no automatic retry')
    product = product.resolve()
    if not product.is_relative_to(ROOT) or not product.is_file():
        raise ValueError('Use a repository build')
    recycle_generated(CLIENT/'.radiance')
    occupancy = storage_preflight()
    # Measured three single-frame captures total 121 MB; extracted runtime plus
    # the bounded 30-second profile needs less than another 329 MB.
    reserve = 450_000_000 if mode == 'TIMED' else 330_000_000
    if occupancy['workspace'] + reserve > 16*1024**3 or occupancy['validations'] + reserve > 8*1024**3:
        raise RuntimeError('Insufficient diagnostic runtime reserve')
    case = RESULT/('crouch-'+mode.lower()+'-'+time.strftime('%Y%m%d-%H%M%S'))
    case.mkdir(parents=True, exist_ok=False)
    (CLIENT/'.radiance-audit-test-instance').write_text('Owned stage 6 indoor pose validation\n', encoding='utf8')
    options = CLIENT/'options.txt'
    original = options.read_text(encoding='utf8')
    (case/'initial-options.txt').write_text(original, encoding='utf8')
    overrides = dict(renderDistance='8', overrideWidth='1920', overrideHeight='1080',
                     fullscreen='false', soundCategory_master='0.0', soundCategory_music='0.0', pauseOnLostFocus='false')
    rows = [r for r in original.splitlines() if r.split(':',1)[0] not in overrides]
    options.write_text('\n'.join(rows+[f'{k}:{v}' for k,v in overrides.items()])+'\n', encoding='utf8')
    probe = probe_jar()
    shutil.copy2(product, CLIENT/'mods/radiance.jar')
    shutil.copy2(probe, CLIENT/'mods/radiance-probe.jar')
    for p in (CLIENT/'radiance').glob('*'):
        if p.is_file(): shutil.copy2(p, case/p.name)
    receipt = json.loads((RESULT/'prepared.json').read_text())
    args = list(receipt['args'])
    args.insert(1, f'-javaagent:{ROOT / "components/render_diagnostics/build/benchmark-support/radiance-benchmark-agent.jar"}')
    flags = dict(experiments='true', correctnessOnly='true', unattended='true', benchmark='true',
                 nativePerformance='true', vanillaPerformance='true', profileControl='true', crouchRegression='true', crouchMode=mode,
                 cityWidth='1920', cityHeight='1080', cityBorderless='true')
    if indoor: flags['crouchIndoor'] = 'true'
    if mode == 'TIMED': flags.update(rendererReplacement='true', entityTakeover='true', partVerify='true', crouchVerifySources='true', capture='true', crouchCapture='true')
    args[1:1] = [f'-Dradiance.audit.{k}={v}' for k,v in flags.items()] + [f'-Dvalidation.result={case}']
    args.insert(1, f'-Dradiance.audit.benchmark.version={TARGET}')
    args[args.index('--width')+1] = '1920'
    args[args.index('--height')+1] = '1080'
    env = dict(os.environ, MCVR_DEVICE_LOSS_TRACE='1', MCVR_LOCAL_AFTERMATH='1',
               RADIANCE_CHUNK_TRACE='0', RADIANCE_CHUNK_PERF='1')
    env.pop('MOD_CLASSES', None)
    if mode == 'TIMED': env.update(RADIANCE_SHARC_AUDIT='1', RADIANCE_CAPTURE_SINGLE_FRAME='1')
    write(case/'launch.json', dict(args=args, product=digest(product), probe=digest(probe),
          mode=mode, storage=occupancy, scene='Saved user acceptance city, same position/rotation; no geometry/quality changes'))
    stop = threading.Event()
    observer = None
    process = None
    started = time.time()
    try:
        with (case/'console.log').open('x', encoding='utf8') as console:
            process = subprocess.Popen(args, cwd=CLIENT, env=env, stdout=console, stderr=subprocess.STDOUT)
            write(case/'process.json', dict(pid=process.pid))
            observer = threading.Thread(target=monitor, args=(case,stop,process.pid))
            observer.start()
            until, verified = time.monotonic()+330, False
            while process.poll() is None and time.monotonic()<until:
                log = (case/'console.log').read_text(encoding='utf8', errors='replace')
                if 'VK_ERROR_DEVICE_LOST' in log:
                    write(RESULT/'crouch-STOP.json',dict(case=str(case),deviceLost=True,automaticRetry=False))
                    until = min(until,time.monotonic()+30)
                if not verified and 'CROUCH world-ready' in log:
                    loaded_core(process.pid, CLIENT, product, case/'loaded-core.json')
                    verified = True
                stop.wait(1)
            if process.poll() is None:
                raise RuntimeError('Bounded client did not stop; retain evidence, no retry')
        log = (case/'console.log').read_text(encoding='utf8',errors='replace')
        if process.returncode or not verified or 'CROUCH PASS normal-stop' not in log or 'All dimensions are saved' not in log or any(
                word in log for word in ('CROUCH FAIL','VK_ERROR_DEVICE_LOST','MCVR fatal')):
            raise RuntimeError('Crouch run failed; preserve evidence and stop')
        shutil.copytree(CLIENT/'logs',case/'logs')
        (case/'screenshots').mkdir()
        for image in (CLIENT/'screenshots').glob('crouch-*.png'):
            shutil.move(image,case/'screenshots'/image.name)
        for name in ('radiance-device-loss-trace.log','radiance-as-lifetime-close.log','radiance-gpu-capabilities.log'):
            if (CLIENT/name).exists(): shutil.copy2(CLIENT/name,case/name)
        if (CLIENT/'radiance-world-captures').exists():
            shutil.move(CLIENT/'radiance-world-captures',case/'world-captures')
        write(case/'result.json',dict(normalSave=True,normalClose=True,automaticRetry=False))
        recycle_generated(CLIENT/'mods/radiance-probe.jar')
        recycle_generated(CLIENT/'.radiance')
        print('Completed bounded indoor pose experiment:',case,flush=True)
    except BaseException as error:
        latest = CLIENT/'logs/latest.log'
        if latest.exists() and latest.stat().st_mtime >= started:
            shutil.copytree(CLIENT/'logs',case/'logs',dirs_exist_ok=True)
            for name in ('radiance-device-loss-trace.log','radiance-as-lifetime-close.log','radiance-gpu-capabilities.log'):
                if (CLIENT/name).exists(): shutil.copy2(CLIENT/name,case/name)
        write(RESULT/'crouch-STOP.json',dict(case=str(case),error=str(error),automaticRetry=False,
                                         pid=process.pid if process else None))
        raise
    finally:
        stop.set()
        if observer: observer.join(timeout=16)
        # Do not change an active client's files after a timeout/fault.
        if process is None or process.poll() is not None:
            options.write_text(original,encoding='utf8')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--product',required=True,type=Path)
    parser.add_argument('--mode',choices=('FRAME_ONLY','TIMED'),default='FRAME_ONLY')
    parser.add_argument('--indoor', action='store_true')
    args = parser.parse_args()
    run(args.product,args.mode,args.indoor)
