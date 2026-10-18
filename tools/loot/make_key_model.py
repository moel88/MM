"""A reusable gold key built from vanilla textured cuboids; no external asset dependency."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[2]/'src/main/resources/assets/celestialfall'
elements=[]
def cube(a,b,rotation=None,texture='gold'):
 e={'from':a,'to':b,'faces':{d:{'uv':[0,0,16,16],'texture':'#'+texture} for d in ['north','south','east','west','up','down']}}
 if rotation:e['rotation']=rotation
 elements.append(e)
# Octagonal bow, thick shaft, two distinct teeth and a collar.
cube([6.8,2,7.25],[8.4,10,8.75]);cube([6.4,8.2,7],[8.8,9.2,9])
cube([8.2,2,7.25],[11.2,3.3,8.75]);cube([8.2,4,7.25],[10.4,5.2,8.75])
for a,b in [([5,11.15,7],[5.8,13.45,9]),([9.4,11.15,7],[10.2,13.45,9]),([6.4,14,7],[8.8,14.8,9]),([6.4,9.8,7],[8.8,10.6,9])]:cube(a,b)
for x,y,angle in [(6.05,13.85,45),(9.15,13.85,-45),(6.05,10.75,-45),(9.15,10.75,45)]:
 cube([x-.95,y-.4,7],[x+.95,y+.4,9],{'origin':[x,y,8],'axis':'z','angle':angle,'rescale':False})
# Small azure enamel center detail on the neck.
cube([7,8.45,6.95],[8.2,9.05,7.1],texture='gem')
model={'textures':{'gold':'minecraft:block/gold_block','gem':'minecraft:block/light_blue_concrete','particle':'minecraft:block/gold_block'},'elements':elements,'gui_light':'front','display':{'gui':{'rotation':[0,0,-28],'translation':[0,0,0],'scale':[.9,.9,.9]},'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[.5,.5,.5]},'fixed':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[.8,.8,.8]},'thirdperson_righthand':{'rotation':[0,-90,20],'translation':[0,1,-1],'scale':[.65,.65,.65]},'firstperson_righthand':{'rotation':[0,-90,25],'translation':[1.1,2,0],'scale':[.65,.65,.65]}}}
(root/'models/item/chest_key.json').write_text(json.dumps(model,indent=2))
for code,name in [('ko_kr','상자 키'),('en_us','Chest Key')]:
 p=root/'lang'/f'{code}.json';data=json.loads(p.read_text());data['item.celestialfall.chest_key']=name;data['entity.celestialfall.field_loot']='필드 보급품' if code=='ko_kr' else 'Field Loot';p.write_text(json.dumps(data,ensure_ascii=False,indent=2))
print('Gold key cuboid model and translations written')
