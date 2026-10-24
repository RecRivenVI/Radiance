"""Contract tests for actual evidence joins, independent of any success marker."""
import unittest
import io
import zipfile
import csv
import json
import tempfile
from pathlib import Path
from unittest.mock import patch
import takeover_experiment as experiment
from takeover_snapshot import is_source_input


class CompletionTests(unittest.TestCase):
    def test_storage_includes_independent_radiance_scratch_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            scratch = Path(directory)/'RadianceFeasibility'
            scratch.mkdir()
            (scratch/'evidence.txt').write_bytes(b'proof')
            def walk(path, **kwargs):
                return [(str(scratch), [], ['evidence.txt'])] if Path(path)==scratch else []
            with patch.object(Path, 'glob', lambda path, pattern: iter([scratch]) if pattern=='Radiance*' else iter([])), \
                    patch.object(experiment.os, 'walk', side_effect=walk):
                self.assertEqual(5, experiment.storage_preflight()['external'])

    def test_source_checkpoints_exclude_generated_evidence_and_local_settings(self):
        for name in ('validations/conformance-takeover/result/source-final.zip',
                     'validations/conformance-takeover/instance/client/options.txt',
                     'components/native_renderer/build/core.dll',
                     'versions/1.21.1-neoforge/build/libs/radiance.jar',
                     'instances/1.21.1-neoforge/client/options.txt', 'local.toml'):
            self.assertFalse(is_source_input(name),name)
        for name in ('validations/conformance-takeover/validation.toml',
                     'validations/conformance-takeover/task/expected.json',
                     'components/native_renderer/src/core/render/chunks.cpp',
                     'documents/project/plan-render_takeover.md'):
            self.assertTrue(is_source_input(name),name)

    def test_profile_scene_comes_from_metadata_not_timestamp_directory(self):
        output=io.BytesIO()
        with zipfile.ZipFile(output,'w') as archive:
            for i in range(4):
                parent=f'radiance-audit/profiles/2026-10-19T00-00-{i:02}-pid1'
                archive.writestr(parent+'/STATUS.txt','valid-cpu-sample=true\n')
                archive.writestr(parent+'/METADATA.txt',f'scene=takeover-{i}-sample\n')
                archive.writestr(parent+'/frames.csv','start_interval_ns,render_thread_cpu_ns\n'+'1000000,500000\n'*101)
                archive.writestr(parent+'/summary.csv','domain,stage\n')
        with zipfile.ZipFile(io.BytesIO(output.getvalue())) as archive:
            result=experiment.profile_summary(archive)
        self.assertEqual(len(result),4)
        self.assertEqual(result['takeover-0-sample']['samples'],101)
        self.assertEqual(result['takeover-0-sample']['mean'],1)

    def test_recording_alone_is_not_gpu_execution(self):
        with self.assertRaises(RuntimeError):
            experiment.completed_chains('1,publish,7,2,0,3\n2,tlas-record,7,2,9,3\n3,frame-complete,-1,-1,9,0\n')

    def test_reused_numeric_version_cannot_reuse_an_old_completion(self):
        trace=('1,gpu-complete,7,0,0,3\n2,publish,7,0,0,3\n3,tlas-record,7,0,9,3\n'
               '4,frame-complete,-1,-1,9,0\n5,publish,7,0,0,3\n6,tlas-record,7,0,10,3\n'
               '7,frame-complete,-1,-1,10,0\n')
        self.assertEqual(experiment.completed_chains(trace),[dict(slot=7,revision=0,generation=3,frame=9)])

    def test_completion_must_match_version_owner_and_follow_scene_use(self):
        prefix='1,gpu-complete,7,2,0,3\n2,publish,7,2,0,3\n'
        valid=prefix+'3,tlas-record,7,2,9,3\n4,frame-complete,-1,-1,9,0\n'
        self.assertEqual(experiment.completed_chains(valid),[dict(slot=7,revision=2,generation=3,frame=9)])
        for invalid in (valid.replace('1,gpu-complete,7,2','1,gpu-complete,7,1'),
                        valid.replace('1,gpu-complete,7,2,0,3','1,gpu-complete,7,2,0,4'),
                        prefix+'3,frame-complete,-1,-1,9,0\n4,tlas-record,7,2,9,3\n'):
            with self.assertRaises(RuntimeError): experiment.completed_chains(invalid)

    def test_overflow_cannot_be_certified(self):
        with self.assertRaises(RuntimeError): experiment.completed_chains('clock_ns=2;dropped=1\n')


class CityUpgradeIntegrityTest(unittest.TestCase):
    def test_modern_palette_counts_and_unsigned_packed_words(self):
        import upgrade_city_fixture as u
        self.assertEqual(4096, u.section_nonair({'block_states':{'palette':[{'Name':'minecraft:stone'}]}}))
        self.assertEqual(0, u.section_nonair({'block_states':{'palette':[{'Name':'minecraft:cave_air'}]}}))
        self.assertEqual(4096, u.section_nonair({'block_states':{
            'palette':[{'Name':'minecraft:air'}, {'Name':'minecraft:stone'}],
            'data':[0x1111111111111111]*256}}))
        with self.assertRaises(ValueError):
            u.section_nonair({'block_states':{'palette':[{'Name':'minecraft:air'}, {'Name':'minecraft:stone'}],
                                             'data':[-1]*256}})

    def test_upgrade_rejects_missing_chunks_and_mass_block_loss(self):
        import upgrade_city_fixture as u
        before=dict(perChunkNonair={'0,0':100}, levelDataVersion=1343)
        after=dict(perChunkNonair={'0,0':100}, levelDataVersion=3955, chunkDataVersions={3955:1})
        u.verify_upgrade(before, after)
        with self.assertRaises(ValueError):
            u.verify_upgrade(before, dict(after, perChunkNonair={}))
        with self.assertRaises(ValueError):
            u.verify_upgrade(before, dict(after, perChunkNonair={'0,0':50}))


class RouteCensusTest(unittest.TestCase):
    def row(self, **values):
        result={k:'0' for k in ('renderer_calls','direct_source_draws','persistent_draws',
                'geometries','direct_source_vertices','vertices','persistent_vertices','generated_bytes')}
        result.update(producer='entity/example',frames='4',capture_policies='surfaceRole=ORDINARY')
        result.update({k:str(v) for k,v in values.items()})
        return result

    def test_unattributed_output_is_not_a_fake_renderer_or_missing_vertices(self):
        from takeover_city import route_summary
        rows=[self.row(renderer_calls=4,direct_source_draws=8,persistent_draws=10,
                      geometries=4,direct_source_vertices=192,persistent_vertices=240,vertices=96),
              self.row(producer='unattributed/dynamic',geometries=2,vertices=48),
              self.row(producer='unattributed/empty')]
        result=route_summary(rows)
        self.assertEqual(result['sourceCounts'],dict(directOnly=0,captureOnly=0,mixed=1,noGeometry=0))
        self.assertEqual(result['unattributedOutputPools'],1)
        self.assertEqual(result['perFrame']['capturedGeneratedVertices'],36)
        self.assertEqual(result['perFrame']['legacyPersistentDraws'],.5)

    def test_frame_correction_needs_independent_renderer_multiples(self):
        from takeover_city import route_summary
        rows=[self.row(renderer_calls=5), self.row(producer='blockentity/example',renderer_calls=10)]
        self.assertEqual(route_summary(rows,1)['frames'],5)
        with self.assertRaises(RuntimeError): route_summary([self.row(renderer_calls=8)],1)
        with self.assertRaises(RuntimeError): route_summary([self.row(direct_source_draws=3,persistent_draws=2)])


class CityCounterWindowTest(unittest.TestCase):
    def test_sparse_events_use_all_completed_frames_and_reject_unmatched_tokens(self):
        import takeover_city as city
        rows = []
        for frame in (10, 11):
            rows.append(dict(frame=str(frame), start_interval_ns='30000000000',
                width='1920', height='1080', render_distance='32', failed='false',
                world='true', sdk_generated_fps='0', focused='false', screen='ChatScreen',
                dimension='minecraft:overworld', camera_x='1', camera_y='2', camera_z='3',
                camera_yaw='4', camera_pitch='5', day_time='6000', rain='0', thunder='0'))
        csv_data = io.StringIO()
        writer = csv.DictWriter(csv_data, fieldnames=list(rows[0]))
        writer.writeheader(); writer.writerows(rows)
        payload = io.BytesIO()
        with zipfile.ZipFile(payload, 'w') as z:
            base = 'radiance-audit/profiles/example/'
            z.writestr(base+'frames.csv', csv_data.getvalue())
            z.writestr(base+'METADATA.txt', 'scene=stage3-example\n')
            keys = ('dropped-writer-batches', 'dropped-native-events',
                    'output-cap-dropped-batches', 'failed-frames', 'java-partition-errors')
            z.writestr(base+'STATUS.txt', ''.join(k+'=0\n' for k in keys))
            z.writestr(base+'summary.csv', 'domain,stage\n')
            z.writestr(base+'counters.csv', 'stage,frame,count,bytes\n'
                'build,10,3,30\nbuild,99,8,80\ngauge,10,7,0\ngauge,11,9,0\n')
        with zipfile.ZipFile(io.BytesIO(payload.getvalue())) as archive, \
                patch.object(city.zipfile, 'ZipFile', return_value=archive), \
                patch.object(Path, 'read_text', return_value=json.dumps({'stage3-example':{}})), \
                patch.object(city, 'analyze_routes', return_value={}), \
                patch.object(city, 'write') as output:
            city.analyze_case(Path('case'), Path('evidence'), 1920, 1080)
        window = output.call_args.args[1]['windows']['stage3-example']
        build = window['counters']['build']
        self.assertEqual(build['windowFrames'], 2)
        self.assertEqual(build['recordedTotalCount'], 3)
        self.assertEqual(build['eventMeanCountPerCompletedFrame'], 1.5)
        self.assertEqual(build['eventMeanBytesPerCompletedFrame'], 15)
        self.assertEqual(build['meanCount'], 3)
        self.assertEqual(window['unmatchedCounterRows'], {'build':1})
        self.assertEqual(window['counters']['gauge']['meanCount'], 8)


if __name__=='__main__': unittest.main()
