"""Owned stage evidence and a pinned-artifact ABBA comparison. Never uses a launcher instance."""
from pathlib import Path
import argparse
import csv
import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import time
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[3]
FUNCTIONAL = ROOT / 'validations/conformance-takeover'
PERFORMANCE = ROOT / 'validations/performance-takeover'
TARGET = '1.21.1-neoforge'


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2)+'\n', encoding='utf8')


def storage_preflight():
    def size(directory, excluded=()):
        total=0
        if not directory.exists(): return 0
        for current,dirs,files in os.walk(directory,followlinks=False):
            dirs[:]=[d for d in dirs if not (Path(current)/d).is_junction()
                     and not (Path(current)/d).is_symlink()
                     and (Path(current)/d).relative_to(directory).as_posix() not in excluded]
            total+=sum((Path(current)/f).stat().st_size for f in files if not (Path(current)/f).is_symlink())
        return total
    external=[Path('D:/Workspaces/Artifacts/Radiance'),*(p for p in Path('D:/Workspaces/Scratches').glob('Radiance*')
                                                   if p.is_dir() and not p.is_junction())]
    values=dict(workspace=size(ROOT,('.git','components/native_renderer/third_party')),
                validations=sum(size(p) for p in (ROOT/'validations').glob('*/*') if p.name in ('instance','result')),
                external=sum(size(p) for p in external))
    if values['workspace']>16*1024**3 or values['validations']>4*1024**3 or values['external']>8*1024**3:
        raise RuntimeError('Storage gate; retain evidence and recycle completed clients first: '+repr(values))
    return values


def archive(paths, base, destination):
    index = []
    with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED) as out:
        for path in sorted(paths):
            name = path.relative_to(base).as_posix()
            index.append(dict(path=name, bytes=path.stat().st_size, sha256=digest(path)))
            out.write(path, name)
    with zipfile.ZipFile(destination) as source:
        for entry in index:
            if hashlib.sha256(source.read(entry['path'])).hexdigest() != entry['sha256']:
                raise RuntimeError('Archive verification failed; retain client: '+entry['path'])
    write(destination.with_suffix('.index.json'), index)
    return index


def recycle(path, validation, marker='.radiance-takeover-stages'):
    path = path.resolve()
    if not path.is_relative_to((validation/'instance').resolve()) or not (path/marker).is_file():
        raise RuntimeError('Recycle target lacks owned instance boundary')
    quoted = str(path).replace("'", "''")
    subprocess.run(['powershell', '-NoProfile', '-Command',
                    "Add-Type -AssemblyName Microsoft.VisualBasic; [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory('"
                    +quoted+"', 'OnlyErrorDialogs', 'SendToRecycleBin')"], check=True)


def completed_chains(trace):
    events = []
    for row in trace.splitlines():
        if row.startswith('clock_ns=') and int(row.split('dropped=')[1]):
            raise RuntimeError('Chunk trace overflow')
        parts = row.split(',')
        if len(parts) == 6 and parts[0].isdigit():
            events.append((int(parts[0]), parts[1], *map(int, parts[2:])))
    complete, gpu, published, uses, finished = [], {}, {}, {}, {}
    for clock, stage, identity, revision, frame, owner in events:
        key = identity, revision, owner
        if stage == 'gpu-complete': gpu[key] = clock
        elif stage == 'publish':
            published.pop(key,None)
            if key in gpu and gpu[key] <= clock:
                published[key] = clock
                del gpu[key]  # A reused revision needs a fresh completion after reload.
        elif stage == 'tlas-record' and key in published and published[key] <= clock: uses.setdefault(frame, set()).add((key,clock))
        elif stage == 'frame-complete': finished[frame]=clock
    for frame in finished:
        complete.extend(dict(slot=k[0], revision=k[1], generation=k[2], frame=frame)
                        for k,clock in uses.get(frame, ()) if finished[frame]>=clock)
    if not complete:
        raise RuntimeError('No actual GPU completion -> publication -> completed scene chain')
    return complete


def preserve(validation, instance, name):
    destination = validation/'result'/name
    destination.mkdir(parents=True, exist_ok=False)
    paths = [p for p in instance.rglob('*') if p.is_file() and
             (p.suffix.lower() in ('.log','.txt','.json','.jsonl','.csv','.png','.xml','.md')
              or 'gpu-diagnostics' in p.parts or 'radiance-gpu-shaders' in p.parts
              or p.suffix.lower() in ('.nv-gpudmp','.dmp'))
             and not any(x in p.parts for x in ('saves','.radiance','assets','libraries'))]
    archive(paths, instance, destination/'evidence.zip')
    native=[dict(path=str(p.relative_to(instance)),bytes=p.stat().st_size,sha256=digest(p))
            for p in instance.rglob('core.dll')]
    write(destination/'native-files.json',native)
    trace=validation/'result/chunk-trace.txt'
    if validation==FUNCTIONAL and trace.is_file():
        archive([trace],validation/'result',destination/'native-chunk-trace.zip')
    return destination


def analyze():
    instance = FUNCTIONAL/'instance'/TARGET/'client'
    log = (instance/'logs/latest.log').read_text(encoding='utf8', errors='replace')
    destination = preserve(FUNCTIONAL, instance, time.strftime('functional-%Y%m%d-%H%M%S'))
    faults = [x for x in ('VK_ERROR_DEVICE_LOST','MCVR fatal','[FATAL]','Crashed!','TAKEOVER_STAGE FAIL') if x in log]
    if faults:
        write(destination/'failure.json', dict(faults=faults, stopGroup=True))
        raise RuntimeError('Functional failure preserved; no retry: '+repr(faults))
    result = json.loads((FUNCTIONAL/'result/takeover-stages.json').read_text(encoding='utf8'))
    for marker in ('TAKEOVER_STAGE normal-stop','All dimensions are saved'):
        if marker not in log: raise RuntimeError('Missing lifecycle evidence: '+marker)
    if not result.get('reloadComplete') or not result.get('boundaryClientState'):
        raise RuntimeError('Missing sample assertions')
    result['completedTerrainChains'] = completed_chains((FUNCTIONAL/'result/chunk-trace.txt').read_text(encoding='utf8'))
    result['firstPixelProof'] = False
    result['visualAcceptance'] = 'pending'
    result['archive'] = str(destination.relative_to(ROOT))
    archive([p for p in (instance/'saves').rglob('*') if p.is_file()], instance/'saves', FUNCTIONAL/'result/world-template.zip')
    # Settings and input state are captured with the exact same world for every formal process.
    archive([p for p in instance.rglob('*') if p.is_file() and
             (p.parent == instance and p.name in ('options.txt','FIXTURE.json') or 'radiance' in p.relative_to(instance).parts)
             and p.suffix.lower() in ('.txt','.json','.yaml','.properties')], instance, FUNCTIONAL/'result/settings-template.zip')
    write(FUNCTIONAL/'result/analysis.json', result)
    write(destination/'analysis.json', result)
    recycle(instance, FUNCTIONAL)
    print(json.dumps({k:v for k,v in result.items() if k != 'completedTerrainChains'}, indent=2))
    print('Completed terrain chains:',len(result['completedTerrainChains']))


def failed():
    instance = FUNCTIONAL/'instance'/TARGET/'client'
    log = (instance/'logs/latest.log').read_text(encoding='utf8',errors='replace')
    destination = preserve(FUNCTIONAL,instance,time.strftime('failed-%Y%m%d-%H%M%S'))
    trace = FUNCTIONAL/'result/chunk-trace.txt'
    if trace.exists(): shutil.copy2(trace,destination/'chunk-trace.txt')
    write(destination/'failure.json',dict(passed=False,reason='See original latest.log',
                                        deviceLost='VK_ERROR_DEVICE_LOST' in log))
    if 'VK_ERROR_DEVICE_LOST' in log or 'All dimensions are saved' not in log:
        raise RuntimeError('Retain failed client; do not retry this runtime group')
    recycle(instance,FUNCTIONAL)
    print('Preserved failed assertion and recycled normally closed disposable client:',destination)


def voxy():
    validation=ROOT/'validations/regression-voxy_integration'
    instance=validation/'instance'/TARGET/'client'
    destination=preserve(validation,instance,time.strftime('stage0-%Y%m%d-%H%M%S'))
    log=(instance/'logs/latest.log').read_text(encoding='utf8',errors='replace')
    faults=[word for word in ('VK_ERROR_DEVICE_LOST','MCVR fatal','[FATAL]','Crashed!','Voxy selection budget') if word in log]
    if faults:
        write(destination/'failure.json',dict(faults=faults,stopGroup=True))
        raise RuntimeError('Voxy failed; preserved without automatic retry')
    report=json.loads((validation/'result/voxy-functional.json').read_text(encoding='utf8'))
    fixture=json.loads((instance/'FIXTURE.json').read_text(encoding='utf8'))
    roots=list(map(int,re.findall(r'Voxy PT requested domain: roots=(\d+)',log)))
    if not report.get('runtimeStagesPassed') or len(report.get('stages',[]))!=6:
        raise RuntimeError('Actual six-stage Voxy workflow incomplete')
    if fixture['voxy_distance_blocks']==64*512 and max(roots,default=0)<=8192:
        raise RuntimeError('Requested root counter did not cover the original capacity counterexample')
    if 'All dimensions are saved' not in log or 'Stopping!' not in log:
        raise RuntimeError('Missing normal lifecycle evidence')
    report.update(requestedRoots=roots,fixture=fixture,archive=str(destination.relative_to(ROOT)),performanceMeasured=False)
    write(destination/'analysis.json',report)
    recycle(instance,validation,marker='.radiance-voxy-validation')
    print(json.dumps(report,indent=2))


def probe_jar():
    output = ROOT/'components/render_diagnostics/build/takeover-probe.jar'
    sources = [ROOT/f'versions/{TARGET}/build/classes/java/probe', ROOT/f'versions/{TARGET}/build/resources/probe']
    if not all(p.is_dir() for p in sources): raise RuntimeError('Run probeClasses first')
    java_sources = ROOT/f'versions/{TARGET}/src/probe/java'
    for source in java_sources.rglob('*.java'):
        compiled = sources[0]/source.relative_to(java_sources).with_suffix('.class')
        if not compiled.is_file() or source.stat().st_mtime_ns > compiled.stat().st_mtime_ns:
            raise RuntimeError('Probe classes are stale; explicitly run :version:'+TARGET+':probeClasses: '+str(source))
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as out:
        for source in sources:
            for file in source.rglob('*'):
                if file.is_file(): out.write(file, file.relative_to(source).as_posix())
    with zipfile.ZipFile(output) as package:
        if '${mod_id}' in package.read('META-INF/neoforge.mods.toml').decode():
            raise RuntimeError('Probe resources were not expanded by the Target')
    return output


def percentile(values, fraction):
    values = sorted(values)
    return values[min(len(values)-1, int((len(values)-1)*fraction))]


def profile_summary(z, expected_windows=4, prefix='takeover-'):
    results = {}
    for name in z.namelist():
        if not name.endswith('/frames.csv'): continue
        parent = name.rsplit('/',1)[0]
        status = dict(row.split('=',1) for row in z.read(parent+'/STATUS.txt').decode().splitlines() if '=' in row)
        if status.get('valid-cpu-sample') != 'true': raise RuntimeError('Invalid profile: '+parent)
        rows = list(csv.DictReader(io.StringIO(z.read(name).decode())))
        values = [int(r['start_interval_ns'])/1e6 for r in rows if int(r['start_interval_ns'])>0]
        if len(values)<100: raise RuntimeError('Insufficient frame sample')
        metadata=z.read(parent+'/METADATA.txt').decode()
        window = dict(row.split('=',1) for row in metadata.splitlines() if '=' in row)['scene']
        if not window.startswith(prefix): raise RuntimeError('Unrelated profile scene')
        results[window] = dict(samples=len(values), mean=sum(values)/len(values),
                               p50=percentile(values,.5),p95=percentile(values,.95),p99=percentile(values,.99),
                               cpuMean=sum(int(r['render_thread_cpu_ns']) for r in rows)/len(rows)/1e6,
                               metadata=metadata,
                               subStages=z.read(parent+'/summary.csv').decode())
    if len(results)!=expected_windows: raise RuntimeError('Unexpected isolated measurement window count')
    return results


def compare():
    config = tomllib.loads((PERFORMANCE/'validation.toml').read_text(encoding='utf8'))
    if not (FUNCTIONAL/'result/analysis.json').is_file(): raise RuntimeError('Complete functional sample first')
    pinned = ROOT/config['comparison']['baseline']
    candidates = list((ROOT/f'versions/{TARGET}/build/libs').glob('radiance-*.jar'))
    candidates = [p for p in candidates if not p.name.endswith('-sources.jar')]
    if len(candidates)!=1: raise RuntimeError('Ambiguous release artifact')
    products = dict(baseline=pinned, candidate=candidates[0])
    product_hashes = {key:digest(path) for key,path in products.items()}
    probe = probe_jar()
    settings = FUNCTIONAL/'result/settings-template.zip'
    world = FUNCTIONAL/'result/world-template.zip'
    java = next(p for p in (Path.home()/'.gradle/jdks').glob('eclipse_adoptium-21*/bin/java.exe'))
    moddev = ROOT/f'versions/{TARGET}/build/moddev'
    classpath=json.loads((ROOT/'build/takeover-stages/packaged-launch-classpath.json').read_text(encoding='utf8'))
    if not classpath['files'] or any('/classes/java/main' in p.replace('\\','/') or '/classes/java/probe' in p.replace('\\','/') for p in classpath['files']):
        raise RuntimeError('Expected the empty packaged source set, not development product/probe classes')
    if not any(Path(p).name.startswith('neoforge-') and Path(p).suffix=='.jar' for p in classpath['files']):
        raise RuntimeError('Actual ModDevGradle game classpath is missing')
    agent = ROOT/'components/render_diagnostics/build/benchmark-support/radiance-benchmark-agent.jar'
    aftermath = ROOT/'components/render_diagnostics/build/native/aftermath/RelWithDebInfo/radiance-aftermath-agent.dll'
    if not aftermath.is_file(): raise RuntimeError('Default local Aftermath agent missing')
    result = PERFORMANCE/'result'
    result.mkdir(parents=True, exist_ok=True)
    for index, label in enumerate(config['comparison']['order']):
        occupancy=storage_preflight()
        instance = PERFORMANCE/'instance'/TARGET/'client'
        if instance.exists(): raise RuntimeError('Previous owned client retained; inspect before continuing')
        instance.mkdir(parents=True)
        with zipfile.ZipFile(world) as z: z.extractall(instance/'saves')
        with zipfile.ZipFile(settings) as z: z.extractall(instance)
        for marker in ('.radiance-takeover-stages','.radiance-audit-test-instance','.radiance-acceptance'):
            (instance/marker).write_text('Owned stage comparison\n',encoding='utf8')
        mods=instance/'mods';mods.mkdir()
        shutil.copy2(products[label],mods/'radiance.jar');shutil.copy2(probe,mods/'radiance-probe.jar')
        for key,path in products.items():
            if digest(path)!=product_hashes[key]: raise RuntimeError('Product changed during comparison')
        case=f'{index+1}-{label}'
        case_result=result/case;case_result.mkdir(exist_ok=False)
        argument_file=case_result/'classpath.args'
        java_classpath=os.pathsep.join(classpath['files']).replace('\\','\\\\').replace('"','\\"')
        argument_file.write_text('-cp\n"'+java_classpath+'"\n',encoding='utf8')
        arguments=[str(java),'-Xmx4G','-Xms1G',f'-javaagent:{agent}',f'-agentpath:{aftermath}=gpu-diagnostics',
                   '-Dradiance.audit.experiments=true','-Dradiance.audit.takeoverStages=true',
                   '-Dradiance.audit.takeoverPerformance=true','-Dradiance.audit.benchmark=true',
                   '-Dradiance.audit.correctnessOnly=true',
                   '-Dradiance.audit.benchmark.version=1.21.1-neoforge','-Dradiance.audit.unattended=true',
                   f'-Dradiance.audit.takeoverWarmup={config["comparison"]["warmup-seconds"]}',
                   f'-Dradiance.audit.takeoverSeconds={config["comparison"]["sample-seconds"]}',
                   f'-Dvalidation.result={case_result}',f'@{argument_file}',f'@{moddev/"packagedClientRunVmArgs.txt"}',
                   f'@{moddev/"packagedClientRunProgramArgs.txt"}', '--width','1920','--height','1080',
                   '--username','DevPlayer','--quickPlaySingleplayer','Audit Benchmark']
        env=dict(os.environ,MCVR_DEVICE_LOSS_TRACE='1',MCVR_LOCAL_AFTERMATH='1',RADIANCE_CHUNK_TRACE='0')
        env.pop('MOD_CLASSES',None)
        write(case_result/'launch.json',dict(label=label,jarSha256=product_hashes[label],probeSha256=digest(probe),
              worldSha256=digest(world),settingsSha256=digest(settings),arguments=arguments,storage=occupancy,
              diagnosticEnvironment={k:env[k] for k in ('MCVR_DEVICE_LOSS_TRACE','MCVR_LOCAL_AFTERMATH','RADIANCE_CHUNK_TRACE')}))
        # Observe other work without closing it; do this before the measurement warm-up.
        observed={}
        for key,command in (
            ('gpu',['nvidia-smi','--query-gpu=name,driver_version,memory.total,memory.used,utilization.gpu','--format=csv']),
            ('javaProcesses',['powershell','-NoProfile','-Command',
                              'Get-Process java -ErrorAction SilentlyContinue | Select-Object Id,CPU,WorkingSet64 | ConvertTo-Json'])):
            try:
                observation=subprocess.run(command,capture_output=True,text=True,encoding='utf8',errors='replace',timeout=20)
                observed[key]=dict(exitCode=observation.returncode,output=observation.stdout)
            except (OSError,subprocess.TimeoutExpired) as failure:
                observed[key]=dict(unavailable=str(failure))
        write(case_result/'environment-before.json',observed)
        with (case_result/'console.log').open('w',encoding='utf8') as console:
            process=subprocess.Popen(arguments,cwd=instance,env=env,stdout=console,stderr=subprocess.STDOUT)
            write(case_result/'process.json',dict(pid=process.pid,started=time.time()))
            try:
                # Query only this owned process, once before the formal warm-up expires.
                time.sleep(18)
                if process.poll() is None:
                    query=subprocess.run(['powershell','-NoProfile','-Command',
                        f'(Get-Process -Id {process.pid}).Modules | Where-Object {{ $_.ModuleName -match "core.dll|sl.interposer|aftermath" }} | Select-Object ModuleName,FileName | ConvertTo-Json'],
                        capture_output=True,text=True,encoding='utf8',errors='replace',timeout=20)
                    (case_result/'loaded-modules.json').write_text(query.stdout,encoding='utf8')
                    modules=json.loads(query.stdout)
                    if isinstance(modules,dict): modules=[modules]
                    core=next(Path(m['FileName']) for m in modules if m['ModuleName'].lower()=='core.dll')
                    if not core.resolve().is_relative_to(instance.resolve()):
                        raise RuntimeError('Loaded core does not belong to this owned instance')
                    with zipfile.ZipFile(products[label]) as package:
                        expected=next(hashlib.sha256(package.read(n)).hexdigest() for n in package.namelist() if n=='core.dll' or n.endswith('/core.dll'))
                    if digest(core)!=expected: raise RuntimeError('Actually loaded DLL differs from embedded release DLL')
                    write(case_result/'loaded-core.json',dict(path=str(core),sha256=expected,verified=True))
                code=process.wait(timeout=582)
            except subprocess.TimeoutExpired:
                write(case_result/'failure.json',dict(timeout=True,pid=process.pid,stopGroup=True))
                raise RuntimeError('Owned process timed out; retained without automatic restart')
        archive_result=preserve(PERFORMANCE,instance,case+'-evidence')
        log=(instance/'logs/latest.log').read_text(encoding='utf8',errors='replace')+(case_result/'console.log').read_text(encoding='utf8',errors='replace')
        faults=[x for x in ('VK_ERROR_DEVICE_LOST','MCVR fatal','[FATAL]','Crashed!','TAKEOVER_STAGE FAIL') if x in log]
        if code or faults or 'TAKEOVER_STAGE normal-stop' not in log or 'All dimensions are saved' not in log:
            write(case_result/'failure.json',dict(exitCode=code,faults=faults,stopGroup=True))
            raise RuntimeError('Comparison stopped after failed process; retained all evidence')
        with zipfile.ZipFile(archive_result/'evidence.zip') as z: summary=profile_summary(z)
        vma=[int(value) for value in re.findall(r'vma-bytes=(\d+)',log)]
        write(case_result/'resource-summary.json',dict(sampledVmaPeakBytes=max(vma,default=0),
                samples=len(vma),scope='allocator observation, not entire-card RTSS usage or instantaneous hardware peak'))
        write(case_result/'analysis.json',summary)
        loaded=[dict(path=str(p.relative_to(instance)),sha256=digest(p)) for p in instance.rglob('core.dll')]
        if not loaded: raise RuntimeError('No extracted native core identity')
        write(case_result/'native-files.json',loaded)
        recycle(instance,PERFORMANCE)
        print(case, {k:round(v['mean'],3) for k,v in summary.items()},flush=True)
    write(result/'pairing.json',dict(products=product_hashes,world=digest(world),settings=digest(settings),
                                   order=config['comparison']['order'],performanceClaim='requires variation analysis'))


def report():
    result=PERFORMANCE/'result'
    pairing=json.loads((result/'pairing.json').read_text(encoding='utf8'))
    runs=[]
    selected={'entity.format.pbr','entity.source.direct-upload','entity.gpu-upload',
              'entity.rigid.instances','entity.rigid.resident-models','entity.rigid.referenced-vertices','entity.rigid.new-input',
              'entity.dynamic.blas-inputs','world.tlas.instances','metadata.upload','sbt.hit.reuse',
              'chunk.scene.static-records','chunk.scene.static-rebuild','chunk.scene.cache-build'}
    for index,label in enumerate(pairing['order'],1):
        case=f'{index}-{label}';directory=result/case
        launch=json.loads((directory/'launch.json').read_text(encoding='utf8'))
        loaded=json.loads((directory/'loaded-core.json').read_text(encoding='utf8'))
        if (launch['worldSha256']!=pairing['world'] or launch['settingsSha256']!=pairing['settings']
                or launch['jarSha256']!=pairing['products'][label] or not loaded['verified']):
            raise RuntimeError('Comparison identities differ')
        windows=json.loads((directory/'analysis.json').read_text(encoding='utf8'))
        with zipfile.ZipFile(result/(case+'-evidence')/'evidence.zip') as archive:
            for name in archive.namelist():
                if not name.endswith('/frames.csv'): continue
                parent=name.rsplit('/',1)[0]
                metadata=archive.read(parent+'/METADATA.txt').decode()
                scene=dict(row.split('=',1) for row in metadata.splitlines() if '=' in row)['scene']
                frames=list(csv.DictReader(io.StringIO(archive.read(name).decode())))
                if any(r['width']!='1920' or r['height']!='1080' or r['render_distance']!='8' or r['failed']!='false' for r in frames):
                    raise RuntimeError('Fixture/output settings changed during a window')
                counters={}
                for row in csv.DictReader(io.StringIO(archive.read(parent+'/counters.csv').decode())):
                    if row['stage'] not in selected: continue
                    count,byte=map(int,(row['count'],row['bytes']))
                    previous=counters.setdefault(row['stage'],{}).get(row['frame'],(0,0))
                    counters[row['stage']][row['frame']]=(previous[0]+count,previous[1]+byte)
                windows[scene]['counters']={stage:dict(reportedFrames=len(data),
                    meanCount=sum(v[0] for v in data.values())/len(data),peakCount=max(v[0] for v in data.values()),
                    meanBytes=sum(v[1] for v in data.values())/len(data),peakBytes=max(v[1] for v in data.values()))
                    for stage,data in counters.items()}
                windows[scene]['status']=archive.read(parent+'/STATUS.txt').decode()
                windows[scene]['stages']=list(csv.DictReader(io.StringIO(windows[scene].pop('subStages'))))
        resources=json.loads((directory/'resource-summary.json').read_text(encoding='utf8'))
        if not resources['samples']: resources['sampledVmaPeakBytes']=None
        runs.append(dict(case=case,label=label,windows=windows,resources=resources,
                         loadedCore=loaded,probeSha256=launch['probeSha256'],
                         environment=json.loads((directory/'environment-before.json').read_text(encoding='utf8'))))
    if len({run['probeSha256'] for run in runs})!=1: raise RuntimeError('Different probes in comparison')
    comparisons={}
    for scene in runs[0]['windows']:
        baseline=[r['windows'][scene]['mean'] for r in runs if r['label']=='baseline']
        candidate=[r['windows'][scene]['mean'] for r in runs if r['label']=='candidate']
        a,b=sum(baseline)/len(baseline),sum(candidate)/len(candidate)
        variation=max(max(baseline)-min(baseline),max(candidate)-min(candidate))
        comparisons[scene]=dict(baselineMeanMs=a,candidateMeanMs=b,deltaMs=a-b,
            reductionPercent=100*(a-b)/a,maxWithinLabelDifferenceMs=variation,
            exceedsObservedRunDifference=(a-b)>variation and max(candidate)<min(baseline))
    output=dict(pairing=pairing,comparisons=comparisons,runs=runs,
                scope='Two processes per artifact, fixed small fixture; observed variation is not a confidence interval. GPU intervals overlap. Memory counters are logical observations; unavailable VMA peak is null.')
    write(result/'comparison.json',output)
    print(json.dumps(comparisons,indent=2))


if __name__=='__main__':
    command=argparse.ArgumentParser()
    command.add_argument('mode',choices=('analyze','compare','failed','voxy','report'))
    mode=command.parse_args().mode
    {'analyze':analyze,'compare':compare,'failed':failed,'voxy':voxy,'report':report}[mode]()
