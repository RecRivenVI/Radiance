"""Compile configured built-in shader requests with their default attribute contracts."""
from pathlib import Path
import json
import re
import subprocess
import hashlib
import sys
import yaml
import zipfile
import io
import shutil

ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / 'components/native_renderer/src'
OUTPUT = ROOT / 'components/render_diagnostics/build/world-materials-shaders'


def requests(pack, values=None):
    configuration = json.loads((pack / 'configs.json').read_text(encoding='utf8'))
    attributes = {}
    for item in configuration['attributes']:
        value = str((values or {}).get(item['name'],item['default_value']))
        kind = item['type']
        if kind == 'bool': value = '1' if value in ('true', '1', 'True', 'render_pipeline.true') else '0'
        if kind.startswith('enum:'): value = str(kind[5:].split('-').index(value))
        define = item.get('define', {})
        if isinstance(define, str): attributes[define] = value
        else:
            for key, expression in define.items():
                attributes[key] = re.sub(r'\bX\b', lambda _: value, expression)
    for phase in ('execution', 'execution_post'):
        for variable in configuration.get(phase, {}).get('global_variables', []):
            for key, value in variable.items():
                attributes[key] = str(value).lower()
    def walk(item, inherited):
        if isinstance(item, dict):
            definitions = inherited | item.get('definitions', {})
            for key, value in item.items():
                if key == 'definitions': continue
                if isinstance(value, str) and Path(value).suffix in ('.comp', '.rgen', '.rmiss', '.rchit', '.rahit', '.vert', '.frag'):
                    yield value, definitions
                elif isinstance(value, (dict, list)): yield from walk(value, definitions)
        elif isinstance(item, list):
            for child in item: yield from walk(child, inherited)
    yield from walk(configuration['passes'], attributes)


def shader_attribute_values(path):
    return dict(line.split('=',1) for line in path.read_text(encoding='utf8').splitlines()
                if line and not line.startswith('#') and '=' in line)


def main(pipeline=None,candidate=None,use_source=False,attributes=None):
    OUTPUT.mkdir(parents=True, exist_ok=True)
    report = []
    values={}
    packaged={}
    if candidate is not None:
        with zipfile.ZipFile(candidate) as product:
            for name in ('vanilla-pt','advanced'):
                content=product.read('shaders/world/ray_tracing/'+name+'.zip')
                destination=OUTPUT/'inputs'/name
                with zipfile.ZipFile(io.BytesIO(content)) as archive:
                    for entry in archive.infolist():
                        target=destination/entry.filename
                        if not target.resolve().is_relative_to(destination.resolve()):raise RuntimeError('Unsafe shader archive path')
                        if not entry.is_dir():
                            target.parent.mkdir(parents=True,exist_ok=True)
                            target.write_bytes(archive.read(entry))
                packaged[name]=destination
                if use_source:
                    shutil.copytree(SOURCE/'shader/world/ray_tracing/internal'/name,destination,dirs_exist_ok=True)
                    shutil.copytree(SOURCE/'shader/util',destination/'util',dirs_exist_ok=True)
                    shutil.copytree(SOURCE/'common',destination/'common',dirs_exist_ok=True)
                    if name=='advanced':
                        shutil.copytree(SOURCE/'shader/world/ray_tracing/internal/vanilla-pt/priority',destination/'priority',dirs_exist_ok=True)
        identity=dict(path=str(candidate),sha256=hashlib.sha256(candidate.read_bytes()).hexdigest(),sourceOverrides=use_source)
        if use_source:
            identity['sourceFiles']={str(path.relative_to(SOURCE)):hashlib.sha256(path.read_bytes()).hexdigest()
                                     for directory in (SOURCE/'shader/util',SOURCE/'shader/world/ray_tracing/internal',SOURCE/'common')
                                     for path in directory.rglob('*') if path.is_file()}
        (OUTPUT/'candidate.json').write_text(json.dumps(identity,indent=2)+'\n',encoding='utf8')
    if pipeline is not None:
        text=pipeline.read_text(encoding='utf8')
        document=yaml.safe_load(re.sub(r'!![\w.$]+\s*','',text))
        def collect(item):
            if isinstance(item,dict):
                if all(key in item for key in ('name','type','value')):values[item['name']]=item['value']
                for child in item.values():collect(child)
            elif isinstance(item,list):
                for child in item:collect(child)
        collect(document)
        if not any('ray_tracing.attribute' in key for key in values):raise RuntimeError('No actual ray-tracing attributes')
        (OUTPUT/'configuration.json').write_text(json.dumps(dict(path=str(pipeline),sha256=hashlib.sha256(pipeline.read_bytes()).hexdigest(),attributes=values),indent=2)+'\n',encoding='utf8')
    if attributes is not None:
        values.update(shader_attribute_values(attributes))
        (OUTPUT/'shader-attributes.json').write_text(json.dumps(dict(path=str(attributes),sha256=hashlib.sha256(attributes.read_bytes()).hexdigest(),values=shader_attribute_values(attributes)),indent=2)+'\n',encoding='utf8')
    for name in ('vanilla-pt', 'advanced'):
        pack = packaged.get(name,SOURCE / 'shader/world/ray_tracing/internal' / name)
        unique = {}
        for path, definitions in requests(pack,values):
            if path.startswith('sky_cube/'):
                definitions = definitions | {'FACE': '0'}
            unique[(path, tuple(sorted(definitions.items())))] = definitions
            if Path(path).suffix == '.rchit' and (path == 'world/default.rchit' or
                    'default.rchit' in (pack/path).read_text(encoding='utf8')):
                selected=definitions|{'RADIANCE_SHARC_HIT_QUERY':'1'}
                unique[(path,tuple(sorted(selected.items())))]=selected
            if path=='world/world.rgen':
                for variant in ('SHARC_QUERY','SHARC_UPDATE'):
                    selected=definitions|{'USE_SHARC':'1',variant:'1','MCVR_SHARC_AUDIT':'0'}
                    unique[(path,tuple(sorted(selected.items())))]=selected
        for (path, _), definitions in unique.items():
            index = len(report)
            command = ['C:/VulkanSDK/1.4.341.1/Bin/glslangValidator.exe', '-V', '--target-env', 'vulkan1.2',
                       '-I' + str(pack), '-I' + str(SOURCE / 'shader'), '-I' + str(SOURCE)]
            command += ['-D' + key + '=' + str(value) for key, value in definitions.items()]
            source = pack / path
            if path.startswith('priority/') and candidate is None:
                source = SOURCE / 'shader/world/ray_tracing/internal/vanilla-pt' / path
            command += [str(source), '-o', str(OUTPUT / f'{index}.spv')]
            result = subprocess.run(command, capture_output=True, text=True, encoding='utf8', errors='replace')
            report.append(dict(pack=name, path=path, definitions=definitions, passed=result.returncode == 0,
                               output=result.stdout + result.stderr))
            if result.returncode:
                print(name, path, result.stdout + result.stderr, flush=True)
                break
        if not report[-1]['passed']: break
    (OUTPUT / 'compile.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print('Configured shader requests:', len(report), 'pass:', all(item['passed'] for item in report))
    if not all(item['passed'] for item in report): raise SystemExit(1)


if __name__ == '__main__': main(Path(sys.argv[1]) if len(sys.argv)>1 else None,Path(sys.argv[2]) if len(sys.argv)>2 else None,'--sources' in sys.argv)
