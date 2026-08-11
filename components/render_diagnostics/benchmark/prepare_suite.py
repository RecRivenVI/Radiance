"""Prepare matched three-target cases without launching or changing original packages."""
from pathlib import Path
import argparse
import hashlib
import json
import zipfile
import io
import prepare_case
import fixture
import profiles

ROOT=Path(__file__).resolve().parents[3]
FORK_SHA='89FDB75B828F06B565133E8818D0DCF74A0D00B2CCACF06080E2EFD6D564BA63'

def shader_config(jar):
    with zipfile.ZipFile(jar) as package:
        paths=[n for n in package.namelist() if n.endswith('/advanced.zip')]
        if len(paths)!=1:raise ValueError('Expected one packaged Advanced shader: '+str(jar))
        payload=package.read(paths[0])
    with zipfile.ZipFile(io.BytesIO(payload)) as shaders:
        configs=shaders.read('configs.json')
    return json.loads(configs),dict(jar=str(jar),entry=paths[0],
                                  packSha256=hashlib.sha256(payload).hexdigest().upper(),
                                  configSha256=hashlib.sha256(configs).hexdigest().upper())


def prepare(output, target, scene, tier=1, warmup=60, sample=30, width=2560, height=1440):
    if scene=='factory' and target=='upstream-fabric':raise ValueError('1.21.4 has no matching modded comparison')
    template=ROOT/'run/upstream-benchmarks-20260925'/('fabric-final-preflight' if target=='upstream-fabric' else 'neo-final-preflight')
    fork=ROOT/'build/libs/Radiance-0.1.5-alpha-neoforge-1.21.1.jar'
    if hashlib.sha256(fork.read_bytes()).hexdigest().upper()!=FORK_SHA:raise ValueError('Fork artifact changed; explicitly re-pin before preparing comparisons')
    output=Path(output).resolve()
    launch_path=prepare_case.prepare(template,output,warmup,sample,scene=scene,tier=tier,renderer=fork if target=='fork' else None)
    renderer=next((output/'mods').glob('Radiance-*.jar'))
    target_config,target_identity=shader_config(renderer)
    reference_config,reference_identity=shader_config(fork)
    profile=profiles.install(output,target,width,height,target_config=target_config,reference_config=reference_config)
    profile['shaderConfigIdentity']={'target':target_identity,'reference':reference_identity}
    launch=json.loads(launch_path.read_text())
    args=launch['args']
    args=[a for a in args if not a.startswith(('-Xmx','-Xms'))]
    args[:0]=['-Xms2G','-Xmx8G']
    if target=='fork':args[:0]=['-Dradiance.rigidModels=true','-Dradiance.rigidParts=false']
    for name,value in [('width',width),('height',height)]:
        if '--'+name in args:args[args.index('--'+name)+1]=str(value)
    # Upstream explicitly expects manually installed DLLs in radiance/. Reuse the exact
    # NVIDIA runtime shipped by the pinned fork for LOCAL comparison; never replace core.dll.
    supplemental=[];profile['nvidiaRuntimeHashes']={}
    with zipfile.ZipFile(fork) as z:
        for name in ['nvngx_dlss.dll','nvngx_dlssd.dll','nvngx_dlssg.dll','LICENSE.txt','NOTICE.txt','runtime-manifest.json']:
            payload=z.read('dlss/'+name)
            if name.endswith('.dll'):profile['nvidiaRuntimeHashes'][name]=hashlib.sha256(payload).hexdigest().upper()
            dest=output/'radiance'/('benchmark-nvidia-notices' if not name.endswith('.dll') else '')/name
            if target=='fork' and name.endswith('.dll'):continue # Original fork bootstrap extracts its own payload.
            dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(payload)
            supplemental.append({'path':str(dest),'sha256':hashlib.sha256(payload).hexdigest().upper(),'source':'Pinned fork dlss/'+name})
    launch['args']=args;launch['artifacts']+=supplemental
    (output/'REQUESTED_SETTINGS.json').write_text(json.dumps(profile,indent=2)+'\n')
    launch['requestedSettings']=profile
    launch['classification']='PREPARED_NOT_MEASURED'
    launch['timeoutSeconds']=max(240,warmup+sample+150)
    # Pin non-binary inputs as well; modified settings must generate a new case.
    initial=json.loads((output/'FIXTURE.json').read_text())
    initial['files']={p:hashlib.sha256((output/p).read_bytes()).hexdigest() for p in initial['files']}
    (output/'FIXTURE.json').write_text(json.dumps(initial,indent=2)+'\n')
    launch['inputs']=[{'path':str(p),'sha256':hashlib.sha256(p.read_bytes()).hexdigest().upper()}
                     for p in [output/'options.txt',output/'radiance/options.properties',output/'radiance/pipeline.yaml',output/'FIXTURE.json',output/'REQUESTED_SETTINGS.json',Path(profile['shaderAttributeStorage'])]]
    launch_path.write_text(json.dumps(launch,indent=2)+'\n')
    return launch_path


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('output',type=Path);p.add_argument('--target',choices=profiles.TARGETS,required=True)
    p.add_argument('--scene',choices=('model-city','terrain-city','factory'),required=True)
    p.add_argument('--tier',type=int,choices=(1,2),default=1);p.add_argument('--warmup',type=int,default=60);p.add_argument('--sample',type=int,default=30)
    p.add_argument('--width',type=int,default=2560);p.add_argument('--height',type=int,default=1440)
    a=p.parse_args();print(prepare(a.output,a.target,a.scene,a.tier,a.warmup,a.sample,a.width,a.height))
