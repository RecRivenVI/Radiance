import unittest

from chunk_interactions import analyze_window, parse_trace


class InteractionTraceTest(unittest.TestCase):
    def test_unchanged_empty_dependency_needs_a_later_completed_scene_not_a_fake_blas(self):
        trace=('TRACE_BASELINE\nclock_ns=1;dropped=0\nTRACE_WINDOW\nJAVA_BRACKET 1400 1420\n'
               'clock_ns=400;dropped=0\n250,frame-submit,-1,-1,10,0\n300,frame-complete,-1,-1,10,0\n')
        log=('CHUNK_UPDATE event=ACTION value=4 java_ns=1160\n'
             'CHUNK_UPDATE event=DIRTY value=6 action=4; id=5; java_ns=1170; origin=BlockPos{x=0, y=256, z=0}\n'
             'CHUNK_UPDATE event=DIRTY value=7 action=4; id=5; java_ns=1180; origin=BlockPos{x=0, y=256, z=0}\n'
             'CHUNK_UPDATE event=EMPTY_CONFIRMED value=7 action=4; id=5; java_ns=1200; origin=BlockPos{x=0, y=256, z=0}; removed=false\n')
        row=analyze_window([dict(operation=1,java_ns=1150)],trace,log)
        self.assertEqual([],row['incompleteOperations'])
        self.assertEqual(1,row['actions'][0]['dirtySections'])
        self.assertTrue(row['actions'][0]['chains'][0]['unchangedEmpty'])
        self.assertEqual({},row['actions'][0]['chains'][0]['nativeStages'])

    def test_bracket_calibrates_epochs_and_requires_gpu_completed_revision(self):
        trace = ('TRACE_BASELINE\nclock_ns=1;dropped=20\nTRACE_WINDOW\n'
                 'JAVA_BRACKET 1400 1420\nclock_ns=400;dropped=20\n'
                 '200,native-enqueue,5,7,0,0\n210,build-prepare,5,7,0,0\n'
                 '220,build-submit,5,7,0,0\n230,gpu-complete,5,7,0,0\n'
                 '240,publish,5,7,0,0\n250,tlas-record,5,7,10,0\n'
                 '300,frame-complete,-1,-1,10,0\n')
        log = ('CHUNK_UPDATE event=ACTION value=4 java_ns=1160\n'
               'CHUNK_UPDATE event=DIRTY value=7 action=4; id=5; java_ns=1180; '
               'origin=MutableBlockPos{x=0, y=256, z=0}\n')
        row = analyze_window([dict(operation=1,java_ns=1150)], trace, log)
        self.assertEqual([],row['incompleteOperations'])
        self.assertEqual([150/1e6,170/1e6],row['actions'][0]['completedSceneLatencyMs'])
        later_window = ('CHUNK_UPDATE event=ACTION value=5 java_ns=1500\n'
                        'CHUNK_UPDATE event=DIRTY value=8 action=5; id=5; java_ns=1520; '
                        'origin=MutableBlockPos{x=0, y=256, z=0}\n')
        self.assertEqual([],analyze_window([dict(operation=1,java_ns=1150)],trace,log+later_window)['incompleteOperations'])
        no_completion = trace.replace('300,frame-complete,-1,-1,10,0\n','')
        self.assertEqual([1],analyze_window([dict(operation=1,java_ns=1150)],no_completion,log)['incompleteOperations'])
        with self.assertRaises(RuntimeError):
            analyze_window([dict(operation=1,java_ns=1150)],trace.replace('230,gpu-complete,5,7,0,0\n',''),log)

    def test_only_pre_window_drops_are_allowed_and_no_clock_guessing(self):
        text = 'TRACE_BASELINE\nclock_ns=1;dropped=20\nTRACE_WINDOW\nJAVA_BRACKET 9 11\nclock_ns=5;dropped=20\n'
        self.assertEqual((4,6),parse_trace(text)[1])
        with self.assertRaises(RuntimeError): parse_trace(text.replace('clock_ns=5;dropped=20','clock_ns=5;dropped=21'))
        with self.assertRaises(RuntimeError): parse_trace(text.replace('JAVA_BRACKET 9 11\n',''))


if __name__ == '__main__':
    unittest.main()
