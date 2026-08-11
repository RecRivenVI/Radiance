import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from renderer_settings import expectations, runtime_options


class EffectiveSettingsTest(unittest.TestCase):
    def test_only_storage_migration_marker_is_excluded_from_runtime_comparison(self):
        values={'displayDefaultsRevision':'7','dlssFrameGeneration':'false','unknownRenderingOption':'keep-strict'}
        self.assertEqual({'dlssFrameGeneration':'false','unknownRenderingOption':'keep-strict'},runtime_options(values))
        self.assertEqual('7',values['displayDefaultsRevision'])

    def test_pack_settings_override_legacy_bounces_and_preset_can_override_pack(self):
        prefix='render_pipeline.module.ray_tracing.attribute.'
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp);(p/'radiance/shaders/world/ray_tracing').mkdir(parents=True)
            with zipfile.ZipFile(p/'pack.zip','w') as z:
                z.writestr('configs.json',json.dumps({'attributes':[{'name':prefix+k} for k in ('num_ray_bounces','coverage_mode')]}))
            with zipfile.ZipFile(p/'core.jar','w') as z:
                z.write(p/'pack.zip','shaders/world/ray_tracing/vanilla-pt.zip')
            (p/'options.txt').write_text('resourcePacks:["vanilla","mod_resources"]\n')
            (p/'radiance/options.properties').write_text('rayBounces=4\ndisplayDefaultsRevision=7\n')
            (p/'radiance/shaders/world/ray_tracing/vanilla-pt.zip.txt').write_text(
                prefix+'num_ray_bounces=128\n'+prefix+'coverage_mode=blend\nobsolete=ignored\n')
            header='!!com.radiance.client.pipeline.Pipeline$PipelineConfigStorage\nmode: PRESET\npresetName: render_pipeline.preset.rt_dlss\npresetModules:\n- entryName: render_pipeline.module.ray_tracing.name\n  attributes:\n  - name: '+prefix+'shader_pack_path\n    value: \'\'\n'
            config=p/'radiance/pipeline.yaml';config.write_text(header)
            a=expectations(p/'core.jar',p)
            ray=a['modules']['render_pipeline.module.ray_tracing.name']
            self.assertEqual('128',ray[prefix+'num_ray_bounces'])
            self.assertEqual('4',a['options']['rayBounces'])
            self.assertNotIn('displayDefaultsRevision',a['options'])
            self.assertEqual({'displayDefaultsRevision':'7'},a['storageMetadata'])
            self.assertEqual('blend',ray[prefix+'coverage_mode'])
            self.assertNotIn('obsolete',ray)
            config.write_text(header+'  - name: '+prefix+'num_ray_bounces\n    value: \'6\'\n')
            self.assertEqual('6',expectations(p/'core.jar',p)['modules']['render_pipeline.module.ray_tracing.name'][prefix+'num_ray_bounces'])


if __name__ == '__main__':
    unittest.main()
