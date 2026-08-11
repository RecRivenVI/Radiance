"""Bounded deterministic workloads; rendering coverage must still be checked in-game.

Common city intentionally combines the previously expensive item-frame baked models,
armored ModelParts and animated block entities. Modded factory is a separate comparison.
"""
import uuid

SCENES = ('model-city', 'terrain-city', 'factory')


def recipe(scene, tier=1):
    if scene not in SCENES or tier not in (1, 2):
        raise ValueError('stress scenes allow tiers 1 or 2 only')
    lines = ['gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
             'gamerule doMobSpawning false', 'gamerule randomTickSpeed 0',
             'gamerule doEntityDrops false', 'gamerule doTileDrops false',
             'gamerule commandBlockOutput false', 'gamerule maxEntityCramming 0',
             'time set 6000', 'weather clear', 'forceload add -128 -128 127 127']
    # Every fill stays under the vanilla 32768-block limit. No unbounded world generation.
    for x in range(-128, 128, 16):
        lines.append(f'fill {x} 63 -128 {x+15} 64 127 minecraft:light_gray_concrete')
    counts = dict(chests=0, banners=0, armor_stands=0, item_frames=0,
                  create_motors=0, create_shafts=0, sable_assemblies=0, buildings=0)
    if scene in ('model-city', 'factory'):
        for layer in range(tier):
            for row in range(16):
                for col in range(16):
                    x, z = col*4-32, -row*4
                    y = 65 + (row//4)*4 + layer*20
                    # Low terraces keep far rows visible instead of hiding them behind a wall.
                    lines.append(f'fill {x} {y-1} {z-1} {x+3} {y-1} {z+2} minecraft:stone_bricks')
                    lines += [f'setblock {x} {y} {z} minecraft:chest[facing=south,type=single]',
                              f'setblock {x+1} {y} {z} minecraft:white_banner[rotation=0]']
                    ident = layer*256+row*16+col
                    raw = uuid.UUID(int=0xA0D17000000000000000000000000000+ident).bytes
                    ints = ','.join(str(int.from_bytes(raw[i:i+4], 'big', signed=True)) for i in range(0,16,4))
                    armor = ','.join('{id:"minecraft:diamond_'+part+'",count:1}'
                                     for part in ('boots','leggings','chestplate','helmet'))
                    lines.append(f'summon minecraft:armor_stand {x+2.5} {y} {z+1.5} '
                                 + '{Tags:["audit_fixture"],NoGravity:1b,Invulnerable:1b,ShowArms:1b,'
                                 + f'UUID:[I;{ints}],ArmorItems:[{armor}],Pose:{{Head:[0f,{ident%4*15}f,0f]}}}}')
                    item = ('oak_fence','hopper','brewing_stand','anvil')[ident%4]
                    lines += [f'setblock {x+3} {y+1} {z-1} minecraft:black_concrete',
                              f'summon minecraft:item_frame {x+3} {y+1} {z} '
                              + '{Tags:["audit_fixture"],Facing:3b,Fixed:1b,Invulnerable:1b,'
                              + f'Item:{{id:"minecraft:{item}",count:1}}}}']
                    for key in ('chests','banners','armor_stands','item_frames'): counts[key] += 1
        camera = [0.5, 83.0+10*(tier-1), 40.5, 180.0, 18.0]
    else:
        for row in range(12):
            for col in range(12):
                x, z = col*16-96, row*16-112
                height = (10+(row+col)%4*3)*tier
                lines += [f'fill {x} 65 {z} {x+9} {64+height} {z+9} minecraft:stone_bricks hollow',
                          f'fill {x+1} 68 {z+9} {x+8} {64+height} {z+9} minecraft:glass',
                          f'fill {x+11} 65 {z+1} {x+11} 69 {z+1} minecraft:oak_log',
                          f'fill {x+10} 69 {z} {x+13} 72 {z+3} minecraft:oak_leaves[persistent=true]',
                          f'fill {x+1} 64 {z+11} {x+8} 64 {z+12} minecraft:water']
                counts['buildings'] += 1
        camera = [0.5, 100.0, 123.5, 180.0, 23.0]
    if scene == 'factory':
        for row in range(8*tier):
            for col in range(8):
                x, z = 40+col*4, -row*6
                lines.append(f'setblock {x} 66 {z} create:creative_motor[facing=south]')
                counts['create_motors'] += 1
                for dz in range(1,5):
                    lines.append(f'setblock {x} 66 {z+dz} create:shaft[axis=z]')
                    counts['create_shafts'] += 1
        for i in range(16*tier):
            x, z = -100+(i%4)*10, -(i//4)*12
            lines += [f'fill {x} 69 {z} {x+7} 69 {z+7} minecraft:oak_planks',
                      f'fill {x} 70 {z} {x+7} 72 {z} minecraft:glass',
                      f'setblock {x+3} 70 {z+3} minecraft:chest[facing=south,type=single]',
                      f'sable assemble connected {x} 69 {z} 128']
            counts['sable_assemblies'] += 1
        camera = [0.5, 91.0, 74.5, 180.0, 22.0]
    lines += ['data modify storage audit:fixture built set value 1b',
              'say AUDIT_FIXTURE_BUILD_DISPATCHED (verify counts before measurement)']
    return lines, counts, camera
