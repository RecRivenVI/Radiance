from pathlib import Path
import importlib.util
import json
import unittest
from unittest.mock import patch
import uuid
import hashlib
import nbtlib
import profiles
import stress_scene
import compare

s=importlib.util.spec_from_file_location('fixture',Path(__file__).with_name('fixture.py'))
fixture=importlib.util.module_from_spec(s);s.loader.exec_module(fixture)

class FixtureTest(unittest.TestCase):
    def test_bounded_tiers_retain_vanilla_recipe_and_require_modded_version(self):
        a,ca=fixture.commands(False,1);b,cb=fixture.commands(False,4)
        self.assertEqual(4*ca['chests'],cb['chests'])
        self.assertFalse(any('create:' in line or 'sable ' in line for line in b))
        self.assertRaises(ValueError,fixture.commands,False,100)

    def test_real_world_nbt_and_datapack_roundtrip_without_overwrite(self):
        root=Path(__file__).resolve().parents[1]/'build/benchmark-fixture-tests'
        root.mkdir(parents=True,exist_ok=True)
        with patch.object(fixture,'REPOSITORY_RUN',root):
            for version in fixture.VERSIONS:
                target=root/str(uuid.uuid4())
                result=fixture.create(target,version)
                data=nbtlib.load(target/'saves/Audit Benchmark/level.dat')['Data']
                self.assertEqual(fixture.VERSIONS[version][0],int(data['DataVersion']))
                self.assertIn('file/audit-fixture',data['DataPacks']['Enabled'])
                pack=json.loads((target/'saves/Audit Benchmark/datapacks/audit-fixture/pack.mcmeta').read_text())
                self.assertEqual(fixture.VERSIONS[version][1],pack['pack']['pack_format'])
                before=(target/'FIXTURE.json').read_bytes()
                self.assertRaises(FileExistsError,fixture.create,target,version)
                self.assertEqual(before,(target/'FIXTURE.json').read_bytes())
                self.assertFalse(result['runtime_verified'])
                self.assertIn('soundCategory_master:0.0', (target/'options.txt').read_text().splitlines())

    def test_output_boundary_is_repository_run_not_any_folder_named_run(self):
        self.assertRaises(ValueError,fixture.create,Path('E:/unrelated/run/benchmark'),'1.21.1')
        self.assertRaises(ValueError,fixture.create,fixture.REPOSITORY_RUN/'unsupported','1.21.4',True)

    def test_stress_recipe_counts_and_fill_budget(self):
        for scene in stress_scene.SCENES:
            commands,counts,camera=stress_scene.recipe(scene,1)
            self.assertLess(len(commands),20000)
            self.assertEqual(5,len(camera))
            for command in commands:
                if command.startswith('fill '):
                    coords=list(map(int,command.split()[1:7]))
                    volume=1
                    for i in range(3):volume*=abs(coords[i]-coords[i+3])+1
                    self.assertLessEqual(volume,32768)
            if scene!='terrain-city':
                for key in ('chests','banners','armor_stands','item_frames'):self.assertEqual(256,counts[key])
                self.assertEqual(256,sum('Facing:3b' in c for c in commands))
            if scene=='factory':
                self.assertEqual((64,256,16),(counts['create_motors'],counts['create_shafts'],counts['sable_assemblies']))
            else:self.assertFalse(any('create:' in c or c.startswith('sable ') for c in commands))
        self.assertRaises(ValueError,stress_scene.recipe,'model-city',100)

    def test_matched_targets_preserve_common_options_and_resolve_real_module_names(self):
        all_profiles=[profiles.settings(target) for target in profiles.TARGETS]
        for profile in all_profiles:
            self.assertEqual(all_profiles[0]['vanilla'],profile['vanilla'])
            self.assertEqual(all_profiles[0]['options'],profile['options'])
            self.assertEqual('false',profile['options']['vsync'])
            self.assertEqual('false',profile['options']['dlssFrameGeneration'])
            self.assertEqual('0',profile['options']['reflexMode'])
            self.assertEqual('0.0',profile['vanilla']['soundCategory_master'])
        self.assertEqual('render_pipeline.preset.rt_dlss_rr',all_profiles[0]['expectedPreset'])
        self.assertEqual('render_pipeline.preset.rt_dlss',all_profiles[1]['expectedPreset'])

    def test_stress_world_recipe_determinism_and_wrong_loader_rejection(self):
        self.assertEqual(stress_scene.recipe('model-city'),stress_scene.recipe('model-city'))
        self.assertRaises(ValueError,fixture.create,fixture.REPOSITORY_RUN/'invalid-factory','1.21.4',True,1,'factory')
        self.assertRaises(ValueError,fixture.create,fixture.REPOSITORY_RUN/'invalid-scene','1.21.1',False,1,'factory')

    def test_shader_controls_use_actual_module_not_global_bounce_option(self):
        def config(module,values):
            return {'attributes':[{'name':f'render_pipeline.module.{module}.attribute.{k}',
                                   'type':'int_range:1-256','default_value':v} for k,v in values.items()]}
        ref=config('ray_tracing',dict(num_ray_bounces='4',initial_samples='32',spatial_reuse_samples='4',
                                     spatial_reuse_radius='32',temporal_confidence_cap='24'))
        target=config('main_render',dict(num_ray_bounces='3',initial_samples='8',spatial_reuse_samples='2',
                                        spatial_reuse_radius='24',temporal_confidence_cap='1'))
        result=profiles.apply_shader_controls(profiles.settings('upstream-neo'),target,ref)
        self.assertEqual('4',result['options']['rayBounces'])
        attrs=result['pipeline']['presetModules'][1]['attributes']
        observed={a['name'].split('.attribute.')[1]:a['value'] for a in attrs}
        self.assertEqual(['4','32','4'],[observed[k] for k in ('num_ray_bounces','initial_samples','spatial_reuse_samples')])
        target['attributes'].pop(0)
        self.assertRaises(ValueError,profiles.apply_shader_controls,profiles.settings('upstream-neo'),target,ref)

    def test_shader_controls_reject_unsupported_range_instead_of_clamping(self):
        keys=('num_ray_bounces','initial_samples','spatial_reuse_samples','spatial_reuse_radius','temporal_confidence_cap')
        ref={'attributes':[dict(name='render_pipeline.module.ray_tracing.attribute.'+k,type='int_range:1-256',default_value='32') for k in keys]}
        target={'attributes':[dict(name='render_pipeline.module.main_render.attribute.'+k,type='int_range:1-16',default_value='8') for k in keys]}
        self.assertRaises(ValueError,profiles.apply_shader_controls,profiles.settings('upstream-neo'),target,ref)

    def test_install_writes_pack_owned_attributes_and_preserves_input_identity(self):
        root=Path(__file__).resolve().parents[1]/'build/benchmark-fixture-tests'/str(uuid.uuid4())
        root.mkdir(parents=True)
        values=dict(num_ray_bounces='4',initial_samples='32',spatial_reuse_samples='4',spatial_reuse_radius='32',temporal_confidence_cap='24')
        ref={'attributes':[dict(name='render_pipeline.module.ray_tracing.attribute.'+k,type='int_range:1-256',default_value=v) for k,v in values.items()]}
        for target,module in [('upstream-neo','main_render'),('fork','ray_tracing')]:
            case=root/target;case.mkdir()
            config={'attributes':[dict(a,name=a['name'].replace('ray_tracing',module)) for a in ref['attributes']]}
            result=profiles.install(case,target,target_config=config,reference_config=ref)
            storage=Path(result['shaderAttributeStorage'])
            self.assertTrue(storage.is_file())
            observed=compare.properties(storage)
            self.assertEqual('32',observed[f'render_pipeline.module.{module}.attribute.initial_samples'])
            self.assertNotIn(f'render_pipeline.module.{module}.attribute.shader_pack_path',observed)
            if target=='upstream-neo':
                pack=(case/'radiance/shaders/world/ray_tracing/advanced.zip').resolve()
                self.assertEqual('advanced.zip-'+hashlib.sha256(str(pack).encode()).hexdigest()+'.properties',storage.name)
            else:self.assertEqual('advanced.zip.txt',storage.name)

    def test_comparison_rejects_silent_fallback_focus_and_resolution_changes(self):
        root=Path(__file__).resolve().parents[1]/'build/benchmark-fixture-tests'/str(uuid.uuid4())
        folder=root/'radiance-audit/benchmark/1';folder.mkdir(parents=True)
        wanted=profiles.settings('upstream-neo')
        (root/'REQUESTED_SETTINGS.json').write_text(json.dumps(wanted))
        (root/'FIXTURE.json').write_text('{"camera":[0.5,83,40.5,180,18]}')
        observed={'activePreset':wanted['expectedPreset'],'activeModules':wanted['rrModule']}
        observed.update({'option.'+k:v for k,v in wanted['options'].items()})
        for module in wanted['pipeline']['presetModules']:
            observed.update({a['name']:a['value'] for a in module['attributes']})
        bounce='render_pipeline.module.main_render.attribute.num_ray_bounces'
        wanted['pipeline']['presetModules'][1]['attributes'].append(dict(name=bounce,type='int_range:1-15',value='4'))
        observed[bounce]='4'
        (root/'REQUESTED_SETTINGS.json').write_text(json.dumps(wanted))
        text='\n'.join(k+'='+v for k,v in observed.items())
        for phase in ('world-start','sample-end'):(folder/(phase+'-settings.properties')).write_text(text)
        (folder/'STATUS.txt').write_text('error=\nworldEntered=true\nincompleteFrame=false\nnormalStopRequested=true\n')
        frame=folder/'frames.csv'
        header='framebuffer_width,framebuffer_height,glfw_actual_focused,iconified\n'
        frame.write_text(header+'2560,1440,0,0\n')
        (root/'loaded-runtimes.json').write_text('[{"path":"nvngx_dlssd.dll"}]')
        (root/'stdout.log').write_text('All dimensions are saved')
        analysis={'minimumCountChecks':{'chests':True},'process':{'exitCode':0,'timeoutClose':False,'loadedCore':True,'observedForegroundSamples':0}}
        analysis['camera']={'position':[0.5,83,40.5],'rotation':[-180,18]}
        self.assertEqual([],compare.gate(root,analysis))
        pack=root/'radiance/shaders/world/ray_tracing/advanced.zip'
        pack.parent.mkdir(parents=True);pack.write_bytes(b'fixed pack identity')
        wanted['shaderConfigIdentity']={'target':{'entry':'shaders/world/ray_tracing/advanced.zip',
                                                'packSha256':hashlib.sha256(pack.read_bytes()).hexdigest().upper()}}
        (root/'REQUESTED_SETTINGS.json').write_text(json.dumps(wanted))
        self.assertEqual([],compare.gate(root,analysis))
        pack.write_bytes(b'changed pack')
        self.assertIn('Extracted Advanced shader identity mismatch',compare.gate(root,analysis))
        pack.write_bytes(b'fixed pack identity')
        (folder/'sample-end-settings.properties').write_text(text.replace(bounce+'=4',bounce+'=3'))
        self.assertTrue(any(bounce in e for e in compare.gate(root,analysis)))
        (folder/'sample-end-settings.properties').write_text(text)
        analysis['camera']['position'][0]=1.5
        self.assertIn('Saved camera position mismatch',compare.gate(root,analysis))
        analysis['camera']['position'][0]=0.5
        (folder/'sample-end-settings.properties').write_text(text.replace(wanted['expectedPreset'],'rt_nrd_fsr'))
        self.assertTrue(any('preset mismatch' in e for e in compare.gate(root,analysis)))
        frame.write_text(header+'1280,720,1,0\n')
        errors=compare.gate(root,analysis)
        self.assertTrue(any('framebuffer_width' in e for e in errors))
        self.assertTrue(any('glfw_actual_focused' in e for e in errors))

if __name__=='__main__':unittest.main()
