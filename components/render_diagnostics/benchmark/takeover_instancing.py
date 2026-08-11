"""Stage 3 source accounting, using the saved vanilla acceptance world and frozen stage 2."""
from pathlib import Path
import argparse
import collections
import csv
import json
import os
import shutil
import subprocess
import threading
import time
import zipfile

from takeover_experiment import ROOT, archive, probe_jar, storage_preflight, write
from takeover_acceptance import CLIENT, RESULT
from takeover_city import assert_stopped, loaded_core
from crouch_regression import recycle_generated
from vanilla_performance import monitor


def prepare_fixtures():
    """Freeze the normally saved building and original city with identical graphics settings."""
    assert_stopped()
    occupancy = storage_preflight()
    root = ROOT/'validations/performance-vanilla/result/instancing'
    if (root/'fixtures.json').exists():
        raise RuntimeError('Keep the first fixed inputs; do not refreeze between products')
    settings = {'options.txt': (CLIENT/'options.txt').read_bytes()}
    for path in (CLIENT/'radiance').rglob('*'):
        if path.is_file() and path.suffix in ('.txt', '.json') and 'temp' not in path.relative_to(CLIENT/'radiance').parts:
            settings[path.relative_to(CLIENT).as_posix()] = path.read_bytes()
    options = settings['options.txt'].decode('utf8').splitlines()
    options = [r for r in options if r.split(':',1)[0] not in ('soundCategory_master','soundCategory_music','pauseOnLostFocus')]
    settings['options.txt'] = ('\n'.join(options+['soundCategory_master:0.0','soundCategory_music:0.0','pauseOnLostFocus:false'])+'\n').encode()
    entries = {}
    from takeover_experiment import digest
    for name in ('building','city'):
        target = root/(name+'-fixture.zip')
        if target.exists(): raise RuntimeError('Do not replace a fixed fixture')
        with zipfile.ZipFile(target,'x',zipfile.ZIP_DEFLATED) as out:
            if name == 'building':
                world = CLIENT/'saves/Newport City'
                for path in world.rglob('*'):
                    if path.is_file() and path.name != 'session.lock':
                        out.write(path,'world/'+path.relative_to(world).as_posix())
            else:
                original = ROOT/'validations/migration-city_world/result/vanilla-city-fixture.zip'
                with zipfile.ZipFile(original) as source:
                    for info in source.infolist():
                        if info.filename.startswith('world/') and not info.is_dir():
                            out.writestr(info,source.read(info))
            for relative,data in settings.items(): out.writestr('settings/'+relative,data)
        entries[name] = dict(path=target.relative_to(ROOT).as_posix(),sha256=digest(target),bytes=target.stat().st_size)
    write(root/'fixtures.json',dict(inputs=entries,settingsSource='Saved stage 2 vanilla acceptance instance',storage=occupancy,
          ordinaryTransparency='stochastic; no blend',simulation='Original saved simulation retained; no entity, geometry or update-frequency cuts'))
    print(json.dumps(entries),flush=True)


def archive_closed_case(case):
    """Keep completed measurements readable while reserving space for the next client."""
    case=case.resolve()
    result=ROOT/'validations/performance-vanilla/result'
    if not case.is_relative_to(result) or not json.loads((case/'result.json').read_text())['passed']:
        raise RuntimeError('Only successful, closed owned measurements may be archived')
    target=case/'raw-evidence.zip'
    if target.exists(): return
    files=[p for p in case.rglob('*') if p.is_file() and p.suffix in ('.csv','.log')]
    archive(files,case,target)  # Read back every member before recycling originals.
    paths='\n'.join(str(p.resolve()) for p in files)
    command="""Add-Type -AssemblyName Microsoft.VisualBasic
@'
PATHS
'@ -split "`n" | ForEach-Object {
  [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile($_.Trim(), 'OnlyErrorDialogs', 'SendToRecycleBin')
}
""".replace('PATHS',paths)
    subprocess.run(['pwsh','-NoProfile','-Command',command],check=True)
    write(case/'RAW_EVIDENCE.json',dict(archive=target.name,index=target.with_suffix('.index.json').name,
          scope='CSV and log originals read back with SHA-256, then sent to Windows Recycle Bin; summaries unchanged'))


def pairs():
    """A1 B1 B2 A2 per fixed scene, resume only completed cases; never retry a failure."""
    import vanilla_performance
    root = ROOT/'validations/performance-vanilla/result/instancing'
    manifest = json.loads((ROOT/'components/render_diagnostics/build/takeover-instancing/manifest.json').read_text())
    fixtures = json.loads((root/'fixtures.json').read_text())['inputs']
    progress_path = root/'paired-progress.json'
    progress = json.loads(progress_path.read_text()) if progress_path.exists() else dict(runs=[])
    products = {label:ROOT/manifest[key] for label,key in (('A','baselinePath'),('B','productPath'))}
    probe = ROOT/manifest['probePath']
    for scene,distance,resolution in (('building',8,1080),('city',32,1080),('city',32,1440)):
        for ordinal in ('A1','B1','B2','A2'):
            key = f'{scene}-r{distance}-{resolution}-{ordinal}'
            if any(row['key']==key for row in progress['runs']): continue
            case = vanilla_performance.run(f'r{distance}-{resolution}',plan='takeover',
                product=products[ordinal[0]],group='instancing-'+key,seconds=20,frozen_probe=probe,
                fixture=ROOT/fixtures[scene]['path'])
            progress['runs'].append(dict(key=key,scene=scene,distance=distance,resolution=resolution,
                                        ordinal=ordinal,case=case.relative_to(ROOT).as_posix()))
            write(progress_path,progress)
            archive_closed_case(case)
    print('All 12 fixed-product ABBA processes completed',flush=True)


def report_pairs():
    root = ROOT/'validations/performance-vanilla/result/instancing'
    runs = json.loads((root/'paired-progress.json').read_text())['runs']
    if len(runs) != 12: raise RuntimeError('Comparison incomplete; do not pool a partial ABBA')
    groups = {}
    identities, probes = {'A':set(),'B':set()}, set()
    for row in runs:
        case = ROOT/row['case']
        launch = json.loads((case/'launch.json').read_text())
        data = json.loads((case/'analysis.json').read_text())
        if not json.loads((case/'result.json').read_text())['passed']:
            raise RuntimeError('Failed process in paired comparison')
        identities[row['ordinal'][0]].add(launch['product'])
        probes.add(launch['probe'])
        group = groups.setdefault(f"{row['scene']}-r{row['distance']}-{row['resolution']}",dict(runs={}))
        group['runs'][row['ordinal']] = dict(case=row['case'],windows=data['windows'])
    if len(probes) != 1 or any(len(v)!=1 for v in identities.values()):
        raise RuntimeError('Mixed frozen products/probe')
    for group in groups.values():
        if set(group['runs']) != {'A1','B1','B2','A2'}: raise RuntimeError('ABBA labels missing')
        group['pairs'] = []
        for a,b in (('A1','B1'),('A2','B2')):
            modes = {}
            for mode in ('FRAME_ONLY','TIMED'):
                aw,bw = (group['runs'][k]['windows'][mode] for k in (a,b))
                if aw['sceneState'] != bw['sceneState']: raise RuntimeError('Paired scene camera/time/weather differs')
                metrics = dict(frameMs=(aw['intervalMs']['mean'],bw['intervalMs']['mean']),
                    cpuMs=(aw['cpuActiveMs']['mean'],bw['cpuActiveMs']['mean']),
                    gpuMs=(aw['baseGpu']['meanMs'],bw['baseGpu']['meanMs']))
                if mode == 'TIMED':
                    for label in ('java.pbr.generated','entity.dynamic.blas-inputs','entity.rigid.instances',
                                  'entity.rigid.blas-build','entity.format.pbr',
                                  'entity.gpu-upload','entity.source.direct-upload',
                                  'metadata.upload','metadata.reuse','world.tlas.instances'):
                        for field in ('count','logicalBytes'):
                            metrics[label+'/'+field] = tuple(w['workPerFrame'].get(label,{}).get(field) for w in (aw,bw))
                modes[mode] = {name:dict(baseline=av,candidate=bv,
                    candidateMinusBaseline=None if av is None or bv is None else bv-av) for name,(av,bv) in metrics.items()}
            group['pairs'].append(dict(baseline=a,candidate=b,modes=modes))
    result = dict(groups=groups,scope='Three fixed-scene ABBA groups; window means paired, unequal frame counts never pooled',
        limitations=['Concurrent game processes and whole-card telemetry are retained per window; no process-exclusive GPU accounting.',
                     'No frames trimmed. Native/Java CPU drill-down and GPU ranges overlap and are not added.',
                     'Logical source/upload bytes are not measured PCIe traffic; newly added candidate-only counters are not baseline zeros.',
                     'This is bounded performance and correctness evidence, not a long-term GPU stability claim.'])
    write(root/'comparison.json',result)
    for name,group in groups.items():
        print(name,[(p['modes']['FRAME_ONLY']['frameMs']) for p in group['pairs']],flush=True)


def census(product_override=None):
    assert_stopped()
    root = ROOT/'validations/performance-vanilla/result/instancing'
    if (root/'STOP.json').exists():
        raise RuntimeError('Retained failure requires analysis, no automatic retry')
    occupancy = storage_preflight()
    reserve = 330_000_000
    if occupancy['workspace']+reserve > 16*1024**3 or occupancy['validations']+reserve > 8*1024**3:
        raise RuntimeError('Insufficient bounded diagnostic storage reserve')
    case = root/('census-'+time.strftime('%Y%m%d-%H%M%S'))
    case.mkdir(parents=True, exist_ok=False)
    receipt = json.loads((RESULT/'prepared.json').read_text())
    original_product = ROOT/receipt['productPath']
    product = Path(product_override).resolve() if product_override else original_product
    if not product.is_relative_to(ROOT) or not product.is_file():
        raise ValueError('Use a built repository artifact')
    probe = probe_jar()
    options = CLIENT/'options.txt'
    original = options.read_text(encoding='utf8')
    overrides = dict(overrideWidth='1920',overrideHeight='1080',fullscreen='false',
                     soundCategory_master='0.0',soundCategory_music='0.0',pauseOnLostFocus='false')
    rows = [r for r in original.splitlines() if r.split(':',1)[0] not in overrides]
    options.write_text('\n'.join(rows+[f'{k}:{v}' for k,v in overrides.items()])+'\n',encoding='utf8')
    shutil.copy2(probe,CLIENT/'mods/radiance-probe.jar')
    if product_override: shutil.copy2(product, CLIENT/'mods/radiance.jar')
    args = list(receipt['args'])
    flags = dict(experiments='true',correctnessOnly='true',unattended='true',benchmark='true',
                 nativePerformance='true',vanillaPerformance='true',instanceCensus='true',profileControl='true',
                 cityScreenshot='true',
                 cityComparison='true',cityProfilePlan='standard',cityProfileSeconds='4',
                 cityLabel='instancing-input-census',cityWidth='1920',cityHeight='1080',cityBorderless='true')
    args[1:1] = [f'-Dradiance.audit.{k}={v}' for k,v in flags.items()] + [
        f'-Dvalidation.result={case}', '-Dradiance.audit.benchmark.version=1.21.1-neoforge',
        f'-javaagent:{ROOT/"components/render_diagnostics/build/benchmark-support/radiance-benchmark-agent.jar"}']
    for name,value in (('--width','1920'),('--height','1080')): args[args.index(name)+1]=value
    env = dict(os.environ,MCVR_DEVICE_LOSS_TRACE='1',MCVR_LOCAL_AFTERMATH='1',
               RADIANCE_CHUNK_PERF='1',RADIANCE_CHUNK_TRACE='0')
    env.pop('MOD_CLASSES',None)
    write(case/'launch.json',dict(args=args,productPath=product.relative_to(ROOT).as_posix(),
          storage=occupancy,reserve=reserve,scope='Counts-only attribution, not performance data'))
    stop = threading.Event()
    observer = process = None
    try:
        with (case/'console.log').open('x',encoding='utf8') as console:
            process = subprocess.Popen(args,cwd=CLIENT,env=env,stdout=console,stderr=subprocess.STDOUT)
            write(case/'process.json',dict(pid=process.pid))
            observer = threading.Thread(target=monitor,args=(case,stop,process.pid));observer.start()
            until,verified,fault = time.monotonic()+300,False,False
            while process.poll() is None and time.monotonic()<until:
                log = (case/'console.log').read_text(encoding='utf8',errors='replace')
                if 'VK_ERROR_DEVICE_LOST' in log and not fault:
                    fault = True
                    write(root/'STOP.json',dict(case=str(case),deviceLost=True,automaticRetry=False))
                    until=min(until,time.monotonic()+30)
                if not verified and 'CITY_COMPARISON world-ready' in log:
                    loaded_core(process.pid,CLIENT,product,case/'loaded-core.json');verified=True
                stop.wait(1)
            if process.poll() is None: raise RuntimeError('Bounded client did not close; no retry')
        log = (case/'console.log').read_text(encoding='utf8',errors='replace')
        shutil.copytree(CLIENT/'logs',case/'logs',dirs_exist_ok=True)
        image = CLIENT/'screenshots/city-diagnostic.png'
        if image.is_file(): shutil.move(image, case/image.name)
        for name in ('radiance-device-loss-trace.log','radiance-as-lifetime-close.log','radiance-gpu-capabilities.log'):
            if (CLIENT/name).exists(): shutil.copy2(CLIENT/name,case/name)
        if process.returncode or fault or not verified or 'CITY_COMPARISON reload-complete normal-stop' not in log or 'All dimensions are saved' not in log:
            raise RuntimeError('Attribution failed; evidence retained, no retry')
        write(case/'result.json',dict(normalSave=True,normalClose=True,automaticRetry=False))
        recycle_generated(CLIENT/'mods/radiance-probe.jar')
        recycle_generated(CLIENT/'.radiance')
        recycle_generated(CLIENT/'radiance/temp')
        write(root/'current-census.json',dict(case=str(case.relative_to(ROOT))))
        print('Completed source attribution:',case,flush=True)
    except BaseException as error:
        write(root/'STOP.json',dict(case=str(case),error=str(error),automaticRetry=False))
        raise
    finally:
        stop.set()
        if observer: observer.join(timeout=16)
        if process is None or process.poll() is not None:
            options.write_text(original,encoding='utf8')
            if product_override: shutil.copy2(original_product, CLIENT/'mods/radiance.jar')


def analyze_census():
    root = ROOT/'validations/performance-vanilla/result/instancing'
    case = ROOT/json.loads((root/'current-census.json').read_text())['case']
    profile, = (case/'profiles').iterdir()
    data = json.loads((profile/'instance-inputs.json').read_text())
    native = {r['frame']: int(r['count']) for r in csv.DictReader((profile/'counters.csv').open())
              if r['stage']=='entity.dynamic.blas-inputs'}
    counts = data['frameCounts']
    matches = {k: dict(java=v,native=native[k],equal=v==native[k]) for k,v in counts.items() if k in native}
    frames=[]
    for frame,rows in data['frames'].items():
        groups={}
        for row in rows:
            name=row['renderer']
            g=groups.setdefault(name,dict(inputs=0,vertices=0,generatedBytes=0,models=set(),materials=collections.Counter(),replay=collections.Counter(),versionRejections=0))
            g['inputs']+=1
            g['models'].update(row['models'])
            g['replay'].update(row['replay'])
            g['versionRejections']+=row['versionRejections']
            for layer in row['layers']:
                g['vertices']+=layer['vertices'];g['generatedBytes']+=layer['bytes']
                g['materials'][layer['layer']+'/'+layer['material']]+=1
        for g in groups.values():g['models']=sorted(g['models'])
        frames.append(dict(frame=frame,inputs=len(rows),native=native.get(frame),
                           groups=dict(sorted(groups.items(),key=lambda p:p[1]['inputs'],reverse=True))))
    result=dict(case=str(case.relative_to(ROOT)),frames=frames,reasons=data['reasons'],
                partCounters=data['partCounters'],reconciledFrames=len(matches),
                redirectedCloudSubmissions=data.get('redirectedCloudSubmissions'),
                initialPartCounters=data.get('initialPartCounters'),
                matchingFrames=sum(v['equal'] for v in matches.values()),mismatches={k:v for k,v in matches.items() if not v['equal']},
                scope='Per-input renderer/model/material accounting, short diagnostic window only')
    write(case/'source-attribution.json',result)
    print(json.dumps(result,indent=2),flush=True)


if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('mode',choices=('census','analyze-census','prepare-fixtures','pairs','report'))
    parser.add_argument('--product', type=Path)
    args=parser.parse_args()
    if args.mode == 'census': census(args.product)
    elif args.mode == 'analyze-census': analyze_census()
    elif args.mode == 'prepare-fixtures': prepare_fixtures()
    elif args.mode == 'pairs': pairs()
    else: report_pairs()
