"""A small, owned functional fixture. No performance window and no external instances."""
from pathlib import Path
import hashlib
import json
import tomllib
import fixture


def main(name='conformance-render_takeover'):
    repository = Path(__file__).resolve().parents[3]
    if name not in ('conformance-render_takeover', 'conformance-boundaries'):
        raise ValueError('Unknown owned functional validation')
    validation = repository / 'validations' / name
    config = tomllib.loads((validation / 'validation.toml').read_text(encoding='utf8'))
    target = config['targets'][0]
    parent = validation / 'instance' / target
    instance = parent / 'client'
    marker = instance / '.radiance-takeover-validation'
    if instance.exists():
        if not marker.is_file():
            raise RuntimeError('Existing instance is not an owned takeover fixture')
        print('Keeping owned functional fixture', instance)
        return
    fixture.REPOSITORY_RUN = parent
    fixture.create(instance, target.rsplit('-', 1)[0], modded=False, tier=1)
    marker.write_text('Functional takeover feasibility only\n', encoding='utf8')
    (instance / '.radiance-acceptance').write_text('Isolated functional fixture\n', encoding='utf8')
    commands = [
        'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
        'gamerule doMobSpawning false', 'gamerule randomTickSpeed 0',
        'time set 6000', 'weather clear',
        'fill -8 64 -8 8 64 8 minecraft:light_gray_concrete',
        'setblock 0 65 0 minecraft:chest', 'setblock 2 65 0 minecraft:white_banner',
        'summon minecraft:armor_stand -2 65 0 {NoGravity:1b,Invulnerable:1b,ShowArms:1b}',
        'summon minecraft:armor_stand -4 65 0 {NoGravity:1b,Invulnerable:1b,ShowArms:1b}',
        'setblock 0 66 -3 minecraft:black_concrete',
        'summon minecraft:item_frame 0 66 -2 {Facing:3b,Fixed:1b,Invulnerable:1b,Item:{id:"minecraft:diamond_sword",count:1}}',
        'summon minecraft:spider 4 65 0 {NoAI:1b,Silent:1b,PersistenceRequired:1b}',
        'summon minecraft:enderman 6 65 0 {NoAI:1b,Silent:1b,PersistenceRequired:1b}',
        'summon minecraft:creeper 4 65 3 {NoAI:1b,Silent:1b,powered:1b,PersistenceRequired:1b}',
        'data modify storage audit:fixture built set value 1b',
        'say TAKEOVER_FIXTURE_READY',
    ]
    folder = instance / 'saves/Audit Benchmark/datapacks/audit-fixture/data/audit/function'
    (folder / 'build.mcfunction').write_text('\n'.join(commands)+'\n', encoding='utf8')
    (folder / 'enter.mcfunction').write_text(
        'gamemode spectator @s\ntp @s 0.5 68 10.5 180 12\ntag @s add audit_ready\n', encoding='utf8')
    options = instance / 'options.txt'
    values = {'soundCategory_master':'0.0', 'soundCategory_music':'0.0',
              'renderDistance':'8', 'simulationDistance':'5', 'onboardAccessibility':'false'}
    rows = [x for x in options.read_text(encoding='utf8').splitlines() if x.split(':',1)[0] not in values]
    options.write_text('\n'.join(rows+[f'{k}:{v}' for k,v in values.items()])+'\n', encoding='utf8')
    pipeline = instance / 'radiance/pipeline.yaml'
    pipeline.parent.mkdir(exist_ok=True)
    pipeline.write_text('!!com.radiance.client.pipeline.Pipeline$PipelineConfigStorage\n'
                        '{"mode":"PRESET","presetName":"render_pipeline.preset.rt_nrd_fsr","presetModules":[]}\n', encoding='utf8')
    manifest = {'purpose':'functional only; no performance result', 'target':target,
                'commands':commands, 'files':{str(p.relative_to(instance)):hashlib.sha256(p.read_bytes()).hexdigest()
                                             for p in instance.rglob('*') if p.is_file() and p.name!='FIXTURE.json'}}
    (instance / 'FIXTURE.json').write_text(json.dumps(manifest,indent=2)+'\n', encoding='utf8')
    print(instance)


if __name__ == '__main__':
    main()
