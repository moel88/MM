"""Compact city traced in reference-image coordinates. Vanilla Java 1.20.1 world."""
from pathlib import Path
import json,math,time,copy
import numpy as np
from PIL import Image,ImageDraw,ImageFont
from scipy.ndimage import distance_transform_edt,gaussian_filter,label as components
import base_city as b

ROOT=b.ROOT;OUT=ROOT/'verification';OUT.mkdir(exist_ok=True)
b.W,b.D,b.OX,b.OZ=352,384,176,192
b.GROUND=80;b.V=np.zeros((b.H,b.D,b.W),np.uint16)
b.WORLD=ROOT/'Cliffside_City_Third_1.20.1';b.WORLD.mkdir(exist_ok=True)
V=b.V;Y=b.YMIN;n=b.n;G=80
rng=np.random.default_rng(16591013)
LANDMARKS=[];AREAS=[];BUILDINGS=[];ROAD_PATHS=[];ENTRIES=[];TREE_COUNT=0
SCALE_X=340/1216;SCALE_Z=383/1020
def px(x):return 5+round(x*SCALE_X)
def pz(z):return round(z*SCALE_Z)
def xyz(x,z,y=81):return px(x)-b.OX,y,pz(z)-b.OZ
def boxp(x1,y1,z1,x2,y2,z2,mat):b.box(px(x1),y1,pz(z1),px(x2),y2,pz(z2),mat)
def mark(name,x,z,y=81):
    xx,yy,zz=xyz(x,z,y);LANDMARKS.append({'name':name,'x':xx,'y':yy,'z':zz,'reference':[x,z]})
def area(name,rect,kind):
    x1,z1,x2,z2=rect;AREAS.append({'name':name,'kind':kind,'reference_bounds':list(rect),'block_bounds':[px(x1)-b.OX,pz(z1)-b.OZ,px(x2)-b.OX,pz(z2)-b.OZ]})
def sign(x,z,lines,y=81):
    xx,zz=px(x),pz(z);b.at(xx,y,zz,b.SIGN)
    messages=n.List[n.String]([n.String(json.dumps({'text':s},ensure_ascii=False)) for s in (lines+['']*4)[:4]])
    b.BE.append(n.Compound({'id':n.String('minecraft:sign'),'x':n.Int(xx-b.OX),'y':n.Int(y),'z':n.Int(zz-b.OZ),
        'front_text':n.Compound({'messages':messages,'color':n.String('black'),'has_glowing_text':n.Byte(0)}),
        'back_text':n.Compound({'messages':n.List[n.String]([n.String('{"text":""}')]*4),'color':n.String('black'),'has_glowing_text':n.Byte(0)}),'is_waxed':n.Byte(1)}))
def chest(x,z,items=None,y=81):
    xx,zz=px(x),pz(z);b.at(xx,y,zz,b.CHEST)
    if items is None:items=[('bread',12),('iron_ingot',8),('torch',16)]
    li=[n.Compound({'Slot':n.Byte(i),'id':n.String('minecraft:'+name),'Count':n.Byte(count)}) for i,(name,count) in enumerate(items)]
    b.BE.append(n.Compound({'id':n.String('minecraft:chest'),'x':n.Int(xx-b.OX),'y':n.Int(y),'z':n.Int(zz-b.OZ),'Items':n.List[n.Compound](li)}))

OUTLINE=[(211,43),(302,42),(410,18),(518,54),(583,40),(622,8),(728,20),(826,14),(948,18),(985,73),(1000,152),(995,238),(1037,306),(1050,376),(1060,454),(1097,489),(1099,606),(1135,693),(1140,797),(1181,851),(1204,930),(1190,1014),(1085,1018),(716,1018),(609,963),(498,945),(409,903),(389,777),(363,690),(348,582),(250,579),(176,583),(104,585),(13,544),(23,474),(14,363),(25,276),(36,207),(85,154),(145,117),(168,78)]
def terrain():
    im=Image.new('1',(b.W,b.D));ImageDraw.Draw(im).polygon([(px(x),pz(z)) for x,z in OUTLINE],fill=1)
    land=np.asarray(im).astype(bool);inside=distance_transform_edt(land);outside=distance_transform_edt(~land)
    z,x=np.indices(land.shape);rim=land&(inside<5)
    top=np.full(land.shape,48,np.int16);top[land]=G
    # Angular, high brown perimeter follows the image's craggy enclosing walls.
    cliff=91+((x//3*7+z//4*11)%7)+((x//5+z//6)%3)*2
    top[rim]=cliff[rim]
    beach=(~land)&(outside<2.4);top[beach]=62
    for y in range(Y,107):
        row=V[y-Y];row[y<=top]=b.STONE
        if y==Y:row[:]=b.BEDROCK;continue
        row[(y>top)&(y<=62)]=b.WATER
        row[(top<=62)&(y<=top)&(y>=top-2)]=b.SAND
        row[land&(y>=63)&(y<=top)]=[b.ROCK,b.ROCK,b.RUST,b.ROCK,b.DARKROCK][(y//4)%5]
        row[land&~rim&(y>=G-3)&(y<=G)]=b.DIRT
        row[land&~rim&(y==G)]=b.GRASS
    return top

def path_pixels(points,width,material,y=80,clear=False):
    mask=Image.new('1',(b.W,b.D));ImageDraw.Draw(mask).line([(px(x),pz(z)) for x,z in points],fill=1,width=width,joint='curve')
    m=np.asarray(mask).astype(bool);V[y-Y,m]=material
    if clear:V[y+1-Y:y+4-Y,m]=b.AIR
    return m
def road(points,width=5):
    path_pixels(points,width+4,b.PAVE);m=path_pixels(points,width,b.ASPHALT,clear=True)
    ROAD_PATHS.append(points)
    for (a,c),(d,e) in zip(points,points[1:]):
        start=np.array([px(a),pz(c)],float);end=np.array([px(d),pz(e)],float);length=np.linalg.norm(end-start)
        direction=(end-start)/max(length,1)
        for distance in range(4,int(length)-2,9):
            x,z=np.rint(start+direction*distance).astype(int)
            b.at(x,G,z,b.LIGHT)
            x,z=np.rint(start+direction*(distance+1)).astype(int);b.at(x,G,z,b.LIGHT)
def lamp(x,z):
    xx,zz=px(x),pz(z);b.box(xx,81,zz,xx,84,zz,b.IRON);b.at(xx,85,zz,b.SEA);b.at(xx,86,zz,b.SLAB)
def building(rect,floors=3,style=0,accent=None,name=None,front='south'):
    x1,z1,x2,z2=map(int,(px(rect[0]),pz(rect[1]),px(rect[2]),pz(rect[3])))
    assert x2-x1>=5 and z2-z1>=5
    top=G+floors*5;wall=[b.WHITE,b.LIGHT,b.QUARTZ,b.GRAY][style%4]
    b.box(x1-1,G,z1-1,x2+1,G,z2+1,b.PAVE)
    b.box(x1,81,z1,x2,top,z2,wall);b.box(x1+1,81,z1+1,x2-1,top-1,z2-1,b.AIR)
    for y in range(G,top,5):
        b.box(x1,y,z1,x2,y,z2,b.PAVE)
        for z in (z1,z2):
            for x in range(x1+2,x2-1,3):b.box(x,y+2,z,min(x+1,x2-1),y+4,z,b.GLASS if style%3!=1 else b.TINT)
        for x in (x1,x2):
            for z in range(z1+2,z2-1,3):b.box(x,y+2,z,x,y+4,min(z+1,z2-1),b.GLASS)
        b.at((x1+x2)//2,y+4,(z1+z2)//2,b.SEA)
        if x2-x1>9:b.box(x2-4,y+1,z1+2,x2-2,y+1,z1+3,b.WOODSLAB)
    # Continuous, full-size ladder shaft, with a solid backing on every floor.
    lx,lz=x1+2,z1+2
    b.box(lx,81,lz,lx,top,lz,b.LADDER);b.box(lx,81,lz-1,lx,top,lz-1,wall)
    b.box(x1,top,z1,x2,top,z2,b.LIGHT);b.at(lx,top,lz,b.LADDER)
    for z in (z1,z2):b.box(x1,top+1,z,x2,top+1,z,wall)
    for x in (x1,x2):b.box(x,top+1,z1,x,top+1,z2,wall)
    b.box(x2-4,top+1,z1+2,x2-2,top+2,z1+4,b.GRAY)
    if floors>=4:b.box(x2-2,top+2,z2-2,x2-2,top+5,z2-2,b.IRON)
    cx=(x1+x2)//2;door_z=z2 if front=='south' else z1
    b.box(cx-1,81,door_z-1,cx+1,83,door_z+1,b.AIR)
    b.box(cx-2,84,door_z-1,cx+2,84,door_z+1,accent or b.LIGHT)
    if front=='south':b.box(cx-1,G,door_z+1,cx+1,G,door_z+3,b.PAVE)
    else:b.box(cx-1,G,door_z-3,cx+1,G,door_z-1,b.PAVE)
    ENTRIES.append((cx,door_z,lx,lz,top))
    BUILDINGS.append({'reference_bounds':list(rect),'bounds':[x1-b.OX,z1-b.OZ,x2-b.OX,z2-b.OZ],'roof_y':top,'floors':floors,'name':name or 'City building'})
    if name:mark(name,(rect[0]+rect[2])//2,rect[3]+5 if front=='south' else rect[1]-5)

def tree(xx,zz,height=6,mat=None):
    global TREE_COUNT
    if V[G-Y,zz,xx]!=b.GRASS:return
    if np.any(V[81-Y:84-Y,zz,xx]!=b.AIR):return
    mat=mat or (b.SLEAF if rng.random()<.65 else b.LEAF)
    for dy,rad in [(-2,2),(-1,3),(0,2),(1,1)]:b.disk(xx,zz,rad,G+height+dy,mat)
    b.box(xx,81,zz,xx,G+height,zz,b.SLOG);TREE_COUNT+=1
def trees_in(rect,density=5,park=False):
    x1,z1,x2,z2=px(rect[0]),pz(rect[1]),px(rect[2]),pz(rect[3])
    b.box(x1,G,z1,x2,G,z2,b.GRASS)
    for z in range(z1+3,z2-2,density):
        for x in range(x1+3,x2-2,density):
            xx=x+int(rng.integers(-1,2));zz=z+int(rng.integers(-1,2))
            if park and (abs(xx-(x1+x2)//2)<3 or abs(zz-(z1+z2)//2)<3):continue
            tree(xx,zz,int(rng.integers(5,9)))
def park(rect,name):
    x1,z1,x2,z2=rect;area(name,rect,'park')
    boxp(x1-7,G,z1-7,x2+7,G,z2+7,b.PAVE)
    trees_in(rect,5,True)
    # Reference parks have dense tree belts and lawn, without the old large cross/fountain.
    path_pixels([(x1+9,z1+9),(x2-9,z1+9),(x2-9,z2-9),(x1+9,z2-9),(x1+9,z1+9)],2,b.PATH,clear=True)
    path_pixels([((x1+x2)//2,z1-8),((x1+x2)//2,z2+8)],2,b.GRASS,clear=True)
    mark(name,(x1+x2)//2,(z1+z2)//2)
def forest(rect,name):
    area(name,rect,'forest');trees_in(rect,4,False);mark(name,(rect[0]+rect[2])//2,(rect[1]+rect[3])//2)

def city():
    print('Reference-traced streets and parcels',flush=True)
    road([(177,277),(869,277)],5)
    road([(184,277),(164,474)],5)
    road([(394,145),(393,275),(377,475)],5)
    road([(591,278),(592,478)],5)
    road([(154,478),(891,478)],5)
    road([(868,280),(886,477),(932,777)],5)
    road([(460,481),(460,771)],5)
    road([(642,482),(642,774)],4)
    road([(455,776),(933,776)],5)
    for x,z in [(181,285),(390,282),(591,285),(865,284),(169,474),(375,475),(589,477),(880,477),(461,773),(642,773),(926,773)]:
        xx,zz=px(x),pz(z)
        # Small crosswalks preserve the reference's narrow road widths.
        for dx in (-2,0,2):b.box(xx+dx,G,zz-2,xx+dx,G,zz+2,b.LIGHT)
    # Trace the distinctive rectangular green areas without changing their relative positions.
    forest((178,149,383,265),'NORTHWEST FOREST')
    park((218,300,354,445),'WEST PARK')
    park((664,512,831,749),'SOUTH GARDENS')
    area('BATTLE FIELD',(626,325,834,442),'arena')
    boxp(611,G,311,850,G,459,b.PAVE)
    boxp(624,G,322,837,G,445,b.DARK)
    boxp(627,G,325,834,G,442,b.RED)
    boxp(632,G,331,828,G,437,b.LIGHT)
    for x in range(639,825,32):boxp(x,G,333,x,G,435,b.PAVE)
    mark('BATTLE FIELD',730,383)
    # Tight blocks and varied footprints copied from the visible map, not a regular tower grid.
    specs=[((429,109,482,165),3,1,None,None),((496,111,548,173),4,3,None,None),
      ((566,107,617,164),4,1,None,None),((634,99,681,149),5,0,None,None),
      ((695,111,744,170),4,2,None,None),((760,112,805,170),4,0,None,None),
      ((817,94,864,151),4,3,b.CYAN,None),((879,120,923,190),4,2,None,None),
      ((416,190,466,265),4,1,None,None),((482,188,533,248),3,1,b.YELLOW,'MARKET'),
      ((560,172,619,264),3,1,b.PINK,'AUDDY SHOP'),((633,180,676,257),4,2,None,None),
      ((691,192,743,267),4,0,None,None),((786,173,834,199),1,2,b.WHITE,'RESELL'),
      ((106,248,147,309),3,1,None,None),((87,331,139,427),4,2,None,None),
      ((414,296,487,341),3,1,None,None),((435,354,485,406),4,0,None,None),
      ((494,351,539,432),4,1,None,None),((548,385,577,465),3,1,None,None),
      ((391,435,462,467),2,1,None,None),((477,443,515,467),2,2,None,None),
      ((873,285,918,326),2,2,b.CYAN,'GUILD HALL'),((881,358,931,434),4,0,b.RED,None),
      ((169,488,207,525),1,3,b.RED,'LIBRARY'),((230,491,273,521),2,1,None,None),
      ((282,490,327,523),2,0,None,None),((393,520,446,579),3,1,None,None),
      ((407,601,447,659),3,1,b.PURPLE,'CAFE'),((423,684,449,735),3,0,None,None),
      ((470,809,519,856),2,1,None,None),((534,810,583,855),2,3,None,None),
      ((904,505,967,589),4,0,None,None),((929,639,982,684),3,1,None,None),
      ((942,710,996,755),3,2,None,None)]
    for rect,floors,style,accent,name in specs:building(rect,floors,style,accent,name)
    # East northern grove and the small blue landmark between arena and south park.
    trees_in((779,211,849,266),5,False)
    building((693,488,738,506),1,3,b.BLUE,'ANUBIS HALL',front='north')
    building((146,431,173,465),1,1,None,'SKIN REMOVER')
    area('PARKING',(488,499,619,760),'parking')
    boxp(480,G,491,629,G,766,b.PAVE);boxp(489,G,500,619,G,758,b.ASPHALT)
    for z in range(510,752,22):
        boxp(492,G,z,514,G,z,b.LIGHT);boxp(596,G,z,616,G,z,b.LIGHT)
    boxp(514,G,503,514,G,754,b.LIGHT);boxp(596,G,503,596,G,754,b.LIGHT)
    mark('PARKING',554,626)
    forest((627,786,1006,977),'SOUTHERN FOREST')
    path_pixels([(827,780),(827,825),(896,854)],2,b.PATH,clear=True)
    path_pixels([(827,825),(747,898),(650,916)],2,b.PATH,clear=True)
    path_pixels([(747,898),(747,983)],2,b.PATH,clear=True)
    shrine();cave();ritual()
    for x,z in [(202,291),(371,291),(599,291),(858,292),(360,460),(600,464),(849,463),(651,549),(852,633),(623,671),(477,748),(868,748),(606,790)]:lamp(x,z)
    for x,z in [(433,184),(178,272),(541,250),(811,250),(160,471),(289,475),(415,551),(672,493),(903,473),(963,764),(461,794)]:chest(x,z)
    # Spawn is between arena and its southern road, facing north into the arena.
    mark('SPAWN',728,462);sign(704,464,['CLIFFSIDE CITY','COMPACT 1/3','ARENA AHEAD'])
    chest(749,461,[('diamond_sword',1),('husk_spawn_egg',32),('golden_apple',32)])

def shrine():
    area('BOSS SHRINE',(881,835,937,881),'landmark')
    xx,zz=px(909),pz(857)
    b.box(xx-5,81,zz-6,xx+5,96,zz+6,b.AIR)
    b.box(xx-5,G,zz-6,xx+5,G,zz+6,b.OBSIDIAN)
    b.box(xx-3,G,zz-4,xx+3,G,zz+4,b.SEA)
    b.box(xx-3,81,zz-4,xx+3,81,zz+4,b.WATER)
    for dx in (-5,5):b.box(xx+dx,81,zz-6,xx+dx,85,zz+6,b.DARK)
    mark('BOSS SHRINE',909,857)
def cave():
    area('CAVE',(627,871,671,940),'landmark')
    xx,zz=px(649),pz(912)
    b.box(xx-6,81,zz-9,xx+3,94,zz+7,b.ROCK)
    # A full-width stair replaces the unwalkable steps a voxel downsample would create.
    for i in range(12):
        floor=80-min(i//2,4)
        b.box(xx+7-i,floor,zz-1,xx+7-i,floor,zz+1,b.BRICK)
        b.box(xx+7-i,floor+1,zz-1,xx+7-i,floor+4,zz+1,b.AIR)
    b.box(xx-5,76,zz-5,xx-2,81,zz+5,b.AIR);b.box(xx-5,75,zz-5,xx-2,75,zz+5,b.BRICK)
    b.at(xx-3,81,zz-4,b.SEA);b.at(xx-3,81,zz+4,b.SEA)
    mark('CAVE',676,912)
def ritual():
    area('GRAIL RUINS',(713,950,785,1009),'landmark')
    xx,zz=px(749),pz(984);b.box(xx-7,81,zz-7,xx+7,97,zz+7,b.AIR)
    b.disk(xx,zz,7,G,b.DARK);b.disk(xx,zz,5,G,b.AMETHYST,4);b.disk(xx,zz,2,81,b.STONE)
    b.at(xx,82,zz,b.GOLD);mark('GRAIL RUINS',749,984)

def check():
    # Clear windows of operation and preserve exact image-relative alignment after quantization.
    coordinates=set()
    for entity in b.BE:
        x,y,z=int(entity['x'])+b.OX,int(entity['y']),int(entity['z'])+b.OZ
        assert (x,y,z) not in coordinates,'Overlapping block entities';coordinates.add((x,y,z))
        material=b.CHEST if str(entity['id']).endswith('chest') else b.SIGN
        assert V[y-Y,z,x]==material
        assert V[y-Y-1,z,x] not in (b.AIR,b.WATER),('Unsupported fixture',x,y,z)
    spawn=next(p for p in LANDMARKS if p['name']=='SPAWN');x,z=spawn['x']+b.OX,spawn['z']+b.OZ
    assert np.all(V[81-Y:83-Y,z,x]==b.AIR) and V[G-Y,z,x] not in (b.AIR,b.WATER)
    assert np.all(V[81-Y:85-Y,pz(333):pz(435)+1,px(637):px(823)+1]==b.AIR),'Arena blocked'
    for cx,cz,lx,lz,top in ENTRIES:
        assert np.all(V[81-Y:84-Y,cz,cx-1:cx+2]==b.AIR),('Door blocked',cx,cz)
        assert np.all(V[81-Y:top-Y+1,lz,lx]==b.LADDER),('Ladder discontinuity',lx,lz)
        assert np.all(V[81-Y:top-Y+1,lz-1,lx]!=b.AIR),('Unsupported ladder',lx,lz)
    walk=(V[G-Y]!=b.AIR)&(V[G-Y]!=b.WATER)&(V[81-Y]==b.AIR)&(V[82-Y]==b.AIR)
    ids,count=components(walk);connected=ids[z,x];assert connected>0
    for cx,cz,*_ in ENTRIES:assert ids[cz,cx]==connected,('Unreachable building entrance',cx,cz)
    for path in ROAD_PATHS:
        for a,c in path:
            xx,zz=px(a),pz(c)
            assert ids[zz,xx]==connected,('Road endpoint not connected',a,c)
    errors=[]
    for parcel in AREAS:
        original=parcel['reference_bounds'];coords=parcel['block_bounds']
        errors.extend(abs(expected-actual) for expected,actual in zip([5+original[0]*SCALE_X-b.OX,original[1]*SCALE_Z-b.OZ,5+original[2]*SCALE_X-b.OX,original[3]*SCALE_Z-b.OZ],coords))
    return {'spawn_clear':True,'arena_clear':True,'block_entities_match':True,'building_doors_and_ladders_clear':True,'road_and_building_entries_connected_to_spawn':True,'reference_parcel_quantization_max_blocks':max(errors)}

def save_level():
    b.save_level();path=b.WORLD/'level.dat';file=n.load(path);data=file['Data']
    data['LevelName']=n.String('Cliffside City 1-3 | 절벽 도시 축소판')
    spawn=next(p for p in LANDMARKS if p['name']=='SPAWN')
    for key,axis in [('SpawnX','x'),('SpawnY','y'),('SpawnZ','z')]:data[key]=n.Int(spawn[axis])
    data['BorderCenterZ']=n.Double(0);file.save()
    fn=b.WORLD/'datapacks/cliffcity/data/cliffcity/functions'
    names={'spawn':'SPAWN','arena':'BATTLE FIELD','park':'SOUTH GARDENS','forest':'SOUTHERN FOREST'}
    # Safe forest waypoint is an open path, not the dense canopy's centre.
    safe_forest=xyz(827,809)
    for filename,name in names.items():
        p=next(p for p in LANDMARKS if p['name']==name);x,y,z=(p['x'],p['y'],p['z']) if filename!='forest' else safe_forest
        (fn/(filename+'.mcfunction')).write_text(f'tp @s {x} {y} {z} 180 0\n')
    (fn/'overview.mcfunction').write_text('tp @s 140 240 290 155 40\n')
    center=next(p for p in LANDMARKS if p['name']=='BATTLE FIELD');commands=[]
    for dx in (-18,-6,6,18):
        for dz in (-10,0,10):commands.append(f'summon minecraft:husk {center["x"]+dx} 81 {center["z"]+dz} '+ '{NoAI:1b,PersistenceRequired:1b,Silent:1b,Tags:["cliffcity_training"]}')
    (fn/'training.mcfunction').write_text('\n'.join(commands)+'\n')
    for filename in ['spawn','arena','park','forest']:
        parts=(fn/(filename+'.mcfunction')).read_text().split();x,y,z=map(int,parts[2:5]);xx,zz=x+b.OX,z+b.OZ
        assert np.all(V[y-Y:y+2-Y,zz,xx]==b.AIR),('Teleport obstructed',filename)

def previews():
    h=b.H-1-np.argmax((V!=b.AIR)[::-1],axis=0);z,x=np.indices(h.shape)
    rgb=np.array(b.colors,float)[V[h,z,x]]
    dz,dx=np.gradient(gaussian_filter(h.astype(float),.65));shade=np.clip(1+(dx*.25+dz*.35)/np.maximum(1,np.hypot(dx,dz)),.65,1.18)
    for step in range(2,45,2):
        shadow=np.zeros(h.shape,bool);shadow[step:,step:]=h[:-step,:-step]>h[step:,step:]+step*1.25
        shade[shadow]=np.minimum(shade[shadow],.68)
    im=Image.fromarray(np.uint8(np.clip(rgb*shade[...,None],0,255)))
    im.save(ROOT/'City_Third_Top_View.png');im.resize((64,64),Image.Resampling.LANCZOS).save(b.WORLD/'icon.png')
    np.savez_compressed(OUT/'surface.npz',heights=h.astype(np.int16)+Y,blocks=V,colors=np.array(b.colors,np.uint8),origin=np.array([-b.OX,-b.OZ]),compact=np.array([1]))
    zoom=3;mapim=im.resize((b.W*zoom,b.D*zoom),Image.Resampling.NEAREST)
    board=Image.new('RGB',(b.W*zoom+390,b.D*zoom),(17,27,34));board.paste(mapim,(0,0));d=ImageDraw.Draw(board)
    font='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf';title=ImageFont.truetype(font,24);body=ImageFont.truetype(font,17);small=ImageFont.truetype(font,14)
    right=b.W*zoom+26;d.text((right,30),'CLIFFSIDE CITY / 1:3',font=title,fill='#f2ead9')
    d.text((right,76),'341 x 384 block design',font=body,fill='#efc765');d.text((right,109),'Reference-traced compact version',font=small,fill='#91adb8')
    order=['SPAWN','BATTLE FIELD','NORTHWEST FOREST','WEST PARK','PARKING','SOUTH GARDENS','SOUTHERN FOREST','BOSS SHRINE','CAVE','GRAIL RUINS','AUDDY SHOP','RESELL','LIBRARY','CAFE','GUILD HALL']
    for i,name in enumerate(order,1):
        p=next(p for p in LANDMARKS if p['name']==name);xx=(p['x']+b.OX)*zoom;zz=(p['z']+b.OZ)*zoom
        d.ellipse((xx-12,zz-12,xx+12,zz+12),fill='#101c25',outline='#e9c065',width=2);d.text((xx,zz),str(i),font=small,fill='white',anchor='mm')
        y=165+(i-1)*56;d.text((right,y),f'{i:02}  {name}',font=body,fill='#e7eaea');d.text((right+31,y+23),f'X {p["x"]}  Y {p["y"]}  Z {p["z"]}',font=small,fill='#98b1bc')
    d.text((right,1040),'Layout: reference-image coordinates.',font=small,fill='#9cb5c0');d.text((right,1065),'Block preview, not a game screenshot.',font=small,fill='#9cb5c0')
    board.save(ROOT/'City_Third_Guide.png')
    # Coordinate plan is useful for auditing the screenshot reconstruction without
    # including the user's source image in the world or making a 100% visual claim.
    (OUT/'reference-layout.json').write_text(json.dumps({'source_image_size':[1536,1020],'map_crop':[0,0,1216,1020],'outline':OUTLINE,'areas':AREAS,'buildings':BUILDINGS,'landmarks':LANDMARKS},indent=2))

if __name__=='__main__':
    start=time.time();terrain();city();checks=check();save_level();previews();chunks,regions=b.save_regions()
    manifest={'name':'Cliffside City Compact 1-3','world_folder':b.WORLD.name,'minecraft':'Java 1.20.1','data_version':3465,
      'dimensions':[341,384],'chunk_extent':[b.W,b.D],'bounds':[-b.OX,-b.OZ,b.W-b.OX-1,b.D-b.OZ-1],
      'city_ground_y':G,'sea_y':62,'chunks':chunks,'regions':regions,'buildings':len(BUILDINGS),'trees':TREE_COUNT,'block_states':len(b.palette),'block_entities':len(b.BE),
      'landmarks':LANDMARKS,'building_details':BUILDINGS,'checks':checks,'generation_seconds':round(time.time()-start,1)}
    (OUT/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    (OUT/'palette.json').write_text(json.dumps([{'name':str(p['Name']),'properties':{k:str(v) for k,v in p.get('Properties',{}).items()}} for p in b.palette],indent=2))
    print(json.dumps({k:v for k,v in manifest.items() if k not in ('landmarks','building_details')},indent=2),flush=True)
