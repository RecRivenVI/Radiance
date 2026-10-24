import unittest

from loading_chain import base_gpu_window, phase_ranges, summarize
import datetime
from vanilla_performance import validate_sources
from test_chunk_interactions import InteractionTraceTest


class LoadingChainTest(unittest.TestCase):
    def test_interaction_camera_window_does_not_erase_initial_quiet(self):
        events=[dict(phase=p,frame=f) for p,f in [('open-world',1),('camera-fixed',2),
            ('quiet-confirmed',10),('interactions-start',20),('quiet-confirmed',30),('normal-stop',80)]]
        ranges,quiet=phase_ranges(events)
        self.assertEqual(10,quiet['frame'])
        self.assertEqual([('entry-and-teleport',1,2),('loading-and-settling',2,10),
                         ('queue-quiet',10,20),('interactions',20,80)],ranges)
    def test_functional_missing_is_explicit_but_performance_and_incompatibility_still_fail(self):
        source = dict(coverage={key:dict(compatible=True,evidence=[]) for key in ('PARTS','STATIC_STATE')})
        self.assertEqual(['BAKED'],validate_sources(source,allow_unobserved=True))
        with self.assertRaises(RuntimeError): validate_sources(source)
        source['coverage']['BAKED'] = dict(compatible=False,evidence=['radiance_probe'])
        with self.assertRaises(RuntimeError): validate_sources(source,allow_unobserved=True)

    def test_only_final_uninterrupted_quiet_window_is_steady(self):
        events=[dict(phase=p,frame=f) for p,f in [('open-world',1),('camera-fixed',2),
            ('quiet-confirmed',10),('quiet-interrupted',20),('quiet-confirmed',30),('normal-stop',40)]]
        ranges,quiet=phase_ranges(events)
        self.assertEqual(30,quiet['frame'])
        self.assertEqual(('queue-quiet',30,40),ranges[-1])
        events.pop(-2)
        ranges,quiet=phase_ranges(events)
        self.assertIsNone(quiet)
        self.assertEqual(2,len(ranges))

    def test_phase_keeps_long_frames_and_gpu_missing_is_not_zero(self):
        frames=[dict(frame=str(f),start_interval_ns=str(ns),wall_ns=str(ns),render_thread_cpu_ns='1000000')
                for f,ns in [(1,1000000),(2,9000000000),(3,1000000)]]
        timing=[dict(frame='2',domain='2',stage='pt',inclusive_ns='4000000',self_ns='4000000')]
        data=summarize(frames,timing,1,3)
        self.assertEqual(9000,data['intervalMs']['max'])
        self.assertEqual(1,data['stagesMs']['2/pt']['samples'])
        self.assertEqual(4,data['stagesMs']['2/pt']['mean'])
        self.assertEqual(2,data['frameCount'])

    def test_route_does_not_mix_initial_quiet_and_returned_quiet(self):
        events=[dict(phase=p,frame=f) for p,f in [('open-world',1),('camera-fixed',2),
            ('quiet-confirmed',10),('route-start',20),('route-finished',40),
            ('quiet-confirmed',50),('quiet-interrupted',60),('quiet-confirmed',70),('normal-stop',80)]]
        ranges,quiet=phase_ranges(events)
        self.assertEqual(70,quiet['frame'])
        self.assertEqual([('entry-and-teleport',1,2),('loading-and-settling',2,10),
            ('queue-quiet',10,20),('streaming-route',20,40),('route-settling',40,70),
            ('post-route-quiet',70,80)],ranges)

    def test_base_gpu_uses_only_complete_buckets_and_weights_completed_frames(self):
        start = datetime.datetime(2026,10,24,14,0,0).astimezone().timestamp()*1000
        log = '\n'.join([
            '[2410月2026 14:00:00.500] window-s=1 metric10-ms=999/999/20',
            '[2410月2026 14:00:02.000] window-s=1 metric10-ms=10/12/4',
            '[2410月2026 14:00:03.000] window-s=1 metric10-ms=20/21/2',
            '[2410月2026 14:00:04.100] window-s=1 metric10-ms=999/999/20'])
        result = base_gpu_window(log,start,start+4000)
        self.assertEqual(6,result['completedFrames'])
        self.assertAlmostEqual(80/6,result['meanMs'])
        self.assertEqual(21,result['maximumMs'])


if __name__=='__main__':
    unittest.main()
