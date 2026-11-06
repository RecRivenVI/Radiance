import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import unittest
from unittest.mock import patch

import memory_diagnostics
import memory_guard
from memory_guard import GIB, Pressure, check_global_stop, guarded_process


def memory(commit, physical=70):
    return dict(availableCommit=commit * GIB, availablePhysical=physical * GIB)


class MemoryGuardTest(unittest.TestCase):
    def test_pressure_distinguishes_independent_floors_and_sustained_loss(self):
        self.assertEqual(Pressure().observe(0, memory(24)), 'availableCommit absolute floor')
        self.assertEqual(Pressure().observe(0, memory(80, 20)), 'availablePhysical absolute floor')
        pressure = Pressure()
        for i, value in enumerate((55, 42, 43, 42, 40, 44, 41, 41)):
            self.assertIsNone(pressure.observe(i / 4, memory(value)))
        pressure = Pressure()
        for i, value in enumerate((48, 46, 44, 42)):
            self.assertIsNone(pressure.observe(i / 4, memory(value)))
        self.assertEqual(pressure.observe(1, memory(40)), 'availableCommit sustained decline')

    def test_blocked_gpu_collector_does_not_delay_owned_stop(self):
        release_gpu = threading.Event()
        spawned = threading.Event()
        block = threading.Thread(target=release_gpu.wait, daemon=True)
        block.start()
        unrelated = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(30)'])

        def read():
            return memory(20 if spawned.is_set() else 74)

        try:
            with tempfile.TemporaryDirectory() as folder:
                case = Path(folder)
                with self.assertRaisesRegex(RuntimeError, 'absolute floor'):
                    with guarded_process(case, [sys.executable, '-c', 'import time; time.sleep(30)'], read=read) as child:
                        spawned.set()
                        child.wait(timeout=4)
                self.assertTrue(block.is_alive())
                self.assertIsNone(unrelated.poll())
                self.assertEqual(json.loads((case / 'SYSTEM_MEMORY_STOP.json').read_text())['pid'], child.pid)
        finally:
            release_gpu.set()
            block.join(timeout=1)
            unrelated.kill()
            unrelated.wait(timeout=2)

    def test_monitor_failure_after_launch_is_fail_closed(self):
        launched = threading.Event()
        resume_read = threading.Event()

        def read():
            if launched.is_set():
                resume_read.wait(8)
            return memory(74)

        try:
            with tempfile.TemporaryDirectory() as folder:
                case = Path(folder)
                with self.assertRaisesRegex(RuntimeError, 'heartbeat expired'):
                    with guarded_process(case, [sys.executable, '-c', 'import time; time.sleep(30)'], read=read) as child:
                        launched.set()
                        self.assertNotEqual(child.wait(timeout=4), 0)
                self.assertTrue((case / 'SYSTEM_MEMORY_STOP.json').is_file())
        finally:
            resume_read.set()

    def test_preflight_rejects_unsafe_launch_and_normal_child_exit_has_no_stop(self):
        with tempfile.TemporaryDirectory() as folder:
            case = Path(folder)
            with self.assertRaisesRegex(RuntimeError, 'availablePhysical absolute floor'):
                with guarded_process(case, ['this-process-must-never-start'], read=lambda: memory(74, 12)):
                    self.fail('unsafe launch')
            self.assertIsNone(json.loads((case / 'SYSTEM_MEMORY_STOP.json').read_text())['pid'])
            normal = case / 'normal'
            normal.mkdir()
            with guarded_process(normal, [sys.executable, '-c', 'pass'], read=lambda: memory(74)) as child:
                self.assertEqual(child.wait(timeout=2), 0)
            self.assertFalse((normal / 'SYSTEM_MEMORY_STOP.json').exists())

    def test_new_output_still_observes_sibling_stops(self):
        with tempfile.TemporaryDirectory() as folder, patch.dict(os.environ, RADIANCE_MEMORY_RECOVERY=''):
            root = Path(folder)
            result = root / 'validations/performance-scene_replay/result'
            new = result / 'new'
            old = result / 'failed'
            old.mkdir(parents=True)
            (old / 'STOP.json').write_text('{}')
            self.assertEqual(check_global_stop(root, new), set())
            (old / 'SYSTEM_MEMORY_STOP.json').write_text('{}')
            with self.assertRaisesRegex(RuntimeError, 'Global stop retained'):
                check_global_stop(root, new)

    def test_conformance_stop_blocks_replay(self):
        with tempfile.TemporaryDirectory() as folder, patch.dict(os.environ, RADIANCE_MEMORY_RECOVERY=''):
            root = Path(folder)
            failed = root / 'validations/conformance-renderer/result/replacement'
            failed.mkdir(parents=True)
            (failed / 'SYSTEM_MEMORY_STOP.json').write_text('{}')
            replay = root / 'validations/performance-scene_replay/result/next'
            with self.assertRaisesRegex(RuntimeError, 'Global stop retained'):
                check_global_stop(root, replay)

    def test_recovery_preserves_exact_markers_and_does_not_acknowledge_a_new_stop(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            campaign = root / 'validations/performance-scene_replay/result'
            retained = campaign / memory_guard.RECOVERED_EVENT / 'SYSTEM_MEMORY_STOP.json'
            retained.parent.mkdir(parents=True)
            retained.write_bytes(b'{"old":true}')
            original = hashlib.sha256(retained.read_bytes()).hexdigest()
            markers = {retained.relative_to(campaign).as_posix(): original}
            recovery = root / 'recovery.json'
            recovery.write_text(json.dumps(dict(event=memory_guard.RECOVERED_EVENT, markers=markers)))
            with patch.dict(memory_guard.RECOVERIES, {memory_guard.RECOVERED_EVENT: markers}), patch.dict(
                    os.environ, RADIANCE_MEMORY_RECOVERY=str(recovery)):
                self.assertEqual(check_global_stop(root, campaign / 'new'), {retained.resolve()})
                (retained.parent / 'DEVICE_LOST.json').write_text('{}')
                with self.assertRaisesRegex(RuntimeError, 'Global stop retained'):
                    check_global_stop(root, campaign / 'new')
                retained.write_bytes(b'{"old":false}')
                with self.assertRaisesRegex(RuntimeError, 'differs from the recovery registration'):
                    check_global_stop(root, campaign / 'new')

    def test_diagnostic_profiles_are_exclusive(self):
        env = {'MCVR_SHADER_SOURCE_DEBUG': '1'}
        self.assertFalse(memory_diagnostics.diagnostic_environment(env, 'basic'))
        self.assertEqual(env['MCVR_GPU_SHADER_EXPORT'], '0')
        self.assertTrue(memory_diagnostics.diagnostic_environment(env, 'resource'))
        self.assertEqual((env['MCVR_AFTERMATH_RESOURCE_ONLY'], env['MCVR_SHADER_SOURCE_DEBUG']), ('1', '0'))
        self.assertTrue(memory_diagnostics.diagnostic_environment(env, 'source'))
        self.assertEqual((env['MCVR_AFTERMATH_RESOURCE_ONLY'], env['MCVR_SHADER_SOURCE_DEBUG']), ('0', '1'))
