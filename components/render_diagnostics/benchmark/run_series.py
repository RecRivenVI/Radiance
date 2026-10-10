"""Run an explicitly prepared finite series, sequentially; stop on the first failed gate."""
from pathlib import Path
import argparse
import json
import subprocess
import compare
import fixture


def run(manifest):
    manifest=Path(manifest).resolve()
    if not manifest.is_relative_to(fixture.REPOSITORY_RUN.resolve()):raise ValueError('Repository run evidence only')
    plan=json.loads(manifest.read_text());cases=[Path(p).resolve() for p in plan['cases']]
    if not cases or len(cases)>24 or len(set(cases))!=len(cases):raise ValueError('Invalid finite case list')
    for case in cases:
        if not case.is_relative_to(fixture.REPOSITORY_RUN.resolve()) or (case/'start.json').exists():
            raise ValueError('Case outside repository run or already executed: '+str(case))
        if not (case/'launch.json').is_file():raise ValueError('Missing prepared case')
    results=[]
    for case in cases:
        print('START '+str(case),flush=True)
        # This is whole-device NVML usage, including desktop/SDK allocations, not process VRAM.
        # The single owned monitor is stopped in finally; the Minecraft process is never killed.
        with (case/'gpu-device.csv').open('x') as gpu_log, (case/'gpu-monitor-error.log').open('x') as gpu_error:
            monitor=subprocess.Popen(['nvidia-smi','--query-gpu=timestamp,name,driver_version,memory.used,memory.total,utilization.gpu,temperature.gpu,power.draw,clocks.current.graphics',
                                      '--format=csv','--loop-ms=1000'],stdout=gpu_log,stderr=gpu_error,
                                     creationflags=subprocess.CREATE_NO_WINDOW)
            try:
                process=subprocess.run(['pwsh','-NoProfile','-File',str(Path(__file__).with_name('Start-Benchmark.ps1')),
                                        '-LaunchJson',str(case/'launch.json')])
            finally:
                if monitor.poll() is None:monitor.terminate()
                monitor.wait(timeout=10)
        if process.returncode:raise RuntimeError('Launcher failed; stopped without retry: '+str(case))
        analysis=compare.collect(case)
        result={'case':str(case),'gate':analysis['configurationGate'],
                'frameIntervals':[p['real_start_interval_ms'] for p in analysis['profiles']]}
        results.append(result)
        (manifest.parent/(manifest.stem+'-results.json')).write_text(json.dumps(results,indent=2)+'\n')
        print(json.dumps(result),flush=True)
        if not result['gate']['passed']:raise RuntimeError('Configuration/runtime gate failed; stopped without retry')
    return results


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('manifest',type=Path);p.add_argument('--execute',action='store_true');a=p.parse_args()
    if not a.execute:raise SystemExit('Pass --execute after preparing and reviewing the finite case list; no clients started')
    run(a.manifest)
