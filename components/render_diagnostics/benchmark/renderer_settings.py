"""Expected effective settings for the owned, built-in Vanilla PT city baseline."""
import io
import json
from pathlib import Path
import zipfile

import yaml


def properties(path):
    return dict(line.strip().split('=',1) for line in Path(path).read_text(encoding='utf8').splitlines()
                if line.strip() and not line.lstrip().startswith('#') and '=' in line)


def runtime_options(values):
    # A file migration marker has no matching runtime Options field. Keep all other
    # names strict so unknown or removed rendering settings still fail verification.
    return {key:value for key,value in values.items() if key != 'displayDefaultsRevision'}


def expectations(core, client):
    client = Path(client)
    text = (client/'radiance/pipeline.yaml').read_text(encoding='utf8')
    if text.startswith('!!com.radiance.client.pipeline.Pipeline$PipelineConfigStorage'):
        text = text.split('\n',1)[1]
    pipeline = yaml.safe_load(text)
    if pipeline['mode'] != 'PRESET' or pipeline['presetName'] != 'render_pipeline.preset.rt_dlss':
        raise ValueError('This city baseline requires the reviewed built-in RT+DLSS preset')
    modules = {m['entryName']:{a['name']:str(a['value']) for a in m.get('attributes',[])}
               for m in pipeline['presetModules']}
    ray = modules['render_pipeline.module.ray_tracing.name']
    if ray['render_pipeline.module.ray_tracing.attribute.shader_pack_path'] not in ('','shaders/world/ray_tracing/vanilla-pt.zip'):
        raise ValueError('This baseline does not interpret custom shader packs')
    with zipfile.ZipFile(core) as outer:
        with zipfile.ZipFile(io.BytesIO(outer.read('shaders/world/ray_tracing/vanilla-pt.zip'))) as inner:
            names={a['name'] for a in json.loads(inner.read('configs.json'))['attributes']}
    stored=properties(client/'radiance/shaders/world/ray_tracing/vanilla-pt.zip.txt')
    # Pipeline's stored module attributes apply after its per-pack file.
    modules['render_pipeline.module.ray_tracing.name']={**{k:v for k,v in stored.items() if k in names},**ray}
    options=properties(client/'radiance/options.properties')
    rendered_options=runtime_options(options)
    packs=next(line.split(':',1)[1] for line in (client/'options.txt').read_text(encoding='utf8').splitlines()
               if line.startswith('resourcePacks:'))
    return dict(modules=modules,options=rendered_options,resourcePacks=json.loads(packs),
                storageMetadata={key:value for key,value in options.items() if key not in rendered_options},
                note='Shader-pack attributes, not legacy Options.rayBounces, govern the actual PT configuration.')
