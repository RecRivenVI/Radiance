import contextlib
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import scene_replay


class ReplayReportTest(unittest.TestCase):
    def test_display_pngs_ignore_unused_alpha(self):
        from PIL import Image

        with tempfile.TemporaryDirectory(dir=scene_replay.ROOT / 'components/render_diagnostics/build') as directory:
            root = Path(directory)
            (root / 'result.json').write_text(json.dumps(dict(width=1, height=1, windows=[
                dict(name='A', shader_pack='pack.zip', route='fixed', warmup_frames=0, timed_frames=2,
                     image_format=97, image_samples=2),
                dict(name='B', shader_pack='pack.zip', route='turn', warmup_frames=0, timed_frames=2,
                     image_format=37, image_samples=1, capture_frames=[0])])), encoding='utf8')
            reference = struct.pack('<4e', 1, .5, .25, 0)
            (root / 'scene.json').write_text(json.dumps(dict(reference_image=dict(file='reference', format=97))),
                                            encoding='utf8')
            with zipfile.ZipFile(root / 'blobs.zip', 'w') as archive:
                archive.writestr('reference', reference)
            (root / 'A.bin').write_bytes(reference)
            (root / 'A-average.bin').write_bytes(struct.pack('<4f', 1, .5, .25, 0))
            for name in ('B.bin', 'B-frame-0.bin'):
                (root / name).write_bytes(bytes((255, 128, 64, 0)))

            scene_replay.images(root, root)

            for name in ('A.png', 'A-average.png', 'B.png', 'B-frame-0.png', 'game-reference.png'):
                with self.subTest(name=name), Image.open(root / name) as image:
                    self.assertEqual(image.mode, 'RGB')
                    self.assertEqual(image.getpixel((0, 0)), (255, 128, 64))

    def test_external_extra_is_frozen_inside_owned_snapshot_before_runner_uses_it(self):
        with tempfile.TemporaryDirectory(dir=scene_replay.ROOT / 'components/render_diagnostics/build') as directory:
            root = Path(directory)
            jar, extra = root / 'radiance.jar', root / 'extra.jar'
            with zipfile.ZipFile(jar, 'w') as archive:
                archive.writestr('core.dll', b'native identity')
                archive.writestr('shaders/test.spv', b'shader identity')
            extra.write_bytes(b'Extra build')
            snapshot = root / 'validations/performance-scene_replay/result/snapshot'
            with patch.object(scene_replay, 'ROOT', root), patch.object(
                    scene_replay.subprocess, 'check_output', return_value='pinned\n'), contextlib.redirect_stdout(io.StringIO()):
                scene_replay.prepare(jar, snapshot, extra)
            self.assertEqual((snapshot / 'inputs/extra.jar').read_bytes(), extra.read_bytes())
            identity = json.loads((snapshot / 'capture-identity.json').read_text())
            self.assertEqual(identity['extra']['sha256'], scene_replay.digest(snapshot / 'inputs/extra.jar'))

    def test_delayed_gpu_results_use_owner_frame_and_exclude_capture_and_warmup(self):
        with tempfile.TemporaryDirectory(dir=scene_replay.ROOT / 'components/render_diagnostics/build') as directory:
            root = Path(directory)
            (root / 'result.json').write_text(json.dumps(dict(windows=[
                dict(name='A', route='fixed', prepare_ms=42,
                     resources_at_start=dict(process_cpu_100ns=10000, private_bytes=100),
                     resources_at_end=dict(process_cpu_100ns=50000, private_bytes=120)),
                dict(name='B', route='fixed', prepare_ms=7)])))
            (root / 'frames.csv').write_text(
                'frame,window,local_frame,warmup,cpu_frame_ms,gpu_sequence,sl_frame_id\n'
                '1,A,0,1,100,1,1\n2,A,1,0,20,2,2\n3,A,2,1,200,3,3\n4,B,0,0,15,4,4\n')
            (root / 'gpu.csv').write_text(
                'frame,domain,stage,gpu_ms\n1,2,pt-pass/world,100\n'
                '4,2,pt-pass/world,10\n2,2,pt-pass/world,12\n3,2,pt-pass/world,100\n')
            with contextlib.redirect_stdout(io.StringIO()):
                scene_replay.report(root)
            data = json.loads((root / 'analysis.json').read_text())['windows']
            self.assertEqual(data['A']['frameMs']['mean'], 20)
            self.assertEqual(data['A']['stages']['2:pt-pass/world']['mean'], 12)
            self.assertEqual(data['B']['stages']['2:pt-pass/world']['mean'], 10)
            self.assertEqual(data['A']['prepareMs'], 42)
            self.assertEqual(data['A']['processCpuPerFrameMs'], 4)
            self.assertEqual(data['A']['resourceEndpoints']['end']['private_bytes'], 120)
            self.assertNotIn('resourceEndpoints', data['B'])


if __name__ == '__main__':
    unittest.main()
