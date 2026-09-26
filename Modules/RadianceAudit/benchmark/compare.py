"""Reject mismatched conditions before producing a bounded performance table."""
from pathlib import Path
import argparse
import csv
import json
import statistics
import hashlib
import analyze


def properties(path):
    result={}
    for line in path.read_text(encoding='utf-8').splitlines():
        if line.startswith('#') or '=' not in line:continue
        k,v=line.split('=',1)
        result[k.replace('\\:',':')]=v.replace('\\:',':').replace('\\=', '=')
    return result


def gate(case, analysis):
    case=Path(case)
    wanted=json.loads((case/'REQUESTED_SETTINGS.json').read_text())
    errors=[]
    shader_identity=wanted.get('shaderConfigIdentity',{}).get('target')
    if shader_identity:
        extracted=case/'radiance'/shader_identity['entry']
        if not extracted.is_file() or hashlib.sha256(extracted.read_bytes()).hexdigest().upper()!=shader_identity['packSha256']:
            errors.append('Extracted Advanced shader identity mismatch')
    fixture=json.loads((case/'FIXTURE.json').read_text())
    expected_camera=fixture.get('camera')
    if expected_camera:
        position=analysis.get('camera',{}).get('position',[])
        rotation=analysis.get('camera',{}).get('rotation',[])
        if len(position)!=3 or any(abs(a-b)>1e-4 for a,b in zip(position,expected_camera[:3])):
            errors.append('Saved camera position mismatch')
        if len(rotation)!=2 or any(abs((a-b+180)%360-180)>1e-4 for a,b in zip(rotation,expected_camera[3:])):
            errors.append('Saved camera rotation mismatch')
    folders=list((case/'radiance-audit/benchmark').glob('*'))
    if len(folders)!=1:return ['Expected exactly one observer record']
    folder=folders[0]
    with (folder/'frames.csv').open() as stream:rows=list(csv.DictReader(stream))
    if not rows:errors.append('No timed frames')
    for name,value in [('framebuffer_width',wanted['width']),('framebuffer_height',wanted['height']),('glfw_actual_focused',0),('iconified',0)]:
        if {row.get(name) for row in rows}!={str(value)}:errors.append(f'Actual {name} differs: {sorted({row.get(name) for row in rows},key=str)}')
    status=properties(folder/'STATUS.txt')
    for name,value in [('error',''),('worldEntered','true'),('incompleteFrame','false'),('normalStopRequested','true')]:
        if status.get(name)!=value:errors.append('Observer '+name+': '+str(status.get(name)))
    common=('vsync','maxFps','inactivityFpsLimit','rayBounces','chunkBuildingThreads',
            'chunkBuildingBatchSize','chunkBuildingTotalBatches','collectChunkEmission')
    actual=[]
    for phase in ('world-start','sample-end'):
        p=folder/(phase+'-settings.properties')
        if not p.exists():errors.append('Missing live configuration: '+phase);continue
        observed=properties(p);actual.append(observed)
        if observed.get('activePreset')!=wanted['expectedPreset']:errors.append(phase+': active preset mismatch (fallback?)')
        if wanted['rrModule'] not in observed.get('activeModules','').split(','):errors.append(phase+': missing active RR module')
        for key in common:
            if observed.get('option.'+key)!=wanted['options'][key]:errors.append(phase+': '+key+'='+str(observed.get('option.'+key)))
        for module in wanted['pipeline']['presetModules']:
            for a in module['attributes']:
                value=observed.get(a['name'])
                if a['name'].endswith('shader_pack_path'):
                    ok=value is not None and value.replace('\\','/').endswith('/advanced.zip')
                else:ok=value==a['value']
                if not ok:errors.append(phase+': '+a['name']+'='+str(value))
        for key,expected in [('frameGenerationMode','0'),('dlssFrameGeneration','false'),('reflexMode','0')]:
            if 'option.'+key in observed and observed['option.'+key]!=expected:errors.append(phase+': '+key+' active')
    if len(actual)==2 and actual[0]!=actual[1]:errors.append('Live settings changed during sample')
    for key,passed in analysis['minimumCountChecks'].items():
        if not passed:errors.append('Fixture check failed: '+key)
    process=analysis['process']
    if process['exitCode']!=0 or process['timeoutClose'] or not process['loadedCore']:errors.append('Process/close/core precheck failed')
    if process['observedForegroundSamples']:errors.append('Game became OS foreground')
    runtime=case/'loaded-runtimes.json'
    observed=json.loads(runtime.read_text(encoding='utf-8-sig')) if runtime.exists() else []
    if isinstance(observed,dict):observed=[observed]
    if not any(Path(p['path']).name.lower()=='nvngx_dlssd.dll' for p in observed):errors.append('RR runtime not observed loaded')
    for item in observed:
        name=Path(item['path']).name.lower()
        expected=wanted.get('nvidiaRuntimeHashes',{}).get(name)
        if expected and expected!=item['sha256']:errors.append('Loaded NVIDIA runtime identity mismatch: '+name)
    logs='\n'.join(p.read_text(encoding='utf-8',errors='replace') for p in [case/'stdout.log',case/'stderr.log',case/'logs/latest.log'] if p.exists())
    if 'All dimensions are saved' not in logs:errors.append('Missing all-dimension save evidence')
    for marker in ['VK_ERROR_DEVICE_LOST','Failed to load function audit:','observer invalid','NGX initialization/query failed']:
        if marker in logs:errors.append('Log marker: '+marker)
    return errors


def collect(case):
    result=analyze.analyze(case)
    errors=gate(case,result)
    result['configurationGate']={'passed':not errors,'errors':errors}
    wanted=json.loads((Path(case)/'REQUESTED_SETTINGS.json').read_text())
    result['performanceComparability']=(
        'MODULE_CONTROLS_MATCHED_NOT_WORK_OR_VISUAL_EQUIVALENCE'
        if wanted.get('shaderWorkloadProfile')=='explicit-module-controls-v2'
        else 'LEGACY_OUTER_SETTINGS_ONLY_NOT_SHADER_PARITY') if not errors else 'REJECTED'
    (Path(case)/'ANALYSIS.json').write_text(json.dumps(result,indent=2)+'\n')
    return result


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('cases',nargs='+',type=Path);a=p.parse_args()
    failed=False
    for case in a.cases:
        result=collect(case)
        failed |= not result['configurationGate']['passed']
        print(json.dumps({'case':str(case),'gate':result['configurationGate'],
                          'frameIntervals':[p['real_start_interval_ms'] for p in result['profiles']]},indent=2))
    raise SystemExit(1 if failed else 0)
