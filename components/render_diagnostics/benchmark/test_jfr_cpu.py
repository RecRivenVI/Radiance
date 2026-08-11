import unittest
from jfr_cpu import summarize, duration_seconds


class JfrCpuTest(unittest.TestCase):
    def test_java21_iso_duration_export(self):
        self.assertEqual(duration_seconds('PT0.002S'), .002)
        self.assertEqual(duration_seconds('PT1M2.5S'), 62.5)
        with self.assertRaises(ValueError):
            duration_seconds('unknown')

    def test_first_cpu_load_interval_has_an_unknown_pre_recording_boundary(self):
        def sample(load):
            return dict(type='jdk.ThreadCPULoad', values=dict(eventThread=dict(javaName='Render thread', javaThreadId=1, osThreadId=7), user=load, system=0))
        result = summarize([sample(.001), sample(.03)])
        self.assertEqual(result['threadCpuLoad']['Render thread']['samples'], 1)
        self.assertEqual(result['threadCpuLoad']['Render thread']['meanReportedLoad'], .03)
        self.assertEqual(len(result['threadCpuLoadBoundarySamples']), 1)

    def test_recursive_methods_are_not_counted_twice_and_threads_remain_separate(self):
        frame = dict(method=dict(type=dict(name='a/b'), name='render'))
        def sample(name):
            return dict(type='jdk.ExecutionSample', values=dict(sampledThread=dict(javaName=name), stackTrace=dict(frames=[frame, frame])))
        result = summarize([sample('Render thread'), sample('worker')])
        self.assertEqual(result['sampledThreads']['Render thread']['samples'], 1)
        self.assertEqual(result['sampledThreads']['Render thread']['inclusiveMethods'], [('a.b.render', 1)])
        self.assertEqual(result['sampledThreads']['worker']['samples'], 1)

    def test_allocation_weights_and_gc_durations_are_not_execution_samples(self):
        result = summarize([dict(type='jdk.ObjectAllocationSample', values=dict(objectClass=dict(name='old-boundary'), weight=9999999999)), dict(type='jdk.ObjectAllocationSample', values=dict(objectClass=dict(name='byte[]'), weight=1234)), dict(type='jdk.GCPhasePause', values=dict(duration=.002))])
        self.assertEqual(result['sampledThreads'], {})
        self.assertEqual(result['allocationSamplesByClassAndLeaf'][0]['sampleWeightBytes'], 1234)
        self.assertEqual(len(result['allocationBoundarySamples']), 1)
        self.assertEqual(result['gcPauseMs']['maximum'], 2)


if __name__ == '__main__':
    unittest.main()
