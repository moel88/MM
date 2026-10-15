"""Deterministic UV texture and registry assets for the articulated gray cyclops."""
from pathlib import Path
import json
from PIL import Image,ImageDraw
root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/celestialfall'
scale=4
image=Image.new('RGBA',(256,256),(137,142,148,255));d=ImageDraw.Draw(image)
def rect(box,fill):d.rectangle(tuple(round(v*scale) for v in box),fill=fill)
def face(x,y,w,h,base):
    # Subtle ceramic bevels stay neutral gray on every face.
    rect((x,y,x+w-.25,y+h-.25),base)
    rect((x+.25,y,x+w-.5,y+.25),(185,190,195,255))
    rect((x,y+.25,x+.25,y+h-.5),(161,167,172,255))
    rect((x+.25,y+h-.5,x+w-.25,y+h-.25),(108,114,121,255))
def cube(u,v,w,h,depth):
    face(u+depth,v,w,depth,(172,177,182,255))
    face(u+depth+w,v,w,depth,(111,116,122,255))
    for x,width,col in [(u,depth,(133,139,146,255)),(u+depth,w,(155,160,166,255)),(u+depth+w,depth,(128,134,141,255)),(u+2*depth+w,w,(143,148,154,255))]:
        face(x,v+depth,width,h,col)
cube(0,0,8,8,8);cube(16,16,8,12,4)
for u,v in [(40,16),(32,48),(0,16),(16,48)]:
    cube(u,v,4,12,4)
    rect((u,v+9.5,u+15.75,v+10),(106,112,119,255))
    rect((u,v+14.75,u+15.75,v+15.75),(119,125,131,255))
# One rounded eye on the front of the head (vanilla head UV: x=8..16,y=8..16).
d.ellipse((34,34,62,61),fill=(105,112,119,255))
d.ellipse((35,34,61,58),fill=(243,245,247,255))
d.ellipse((42,39,56,57),fill=(60,66,73,255))
d.ellipse((45,41,54,55),fill=(29,34,40,255))
d.ellipse((43,40,48,45),fill=(255,255,255,255))
d.ellipse((51,51,53,53),fill=(200,205,210,255))
# A tiny recessed chest panel, still entirely gray.
rect((22.5,22,25.5,25),(122,128,135,255));rect((23,22.5,25,24.5),(165,170,175,255))
dest=root/'textures/entity/training_dummy.png';dest.parent.mkdir(parents=True,exist_ok=True);image.save(dest)
names={'ko_kr':['가만히 있는 더미','공격 더미','방어 더미','콤보 연습 더미'],'en_us':['Idle Dummy','Attack Dummy','Guard Dummy','Combo Training Dummy']}
kinds=['idle','attack','guard','combo']
for lang,labels in names.items():
    p=root/f'lang/{lang}.json';data=json.loads(p.read_text())
    for kind,label in zip(kinds,labels):
        data[f'entity.celestialfall.{kind}_dummy']=label
        data[f'item.celestialfall.{kind}_dummy_spawn_egg']=label+(' 생성 알' if lang=='ko_kr' else ' Spawn Egg')
    if lang=='ko_kr':data.update({'message.botand.dummy_no_space':'앞쪽 공간이 부족합니다. 두 블록 높이의 빈 공간을 확보하세요.','message.botand.dummy_spawned':'%s 소환 완료','message.botand.dummy_cleared':'주변 32블록의 더미 %s개를 제거했습니다.','message.botand.dummy_reset':'주변 32블록의 더미 %s개 체력과 전투 상태를 초기화했습니다.','hud.botand.dummy_broken':'가드 파괴!','hud.botand.dummy_guard':'가드 %s/100'})
    else:data.update({'message.botand.dummy_no_space':'Not enough space ahead. Clear a two-block-high area.','message.botand.dummy_spawned':'Spawned %s','message.botand.dummy_cleared':'Removed %s dummies within 32 blocks.','message.botand.dummy_reset':'Reset health and combat for %s dummies within 32 blocks.','hud.botand.dummy_broken':'GUARD BROKEN','hud.botand.dummy_guard':'Guard %s/100'})
    p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
for kind in kinds:
    p=root/f'models/item/{kind}_dummy_spawn_egg.json';p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps({'parent':'minecraft:item/template_spawn_egg'})+'\n')
print(dest)
