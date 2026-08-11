"""Verified, new-instance-only unpacking of the authorized copied-world input."""
import hashlib
import json
from pathlib import Path, PurePosixPath
import tomllib
import zipfile


def members(archive):
    result = []
    for entry in archive.infolist():
        name = PurePosixPath(entry.filename)
        if (name.is_absolute() or '..' in name.parts or '\\' in entry.filename
                or ':' in entry.filename or entry.external_attr >> 16 & 0o170000 == 0o120000):
            raise ValueError(f'Unsafe fixture entry: {entry.filename}')
        result.append(entry)
    return result


def muted_options(text, render_distance=None):
    override = {'soundCategory_master': '0.0', 'pauseOnLostFocus': 'false',
                'onboardAccessibility': 'false', 'fullscreen': 'false'}
    if render_distance is not None:
        if type(render_distance) is not int or not 2 <= render_distance <= 32:
            raise ValueError('Render distance must be an integer in [2,32]')
        override['renderDistance'] = str(render_distance)
    rows = [line for line in text.splitlines() if line.partition(':')[0] not in override]
    return '\n'.join(rows + [f'{key}:{value}' for key, value in override.items()]) + '\n'


def prepare(repository, render_distance=None):
    validation = repository / 'validations/performance-chunk_loading'
    config = tomllib.loads((validation / 'validation.toml').read_text(encoding='utf-8'))
    name = config['world-input']
    declared = tomllib.loads((repository / 'inputs.toml').read_text(encoding='utf-8'))[name]
    local = tomllib.loads((repository / 'local.toml').read_text(encoding='utf-8'))
    source = Path(local['inputs'][name])
    if not source.is_absolute():
        source = repository / source
    digest = hashlib.file_digest(source.open('rb'), 'sha256').hexdigest()
    if digest not in declared['sha256']:
        raise ValueError('Copied-world input hash mismatch')
    instance = validation / 'instance' / config['targets'][0] / 'client'
    if instance.exists():
        raise FileExistsError('Preserve existing diagnostic instance/evidence before another run')
    with zipfile.ZipFile(source) as archive:
        entries = members(archive)
        required = {'saves/Newport City/level.dat', 'options.txt',
                    'radiance/options.properties', 'radiance/pipeline.yaml'}
        if not required.issubset({entry.filename for entry in entries}):
            raise ValueError('Incomplete copied-world fixture')
        options_text = muted_options(archive.read('options.txt').decode('utf-8'), render_distance)
        instance.mkdir(parents=True)
        archive.extractall(instance, members=entries)
    options = instance / 'options.txt'
    options.write_text(options_text, encoding='utf-8', newline='\n')
    (instance / '.radiance-audit-test-instance').write_text('copied-world loading diagnosis\n', encoding='utf-8')
    (instance / '.radiance-loading').write_text(digest + '\n', encoding='utf-8')
    receipt = {'input': name, 'sha256': digest, 'instance': str(instance),
               'overrides': ['master volume=0', 'pauseOnLostFocus=false',
                             'onboardAccessibility=false', 'fullscreen=false']
                            + ([] if render_distance is None else [f'renderDistance={render_distance}']),
               'render_distance_override': render_distance,
               'configuration_sha256': {name: hashlib.sha256((instance / name).read_bytes()).hexdigest()
                                        for name in ('options.txt', 'radiance/options.properties',
                                                     'radiance/pipeline.yaml')},
               'world_edits': False, 'source_world_modified': False}
    (instance / 'FIXTURE.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(receipt, indent=2))


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('--render-distance', type=int, help='Only intentional setting changed for distance comparisons')
    prepare(Path(__file__).resolve().parents[3], parser.parse_args().render_distance)
