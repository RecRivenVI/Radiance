from pathlib import Path
import json
import tempfile
import unittest
from prepare_line_validation import configure_pipeline


class LinePipelineTest(unittest.TestCase):
    def test_explicit_fsr_configuration_is_owned_and_records_actual_preset(self):
        area = Path(__file__).resolve().parents[1] / 'build/test-fixtures'
        area.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=area) as folder:
            root = Path(folder)
            with self.assertRaises(ValueError):
                configure_pipeline(root, 'rt_nrd_fsr')
            (root / '.radiance-line-validation').write_text('owned')
            configure_pipeline(root, 'rt_nrd_fsr')
            text = (root / 'radiance/pipeline.yaml').read_text(encoding='utf8')
            body = json.loads(text.split('\n', 1)[1])
            self.assertEqual('render_pipeline.preset.rt_nrd_fsr', body['presetName'])
            self.assertEqual([], body['presetModules'])
            before = text
            with self.assertRaises(ValueError):
                configure_pipeline(root, '../external')
            self.assertEqual(before, (root / 'radiance/pipeline.yaml').read_text(encoding='utf8'))


if __name__ == '__main__':
    unittest.main()
