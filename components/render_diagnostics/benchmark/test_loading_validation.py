import io
import unittest
import zipfile
from prepare_loading_validation import members, muted_options
from analyze_loading_validation import measurement_coverage, shutdown_evidence


class LoadingFixtureTest(unittest.TestCase):
    def test_complete_frame_intervals_and_short_unfinished_tail(self):
        events = [{'frame': 0, 'elapsed_ns': 0}, {'frame': 2, 'elapsed_ns': 1_000_000_000},
                  {'frame': 3, 'elapsed_ns': 1_100_000_000}]
        frames = [{'frame': str(i), 'start_interval_ns': '500000000'} for i in (1, 2)]
        result = measurement_coverage(events, frames)
        self.assertTrue(result['complete_for_elapsed_window'])
        self.assertEqual(result['large_unmeasured_gaps'], [])

    def test_normal_close_does_not_fill_a_missing_long_frame(self):
        events = [{'frame': 0, 'elapsed_ns': 0}, {'frame': 2, 'elapsed_ns': 1_000_000_000},
                  {'frame': 3, 'elapsed_ns': 99_000_000_000}]
        frames = [{'frame': str(i), 'start_interval_ns': '500000000'} for i in (1, 2)]
        result = measurement_coverage(events, frames)
        self.assertFalse(result['complete_for_elapsed_window'])
        self.assertEqual(result['large_unmeasured_gaps'][0]['unmeasured_ns'], 98_000_000_000)
        self.assertEqual(result['large_unmeasured_gaps'][0]['missing_frames'], 1)
        self.assertTrue(shutdown_evidence([{'phase': 'normal-stop'}],
            'Stopping!\nAll dimensions are saved\nScene released;', [])['completed_run'])

    def test_a_recorded_long_frame_remains_in_the_measurement(self):
        events = [{'frame': 0, 'elapsed_ns': 0}, {'frame': 1, 'elapsed_ns': 98_000_000_000}]
        result = measurement_coverage(events, [{'frame': '1', 'start_interval_ns': '98000000000'}])
        self.assertTrue(result['complete_for_elapsed_window'])
        self.assertEqual(result['recorded_interval_ns'], 98_000_000_000)

    def test_startup_release_cannot_certify_failed_shutdown(self):
        result = shutdown_evidence([], 'Scene released; startup\nVK_ERROR_DEVICE_LOST\nAll dimensions are saved', [{}])
        self.assertTrue(result['all_dimensions_saved'])
        self.assertFalse(result['normal_scene_release_logged'])
        self.assertFalse(result['completed_run'])

    def test_normal_shutdown_requires_release_after_stop(self):
        events = [{'phase': 'normal-stop'}]
        self.assertTrue(shutdown_evidence(events, 'Stopping!\nAll dimensions are saved\nScene released;', [])['completed_run'])
        self.assertFalse(shutdown_evidence(events, 'Scene released;\nStopping!\nAll dimensions are saved', [])['completed_run'])

    def test_native_stdout_missing_from_java_log_uses_completed_close_trace(self):
        trace = ('BEGIN CPU_ONLY cause=normal-device-close result=0 retained=1 dropped=0\n'
                 '1 2 observedFrame=3 normal-device-close result=0 value0=0\nEND\n')
        logs = 'Stopping!\nAll dimensions are saved'
        result = shutdown_evidence([{'phase': 'normal-stop'}], logs, [], trace)
        self.assertFalse(result['normal_scene_release_logged'])
        self.assertTrue(result['normal_native_close_traced'])
        self.assertTrue(result['completed_run'])
        self.assertFalse(shutdown_evidence([], logs, [], trace)['completed_run'])
        self.assertFalse(shutdown_evidence([{'phase': 'normal-stop'}], logs, [{}], trace)['completed_run'])
        self.assertFalse(shutdown_evidence([{'phase': 'normal-stop'}], logs + '\nVK_ERROR_DEVICE_LOST', [], trace)['completed_run'])

    def test_failed_or_incomplete_native_trace_is_not_normal_close(self):
        prefix = 'BEGIN CPU_ONLY cause=normal-device-close result=0 retained=1 dropped=0\n'
        event = '1 2 observedFrame=3 normal-device-close result=0 value0=0\n'
        for trace in ('', prefix, prefix + 'END\n', prefix + event,
                      prefix + event.replace('result=0', 'result=-4') + 'END\n',
                      (prefix + event + 'END\n').replace('cause=normal-device-close', 'cause=device-lost-close')):
            with self.subTest(trace=trace):
                self.assertFalse(shutdown_evidence([{'phase': 'normal-stop'}],
                    'Stopping!\nAll dimensions are saved', [], trace)['completed_run'])

    def test_unrelated_graphics_unchanged(self):
        result = muted_options('renderDistance:16\nsoundCategory_master:1.0\npauseOnLostFocus:true\n')
        self.assertIn('renderDistance:16\n', result)
        self.assertEqual(result.count('soundCategory_master:'), 1)
        self.assertIn('soundCategory_master:0.0\n', result)
        self.assertIn('pauseOnLostFocus:false\n', result)
        self.assertEqual(muted_options(result), result)

    def test_distance_comparison_changes_only_render_distance(self):
        source = ('renderDistance:16\nsimulationDistance:32\nguiScale:2\n'
                  'soundCategory_master:1.0\npauseOnLostFocus:true\n')
        a, b = (muted_options(source, distance) for distance in (16, 32))
        self.assertIn('renderDistance:16\n', a)
        self.assertIn('renderDistance:32\n', b)
        self.assertEqual(a.replace('renderDistance:16\n', ''), b.replace('renderDistance:32\n', ''))
        self.assertEqual(b.count('renderDistance:'), 1)
        self.assertEqual(muted_options(b, 32), b)

    def test_reject_invalid_render_distance(self):
        for distance in (1, 33, 16.5, '32', True):
            with self.subTest(distance=distance), self.assertRaises(ValueError):
                muted_options('renderDistance:16\n', distance)

    def test_reject_escape_and_symlink(self):
        for name in ('../escape', '/absolute', 'saves/../../escape', 'C:/drive', 'a\\b', 'link'):
            with self.subTest(name=name), zipfile.ZipFile(io.BytesIO(), 'w') as archive:
                entry = zipfile.ZipInfo(name)
                entry.filename = name
                if name == 'link':
                    entry.external_attr = 0o120777 << 16
                archive.writestr(entry, 'content')
                with self.assertRaises(ValueError):
                    members(archive)

    def test_world_path_preserved(self):
        with zipfile.ZipFile(io.BytesIO(), 'w') as archive:
            archive.writestr('saves/Newport City/region/r.0.0.mca', 'region')
            self.assertEqual(members(archive)[0].filename, 'saves/Newport City/region/r.0.0.mca')


if __name__ == '__main__':
    unittest.main()
