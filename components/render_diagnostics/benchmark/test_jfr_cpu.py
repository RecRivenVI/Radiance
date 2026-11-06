import unittest
from jfr_cpu import summarize, duration_seconds, attribute_frames


class JfrCpuTest(unittest.TestCase):
    def test_frame_clock_join_keeps_other_threads_and_nested_events_separate(self):
        thread = dict(javaThreadId=7, osThreadId=17)
        def event(kind, start, **values):
            return dict(type=kind, values=dict(startTime='2026-11-04T00:00:00.'+start+'Z', **values))
        events = [event('radiance.RenderFrame', '000', duration='PT0.2S', measuredWallNs='PT0.2S', frameToken=31, eventThread=thread),
                  event('jdk.ExecutionSample', '050', sampledThread=thread, stackTrace=dict(frames=[])),
                  event('jdk.ExecutionSample', '060', sampledThread=dict(javaThreadId=8, osThreadId=18)),
                  event('jdk.ExecutionSample', '200', sampledThread=thread),
                  event('jdk.ClassLoad', '190', duration='PT0.05S', eventThread=thread, loadedClass=dict(name='Renderer')),
                  event('jdk.GCPhasePause', '010', duration='PT0.01S'),
                  event('jdk.SafepointBegin', '010', duration='PT0.01S')]
        rows = attribute_frames(events)
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]['frame'], 31)
        self.assertEqual(rows[0]['renderSamples'], 1)
        self.assertEqual(len(rows[0]['overlappingEvents']), 3)
        loaded = next(e for e in rows[0]['overlappingEvents'] if e['event'] == 'jdk.ClassLoad')
        self.assertAlmostEqual(loaded['overlapMs'], 10, places=2)
        self.assertEqual(loaded['className'], 'Renderer')

    def test_parallel_compilations_do_not_hide_stop_the_world_events(self):
        start = '2026-11-04T00:00:00Z'
        events = [dict(type='radiance.RenderFrame', values=dict(
            startTime=start, duration=.2, measuredWallNs=200000000, frameToken=9,
            eventThread=dict(javaThreadId=7, osThreadId=17)))]
        events += [dict(type='jdk.Compilation', values=dict(
            startTime=start, duration=.15, eventThread=dict(javaThreadId=8, osThreadId=18)))
                   for _ in range(30)]
        events.append(dict(type='jdk.GCPhasePause', values=dict(startTime=start, duration=.01)))
        row = attribute_frames(events)[0]
        self.assertEqual(len(row['overlappingCompilations']), 8)
        self.assertEqual([e['event'] for e in row['overlappingEvents']], ['jdk.GCPhasePause'])

    def test_native_entity_build_is_not_misreported_as_java_entity_rendering(self):
        def frame(owner, method):
            return dict(method=dict(type=dict(name=owner), name=method))
        sample = dict(type='jdk.NativeMethodSample', values=dict(
            sampledThread=dict(javaName='Render thread'), stackTrace=dict(frames=[
                frame('com/radiance/client/proxy/world/EntityProxy', 'build'),
                frame('com/radiance/client/proxy/world/EntityProxy', 'queueEntitiesBuild$mixinextras$wrapped')])) )
        result = summarize([sample])
        self.assertEqual(result['renderSampleGroups'], {'native-entity-build': 1})
        self.assertEqual(result['entityRenderLeaves'], [])

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
