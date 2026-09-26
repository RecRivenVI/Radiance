"""Make a fresh run from a successful isolated template and the current observer build.

The template supplies its exact runtime classpath and mod artifacts. This script never
installs/upgrades a loader or silently swaps the renderer under the template's settings.
"""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import fixture


def prepare(template, output, warmup=30, sample=30, *, scene=None, tier=None, renderer=None):
    template, output = Path(template).resolve(), Path(output).resolve()
    if not template.is_relative_to(fixture.REPOSITORY_RUN.resolve()):
        raise ValueError('template must be a repository run instance')
    if not 1 <= warmup <= 3600 or not 1 <= sample <= 3600:
        raise ValueError('durations must be 1..3600 seconds')
    original=json.loads((template/'FIXTURE.json').read_text())
    launch=json.loads((template/'launch.json').read_text())
    # A new recipe world avoids carrying prior players, changed blocks or elapsed world time.
    selected_scene=scene or original.get('scene','legacy')
    modded=original['modded'] if selected_scene=='legacy' else selected_scene=='factory'
    fixture.create(output,original['version'],modded,tier or original['tier'],selected_scene)
    (output/'mods').mkdir();(output/'diagnostics').mkdir()
    for p in sorted((template/'mods').glob('*.jar')):
        if p.name.startswith('RadianceAudit-'):continue
        if not modded and (p.name.startswith('create-') or p.name.startswith('sable-')):continue
        if renderer and p.name.startswith('Radiance-'):continue
        matches=[a for a in launch['artifacts'] if Path(a['path']).resolve()==p.resolve()]
        if len(matches)!=1 or hashlib.sha256(p.read_bytes()).hexdigest().upper()!=matches[0]['sha256']:
            raise ValueError('Template artifact identity missing/changed: '+str(p))
        shutil.copy2(p,output/'mods'/p.name)
    if renderer:
        renderer=Path(renderer).resolve()
        shutil.copy2(renderer,output/'mods'/renderer.name)
    libs=Path(__file__).resolve().parents[1]/'build/libs'
    suffix='fabric-1.21.4' if original['version']=='1.21.4' else 'neoforge-1.21.1'
    for pattern,folder in [('*-benchmark-'+suffix+'.jar','mods'),('*-benchmark-agent.jar','diagnostics')]:
        paths=list(libs.glob(pattern))
        if len(paths)!=1:raise ValueError('Build exactly one matching diagnostic artifact: '+pattern)
        shutil.copy2(paths[0],output/folder/paths[0].name)
    if (template/'programargs.txt').exists():shutil.copy2(template/'programargs.txt',output/'programargs.txt')
    # Renderer defaults are captured by preflight, not silently copied across incompatible versions.
    args=[]
    for a in launch['args']:
        a=a.replace(str(template),str(output))
        if a.startswith('-javaagent:'):a='-javaagent:'+str(next((output/'diagnostics').glob('*.jar')))
        elif a.startswith('-Dradiance.audit.benchmark.warmupSeconds='):a='-Dradiance.audit.benchmark.warmupSeconds='+str(warmup)
        elif a.startswith('-Dradiance.audit.benchmark.sampleSeconds='):a='-Dradiance.audit.benchmark.sampleSeconds='+str(sample)
        args.append(a)
    launch.update(case=str(output),args=args)
    launch['artifacts']=[{'path':str(p),'sha256':hashlib.sha256(p.read_bytes()).hexdigest().upper()}
                         for p in [*sorted((output/'mods').glob('*.jar')),*sorted((output/'diagnostics').glob('*.jar'))]]
    (output/'launch.json').write_text(json.dumps(launch,indent=2))
    return output/'launch.json'


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('template',type=Path);p.add_argument('output',type=Path)
    p.add_argument('--warmup',type=int,default=30);p.add_argument('--sample',type=int,default=30)
    a=p.parse_args();print(prepare(a.template,a.output,a.warmup,a.sample))
