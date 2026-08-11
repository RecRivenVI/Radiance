"""Create a NEW disposable flat benchmark world, never a production-world editor.

Python 3 + nbtlib 2.0.4. Game commands build the fixture on first load. Generation is
not a proof those commands executed: retain the game log and validate expected counts.
"""
from pathlib import Path
import argparse
import hashlib
import json
import uuid
import nbtlib as n
import stress_scene

VERSIONS = {"1.21.1": (3955, 48), "1.21.4": (4189, 61)}
REPOSITORY_RUN = Path(__file__).resolve().parents[3] / 'run'


def commands(modded=False, tier=1):
    if tier not in (1, 2, 4):
        raise ValueError("tiers are explicitly bounded: 1, 2, 4")
    build = ["gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
             "gamerule doMobSpawning false", "gamerule randomTickSpeed 0",
             "gamerule commandBlockOutput false", "time set 6000", "weather clear",
             "forceload add -48 -112 48 48", "fill -48 63 -112 48 63 48 minecraft:stone",
             "fill -48 64 -112 48 64 48 minecraft:light_gray_concrete"]
    expected = {"chests": 0, "banners": 0, "armor_stands": 0, "item_frames": 0,
                "create_motors": 0, "create_shafts": 0, "sable_assemblies": 0}
    # Separate chests avoid accidental double-chest merging. Identical recipe across MC versions.
    for z in range(8 * tier):
        for x in range(8):
            px, pz = x * 3 - 12, -z * 3
            build += [f"setblock {px} 65 {pz} minecraft:chest[facing=south,type=single]",
                      f"setblock {px+1} 65 {pz} minecraft:white_banner[rotation=0]"]
            expected["chests"] += 1
            expected["banners"] += 1
    for i in range(16 * tier):
        px, pz = (i % 8) * 3 - 11, 4 + (i // 8) * 3
        uid = uuid.UUID(int=0xA0D17000000000000000000000000000 + i)
        ints = [int.from_bytes(uid.bytes[j:j+4], 'big', signed=True) for j in range(0, 16, 4)]
        build.append(f'summon minecraft:armor_stand {px} 65 {pz} '
                     + '{Tags:["audit_fixture"],NoGravity:1b,Invulnerable:1b,UUID:[I;'
                     + ','.join(map(str, ints))
                     + '],ShowArms:1b,ArmorItems:[{},{},{},{id:"minecraft:diamond_helmet",count:1}]}')
        expected["armor_stands"] += 1
        build += [f"setblock {px} 66 {pz+1} minecraft:black_concrete",
                  f'summon minecraft:item_frame {px} 66 {pz} '
                  + '{Tags:["audit_fixture"],Facing:2b,Fixed:1b,Invulnerable:1b,Item:{id:"minecraft:diamond_sword",count:1}}']
        expected["item_frames"] += 1
    if modded:
        for i in range(8 * tier):
            x, z = 22 + (i % 4) * 4, -(i // 4) * 6
            # Create 6.0.10 motor's real default is 16 RPM; no guessed NBT field is injected.
            build.append(f"setblock {x} 66 {z} create:creative_motor[facing=south]")
            expected["create_motors"] += 1
            for dz in range(1, 5):
                build.append(f"setblock {x} 66 {z+dz} create:shaft[axis=z]")
                expected["create_shafts"] += 1
        for i in range(4 * tier):
            x, z = -38 + (i % 2) * 9, -(i // 2) * 9
            build += [f"fill {x} 68 {z} {x+5} 68 {z+5} minecraft:oak_planks",
                      f"setblock {x+2} 69 {z+2} minecraft:chest[facing=south,type=single]",
                      f"sable assemble connected {x} 68 {z} 128"]
            expected["sable_assemblies"] += 1
    build += ["data modify storage audit:fixture built set value 1b",
              'say AUDIT_FIXTURE_BUILD_DISPATCHED (verify counts and command errors before measurement)']
    return build, expected


def create(output, version, modded=False, tier=1, scene='legacy'):
    output = Path(output).resolve()
    if version not in VERSIONS or (modded and version != "1.21.1"):
        raise ValueError("modded fixtures are only defined for 1.21.1")
    if scene != 'legacy' and (scene not in stress_scene.SCENES or tier not in (1, 2) or modded != (scene == 'factory')):
        raise ValueError('stress recipe/version/mod selection mismatch')
    # Output must be new, under a repository run directory, with explicit disposable marker.
    if not output.is_relative_to(REPOSITORY_RUN.resolve()):
        raise ValueError("output must be inside an isolated repository run directory")
    output.mkdir(parents=True, exist_ok=False)
    (output / '.radiance-audit-test-instance').write_text('Generated disposable benchmark instance\n')
    world = output / 'saves/Audit Benchmark'
    pack = world / 'datapacks/audit-fixture'
    function = pack / 'data/audit/function'
    tags = pack / 'data/minecraft/tags/function'
    function.mkdir(parents=True)
    tags.mkdir(parents=True)
    camera = [0.5, 75.0, 34.5, 180.0, 17.0]
    if scene == 'legacy':
        build, expected = commands(modded, tier)
    else:
        build, expected, camera = stress_scene.recipe(scene, tier)
    files = {'build.mcfunction': '\n'.join(build)+'\n',
             'load.mcfunction': 'execute unless data storage audit:fixture built run function audit:build\n',
             'tick.mcfunction': 'execute as @a[tag=!audit_ready] run function audit:enter\n',
             'enter.mcfunction': 'gamemode spectator @s\ntp @s '+' '.join(map(str,camera))+'\ntag @s add audit_ready\n'}
    for name, content in files.items():
        (function / name).write_text(content, encoding='utf8')
    for tag in ('load', 'tick'):
        (tags / (tag+'.json')).write_text(json.dumps({'values': ['audit:'+tag]}))
    data_version, pack_version = VERSIONS[version]
    (pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': pack_version, 'description': 'Disposable Audit benchmark recipe v1'}}))
    flat = n.Compound({'type': n.String('minecraft:flat'), 'settings': n.Compound({
        'biome': n.String('minecraft:plains'), 'features': n.Byte(0), 'lakes': n.Byte(0),
        'layers': n.List[n.Compound]([n.Compound({'block': n.String('minecraft:bedrock'), 'height': n.Int(1)}),
                                    n.Compound({'block': n.String('minecraft:stone'), 'height': n.Int(127)})]),
        'structure_overrides': n.List[n.String]([])})})
    dimensions = n.Compound({'minecraft:overworld': n.Compound({'type': n.String('minecraft:overworld'), 'generator': flat})})
    for dim, preset, biome in [('the_nether', 'nether', 'minecraft:nether'), ('the_end', 'end', 'minecraft:the_end')]:
        source = n.Compound({'type': n.String('minecraft:multi_noise'), 'preset': n.String(biome)}) if dim == 'the_nether' else n.Compound({'type': n.String('minecraft:the_end')})
        dimensions['minecraft:'+dim] = n.Compound({'type': n.String('minecraft:'+dim), 'generator': n.Compound({
            'type': n.String('minecraft:noise'), 'settings': n.String('minecraft:'+preset), 'biome_source': source})})
    data = n.Compound({'DataVersion': n.Int(data_version), 'Version': n.Compound({'Id': n.Int(data_version), 'Name': n.String(version), 'Series': n.String('main'), 'Snapshot': n.Byte(0)}),
        'version': n.Int(19133), 'LevelName': n.String('Audit Benchmark'), 'initialized': n.Byte(1), 'allowCommands': n.Byte(1),
        'GameType': n.Int(3), 'Difficulty': n.Byte(0), 'hardcore': n.Byte(0), 'SpawnX': n.Int(0), 'SpawnY': n.Int(75), 'SpawnZ': n.Int(34),
        'Time': n.Long(6000), 'DayTime': n.Long(6000), 'LastPlayed': n.Long(0), 'DragonFight': n.Compound({}),
        'DataPacks': n.Compound({'Enabled': n.List[n.String](['vanilla', 'file/audit-fixture']), 'Disabled': n.List[n.String]([])}),
        'GameRules': n.Compound({k: n.String(v) for k, v in {'doDaylightCycle': 'false', 'doWeatherCycle': 'false', 'doMobSpawning': 'false', 'randomTickSpeed': '0', 'spawnChunkRadius': '0'}.items()}),
        'WorldGenSettings': n.Compound({'seed': n.Long(250925), 'generate_features': n.Byte(0), 'bonus_chest': n.Byte(0), 'dimensions': dimensions})})
    n.File({'Data': data}, gzipped=True).save(world / 'level.dat')
    (output / 'options.txt').write_text('fullscreen:false\npauseOnLostFocus:false\nenableVsync:false\nmaxFps:260\nrenderDistance:8\nsimulationDistance:5\nentityDistanceScaling:1.0\nfov:0.0\nguiScale:2\nshowSubtitles:false\nautoJump:false\ninactivityFpsLimit:minimized\nsoundCategory_master:0.0\n')
    manifest = {'recipe': 1 if scene == 'legacy' else 2, 'scene': scene, 'camera': camera,
                'version': version, 'modded': modded, 'tier': tier, 'seed': 250925,
                'expected': expected, 'world': str(world), 'runtime_verified': False,
                'files': {str(p.relative_to(output)): hashlib.sha256(p.read_bytes()).hexdigest() for p in output.rglob('*') if p.is_file()}}
    (output / 'FIXTURE.json').write_text(json.dumps(manifest, indent=2))
    return manifest


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('output', type=Path)
    parser.add_argument('--version', choices=VERSIONS, required=True)
    parser.add_argument('--modded', action='store_true')
    parser.add_argument('--tier', type=int, choices=(1, 2, 4), default=1)
    parser.add_argument('--scene', choices=('legacy', *stress_scene.SCENES), default='legacy')
    args = parser.parse_args()
    print(json.dumps(create(args.output, args.version, args.modded, args.tier, args.scene), indent=2))
