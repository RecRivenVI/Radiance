"""Read saved fixture evidence and real-frame samples. Does not assign visual acceptance."""
from pathlib import Path
import argparse
from collections import Counter
import csv
import hashlib
import io
import json
import statistics
import zlib
import zipfile
import nbtlib


def chunks(folder):
    for path in folder.glob('*.mca'):
        b = path.read_bytes()
        for i in range(1024):
            offset = int.from_bytes(b[i*4:i*4+3], 'big') * 4096
            if not offset:
                continue
            size = int.from_bytes(b[offset:offset+4], 'big')
            compression = b[offset+4]
            if compression & 128:
                raise ValueError('External oversized chunk needs a separate decoder: '+str(path))
            payload = b[offset+5:offset+4+size]
            if compression == 2:
                payload = zlib.decompress(payload)
            elif compression != 3:
                raise ValueError('Unsupported compression '+str(compression))
            yield nbtlib.File.parse(io.BytesIO(payload))


def distribution(values):
    values = sorted(values)
    if not values:
        return None
    def percentile(p):
        at = (len(values)-1)*p
        low = int(at)
        return values[low]+(values[min(low+1,len(values)-1)]-values[low])*(at-low)
    return {'n': len(values), 'mean': statistics.mean(values), 'p50': percentile(.5),
            'p95': percentile(.95), 'p99': percentile(.99), 'min': values[0], 'max': values[-1]}


def analyze(case):
    case = Path(case)
    manifest = json.loads((case/'FIXTURE.json').read_text())
    launch = json.loads((case/'launch.json').read_text())
    for item in launch['artifacts']:
        assert hashlib.sha256(Path(item['path']).read_bytes()).hexdigest().upper() == item['sha256'], item['path']
    loaded=json.loads((case/'loaded-core.json').read_text(encoding='utf-8-sig'))
    if isinstance(loaded,list):
        assert len(loaded)==1
        loaded=loaded[0]
    renderer=[p for p in (case/'mods').glob('Radiance-*.jar')]
    assert len(renderer)==1
    with zipfile.ZipFile(renderer[0]) as jar:
        entries=[name for name in jar.namelist() if name=='core.dll' or name.endswith('/core.dll')]
        assert len(entries)==1
        embedded=hashlib.sha256(jar.read(entries[0])).hexdigest().upper()
    assert embedded==loaded['sha256']==hashlib.sha256(Path(loaded['path']).read_bytes()).hexdigest().upper(), 'Loaded/embedded core mismatch'
    world = case/'saves/Audit Benchmark'
    counts = Counter()
    kinetic = []
    for chunk in chunks(world/'region'):
        for entity in chunk.get('block_entities', []):
            counts[str(entity['id'])] += 1
            if str(entity['id']).startswith('create:'):
                kinetic.append({'id': str(entity['id']), 'speed': float(entity.get('Speed', 0))})
    for chunk in chunks(world/'entities'):
        for entity in chunk.get('Entities', []):
            counts[str(entity['id'])] += 1
    mappings = {'chests':'minecraft:chest','banners':'minecraft:banner','armor_stands':'minecraft:armor_stand','item_frames':'minecraft:item_frame',
                'create_motors':'create:motor','create_shafts':'create:simple_kinetic'}
    checks = {key: counts[target] >= manifest['expected'][key] for key,target in mappings.items()}
    occupancy=world/'data/sable_sub_level_occupancy.dat'
    if occupancy.exists():
        bits=nbtlib.load(occupancy)['data']['sub_level_occupancy']
        counts['sable:occupied_slots']=sum((int(value)&((1<<64)-1)).bit_count() for value in bits)
    checks['sable_assemblies']=counts['sable:occupied_slots']==manifest['expected']['sable_assemblies']
    checks['kinetics_running']=all(item['speed']!=0 for item in kinetic) if kinetic else manifest['expected']['create_motors']==0
    profiles = []
    for frames in sorted(case.glob('radiance-audit/benchmark/*/frames.csv')):
        with frames.open() as stream:rows = list(csv.DictReader(stream))
        profiles.append({'source': str(frames), 'frames':len(rows),
            'frame_wall_ms': distribution([int(row['frame_wall_ns'])/1e6 for row in rows]),
            'real_start_interval_ms':distribution([int(row['start_interval_ns'])/1e6 for row in rows]),
            'glfw_focus': dict(Counter(row.get('glfw_actual_focused', 'unavailable') for row in rows)),
            'minecraft_cached_focus':dict(Counter(row.get('minecraft_cached_focused', row.get('actual_window_focused','unavailable')) for row in rows)),
            'status':(frames.parent/'STATUS.txt').read_text()})
    player=nbtlib.load(world/'level.dat')['Data'].get('Player',{})
    result={'counts':dict(counts), 'minimumCountChecks':checks, 'kinetics':kinetic, 'verifiedCore':loaded,
            'camera':{'position':list(map(float,player.get('Pos',[]))), 'rotation':list(map(float,player.get('Rotation',[])))},
            'profiles':profiles, 'process':json.loads((case/'result.json').read_text(encoding='utf-8-sig')),
            'performanceComparability':'PRECHECK_ONLY: confirm renderer options, actual dimensions, warm state and repeated A/B before comparing',
            'limitations':['saved object counts do not prove visible/instanced/PT submission',
                'Sable assemblies require separate saved-state verification', 'no generated-frame or native GPU timer evidence',
                'per-frame CPU nanoseconds may be quantized on Windows; use interval aggregates']}
    (case/'ANALYSIS.json').write_text(json.dumps(result,indent=2))
    return result


if __name__ == '__main__':
    p=argparse.ArgumentParser();p.add_argument('case',type=Path)
    result=analyze(p.parse_args().case)
    print(json.dumps({'counts':result['counts'],'checks':result['minimumCountChecks'],'process':result['process']},indent=2))
