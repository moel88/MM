"""Two coordinate-selected venues. The cafe arcade is recessed off the road."""
import numpy as np
from scipy.ndimage import label

def renovate(g, original):
 b,c=g.b,g.c;put,box=g.put,g.box
 def mat(name,color,**props):return g.block(name,color,**props)
 birch=mat('birch_planks','d9ca94');navy=mat('blue_terracotta','464d79')
 oak=mat('stripped_spruce_log','957450',axis='y');glass=mat('glass','e3f5f6')
 pot=mat('potted_blue_orchid','69bdd7');fern=mat('potted_fern','6d956c')
 cake=mat('cake','f2ddc9',bites='0');diamond=mat('diamond_block','75dcd9')
 amethyst=mat('amethyst_cluster','c79be7',facing='up',waterlogged='false')
 rod=mat('end_rod','e8f6ee',facing='up')
 trap=mat('spruce_trapdoor','8b684c',facing='north',half='top',open='false',powered='false',waterlogged='false')
 topslab=mat('smooth_quartz_slab','eeeae3',type='top',waterlogged='false')
 dark=mat('polished_blackstone','383945')
 lever=mat('lever','b1aaa1',face='wall',facing='east',powered='false')
 basin=mat('water_cauldron','586b6b',level='3');smooth=b.QUARTZ
 def pendant(x,z,ceiling=89):put(x,ceiling,z,g.CHAIN);put(x,ceiling-1,z,g.HANG)
 def plant(x,z,y=81):put(x,y,z,g.CUT);put(x,y+1,z,g.LEAF);put(x,y+2,z,g.FLOWERLEAF)
 def table(x,z,y=81,chairs='z'):
  put(x,y,z,g.CUT);put(x,y+1,z,pot)
  if chairs=='z':put(x,y,z-2,g.STAIRS['north']);put(x,y,z+2,g.STAIRS['south'])
  else:put(x-2,y,z,g.STAIRS['west']);put(x+2,y,z,g.STAIRS['east'])
 def letters(text,start,y,z,face='south'):
  glyph={'S':['111','100','111','001','111'],'H':['101','101','111','101','101'],'O':['111','101','101','101','111'],'P':['110','101','110','100','100'],
   'C':['111','100','100','100','111'],'A':['010','101','111','101','101'],'F':['111','100','110','100','100'],'E':['111','100','110','100','111']}
  for k,ch in enumerate(text):
   for row,cols in enumerate(glyph[ch]):
    for col,on in enumerate(cols):
     if on=='1':
      if face=='south':put(start+k*4+col,y+4-row,z,b.SEA)
      else:put(start,y+4-row,z-k*4-col,b.SEA)
 # AUDDY SHOP: original footprint; tall showroom and upper customer lounge.
 box(-15,81,-128,3,105,-92,b.AIR);box(-14,80,-127,2,80,-93,birch)
 for x in [-14,2]:box(x,81,-127,x,95,-93,smooth)
 for z in [-127,-93]:box(-14,81,z,2,95,z,smooth)
 box(-13,81,-126,1,94,-94,b.AIR);box(-13,90,-126,1,90,-94,birch)
 box(-14,95,-127,2,95,-93,smooth)
 for x in [-14,2]:
  box(x,81,-127,x,81,-93,navy)
  for z in range(-124,-94,6):
   box(x,82,z,x,88,z+3,glass);box(x,91,z,x,93,z+3,g.LIGHTGLASS)
  for z in [-127,-119,-111,-103,-93]:box(x,81,z,x,95,z,g.QPILLAR)
 for y in [89,94,96]:
  for z in [-128,-92]:box(-15,y,z,3,y,z,g.QSLAB)
  for x in [-15,3]:box(x,y,-127,x,y,-93,g.QSLAB)
 for x1,x2 in [(-13,-9),(-3,1)]:box(x1,82,-93,x2,84,-93,glass)
 box(-8,81,-95,-4,85,-89,b.AIR);box(-7,80,-96,-5,80,-89,navy)
 for x in [-8,-4]:box(x,81,-93,x,85,-93,g.QPILLAR)
 box(-14,86,-93,2,90,-93,navy);letters('SHOP',-13,86,-92)
 for x1 in [-12,-7,-2]:box(x1,91,-93,x1+2,93,-93,g.LIGHTGLASS)
 for x in range(-9,-2):box(x,85,-92,x,85,-90,g.CW if x%2 else g.WW)
 for x in [-10,-2]:put(x,84,-91,g.HANG)
 for x,gem in [(-11,diamond),(-1,b.AMETHYST)]:
  box(x-1,81,-95,x+1,81,-95,g.CUT);put(x,82,-95,gem);put(x,83,-95,amethyst)
 for x in [-13,1]:plant(x,-91)
 for x in [-13,1]:
  for z in [-100,-106,-112,-118]:
   box(x,81,z-1,x,83,z+1,g.DWOOD);box(x,82,z-1,x,82,z+1,b.BOOKS)
   put(x,84,z,pot);put(x,83,z,b.SEA)
 for z,gem in [(-104,b.AMETHYST),(-113,diamond)]:
  box(-8,81,z-1,-4,81,z+1,navy);box(-8,82,z-1,-4,82,z+1,smooth)
  put(-6,83,z,gem);put(-6,84,z,amethyst)
  for x in [-8,-4]:put(x,83,z,rod)
 box(-10,81,-121,0,81,-121,g.CUT);box(-10,82,-121,0,82,-121,smooth)
 put(-2,83,-121,dark);put(-1,83,-121,b.GOLD);put(-9,83,-121,pot)
 box(-9,81,-126,0,82,-126,b.BOOKS)
 for x in [-8,-4,0]:put(x,83,-126,fern)
 for x in [-10,-2]:
  for z in [-99,-108,-117,-124]:pendant(x,z)
 for z in range(-119,-95):
  if z not in range(-115,-101):put(-6,81,z,g.CARPET)
 for z in [-100,-110,-120]:table(-6,z,91,'x');pendant(-6,z,94)
 for x in [-14,2]:box(x,96,-127,x,96,-93,g.CUT)
 for z in [-127,-93]:box(-14,96,z,2,96,z,g.CUT)
 box(-9,95,-118,-3,95,-103,g.LIGHTGLASS)
 for z in [-99,-122]:
  for x in [-11,-1]:plant(x,z,96)
 box(-12,81,-126,-12,95,-126,smooth);box(-12,81,-125,-12,95,-125,b.LADDER)
 box(-13,91,-124,-11,93,-123,b.AIR)
 c.BUILDINGS[10]['name']='AUDDY SHOP / Azure Boutique'
 # CAFE: reclaim the old furniture terrace occupying the asphalt road.
 for y in range(80,86):
  g.V[y-g.Y,36+g.OZ:56+g.OZ,-44+g.OX:-40+g.OX]=original[y-g.Y,36+g.OZ:56+g.OZ,-44+g.OX:-40+g.OX]
 box(-58,81,33,-45,105,56,b.AIR);box(-54,84,57,-50,84,57,b.AIR)
 box(-54,80,56,-50,80,58,b.GRASS)
 for x in [-53,-52,-51]:put(x,81,57,g.CUT);put(x,82,57,g.FLOWERLEAF)
 box(-57,80,34,-45,80,55,birch)
 for x in [-57,-48]:box(x,81,34,x,84,55,smooth)
 for z in [34,55]:box(-57,81,z,-48,84,z,smooth)
 for y in [85,90,95]:box(-57,y,34,-46,y,55,birch if y<95 else smooth)
 for x in [-57,-46]:box(x,86,34,x,94,55,smooth)
 for z in [34,55]:box(-57,86,z,-46,94,z,smooth)
 box(-56,86,35,-47,89,54,b.AIR);box(-56,91,35,-47,94,54,b.AIR)
 for z in [36,48]:box(-48,82,z,-48,84,z+5,glass)
 box(-48,81,43,-45,84,45,b.AIR);box(-50,80,43,-45,80,45,g.CUT)
 for z in [42,46]:box(-48,81,z,-48,84,z,oak)
 box(-48,84,43,-45,84,45,g.CUPSLAB)
 for z in [34,40,49,55]:box(-46,81,z,-46,84,z,oak)
 for z in range(34,56):put(-45,85,z,g.CW if z%2 else g.WW)
 box(-46,86,36,-46,90,53,navy);letters('CAFE',-45,86,52,'east')
 for x in [-57,-46]:
  for z in [36,43,50]:box(x,91,z,x,93,z+3,g.LIGHTGLASS)
 for z in [34,55]:
  box(-54,82,z,-50,84,z,glass)
  for y in [87,92]:box(-54,y,z,-49,y+1,z,g.LIGHTGLASS)
 box(-57,81,55,-46,81,55,g.CUT)
 for y in [90,95,96]:
  for z in [33,56]:box(-58,y,z,-45,y,z,g.QSLAB)
  box(-58,y,34,-58,y,55,g.QSLAB)
  if y!=90:box(-45,y,34,-45,y,55,g.CUPSLAB)
 box(-56,81,40,-55,81,50,g.DWOOD);box(-55,82,40,-55,82,50,g.DWOOD)
 box(-56,82,43,-56,83,45,dark);put(-55,83,44,lever)
 put(-56,82,46,basin);put(-55,83,48,cake);put(-55,83,41,pot)
 box(-56,83,48,-56,83,50,b.BOOKS)
 for z in [40,45,50]:pendant(-54,z,84)
 for z in [38,51]:
  put(-49,81,z,g.CUT);put(-49,82,z,pot)
  put(-49,81,z-2,g.STAIRS['north']);put(-49,81,z+2,g.STAIRS['south'])
 for z in [37,51]:
  put(-47,81,z,g.STAIRS['south']);put(-47,81,z+2,g.CUT)
  put(-47,82,z+2,pot);put(-47,84,z,g.HANG)
 for y in [86,91]:
  for z in [39,49]:table(-51,z,y,'z');pendant(-51,z,y+3)
  box(-56,y,42,-56,y+2,49,b.BOOKS)
  for z in [36,53]:put(-48,y,z,g.CUT);put(-48,y+1,z,pot)
 for x in [-57,-46]:box(x,96,34,x,96,55,g.CUT)
 for z in [34,55]:box(-57,96,z,-46,96,z,g.CUT)
 g.pergola(-51,45,96,3)
 for z in [36,53]:plant(-48,z,96)
 box(-55,81,35,-55,95,35,smooth);box(-55,81,36,-55,95,36,b.LADDER)
 _,_,lx,lz,top=c.ENTRIES[28];c.ENTRIES[28]=(-48+g.OX,44+g.OZ,lx,lz,top)
 c.BUILDINGS[28]['name']='CAFE / Bomi Blue Hour'
 g.count('boutique_interiors');g.count('cafe_interiors');g.count('corrected_venue_entrances')
 walk=(g.V[80-g.Y]!=b.AIR)&np.isin(g.V[81-g.Y],[b.AIR,g.CARPET,b.LADDER])&(g.V[82-g.Y]==b.AIR)
 ids,_=label(walk)
 def connected(points):
  labels=[ids[z+g.OZ,x+g.OX] for x,z in points]
  assert labels[0]>0 and len(set(labels))==1,(points,labels)
 connected([(-6,-89),(-6,-97),(-10,-110),(-12,-124),(-5,-120)])
 connected([(-45,44),(-47,44),(-51,44),(-53,38),(-53,52),(-55,37)])
 assert np.all(g.V[81-g.Y:84-g.Y,43+g.OZ:46+g.OZ,-48+g.OX:-44+g.OX]==b.AIR),'Cafe entrance clearance'
 assert np.all(g.V[81-g.Y:84-g.Y,36+g.OZ:56+g.OZ,-44+g.OX:-40+g.OX]==original[81-g.Y:84-g.Y,36+g.OZ:56+g.OZ,-44+g.OX:-40+g.OX]),'Road obstructed'
 return {'shop':{'target':[-5,81,-112],'entry':[-6,81,-93],'front':'south'},'cafe':{'target':[-51,81,44],'entry':[-48,81,44],'front':'east'},'interior_routes_connected':True,'public_road_restored':True}
