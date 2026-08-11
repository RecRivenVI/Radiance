"""Generate one isolated functional line fixture; no benchmark sampling."""
from pathlib import Path
import argparse
import json


def configure_pipeline(instance, preset):
    if preset not in ('rt_dlss', 'rt_nrd_fsr') or not (instance / '.radiance-line-validation').is_file():
        raise ValueError('Explicit pipeline requires a known preset and an owned line fixture')
    folder = instance / 'radiance'
    folder.mkdir(exist_ok=True)
    config = dict(mode='PRESET', presetName='render_pipeline.preset.' + preset, presetModules=[])
    (folder / 'pipeline.yaml').write_text('!!com.radiance.client.pipeline.Pipeline$PipelineConfigStorage\n'
                                        + json.dumps(config, indent=2) + '\n', encoding='utf8')


def main(preset=None):
    import fixture
    repository = Path(__file__).resolve().parents[3]
    parent = repository / 'validations/regression-line_capture/instance/1.21.1-neoforge'
    instance = parent / 'client'
    if instance.exists():
        if not (instance / '.radiance-line-validation').is_file():
            raise RuntimeError('Existing instance has no line ownership marker')
        print('Keeping owned line validation fixture')
    else:
        fixture.REPOSITORY_RUN = parent
        fixture.create(instance, '1.21.1', modded=True, tier=1)
        (instance / '.radiance-line-validation').write_text('Stage A functional line validation\n', encoding='utf-8')
        (instance / '.radiance-acceptance').write_text('Isolated validation\n', encoding='utf-8')
        options = instance / 'options.txt'
        with options.open('a', encoding='utf-8', newline='\n') as out:
            out.write('onboardAccessibility:false\n')
    if preset is not None:
        configure_pipeline(instance, preset)
    print(instance)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--preset', choices=('rt_dlss', 'rt_nrd_fsr'))
    main(parser.parse_args().preset)
