"""Run actual vanilla OptimizeWorldScreen, inspect every saved region and freeze its copy."""
from pathlib import Path
from collections import Counter
import gzip
import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import time
import tomllib
import zipfile
import zlib
import nbtlib
import numpy as np

from takeover_experiment import ROOT, TARGET, archive, digest, recycle, storage_preflight, write
from takeover_city import assert_stopped

VALIDATION = ROOT / 'validations/migration-city_world'
RESULT = VALIDATION / 'result'
CLIENT = VALIDATION / 'instance' / TARGET / 'client'
WORLD = CLIENT / 'saves/Newport City'
CAMERA = [95.784462690, 141.607489199, 12.897247229]
ROTATION = [53.53503, 32.74906]
AIR = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'}


def region_chunks(path, data=None):
    if data is None: data = path.read_bytes()
    if len(data) < 8192: raise ValueError('Truncated region: ' + str(path))
    for i in range(1024):
        sector = int.from_bytes(data[4*i:4*i+3], 'big')
        if not sector: continue
        at = sector * 4096
        size = int.from_bytes(data[at:at+4], 'big')
        capacity = data[4*i+3] * 4096
        if at < 8192 or size < 2 or size + 4 > capacity or at + size + 4 > len(data):
            raise ValueError('Invalid chunk allocation: ' + str(path))
        kind = data[at+4]
        payload = data[at+5:at+4+size]
        if kind == 2: payload = zlib.decompress(payload)
        elif kind == 1: payload = gzip.decompress(payload)
        elif kind != 3: raise ValueError('Unsupported compression: ' + str(kind))
        yield nbtlib.File.parse(io.BytesIO(payload))


def section_nonair(section):
    if 'Blocks' in section:
        blocks = bytes(section['Blocks'])
        add = bytes(section.get('Add', []))
        if not any(add): return len(blocks) - blocks.count(0)
        return sum(value != 0 or bool(add and ((add[i//2] >> ((i%2)*4)) & 15))
                   for i, value in enumerate(blocks))
    states = section.get('block_states')
    if states is None: return 0
    palette = states['palette']
    present = [str(p['Name']) not in AIR for p in palette]
    if len(palette) == 1: return 4096 if present[0] else 0
    bits = max(4, (len(palette)-1).bit_length())
    per_long = 64 // bits
    longs = states['data']
    words = np.asarray(longs, dtype=np.int64).view(np.uint64)
    indices = ((words[:,None] >> (np.arange(per_long,dtype=np.uint64)*bits))
               & ((1 << bits)-1)).reshape(-1)[:4096]
    if len(indices)!=4096 or int(indices.max())>=len(palette):
        raise ValueError('Palette index out of bounds')
    return int(np.count_nonzero(np.asarray(present,dtype=np.bool_)[indices]))


def inspect(world):
    level = nbtlib.load(world / 'level.dat')['Data']
    rows = {}
    versions = Counter()
    for path in sorted(world.rglob('*.mca')):
        if path.parent.name != 'region': continue
        for root in region_chunks(path):
            chunk = root.get('Level', root)
            key = f'{path.parent.parent.relative_to(world).as_posix()}:{int(chunk["xPos"])},{int(chunk["zPos"])}'
            if key in rows: raise ValueError('Duplicate saved chunk: ' + key)
            count = sum(section_nonair(s) for s in chunk.get('Sections', chunk.get('sections', [])))
            rows[key] = count
            versions[int(root.get('DataVersion', -1))] += 1
    return dict(levelDataVersion=int(level.get('DataVersion', -1)),
                version={k:str(v) for k,v in level.get('Version', {}).items()},
                chunks=len(rows), nonairBlocks=sum(rows.values()),
                chunkDataVersions=dict(versions), perChunkNonair=rows)


def verify_upgrade(before, after):
    if before['perChunkNonair'].keys() != after['perChunkNonair'].keys():
        raise ValueError('World optimization changed saved chunk set')
    lost = {k:(v, after['perChunkNonair'][k]) for k,v in before['perChunkNonair'].items()
            if after['perChunkNonair'][k] < v * .98}
    if lost: raise ValueError('Possible mass block loss; stop: ' + str(list(lost.items())[:20]))
    if after['levelDataVersion'] <= before['levelDataVersion']:
        raise ValueError('Level was not upgraded')
    if set(map(int, after['chunkDataVersions'])) != {after['levelDataVersion']}:
        raise ValueError('Not every chunk reached current data version')


def verify_frozen():
    """Compare the original height range too: new below-zero terrain cannot hide old block loss."""
    fixture = RESULT/'vanilla-city-fixture.zip'
    manifest = json.loads(fixture.with_suffix('.json').read_text())
    case = ROOT / manifest['upgrade']['case']
    before = json.loads((case/'before.json').read_text())
    counts = {}
    with zipfile.ZipFile(fixture) as z:
        for name in z.namelist():
            path = Path(name)
            if path.suffix!='.mca' or path.parent.name!='region': continue
            dimension = path.parent.parent.relative_to('world').as_posix()
            for root in region_chunks(path, z.read(name)):
                key = f'{dimension}:{int(root["xPos"])},{int(root["zPos"])}'
                counts[key] = sum(section_nonair(s) for s in root.get('sections', [])
                                  if 0 <= int(s['Y']) < 16)
    expected = before['perChunkNonair']
    if counts.keys()!=expected.keys(): raise ValueError('Frozen chunk set differs')
    lost = {k:(n,counts[k]) for k,n in expected.items() if counts[k] < n*.98}
    write(RESULT/'frozen-integrity.json',dict(sha256=digest(fixture), chunks=len(counts),
          originalHeight=[0,256], originalNonair=sum(expected.values()),
          upgradedNonairInOriginalHeight=sum(counts.values()),
          missingOrSignificantlyReduced=lost, passed=not lost))
    if lost: raise ValueError('Original-height block loss; stop before city runs: '+str(list(lost.items())[:10]))


def align_settings():
    """Retain the first fixture as an explicit rejected-settings candidate; world bytes stay exact."""
    fixture = RESULT/'vanilla-city-fixture.zip'
    manifest = json.loads(fixture.with_suffix('.json').read_text())
    original = ROOT/'validations/performance-scene_diagnosis/result/retained-city-inputs/dynamic-baked-comparison.zip'
    candidate = RESULT/'vanilla-city-fixture-settings-candidate.zip'
    candidate_manifest = candidate.with_suffix('.json')
    if candidate.exists(): raise RuntimeError('Settings correction already retained; do not overwrite')
    with zipfile.ZipFile(original) as old:
        content = old.read('settings/options.txt')
    replacement = RESULT/'vanilla-city-fixture-aligned.zip'
    with zipfile.ZipFile(fixture) as source, zipfile.ZipFile(replacement,'x',zipfile.ZIP_DEFLATED) as dest:
        for name in source.namelist():
            dest.writestr(name, content if name=='settings/options.txt' else source.read(name))
    with zipfile.ZipFile(fixture) as source, zipfile.ZipFile(replacement) as dest:
        changed = [n for n in source.namelist() if source.read(n)!=dest.read(n)]
        if changed!=['settings/options.txt'] or dest.read(changed[0])!=content:
            raise ValueError('Settings alignment changed world or other settings')
        for entry in manifest['files']:
            entry['sha256'] = hashlib.sha256(dest.read(entry['path'])).hexdigest()
            entry['bytes'] = len(dest.read(entry['path']))
    manifest['previousFixtureSha256'] = manifest['sha256']
    manifest['sha256'] = digest(replacement)
    manifest['settingsCorrection'] = 'Original simulationDistance=32 and pauseOnLostFocus restored exactly; no world bytes changed'
    fixture.rename(candidate)
    fixture.with_suffix('.json').rename(candidate_manifest)
    replacement.rename(fixture)
    write(fixture.with_suffix('.json'), manifest)
    integrity_path = RESULT/'frozen-integrity.json'
    integrity = json.loads(integrity_path.read_text())
    if integrity['sha256']!=manifest['previousFixtureSha256']: raise ValueError('Integrity identity differs')
    integrity['previousFixtureSha256'] = integrity['sha256']
    integrity['sha256'] = manifest['sha256']
    integrity['worldBytesUnchanged'] = True
    write(integrity_path, integrity)


def freeze(world, source_hash, report):
    # World changes below are applied only after vanilla optimization has passed.
    datafile = nbtlib.load(world / 'level.dat')
    data = datafile['Data']
    data['LevelName'] = nbtlib.String('Newport City — Stage 0–3 acceptance')
    data['Time'] = nbtlib.Long(6000)
    data['DayTime'] = nbtlib.Long(6000)
    for key in ('raining', 'thundering'): data[key] = nbtlib.Byte(0)
    for key in ('rainTime', 'thunderTime', 'clearWeatherTime'): data[key] = nbtlib.Int(2147483647)
    rules = data.setdefault('GameRules', nbtlib.Compound())
    for key in ('doDaylightCycle', 'doWeatherCycle', 'doMobSpawning'):
        rules[key] = nbtlib.String('false')
    rules['randomTickSpeed'] = nbtlib.String('0')
    data['GameType'] = nbtlib.Int(1)
    player = data['Player']
    player['Pos'] = nbtlib.List[nbtlib.Double](CAMERA)
    player['Rotation'] = nbtlib.List[nbtlib.Float](ROTATION)
    player['Motion'] = nbtlib.List[nbtlib.Double]([0,0,0])
    player['playerGameType'] = nbtlib.Int(1)
    abilities = player.setdefault('abilities', nbtlib.Compound())
    for key in ('flying', 'mayfly', 'instabuild', 'invulnerable'): abilities[key] = nbtlib.Byte(1)
    datafile.save()
    destination = RESULT / 'vanilla-city-fixture.zip'
    entries = []
    with zipfile.ZipFile(destination, 'x', zipfile.ZIP_DEFLATED) as out:
        for source in sorted(world.rglob('*')):
            if not source.is_file() or source.name == 'session.lock': continue
            name = 'world/' + source.relative_to(world).as_posix()
            out.write(source, name)
            entries.append(dict(path=name, bytes=source.stat().st_size, sha256=digest(source)))
        old = ROOT / 'validations/performance-scene_diagnosis/result/retained-city-inputs/dynamic-baked-comparison.zip'
        with zipfile.ZipFile(old) as settings:
            for name in settings.namelist():
                if not name.startswith('settings/'): continue
                content = settings.read(name)
                out.writestr(name, content)
                entries.append(dict(path=name, bytes=len(content), sha256=hashlib.sha256(content).hexdigest()))
    with zipfile.ZipFile(destination) as z:
        for entry in entries:
            if hashlib.sha256(z.read(entry['path'])).hexdigest() != entry['sha256']:
                raise ValueError('Fixture readback mismatch')
    write(destination.with_suffix('.json'), dict(sha256=digest(destination), sourceSha256=source_hash,
          files=entries, camera=CAMERA, rotation=ROTATION, renderDistance=32, dayTime=6000,
          weather='clear', upgrade=report, settingsSource=str(old.relative_to(ROOT)),
          note='Vanilla optimized world; same fixed graphics as former city, no third-party mod JARs'))


def run():
    assert_stopped()
    occupancy = storage_preflight()
    if occupancy['workspace'] + 500_000_000 > 16 * 1024**3:
        raise RuntimeError('Insufficient upgrade storage reserve')
    if CLIENT.exists(): raise RuntimeError('Previous upgrade client retained; inspect first')
    local = tomllib.loads((ROOT/'local.toml').read_text(encoding='utf8'))
    registered = tomllib.loads((ROOT/'inputs.toml').read_text(encoding='utf8'))['epicsawrunner_city_original']
    source = Path(local['inputs']['epicsawrunner_city_original'])
    source_hash = digest(source)
    if source_hash not in registered['sha256']: raise RuntimeError('Original city input differs')
    RESULT.mkdir(parents=True, exist_ok=True)
    case = RESULT / ('upgrade-' + time.strftime('%Y%m%d-%H%M%S'))
    case.mkdir()
    WORLD.mkdir(parents=True)
    with zipfile.ZipFile(source) as z:
        for name in z.namelist():
            if name.endswith('/'): continue
            relative = name.split('/',1)[1]
            output = WORLD / relative
            if not output.resolve().is_relative_to(WORLD.resolve()): raise ValueError('ZIP leaves world')
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(z.read(name))
    (CLIENT/'.radiance-vanilla-upgrade').write_text('Owned vanilla world optimization\n')
    (CLIENT/'.radiance-takeover-stages').write_text('Owned vanilla world optimization\n')
    (CLIENT/'.radiance-audit-test-instance').write_text('Owned vanilla world optimization\n')
    (CLIENT/'mods').mkdir()
    (CLIENT/'options.txt').write_text('soundCategory_master:0.0\nsoundCategory_music:0.0\nonboardAccessibility:false\npauseOnLostFocus:false\nfullscreen:false\n')
    before = inspect(WORLD)
    write(case/'before.json', before)
    cp = json.loads((ROOT/'build/takeover-stages/packaged-launch-classpath.json').read_text())['files']
    cp = [p for p in cp if '/components/' not in p.replace('\\','/')]
    if any('/classes/java/main' in p.replace('\\','/') or '/classes/java/probe' in p.replace('\\','/') for p in cp):
        raise ValueError('Product/probe classes would contaminate vanilla optimization')
    (case/'classpath.args').write_text('-cp\n"' + os.pathsep.join(cp).replace('\\','\\\\').replace('"','\\"') + '"\n')
    java = next((Path.home()/'.gradle/jdks').glob('eclipse_adoptium-21*/bin/java.exe'))
    moddev = ROOT / f'versions/{TARGET}/build/moddev'
    legacy = moddev / 'packagedClientLegacyClasspath.txt'
    pure_legacy = case / 'vanilla-legacy-classpath.txt'
    pure_legacy.write_text('\n'.join(p for p in legacy.read_text().splitlines()
                                         if '/components/' not in p.replace('\\','/')), encoding='utf8')
    agent = ROOT/'components/render_diagnostics/build/benchmark-support/radiance-benchmark-agent.jar'
    args = [str(java), f'-javaagent:{agent}', '-Dradiance.audit.optimizeWorld=true',
            '-Dradiance.audit.experiments=true',
            '-Dradiance.audit.correctnessOnly=true', '-Dradiance.audit.unattended=true',
            '-Dradiance.audit.benchmark=true', '-Dradiance.audit.benchmark.version='+TARGET,
            f'-Dvalidation.result={case}', f'@{case/"classpath.args"}',
            f'@{moddev/"packagedClientRunVmArgs.txt"}', f'-DlegacyClassPath.file={pure_legacy}', '-Xmx4G',
            f'@{moddev/"packagedClientRunProgramArgs.txt"}', '--username', 'RecRivenVI',
            '--width','1920','--height','1080']
    env = dict(os.environ); env.pop('MOD_CLASSES', None)
    write(case/'launch.json', dict(args=args, source=str(source), sourceSha256=source_hash,
          storage=occupancy, mods=[], productLoaded=False, probeLoaded=False,
          method='vanilla OptimizeWorldScreen.create, eraseCache=false'))
    with (case/'console.log').open('x', encoding='utf8') as log:
        process = subprocess.Popen(args, cwd=CLIENT, env=env, stdout=log, stderr=subprocess.STDOUT)
        write(case/'process.json', dict(pid=process.pid))
        code = process.wait(timeout=1200)
    log_file = CLIENT/'logs/latest.log'
    logs = log_file.read_text(encoding='utf8', errors='replace') if log_file.is_file() else ''
    archive([p for p in (CLIENT/'logs').rglob('*') if p.is_file()], CLIENT, case/'logs.zip')
    result = case/'optimization.properties'
    failures = [line for line in logs.splitlines() if re.search(r'\bERROR\b|Failed to (?:upgrade|load|read|save)|Error upgrading', line)]
    if code or failures or not result.is_file() or (case/'optimization-failure.txt').exists():
        write(case/'failure.json', dict(exitCode=code, errors=failures, automaticRetry=False))
        raise RuntimeError('Vanilla upgrade failed; retain copy and stop: ' + str(case))
    after = inspect(WORLD)
    write(case/'after.json', after)
    verify_upgrade(before, after)
    if digest(source) != source_hash: raise ValueError('Original input changed')
    report = dict(before={k:v for k,v in before.items() if k!='perChunkNonair'},
                  after={k:v for k,v in after.items() if k!='perChunkNonair'},
                  vanillaResult=result.read_text(), errors=[], sourceUnchanged=True,
                  case=str(case.relative_to(ROOT)), camera=CAMERA, rotation=ROTATION)
    freeze(WORLD, source_hash, report)
    write(case/'receipt.json', report)
    recycle(CLIENT, VALIDATION)
    print(json.dumps(report, indent=2), flush=True)


if __name__ == '__main__':
    import sys
    if sys.argv[1:] == ['--verify-frozen']: verify_frozen()
    elif sys.argv[1:] == ['--align-settings']: align_settings()
    elif sys.argv[1:]: raise SystemExit('Unknown city upgrade arguments')
    else: run()
