"""No-height surfaces must inherit shared material/light/medium behavior."""
import json
from pathlib import Path
import unittest
from unittest.mock import Mock

from compile_world_materials import shader_attribute_values

ROOT = Path(__file__).resolve().parents[3]
PACKS = ROOT / 'components/native_renderer/src/shader/world/ray_tracing/internal'


class MaterialShaderVariantsTest(unittest.TestCase):
    def test_shader_sidecar_overrides_preset_and_pack_defaults(self):
        properties=Mock()
        properties.read_text.return_value='# own settings\nmoon=0.64,0.8,1.6\nexpression=x=y\n'
        values={'moon':'0.000016,0.00002,0.00004','preset':'retained'}
        values.update(shader_attribute_values(properties))
        self.assertEqual('0.64,0.8,1.6',values['moon'])
        self.assertEqual('x=y',values['expression'])
        self.assertEqual('retained',values['preset'])

    def test_no_height_groups_use_shared_shading_and_explicit_capability(self):
        def groups(item):
            if isinstance(item, dict):
                for key, value in item.items():
                    if key == 'entity_solid_z_offset_forward':
                        yield value
                    else:
                        yield from groups(value)
            elif isinstance(item, list):
                for value in item:
                    yield from groups(value)
        for name in ('vanilla-pt', 'advanced'):
            with self.subTest(pack=name):
                selected = list(groups(json.loads((PACKS / name / 'configs.json').read_text())))
                self.assertTrue(selected)
                for group in selected:
                    self.assertTrue(group['shaders']['rchit'].endswith('/default.rchit'))
                    self.assertEqual('1', group['definitions']['RADIANCE_NO_HEIGHT_SURFACE'])
        primary = (PACKS / 'advanced/primary/default.rchit').read_text()
        self.assertIn('surfaceCachePackedData |= NO_HEIGHT_SURFACE_BIT;', primary)
        self.assertEqual(2, primary.count('encodeFirstHitUint(surfaceCachePackedData)'))


if __name__ == '__main__':
    unittest.main()
