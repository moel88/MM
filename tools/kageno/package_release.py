from pathlib import Path
import json,zipfile,hashlib,shutil,sys,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
BASE=Path(sys.argv[1]);OUT=Path(sys.argv[2]);OUT.mkdir(parents=True,exist_ok=True)
VERSION='2.26.0';PROOF=Path((ROOT/'build/kageno-proof-path.txt').read_text().strip());ATOMIC=Path((ROOT/'build/kageno-atomic-proof-path.txt').read_text().strip())
client=(PROOF/'client-result.txt').read_text();atomic=(ATOMIC/'atomic-result.txt').read_text()
assert not (PROOF/'client-error.txt').exists() and not (ATOMIC/'client-error.txt').exists()
required=['R deals all five hits total 21','R rejects malicious movement packet','R cooldown rejects replay after recovery','H multi-player damage target 2','non-Kageno victim receives synchronized H camera and lock','non-Kageno victim receives R grayscale','T ground fire deals 13 hits total 51','held T carries target and deals six hits total 50','E deals eight hits total 26','Y deals two hits total 16','M1 total damage 28','Q hit grows power and synchronizes shadow mark','shadow mark amplifies damage from other sources exactly once','G starts atomic from arbitrary held item','map exit clears server locks','H delayed snapshot cannot reset animation clock','H camera continuity captured for caster and victim','E thrust excludes nearby side and rear targets']
required.append('H original musical cue plays continuously at 1x')
for item in required:assert 'PASS '+item in client,item
for item in ['G never takes over gameplay camera','G caster cannot move or jump','G preserves all 9583 sampled terrain blocks','G preserves the manually selected camera base yaw','G applies only bounded shake around manual camera rotation','G actual framebuffer is covered by violet energy inside the blast','G original spoken audio actually starts in SoundManager','G movement lock releases','G body pose is active without a cinematic camera']:assert 'PASS '+item in atomic
for tick in [20,128,240,450]:
 assert f'PASS Q theme stays playing at G tick {tick}' in atomic
 assert f'PASS Q theme instance is unchanged at G tick {tick}' in atomic
for item in ['R server walk speed is exactly 50 percent','R caster actually walks during skill','R restores walking speed after completion','F guard does not zoom the lens']:assert 'PASS '+item in client
roots=[ET.parse(p).getroot() for p in (ROOT/'build/test-results/test').glob('TEST-*.xml')]
assert roots and sum(int(p.attrib['failures'])+int(p.attrib['errors']) for p in roots)==0
jar=ROOT/f'build/libs/botand-{VERSION}.jar'
with zipfile.ZipFile(jar) as z:
 assert z.testzip() is None and json.loads(z.read('fabric.mod.json'))['version']==VERSION
 assert not any('botandverify' in n for n in z.namelist())
 for n in ['stand/StandHolder','stand/StandManager','stand/KagenoManager','stand/KagenoRules','client/StandClient','client/KagenoSword','client/KagenoRenderer','mixin/KagenoFreezeMixin','client/KagenoFire','client/KagenoAudio','mixin/client/KagenoCullingMixin','client/KagenoCameraPath','client/KagenoCastClock','client/KagenoVfx','client/KagenoStrikeEffects','client/AtomicCraterEffect','client/AtomicImmersion','mixin/client/StandFovMixin']:assert f'dev/sangwoo/celestialfall/{n}.class' in z.namelist()
 assert 'KagenoFreezeMixin' in json.loads(z.read('celestialfall.mixins.json'))['mixins']
 assert 'KagenoShadowMixin' in json.loads(z.read('celestialfall.client.mixins.json'))['client']
 assert 'dev/sangwoo/celestialfall/mixin/client/KagenoShadowMixin.class' in z.namelist()
 assert 'assets/celestialfall/sounds/kageno_theme.ogg' in z.namelist()
 assert json.loads(z.read('assets/celestialfall/models/item/atomic_sword.json'))['parent']=='builtin/entity'
 assert b'intermediary' in z.read('celestialfall.accesswidener')
 ref=json.loads(z.read('celestialfall.refmap.json'))['mappings'];assert any('KagenoFreezeMixin' in n for n in ref)
with zipfile.ZipFile(BASE) as old:
 entries={n:old.read(n) for n in old.namelist() if not n.endswith('/') and not n.startswith(('source/','verification/','mods/botand-','preview/Kageno_','preview/Atomic_','preview/Botand_')) and n!='INSTALL_KO.md'}
readme=(ROOT/'STANDS_KO.md').read_text()
entries['INSTALL_KO.md']=readme.encode();entries['mods/'+jar.name]=jar.read_bytes()
notices=entries.get('THIRD_PARTY_NOTICES.txt',b'').decode('utf-8')
if 'Kageno 2.19.0:' not in notices:notices+='\nKageno 2.19.0: kageno_theme.ogg is an excerpt beginning at 134 seconds of the user-supplied "The Eminence in Shadow OST - Theme of Cid Kagenou(1).mp3". This supplied music is not covered by the code license. Kageno sword geometry is recreated in code from the user-supplied visual reference.\n'
if 'Kageno 2.20.0:' not in notices:notices+='\nKageno 2.20.0: per-skill and Atomic audio are native-speed excerpts from the user-supplied 1000022644.mp4 review. These media excerpts are not covered by the code license. The procedural flame shader, keyframed Minecraft rig and effect meshes are a native recreation; no original Roblox code or models are bundled.\n'
notices+='\nKageno 2.24.0: supersedes 2.23 procedural combat audio. Current Kageno SFX are source-separated excerpts from the user-supplied video. BandIt v2 by Karn N. Watcharasupat, Chih-Wei Wu, Iroro Orife (CC BY-SA 4.0 weights; Apache-2.0 code), ONNX conversion by Prompt_what. Weights are not bundled. See source/celestial-fall/tools/kageno/audio_model.json and BANDIT_NOTICE.txt for provenance. Reference video/audio are supplied media and not covered by the code license.\n'
notices+='\nKageno 2.25.0: H intentionally retains the original music and effects. G retains the unseparated spoken opening and crossfades into separated explosion audio. These excerpts remain supplied media, not code-license assets.\n'
notices+='\nKageno 2.26.0: motion, flame and interior effects revised; all shipped audio bytes are unchanged from 2.25.0.\n'
entries['THIRD_PARTY_NOTICES.txt']=notices.encode()
for p in sorted(ROOT.rglob('*')):
 rel=p.relative_to(ROOT)
 if not p.is_file() or rel.parts[0] in ['.gradle','build','run'] or '__pycache__' in rel.parts or p.suffix in ['.pyc','.class','.log']:continue
 if p.suffix=='.jar' and p.name!='gradle-wrapper.jar':continue
 entries['source/celestial-fall/'+str(rel)]=p.read_bytes()
for proof in [PROOF,ATOMIC]:
 for p in proof.glob('Kageno_*.png'):entries['preview/'+p.name]=p.read_bytes()
entries['verification/client-result.txt']=client.encode();entries['verification/atomic-result.txt']=atomic.encode()
entries['verification/h-camera-frames.csv']=(PROOF/'h-camera-frames.csv').read_bytes()
entries['verification/h-camera-performance.json']=(PROOF/'h-camera-performance.json').read_bytes()
entries['verification/native-phase-times.txt']=(PROOF/'phase-times.txt').read_bytes()
entries['verification/atomic-phase-times.txt']=(ATOMIC/'phase-times.txt').read_bytes()
entries['verification/h-camera-summary.txt']=(PROOF/'h-camera-summary.txt').read_bytes()
entries['verification/framebuffer-gray.json']=(PROOF/'framebuffer-gray.json').read_bytes()
entries['verification/current-atomic-audio.json']=(ROOT/'src/main/resources/assets/celestialfall/audio_sync.json').read_bytes()
entries['verification/audio.json']=(ROOT/'tools/kageno/audio_reference.json').read_bytes() # unchanged 2.25 original/separated audio assets
for p in (ROOT/'build/test-results/test').glob('TEST-*.xml'):entries['verification/unit-tests/'+p.name]=p.read_bytes()
manifest={'version':VERSION,'H_back_forward_slide':True,'G_real_player_pose_without_camera_takeover':True,'T_reference_ignition_and_low_glide':True,'unit_tests':sum(int(p.attrib['tests']) for p in roots),'native_checks':client.count('PASS ')+atomic.count('PASS '),'native_client_and_integrated_server':True,'client_as_caster_and_non_stand_victim':True,'server_player_fixtures':4,'three_simultaneous_H_victims':True,'reference_song_offset_seconds':134,'R_half_walk_verified':True,'G_no_cutscene_and_no_movement_verified':True,'G_unchanged_sampled_blocks':9583,'guard_FOV_stable':True,'combat_audio_from_supplied_video':True,'music_separation_applied':True,'H_original_music_preserved':True,'G_original_dialogue_preserved':True,'G_inside_violet_framebuffer_verified':True,'G_manual_camera_with_additive_shake':True,'perfect_music_removal_claimed':False,'Q_G_continuous_instance_verified':True,'audio_preview_is_device_recording':False,'separate_remote_multiplayer_tested':False,'user_device_tested':False,'actual_framebuffer_grayscale_verified':True,'E_forward_thrust_and_side_rejection':True,'H_stale_snapshot_and_frame_continuity_verified':True,'reference_comparison_video_included':True,'atomic_reference_cast_seconds':23.4,'jar_sha256':hashlib.sha256(jar.read_bytes()).hexdigest()}
entries['verification/release.json']=json.dumps(manifest,ensure_ascii=False,indent=2).encode()
film=OUT/'Botand_2.26.0_Reference_Comparison.mp4'
assert film.is_file(), 'Reference comparison video is required for this release'
entries['preview/'+film.name]=film.read_bytes()
entries['verification/comparison-alignment.json']=(ROOT/'build/kageno-comparison/alignment.json').read_bytes()
for p in (ROOT/'tools/kageno').glob('*reference.json'):entries['verification/'+p.name]=p.read_bytes()
archive=OUT/f'Botand-1.20.1-Fabric-v{VERSION}.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=7) as z:
 for name,data in entries.items():z.writestr(name,data)
with zipfile.ZipFile(archive) as z:assert z.testzip() is None
shutil.copyfile(jar,OUT/jar.name)
(OUT/f'Botand_{VERSION}_사용법.md').write_text(readme)
print(json.dumps({'verification':manifest,'files':[{'name':p.name,'bytes':p.stat().st_size} for p in [OUT/jar.name,archive,OUT/f'Botand_{VERSION}_사용법.md']]},ensure_ascii=False,indent=2))
