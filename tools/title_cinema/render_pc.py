"""Review renderer: exact shipped PC shaders, geometry, animation, lighting and post chain.
Actual Minecraft captures remain the integration evidence, this generates review videos.
"""
from pathlib import Path
import json,struct,math,io,os
import numpy as np
import moderngl
from PIL import Image,ImageDraw
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[1];RES=ROOT/'src/main/resources/assets/celestialfall';OUT=ROOT/'build/title-cinema';OUT.mkdir(parents=True,exist_ok=True)
SH=RES/'cinema/shaders';meta=json.loads((RES/'cinema/scene.json').read_text())
raw=(RES/'cinema/performance.anim').read_bytes();_,count,bones,fps,intro,loop=struct.unpack_from('<4sIIIff',raw);frames=np.frombuffer(raw[24:],'<f4').reshape(count,bones*12+9)
ctx=moderngl.create_standalone_context(backend='egl')
def program(v,f):return ctx.program(vertex_shader=(SH/v).read_text(),fragment_shader=(SH/f).read_text())
lit=program('scene.vert','scene.frag');shadowprog=program('scene.vert','shadow.frag');blur=program('screen.vert','blur.frag');comp=program('screen.vert','composite.frag')
def setu(p,k,v):
 if k in p:p[k].value=v
def matrix(p,k,m):
 if k in p:p[k].write(np.array(m,dtype='f4').T.tobytes())
textures={}
for name in ['stage','bomi']:
 im=Image.open(RES/'textures/cinema'/f'{name}.png').convert('RGBA');tex=ctx.texture(im.size,4,im.tobytes());tex.filter=(moderngl.NEAREST,moderngl.NEAREST) if name=='bomi' else (moderngl.LINEAR,moderngl.LINEAR);tex.repeat_x=False;tex.repeat_y=False;textures[name]=tex
meshes={};vaos={};shadowvaos={}
def addmesh(name,data):
 meshes[name]=data;vb=ctx.buffer(np.asarray(data,'f4').tobytes());fmt=[(vb,'3f 3f 2f 4f 1f 1f','Position','Normal','UV','Color','Bone','Material')]
 vaos[name]=ctx.vertex_array(lit,fmt,skip_errors=True);shadowvaos[name]=ctx.vertex_array(shadowprog,fmt,skip_errors=True)
for name in meta['meshes']:
 raw=(RES/'cinema'/f'{name}.mesh').read_bytes();addmesh(name,np.frombuffer(raw[8:],'<f4').reshape(-1,14))
pp=[]
for i in range(80):
 x=math.sin(i*7.4)*4;y=.3+(i*.731%3.8);z=2-(i*1.373%25);r=.017+(i%3)*.007
 points=[(x-r,y-r,z),(x+r,y-r*.2,z+.01),(x+r*.7,y+r,z),(x-r*.7,y+r*.4,z)]
 for j in [0,1,2,0,2,3]:pp.append([*points[j],0,0,1,.02,.02,.91,.97,1,.72,-1,6])
addmesh('petals',np.array(pp,'f4'))
shadow=ctx.depth_texture((2048,2048));shadow.compare_func='<=';shadow.filter=(moderngl.LINEAR,moderngl.LINEAR);shadow.repeat_x=False;shadow.repeat_y=False;shadowfb=ctx.framebuffer(depth_attachment=shadow)
quadblur=ctx.vertex_array(blur,[]);quadcomp=ctx.vertex_array(comp,[])
def normalize(v):return np.asarray(v)/np.linalg.norm(v)
def view(eye,target):
 eye=np.array(eye);f=normalize(np.array(target)-eye);s=normalize(np.cross(f,[0,1,0]));u=np.cross(s,f);m=np.eye(4);m[:3,:3]=[s,u,-f];m[:3,3]=-m[:3,:3]@eye;return m
def ortho(l,r,b,t,n,f):return np.array([[2/(r-l),0,0,-(r+l)/(r-l)],[0,2/(t-b),0,-(t+b)/(t-b)],[0,0,-2/(f-n),-(f+n)/(f-n)],[0,0,0,1]])
def projection(fov,aspect):
 f=1/math.tan(math.radians(fov)/2);n=.05;far=90
 return np.array([[f/aspect,0,0,0],[0,f,0,0],[0,0,-(far+n)/(far-n),-2*far*n/(far-n)],[0,0,-1,0]])
def sample(t):
 t=t if t<intro else intro+(t-intro)%loop;k=t*fps;i=int(k);return frames[i]*(1-k+i)+frames[min(i+1,count-1)]*(k-i)
targets={}
def gettargets(width,height):
 if (width,height) in targets:return targets[width,height]
 hdr=ctx.texture((width,height),4,dtype='f2');depth=ctx.depth_texture((width,height));depth.compare_func='';depth.repeat_x=False;depth.repeat_y=False
 fb=ctx.framebuffer([hdr],depth);small=(max(1,width//4),max(1,height//4));p=ctx.texture(small,4,dtype='f2');q=ctx.texture(small,4,dtype='f2');pf=ctx.framebuffer([p]);qf=ctx.framebuffer([q]);c=ctx.texture((width,height),4);cf=ctx.framebuffer([c])
 for tex in [hdr,p,q,depth]:tex.filter=(moderngl.LINEAR,moderngl.LINEAR);tex.repeat_x=False;tex.repeat_y=False
 val=(hdr,depth,fb,p,q,pf,qf,cf);targets[width,height]=val;return val
def render(t,width=1536,height=864,ui=True,model=False,prop=False):
 frame=sample(t);m=np.tile(np.eye(4),(32,1,1));m[:bones,:3,:]=frame[:bones*12].reshape(bones,3,4);ex=frame[bones*12:];eye=ex[:3].copy();target=ex[3:6].copy();fov=float(ex[6])
 if width/height<1.25:eye[0]=.15;target[0]=-.08;eye[2]+=1.15;target[1]=1.42;fov=49
 names=['stage','bomi','prop'];transparent=['glass','glow','petals']
 if model:
  m=np.tile(np.eye(4),(32,1,1));eye=np.array([.18,1.4,3.8]);target=np.array([0,1.02,0]);fov=32;names=['bomi'];transparent=[]
 if prop:
  m=np.tile(np.eye(4),(32,1,1));eye=np.array(meta['hourglass_center'])+np.array([.455,.26,.905]);target=np.array(meta['hourglass_center']);fov=35;names=['prop'];transparent=['glass']
 vp=projection(fov,width/height)@view(eye,target);z=m[0,2,3]-2;light=ortho(-7,7,-7,7,.1,40)@view([-6.4,14.28,z+6.24],[0,1,z])
 if prop:light=ortho(-.8,.8,-.8,.8,.1,20)@view([-4,7,4],meta['hourglass_center'])
 for p in [lit,shadowprog]:
  matrix(p,'ViewProjection',vp if p==lit else light);matrix(p,'LightMatrix',light);setu(p,'Time',t)
  if 'Bones' in p:p['Bones'].write(m.transpose(0,2,1).astype('f4').tobytes())
 shadowfb.use();shadowfb.clear(depth=1);ctx.viewport=(0,0,2048,2048);ctx.enable(moderngl.DEPTH_TEST);ctx.disable(moderngl.BLEND|moderngl.CULL_FACE);ctx.depth_func='<=';ctx.polygon_offset=(1.2,2)
 setu(shadowprog,'Atlas',0)
 for name in names:textures['bomi' if name=='bomi' else 'stage'].use(0);shadowvaos[name].render()
 ctx.polygon_offset=(0,0)
 hdr,depth,fb,p,q,pf,qf,cf=gettargets(width,height);fb.use();fb.depth_mask=True;ctx.viewport=(0,0,width,height);fb.clear(.54,.71,1.02,1,depth=1)
 if model or prop:fb.clear(.17,.25,.42,1,depth=1)
 setu(lit,'Camera',tuple(eye));setu(lit,'Atlas',0);setu(lit,'ShadowMap',1);shadow.use(1)
 for name in names:textures['bomi' if name=='bomi' else 'stage'].use(0);vaos[name].render()
 ctx.enable(moderngl.BLEND);ctx.blend_func=(moderngl.SRC_ALPHA,moderngl.ONE_MINUS_SRC_ALPHA,moderngl.ONE,moderngl.ONE_MINUS_SRC_ALPHA);fb.depth_mask=False;textures['stage'].use(0)
 for name in transparent:vaos[name].render()
 fb.depth_mask=True;ctx.disable(moderngl.BLEND|moderngl.DEPTH_TEST)
 setu(blur,'Source',0)
 for i in range(4):
  (pf if i%2==0 else qf).use();ctx.viewport=(0,0,*p.size);(hdr if i==0 else q if i%2==0 else p).use(0);setu(blur,'Extract',int(i==0));setu(blur,'Direction',(1.4/p.width,0) if i%2==0 else (0,1.4/p.height));quadblur.render(vertices=3)
 cf.use();ctx.viewport=(0,0,width,height);hdr.use(0);q.use(1);depth.use(2);setu(comp,'Source',0);setu(comp,'Bloom',1);setu(comp,'Depth',2);setu(comp,'Resolution',(width,height));setu(comp,'Focus',float(np.linalg.norm(eye[[0,2]]-m[0,[0,2],3])));quadcomp.render(vertices=3)
 im=Image.frombytes('RGB',(width,height),cf.read(components=3)).transpose(Image.Transpose.FLIP_TOP_BOTTOM)
 if ui:
  # Reuse only the UI command rasterizer; scene images come from the current PC pipeline.
  im=overlay(im)
 return im
_ui_source=(HERE/'render_scene.py').read_text()
exec(_ui_source[_ui_source.index('def overlay('):_ui_source.index('def save_image(')],globals())
def save_image(im,path):
 b=io.BytesIO();im.save(b,format='PNG')
 with Path(path).open('wb') as f:f.write(b.getvalue());f.flush();os.fsync(f.fileno())
if __name__=='__main__':
 for t in [0,2,7.3,11.7]:save_image(render(t),OUT/f'pc-{t:g}.png')
 save_image(render(7.3,1000,1300,False,True),OUT/'pc-model.png')
 save_image(render(7.3,1000,1100,False,False,True),OUT/'pc-hourglass.png')
 print(ctx.info['GL_RENDERER'])
