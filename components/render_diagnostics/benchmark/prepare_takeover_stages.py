"""Prepare the owned stage sample; never inspect a user world or launch a client."""
from pathlib import Path
import json
import hashlib
import fixture

ROOT = Path(__file__).resolve().parents[3]


def main():
    parent = ROOT / 'validations/conformance-takeover/instance/1.21.1-neoforge'
    instance = parent / 'client'
    marker = instance / '.radiance-takeover-stages'
    if instance.exists():
        if not marker.is_file():
            raise RuntimeError('Unowned stage instance')
        print('Keeping owned stage instance', instance)
        return
    fixture.REPOSITORY_RUN = parent
    fixture.create(instance, '1.21.1', modded=False, tier=1)
    result = ROOT / 'validations/conformance-takeover/result'
    result.mkdir(exist_ok=True)
    # Prior attempts are archived before their disposable client is removed. A new
    # process must never append to another candidate's completion evidence.
    (result / 'chunk-trace.txt').write_text('', encoding='utf8')
    marker.write_text('Stages 0-2 and terrain functional sample\n', encoding='utf8')
    (instance / '.radiance-acceptance').write_text('Owned stage sample\n', encoding='utf8')
    (instance / '.radiance-audit-test-instance').write_text('Owned stage sample\n', encoding='utf8')
    commands = ['gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
                'gamerule doMobSpawning false', 'gamerule randomTickSpeed 0', 'time set 6000',
                'weather clear', 'fill -24 64 -24 24 64 16 minecraft:light_gray_concrete',
                'fill -20 65 -10 20 79 -10 minecraft:black_concrete',
                'fill -20 65 -18 20 79 -18 minecraft:black_concrete',
                'fill 14 65 0 17 65 3 minecraft:stone', 'setblock 15 66 1 minecraft:water']
    items = ['diamond_sword', 'red_concrete', 'oak_leaves', 'glass', 'grass_block', 'apple']
    for y in range(10):
        for x in range(16):
            commands.append(f'summon minecraft:item_frame {x*2-15} {66+y} -9 '
                            + '{Tags:["takeover_frame"],'
                            + f'TileX:{x*2-15},TileY:{66+y},TileZ:-9,'
                            + 'Facing:3b,Fixed:1b,Invulnerable:1b,Item:{id:"minecraft:'
                            + items[(x+y) % len(items)] + '",count:1}}')
    for y in range(12):
        for x in range(2):
            commands.append(f'summon minecraft:painting {x*2-19} {66+y} -9 '
                            + '{Tags:["takeover_painting"],'
                            + f'TileX:{x*2-19},TileY:{66+y},TileZ:-9,'
                            + 'facing:0b,Invulnerable:1b,variant:"minecraft:kebab"}')
    commands += ['data modify storage audit:fixture built set value 1b', 'say TAKEOVER_STAGES_FIXTURE_READY']
    pack = instance / 'saves/Audit Benchmark/datapacks/audit-fixture/data/audit/function'
    (pack / 'build.mcfunction').write_text('\n'.join(commands)+'\n', encoding='utf8')
    (pack / 'load.mcfunction').write_text('forceload add -32 -32 31 31\n'
                                          'schedule function audit:wait 1t replace\n',encoding='utf8')
    loaded=' '.join(f'if loaded {x*16} 64 {z*16}' for x in range(-2,2) for z in range(-2,2))
    (pack / 'wait.mcfunction').write_text('execute unless data storage audit:fixture {built:1b} '
        + loaded+' run function audit:build\n'
        +'execute unless data storage audit:fixture {built:1b} run schedule function audit:wait 1t replace\n',encoding='utf8')
    (pack / 'enter.mcfunction').write_text('gamemode spectator @s\ntp @s 0.5 73 14.5 180 0\n'
                                         'tag @s add audit_ready\n', encoding='utf8')
    pipeline = instance / 'radiance'
    pipeline.mkdir(exist_ok=True)
    (pipeline / 'pipeline.yaml').write_text('!!com.radiance.client.pipeline.Pipeline$PipelineConfigStorage\n'
        '{"mode":"PRESET","presetName":"render_pipeline.preset.rt_nrd_fsr","presetModules":[]}\n', encoding='utf8')
    (instance / 'FIXTURE.json').write_text(json.dumps({'version':1, 'frames':160, 'paintings':24,
        'commands': commands, 'files':{str(p.relative_to(instance)):hashlib.sha256(p.read_bytes()).hexdigest()
                                     for p in instance.rglob('*') if p.is_file() and p.name != 'FIXTURE.json'}},
        indent=2)+'\n', encoding='utf8')
    print(instance)


if __name__ == '__main__':
    main()
