from pathlib import Path
import json,zipfile,hashlib,shutil,sys,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
BASE=Path(sys.argv[1]);OUT=Path(sys.argv[2]);OUT.mkdir(parents=True,exist_ok=True)
VERSION='2.18.0';PROOF=Path((ROOT/'build/stand-proof-path.txt').read_text().strip())
client=(PROOF/'client-result.txt').read_text();restart=(PROOF/'restart-result.txt').read_text()
assert not (PROOF/'client-error.txt').exists() and not (PROOF/'restart-error.txt').exists()
for item in ['R casts with empty hands','R casts while stone selected','R casts with fruit selected','stand restored on respawn','fruit resets stand','all selected items survive stand lifecycle']:assert 'PASS '+item in client
assert 'PASS server stand restored after process restart' in restart
roots=[ET.parse(p).getroot() for p in (ROOT/'build/test-results/test').glob('TEST-*.xml')]
assert roots and sum(int(p.attrib['failures'])+int(p.attrib['errors']) for p in roots)==0
jar=ROOT/f'build/libs/botand-{VERSION}.jar'
with zipfile.ZipFile(jar) as z:
 assert z.testzip() is None and json.loads(z.read('fabric.mod.json'))['version']==VERSION
 assert not any('botandverify' in n for n in z.namelist())
 for n in ['stand/StandHolder','stand/StandManager','client/StandClient','mixin/PlayerStandMixin','mixin/client/StandHeldItemFeatureMixin']:assert f'dev/sangwoo/celestialfall/{n}.class' in z.namelist()
 assert 'PlayerStandMixin' in json.loads(z.read('celestialfall.mixins.json'))['mixins']
 assert 'StandHeldItemFeatureMixin' in json.loads(z.read('celestialfall.client.mixins.json'))['client']
 ref=json.loads(z.read('celestialfall.refmap.json'))['mappings'];assert any('StandHeldItemFeatureMixin' in n for n in ref)
 assert b'intermediary' in z.read('celestialfall.accesswidener')
with zipfile.ZipFile(BASE) as old:
 entries={n:old.read(n) for n in old.namelist() if not n.endswith('/') and not n.startswith(('source/','verification/','mods/botand-')) and n!='INSTALL_KO.md'}
readme=(ROOT/'STANDS_KO.md').read_text()+'\n\n---\n\n'+(ROOT/'LOOT_KO.md').read_text()+'\n\n---\n\n'+(ROOT/'COMMERCE_KO.md').read_text()
entries['INSTALL_KO.md']=readme.encode();entries['mods/'+jar.name]=jar.read_bytes()
for p in sorted(ROOT.rglob('*')):
 rel=p.relative_to(ROOT)
 if not p.is_file() or rel.parts[0] in ['.gradle','build','run'] or '__pycache__' in rel.parts or p.suffix in ['.pyc','.class']:continue
 if p.suffix=='.jar' and p.name!='gradle-wrapper.jar':continue
 entries['source/celestial-fall/'+str(rel)]=p.read_bytes()
for p in PROOF.glob('*.png'):entries['preview/'+p.name]=p.read_bytes()
for name in ['client-result.txt','restart-result.txt']:entries['verification/'+name]=(PROOF/name).read_bytes()
for p in (ROOT/'build/test-results/test').glob('TEST-*.xml'):entries['verification/unit-tests/'+p.name]=p.read_bytes()
manifest={'version':VERSION,'unit_tests':sum(int(p.attrib['tests']) for p in roots),'native_client_and_integrated_server':True,'process_restart':True,'first_and_third_person_images_visually_inspected':True,'separate_remote_multiplayer_tested':False,'user_device_tested':False,'jar_sha256':hashlib.sha256(jar.read_bytes()).hexdigest()}
entries['verification/release.json']=json.dumps(manifest,ensure_ascii=False,indent=2).encode()
archive=OUT/f'Botand-1.20.1-Fabric-v{VERSION}.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=7) as z:
 for name,data in entries.items():z.writestr(name,data)
with zipfile.ZipFile(archive) as z:assert z.testzip() is None
shutil.copyfile(jar,OUT/jar.name)
(OUT/f'Botand_{VERSION}_사용법.md').write_text(readme)
print(json.dumps({'verification':manifest,'files':[{'name':p.name,'bytes':p.stat().st_size} for p in OUT.iterdir()]},ensure_ascii=False,indent=2))
