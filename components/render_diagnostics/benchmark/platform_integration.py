"""Packaged public-platform/Extra functional sessions; no performance campaign or desktop input."""
from pathlib import Path
import argparse,json,os,shutil,subprocess,time,tomllib,zipfile
from takeover_experiment import ROOT,TARGET,EXTRA_ROOT as EXTRA,WORKSPACE_SOFT_LIMIT_GIB,WORKSPACE_HARD_LIMIT_GIB,probe_jar,recycle,preserve,write,digest,storage_preflight
from entity_takeover import extract
from takeover_city import loaded_core
import fixture

VALIDATION=ROOT/'validations/conformance-takeover'
CLIENT=VALIDATION/'instance'/TARGET/'client'
RESULT=VALIDATION/'result/platform-reorganization'

def extra_probe():
    output=EXTRA/'versions'/TARGET/'build/intermediates/platform-probe.jar'
    output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as z:
        for directory in ('classes/java/probe','resources/probe'):
            base=EXTRA/'versions'/TARGET/'build'/directory
            for path in base.rglob('*'):
                if path.is_file():z.write(path,path.relative_to(base).as_posix())
    return output

def run(label, core_override=None, extra_override=None, validation_limit_gib=8, source_stress=False, input_names=None, expected_sources=None, veil_resources=False, workspace_limit_gib=WORKSPACE_SOFT_LIMIT_GIB, instance_name="client", spring_efficiency=False):
    if instance_name not in ("client", "veil"):
        raise ValueError("Unregistered owned functional instance name")
    client=VALIDATION/"instance"/TARGET/instance_name
    occupancy=storage_preflight(validation_limit_gib=validation_limit_gib, workspace_limit_gib=workspace_limit_gib)
    # Preflight enforces the 16 GiB soft admission limit; the bounded active client must
    # fit the 20 GiB hard limit. Its regenerated environment is recycled on completion.
    if occupancy['workspace']+450_000_000>WORKSPACE_HARD_LIMIT_GIB*1024**3 or occupancy['validations']+450_000_000>validation_limit_gib*1024**3:raise RuntimeError('Insufficient owned functional-session reserve')
    if client.exists():raise RuntimeError('Retained instance; inspect before a new session')
    if (RESULT/'DEVICE_LOST.json').exists():raise RuntimeError('Device loss stopped this group')
    case=RESULT/(label+'-'+time.strftime('%Y%m%d-%H%M%S'));case.mkdir(parents=True,exist_ok=False)
    products=[Path(core_override).resolve()] if core_override is not None else [p for p in (ROOT/f'versions/{TARGET}/build/libs').glob('radiance-*.jar') if not p.name.endswith('-sources.jar')]
    if len(products)!=1:raise RuntimeError('Build one current distributedJar first')
    product=products[0]
    modded=label=='sources'
    fixture.REPOSITORY_RUN=VALIDATION/'instance'
    fixture.create(client,'1.21.1',modded=modded,tier=1)
    # This is a new correctness fixture, not a historical performance input. Keep Y64 objects above ground.
    import nbtlib
    level=nbtlib.load(client/'saves/Audit Benchmark/level.dat');level['Data']['WorldGenSettings']['dimensions']['minecraft:overworld']['generator']['settings']['layers'][1]['height']=nbtlib.Int(63);level.save()
    extract(VALIDATION/'result/settings-template.zip',client)
    options=client/'options.txt';s=options.read_text()
    s='\n'.join(x for x in s.splitlines() if not x.startswith(('soundCategory_master:','soundCategory_music:','renderDistance:','guiScale:','pauseOnLostFocus:')))
    options.write_text(s+'\nsoundCategory_master:0.0\nsoundCategory_music:0.0\nrenderDistance:8\nguiScale:2\npauseOnLostFocus:false\n',encoding='utf8')
    for marker in ('.radiance-entity-takeover','.radiance-takeover-stages','.radiance-acceptance'):
        (client/marker).write_text('Owned platform integration fixture\n',encoding='utf8')
    mods=client/'mods';mods.mkdir()
    probe=next((ROOT/f'versions/{TARGET}/build/intermediates/probe').glob('Radiance-probe*.jar'))
    with zipfile.ZipFile(probe) as check:
        if 'com/radiance/audit/PlatformIntegrationProbe.class' not in check.namelist():raise RuntimeError('Platform probe was not built')
    jars=[product,probe]
    extraJars=[Path(extra_override).resolve()] if extra_override is not None else [p for p in (EXTRA/f'versions/{TARGET}/build/libs').glob('radiance_extra-*.jar') if not p.name.endswith('-sources.jar')]
    if label!='core' and len(extraJars)!=1:raise RuntimeError('Build one current Extra product first')
    extraJar=extraJars[0] if extraJars else None
    if label!='core':jars.append(extraJar)
    if modded:
        jars.append(extra_probe())
        local=tomllib.loads((ROOT/'local.toml').read_text(encoding='utf8'))
        registry=tomllib.loads((ROOT/'inputs.toml').read_text(encoding='utf8'))
        for name in (input_names if input_names is not None else ('create','sable','aeronautics_bundled','voxy')):
            path=Path(local['inputs'][name]);h=digest(path)
            if h not in registry[name]['sha256']:raise RuntimeError('Input identity changed: '+name)
            jars.append(path)
    for path in jars:(mods/path.name).symlink_to(path)
    cp=json.loads((ROOT/'build/takeover-stages/packaged-launch-classpath.json').read_text())['files']
    if any('/classes/java/main' in p.replace('\\','/') or '/classes/java/probe' in p.replace('\\','/') for p in cp):raise RuntimeError('Development classes shadow products')
    argument=os.pathsep.join(cp).replace('\\','\\\\').replace('"','\\"')
    (case/'classpath.args').write_text('-cp\n"'+argument+'"\n',encoding='utf8')
    java=next((Path.home()/'.gradle/jdks').glob('eclipse_adoptium-21*/bin/java.exe'))
    moddev=ROOT/f'versions/{TARGET}/build/moddev'
    agent=ROOT/'components/render_diagnostics/build/benchmark-support/radiance-benchmark-agent.jar'
    aftermath=ROOT/'components/render_diagnostics/build/native/aftermath/RelWithDebInfo/radiance-aftermath-agent.dll'
    expected='radiance_extra:sable,radiance_extra:flywheel,radiance_extra:simulated,radiance_extra:veil,radiance_extra:voxy,radiance_extra:outlines' if modded else ''
    if expected_sources is not None:expected=expected_sources
    args=[str(java),f'-javaagent:{agent}',f'-agentpath:{aftermath}=gpu-diagnostics',
          '-Dradiance.audit.experiments=true','-Dradiance.audit.entityTakeover=true','-Dradiance.audit.entityFixture=true',
          '-Dradiance.audit.partVerify=true','-Dradiance.audit.takeoverCompare=true','-Dradiance.audit.partVerifyAll=true',
          '-Dradiance.audit.paintingVerify=true','-Dradiance.audit.benchmark=true','-Dradiance.audit.correctnessOnly=true',
          '-Dradiance.audit.unattended=true','-Dradiance.audit.benchmark.version='+TARGET,'-Dradiance.audit.platform=true',
          '-Dradiance.audit.expectedSources='+expected,'-Dradiance.audit.cityWidth=1920','-Dradiance.audit.cityHeight=1080',
          '-Dradiance.audit.cityBorderless=true',f'-Dvalidation.result={case}',f'@{case/"classpath.args"}',
          f'@{moddev/"packagedClientRunVmArgs.txt"}','-Xmx4G',f'@{moddev/"packagedClientRunProgramArgs.txt"}',
          '--username','RecRivenVI','--width','1920','--height','1080']
    if veil_resources:
        args.insert(1,'-Dradiance.audit.veilResources=true')
    if source_stress:
        # Same packaged fixture and products, without any dual-renderer correctness probes.
        diagnostic_prefixes=('-Dradiance.audit.entityTakeover=', '-Dradiance.audit.entityFixture=', '-Dradiance.audit.partVerify',
                             '-Dradiance.audit.takeoverCompare=', '-Dradiance.audit.paintingVerify=',
                             '-Dradiance.audit.platform=', '-Dradiance.audit.correctnessOnly=')
        args=[arg for arg in args if not arg.startswith(diagnostic_prefixes)]
        args[1:1]=['-Dradiance.audit.sourceStress=true','-Dradiance.audit.profileControl=true',
                   '-Dradiance.audit.nativePerformance=true','-Dradiance.audit.vanillaPerformance=true',
                   '-Dradiance.audit.benchmark.sampleSeconds=120']
    env=dict(os.environ,MCVR_DEVICE_LOSS_TRACE='1',MCVR_LOCAL_AFTERMATH='1',RADIANCE_CHUNK_TRACE='0')
    if spring_efficiency:
        env['RADIANCE_SPRING_DIRECT_WORLD']='1'
        args[1:1]=['-Dradiance.audit.springEfficiency=true','-Dradiance.extra.verifySpringTemplates=true']
    env.pop('MOD_CLASSES',None)
    env['RADIANCE_TAKEOVER_SOURCES']='1' if 'radiance_extra:flywheel' in expected.split(',') else '0'
    write(case/'launch.json',dict(args=args,storage=occupancy,mods=[dict(path=str(p),sha256=digest(p)) for p in jars],performanceMeasured=source_stress))
    with (case/'console.log').open('x',encoding='utf8') as out:
        process=subprocess.Popen(args,cwd=client,env=env,stdout=out,stderr=subprocess.STDOUT)
        write(case/'process.json',dict(pid=process.pid))
        deadline=time.monotonic()+450;verified=False
        while process.poll() is None and time.monotonic()<deadline:
            log=client/'logs/latest.log';content=log.read_text(encoding='utf8',errors='replace') if log.is_file() else ''
            if 'VK_ERROR_DEVICE_LOST' in content:
                write(RESULT/'DEVICE_LOST.json',dict(case=label,pid=process.pid,automaticRetry=False));deadline=min(deadline,time.monotonic()+30)
            ready='SOURCE_STRESS world-ready' if source_stress else 'ENTITY_TAKEOVER world-ready'
            if not verified and ready in content:
                loaded_core(process.pid,client,product,case/'loaded-core.json');verified=True
            time.sleep(1)
        if process.poll() is None:raise RuntimeError('Bounded session timed out; retain instance, do not restart')
    evidence=preserve(VALIDATION,client,case.name+'-evidence')
    log=(client/'logs/latest.log').read_text(encoding='utf8',errors='replace')
    required=('SPRING_WORLD normal-stop','All dimensions are saved') if spring_efficiency else (('SOURCE_STRESS PASS normal-stop','All dimensions are saved') if source_stress else ('PLATFORM_INTEGRATION PASS','ENTITY_TAKEOVER PASS normal-stop','All dimensions are saved'))
    if process.returncode or not verified or any(x in log for x in ('PLATFORM_INTEGRATION FAIL','ENTITY_TAKEOVER FAIL','SOURCE_STRESS FAIL','VK_ERROR_DEVICE_LOST')) or any(x not in log for x in required):
        write(case/'failure.json',dict(exit=process.returncode,evidence=str(evidence),automaticRetry=False));raise RuntimeError('Functional failure; retained: '+label)
    if veil_resources:
        resource_report=case/'veil-resources.json'
        if not resource_report.is_file() or json.loads(resource_report.read_text()).get('checkpoints')!=2:
            raise RuntimeError('Veil before/after reload resource checkpoints missing; retain instance')
    if spring_efficiency:
        spring=json.loads((client/'radiance-audit/spring-world/manifest.json').read_text())
        stats=spring.get('sourceCache', [])
        if len(stats)<5 or stats[1]<=10 or stats[4]<2:
            raise RuntimeError('Source spring cache/recomputation comparison did not execute')
        write(case/'spring-source-comparison.json',spring)
    write(case/'receipt.json',dict(passed=True,exitCode=process.returncode,evidence=str(evidence),normalSave=True,performanceMeasured=source_stress,userVisual='pending'))
    recycle(client,VALIDATION)
    print('Platform session passed: '+label,flush=True)

def manual(launch=False):
    case=RESULT/'manual'
    if launch:
        storage_preflight()
        receipt=json.loads((case/'prepared.json').read_text(encoding='utf8'))
        for artifact in receipt['products']:
            if digest(Path(artifact['path']))!=artifact['sha256']:raise RuntimeError('Prepared product changed; prepare a new candidate first')
        if not (CLIENT/'.radiance-platform-manual').is_file():raise RuntimeError('Manual instance marker missing')
        env=dict(os.environ,MCVR_DEVICE_LOSS_TRACE='1',MCVR_LOCAL_AFTERMATH='1');env.pop('MOD_CLASSES',None)
        return subprocess.run(receipt['args'],cwd=CLIENT,env=env).returncode
    occupancy=storage_preflight()
    if CLIENT.exists():raise RuntimeError('An existing instance is retained; do not replace user data')
    case.mkdir(parents=True,exist_ok=False)
    fixture.REPOSITORY_RUN=VALIDATION/'instance'
    fixture.create(CLIENT,'1.21.1',modded=True,tier=1)
    import nbtlib
    level=nbtlib.load(CLIENT/'saves/Audit Benchmark/level.dat');level['Data']['WorldGenSettings']['dimensions']['minecraft:overworld']['generator']['settings']['layers'][1]['height']=nbtlib.Int(63);level.save()
    enter=CLIENT/'saves/Audit Benchmark/datapacks/audit-fixture/data/audit/function/enter.mcfunction'
    enter.write_text(enter.read_text().replace('gamemode spectator','gamemode creative'),encoding='utf8')
    extract(VALIDATION/'result/settings-template.zip',CLIENT)
    options=CLIENT/'options.txt';rows=[x for x in options.read_text().splitlines() if not x.startswith(('soundCategory_master:','soundCategory_music:','renderDistance:','guiScale:','overrideWidth:','overrideHeight:','fullscreen:'))]
    options.write_text('\n'.join(rows)+'\nsoundCategory_master:0.5\nsoundCategory_music:0.0\nrenderDistance:8\nguiScale:2\noverrideWidth:0\noverrideHeight:0\nfullscreen:false\n',encoding='utf8')
    (CLIENT/'.radiance-platform-manual').write_text('User-operated integrated platform candidate; do not replace or start automatically.\n',encoding='utf8')
    product=next(p for p in (ROOT/f'versions/{TARGET}/build/libs').glob('radiance-*.jar') if not p.name.endswith('-sources.jar'))
    extra=next(p for p in (EXTRA/f'versions/{TARGET}/build/libs').glob('radiance_extra-*.jar') if not p.name.endswith('-sources.jar'))
    local=tomllib.loads((ROOT/'local.toml').read_text(encoding='utf8'));registry=tomllib.loads((ROOT/'inputs.toml').read_text(encoding='utf8'))
    jars=[product,extra]
    for name in ('create','sable','aeronautics_bundled','voxy','customskinloader-bootstrap','zume'):
        path=Path(local['inputs'][name]);h=digest(path)
        if h not in registry[name]['sha256']:raise RuntimeError('Manual input changed: '+name)
        jars.append(path)
    mods=CLIENT/'mods';mods.mkdir()
    for path in jars:(mods/path.name).symlink_to(path)
    cp=json.loads((ROOT/'build/takeover-stages/packaged-launch-classpath.json').read_text())['files']
    argument=os.pathsep.join(cp).replace('\\','\\\\').replace('"','\\"')
    (case/'classpath.args').write_text('-cp\n"'+argument+'"\n',encoding='utf8')
    java=next((Path.home()/'.gradle/jdks').glob('eclipse_adoptium-21*/bin/java.exe'))
    moddev=ROOT/f'versions/{TARGET}/build/moddev';aftermath=ROOT/'components/render_diagnostics/build/native/aftermath/RelWithDebInfo/radiance-aftermath-agent.dll'
    args=[str(java),f'-agentpath:{aftermath}=gpu-diagnostics',f'@{case/"classpath.args"}',f'@{moddev/"packagedClientRunVmArgs.txt"}',
          '-Xmx16G',f'@{moddev/"packagedClientRunProgramArgs.txt"}','--username','RecRivenVI','--width','2560','--height','1440']
    write(case/'prepared.json',dict(args=args,products=[dict(path=str(p),sha256=digest(p)) for p in jars],profile=dict(width=2560,height=1440,masterVolume=0.5,musicVolume=0,username='RecRivenVI'),launched=False,probeInstalled=False,world='Audit Benchmark'))
    print('Prepared manual platform instance; not launched: '+str(CLIENT),flush=True)
    return 0

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('case',choices=('core','extra-alone','sources','prepare-manual','manual'))
    choice=parser.parse_args().case
    if choice in ('prepare-manual','manual'):raise SystemExit(manual(choice=='manual'))
    run(choice)
