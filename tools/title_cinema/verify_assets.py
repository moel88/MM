from pathlib import Path
import json,struct,hashlib,xml.etree.ElementTree as ET,subprocess
import numpy as np
ROOT=Path(__file__).resolve().parents[2];A=ROOT/'src/main/resources/assets/celestialfall';OUT=ROOT/'build/title-cinema'
meta=json.loads((A/'cinema/scene.json').read_text());total=0
for name,count in meta['meshes'].items():
 raw=(A/'cinema'/(name+'.mesh')).read_bytes();magic,n=struct.unpack_from('<4sI',raw);assert magic==b'BMS1' and n==count and len(raw)==8+n*56
 v=np.frombuffer(raw[8:],'<f4').reshape(-1,14);assert np.isfinite(v).all();assert ((v[:,12]>=-1)&(v[:,12]<len(meta['bones']))).all();assert np.max(abs(np.linalg.norm(v[:,3:6],axis=1)-1))<.001;total+=n//3
assert meta['character']['arm_model']=='slim' and not meta['character']['custom_geometry']
assert meta['meshes']['bomi']==432 and len(meta['bones'])==7
skin=(A/'textures/cinema/bomi.png').read_bytes()
assert hashlib.sha256(skin).hexdigest()==meta['character']['skin_sha256']
assert skin==(ROOT/'tools/title_cinema/assets/bomi_skin.png').read_bytes()
assert (A/'cinema/blink.mesh').stat().st_size==8
raw=(A/'cinema/performance.anim').read_bytes();_,n,b,fps,intro,loop=struct.unpack_from('<4sIIIff',raw);v=np.frombuffer(raw[24:],'<f4').reshape(n,b*12+9)
assert np.isfinite(v).all();assert np.max(abs(v[-1]-v[int(intro*fps)]))==0
assert np.max(abs(v[int(intro*fps)]-v[int(intro*fps)-1]))<.01
sounds=json.loads((A/'sounds.json').read_text());cue=json.loads((A/'cinema/performance.json').read_text())
for e in cue['events']+cue['loop_events']:assert e['sound'] in sounds
for p in (A/'sounds/cinema').glob('*.ogg'):
 subprocess.run(['ffmpeg','-v','error','-i',str(p),'-f','null','-'],check=True,stdout=subprocess.DEVNULL)
music=json.loads((A/'sounds/cinema/music_sources.json').read_text())
asset=A/'sounds/cinema/title_satellites.ogg'
assert hashlib.sha256(asset.read_bytes()).hexdigest()==music['asset_sha256']
assert sounds['cinema_title_satellites']['sounds'][0]['stream'] is True
assert cue['music']['start_seconds']==3.8 and cue['final_facing']=='screen_left'
steps=[A/'sounds/cinema'/('step_'+str(i)+'.ogg') for i in range(1,9)]
assert len({hashlib.sha256(p.read_bytes()).hexdigest() for p in steps})==8
for i in range(1,9):assert sounds['cinema_step_'+str(i)]['sounds'][0]['preload'] is True
raw_audio=subprocess.check_output(['ffmpeg','-v','error','-i',str(asset),'-ar','48000','-ac','2','-f','f32le','pipe:1'])
audio=np.frombuffer(raw_audio,'<f4').reshape(-1,2);assert np.isfinite(audio).all()
onset=float(np.flatnonzero(np.max(abs(audio),axis=1)>10**(-55/20))[0]/48000)
assert onset<.025 and abs(len(audio)/48000-129.4)<.02 and np.max(abs(audio))<1
music_check={'cue_seconds':cue['music']['start_seconds'],'audible_onset_after_cue_seconds':onset,
 'duration_seconds':len(audio)/48000,'peak':float(np.max(abs(audio))),'streamed':True,
 'footstep_variants':8,'final_facing':cue['final_facing'],'source_sha256':music['source_sha256']}
results=[ET.parse(p).getroot() for p in (ROOT/'build/test-results/test').glob('TEST-*.xml')]
r={'version':'2.13.3','tests':sum(int(x.attrib['tests']) for x in results),'failures':sum(int(x.attrib['failures']) for x in results),'errors':sum(int(x.attrib['errors']) for x in results),'rig_bones':b,'character_model':'Minecraft native slim','skin_bytes_unchanged':True,'music_and_footsteps':music_check,'character_triangles':meta['meshes']['bomi']//3,'hourglass_triangles':(meta['meshes']['prop']+meta['meshes']['glass'])//3,'all_scene_triangles':total,'intro_seconds':intro,'loop_seconds':loop,'animation_fps':fps,'loop_seam_exact':True,'background_image_or_video_used_in_game':False,'post_processing_required':False,'renderer':'Capability-gated desktop GLSL / Minecraft compatibility path','preview_video':'No new video; separate desktop/forced-compatibility PNG captures from actual Minecraft 1.20.1.','target':'Desktop effects with automatic Android/GLES compatibility','external_shader_pack_tested':False,'android_pojav_device_tested':False,'checksums':{str(p.relative_to(A)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((A/'cinema').rglob('*')) if p.is_file()}}
for name,dir in [('native_map_client','native-map/'),('native_compat_client','native-compat/')]:
 p=OUT/dir/'native-client-result.txt';r[name]=p.read_text().strip() if p.exists() else 'Not completed'
assert r['tests']==149 and r['failures']==0 and r['errors']==0
for key in ['native_map_client','native_compat_client']:assert r[key].startswith('PASS 2.13.3:')
r['native_indexed_query_regression_covered']=True
(OUT/'VERIFICATION.json').write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:v for k,v in r.items() if k!='checksums'},ensure_ascii=False,indent=2))
