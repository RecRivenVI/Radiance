"""Prepare the bounded query-rule smoke fixture; no performance sampling."""
from pathlib import Path
import json
import tomllib
import fixture

repository = Path(__file__).resolve().parents[3]
validation = repository / 'validations/regression-query_visibility'
config = tomllib.loads((validation / 'validation.toml').read_text(encoding='utf-8'))
target = config['targets'][0]
parent = validation / 'instance' / target
instance = parent / 'client'
if instance.exists():
    if not (instance / '.radiance-acceptance').is_file():
        raise RuntimeError('Existing query instance has no ownership marker; refusing to overwrite')
    print('Keeping existing isolated query fixture')
else:
    fixture.REPOSITORY_RUN = parent
    manifest = fixture.create(instance, target.rsplit('-', 1)[0], modded=True, tier=1)
    (instance / '.radiance-acceptance').write_text('Query-rule functional validation\n', encoding='utf-8')
    build = instance / 'saves/Audit Benchmark/datapacks/audit-fixture/data/audit/function/build.mcfunction'
    with build.open('a', encoding='utf-8', newline='\n') as stream:
        stream.write('fill -9 65 -2 -4 66 3 minecraft:water\n')
        stream.write('execute as @e[type=minecraft:armor_stand,limit=1] run data merge entity @s {Glowing:1b,CustomName:\'{"text":"Query fixture"}\',CustomNameVisible:1b}\n')
    manifest['purpose'] = 'Functional query smoke only; no visual or performance judgment'
    (instance / 'FIXTURE.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print('Created isolated query fixture', instance)
