"""Original 3D title set. Exports the SAME meshes consumed by Minecraft and review renderer.
No screenshot, generated background plate or video is used as the game background.
"""
from pathlib import Path
import io,json,math,struct,os
import numpy as np
from PIL import Image,ImageDraw
from scipy.spatial.transform import Rotation

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
RES=ROOT/'src/main/resources/assets/celestialfall'
OUT=RES/'cinema';OUT.mkdir(parents=True,exist_ok=True)
TEX=RES/'textures/cinema';TEX.mkdir(parents=True,exist_ok=True)
rng=np.random.default_rng(240513)
N=512;atlas=Image.new('RGBA',(N,N),(255,255,255,255));draw=ImageDraw.Draw(atlas);tiles={}
def save(path,b):
    with Path(path).open('wb') as f:f.write(b);f.flush();os.fsync(f.fileno())
def texture(name,col,kind='flat'):
    k=len(tiles);x=(k%8)*64;y=(k//8)*64
    for yy in range(64):
        for xx in range(64):
            v=0
            if kind!='flat':
                v=int(rng.integers(-8,9)) if xx%4==0 and yy%4==0 else 0
                if kind=='stone':v+=-13 if xx<2 or yy<2 or xx>61 or yy>61 else 0
                if kind=='bark':v+=int(3*math.sin(xx*.33))+(-5 if (xx+yy//19)%23<2 else 0)
                if kind=='leaves':v+=int(9*math.sin(xx*.3)*math.sin(yy*.24))
            draw.point((x+xx,y+yy),fill=tuple(max(0,min(255,c+v)) for c in col)+(255,))
    tiles[name]=[x+.7,y+.7,x+63.3,y+63.3]
for args in [('white',(250,253,255),'flat'),('stone',(203,218,238),'stone'),
             ('paver',(230,235,246),'stone'),('bark',(78,85,114),'bark'),
             ('leaves',(219,239,255),'leaves'),('glass',(192,229,255),'flat'),
             ('blue',(101,156,224),'flat'),('gold',(225,195,132),'flat'),
             ('navy',(40,68,108),'flat'),('sand',(94,200,255),'flat'),
             ('green',(89,142,150),'leaves'),('window',(125,183,217),'flat'),
             ('light',(255,232,182),'flat'),('dark',(31,52,84),'flat')]:texture(*args)
# Continuous soft alpha sprites for actual 3D particles and ground contact shadows.
for name,center,color,power in [('glow',(64,320),(185,229,255),2.8),('shadow',(192,320),(26,44,77),1.5)]:
    cx,cy=center
    for yy in range(cy-64,cy+64):
        for xx in range(cx-64,cx+64):
            r2=((xx-cx)/60)**2+((yy-cy)/60)**2
            a=int(255*max(0,1-r2)**power)
            draw.point((xx,yy),fill=tuple(color)+(a,))
    tiles[name]=[cx-63.5,cy-63.5,cx+63.5,cy+63.5]
buffer=io.BytesIO();atlas.save(buffer,format='PNG');save(TEX/'stage.png',buffer.getvalue())
meshes={k:[] for k in ['stage','glow','bomi','prop','glass','petals']}
FACE=[[4,5,6,7],[1,0,3,2],[5,1,2,6],[0,4,7,3],[7,6,2,3],[0,1,5,4]]
def polygon(group,points,color=(1,1,1,1),tile='white',bone=-1,mode=0,uv=None):
    pp=np.asarray(points,float);normal=np.cross(pp[1]-pp[0],pp[2]-pp[0]);normal/=np.linalg.norm(normal)
    if uv is None:
        x,y,X,Y=tiles[tile]
        uv=np.array([[x,Y],[X,Y],[X,y],[x,y]])/N
        if len(pp)!=4:uv=np.array([[.5,.5]]*len(pp))
    for i in range(1,len(pp)-1):
        for j in [0,i,i+1]:meshes[group].append([*pp[j],*normal,*uv[j],*color,bone,mode])
def cube(center,size,col=(1,1,1,1),tile='white',group='stage',bone=-1,rot=None,mode=0):
    v=np.array([[-1,-1,-1],[1,-1,-1],[1,1,-1],[-1,1,-1],[-1,-1,1],[1,-1,1],[1,1,1],[-1,1,1]])*np.array(size)/2
    if rot is not None:v=v@np.asarray(rot).T
    v+=center
    for f in FACE:polygon(group,v[f],col,tile,bone,mode)
def rod(a,b,width,col,tile='white',group='stage',bone=-1,depth=None):
    a=np.array(a);b=np.array(b);v=b-a;y=v/np.linalg.norm(v);x=np.cross(y,[0,0,1])
    if np.linalg.norm(x)<1e-6:x=np.cross(y,[1,0,0])
    x/=np.linalg.norm(x);z=np.cross(x,y)
    cube((a+b)/2,[width,np.linalg.norm(v)+.003,depth or width],col,tile,group,bone,np.column_stack([x,y,z]))
def ring_mesh(profile,center,col,tile='white',group='stage',bone=-1,n=16,mode=0):
    # profile: (Y offset, radius); disk caps plus outward side faces.
    c=np.array(center);rows=[np.array([[math.sin(a)*r,y,math.cos(a)*r] for a in np.linspace(0,math.tau,n,endpoint=False)])+c for y,r in profile]
    for j in range(len(rows)-1):
        for i in range(n):k=(i+1)%n;polygon(group,[rows[j][i],rows[j+1][i],rows[j+1][k],rows[j][k]],col,tile,bone,mode)
    polygon(group,list(reversed(rows[0])),col,tile,bone,mode)
    polygon(group,rows[-1],col,tile,bone,mode)
def flower(x,y,z,size=.12,blue=False):
    # Five small low-poly petals, each with its own lit bevel.
    tint=(.59,.77,1,1) if blue else (.97,.99,1,1)
    for k in range(5):
        a=math.tau*k/5
        cube((x+math.sin(a)*size*.48,y+math.cos(a)*size*.48,z),
             (size*.53,size*.60,size*.20),tint,'white',rot=Rotation.from_euler('z',-a).as_matrix())
    cube((x,y,z+size*.15),(size*.27,size*.27,size*.22),(1,.87,.53,1),'white',mode=1)

# A quiet avenue in the Botand island park; all set pieces are client-side cosmetic geometry.
cube((0,-.15,-9),(30,.25,56),(.72,.86,.9,1),'stone')
for row in range(45):
    z=5-row*.72
    for col in range(7):
        x=-2.16+col*.72+(row%2)*.05
        c=float(rng.uniform(.94,1.04));shadow=1.
        cube((x,-.02,z),(.697,.10,.693),(c*shadow,c*shadow,c*shadow,1),'paver')
for s in [-1,1]:
    cube((s*3.5,-.03,-10),(2.65,.18,36),(.6,.83,.85,1),'green')
    for j in range(32):
        z=5-j*1.0
        cube((s*2.65,.15,z),(.70,.42,.95),(1,1,1,1),'stone')
        cube((s*2.65,.38,z),(.82,.09,1.01),(.98,1,1,1),'paver')
        if j%4==0:
            cube((s*2.62,.63,z),(.70,.55,.70),(.92,.98,1,1),'stone')
            cube((s*2.62,.94,z),(.82,.10,.82),(1,1,1,1),'paver')
    # Formal side buildings give the forest the setting of a dedicated town map.
    for j in range(5):
        z=-4-j*5.5;h=5.0+(j%3)*1.25;x=s*(7.2+(j%2)*.5)
        cube((x,h/2,z),(3.8,h,4.7),(.96,.985,1,1),'stone')
        for y in np.arange(.7,h,.92):
            cube((x,y,z+2.39),(3.86,.13,.18),(.94,.98,1,1),'paver')
            for off in [-1.13,0,1.13]:
                cube((x+off,y+.4,z+2.43),(.70,.62,.06),(.86,.94,1,1),'window')
                cube((x+off,y+.4,z+2.49),(.043,.64,.07),(.95,1,1,1),'paver')
        cube((x,h+.1,z),(4.12,.24,4.95),(.81,.9,1,1),'paver')
        for y in [1.2,2.4,3.6]:cube((x-s*1.95,y,z+1.82),(.16,.70,.46),(.68,.83,1,1),'blue')

def tree(x,z,h,spread,seed):
    tr=np.random.default_rng(seed);sgn=1 if x>0 else -1
    trunk=(x,h*.39,z)
    cube(trunk,(.50,h*.78,.53),(1,1,1,1),'bark')
    for k in range(3):
        y=h*(.44+k*.13)
        end=(x-sgn*(.75+k*.28),y+.73,z+(k-1)*.52)
        rod((x,y,z),end,.27,(1,1,1,1),'bark')
        cube(end,(.28,.46,.3),(.9,1,1,1),'bark')
    # Scatter clusters on an ellipsoidal canopy; branch direction creates an arch.
    crown=np.array([x-sgn*.7,h,z])
    for k in range(105):
        p=tr.normal(size=3);p/=max(np.linalg.norm(p),.001)
        p*=tr.uniform(.28,1)**(1/3);p*=np.array([spread,.84,spread*.9]);p+=crown
        size=tr.uniform(.34,.67)
        tone=tr.choice([0,1,2,3],p=[.32,.30,.25,.13])
        colors=[(.50,.72,1,1),(.72,.84,1,1),(.94,.97,1,1),(.66,.71,.98,1)]
        cube(p,(size,size*.73,size),colors[tone],'leaves',mode=5)
        if k%4==0:
            for t in range(2):
                q=p+tr.uniform(-.3,.3,3)*size;q[1]-=size*.35
                cube(q,(size*.24,size*.24,size*.24),(.96,.98,1,1),'white')
        if k%11==0:
            flower(p[0],p[1]-.13,p[2]+size*.52,.16,k%3==0)
        if k%13==0:
            # Dangling blossom racemes break the block canopy silhouette.
            for j in range(3):
                q=p+np.array([0,-size*.4-j*.13,0])
                cube(q,(.14-j*.025,.14,.15-j*.025),(.81,.88,1,1),'white')
for i,z in enumerate([3,-1.5,-6,-11,-16,-21,-27]):
    for s in [-1,1]:tree(s*(3.45+(i%2)*.32),z,4.1+(i%3)*.33,2.1,410+i*10+s)

for x in [-4,-2,0,2,4]:tree(x,-29,4.4,2.0,720+int(x))

for s in [-1,1]:
    for j,z in enumerate([-1.4,-6,-11,-16]):
        x=s*(1.75+(j%2)*.12)
        for k in range(5):
            yy=3.55-k*.16
            flower(x+.04*math.sin(k*1.3),yy,z,.20-k*.017,k%2==0)

# White-blue flowers, grass blades and warm little lanterns along the avenue.
for i in range(410):
    s=1 if i%2 else -1;x=s*rng.uniform(2.84,4.8);z=rng.uniform(-26,5);y=rng.uniform(.28,.75)
    rod((x,.02,z),(x,y,z),.025,(.52,.74,.79,1),'white')
    flower(x,y,z,float(rng.uniform(.11,.21)),i%3==0)
for s in [-1,1]:
    for z in [1.7,-5.2,-12.2,-19.2]:
        x=s*2.80;rod((x,.85,z),(x,1.66,z),.065,(.35,.45,.57,1),'white')
        cube((x,1.65,z),(.19,.31,.19),(1,1,1,1),'light',mode=1)
        for dx in [-.115,.115]:
            for dz in [-.115,.115]:cube((x+dx,1.65,z+dz),(.037,.36,.037),(.52,.61,.71,1),'white')
        cube((x,1.87,z),(.32,.10,.31),(.4,.52,.66,1),'white')
        # Translucent glow is a real billboard, not a post-processing framebuffer.
        polygon('glow',[(x-.44,1.2,z+.15),(x+.44,1.2,z+.15),(x+.44,2.08,z+.15),(x-.44,2.08,z+.15)],(1,.84,.60,.21),'glow',mode=1)

# Minecraft's native slim PlayerEntityModel, including its original UVs and overlay layers.
manifest=json.loads((OUT/'bomi.native.json').read_text());bones=manifest['bones'];idx={b['name']:i for i,b in enumerate(bones)}
raw=(OUT/'bomi.mesh').read_bytes();meshes['bomi']=np.frombuffer(raw[8:],'<f4').reshape(-1,14).tolist()
chest=idx['body'];hg=(0.,1.10,.565)
# Independently exported prop; share exact topology and materials with runtime.
from rework.hourglass import build_hourglass,export_hourglass
for name,v in build_hourglass().items():
    v=v.copy();v[:,:3]+=np.array(hg);v[:,12]=chest
    meshes['glass' if name=='glass' else 'prop'].extend(v.tolist())
export_hourglass(HERE/'rework/output')

# The face is ONLY the supplied skin; no eyelid meshes or animated facial redraws.
# Empty legacy asset prevents stale resource copies from restoring the old face geometry.
save(OUT/'blink.mesh',struct.pack('<4sI',b'BMS1',0))

# Linear-light shading, fog and cast shadows are evaluated by the GPU at runtime.
sun=np.array([-.40,.83,.39]);sun/=np.linalg.norm(sun)
for group,verts in meshes.items():
    ar=np.asarray(verts,dtype='<f4')
    if len(ar)==0:continue
    meshes[group]=ar
    save(OUT/(group+'.mesh'),struct.pack('<4sI',b'BMS1',len(ar))+ar.tobytes())

meta={'format':1,'stride_floats':14,'bones':bones,'hourglass_center':hg,
      'meshes':{k:len(v) for k,v in meshes.items() if len(v)},'tiles':tiles,
      'sun':sun.tolist(),'sky':[.73,.85,.99],
      'source':'Native Minecraft slim player + unchanged user skin; client-side Botand park set',
      'character':manifest,
      'uses_background_image':False,'post_processing_required':True,'renderer':'Aurora PC HDR + shadow map + depth of field + bloom'}
save(OUT/'scene.json',json.dumps(meta,indent=2).encode())
print(json.dumps(meta['meshes'],indent=2))
