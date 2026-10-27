from pathlib import Path
import zipfile,os,hashlib,json
ROOT=Path(__file__).resolve().parents[2];WORK=ROOT.parent;D=WORK/'deliverables';S=ROOT/'tools/title_cinema/rework';M=S/'output';B=ROOT/'build/title-cinema'
def save(p,b):
 with Path(p).open('wb') as f:f.write(b);f.flush();os.fsync(f.fileno())
for src,name in [(M/'Bomi_Stand_Mascot.glb','Bomi_Stand_Mascot_v5.glb'),(M/'Aurora_Hourglass.glb','Aurora_Hourglass.glb'),(B/'pc-model.png','Bomi_Model_Preview_v5.png'),(B/'pc-hourglass.png','Aurora_Hourglass_Preview.png')]:save(D/name,src.read_bytes())
readme='''보미 v5 · 사용자 지정 새 스킨 기반 / Aurora 모래시계

Bomi_Stand_Mascot.glb: 텍스처가 포함된 27관절 보미 모델. 단위 m, 앞쪽 +Z.
Bomi_Stand_Mascot.bbmodel: Blockbench 자유 메시 편집본. 표준 스킨 파일이 아닙니다.
Aurora_Hourglass.glb: 프레임/유리/모래/보석을 나눈 독립 재사용 모델. 높이 약 0.546 m. 원점 중앙.
Aurora_Hourglass.bbmodel: 같은 모래시계의 편집용 메시와 색상 텍스처.

모드 2.13.0의 메인화면에는 이미 통합되어 있습니다. 이 ZIP은 편집/재사용용이며 mods 폴더에 넣는 파일이 아닙니다.
별도 YSM 모델 배포 형식으로 변환한 파일도 아닙니다.
보미 GLB에는 기본 대기/걷기/손 흔들기 포즈가 있습니다. 메인화면 전용 걷기·뒤돌아보기·모래시계 시선 동작은 전체 모드 소스의 performance.anim 및 build_animation.py가 담당합니다.

source/의 생성기는 Python + NumPy + SciPy + Pillow로 실행할 수 있습니다.
보미 생성: build_model.py / new_character.py / assets/
모래시계 생성: hourglass.py
얼굴 텍스처는 새 모델을 위해 생성했고, user skin 원본은 변경 없이 보존했습니다.
모래시계 GLB는 표준 PBR/투명 재질이고 게임은 전용 GLSL을 사용하므로 편집기마다 반사광이 다를 수 있습니다.

preview/는 실제 배포 메시의 렌더입니다.
'''
p=D/'Bomi_Model_Pack_v5.zip'
with zipfile.ZipFile(p,'w',zipfile.ZIP_DEFLATED,compresslevel=7) as z:
 z.writestr('README_KO.txt',readme)
 for file in M.glob('*'):
  if file.suffix in ['.glb','.bbmodel','.json','.png'] and not file.name.startswith('Bomi_New_'):z.write(file,'models/'+file.name)
 for name in ['build_model.py','new_character.py','hourglass.py','ASSETS.txt']:
  z.write(S/name,'source/'+name)
 for file in (S/'assets').glob('*'):z.write(file,'source/assets/'+file.name)
 z.write(B/'pc-model.png','preview/Bomi_Model.png');z.write(B/'pc-hourglass.png','preview/Aurora_Hourglass.png')
 z.write(ROOT/'LICENSE','LICENSE.txt')
with p.open('rb') as f:os.fsync(f.fileno())
with zipfile.ZipFile(p) as z:assert z.testzip() is None
print(p.name,p.stat().st_size)
