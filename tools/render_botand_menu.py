"""Render the shared native UI vector commands. Review art, not a Minecraft/Pojav screenshot."""
from pathlib import Path
import json,math
from PIL import Image,ImageDraw,ImageFont
import numpy as np
root=Path(__file__).resolve().parents[1];out=root/'build/botand-menu'
fontpath=root.parent/'preview-assets/NotoSansCJKkr-Regular.otf'
scenes=json.loads((out/'scenes.json').read_text());scale=2
fonts={}
def font(size):
 size=max(1,round(size));fonts.setdefault(size,ImageFont.truetype(str(fontpath),size));return fonts[size]
def rgba(v):return(v>>16&255,v>>8&255,v&255,v>>24&255)
def render(scene):
 im=Image.new('RGB',(1800,1000),'#102841');d=ImageDraw.Draw(im,'RGBA')
 for sprite in scene.get('sprites',[]):
  atlas=Image.open(root/'src/main/resources/assets/celestialfall/textures/gui/menu_dock.png')
  x,y,w,h=sprite['u'],sprite['v'],sprite['sw'],sprite['sh']
  tile=atlas.crop((x,y,x+w,y+h)).resize((max(1,round(sprite['w']*scale)),max(1,round(sprite['h']*scale))),Image.Resampling.BILINEAR)
  im.paste(tile,(round(sprite['x']*scale),round(sprite['y']*scale)),tile)
 for p in scene['shapes']:
  points=[(p['xy'][i]*scale,p['xy'][i+1]*scale) for i in range(0,len(p['xy']),2)]
  if p['top']==p['bottom']:d.polygon(points,fill=rgba(p['top']));continue
  x0=max(0,int(min(x for x,y in points)));y0=max(0,int(min(y for x,y in points)));x1=min(im.width,math.ceil(max(x for x,y in points)));y1=min(im.height,math.ceil(max(y for x,y in points)))
  if x1<=x0 or y1<=y0:continue
  mask=Image.new('L',(x1-x0,y1-y0));ImageDraw.Draw(mask).polygon([(x-x0,y-y0) for x,y in points],fill=255)
  top,bottom=np.array(rgba(p['top'])),np.array(rgba(p['bottom']));f=np.linspace(0,1,y1-y0)[:,None];row=(top*(1-f)+bottom*f).astype(np.uint8)
  arr=np.broadcast_to(row[:,None,:],(y1-y0,x1-x0,4)).copy();arr[:,:,3]=(arr[:,:,3].astype(np.uint16)*np.array(mask)//255).astype(np.uint8)
  layer=Image.fromarray(arr,'RGBA');im.paste(layer,(x0,y0),layer)
 for t in scene['labels']:
  f=font(t['size']*scale);text=t['text'];length=d.textlength(text,font=f);maxwidth=t['maxWidth']*scale
  if length>maxwidth:f=font(t['size']*scale*maxwidth/length);length=d.textlength(text,font=f)
  x=t['x']*scale-(length/2 if t['centered'] else 0);y=t['y']*scale
  d.text((x,y),text,font=f,fill=rgba(t['color']),anchor='lt')
 return im
for name,data in scenes.items():
 im=render(data['scene']);im.save(out/(name+'.png'))
# A compact sheet shows all actual menu panels, and both initial/open dock states.
sheet=Image.new('RGB',(1890,1940),'#101e32');d=ImageDraw.Draw(sheet)
d.text((35,22),'BOTAND 2.10 · 플레이어 메뉴',font=font(38),fill='#effaff',anchor='lt')
d.text((35,76),'모드 UI 코드로 그린 배치 미리보기 · 실제 게임 화면은 아님',font=font(22),fill='#8bb6d0',anchor='lt')
labels={'settings':'설정 · 음량 조절과 저장','achievements':'업적 · 검색 / 칭호 적용','quests':'퀘스트 · 처음에는 빈 목록','news':'소식 · 아직 소식 없음','rebirth':'환생 · 초월 / 서버 조건 확인'}
for i,name in enumerate(['settings','achievements','quests','news','rebirth']):
 x=30+(i%2)*930;y=134+(i//2)*560
 d.text((x,y),labels[name],font=font(23),fill='#d9f4ff',anchor='lt')
 sheet.paste(Image.open(out/(name+'.png')).resize((900,500),Image.Resampling.LANCZOS),(x,y+41))
x,y=960,1254
d.text((x,y),'메뉴 버튼 · 처음 / 펼친 상태',font=font(23),fill='#d9f4ff',anchor='lt')
closed=Image.open(out/'dock_closed.png').crop((0,730,120,930)).resize((156,260),Image.Resampling.LANCZOS)
opened=Image.open(out/'dock_open.png').crop((0,330,340,930)).resize((306,540),Image.Resampling.LANCZOS)
sheet.paste(closed,(1010,1450));sheet.paste(opened,(1360,1285))
d.text((1010,1730),'선 세 개 버튼만',font=font(20),fill='#acc9dd',anchor='lt')
d.text((1375,1870),'뽑기·가방은 비활성',font=font(20),fill='#acc9dd',anchor='lt')
sheet.save(out/'Botand_UI_2.10_Preview.png')
print(out/'Botand_UI_2.10_Preview.png')

# Enlarged view of the same atlas regions and layout used by the actual dock.
icons=Image.new('RGB',(1100,900),'#102841');draw=ImageDraw.Draw(icons)
draw.text((40,30),'BOTAND 2.10.1 · 원본 메뉴 아이콘',font=font(32),fill='#effaff',anchor='lt')
draw.text((40,82),'실제 버튼 코드의 배치 렌더 · 게임 스크린샷 아님',font=font(20),fill='#a6cddd',anchor='lt')
draw.text((60,166),'처음 들어왔을 때',font=font(25),fill='#eaf9ff',anchor='lt')
draw.text((512,166),'메뉴를 눌렀을 때',font=font(25),fill='#eaf9ff',anchor='lt')
# A common 1.35 enlargement preserves the in-game relative sizes and layout.
closed=Image.open(out/'dock_closed.png').crop((14,470,302,934)).resize((389,627),Image.Resampling.BILINEAR)
opened=Image.open(out/'dock_open.png').crop((14,470,302,934)).resize((389,627),Image.Resampling.BILINEAR)
icons.paste(closed,(64,218))
icons.paste(opened,(512,218))
draw.text((64,831),'선 세 개 버튼만 표시',font=font(20),fill='#a6cddd',anchor='lt')
draw.text((512,858),'뽑기 · 가방은 잠금 유지',font=font(20),fill='#a6cddd',anchor='lt')
icons.save(out/'Botand_Menu_Original_2.10.1.png')
