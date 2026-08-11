"""Prepare one owned, silent functional fixture; no profiling or performance comparison."""
from pathlib import Path
import hashlib
import json
import os


def main():
    import fixture
    repository = Path(__file__).resolve().parents[3]
    parent = repository / 'validations/regression-voxy_integration/instance/1.21.1-neoforge'
    instance = parent / 'client'
    if instance.exists():
        if not (instance / '.radiance-voxy-validation').is_file():
            raise RuntimeError('Existing Voxy fixture has no ownership marker')
        print('Keeping owned Voxy world; resetting explicit functional configuration')
    else:
        fixture.REPOSITORY_RUN = parent
        fixture.create(instance, '1.21.1', modded=False, tier=1)
        (instance / '.radiance-voxy-validation').write_text('Functional Voxy integration only\n', encoding='utf8')
    pack = instance / 'saves/Audit Benchmark/datapacks/audit-fixture/data/audit/function'
    commands = ['gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
                'gamerule doMobSpawning false', 'gamerule randomTickSpeed 0',
                'time set 6000', 'weather clear',
                'forceload add -32 -32 32 32', 'forceload add 304 -16 336 16',
                'fill -24 64 -24 24 64 24 minecraft:stone',
                'fill 8 64 -8 20 64 8 minecraft:water',
                'fill -16 65 -8 -12 70 8 minecraft:oak_leaves[persistent=true]',
                'fill -8 65 -8 -4 70 8 minecraft:glass',
                'fill 312 64 -8 328 80 8 minecraft:stone',
                'fill 313 65 -7 327 79 7 minecraft:air',
                'fill 312 67 -4 312 73 4 minecraft:glowstone',
                'forceload remove all',
                'data modify storage audit:fixture built set value 1b',
                'say VOXY_FIXTURE_READY']
    (pack / 'build.mcfunction').write_text('\n'.join(commands) + '\n', encoding='utf8')
    options = instance / 'options.txt'
    rows = [line for line in options.read_text().splitlines()
            if not line.startswith(('renderDistance:', 'fov:', 'soundCategory_master:'))]
    options.write_text('\n'.join(rows + ['renderDistance:4', 'fov:0.0',
                                        'soundCategory_master:0.0', 'onboardAccessibility:false']) + '\n', encoding='utf8')
    config = instance / 'config'
    config.mkdir(exist_ok=True)
    # Explicitly bounded functional distance. Product/source defaults are untouched.
    (config / 'voxy-config.json').write_text(json.dumps(dict(enabled=True, enable_rendering=True,
        ingest_enabled=True, section_render_distance=1, sub_division_size=64,
        service_threads=2, gpu_debug=False), indent=2) + '\n', encoding='utf8')
    pipeline = instance / 'radiance'
    pipeline.mkdir(exist_ok=True)
    properties = pipeline / 'options.properties'
    rows = properties.read_text(encoding='utf8').splitlines() if properties.exists() else []
    rows = [row for row in rows if not row.startswith('collectChunkEmission=')]
    properties.write_text('\n'.join(rows + ['collectChunkEmission=true']) + '\n', encoding='utf8')
    pipeline_only = os.environ.get('RADIANCE_VOXY_PIPELINE_ONLY') == '1'
    modules = [dict(entryName='render_pipeline.module.ray_tracing.name', attributes=[dict(
        name='render_pipeline.module.ray_tracing.attribute.shader_pack_path', type='string',
        value='shaders/world/ray_tracing/advanced.zip')])] if pipeline_only else []
    (pipeline / 'pipeline.yaml').write_text('!!com.radiance.client.pipeline.Pipeline$PipelineConfigStorage\n'
        + json.dumps(dict(mode='PRESET', presetName='render_pipeline.preset.rt_nrd_fsr', presetModules=modules), indent=2)
        + '\n', encoding='utf8')
    controls = [instance / '.radiance-voxy-validation', options, config / 'voxy-config.json',
                pipeline / 'options.properties', pipeline / 'pipeline.yaml', pack / 'build.mcfunction',
                instance / 'saves/Audit Benchmark/level.dat']
    manifest = dict(purpose='Voxy functional integration, not a performance fixture',
                    render_distance=4, voxy_distance_blocks=512, width=1920, height=1080,
                    master_volume=0, files={str(p.relative_to(instance)): hashlib.sha256(p.read_bytes()).hexdigest()
                    for p in controls if p.is_file()})
    (instance / 'FIXTURE.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf8')
    print(instance)


if __name__ == '__main__':
    main()
