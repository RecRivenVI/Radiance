"""Prepare an explicitly pinned fork artifact; never overwrite a historical case."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import fixture
import prepare_case
import prepare_suite
import profiles


def identity(path):
    return dict(path=str(path), sha256=hashlib.sha256(path.read_bytes()).hexdigest().upper())


def prepare(output, renderer, expected_sha, scene='model-city', warmup=60, sample=30, profile=False,
            *, direct=False, compact=False):
    renderer=Path(renderer).resolve()
    if identity(renderer)['sha256'] != expected_sha.upper():
        raise ValueError('Explicit renderer identity mismatch')
    root=fixture.REPOSITORY_RUN
    template=root/'upstream-benchmarks-20260925/neo-final-preflight'
    output=Path(output).resolve()
    launch_path=prepare_case.prepare(template,output,warmup,sample,scene=scene,tier=1,renderer=renderer)
    config,shader_identity=prepare_suite.shader_config(renderer)
    requested=profiles.install(output,'fork',target_config=config,reference_config=config)
    requested['shaderConfigIdentity']={'target':shader_identity,'reference':shader_identity}
    launch=json.loads(launch_path.read_text())
    args=[a for a in launch['args'] if not a.startswith(('-Xmx','-Xms'))]
    args[:0]=['-Xms2G','-Xmx8G','-Dradiance.rigidModels=true','-Dradiance.rigidParts=false',
              '-Dradiance.directEntityInput='+str(direct).lower(),
              '-Dradiance.compactVertices='+str(compact).lower()]
    for name,value in [('width',requested['width']),('height',requested['height'])]:
        if '--'+name in args:args[args.index('--'+name)+1]=str(value)
    if profile:
        # Both are the same mod id: replace the portable adapter, retaining the startup agent.
        adapter=next((output/'mods').glob('RadianceAudit-*-benchmark-neoforge-1.21.1.jar'))
        archive=output/'diagnostics/unused-portable-adapter.jar'
        adapter.rename(archive)
        normal=Path(__file__).resolve().parents[1]/'build/libs/RadianceAudit-0.1.5-alpha.jar'
        shutil.copy2(normal,output/'mods'/normal.name)
        args[:0]=[f'-Dradiance.audit.profileDelaySeconds={warmup}',
                  f'-Dradiance.audit.profileSeconds={max(1,sample-5)}']
    requested['observerMode']='fork-native-profile' if profile else 'portable-frame-only'
    requested['forkInputVariant']={'directEntityInput':direct,'compactVertices':compact,
                                  'runtimeActivation':'Check matching native profile counters; request alone is not proof'}
    launch.update(args=args,requestedSettings=requested,classification='PREPARED_NOT_MEASURED',
                  timeoutSeconds=max(240,warmup+sample+150))
    launch['artifacts']=[identity(p) for p in [*sorted((output/'mods').glob('*.jar')),
                                              *sorted((output/'diagnostics').glob('*.jar'))]]
    (output/'REQUESTED_SETTINGS.json').write_text(json.dumps(requested,indent=2)+'\n')
    initial=json.loads((output/'FIXTURE.json').read_text())
    initial['files']={p:hashlib.sha256((output/p).read_bytes()).hexdigest() for p in initial['files']}
    (output/'FIXTURE.json').write_text(json.dumps(initial,indent=2)+'\n')
    launch['inputs']=[identity(p) for p in [output/'options.txt',output/'radiance/options.properties',
                    output/'radiance/pipeline.yaml',output/'FIXTURE.json',output/'REQUESTED_SETTINGS.json',
                    Path(requested['shaderAttributeStorage'])]]
    launch_path.write_text(json.dumps(launch,indent=2)+'\n')
    return launch_path


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('output',type=Path);p.add_argument('--renderer',type=Path,required=True)
    p.add_argument('--sha256',required=True);p.add_argument('--scene',choices=('model-city','factory'),default='model-city')
    p.add_argument('--warmup',type=int,default=60);p.add_argument('--sample',type=int,default=30)
    p.add_argument('--profile',action='store_true');p.add_argument('--direct',action='store_true')
    p.add_argument('--compact',action='store_true');a=p.parse_args()
    print(prepare(a.output,a.renderer,a.sha256,a.scene,a.warmup,a.sample,a.profile,direct=a.direct,compact=a.compact))
