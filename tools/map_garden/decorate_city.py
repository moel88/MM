"""BOMI STAND Azure Garden: deterministic, vanilla-only renovation of the 1:3 city.
Coordinates, entrances, navigation functions and the arena's entire playing surface remain fixed.
"""
from pathlib import Path
import json, math, time, shutil, struct, gzip, hashlib, sys
import numpy as np
from scipy.ndimage import label, distance_transform_edt
from PIL import Image
import build_compact_city as c
b=c.b; V=b.V; Y=b.YMIN; OX=b.OX; OZ=b.OZ; OUT=b.ROOT; rng=np.random.default_rng(20261013)
OLD_WORLD=OUT/'Cliffside_City_Third_1.20.1'
WORLD=OUT/'Botand_Azure_Garden_1.20.1'
COUNTS={}
def count(k):COUNTS[k]=COUNTS.get(k,0)+1

def block(name,color,**p):return b.block(name,color,**p)
CALCITE=block('calcite','dfded9');ANDESITE=block('andesite','86888a');POLISH=block('polished_andesite','969a9c')
BRICKS=block('deepslate_bricks','454e56');TILES=block('deepslate_tiles','384550')
QBRICK=block('quartz_bricks','e5e5df');QPILLAR=block('quartz_pillar','e9e8e0',axis='y')
QSLAB=block('smooth_quartz_slab','eeeae3',type='bottom',waterlogged='false')
CYAN=block('light_blue_terracotta','7296b0');PALE=block('light_blue_concrete','67aeca')
CW=block('light_blue_wool','8bc6df');WW=block('white_wool','ececed')
DWOOD=block('dark_oak_planks','54402e');STRIP=block('stripped_birch_log','c5b77b',axis='y')
BIRCHLOG=block('birch_log','dddcca',axis='y')
COPPER=block('waxed_oxidized_copper','509984');CUT=block('waxed_oxidized_cut_copper','539e8a')
CUPSLAB=block('waxed_oxidized_cut_copper_slab','559c8d',type='bottom',waterlogged='false')
LEAF=block('azalea_leaves','648353',persistent='true',distance='1',waterlogged='false')
FLOWERLEAF=block('flowering_azalea_leaves','91a680',persistent='true',distance='1',waterlogged='false')
CHERRY=block('cherry_leaves','eabccc',persistent='true',distance='1',waterlogged='false')
AZALEA=block('flowering_azalea','91b07a');FERN=block('fern','698658')
DAISY=block('oxeye_daisy','f2eacd');ORCHID=block('blue_orchid','71c8e3');ALLIUM=block('allium','bd94cb')
LILY=block('lily_of_the_valley','e9efec');SEAPICKLE=block('sea_pickle','9bc6a0',pickles='3',waterlogged='true')
LIGHTGLASS=block('white_stained_glass','d4e7e8');CYGLASS=block('cyan_stained_glass','4ba8b7')
SHROOM=block('shroomlight','eecda0');HANG=block('lantern','f2ce94',hanging='true',waterlogged='false')
CHAIN=block('chain','566775',axis='y',waterlogged='false')
FENCE=block('dark_oak_fence','584d3f',north='false',east='false',south='false',west='false',waterlogged='false')
CARPET=block('light_blue_carpet','8cccdc');MOSS=block('moss_carpet','789658')
STAIRS={d:block('dark_oak_stairs','64513d',facing=d,half='bottom',shape='straight',waterlogged='false') for d in ['north','south','east','west']}
BARS=block('iron_bars','8c9ca4',north='true',south='true',east='false',west='false',waterlogged='false')

def put(x,y,z,m):b.at(x+OX,y,z+OZ,m)
def box(x,y,z,x2,y2,z2,m):b.box(x+OX,y,z+OZ,x2+OX,y2,z2+OZ,m)
def at(x,y,z):return int(V[y-Y,z+OZ,x+OX])
def disk(x,z,r,y,m,inner=0):b.disk(x+OX,z+OZ,r,y,m,inner)
def free(x,z,r=0,y=81,h=5):
 if not (0<=x+OX-r and x+OX+r<b.W and 0<=z+OZ-r and z+OZ+r<b.D):return False
 return not np.any(V[y-Y:y+h-Y,z+OZ-r:z+OZ+r+1,x+OX-r:x+OX+r+1])
def safe(x,z):return not PROTECT[z+OZ,x+OX]
def ground(x,z,m):
 if at(x,80,z)!=b.AIR:put(x,80,z,m)
def planting(x,z):
 if safe(x,z) and free(x,z,0,81,2) and at(x,80,z) in [b.GRASS,b.MOSS,b.DIRT]:put(x,81,z,[b.BLUEFLOWER,DAISY,LILY,ORCHID,AZALEA,FERN][(x*7+z*13)%6]);count('flowers')
def path(points,width=3,material=None):
 from PIL import ImageDraw
 im=Image.new('1',(b.W,b.D));ImageDraw.Draw(im).line([(x+OX,z+OZ) for x,z in points],fill=1,width=width,joint='curve')
 mask=np.asarray(im).astype(bool);V[80-Y,mask]=material or QBRICK;V[81-Y:84-Y,mask]=b.AIR
 return mask

def sphere(x,y,z,rx,ry,rz,m):
 for dy in range(-ry,ry+1):
  for dz in range(-rz,rz+1):
   for dx in range(-rx,rx+1):
    if (dx/rx)**2+(dy/ry)**2+(dz/rz)**2<1.08 and at(x+dx,y+dy,z+dz)==b.AIR:put(x+dx,y+dy,z+dz,m)

def tree(x,z,h=9,kind=0):
 if not safe(x,z) or not free(x,z,1,81,4) or at(x,80,z) not in [b.GRASS,b.DIRT,b.MOSS]:return
 log=BIRCHLOG if kind!=2 else b.SLOG
 box(x,81,z,x,80+h,z,log)
 # Offset lobes and separate branches replace identical four-layer cylinders.
 leaf=[LEAF,CHERRY,b.SLEAF,FLOWERLEAF][kind]
 if kind==2:
  for yy in range(84,82+h):
   r=max(1,int((82+h-yy)*.38));disk(x,z,r,yy,leaf)
  put(x,82+h,z,leaf)
 else:
  sphere(x,80+h,z,4,3,4,leaf)
  for dx,dz,up in [(-3,0,-2),(2,2,-1),(0,-3,0)]:
   for t in range(1,4):put(x+round(dx*t/3),79+h+up,z+round(dz*t/3),log)
   sphere(x+dx,80+h+up,z+dz,3,2,3,leaf)
  # A few pale blossom clusters and pendant lights at a readable height.
  for dx,dz in [(-2,-2),(2,1)]:
   if kind in [0,3]:put(x+dx,80+h-1,z+dz,FLOWERLEAF)
  if (x+z)%3==0:put(x-2,80+h-3,z,CHAIN);put(x-2,80+h-4,z,HANG)
 count('sculpted_trees')

def bench(x,z,d='north',y=81):
 if not free(x,z,2,y,3) or not safe(x,z):return
 for dx in [-1,0,1]:put(x+dx,y,z,STAIRS[d])
 put(x-2,y,z,b.WOODSLAB);put(x+2,y,z,b.WOODSLAB);count('benches')
def lamp(x,z,y=81):
 if not safe(x,z) or not free(x,z,1,y,7):return
 put(x,y,z,BRICKS);box(x,y+1,z,x,y+4,z,FENCE)
 box(x-1,y+5,z,x+1,y+5,z,CUPSLAB)
 for dx in [-1,1]:put(x+dx,y+4,z,HANG)
 put(x,y+5,z,b.SEA);put(x,y+6,z,QSLAB);count('street_lamps')

def fountain(x,z,r=6):
 box(x-r-2,81,z-r-2,x+r+2,100,z+r+2,b.AIR)
 disk(x,z,r+2,80,QBRICK);disk(x,z,r,80,b.SEA)
 disk(x,z,r,81,QBRICK);disk(x,z,r-1,81,b.WATER)
 disk(x,z,2,81,QBRICK);disk(x,z,1,82,QPILLAR)
 # A contained glass water jet: elevated source cannot spill beyond the bowl.
 for yy in [82,83,84]:
  put(x,yy,z,b.WATER)
  for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)]:put(x+dx,yy,z+dz,LIGHTGLASS)
 put(x,85,z,QSLAB)
 for dx,dz in [(r,0),(-r,0),(0,r),(0,-r)]:put(x+dx,82,z+dz,QSLAB)
 for dx,dz in [(r+1,r+1),(-r-1,r+1),(r+1,-r-1),(-r-1,-r-1)]:
  put(x+dx,81,z+dz,QPILLAR);put(x+dx,82,z+dz,b.LANTERN)
 count('fountains')

def pergola(x,z,y=81,r=4):
 # Roof clearances are four blocks; all four sides remain open.
 for dx in [-r,r]:
  for dz in [-r,r]:box(x+dx,y,z+dz,x+dx,y+4,z+dz,STRIP);put(x+dx,y+3,z+dz-1,HANG)
 for dx in range(-r-1,r+2,2):box(x+dx,y+5,z-r-1,x+dx,y+5,z+r+1,DWOOD)
 for dz in [-r,r]:box(x-r-1,y+4,z+dz,x+r+1,y+4,z+dz,DWOOD)
 for dx,dz in [(-r-1,0),(r+1,0),(0,-r-1),(0,r+1)]:put(x+dx,y+5,z+dz,FLOWERLEAF)
 count('pergolas')

def facades():
 for i,building in enumerate(c.BUILDINGS):
  x1,z1,x2,z2=building['bounds'];roof=building['roof_y'];cx=(x1+x2)//2;cz=(z1+z2)//2
  tone=[CALCITE,b.QUARTZ,b.WHITE,QBRICK][i%4]
  for yy in range(81,roof):
   for zz in [z1,z2]:
    for xx in range(x1,x2+1):
     old=at(xx,yy,zz)
     if old in [b.WHITE,b.QUARTZ,b.GRAY,b.LIGHT]:put(xx,yy,zz,tone)
     if old==b.TINT:put(xx,yy,zz,b.GLASS)
   for xx in [x1,x2]:
    for zz in range(z1,z2+1):
     if at(xx,yy,zz) in [b.WHITE,b.QUARTZ,b.GRAY,b.LIGHT]:put(xx,yy,zz,tone)
  for xx in [x1,x2]:
   for zz in [z1,z2]:box(xx,81,zz,xx,roof+1,zz,QPILLAR)
  for yy in range(85,roof+1,5):
   for zz in [z1-1,z2+1]:box(x1,yy,zz,x2,yy,zz,QSLAB)
   for xx in [x1-1,x2+1]:box(xx,yy,z1,xx,yy,z2,QSLAB)
  # Slim blue corner stripes, recessed glass, balcony flower troughs.
  for zz in [z1,z2]:
   for xx in [x1+1,x2-1]:box(xx,86,zz,xx,roof-1,zz,CYAN)
  for yy in range(87,roof-1,5):
   for xx in range(x1+3,x2-2,5):
    for zz in [z1-1,z2+1]:
     box(xx-1,yy,zz,xx+1,yy,zz,CUPSLAB)
     for dx in [-1,0,1]:put(xx+dx,yy+1,zz,FLOWERLEAF if (xx+yy)%2 else LEAF)
  front=z1 if building['name']=='ANUBIS HALL' else z2
  out=-1 if front==z1 else 1
  for dx in range(-2,3):
   for dz in [out,2*out]:put(cx+dx,84,front+dz,WW if dx%2 else CW)
  for dx in [-3,3]:put(cx+dx,83,front+out,HANG)
  # Rooftop gardens don't block the original continuous ladder shaft at x1+2,z1+2.
  for xx in range(x1+4,x2-1):
   put(xx,roof+1,z2-2,b.GRASS);put(xx,roof+2,z2-2,FLOWERLEAF if xx%3 else AZALEA)
  if x2-x1>=12 and z2-z1>=17 and i%2==0:
   pergola(cx,cz+2,roof+1,3)
   for xx in [cx-1,cx,cx+1]:put(xx,roof+1,cz+2,STAIRS['north'])
  if i%3==0:
   box(x1+3,roof+1,z1+5,x1+5,roof+1,z1+7,CUT)
   box(x1+3,roof+2,z1+5,x1+5,roof+2,z1+7,CUPSLAB)
  count('renovated_buildings')
 # Distinct stepped clock tower on the existing tallest northern building.
 x,z,yy=12,-145,107
 for size,dy in [(4,0),(3,1),(3,6),(2,7),(1,10)]:box(x-size,yy+dy,z-size,x+size,yy+dy,z+size,QSLAB if dy%2==0 else CUT)
 box(x-2,yy+2,z-2,x+2,yy+5,z+2,QPILLAR)
 for dz in [-3,3]:
  box(x-1,yy+3,z+dz,x+1,yy+5,z+dz,TILES);put(x,yy+4,z+dz,b.SEA);put(x,yy+5,z+dz,b.GOLD);put(x+1,yy+4,z+dz,b.GOLD)
 box(x,yy+11,z,x,yy+13,z,CHAIN);put(x,yy+14,z,b.SEA);count('clock_towers')

def parks():
 for x1,z1,x2,z2,kind in [(-110,-79,-72,-25,0),(15,0,61,89,1)]:
  # Clear old park trees only inside the original park footprint, keeping outer streets/buildings.
  box(x1,81,z1,x2,104,z2,b.AIR);box(x1,80,z1,x2,80,z2,b.GRASS)
  cx=(x1+x2)//2;cz=(z1+z2)//2
  path([(x1+3,z1+3),(x2-3,z1+3),(x2-3,z2-3),(x1+3,z2-3),(x1+3,z1+3)],3)
  path([(cx,z1-1),(cx,z2+1)],3)
  path([(x1-1,cz),(x2+1,cz)],3)
  fz=cz if kind==0 else 23
  path([(cx-9,fz),(cx-9,fz+9),(cx+9,fz+9),(cx+9,fz),(cx+9,fz-9),(cx-9,fz-9),(cx-9,fz)],3)
  fountain(cx,fz,5 if kind==0 else 6)
  if kind==1:
   path([(cx-8,69),(cx+8,69)],5);pergola(cx,69,81,5)
   disk(cx,69,4,80,QBRICK)
   for dx in [-3,3]:bench(cx+dx,72)
  for xx in [x1+7,x2-7]:
   for zz in range(z1+8,z2-4,14):tree(xx,zz,8+(zz%3),1 if kind==0 else 3)
  for xx,zz in [(cx-12,fz-9),(cx+12,fz+9),(cx-12,fz+9),(cx+12,fz-9)]:bench(xx,zz)
  for xx in [x1+4,x2-4]:
   for zz in range(z1+4,z2,18):lamp(xx,zz)
  for zz in range(z1+2,z2-1):
   for xx in range(x1+2,x2-1):
    if (xx*11+zz*17)%7==0:planting(xx,zz)
  count('formal_gardens')

def forests():
 # Selectively replace trees but preserve all path floors, cave and boss landmarks.
 for x1,z1,x2,z2 in [(-121,-136,-64,-92),(4,103,110,175)]:
  part=V[81-Y:101-Y,z1+OZ:z2+OZ+1,x1+OX:x2+OX+1]
  part[np.isin(part,[b.SLOG,b.LOG,b.LEAF,b.SLEAF,b.BIRCH])]=b.AIR
  points=[]
  for _ in range(2200):
   x=int(rng.integers(x1+5,x2-4));z=int(rng.integers(z1+5,z2-4))
   if any((x-a)**2+(z-d)**2<55 for a,d in points):continue
   if not free(x,z,2,81,10) or not safe(x,z):continue
   # Don't grow canopies through shrines/cave, nor put trunks on the original trail.
   if at(x,80,z)!=b.GRASS:continue
   if z1>0 and ((x-83)**2+(z-130)**2<190 or (x-10)**2+(z-150)**2<150 or (x-38)**2+(z-177)**2<130):continue
   tree(x,z,int(rng.integers(9,15)),int(rng.choice([0,1,2,3],p=[.33,.10,.37,.20])));points.append((x,z))
  for z in range(z1+2,z2-1,2):
   for x in range(x1+2,x2-1,2):
    if rng.random()<.35:planting(x,z)
  count('forest_groves')
 # Staggered avenue trees in previously empty northern green space.
 for x in [-147,-130,-113,-96,-79,-62]:tree(x,-148,10+(x%3),3)
 for x in [-41,-21,0,20,40,60,80]:tree(x,-169,10+(x%2),1 if x%3==0 else 0)
 # Southern forest walking route, softened edges and lamps at path junctions.
 for x,z in [(60,116),(47,129),(27,151),(46,167)]:lamp(x,z)

def streets():
 floor=V[80-Y];zz,xx=np.indices(floor.shape)
 paving=floor==b.PAVE;floor[paving]=POLISH
 # Continuous gray paving: no periodic quartz inserts in pavements/building floors.
 # Continuous road lanes stay untouched. Ornament the spare sidewalk strips instead.
 for points in c.ROAD_PATHS:
  for (ax,az),(bx,bz) in zip(points,points[1:]):
   a=np.array([c.px(ax)-OX,c.pz(az)-OZ],float);d=np.array([c.px(bx)-OX,c.pz(bz)-OZ],float)-a
   length=np.linalg.norm(d);unit=d/max(1,length);normal=np.array([-unit[1],unit[0]])
   for t in range(6,int(length)-4,13):
    for side in [-1,1]:
     x,z=np.rint(a+unit*t+normal*side*4).astype(int)
     if at(x,80,z) in [POLISH,QBRICK,b.GRASS] and free(x,z,1,81,7):lamp(x,z)
 # Lay eight-point star medallions in ground only: no obstacle in the arena.
 for x in range(6,62):
  for z in range(-68,-26):
   dx=x-33;dz=z+48
   if abs(dx)==abs(dz) and abs(dx)<=10:put(x,80,z,CYAN)
   elif 9.5<math.hypot(dx,dz)<10.7:put(x,80,z,QBRICK)
   elif abs(dx)<2 and abs(dz)<7 or abs(dz)<2 and abs(dx)<7:put(x,80,z,b.QUARTZ)
 for x,z in [(0,-74),(66,-74),(0,-22),(66,-22)]:
  if free(x,z,1,81,9):
   box(x,81,z,x,85,z,QPILLAR);put(x,86,z,b.SEA);put(x,87,z,CUPSLAB)
 for x in range(7,62,7):
  for z in [-73,-23]:
   if free(x,z,0,81,3) and safe(x,z):put(x,81,z,STAIRS['north' if z==-23 else 'south'])
 # Original street lamps get a matching cap; coordinates retained.
 for x,z in [(202,291),(371,291),(599,291),(858,292),(360,460),(600,464),(849,463),(651,549),(852,633),(623,671),(477,748),(868,748),(606,790)]:
  xx,_,zz=c.xyz(x,z);put(xx,86,zz,CUPSLAB)

def cafes_and_shops():
 # Terrace east of the existing cafe, bounded away from the street.
 box(-44,80,36,-41,80,55,QBRICK)
 for z in [39,48]:
  put(-43,81,z,FENCE);put(-43,82,z,CW);put(-43,83,z,b.LANTERN)
  put(-43,81,z-2,STAIRS['south']);put(-43,81,z+2,STAIRS['north'])
 count('cafe_terraces')
 # Shop awnings are already on every facade. Pocket stalls sit on spare north grass.
 for x,z in [(-28,-86),(-7,-86),(29,-85)]:
  if not free(x,z,2,81,5) or not safe(x,z):continue
  box(x-2,80,z-1,x+2,80,z+2,QBRICK)
  for dx in [-2,2]:box(x+dx,81,z,x+dx,84,z,STRIP)
  for dx in range(-2,3):box(x+dx,84,z-1,x+dx,84,z+2,CW if dx%2 else WW)
  box(x-1,81,z+1,x+1,81,z+1,DWOOD);put(x,82,z+1,AZALEA);count('market_stalls')
 # Parked compact cars, avoiding the parking centre aisle and exits.
 for x,z,color in [(-30,12,PALE),(-30,36,b.WHITE),(-3,57,CYAN),(-3,76,b.WHITE)]:
  if not free(x,z,2,81,4):continue
  for dx in [-1,1]:
   for dz in [-2,2]:put(x+dx,81,z+dz,TILES)
  box(x-1,81,z-1,x+1,81,z+1,color);box(x,82,z-1,x,82,z+1,b.GLASS)
  put(x,83,z,color);put(x,81,z-2,b.SEA);count('parked_cars')
 # Courtyard stepping stones and small garden clusters fill empty lots, not roads.
 for x,z in [(-69,-42),(-56,-145),(44,-110),(68,-106),(-68,10),(74,31),(85,103),(-3,104)]:
  if free(x,z,3,81,8) and at(x,80,z)==b.GRASS:
   tree(x,z,8,3);bench(x,z+5);lamp(x+5,z+5)

def shrine():
 # Existing boss basin stays where it was; add a pale, cyan-lit gateway behind it.
 x,z=83,130
 for dx in [-6,6]:
  box(x+dx,81,z-7,x+dx,89,z-7,QPILLAR);put(x+dx,90,z-7,b.SEA)
 box(x-6,90,z-7,x+6,90,z-7,QBRICK);box(x-5,91,z-7,x+5,91,z-7,CUT)
 box(x-3,92,z-7,x+3,92,z-7,CUPSLAB);put(x,93,z-7,b.AMETHYST)
 for dx in [-4,4]:put(x+dx,82,z+7,b.LANTERN)
 # Grail ring is low and passable, with four freestanding ruins.
 x,z=38,177
 for dx,dz in [(-6,-6),(6,-6),(-6,6),(6,6)]:
  box(x+dx,81,z+dz,x+dx,84+((dx+dz)%3),z+dz,CALCITE);put(x+dx,85+((dx+dz)%3),z+dz,b.SEA)
 count('refined_shrines')

def cliffs():
 # Preserve every contour/height; replace monotonous brown bands with weathered coastal stone.
 for y in range(63,106):
  layer=V[y-Y];zz,xx=np.indices(layer.shape)
  rock=np.isin(layer,[b.ROCK,b.RUST,b.DARKROCK])
  exposed=np.isin(V[y+1-Y],[b.AIR,b.WATER])
  for dx,dz in [(1,0),(-1,0),(0,1),(0,-1),(2,0),(-2,0),(0,2),(0,-2)]:exposed|=np.isin(np.roll(layer,(dz,dx),(0,1)),[b.AIR,b.WATER])
  rock &= exposed
  layer[rock]=ANDESITE
  layer[rock&(((xx*3+zz*5+y)//9)%6==0)]=CALCITE
  layer[rock&((y//4)%4==0)]=b.STONE
 # Sparse moss at the top, keeping crags recognizable rather than flattening them.
 top=b.H-1-np.argmax((V!=b.AIR)[::-1],axis=0)
 for iz,ix in zip(*np.nonzero(top+Y>91)):
  y=int(top[iz,ix]+Y)
  if at(ix-OX,y,iz-OZ) in [ANDESITE,b.STONE,CALCITE] and (ix*17+iz*7)%5==0:put(ix-OX,y,iz-OZ,b.MOSS)


def make_patch(before, filename="azure_garden.bgp.gz"):
 # Chunk-major and bottom-up application avoids excessive chunk churn.
 ys,zs,xs=np.nonzero(before!=V)
 # Finish every retaining wall before adding any water, including across chunk boundaries.
 order=np.lexsort((xs,zs,ys,xs//16,zs//16,V[ys,zs,xs]==b.WATER));ys,zs,xs=ys[order],zs[order],xs[order]
 palette=[]
 for p in b.palette:
  state=str(p['Name']);props=p.get('Properties',{})
  if props:state+='['+','.join(k+'='+str(v) for k,v in sorted(props.items()))+']'
  palette.append(state)
 header=json.dumps({'revision':1,'name':'Azure Garden','palette':palette,'cells':len(xs)},separators=(',',':')).encode()
 asset=Path(__file__).resolve().parents[2]/'src/main/resources/data/celestialfall/map'/filename;asset.parent.mkdir(parents=True,exist_ok=True)
 with gzip.open(asset,'wb',compresslevel=9) as f:
  f.write(b'BGP1');f.write(struct.pack('>I',len(header)));f.write(header)
  for y,z,x in zip(ys,zs,xs):f.write(struct.pack('>hhhHH',int(x)-OX,int(y)+Y,int(z)-OZ,int(before[y,z,x]),int(V[y,z,x])))
 return {'changed_blocks':len(xs),'patch_bytes':asset.stat().st_size,'patch_sha256':hashlib.sha256(asset.read_bytes()).hexdigest()}

def make_floor_repair():
 # Compare against the shipped 2.14.0 patch, not a broad quartz replacement.
 # This retains fountains, park paths, architecture and the arena medallion.
 def read_patch(path):
  with gzip.open(path,'rb') as f:
   assert f.read(4)==b'BGP1';h=json.loads(f.read(struct.unpack('>I',f.read(4))[0]));raw=f.read()
  assert len(raw)==h['cells']*10
  return {p[:3]:(h['palette'][p[3]],h['palette'][p[4]]) for p in struct.iter_unpack('>hhhHH',raw)}
 asset=Path(__file__).resolve().parents[2]/'src/main/resources/data/celestialfall/map'
 old=read_patch(Path(__file__).with_name('azure_garden_2_14_0.bgp.gz'));new=read_patch(asset/'azure_garden.bgp.gz')
 assert old.keys()==new.keys()
 changed=[p for p in old if old[p]!=new[p]]
 assert changed
 for p in changed:
  assert p[1]==80 and old[p]==('minecraft:smooth_stone','minecraft:quartz_bricks') and new[p]==('minecraft:smooth_stone','minecraft:polished_andesite'),(p,old[p],new[p])
 changed.sort(key=lambda p:(p[2]//16,p[0]//16,p[2],p[0]))
 h=json.dumps({'revision':1,'name':'Azure Garden pavement repair 2.14.1','palette':['minecraft:quartz_bricks','minecraft:polished_andesite'],'cells':len(changed)},separators=(',',':')).encode()
 with gzip.open(asset/'azure_garden_floor_fix.bgp.gz','wb',compresslevel=9) as f:
  f.write(b'BGP1');f.write(struct.pack('>I',len(h)));f.write(h)
  for p in changed:f.write(struct.pack('>hhhHH',*p,0,1))
 return len(changed)

if __name__=='__main__':
 started=time.time();c.terrain();c.city();c.check();before=V.copy()
 # Save exact compatibility signatures before enriching the world.
 c.save_level();fn=OLD_WORLD/'datapacks/cliffcity/data/cliffcity/functions'
 signatures={name:(fn/(name+'.mcfunction')).read_bytes() for name in ['arena','forest','spawn']}
 PROTECT=np.zeros((b.D,b.W),bool)
 def reserve(x1,z1,x2,z2):PROTECT[z1+OZ:z2+OZ+1,x1+OX:x2+OX+1]=True
 reserve(4,-70,62,-26);reserve(29,-23,37,-15)
 for cx,cz,*_ in c.ENTRIES:reserve(cx-OX-2,cz-OZ-4,cx-OX+2,cz-OZ+4)
 for e in b.BE:reserve(int(e['x'])-1,int(e['z'])-1,int(e['x'])+1,int(e['z'])+1)
 # Navigational forest teleport remains clear. Park teleport is a central path intersection.
 reserve(58,109,62,115);reserve(36,43,40,47)
 for task in [facades,parks,forests,streets,cafes_and_shops,shrine,cliffs]:print(task.__name__,flush=True);task()
 # Preserve every original container/sign, their support blocks, and their original inventory/text NBT.
 for e in b.BE:
  x,z,y=int(e['x'])+OX,int(e['z'])+OZ,int(e['y'])
  V[y-Y,z,x]=before[y-Y,z,x];V[y-1-Y,z,x]=before[y-1-Y,z,x]
  V[y+1-Y,z,x]=b.AIR
 # Generate the legacy floor repair before the two venue renovations.
 make_patch(before);floor_count=make_floor_repair();garden=V.copy()
 import venues
 venue_checks=venues.renovate(sys.modules[__name__],before)
 venue_patch=make_patch(garden,'azure_venues.bgp.gz')
 # Alternative original-city states allow the same update on the old compact map.
 ys,zs,xs=np.nonzero(garden!=V)
 original_venues=garden.copy();original_venues[ys,zs,xs]=before[ys,zs,xs]
 # Store original states aligned to the primary patch, including states already equal to target.
 primary=Path(__file__).resolve().parents[2]/'src/main/resources/data/celestialfall/map/azure_venues.bgp.gz'
 with gzip.open(primary,'rb') as f:
  raw=f.read()
 size=struct.unpack('>I',raw[4:8])[0];head=raw[:8+size]
 with gzip.open(primary.with_name('azure_venues_original.bgp.gz'),'wb') as f:
  f.write(head)
  for x,y,z,old,new in struct.iter_unpack('>hhhHH',raw[8+size:]):
   f.write(struct.pack('>hhhHH',x,y,z,int(before[y-Y,z+OZ,x+OX]),new))
 del garden,original_venues
 checks=c.check();checks.update(venue_checks)
 if WORLD.exists():shutil.rmtree(WORLD)
 shutil.copytree(OLD_WORLD,WORLD);b.WORLD=WORLD
 level=b.n.load(WORLD/'level.dat');level['Data']['LevelName']=b.n.String('BOMI STAND | Azure Garden');level['Data']['DayTime']=b.n.Long(1000);level.save()
 # Preserve auto-detection fingerprint byte-for-byte, independent of world folder naming.
 for name,raw in signatures.items():assert (WORLD/'datapacks/cliffcity/data/cliffcity/functions'/(name+'.mcfunction')).read_bytes()==raw
 patch=make_patch(before);patch['repaired_floor_inserts']=floor_count;patch['venues']=venue_patch;c.previews();chunks,regions=b.save_regions()
 # Above checks cover the protected combat rectangle, doors, ladders, spawn and road connectivity.
 manifest={'name':'Botand Azure Garden','world_folder':WORLD.name,'minecraft':'Java 1.20.1','version':'2.15.0','data_version':3465,'dimensions':[341,384],'bounds':[-176,-192,175,191],'chunks':chunks,'regions':regions,'block_entities':len(b.BE),'block_states':len(b.palette),'layout_changed':False,'navigation_functions_unchanged':True,'decorations':COUNTS,'checks':checks,**patch,'landmarks':c.LANDMARKS,'generation_seconds':round(time.time()-started,1)}
 (OUT/'verification/manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2))
 (OUT/'verification/palette.json').write_text(json.dumps([{'name':str(p['Name']),'properties':{k:str(v) for k,v in p.get('Properties',{}).items()}} for p in b.palette],indent=2))
 print(json.dumps({k:v for k,v in manifest.items() if k!='landmarks'},indent=2),flush=True)
