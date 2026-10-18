import struct,gzip,time
from pathlib import Path
S=lambda x:(8,x)
I=lambda x:(3,x)
B=lambda x:(1,x)
L=lambda x:(4,x)
C=lambda x:(10,x)
A=lambda typ,x:(9,(typ,x))
def text(x):
 b=x.encode();return struct.pack('>H',len(b))+b
def payload(tag,value):
 if tag in (1,3,4,5,6):return struct.pack({1:'>b',3:'>i',4:'>q',5:'>f',6:'>d'}[tag],value)
 if tag==8:return text(value)
 if tag==9:
  t,items=value;return bytes([t])+struct.pack('>i',len(items))+b''.join(payload(t,x) for x in items)
 if tag==10:return b''.join(bytes([t])+text(k)+payload(t,v) for k,(t,v) in value.items())+b'\0'
def save(p,data):p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(gzip.compress(b'\x0a\0\0'+payload(10,data)))
root=Path(__file__).resolve().parents[2]/'run/saves/Botand_Remake_QA'
if root.exists():raise SystemExit('Test world already exists; reuse it or move it before creating a new one.')
over={'type':S('minecraft:overworld'),'generator':C({'type':S('minecraft:flat'),'settings':C({'biome':S('minecraft:plains'),'lakes':B(0),'features':B(0),'structure_overrides':A(8,[]),'layers':A(10,[{'height':I(h),'block':S('minecraft:'+b)} for h,b in [(1,'bedrock'),(143,'stone'),(1,'smooth_stone')]])})})}
dimensions={'minecraft:overworld':C(over)}
for name,settings,biome in [('the_nether','nether',{'type':S('minecraft:multi_noise'),'preset':S('minecraft:nether')}),('the_end','end',{'type':S('minecraft:the_end')})]:
 dimensions['minecraft:'+name]=C({'type':S('minecraft:'+name),'generator':C({'type':S('minecraft:noise'),'settings':S('minecraft:'+settings),'biome_source':C(biome)})})
data={'DataVersion':I(3465),'version':I(19133),'Version':C({'Id':I(3465),'Name':S('1.20.1'),'Series':S('main'),'Snapshot':B(0)}),'LevelName':S('Botand Remake QA'),'GameType':I(1),'hardcore':B(0),'Difficulty':B(0),'allowCommands':B(1),'initialized':B(1),'SpawnX':I(0),'SpawnY':I(81),'SpawnZ':I(0),'Time':L(1000),'DayTime':L(1000),'LastPlayed':L(int(time.time()*1000)),'GameRules':C({k:S(v) for k,v in {'doDaylightCycle':'false','doWeatherCycle':'false','doMobSpawning':'false','doFireTick':'false','spawnRadius':'0','randomTickSpeed':'0','keepInventory':'true'}.items()}),'WorldGenSettings':C({'seed':L(123),'generate_features':B(0),'bonus_chest':B(0),'dimensions':C(dimensions)}),'DataPacks':C({'Enabled':A(8,['vanilla']),'Disabled':A(8,[])}),'enabled_features':A(8,['minecraft:vanilla'])}
save(root/'level.dat',{'Data':C(data)})
save(root/'data/botand_map.dat',{'DataVersion':I(3465),'data':C({'Dimensions':C({'minecraft:overworld':B(1)})})})
print(root.resolve())
