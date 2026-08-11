"""Requested common settings. Live observation, not this file, decides comparability."""
import json
import re
import copy
import hashlib

TARGETS = {
    'upstream-neo': ('1.21.1', 'main_render', 'dlss_rr', 'rt_dlss_rr'),
    'upstream-fabric': ('1.21.4', 'ray_tracing', 'dlss', 'rt_dlss'),
    'fork': ('1.21.1', 'ray_tracing', 'dlss', 'rt_dlss'),
}

def apply_shader_controls(profile, target_config, reference_config):
    """Normalize exposed controls from the pinned packages, without editing either shader.

    Matching input numbers is not an equivalence proof for different integrators. Keep
    target-only controls explicit, with a reason, instead of silently ignoring them.
    """
    profile = copy.deepcopy(profile)
    ray = TARGETS[profile['target']][1]
    prefix = f'render_pipeline.module.{ray}.attribute.'
    reference = {a['name'].split('.attribute.',1)[1]:a for a in reference_config['attributes']}
    aliases = {'is_parallax_enabled':'enable_parallax',
               'should_capture_indirect_volumetric_clouds':'capture_volumetric_cloud_indirect',
               'should_volumetric_clouds_cast_shadows':'volumetric_cloud_cast_shadow'}
    overrides = {'quality_tier':prefix+'quality_tier.high',
                 'restir_resolution':prefix+'resolution_mode.quality',
                 'froxel_resolution':prefix+'resolution_mode.quality',
                 # Old integrator has no separate initial-disocclusion count: it uses 32.
                 'initial_disocclusion_boost_samples':reference['initial_samples']['default_value']}
    attributes=[]; mapping=[]
    for source in target_config['attributes']:
        name=source['name'];key=name.split('.attribute.',1)[1]
        if not name.startswith(prefix):raise ValueError('Shader module identity mismatch: '+name)
        reference_key=aliases.get(key,key)
        value=source['default_value'];reason='target-only default; no equal-work claim'
        if reference_key in reference:
            value=reference[reference_key]['default_value'].replace('render_pipeline.module.ray_tracing.attribute.',prefix)
            reason='reference control '+reference_key
        if key in overrides:
            value=overrides[key];reason='explicit RR/full-resolution or initial-sample override'
        kind=source['type']
        if kind.startswith('enum:') and value not in kind[5:].split('-'):
            raise ValueError('Unmappable enum value: '+name+'='+value)
        if kind.startswith(('int_range:','float_range:')):
            bounds=re.fullmatch(r'(-?\d+(?:\.\d+)?)-(-?\d+(?:\.\d+)?)',kind.split(':',1)[1])
            if bounds is None or not float(bounds[1])<=float(value)<=float(bounds[2]):
                raise ValueError('Out-of-range shared shader control: '+name+'='+value)
        attributes.append(dict(name=name,type=kind,value=value))
        mapping.append(dict(key=key,value=value,originalDefault=source['default_value'],basis=reason,define=source.get('define')))
    found={a['name'].split('.attribute.',1)[1] for a in attributes}
    for required in ('num_ray_bounces','initial_samples','spatial_reuse_samples','spatial_reuse_radius','temporal_confidence_cap'):
        if required not in found:raise ValueError('Missing workload control: '+required)
    module=next(m for m in profile['pipeline']['presetModules'] if m['entryName']==f'render_pipeline.module.{ray}.name')
    names={a['name'] for a in attributes}
    module['attributes']=[a for a in module['attributes'] if a['name'] not in names]+attributes
    profile['shaderControlMapping']=mapping
    profile['shaderWorkloadProfile']='explicit-module-controls-v2'
    profile['limits'] += ['All exposed ray-pack attributes are pinned/read back; unique controls retain documented target-specific behavior.',
                          'Equal sampling counts/light inputs do not equate integration, culling, froxel, tone-mapping or hidden RR-model behavior.']
    return profile


def settings(target, width=2560, height=1440):
    if target not in TARGETS or not 640 <= width <= 3840 or not 360 <= height <= 2160:
        raise ValueError('Unsupported target or bounded resolution')
    version, ray, rr, preset = TARGETS[target]
    options = dict(vsync='false', maxFps='260', inactivityFpsLimit='260', rayBounces='4',
                   chunkBuildingThreads='8', chunkBuildingBatchSize='8', chunkBuildingTotalBatches='8',
                   collectChunkEmission='true', dlssMode='1', denoiserMode='1',
                   upscalerType='0', upscalerQuality='1', frameGenerationMode='0',
                   frameGenerationMultiplier='2', dlssFrameGeneration='false', reflexMode='0',
                   dlssSrModel='0', dlssRrModel='5', dlssFgModel='0')
    # Unsupported optional properties are retained in requested settings, not treated as applied.
    vanilla = dict(fullscreen='false', pauseOnLostFocus='false', enableVsync='false', maxFps='260', soundCategory_master='0.0',
                   renderDistance='16', simulationDistance='5', entityDistanceScaling='4.0',
                   fov='0.0', guiScale='2', graphicsMode='1', particles='0', mipmapLevels='4',
                   biomeBlendRadius='2', ao='true', renderClouds='false', resourcePacks='[]',
                   autoJump='false', inactivityFpsLimit='minimized', overrideWidth=str(width), overrideHeight=str(height))
    def attribute(module, key, kind, value):
        return dict(name=f'render_pipeline.module.{module}.attribute.{key}', type=kind, value=value)
    rr_mode=f'render_pipeline.module.{rr}.attribute.mode'
    modules = [dict(entryName=f'render_pipeline.module.{rr}.name', attributes=[
        attribute(rr,'mode','enum:'+'-'.join(rr_mode+'.'+v for v in ('performance','balanced','quality','dlaa')),rr_mode+'.balanced')]),
        dict(entryName=f'render_pipeline.module.{ray}.name', attributes=[
            attribute(ray,'shader_pack_path','string','shaders/world/ray_tracing/advanced.zip'),
            attribute(ray,'use_jitter','bool','render_pipeline.true'),
            attribute(ray,'use_sharc','bool','render_pipeline.true')])]
    if ray=='main_render':
        modules[-1]['attributes'].append(attribute(ray,'sharc_resolve_frame_stride','int_range:1-16','1'))
    else:
        modules[-1]['attributes'].append(attribute(ray,'sharc_debug_mode','enum:off-hash_grid-occupancy-heatmap-query_state-query_cache-surface_cache','off'))
    pipeline=dict(mode='PRESET',presetName='render_pipeline.preset.'+preset,presetModules=modules)
    return dict(target=target, version=version, width=width, height=height, options=options,
                vanilla=vanilla,pipeline=pipeline,rrModule=f'render_pipeline.module.{rr}.name',
                expectedPreset=pipeline['presetName'],
                limits=['Upstream internal RR preset is not publicly selectable; historical header uses E (5), exact live model needs SDK evidence.',
                        'Native SDK/backend and renderer implementations differ by target; equal settings do not imply equal pixels.',
                        'No-focus, visible, non-minimized window required; foreground samples are invalid.',
                        'Fresh world setup/loading is excluded by warmup but must finish before timed samples.'])


def install(case, target, width=2560, height=1440, *, target_config=None, reference_config=None):
    profile=settings(target,width,height)
    if target_config is not None:
        profile=apply_shader_controls(profile,target_config,reference_config)
    (case/'radiance').mkdir(exist_ok=True)
    (case/'radiance/options.properties').write_text('\n'.join(k+'='+v for k,v in profile['options'].items())+'\n')
    (case/'options.txt').write_text('\n'.join(k+':'+v for k,v in profile['vanilla'].items())+'\n')
    (case/'radiance/pipeline.yaml').write_text('!!com.radiance.client.pipeline.Pipeline$PipelineConfigStorage\n'+json.dumps(profile['pipeline'],indent=2)+'\n')
    if target_config is not None:
        # Dynamic shader attributes are owned by the pack settings, not pipeline.yaml.
        # 0.1.6 hashes the absolute normalized pack path; older versions use a sibling file.
        pack=(case/'radiance/shaders/world/ray_tracing/advanced.zip').resolve()
        if target=='upstream-neo':
            storage=case/'radiance/shader-pack-settings'/('advanced.zip-'+hashlib.sha256(str(pack).encode('utf-8')).hexdigest()+'.properties')
        else:
            storage=pack.with_name(pack.name+'.txt')
        storage.parent.mkdir(parents=True,exist_ok=True)
        ray=TARGETS[target][1]
        attrs=next(m['attributes'] for m in profile['pipeline']['presetModules'] if m['entryName']==f'render_pipeline.module.{ray}.name')
        dynamic_names={a['name'] for a in target_config['attributes']}
        storage.write_text(''.join(a['name']+'='+a['value']+'\n' for a in attrs if a['name'] in dynamic_names),encoding='utf-8')
        profile['shaderAttributeStorage']=str(storage)
    (case/'REQUESTED_SETTINGS.json').write_text(json.dumps(profile,indent=2)+'\n')
    args_file=case/'programargs.txt'
    if args_file.exists():
        text=args_file.read_text()
        for name,value in [('width',width),('height',height)]:
            text,count=re.subn(r'(--'+name+r'\s+)\d+',lambda m:m[1]+str(value),text)
            if count!=1:raise ValueError('Expected one --'+name+' argument')
        args_file.write_text(text)
    return profile
