from pathlib import Path
import zipfile,json,hashlib,shutil,os
ROOT=Path(__file__).resolve().parents[2];WORK=ROOT.parent;OUT=WORK/'deliverables';B=ROOT/'build/title-cinema';OUT.mkdir(exist_ok=True)
def copy(src,dst):
 data=Path(src).read_bytes()
 with Path(dst).open('wb') as f:f.write(data);f.flush();os.fsync(f.fileno())
 assert Path(dst).read_bytes()==data
assert json.loads((B/'VERIFICATION.json').read_text())['version']=='2.13.3'
jar=ROOT/'build/libs/botand-2.13.3.jar';copy(jar,OUT/jar.name)
with zipfile.ZipFile(jar) as z:
 assert json.loads(z.read('fabric.mod.json'))['version']=='2.13.3'
 assert not any('botandverify' in n or 'CinemaClientCapture' in n for n in z.namelist())
 assert 'assets/celestialfall/sounds/cinema/LICENSE.txt' not in z.namelist()
 for name in ['stage.mesh','bomi.mesh','performance.anim']:
  assert z.read('assets/celestialfall/cinema/'+name)==(ROOT/'src/main/resources/assets/celestialfall/cinema'/name).read_bytes()
 for p in (ROOT/'src/main/resources/assets/celestialfall/sounds/cinema').glob('*.ogg'):
  assert z.read('assets/celestialfall/sounds/cinema/'+p.name)==p.read_bytes()
copy(B/'native-map/minecraft-2.png',OUT/'Botand_Main_Menu_2.13.3.png')
copy(B/'native-compat/minecraft-2.png',OUT/'Botand_Main_Menu_Compatibility_2.13.3.png')
install='''보탠드 2.13.3 · 맵 입장 충돌 수정

설치 대상: Minecraft Java 1.20.1 / Fabric / Java 17
1. 게임을 완전히 종료하세요.
2. 현재 사용 중인 인스턴스의 mods 폴더에서 기존 botand-*.jar 또는 이전 celestial-fall 모드 JAR을 다른 폴더에 백업하세요.
3. botand-2.13.3.jar 하나만 mods 폴더에 넣으세요. 구버전과 신버전을 함께 넣지 마세요.
4. Fabric API 0.92.2+1.20.1은 계속 필요합니다. 이미 설치되어 있으면 그대로 사용하세요. 이 ZIP의 mods 폴더에도 포함되어 있습니다.
5. 기존 보탠드 전용 맵에 들어가면 새 메인화면이 열립니다. 플레이를 누르면 같은 맵에서 시작합니다.

이전 보탠드 모드의 업데이트 파일입니다. 별도 장식 모드나 YSM 모델 파일을 추가로 설치하지 않아도 됩니다. 기존 전용 맵 파일은 그대로 사용합니다.

보미는 화면 왼쪽을 바라봅니다. 회전 시작(3.8초)에 업로드한 Satellites가 재생됩니다. 앞 무음을 제거했으며 음정·속도를 바꾸지 않았습니다. 대기 동작·화면 크기 변경 시 노래는 이어지고 플레이하면 정지합니다. 음악은 배경음악 슬라이더로 조절합니다. 돌길 발소리는 서로 다른 8개의 레이어 조합입니다.

로고·플레이 버튼은 처음부터 표시되고, 약 7초 도입 후 모래시계를 보는 12초 대기 동작이 반복됩니다. 화면의 설정된 인터페이스 음량에 맞춰 전용 효과음이 재생됩니다.

보미는 보내주신 64×64 원본 스킨을 그대로 사용합니다. Minecraft의 기본 슬림 모델(팔 3픽셀)과 기본 겉 레이어를 적용했으며, 기존 커스텀 얼굴·머리카락·의상 모델은 사용하지 않습니다. 걷기·돌아보기·모래시계 대기 동작과 기존 모래시계·정원은 유지했습니다.

모바일 MJ/Pojav에서 맵 입장 후 메인화면이 지원하지 않는 그래픽 함수를 호출하여 강제 종료되던 문제를 수정했습니다. Android/GLES 또는 필수 그래픽 기능이 없는 환경은 Minecraft 기본 렌더러로 같은 보미·정원·모래시계·음악을 표시합니다. PC의 그림자·HDR·블룸·심도는 유지합니다. 별도 셰이더팩을 설치할 필요가 없습니다.

배경 정원은 메인화면용 3D 세트이며 플레이 맵의 블록이나 건물을 바꾸지 않습니다.

149개 자동 테스트와 실제 Minecraft 1.20.1의 PC/강제 호환 모드에서 자동 메뉴 진입, 음악 타이밍, 플레이 후 같은 맵 복귀를 확인했습니다. 실제 Android 기기에서는 직접 검증하지 못했습니다. 외부 셰이더팩 조합도 미검증입니다.

preview/의 PNG는 실제 게임 캡처입니다. Compatibility 파일은 데스크톱에서 강제 선택한 호환 렌더러 화면이며, 일반 파일은 PC 고급 효과 화면입니다.
'''
old=zipfile.ZipFile(OUT/'Botand-1.20.1-Fabric-v2.11.2.zip');target=OUT/'Botand-1.20.1-Fabric-v2.13.3.zip'
with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED,compresslevel=7) as z:
 for name in ['mods/fabric-api-0.92.2+1.20.1.jar','CONFIG_EXAMPLE.json','licenses/Botand.txt','THIRD_PARTY_NOTICES.txt','licenses/Fabric-API.txt']:
  z.writestr(name,old.read(name))
 z.write(jar,'mods/'+jar.name);z.writestr('INSTALL_KO.txt',install);z.write(ROOT/'TITLE_CINEMA.md','TITLE_CINEMA.md')
 for file in [OUT/'Botand_Main_Menu_2.13.3.png',OUT/'Botand_Main_Menu_Compatibility_2.13.3.png',B/'pc-model.png',B/'pc-hourglass.png']:
  z.write(file,'preview/'+file.name)
 for name in ['minecraft-0.png','minecraft-1.png','minecraft-3.png','minecraft-credits.png','minecraft-after-close.png','native-client-result.txt']:
  z.write(B/'native-map'/name,'verification/native-map/'+name)
 for file in (B/'native-compat').glob('*'):
  if file.is_file():z.write(file,'verification/native-compat/'+file.name)
 z.write(B/'VERIFICATION.json','verification/VERIFICATION.json')
 z.write(ROOT/'src/main/resources/assets/celestialfall/sounds/cinema/music_sources.json','licenses/USER_PROVIDED_MUSIC.json')
 for file in (ROOT/'tools/title_cinema/rework/output').glob('*'):
  if file.name.startswith(('Aurora_Hourglass','hourglass_')):z.write(file,'models/'+file.name)
 z.write(ROOT/'tools/title_cinema/assets/bomi_skin.png','skin/Bomi_Original_Slim.png')
 z.write(ROOT/'src/main/resources/assets/celestialfall/cinema/bomi.native.json','skin/native-player-info.json')
 z.write(WORK/'cinema-final-build.log','verification/build.log')
 for p in sorted((ROOT/'src').rglob('*')):
  if p.is_file():z.write(p,'source/celestial-fall/'+str(p.relative_to(ROOT)))
 for p in sorted((ROOT/'tools').rglob('*')):
  if p.is_file() and '__pycache__' not in str(p) and p.suffix not in ['.pyc','.class','.jar']:
   if p.name in ['Bomi_Stand_Mascot.glb','bomi_model_manifest.json','bomi_face_albedo.png']:continue
   if 'rework' in p.parts and not (p.name=='hourglass.py' or p.name.startswith(('Aurora_Hourglass','hourglass_'))):continue
   z.write(p,'source/celestial-fall/'+str(p.relative_to(ROOT)))
 for name in ['build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','BUILD_WINDOWS.bat','LICENSE','README.md','TITLE_CINEMA.md','CONFIG_EXAMPLE.json']:
  p=ROOT/name
  if p.exists():z.write(p,'source/celestial-fall/'+name)
 for p in (ROOT/'gradle').rglob('*'):
  if p.is_file():z.write(p,'source/celestial-fall/'+str(p.relative_to(ROOT)))
 for name in ['TITLE_ASSETS.txt']:
  p=ROOT/name
  if p.exists():z.write(p,'source/celestial-fall/'+name)
with target.open('rb') as f:os.fsync(f.fileno())
with zipfile.ZipFile(target) as z:assert z.testzip() is None
report={p.name:{'size':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in [OUT/jar.name,target,OUT/'Botand_Main_Menu_2.13.3.png',OUT/'Botand_Main_Menu_Compatibility_2.13.3.png']}
(OUT/'Botand_2.13.3_checksums.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
