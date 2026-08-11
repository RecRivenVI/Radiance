"""Prepare or launch the user's frozen pure-vanilla combined acceptance instance."""
from pathlib import Path
import argparse
import json
import os
import shutil
import subprocess
import zipfile
import nbtlib

from takeover_experiment import ROOT, TARGET, digest, storage_preflight, write
from takeover_city import assert_stopped

VALIDATION = ROOT / 'validations/visual-takeover_stages'
CLIENT = VALIDATION / 'instance' / TARGET / 'client'
RESULT = VALIDATION / 'result'
PRODUCT = ROOT / 'components/render_diagnostics/build/takeover-instancing/radiance.jar'
FIXTURES = {
    'Newport City': ROOT / 'validations/migration-city_world/result/vanilla-city-fixture.zip',
    'Stage 2 and 3 Gallery': ROOT / 'validations/conformance-entity_takeover/result/small-vanilla-fixture.zip',
}


def stage67_fixture(world):
    """An idempotent, ordinary datapack; no probe is needed for manual particle controls."""
    pack = world/'datapacks/radiance-stage67'
    commands = [
        'fill 20 64 -2 36 64 12 minecraft:light_gray_concrete',
        'fill 27 64 1 31 64 5 minecraft:stone',
        'fill 28 65 2 30 66 4 minecraft:water',
        'setblock 24 65 5 minecraft:campfire[lit=true]',
        'setblock 25 65 5 minecraft:soul_campfire[lit=true]',
        'setblock 32 65 3 minecraft:oak_leaves[persistent=true]',
        'setblock 33 65 3 minecraft:glass',
        'setblock 34 65 3 minecraft:red_stained_glass',
        'summon minecraft:armor_stand 26 65 5 {CustomName:\'{"text":"Stage 6 / 7"}\',CustomNameVisible:1b,NoGravity:1b}',
        'summon minecraft:text_display 26 68 5 {text:\'{"text":"World text + alpha"}\',billboard:"center",background:1073741824}',
    ]
    controls = [
        'particle minecraft:smoke 26 67 4 1 1 1 0.02 80 force',
        'particle minecraft:elder_guardian 26 67 4 0 0 0 0 1 force',
        'particle minecraft:glow 26 67 4 1 1 1 0.02 80 force',
        'summon minecraft:lightning_bolt 26 65 4',
        'summon minecraft:item 28 66 8 {Item:{id:"minecraft:diamond",count:1}}',
        'summon minecraft:firework_rocket 26 66 4 {LifeTime:20,FireworksItem:{id:"minecraft:firework_rocket",count:1,components:{"minecraft:fireworks":{flight_duration:1,explosions:[{shape:"small_ball",colors:[16720452],fade_colors:[4521864],has_trail:1b,has_twinkle:1b}]}}}}',
        'weather rain',
        'weather clear',
    ]
    for i, command in enumerate(controls):
        x=20+i*2
        commands += [f'setblock {x} 65 0 minecraft:command_block'+
                     '{Command:'+json.dumps(command)+',TrackOutput:0b}',
                     f'setblock {x} 66 0 minecraft:stone_button[face=floor,facing=north]']
    commands += ['data modify storage stage67:fixture built set value 1b', 'say STAGE67_FIXTURE_READY']
    files = {
        'pack.mcmeta': json.dumps({'pack': {'pack_format': 48, 'description': 'Radiance stage 6 and 7 visual fixture'}})+'\n',
        'data/minecraft/tags/function/load.json': json.dumps({'values': ['stage67:load']})+'\n',
        'data/stage67/function/load.mcfunction': 'forceload add 20 -2 36 12\nschedule function stage67:wait 1t replace\n',
        'data/stage67/function/wait.mcfunction': 'execute if loaded 20 64 0 if loaded 36 64 0 unless data storage stage67:fixture {built:1b} run function stage67:build\nexecute unless data storage stage67:fixture {built:1b} run schedule function stage67:wait 1t replace\n',
        'data/stage67/function/build.mcfunction': '\n'.join(commands)+'\n',
        'data/stage67/function/enter.mcfunction': 'gamemode creative @s\ntp @s 26 67 11 180 20\n',
    }
    hashes={}
    for name, text in files.items():
        path=pack/name
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(text,encoding='utf8')
        hashes[name]=digest(path)
    return hashes


def prepare(product_override=None):
    product = Path(product_override).resolve() if product_override is not None else PRODUCT
    if not product.is_relative_to(ROOT): raise ValueError('Use a frozen repository artifact')
    assert_stopped()
    occupancy = storage_preflight()
    if CLIENT.exists(): raise RuntimeError('Acceptance instance exists; preserve the user\'s saves and settings')
    if not product.is_file(): raise RuntimeError('Acceptance artifact is not frozen')
    # Reserve both fixtures, the release copy and its entire unpacked payload, plus runtime logs.
    # The payload includes more than the bootstrap actually extracts; no storage limit is relaxed.
    reserve = product.stat().st_size + 64 * 1024**2
    for archive in [product, *FIXTURES.values()]:
        with zipfile.ZipFile(archive) as z:
            reserve += sum(entry.file_size for entry in z.infolist())
    if occupancy['workspace'] + reserve > 16*1024**3 or occupancy['validations'] + reserve > 8*1024**3:
        raise RuntimeError('Insufficient acceptance storage reserve: ' + str(reserve))
    hashes = {}
    CLIENT.mkdir(parents=True)
    for world, source in FIXTURES.items():
        expected = json.loads(source.with_suffix('.json').read_text())['sha256']
        if digest(source) != expected: raise RuntimeError('Fixture identity differs: ' + world)
        hashes[world] = expected
        with zipfile.ZipFile(source) as z:
            for name in z.namelist():
                if name.startswith('world/'):
                    target = CLIENT / 'saves' / world / name.removeprefix('world/')
                elif world == 'Newport City' and name.startswith('settings/'):
                    target = CLIENT / name.removeprefix('settings/')
                else: continue
                if not target.resolve().is_relative_to(CLIENT.resolve()): raise ValueError('Unsafe fixture path')
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(z.read(name))
                if target.read_bytes() != z.read(name): raise ValueError('Fixture extraction differs')
        level = CLIENT / 'saves' / world / 'level.dat'
        nbt = nbtlib.load(level)
        nbt['Data']['LevelName'] = nbtlib.String('城市：合并验收' if world == 'Newport City' else '小型夹具：原版合并验收')
        nbt.save()
    controls = stage67_fixture(CLIENT/'saves/Stage 2 and 3 Gallery')
    options = CLIENT / 'options.txt'
    updated = dict(soundCategory_master='0.5', soundCategory_music='0.0',
                   overrideWidth='2560', overrideHeight='1440', fullscreen='false',
                   onboardAccessibility='false')
    rows = [r for r in options.read_text().splitlines() if r.split(':',1)[0] not in updated]
    options.write_text('\n'.join(rows + [f'{k}:{v}' for k,v in updated.items()])+'\n')
    mods = CLIENT / 'mods'; mods.mkdir()
    shutil.copy2(product, mods / 'radiance.jar')
    if digest(mods/'radiance.jar') != digest(product): raise ValueError('Product copy differs')
    cp = json.loads((ROOT/'build/takeover-stages/packaged-launch-classpath.json').read_text())['files']
    if any('/classes/java/main' in p.replace('\\','/') or '/classes/java/probe' in p.replace('\\','/') for p in cp):
        raise ValueError('Development classpath would replace frozen product')
    RESULT.mkdir(parents=True, exist_ok=True)
    (RESULT/'classpath.args').write_text('-cp\n"'+os.pathsep.join(cp).replace('\\','\\\\').replace('"','\\"')+'"\n')
    java = next((Path.home()/'.gradle/jdks').glob('eclipse_adoptium-21*/bin/java.exe'))
    moddev = ROOT / f'versions/{TARGET}/build/moddev'
    args = [str(java), f'@{RESULT/"classpath.args"}', f'@{moddev/"packagedClientRunVmArgs.txt"}',
            '-Xmx8G', f'@{moddev/"packagedClientRunProgramArgs.txt"}',
            '--username','RecRivenVI','--width','2560','--height','1440']
    aftermath = ROOT/'components/render_diagnostics/build/native/aftermath/RelWithDebInfo/radiance-aftermath-agent.dll'
    if aftermath.is_file(): args.insert(1, f'-agentpath:{aftermath}=gpu-diagnostics')
    write(RESULT/'prepared.json', dict(product=digest(product), productPath=str(product.relative_to(ROOT)), fixtures=hashes, mods=['radiance'],
          width=2560,height=1440,masterVolume=.5,musicVolume=0,player='RecRivenVI',
          args=args, acceptance='pending; no game launched by preparation', probe=False,
          stages=['0','1','2','3','4','5','6'], proceduralFixture=controls,
          storageBefore=occupancy, reservedBytes=reserve))
    print('Prepared pure acceptance instance:', CLIENT, flush=True)


def refresh(product, distance=8):
    """Replace the owned acceptance artifact while preserving the user's two saves."""
    product = product.resolve()
    if not 2 <= distance <= 32:
        raise ValueError('Acceptance view distance outside the supported range')
    if not product.is_relative_to(ROOT) or not product.is_file():
        raise ValueError('Use a frozen repository artifact')
    assert_stopped()
    occupancy = storage_preflight()
    receipt = json.loads((RESULT/'prepared.json').read_text())
    mods = CLIENT/'mods'
    if any(p.name not in ('radiance.jar', 'radiance-probe.jar') for p in mods.glob('*.jar')):
        raise RuntimeError('Acceptance instance contains an unexpected mod')
    for path in mods.glob('*.jar'):
        if path.name == 'radiance.jar' and digest(path) == digest(product):
            continue
        literal = str(path.resolve()).replace("'", "''")
        subprocess.run(['pwsh', '-NoProfile', '-Command',
            'Add-Type -AssemblyName Microsoft.VisualBasic; '
            f"[Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile('{literal}', 'OnlyErrorDialogs', 'SendToRecycleBin')"], check=True)
    if not (mods/'radiance.jar').exists():
        shutil.copy2(product, mods/'radiance.jar')
    if digest(mods/'radiance.jar') != digest(product):
        raise ValueError('Product copy differs')
    options = CLIENT/'options.txt'
    updated = dict(renderDistance=str(distance), soundCategory_master='0.5', soundCategory_music='0.0',
                   overrideWidth='2560', overrideHeight='1440', fullscreen='false')
    rows = [r for r in options.read_text(encoding='utf8').splitlines()
            if r.split(':', 1)[0] not in updated]
    options.write_text('\n'.join(rows + [f'{k}:{v}' for k,v in updated.items()])+'\n', encoding='utf8')
    receipt.update(product=digest(product), productPath=str(product.relative_to(ROOT)),
                   acceptance='Frozen product in this receipt awaits manual observation; not launched',
                   probe=False, renderDistance=distance, storageBefore=occupancy,
                   stages=['1','2','3'], historicalStages='0 through 6; see adr-13',
                   checklist='validations/visual-takeover_stages/validation.md')
    write(RESULT/'prepared.json', receipt)
    print('Refreshed pure acceptance instance without launching:', CLIENT, flush=True)


def launch():
    assert_stopped()
    storage_preflight()
    receipt = json.loads((RESULT/'prepared.json').read_text())
    jars = list((CLIENT/'mods').glob('*.jar'))
    if len(jars)!=1 or digest(jars[0])!=receipt['product']:
        raise RuntimeError('Acceptance mods or frozen product changed')
    env = dict(os.environ, MCVR_DEVICE_LOSS_TRACE='1', MCVR_LOCAL_AFTERMATH='1')
    env.pop('MOD_CLASSES', None)
    with (RESULT/'manual-console.log').open('a',encoding='utf8') as log:
        process = subprocess.Popen(receipt['args'],cwd=CLIENT,env=env,stdout=log,stderr=subprocess.STDOUT)
        write(RESULT/'manual-process.json',dict(pid=process.pid, product=receipt['product']))
        return process.wait()


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=('prepare','refresh','run'))
    parser.add_argument('--product',type=Path)
    parser.add_argument('--distance',type=int,default=8)
    options=parser.parse_args();action=options.action
    if action=='prepare': prepare(options.product)
    elif action=='refresh': refresh(options.product,options.distance)
    else: raise SystemExit(launch())
