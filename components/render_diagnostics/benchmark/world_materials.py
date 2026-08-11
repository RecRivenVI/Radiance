"""Bounded packaged material/startup checks in an owned, disposable client."""
from pathlib import Path
import argparse
import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import threading
import time
import tomllib
import zipfile

import fixture
from entity_takeover import extract
from takeover_experiment import ROOT, TARGET, EXTRA_ROOT, WORKSPACE_SOFT_LIMIT_GIB, WORKSPACE_HARD_LIMIT_GIB, digest, recycle, storage_preflight, write

VALIDATION = ROOT / 'validations/conformance-takeover'
RESULT = VALIDATION / 'result/world-materials-134'
CLIENT = VALIDATION / 'instance' / TARGET / 'm134'


def world_probe_jar():
    # This session enables only WorldMaterialsProbe, not the full takeover census.
    # javac may retain identical old class files after formatting-only source changes;
    # the active probe and its changed poll seam must both have been compiled.
    base = ROOT / f'versions/{TARGET}'
    classes = base / 'build/classes/java/probe'
    for relative in ('com/radiance/audit/WorldMaterialsProbe', 'com/radiance/audit/EndSeaWorldMaterialProbe', 'com/radiance/audit/DisplayOutputProbe',
                     'com/radiance/audit/mixin/MinecraftAuditMixin'):
        source = base / ('src/probe/java/' + relative + '.java')
        compiled = classes / (relative + '.class')
        if not compiled.is_file() or source.stat().st_mtime_ns > compiled.stat().st_mtime_ns:
            raise RuntimeError('Compile the active world material probe first: ' + relative)
    output = ROOT / 'components/render_diagnostics/build/world-materials-probe.jar'
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
        for directory in (classes, base / 'build/resources/probe'):
            for file in directory.rglob('*'):
                if file.is_file(): archive.write(file, file.relative_to(directory).as_posix())
    return output


def display_fixture_pack(client, case):
    from PIL import Image
    destination=client/'resourcepacks/display-fixture.zip'
    destination.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(destination,'w',zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('pack.mcmeta',json.dumps(dict(pack=dict(pack_format=34,description='Owned emissive surface and mirror fixture'))))
        for name,colour in (('sea_lantern',(0,10,0,254)),('iron_block',(255,230,0,255))):
            image=Image.new('RGBA',(16,16),colour)
            data=io.BytesIO();image.save(data,format='PNG')
            archive.writestr('assets/minecraft/textures/block/'+name+'_s.png',data.getvalue())
    options=client/'options.txt'
    lines=options.read_text().splitlines()
    for i,line in enumerate(lines):
        if line.startswith('resourcePacks:'):
            packs=json.loads(line.partition(':')[2]);packs.append('file/display-fixture.zip')
            lines[i]='resourcePacks:'+json.dumps(packs)
            break
    else:lines.append('resourcePacks:'+json.dumps(['vanilla','file/display-fixture.zip']))
    options.write_text('\n'.join(lines)+'\n',encoding='utf8')
    write(case/'display-fixture.json',dict(path=str(destination),sha256=digest(destination),
        seaLanternSpecularAlpha=254,mirrorSpecularGreen=230,albedo='Existing source textures unchanged'))


def normal_fixture_pack(client, case):
    from PIL import Image

    destination = client / 'resourcepacks/labpbr-normal-fixture.zip'
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('pack.mcmeta', json.dumps(dict(pack=dict(
            pack_format=34, description='Radiance owned grazing normal fixture'))))
        for material, specular in (('yellow', (96, 10, 0, 255)), ('orange', (230, 255, 0, 255))):
            for suffix, colour in (('', (160, 160, 160, 255)), ('_s', specular), ('_n', None)):
                image = Image.new('RGBA', (16, 16), colour or (128, 128, 255, 255))
                if suffix == '_n':
                    for x in range(16):
                        for y in range(16):
                            image.putpixel((x, y), ((37, 128, 218, 128)[x // 4], 128, 255, 255))
                data = io.BytesIO()
                image.save(data, format='PNG')
                archive.writestr('assets/minecraft/textures/block/' + material + '_concrete' + suffix + '.png', data.getvalue())
    options = client / 'options.txt'
    lines = options.read_text(encoding='utf8').splitlines()
    packs = next((json.loads(line.partition(':')[2]) for line in lines if line.startswith('resourcePacks:')), [])
    packs.append('file/' + destination.name)
    lines = [line for line in lines if not line.startswith('resourcePacks:')]
    options.write_text('\n'.join(lines) + '\nresourcePacks:' + json.dumps(packs) + '\n', encoding='utf8')
    write(case / 'normal-fixture.json', dict(sha256=digest(destination), source='Generated solid albedo and byte-normal stripes; no third-party assets',
                                          normalRedBytes=[37, 128, 218, 128], normalGreenByte=128,
                                          heightByte=255, emissionByte=255, materials=['diffuse', 'metal']))


def run(mode, label, core, extra=None, reuse_cache=None, shader_pack=None, shader_preflight=None, workspace_limit_gib=WORKSPACE_SOFT_LIMIT_GIB, algorithm="vanilla-pt", external_limit_gib=8, settings_input=None):
    if shader_pack is not None:
        report=json.loads(shader_preflight.read_text()) if shader_preflight else {}
        if not report.get('passed') or report.get('packSha256')!=digest(shader_pack):
            raise RuntimeError('Startup isolation requires a passing preflight for this exact pack')
    occupancy = storage_preflight(validation_limit_gib=8, workspace_limit_gib=workspace_limit_gib, external_limit_gib=external_limit_gib)
    if workspace_limit_gib is not None and occupancy['workspace'] + 500_000_000 > WORKSPACE_HARD_LIMIT_GIB * 1024**3:
        raise RuntimeError('Insufficient workspace hard-limit reserve for an owned client')
    if occupancy['validations'] + 500_000_000 > 8 * 1024**3:
        raise RuntimeError('Insufficient validation reserve')
    if CLIENT.exists() or (RESULT / 'DEVICE_LOST.json').exists():
        raise RuntimeError('Retained client or device-loss boundary; inspect before continuing')
    case = RESULT / (label + '-' + mode + '-' + time.strftime('%Y%m%d-%H%M%S'))
    case.mkdir(parents=True, exist_ok=False)
    fixture.REPOSITORY_RUN = VALIDATION / 'instance'
    fixture.create(CLIENT, '1.21.1', modded=False, tier=1)
    extract(VALIDATION / 'result/settings-template.zip', CLIENT)
    if settings_input is not None:
        if mode != 'levitite-stress':
            raise ValueError('The registered gameplay settings override is limited to the effect benchmark')
        registry = tomllib.loads((ROOT/'inputs.toml').read_text(encoding='utf8'))
        local = tomllib.loads((ROOT/'local.toml').read_text(encoding='utf8'))
        source = Path(local['inputs'][settings_input])
        if digest(source) not in registry[settings_input]['sha256']:
            raise RuntimeError('Changed registered gameplay settings')
        extract(source, CLIENT)
        write(case/'gameplay-settings-input.json', dict(name=settings_input, sha256=digest(source)))
    (CLIENT / '.radiance-takeover-stages').write_text('Owned material/startup check\n', encoding='utf8')
    if reuse_cache is not None:
        extract(reuse_cache, CLIENT)
        write(case/'cache-input.json',dict(path=str(reuse_cache),sha256=digest(reuse_cache)))
    settings=CLIENT/'radiance/pipeline.yaml'
    if shader_pack is not None:
        if mode!='startup':raise RuntimeError('Compile isolation must not enter a world')
        selected=CLIENT/'radiance/shader-packs/startup-isolation.zip'
        selected.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(shader_pack,selected)
        text=settings.read_text(encoding='utf8')
        text,count=re.subn(r'(name: render_pipeline.module.ray_tracing.attribute.shader_pack_path\n\s+type: string\n\s+value: )[^\n]+',lambda m:m[1]+"'shader-packs/startup-isolation.zip'",text)
        if count!=1:raise RuntimeError('Shader selection missing')
        settings.write_text(text,encoding='utf8')
        write(case/'shader-isolation.json',dict(path=str(shader_pack),sha256=digest(shader_pack),worldEntered=False))
    if algorithm != 'vanilla-pt':
        text,count=re.subn(r'(name: render_pipeline.module.ray_tracing.attribute.shader_pack_path\n\s+type: string\n\s+value: )[^\n]+',
                           lambda m:m[1]+"'shaders/world/ray_tracing/"+algorithm+".zip'",settings.read_text(encoding='utf8'))
        if count!=1:raise RuntimeError('Built-in shader selection missing')
        settings.write_text(text,encoding='utf8')
        with zipfile.ZipFile(core) as product:
            with zipfile.ZipFile(io.BytesIO(product.read('shaders/world/ray_tracing/'+algorithm+'.zip'))) as pack:
                attributes=json.loads(pack.read('configs.json'))['attributes']
        destination=CLIENT/('radiance/shaders/world/ray_tracing/'+algorithm+'.zip.txt')
        destination.write_text(''.join(a['name']+'='+str(a['default_value'])+'\n' for a in attributes),encoding='utf8')
        options_file=CLIENT/'radiance/options.properties'
        options_text=options_file.read_text(encoding='utf8') if options_file.is_file() else ''
        options_file.write_text('\n'.join(line for line in options_text.splitlines() if not line.startswith('collectChunkEmission='))+'\ncollectChunkEmission=true\n',encoding='utf8')
        write(case/'emission-input.json',dict(collectChunkEmission=True,reason='Required by the selected built-in algorithm; owned functional fixture only'))
    elif shader_pack is None:
        text,count=re.subn(r'(name: render_pipeline.module.ray_tracing.attribute.shader_pack_path\n\s+type: string\n\s+value: )[^\n]+',
                           lambda m:m[1]+"'shaders/world/ray_tracing/vanilla-pt.zip'",settings.read_text(encoding='utf8'))
        if count!=1:raise RuntimeError('Built-in shader selection missing')
        settings.write_text(text,encoding='utf8')

    write(case/'algorithm.json',dict(builtIn=algorithm))
    write(case/'settings-input.json',dict(source='settings-template.zip',sha256=digest(settings)))
    if mode in ('surfaces','normals','endsea','endsea-end','endsea-far','display'):
        text=settings.read_text(encoding='utf8')
        controls=dict(exposure_up_speed='1.0',exposure_down_speed='3.0',
                      log2_luminance_min='-20.0',max_exposure='65536.0')
        shader_controls={}
        if mode in ('surfaces','normals','endsea','endsea-end','endsea-far','display'):
            with zipfile.ZipFile(core) as product:
                with zipfile.ZipFile(io.BytesIO(product.read('shaders/world/ray_tracing/'+algorithm+'.zip'))) as pack:
                    attributes=json.loads(pack.read('configs.json'))['attributes']
            moon=next(a for a in attributes if a['name'].endswith('.attribute.moon_radiance'))
            shader_controls={moon['name']:moon['default_value'],
                             'render_pipeline.module.ray_tracing.attribute.num_ray_bounces':'8'}
            shader_settings=CLIENT/('radiance/shaders/world/ray_tracing/'+algorithm+'.zip.txt')
            properties=shader_settings.read_text(encoding='utf8')
            for key,value in shader_controls.items():
                properties,count=re.subn(r'(?m)^'+re.escape(key)+r'=[^\n]*$',
                                         lambda _:key+'='+value,properties)
                if count!=1:raise RuntimeError('Controlled shader material configuration absent: '+key)
            shader_settings.write_text(properties,encoding='utf8')
        for name,value in controls.items():
            expression=r'(name: render_pipeline.module.tone_mapping.attribute.'+name+r'\n\s+type: [^\n]+\n\s+value: )[^\n]+'
            text,count=re.subn(expression,lambda match:match[1]+"'"+value+"'",text)
            if count!=1:raise RuntimeError('Controlled material configuration absent: '+name)
        settings.write_text(text,encoding='utf8')
        write(case/'controlled-settings.json',dict(values=controls,shaderValues=shader_controls,sha256=digest(settings),
                                                   scope='Owned functional fixture only; user configuration preserved'))
    shutil.copyfile(settings,case/'pipeline-input.yaml')
    shader_settings=CLIENT/('radiance/shaders/world/ray_tracing/'+algorithm+'.zip.txt')
    shutil.copyfile(shader_settings,case/'shader-attributes-input.txt')
    if settings_input is not None:
        from renderer_settings import expectations
        write(case/'expected-rendering.json', expectations(core, CLIENT))
    options = CLIENT / 'options.txt'
    lines = [line for line in options.read_text().splitlines()
             if not line.startswith(('soundCategory_master:', 'soundCategory_music:', 'pauseOnLostFocus:'))]
    options.write_text('\n'.join(lines) + '\nsoundCategory_master:0.0\nsoundCategory_music:0.0\npauseOnLostFocus:false\n', encoding='utf8')
    if mode == 'normals':
        normal_fixture_pack(CLIENT, case)
    if mode == 'display':
        display_fixture_pack(CLIENT, case)
    mods = CLIENT / 'mods'
    mods.mkdir()
    products = [core.resolve(), world_probe_jar()]
    if extra is not None:
        products.append(extra.resolve())
        if mode in ('handoff','levitite','levitite-ponder','levitite-stress'):
            base = EXTRA_ROOT / f'versions/{TARGET}/build'
            probe_name={'levitite-stress':'LevititeStressProbe','levitite':'LevititeWorldProbe','levitite-ponder':'LevititePonderProbe','handoff':'SableHandoffProbe'}[mode]
            source = base.parent / ('src/probe/java/com/radiance/extra/audit/'+probe_name+'.java')
            compiled = base / ('classes/java/probe/com/radiance/extra/audit/'+source.stem+'.class')
            if not compiled.is_file() or source.stat().st_mtime_ns > compiled.stat().st_mtime_ns:
                raise RuntimeError('Compile the active Extra handoff probe first')
            probe = base / 'libs/radiance_extra-handoff-probe.jar'
            with zipfile.ZipFile(probe, 'w', zipfile.ZIP_DEFLATED) as archive:
                for directory in (base/'classes/java/probe', base/'resources/probe'):
                    for file in directory.rglob('*'):
                        if file.is_file(): archive.write(file, file.relative_to(directory).as_posix())
            products.append(probe)
        local = tomllib.loads((ROOT / 'local.toml').read_text(encoding='utf8'))
        registry = tomllib.loads((ROOT / 'inputs.toml').read_text(encoding='utf8'))
        inputs = ('create', 'sable', 'aeronautics_bundled')
        if mode not in ('endsea-end','endsea-far', 'handoff','levitite','levitite-ponder','levitite-stress'): inputs += ('voxy',)
        for name in inputs:
            path = Path(local['inputs'][name])
            if digest(path) not in registry[name]['sha256']:
                raise RuntimeError('Local input identity changed: ' + name)
            products.append(path)
    for product in products:
        (mods / product.name).symlink_to(product)
    cp = json.loads((ROOT / 'build/takeover-stages/packaged-launch-classpath.json').read_text())['files']
    if any('/classes/java/main' in p.replace('\\', '/') for p in cp):
        raise RuntimeError('Development product classes shadow packaged JAR')
    argument = os.pathsep.join(cp).replace('\\', '\\\\').replace('"', '\\"')
    (case / 'classpath.args').write_text('-cp\n"' + argument + '"\n', encoding='utf8')
    java = next((Path.home() / '.gradle/jdks').glob('eclipse_adoptium-21*/bin/java.exe'))
    moddev = ROOT / f'versions/{TARGET}/build/moddev'
    args = [str(java),
            f'-javaagent:{ROOT / "components/render_diagnostics/build/benchmark-support/radiance-benchmark-agent.jar"}',
            f'-agentpath:{ROOT / "components/render_diagnostics/build/native/aftermath/RelWithDebInfo/radiance-aftermath-agent.dll"}=gpu-diagnostics',
            '-Dradiance.audit.experiments=true', '-Dradiance.audit.unattended=true',
            '-Dradiance.audit.benchmark=true', '-Dradiance.audit.benchmark.version=1.21.1-neoforge',
            '-Dradiance.audit.correctnessOnly=true',
            '-Dradiance.extra.sourceAudit=true',
            '-Dradiance.audit.worldMaterials134=' + mode, '-Dvalidation.result=' + str(case),
            '-Dradiance.audit.cityWidth=1920', '-Dradiance.audit.cityHeight=1080',
            '-Dradiance.audit.cityBorderless=true',
            f'-XX:StartFlightRecording=filename={case / "startup.jfr"},settings=profile,dumponexit=true,maxsize=32m',
            f'@{case / "classpath.args"}', f'@{moddev / "packagedClientRunVmArgs.txt"}',
            '-Xmx4G', f'@{moddev / "packagedClientRunProgramArgs.txt"}',
            '--username', 'RecRivenVI', '--width', '1920', '--height', '1080']
    if mode != 'startup':
        args += ['--quickPlaySingleplayer', 'Audit Benchmark']
    if settings_input is not None:
        args.insert(1, '-Dradiance.audit.expectedRenderingConfig=' + str(case/'expected-rendering.json'))
        args.insert(1, '-Xms512M')
        args[next(i for i,value in enumerate(args) if value.startswith('-Xmx'))] = '-Xmx16G'
    env = dict(os.environ, MCVR_DEVICE_LOSS_TRACE='1', MCVR_LOCAL_AFTERMATH='1')
    if mode == 'levitite-stress':
        env['RADIANCE_CHUNK_PERF'] = '1'
    if mode == 'levitite':
        env['RADIANCE_AUDIT_COMPUTED_SURFACES'] = '1'
    if mode == 'display':
        args = [value for value in args if not value.startswith('-XX:StartFlightRecording=')]
        env['RADIANCE_CAPTURE_SINGLE_FRAME'] = '1'
        if os.environ.get('RADIANCE_DISPLAY_CONTROLS_ONLY')=='1':args.insert(1,'-Dradiance.audit.displayControlsOnly=true')
        args.insert(1, '-Dradiance.audit.capture=true')
    env.pop('MOD_CLASSES', None)
    write(case / 'launch.json', dict(args=args, storage=occupancy,externalLimitGiB=external_limit_gib,workspaceLimitSuspended=workspace_limit_gib is None,pipelineSha256=digest(settings),shaderAttributesSha256=digest(shader_settings),runnerSha256=digest(Path(__file__)),
                                   products=[dict(path=str(p), sha256=digest(p)) for p in products]))
    with (case / 'console.log').open('x', encoding='utf8') as output:
        process = subprocess.Popen(args, cwd=CLIENT, env=env, stdout=output, stderr=subprocess.STDOUT)
        write(case / 'process.json', dict(pid=process.pid))
        stopped = threading.Event()
        observers = []
        if mode == 'levitite-stress':
            from vanilla_performance import monitor
            from renderer_campaign import observe_wddm
            observers = [threading.Thread(target=monitor, args=(case, stopped, process.pid)),
                         threading.Thread(target=observe_wddm, args=(case,))]
            for observer in observers:
                observer.start()
        try:
            deadline = time.monotonic() + 300
            while process.poll() is None and time.monotonic() < deadline:
                log = CLIENT / 'logs/latest.log'
                content = log.read_text(encoding='utf8', errors='replace') if log.is_file() else ''
                content += (case / 'console.log').read_text(encoding='utf8', errors='replace')
                if 'VK_ERROR_DEVICE_LOST' in content:
                    write(RESULT / 'DEVICE_LOST.json', dict(case=str(case), automaticRetry=False))
                    deadline = min(deadline, time.monotonic() + 30)
                time.sleep(1)
            if process.poll() is None:
                raise RuntimeError('Owned bounded session timed out; retained for diagnosis')
        finally:
            stopped.set()
            for observer in observers:
                observer.join(timeout=16)
    retain_completed_run(case, process.returncode)


def verify_display_output(case):
    """Read the real post-GUI and post-encoding GPU captures, not screenshot brightness."""
    import math
    import struct
    import numpy as np
    report=json.loads((case/'world-materials.json').read_text())
    scale=report['referencePatchScale']
    controls_only='-Dradiance.audit.displayControlsOnly=true' in json.loads((case/'launch.json').read_text())['args']
    expected={'hdr-luminance-update','system-ui-white'} if controls_only else {'sdr-no-bloom','sdr-bloom','auto-hdr-bloom','hdr-luminance-update','system-ui-white'}
    results=[]
    with zipfile.ZipFile(case/'client-evidence.zip') as archive:
        for name in archive.namelist():
            if not name.startswith('radiance-display-captures/') or not name.endswith('/status.txt'):continue
            prefix=name.rsplit('/',1)[0]+'/'
            label=archive.read(prefix+'../request.accepted').decode().strip() if prefix+'../request.accepted' in archive.namelist() else archive.read(prefix.rsplit('frame-',1)[0]+'request.accepted').decode().strip()
            if label not in expected:raise AssertionError('Unexpected or duplicate display capture '+label)
            if archive.read(name).decode().strip()!='COMPLETE':raise AssertionError('GPU capture did not complete')
            metadata=dict(line.split('=',1) for line in archive.read(prefix+'display.txt').decode().splitlines())
            rows={row.split('\t')[0]:row.split('\t')[1:] for row in archive.read(prefix+'images.tsv').decode().splitlines()}
            width,height,fmt,size=map(int,rows['display-canvas'])
            canvas=np.frombuffer(archive.read(prefix+'display-canvas.bin'),dtype='<f2').reshape(height,width,4)
            fmt=int(rows['display-encoded'][2]); raw=archive.read(prefix+'display-encoded.bin')
            encoding=int(metadata['encoding']);ui_white=float(metadata['uiWhite'])
            if label=='hdr-luminance-update':
                if ui_white!=120 or float(metadata['referenceWhite'])!=180 or (encoding and float(metadata['peakNits'])!=800):
                    raise AssertionError('Requested display brightness was not applied '+str(metadata))
            if label=='system-ui-white':
                system_white=float(metadata['systemWhite'])
                if metadata['uiWhiteAutomatic']!='1' or abs(ui_white-min(1000,max(80,system_white)))>.01 or float(metadata['referenceWhite'])!=200:
                    raise AssertionError('Automatic GUI white did not match the current display '+str(metadata))
                if not abs(float(report['displayScenes'][-1]['uiWhite']))<1e-6:raise AssertionError('Auto setting was not retained')
            def decode_srgb(v):return v/12.92 if v<=0.04045 else ((v+0.055)/1.055)**2.4
            def decode_pq(v):
                p=v**(32/2523)
                return 10000*(max(p-3424/4096,0)/(2413/128-2392/128*p))**(16384/2610)
            patches=[]
            for x,value in ((16,1.),(34,128/255)):
                ix,iy=int(x*scale),int(16*scale)
                before=canvas[iy,ix,:3].astype(float)
                if np.max(np.abs(before-value))>.003:raise AssertionError('GUI patch not preserved before output '+str(before))
                index=iy*width+ix
                if fmt in (37,43,44,50):
                    after=np.frombuffer(raw,dtype='u1').reshape(height,width,4)[iy,ix,:3]/255
                    if fmt in (44,50):after=after[::-1]
                    target=value;tolerance=2/255
                elif fmt in (58,64):
                    packed=struct.unpack_from('<I',raw,index*4)[0]
                    channels=[(packed>>shift)&1023 for shift in (0,10,20)]
                    if fmt==58:channels.reverse()
                    after=np.array([decode_pq(c/1023) for c in channels])
                    target=decode_srgb(value)*ui_white;tolerance=max(.6,target*.012)
                elif fmt==97:
                    after=np.frombuffer(raw,dtype='<f2').reshape(height,width,4)[iy,ix,:3].astype(float)*80
                    target=decode_srgb(value)*ui_white;tolerance=max(.05,target*.002)
                else:raise AssertionError('Unsupported display capture format '+str(fmt))
                if np.max(np.abs(after-target))>tolerance:raise AssertionError('Display encoding mismatch '+str((after,target)))
                patches.append(dict(source=before.tolist(),output=after.tolist(),target=target,unit='nit' if encoding else 'sRGB'))
            exposure,log_lum,valid,_=struct.unpack('<ffII',archive.read(prefix+'exposure.bin'))
            if not math.isfinite(exposure) or exposure<=0 or not math.isfinite(log_lum) or valid!=1:raise AssertionError('Invalid real exposure state')
            hdr=np.frombuffer(archive.read(prefix+'world-hdr.bin'),dtype='<f2')
            if not np.all(np.isfinite(hdr)) or not np.any(hdr>0):raise AssertionError('Invalid world HDR radiance')
            peak_nits=None
            if encoding:
                if fmt in (58,64):
                    words=np.frombuffer(raw,dtype='<u4')
                    code=max(int(((words>>shift)&1023).max()) for shift in (0,10,20))/1023
                    peak_nits=decode_pq(code)
                else:
                    peak_nits=float(np.frombuffer(raw,dtype='<f2').reshape(-1,4)[:,:3].max())*80
                if peak_nits>float(metadata['peakNits'])*1.015:raise AssertionError('HDR output exceeded selected peak')
                if label=='auto-hdr-bloom' and peak_nits<=float(metadata['referenceWhite']):raise AssertionError('World highlights lost HDR headroom')
            results.append(dict(scene=label,metadata=metadata,patches=patches,exposure=exposure,meanLogLuminance=log_lum,peakOutputComponentNits=peak_nits))
            expected.remove(label)
    if expected:raise AssertionError('Missing completed display captures '+str(expected))
    console=(case/'console.log').read_text(errors='replace')
    update=console.split('DISPLAY_LUMINANCE_UPDATE begin',1)[1].split('DISPLAY_LUMINANCE_UPDATE end',1)[0]
    if 'CPU pipeline-create begin' in update or '[radiance-recreate] rebuild images=' in update:
        raise AssertionError('Brightness-only controls rebuilt world resources')
    write(case/'display-encoding-result.json',dict(passed=True,captures=results,visualAcceptance='pending-real-display'))


def retain_completed_run(case, exit_code):
    # Read evidence back before recycling its environment. Fault evidence is never discarded.
    evidence_hashes = {}
    def retain(archive, path):
        name = path.relative_to(CLIENT).as_posix()
        evidence_hashes[name] = digest(path)
        archive.write(path, name)
    with zipfile.ZipFile(case / 'client-evidence.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
        for relative in ('options.txt','radiance/options.properties','radiance/pipeline.yaml','radiance/shaders/world/ray_tracing/vanilla-pt.zip.txt','radiance/shaders/world/ray_tracing/advanced.zip.txt'):
            path=CLIENT/relative
            if path.is_file():retain(archive,path)
        for pattern in ('hs_err_pid*','*.nv-gpudmp','radiance-*.log','radiance-*.jsonl'):
            for path in CLIENT.glob(pattern):
                if path.is_file(): retain(archive, path)
        for directory in ('logs', 'crash-reports', 'gpu-diagnostics', 'radiance-gpu-shaders', 'radiance-audit', 'screenshots', 'computed-surface-audit', 'radiance-display-captures'):
            for path in (CLIENT / directory).rglob('*'):
                if path.is_file():
                    retain(archive, path)
    with zipfile.ZipFile(case / 'client-evidence.zip') as archive:
        for name, expected in evidence_hashes.items():
            if hashlib.sha256(archive.read(name)).hexdigest() != expected:
                raise RuntimeError('Evidence readback failed; client retained: ' + name)
    write(case / 'evidence-index.json', evidence_hashes)
    if (CLIENT/'radiance/cache').is_dir():
        from takeover_experiment import archive
        archive([p for p in (CLIENT/'radiance/cache').rglob('*') if p.is_file()],CLIENT,case/'owned-cache.zip')
    # Keep this bounded case's captures directly viewable after retiring its copied client.
    for path in (CLIENT / 'screenshots').glob('*.png'):
        destination=case/'screenshots'/path.name
        destination.parent.mkdir(exist_ok=True)
        shutil.copyfile(path,destination)
        if digest(destination)!=digest(path):raise RuntimeError('Screenshot copy verification failed')
    report = case / 'world-materials.json'
    device_lost = 'VK_ERROR_DEVICE_LOST' in (case/'console.log').read_text(encoding='utf8', errors='replace')
    if device_lost:
        write(RESULT/'DEVICE_LOST.json', dict(case=str(case), automaticRetry=False))
    selected=re.search(r'attribute.shader_pack_path\n\s+type: string\n\s+value: ([^\n]+)',(CLIENT/'radiance/pipeline.yaml').read_text(encoding='utf8'))
    requested='shader-packs/startup-isolation.zip' if (case/'shader-isolation.json').exists() else 'shaders/world/ray_tracing/'+json.loads((case/'algorithm.json').read_text())['builtIn']+'.zip'
    actual=selected[1].strip().strip("\"'") if selected else None
    algorithm_matches=actual==requested
    write(case/'algorithm-result.json',dict(requested=requested,actual=actual,matches=algorithm_matches))
    passed = report.is_file() and json.loads(report.read_text())['passed'] and not device_lost and algorithm_matches
    if passed and 'displayScenes' in json.loads(report.read_text()):
        verify_display_output(case)
    if (case/'gameplay-settings-input.json').exists() and not (case/'effective-levitite-stress.json').is_file():
        passed = False
    write(case / 'summary.json', dict(exitCode=exit_code, passed=passed,
                                    automaticRetry=False, visualAcceptance='pending'))
    if device_lost:
        raise RuntimeError('Device loss preserved; retain the client and stop the entire case group')
    recycle(CLIENT, VALIDATION)
    print(case, flush=True)
    if not passed or exit_code:
        raise RuntimeError('Check failed; evidence preserved, no automatic retry')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=('startup', 'end','surfaces','normals','handoff','levitite','levitite-ponder','levitite-stress','endsea','endsea-end','endsea-far','height','environment','display'))
    parser.add_argument('label')
    parser.add_argument('core', type=Path)
    parser.add_argument('--extra', type=Path)
    parser.add_argument('--workspace-limit-suspended', action='store_true', help='Requires explicit per-task user authorization; validation/external caps remain active')
    parser.add_argument('--external-limit-gib', type=int, choices=(8,9), default=8, help='9 requires explicit per-task user authorization')
    parser.add_argument('--algorithm', choices=('vanilla-pt','advanced'), default='vanilla-pt')
    parser.add_argument('--reuse-cache', type=Path)
    parser.add_argument('--settings-input')
    parser.add_argument('--shader-pack', type=Path)
    parser.add_argument('--shader-preflight', type=Path)
    parser.add_argument('--workspace-limit-gib', type=int, choices=range(1,WORKSPACE_HARD_LIMIT_GIB+1), default=WORKSPACE_SOFT_LIMIT_GIB,
                        help='Defaults to the soft limit; above-soft use requires cleanup and user approval')
    arguments = parser.parse_args()
    run(arguments.mode, arguments.label, arguments.core, arguments.extra, arguments.reuse_cache, arguments.shader_pack, arguments.shader_preflight, None if arguments.workspace_limit_suspended else arguments.workspace_limit_gib, arguments.algorithm, arguments.external_limit_gib, arguments.settings_input)
