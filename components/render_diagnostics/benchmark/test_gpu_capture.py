import hashlib
from pathlib import Path
import tempfile
import unittest
import zipfile
from gpu_capture import prepare_sdk, REQUIRED


class CaptureInputTest(unittest.TestCase):
    def test_sdk_hash_layout_and_cache_conflict(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            archive = folder/'sdk.zip'
            with zipfile.ZipFile(archive, 'w') as sdk:
                for name in REQUIRED:
                    sdk.writestr(name, b'fixture-not-a-real-SDK')
            digest = hashlib.sha256(archive.read_bytes()).hexdigest()
            with self.assertRaisesRegex(ValueError, 'SHA-256'):
                prepare_sdk(archive, ['0'*64], folder/'cache')
            sdk = prepare_sdk(archive, [digest], folder/'cache')
            self.assertEqual(sdk, prepare_sdk(archive, [digest], folder/'cache'))
            (sdk/REQUIRED[-1]).write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError, 'changed'):
                prepare_sdk(archive, [digest], folder/'cache')

    def test_sdk_cannot_extract_outside_cache(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            archive = folder/'sdk.zip'
            with zipfile.ZipFile(archive, 'w') as sdk:
                for name in REQUIRED:
                    sdk.writestr(name, b'fixture')
                sdk.writestr('../escaped', b'escape')
            digest = hashlib.sha256(archive.read_bytes()).hexdigest()
            with self.assertRaises(ValueError):
                prepare_sdk(archive, [digest], folder/'cache')
            self.assertFalse((folder/'escaped').exists())


if __name__ == '__main__':
    unittest.main()
