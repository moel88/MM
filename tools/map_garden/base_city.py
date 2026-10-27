#!/usr/bin/env python3
"""Deterministic Java 1.20.1 Anvil world. Reference layout rebuilt with vanilla blocks."""
from pathlib import Path
import io, json, math, struct, time, zlib, hashlib
import numpy as np
from PIL import Image, ImageDraw
from scipy.ndimage import distance_transform_edt, gaussian_filter
import nbtlib as n

ROOT=Path(__file__).resolve().parents[2]/'build/map-garden';ROOT.mkdir(parents=True,exist_ok=True)
(ROOT/'verification').mkdir(parents=True,exist_ok=True)
WORLD=ROOT/'Cliffside_City_1.20.1'
WORLD.mkdir(exist_ok=True)
W,D,H,YMIN=352,384,272,-64
GROUND=96
OX=512;OZ=512
rng=np.random.default_rng(1659)
palette=[]; ids={}; colors=[]
def block(name,color,**props):
    key=(name,tuple(sorted(props.items())))
    if key in ids:return ids[key]
    idx=len(palette);ids[key]=idx
    c=n.Compound({'Name':n.String('minecraft:'+name)})
    if props:c['Properties']=n.Compound({k:n.String(str(v)) for k,v in props.items()})
    palette.append(c);colors.append(tuple(int(color[i:i+2],16) for i in (0,2,4)))
    return idx
AIR=block('air','99c9e0');STONE=block('stone','777b7c');BEDROCK=block('bedrock','35393c')
WATER=block('water','207da0',level='0');SAND=block('sand','c9bd91');GRAVEL=block('gravel','8b8a86')
DIRT=block('dirt','77603e');GRASS=block('grass_block','508445',snowy='false')
ROCK=block('brown_terracotta','704731');RUST=block('terracotta','9d664c');DARKROCK=block('gray_terracotta','514139')
ASPHALT=block('black_concrete','202931');WHITE=block('white_concrete','d5dcdd');GRAY=block('gray_concrete','59616a')
LIGHT=block('light_gray_concrete','929ea2');PAVE=block('smooth_stone','a0a4a2');BRICK=block('stone_bricks','8b9495')
DARK=block('polished_deepslate','434952');QUARTZ=block('smooth_quartz','e3e1d5');RED=block('red_concrete','a52c39')
YELLOW=block('yellow_concrete','e4b638');CYAN=block('cyan_concrete','347f95');BLUE=block('blue_concrete','3c4f9a')
PINK=block('magenta_concrete','a14598');PURPLE=block('purple_concrete','694897');GOLD=block('gold_block','e6bf51')
GLASS=block('light_blue_stained_glass','83b9c5');TINT=block('gray_stained_glass','596974')
SEA=block('sea_lantern','b4e9e0');GLOW=block('glowstone','d6ac67');IRON=block('iron_block','bbc5c7')
WOOD=block('spruce_planks','775337');OAK=block('oak_planks','b18f57');BOOKS=block('bookshelf','8a7251')
LOG=block('oak_log','766049',axis='y');SLOG=block('spruce_log','5e4836',axis='y')
LEAF=block('oak_leaves','467043',persistent='true',distance='1',waterlogged='false')
SLEAF=block('spruce_leaves','315643',persistent='true',distance='1',waterlogged='false')
BIRCH=block('birch_leaves','64884d',persistent='true',distance='1',waterlogged='false')
HEDGE=block('azalea_leaves','4d7645',persistent='true',distance='1',waterlogged='false')
MOSS=block('moss_block','567a41');PATH=block('dirt_path','9c8551');MUD=block('packed_mud','8a7150')
FENCE=block('dark_oak_fence','544432',east='false',west='false',north='false',south='false',waterlogged='false')
LANTERN=block('lantern','e7b351',hanging='false',waterlogged='false')
CHAIN=block('chain','57616c',axis='y',waterlogged='false')
SLAB=block('smooth_stone_slab','b0b3b0',type='bottom',waterlogged='false')
WOODSLAB=block('spruce_slab','896542',type='bottom',waterlogged='false')
STAIR=block('quartz_stairs','dadbd6',facing='north',half='bottom',shape='straight',waterlogged='false')
LADDER=block('ladder','b29359',facing='south',waterlogged='false')
CHEST=block('chest','a97a3e',facing='south',type='single',waterlogged='false')
SIGN=block('oak_sign','b59a66',rotation='0',waterlogged='false')
REDSTONE=block('redstone_block','a5362d');EMERALD=block('emerald_block','2daa78')
AMETHYST=block('amethyst_block','8863a6');OBSIDIAN=block('obsidian','282638')
FLOWER=block('poppy','ce4544');BLUEFLOWER=block('cornflower','6476bf')
CARPET=block('red_carpet','a02e35');COBBLE=block('cobblestone','707a76')
V=np.zeros((H,D,W),np.uint16)
BE=[]; landmarks=[]; buildings=[]; roads=[]; tree_count=0
def box(x1,y1,z1,x2,y2,z2,b):
    x1,x2=max(0,int(x1)),min(W-1,int(x2));z1,z2=max(0,int(z1)),min(D-1,int(z2))
    y1,y2=max(YMIN,int(y1)),min(YMIN+H-1,int(y2))
    if x2>=x1 and z2>=z1 and y2>=y1:V[y1-YMIN:y2-YMIN+1,z1:z2+1,x1:x2+1]=b
def at(x,y,z,b):box(x,y,z,x,y,z,b)
def disk(x,z,r,y,b,inner=0):
    x1,x2=max(0,x-r),min(W-1,x+r);z1,z2=max(0,z-r),min(D-1,z+r)
    zz,xx=np.ogrid[z1:z2+1,x1:x2+1];m=((xx-x)**2+(zz-z)**2<=r*r)&((xx-x)**2+(zz-z)**2>=inner*inner)
    V[y-YMIN,z1:z2+1,x1:x2+1][m]=b
def label(name,x,z,y=GROUND+1):
    landmarks.append({'name':name,'x':x-512,'y':y,'z':z-512})
def sign(x,z,lines,y=97):
    at(x,y,z,SIGN)
    messages=[n.String(json.dumps({'text':t},ensure_ascii=False)) for t in (lines+['']*4)[:4]]
    BE.append(n.Compound({'id':n.String('minecraft:sign'),'x':n.Int(x-512),'y':n.Int(y),'z':n.Int(z-512),
       'front_text':n.Compound({'messages':n.List[n.String](messages),'color':n.String('black'),'has_glowing_text':n.Byte(0)}),
       'back_text':n.Compound({'messages':n.List[n.String]([n.String('{"text":""}')]*4),'color':n.String('black'),'has_glowing_text':n.Byte(0)}),'is_waxed':n.Byte(1)}))
def chest(x,z,items,y=97):
    at(x,y,z,CHEST);li=[]
    for slot,(name,count) in enumerate(items):li.append(n.Compound({'Slot':n.Byte(slot),'id':n.String('minecraft:'+name),'Count':n.Byte(count)}))
    BE.append(n.Compound({'id':n.String('minecraft:chest'),'x':n.Int(x-512),'y':n.Int(y),'z':n.Int(z-512),'Items':n.List[n.Compound](li)}))

def terrain():
    print('Terrain / ocean / cliffs',flush=True)
    im=Image.new('1',(W,D));draw=ImageDraw.Draw(im)
    draw.polygon([(156,67),(745,65),(837,99),(872,203),(881,288),(916,380),(917,485),(946,551),(962,735),(959,868),(1002,1057),(979,1120),(534,1115),(470,1051),(402,1024),(369,889),(302,836),(296,608),(125,606),(62,571),(38,482),(45,307),(70,226),(111,151)],fill=1)
    inside=np.asarray(im).astype(bool);si=distance_transform_edt(inside);so=distance_transform_edt(~inside)
    zz,xx=np.indices((D,W));noise=7*np.sin(xx*.061+np.sin(zz*.036))+5*np.sin(zz*.079+np.cos(xx*.027))+3*np.sin((xx+zz)*.13)
    dist=si-so+noise
    top=np.full((D,W),48,np.int16)
    shore=(dist>-12)&(dist<2);top[shore]=(49+(dist[shore]+12)*1.2).astype(np.int16)
    land=dist>=2;top[land]=96
    rim=land&(dist<26)
    crag=(103+10*np.sin(xx*.086)+9*np.cos(zz*.072)+5*np.sin((xx-zz)*.093)).astype(np.int16)
    top[rim]=np.maximum(100,crag[rim])
    # The external cliff face rises from the sea; inner city stays level and walkable.
    for y in range(YMIN,137):
        row=V[y-YMIN];row[y<=top]=STONE
        if y==YMIN:row[:]=BEDROCK;continue
        row[(y>top)&(y<=62)]=WATER
        row[(top<=66)&(y<=top)&(y>=top-2)]=SAND
        row[land&(y<=top)&(y>=70)]=[ROCK,RUST,ROCK,DARKROCK,ROCK][(y//5)%5]
        row[land&~rim&(y>=93)&(y<=96)]=DIRT
        row[land&~rim&(y==96)]=GRASS
        row[rim&(y==top)]=ROCK
    return top

def street(x1,z1,x2,z2,width=14):
    roads.append((x1,z1,x2,z2,width))
    if x1==x2:
        box(x1-width//2-5,96,z1,x2+width//2+5,96,z2,PAVE)
        box(x1-width//2,96,z1,x2+width//2,96,z2,ASPHALT)
        for z in range(z1+4,z2-3,14):box(x1,96,z,x1,96,z+6,YELLOW)
        for x in [x1-width//2-4,x1+width//2+4]:
            for z in range(z1+17,z2-8,42):lamp(x,z)
    else:
        box(x1,96,z1-width//2-5,x2,96,z2+width//2+5,PAVE)
        box(x1,96,z1-width//2,x2,96,z2+width//2,ASPHALT)
        for x in range(x1+4,x2-3,14):box(x,96,z1,x+6,96,z1,YELLOW)
        for z in [z1-width//2-4,z1+width//2+4]:
            for x in range(x1+17,x2-8,42):lamp(x,z)
def lamp(x,z):
    at(x,97,z,DARK);box(x,98,z,x,102,z,IRON);box(x-1,103,z,x+1,103,z,SEA);box(x-1,104,z,x+1,104,z,SLAB)
def crossing(x,z):
    box(x-8,96,z-8,x+8,96,z+8,ASPHALT)
    for i in range(-6,7,3):
        for d in [-12,12]:box(x+i,96,z+d-2,x+i+1,96,z+d+2,WHITE);box(x+d-2,96,z+i,x+d+2,96,z+i+1,WHITE)
    box(x+11,97,z+11,x+11,103,z+11,IRON);at(x+11,104,z+11,REDSTONE);at(x+11,105,z+11,DARK)

def building(x1,z1,x2,z2,floors,style=0,accent=CYAN,name=None,front='south'):
    base=97;top=base+floors*7
    wall=[WHITE,LIGHT,QUARTZ,GRAY][style%4]
    box(x1-3,96,z1-3,x2+3,96,z2+3,PAVE)
    box(x1,base,z1,x2,top,z2,wall);box(x1+2,base,z1+2,x2-2,top-1,z2-2,AIR)
    for y in range(base,top,7):
        box(x1+1,y,z1+1,x2-1,y,z2-1,PAVE if y>base else QUARTZ)
        # Lighted entrance halls and accessible stories.
        for x in range(x1+5,x2-3,9):
            for z in range(z1+5,z2-3,9):at(x,y+6,z,SEA)
        for z in (z1,z2):
            box(x1+2,y+2,z,x2-2,y+5,z,GLASS if style%2==0 else TINT)
            for x in range(x1+2,x2,6):box(x,y+1,z,x,y+6,z,wall)
        for x in (x1,x2):
            box(x,y+2,z1+2,x,y+5,z2-2,GLASS)
            for z in range(z1+2,z2,6):box(x,y+1,z,x,y+6,z,wall)
        if y>base:box(x1-1,y,z1-1,x2+1,y,z2+1,LIGHT);box(x1+1,y,z1+1,x2-1,y,z2-1,PAVE)
        box(x1+4,y+1,z1+4,x1+10,y+1,z1+5,WOODSLAB)
        box(x2-7,y+1,z2-6,x2-4,y+1,z2-4,OAK)
        at(x2-5,y+2,z2-5,LANTERN)
    box(x1+3,base+1,z1+2,x1+3,top,z1+2,STONE)
    box(x1+3,base+1,z1+3,x1+3,top,z1+3,LADDER)
    for y in range(base+7,top+1,7):at(x1+3,y,z1+3,LADDER)
    box(x1-1,top,z1-1,x2+1,top,z2+1,LIGHT)
    box(x1,top+1,z1,x2,top+1,z1,wall);box(x1,top+1,z2,x2,top+1,z2,wall)
    box(x1,top+1,z1,x1,top+1,z2,wall);box(x2,top+1,z1,x2,top+1,z2,wall)
    at(x1+3,top,z1+3,LADDER)
    # Rooftop services break up repeated masses.
    box(x1+6,top+1,z1+6,x1+14,top+4,z1+13,GRAY)
    box(x1+5,top+5,z1+5,x1+15,top+5,z1+14,SLAB)
    for x in range(x2-12,x2-3,4):box(x,top+1,z2-9,x+2,top+2,z2-5,IRON)
    if floors>=6:box(x2-5,top+1,z1+5,x2-5,top+10,z1+5,IRON);at(x2-5,top+11,z1+5,REDSTONE)
    cx=(x1+x2)//2;fz=z2 if front=='south' else z1
    box(cx-2,base+1,fz-1,cx+2,base+4,fz+1,AIR)
    box(cx-6,base+5,fz-2,cx+6,base+6,fz+2,accent)
    box(cx-5,base+5,fz-3,cx+5,base+5,fz-3,SEA)
    if name:
        label(name,cx,fz+5 if front=='south' else fz-5)
        sign(cx+7,fz+3 if front=='south' else fz-3,[name,'CLIFFSIDE CITY'])
    buildings.append({'bounds':[x1-512,z1-512,x2-512,z2-512],'floors':floors,'roof_y':top,'name':name or 'City building'})
    return top

def tree(x,z,height=11,spruce=False):
    global tree_count
    # Road and furnished park paths retain clear walking room.
    if V[96-YMIN,z,x] not in (GRASS,MOSS,DIRT):return
    log=SLOG if spruce else LOG;leaf=SLEAF if spruce else (BIRCH if rng.random()<.2 else LEAF)
    if spruce:
        for y in range(100,97+height):
            r=max(1,int((97+height-y)/3)+(1 if y%3==0 else 0))
            disk(x,z,r,y,leaf)
    else:
        for dy,r in [(-3,3),(-2,5),(-1,5),(0,4),(1,3),(2,1)]:disk(x,z,r,97+height+dy,leaf)
    box(x,97,z,x,97+height,z,log);tree_count+=1
def bench(x,z):
    box(x,97,z,x+4,97,z,WOODSLAB);box(x,98,z+1,x+4,98,z+1,WOODSLAB)
    at(x,97,z+1,DARK);at(x+4,97,z+1,DARK)
def fountain(x,z,r=9):
    for y in (96,97):disk(x,z,r,y,QUARTZ)
    disk(x,z,r-2,97,WATER);disk(x,z,r-1,96,SEA)
    box(x-1,97,z-1,x+1,100,z+1,QUARTZ);at(x,101,z,WATER)
def park(x1,z1,x2,z2,name,dense=False):
    box(x1-3,96,z1-3,x2+3,96,z2+3,PAVE);box(x1,96,z1,x2,96,z2,GRASS)
    for x in (x1,x2):box(x,97,z1,x,98,z2,HEDGE)
    for z in (z1,z2):box(x1,97,z,x2,98,z,HEDGE)
    cx=(x1+x2)//2;cz=(z1+z2)//2
    for y in (96,):box(cx-3,y,z1-4,cx+3,y,z2+4,PAVE);box(x1-4,y,cz-3,x2+4,y,cz+3,PAVE)
    box(cx-4,97,z1-1,cx+4,99,z1+1,AIR);box(cx-4,97,z2-1,cx+4,99,z2+1,AIR)
    box(x1-1,97,cz-4,x1+1,99,cz+4,AIR);box(x2-1,97,cz-4,x2+1,99,cz+4,AIR)
    fountain(cx,cz,11)
    for z in range(z1+13,z2-9,18 if dense else 23):
        for x in range(x1+13,x2-9,18 if dense else 24):
            if abs(x-cx)<16 or abs(z-cz)<15:continue
            tree(x+int(rng.integers(-3,4)),z+int(rng.integers(-3,4)),int(rng.integers(8,15)))
    for z in range(z1+18,z2-12,40):bench(cx+7,z);lamp(cx-6,z)
    for x in range(x1+15,x2-12,35):
        box(x,96,z1+7,x+6,96,z1+9,DIRT)
        for xx in range(x,x+7,2):at(xx,97,z1+8,FLOWER)
    label(name,cx,cz);sign(cx+6,z1+3,[name,'PUBLIC GARDEN'])
def forest(x1,z1,x2,z2,name):
    box(x1,96,z1,x2,96,z2,GRASS)
    for z in range(z1+10,z2-8,13):
        for x in range(x1+10,x2-8,14):
            tree(x+int(rng.integers(-3,4)),z+int(rng.integers(-3,4)),int(rng.integers(12,23)),rng.random()<.6)
    label(name,(x1+x2)//2,(z1+z2)//2)
def trail(points,width=4):
    for (x1,z1),(x2,z2) in zip(points,points[1:]):
        steps=max(abs(x2-x1),abs(z2-z1))+1
        for x,z in zip(np.linspace(x1,x2,steps).astype(int),np.linspace(z1,z2,steps).astype(int)):
            box(x-width//2,96,z-width//2,x+width//2,96,z+width//2,PATH)
            box(x-width//2,97,z-width//2,x+width//2,101,z+width//2,AIR)

def arena():
    print('Arena / streets / buildings',flush=True)
    box(579,96,330,816,96,487,PAVE)
    box(590,97,343,805,97,471,DARK)
    box(594,97,347,801,97,467,RED)
    box(598,97,351,797,97,463,LIGHT)
    # Fine paving joints, a restrained centre sigil and lighting in the perimeter.
    for x in range(602,798,16):box(x,97,351,x,97,463,PAVE)
    for z in range(351,464,16):box(598,97,z,797,97,z,PAVE)
    disk(698,407,22,97,WHITE,20);disk(698,407,10,97,GRAY,9)
    for x in range(602,800,18):at(x,97,345,SEA);at(x,97,469,SEA)
    for z in range(354,465,18):at(592,97,z,SEA);at(803,97,z,SEA)
    # Open entrances, low spectator seating outside the combat surface.
    for z in (336,477):
        for x in range(610,794,26):bench(x,z)
    for x in (584,810):
        for z in (337,480):
            box(x,97,z,x+2,105,z+2,QUARTZ);box(x-1,106,z-1,x+3,106,z+3,SEA)
            box(x+1,107,z+1,x+1,111,z+1,IRON);box(x+2,108,z+1,x+6,110,z+1,RED)
    box(692,97,472,704,97,487,SLAB)
    label('BATTLE FIELD',698,407,98);sign(705,488,['BATTLE FIELD','OPEN TRAINING ARENA','200 x 112 blocks'])

def parking():
    box(461,96,546,587,96,841,PAVE);box(469,96,553,579,96,834,ASPHALT)
    for z in range(561,827,15):
        box(472,96,z,497,96,z,WHITE);box(550,96,z,576,96,z,WHITE)
    for x in (497,550):box(x,96,559,x,96,827,WHITE)
    for z in range(570,823,32):box(523,96,z,523,96,z+9,YELLOW)
    for x in (464,584):
        for z in range(563,836,48):lamp(x,z)
    for z,c in [(571,WHITE),(630,BLUE),(719,RED),(779,LIGHT)]:car(480,z,c)
    for z,c in [(602,GRAY),(750,WHITE)]:car(554,z,c)
    label('PARKING',525,692);sign(565,549,['PARKING','CITY WEST'])
def car(x,z,c):
    box(x,97,z,x+11,99,z+5,c);box(x+3,100,z+1,x+8,101,z+4,GLASS);box(x+4,102,z+1,x+7,102,z+4,c)
    for xx in (x+2,x+9):
        for zz in (z-1,z+6):box(xx,97,zz,xx+1,98,zz,DARK)
    at(x,98,z+1,SEA);at(x,98,z+4,SEA);at(x+11,98,z+1,REDSTONE);at(x+11,98,z+4,REDSTONE)

def city():
    forest(156,147,342,284,'NORTHWEST FOREST')
    trail([(251,294),(251,234),(190,208),(183,159)],5)
    for args in [(144,311,839,311),(144,516,838,516),(144,311,144,516),(365,140,365,516),(565,311,565,516),(837,214,837,861),(432,516,432,861),(603,516,603,861),(432,861,916,861)]:street(*args)
    for x,z in [(144,311),(144,516),(365,311),(365,516),(565,311),(565,516),(837,311),(837,516),(432,516),(603,516),(432,861),(603,861),(837,861)]:crossing(x,z)
    park(180,347,326,480,'WEST PARK')
    park(630,552,795,820,'SOUTH GARDENS',True)
    arena();parking()
    # North district: separate footprints, varied heights, consistent street faces.
    specs=[(386,126,429,188,5,0,CYAN,'NORTH RESIDENCE'),(445,118,493,178,7,1,RED,None),
      (514,119,559,177,6,2,YELLOW,None),(581,113,625,183,9,0,CYAN,None),
      (648,122,702,178,7,2,CYAN,None),(724,126,780,179,8,3,CYAN,None),
      (387,216,437,290,5,1,YELLOW,None),(453,217,502,290,4,2,YELLOW,'SKYLINE MARKET'),
      (521,221,559,291,3,0,PINK,'AUDDY SHOP'),(581,214,625,290,5,1,CYAN,None),
      (650,225,699,286,4,2,CYAN,None),(739,240,798,292,3,0,GOLD,'RESALE STORE'),
      (758,194,797,224,2,2,WHITE,None),
      (90,333,120,394,5,0,CYAN,None),(87,415,119,469,4,1,CYAN,None),
      (383,347,434,409,6,0,CYAN,None),(452,341,488,386,7,1,CYAN,None),(502,347,542,412,5,2,CYAN,None),
      (385,435,431,485,3,2,PURPLE,None),(454,421,490,485,4,0,CYAN,None),(507,436,541,485,3,1,CYAN,None),
      (848,334,880,394,6,2,CYAN,'GUILD HALL'),(850,423,889,481,6,0,RED,None),
      (169,547,232,580,2,3,RED,'LIBRARY'),(254,546,288,578,3,1,YELLOW,None),
      (369,553,409,603,4,2,YELLOW,'APARTMENTS'),(370,635,409,685,2,3,PURPLE,'CAFE'),
      (375,719,410,770,4,0,CYAN,None),(377,794,410,836,3,1,CYAN,None),
      (861,558,917,624,6,0,CYAN,None),(862,650,920,720,5,1,WHITE,None),(866,747,924,815,4,2,CYAN,None)]
    for args in specs:building(*args)
    # Functional ground-floor interiors for the named shops.
    for x in range(175,230,8):box(x,98,550,x+4,101,552,BOOKS)
    for x in range(175,230,8):box(x,98,560,x+4,101,562,BOOKS)
    for x in range(527,556,8):chest(x,226,[('iron_ingot',16),('bread',16),('torch',32)],98)
    for x in range(378,406,9):
        for z in range(643,677,11):at(x,98,z,WOOD);at(x,99,z,WOODSLAB);bench(x-2,z+2)
    # Small quest-like landmark building south of the arena, purely a building.
    building(668,531,715,542,1,3,BLUE,'ANUBIS HALL',front='north')
    # Connecting paths to north back entrances and forest gate.
    box(370,96,198,802,96,207,PAVE);box(706,96,185,714,96,292,PAVE)
    for x in range(395,808,49):lamp(x,202)
    for x,z in [(347,170),(349,280),(345,440),(638,295),(728,289),(814,492),(810,703)]:
        box(x-4,96,z-4,x+4,96,z+4,DIRT);tree(x,z,10)
    forest(567,889,919,1084,'SOUTHERN FOREST')
    trail([(699,879),(699,925),(780,956),(822,962)],6)
    trail([(699,925),(603,980),(550,982)],5)
    trail([(699,925),(699,1010),(664,1062)],5)
    shrine(826,965);ritual(662,1065);cave(551,984)
    # Clear arena forecourt and exact spawn; no dependency on the mod to load the map.
    box(677,96,488,720,96,502,QUARTZ)
    label('SPAWN',698,496,97)
    sign(691,495,['CLIFFSIDE CITY','1.20.1 / JAVA','ARENA AHEAD','WELCOME'])
    chest(685,494,[('diamond_sword',1),('bow',1),('arrow',64),('zombie_spawn_egg',32),('husk_spawn_egg',32),('golden_apple',32),('carved_pumpkin',16)])
    chest(685,497,[('spyglass',1),('elytra',1),('firework_rocket',64)])

def shrine(x,z):
    box(x-22,97,z-22,x+22,130,z+22,AIR)
    disk(x,z,22,96,DARK);disk(x,z,19,96,QUARTZ)
    disk(x,z,12,96,SEA);disk(x,z,12,97,WATER);disk(x,z,15,97,OBSIDIAN,13)
    for dx,dz in [(-18,-18),(-18,18),(18,-18),(18,18)]:
        box(x+dx,97,z+dz,x+dx+2,108,z+dz+2,DARK);box(x+dx-1,109,z+dz-1,x+dx+3,109,z+dz+3,SEA)
    label('BOSS SHRINE',x,z);sign(x-9,z+20,['BOSS SHRINE','DECORATIVE LANDMARK'])
def ritual(x,z):
    box(x-24,97,z-20,x+24,130,z+20,AIR)
    disk(x,z,22,96,DARK);disk(x,z,19,96,GOLD,18);disk(x,z,12,96,AMETHYST,10)
    for t in np.linspace(0,2*np.pi,9)[:-1]:
        xx,zz=x+int(19*np.cos(t)),z+int(19*np.sin(t));box(xx,97,zz,xx,101,zz,DARK);at(xx,102,zz,SEA)
    disk(x,z,4,97,OBSIDIAN);at(x,98,z,GOLD);label('GRAIL RUINS',x,z)
def cave(x,z):
    # Freestanding rocky outcrop with an open, traversable tunnel and underground chamber.
    for y in range(97,121):
        rr=int(26*math.sqrt(max(0,1-((y-96)/25)**2)))
        disk(x-7,z,rr,y,ROCK)
    for i in range(30):
        xx=x+22-i;floor=96-max(0,i-7)//2
        box(xx,floor,z-3,xx,floor,z+3,BRICK);box(xx,floor+1,z-3,xx,floor+6,z+3,AIR)
        if i%7==0:at(xx,floor+5,z-3,SEA)
    box(x-24,81,z-12,x-7,89,z+12,AIR);box(x-24,80,z-12,x-7,80,z+12,BRICK)
    for xx in (x-22,x-10):
        for zz in (z-10,z+10):at(xx,88,zz,SEA)
    chest(x-20,z,[('amethyst_shard',24),('gold_ingot',12),('diamond',3)],81)
    label('CAVE',x+22,z);sign(x+24,z+5,['CAVE','FOLLOW THE LIGHTS'])

def pack(values,bits):
    values=np.asarray(values,np.uint64).ravel();per=64//bits
    padded=np.zeros(((len(values)+per-1)//per)*per,np.uint64);padded[:len(values)]=values
    q=padded.reshape(-1,per);shift=np.arange(per,dtype=np.uint64)*np.uint64(bits)
    return np.bitwise_or.reduce(q<<shift,axis=1).view(np.int64)
def nbtbytes(c):
    buf=io.BytesIO();n.File(c).write(buf);return buf.getvalue()
def chunk(cx,cz,blocks,entities):
    sections=[]
    for sy in range(-4,20):
        offset=sy*16-YMIN
        if offset<H:
            s=blocks[offset:min(offset+16,H)]
            vals,inv=np.unique(s,return_inverse=True)
        else:vals=np.array([0]);inv=None
        bs=n.Compound({'palette':n.List[n.Compound]([palette[int(v)] for v in vals])})
        if len(vals)>1:bs['data']=n.LongArray(pack(inv,max(4,math.ceil(math.log2(len(vals))))))
        sections.append(n.Compound({'Y':n.Byte(sy),'block_states':bs,'biomes':n.Compound({'palette':n.List[n.String]([n.String('minecraft:plains')])})}))
    top=H-np.argmax((blocks!=AIR)[::-1],axis=0)
    solid=(blocks!=AIR)&(blocks!=WATER)
    ocean=H-np.argmax(solid[::-1],axis=0)
    hm=n.Compound({k:n.LongArray(pack(arr,9)) for k,arr in [('WORLD_SURFACE',top),('MOTION_BLOCKING',top),('OCEAN_FLOOR',ocean)]})
    return n.Compound({'DataVersion':n.Int(3465),'xPos':n.Int(cx),'zPos':n.Int(cz),'yPos':n.Int(-4),
        'Status':n.String('minecraft:full'),'LastUpdate':n.Long(0),'InhabitedTime':n.Long(0),'isLightOn':n.Byte(0),
        'sections':n.List[n.Compound](sections),'block_entities':n.List[n.Compound](entities),'Heightmaps':hm,
        'structures':n.Compound({'starts':n.Compound({}),'References':n.Compound({})}),
        'block_ticks':n.List[n.Compound]([]),'fluid_ticks':n.List[n.Compound]([]),
        'PostProcessing':n.List[n.List[n.Short]]([n.List[n.Short]([]) for _ in range(24)])})
def save_regions():
    out=WORLD/'region';out.mkdir(exist_ok=True);groups={}
    for e in BE:groups.setdefault((int(e['x'])//16,int(e['z'])//16),[]).append(e)
    regions={};count=0
    for iz in range(D//16):
        for ix in range(W//16):
            cx,cz=ix-OX//16,iz-OZ//16;reg=(cx//32,cz//32);idx=(cx%32)+(cz%32)*32
            data=zlib.compress(nbtbytes(chunk(cx,cz,V[:,iz*16:iz*16+16,ix*16:ix*16+16],groups.get((cx,cz),[]))),6)
            regions.setdefault(reg,{})[idx]=data;count+=1
        if iz%12==0:print('Chunks',count,'/',W*D//256,flush=True)
    for (rx,rz),chunks in regions.items():
        header=bytearray(8192);body=bytearray();sector=2
        for idx,data in sorted(chunks.items()):
            length=len(data)+1;num=(length+4+4095)//4096
            header[idx*4:idx*4+4]=((sector<<8)|num).to_bytes(4,'big')
            header[4096+idx*4:4100+idx*4]=int(time.time()).to_bytes(4,'big')
            record=struct.pack('>I',length)+b'\x02'+data
            body+=record+b'\0'*(num*4096-len(record));sector+=num
        with (out/f'r.{rx}.{rz}.mca').open('wb') as f:
            f.write(header+body);f.flush();__import__('os').fsync(f.fileno())
    return count,len(regions)
def save_level():
    layers=[(1,'bedrock'),(106,'stone'),(4,'gravel'),(2,'sand'),(14,'water')]
    overworld=n.Compound({'type':n.String('minecraft:overworld'),'generator':n.Compound({'type':n.String('minecraft:flat'),
        'settings':n.Compound({'biome':n.String('minecraft:deep_ocean'),'lakes':n.Byte(0),'features':n.Byte(0),
        'structure_overrides':n.List[n.String]([]),'layers':n.List[n.Compound]([n.Compound({'height':n.Int(h),'block':n.String('minecraft:'+b)}) for h,b in layers])})})})
    # Default alternate dimensions, untouched until visited.
    nether=n.Compound({'type':n.String('minecraft:the_nether'),'generator':n.Compound({'type':n.String('minecraft:noise'),'settings':n.String('minecraft:nether'),'biome_source':n.Compound({'type':n.String('minecraft:multi_noise'),'preset':n.String('minecraft:nether')})})})
    end=n.Compound({'type':n.String('minecraft:the_end'),'generator':n.Compound({'type':n.String('minecraft:noise'),'settings':n.String('minecraft:end'),'biome_source':n.Compound({'type':n.String('minecraft:the_end')})})})
    rules={'doDaylightCycle':'false','doWeatherCycle':'false','doMobSpawning':'false','mobGriefing':'false','doFireTick':'false','keepInventory':'true','spawnRadius':'0','randomTickSpeed':'0','doInsomnia':'false','doPatrolSpawning':'false','doTraderSpawning':'false','commandBlockOutput':'false'}
    data=n.Compound({'DataVersion':n.Int(3465),'version':n.Int(19133),'Version':n.Compound({'Id':n.Int(3465),'Name':n.String('1.20.1'),'Series':n.String('main'),'Snapshot':n.Byte(0)}),
        'LevelName':n.String('Cliffside City | 절벽 도시'),'GameType':n.Int(1),'hardcore':n.Byte(0),'Difficulty':n.Byte(2),'DifficultyLocked':n.Byte(0),'allowCommands':n.Byte(1),'initialized':n.Byte(1),
        'SpawnX':n.Int(186),'SpawnY':n.Int(97),'SpawnZ':n.Int(-16),'SpawnAngle':n.Float(180),
        'Time':n.Long(6000),'DayTime':n.Long(6000),'LastPlayed':n.Long(int(time.time()*1000)),'raining':n.Byte(0),'thundering':n.Byte(0),'clearWeatherTime':n.Int(1000000000),
        'GameRules':n.Compound({k:n.String(v) for k,v in rules.items()}),
        'WorldGenSettings':n.Compound({'seed':n.Long(16591012),'generate_features':n.Byte(0),'bonus_chest':n.Byte(0),'dimensions':n.Compound({'minecraft:overworld':overworld,'minecraft:the_nether':nether,'minecraft:the_end':end})}),
        'DataPacks':n.Compound({'Enabled':n.List[n.String]([n.String('vanilla'),n.String('file/cliffcity')]),'Disabled':n.List[n.String]([])}),
        'enabled_features':n.List[n.String]([n.String('minecraft:vanilla')]),'WasModded':n.Byte(0),
        'BorderCenterX':n.Double(0),'BorderCenterZ':n.Double(64),'BorderSize':n.Double(59999968),'BorderSizeLerpTarget':n.Double(59999968),'BorderSizeLerpTime':n.Long(0),
        'BorderSafeZone':n.Double(5),'BorderDamagePerBlock':n.Double(.2),'BorderWarningBlocks':n.Double(5),'BorderWarningTime':n.Double(15)})
    n.File({'Data':data},gzipped=True).save(WORLD/'level.dat')
    dp=WORLD/'datapacks/cliffcity';fn=dp/'data/cliffcity/functions';fn.mkdir(parents=True,exist_ok=True)
    (dp/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':15,'description':'Cliffside City navigation and optional training'}}))
    for name,command in {'spawn':'tp @s 186 97 -16 180 0','arena':'tp @s 186 98 -105 180 0','park':'tp @s 200 97 174 0 0','forest':'tp @s 187 97 413 0 0','overview':'tp @s 380 420 680 150 40'}.items():
        (fn/(name+'.mcfunction')).write_text(command+'\n')
    training=[]
    for dx in (-48,-16,16,48):
        for dz in (-24,0,24):training.append(f'summon minecraft:husk {186+dx} 98 {-105+dz} '+ '{NoAI:1b,PersistenceRequired:1b,Silent:1b,Tags:["cliffcity_training"]}')
    (fn/'training.mcfunction').write_text('\n'.join(training)+'\n')
    (fn/'clear_training.mcfunction').write_text('kill @e[tag=cliffcity_training]\n')

def previews(top):
    print('Block-derived previews',flush=True)
    h=H-1-np.argmax((V!=AIR)[::-1],axis=0);zz,xx=np.indices((D,W));b=V[h,zz,xx]
    rgb=np.array(colors,dtype=float)[b]
    # Deterministic per-block variation, height shading and cast-shadow approximation.
    noise=((xx*13+zz*17)%11-5)*.9
    gradz,gradx=np.gradient(gaussian_filter(h.astype(float),.7))
    shade=np.clip(1+(.24*gradx+.33*gradz)/np.maximum(1,np.hypot(gradx,gradz)),.62,1.16)
    occluded=np.zeros((D,W),bool)
    for delta in range(3,100,3):
        shifted=np.full_like(h,-500,dtype=np.int16)
        shifted[delta:,delta:]=h[:-delta,:-delta]
        occluded|=shifted>h+delta*.85
    shade[occluded]*=.69
    rgb=np.uint16(np.clip(rgb*shade[...,None]+noise[...,None],0,255))
    im=Image.fromarray(rgb);im.save(ROOT/'City_Top_View.png')
    # Preserve the block grid and put the legend outside the actual map.
    board=Image.new('RGB',(W+370,D),(18,27,35));board.paste(im,(0,0));d=ImageDraw.Draw(board)
    from PIL import ImageFont
    font='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
    title=ImageFont.truetype(font,25);body=ImageFont.truetype(font,17);small=ImageFont.truetype(font,14)
    d.text((1050,37),'CLIFFSIDE CITY',font=title,fill='#f0eee2');d.text((1050,80),'JAVA 1.20.1  /  WORLD MAP',font=small,fill='#a6bcc5')
    d.text((1050,114),'1,024 x 1,152 blocks',font=body,fill='#f0c569')
    spots=[('SPAWN',186,-16),('BATTLE FIELD',186,-105),('WEST PARK',-259,-99),('NORTHWEST FOREST',-263,-297),('PARKING',13,180),('SOUTH GARDENS',200,174),('SOUTHERN FOREST',231,474),('BOSS SHRINE',314,453),('CAVE',61,472),('GRAIL RUINS',150,553),('LIBRARY',-312,73),('AUDDY SHOP',28,-216),('CAFE',-123,177),('GUILD HALL',352,-113)]
    for i,(name,x,z) in enumerate(spots,1):
        px,pz=x+512,z+512;col='#f1c76d'
        d.ellipse((px-11,pz-11,px+11,pz+11),fill='#16232d',outline=col,width=2)
        d.text((px,pz),str(i),font=small,fill='white',anchor='mm')
        y=170+(i-1)*57;d.text((1050,y),f'{i:02}  {name}',font=body,fill='#e3e8e9');d.text((1083,y+24),f'X {x:>4}   Z {z:>4}',font=small,fill='#94acb7')
    d.text((1050,1005),'Preview from the saved block data.',font=small,fill='#94acb7')
    d.text((1050,1031),'Buildings / roads / parks / cliffs',font=small,fill='#94acb7')
    d.text((1050,1073),'NORTH is up. Coordinates are X/Z.',font=small,fill='#f1c76d')
    board.save(ROOT/'City_Guide.png')
    # Inputs for a real geometry render; heightfield sides use the actual block colors.
    np.savez_compressed(ROOT/'verification/surface.npz',heights=h.astype(np.int16)+YMIN,blocks=V,colors=np.array(colors,np.uint16))
    im.resize((64,64),Image.Resampling.LANCZOS).save(WORLD/'icon.png')

def verify():
    spawn=(698,97,496);assert V[97-YMIN,496,698]==AIR and V[98-YMIN,496,698]==AIR
    assert V[96-YMIN,496,698] not in (AIR,WATER)
    for e in BE:
        x,y,z=int(e['x'])+512,int(e['y']),int(e['z'])+512
        expected=SIGN if str(e['id']).endswith(':sign') else CHEST
        assert V[y-YMIN,z,x]==expected,(x,y,z,str(e['id']),int(V[y-YMIN,z,x]))
    assert np.all(V[98-YMIN:103-YMIN,351:464,598:798]==AIR),'Combat surface must be open'
    # Ground-level cardinal road routes are free for a two-block-tall player.
    for x1,z1,x2,z2,width in roads:
        if x1==x2:cells=V[97-YMIN:99-YMIN,z1:z2+1,x1]
        else:cells=V[97-YMIN:99-YMIN,z1,x1:x2+1]
        assert np.all(cells==AIR),'Blocked road'
    return {'spawn_clear':True,'arena_clear':True,'roads_clear':True,'block_entities_match':True}

if __name__=='__main__':
    start=time.time();top=terrain();city();checks=verify();previews(top);chunks,regions=save_regions();save_level()
    manifest={'name':'Cliffside City','minecraft':'Java 1.20.1','data_version':3465,'dimensions':[W,D],'bounds':[-512,-512,511,639],
      'city_ground_y':96,'sea_y':62,'chunks':chunks,'regions':regions,'buildings':len(buildings),'trees':tree_count,'block_states':len(palette),
      'block_entities':len(BE),'landmarks':landmarks,'building_details':buildings,'checks':checks,'generation_seconds':round(time.time()-start,1)}
    (ROOT/'verification/manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    (ROOT/'verification/palette.json').write_text(json.dumps([{'name':str(p['Name']),'properties':{k:str(v) for k,v in p.get('Properties',{}).items()}} for p in palette],indent=2))
    print(json.dumps({k:v for k,v in manifest.items() if k not in ('landmarks','building_details')},indent=2),flush=True)
