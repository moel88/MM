"""Faithful preview of shipped runtime meshes, CPU skinning, vertex light and camera."""
from pathlib import Path
import json,struct,math,io,sys,os
import numpy as np
import moderngl
from PIL import Image,ImageDraw,ImageFont,ImageFilter
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[1];RES=ROOT/'src/main/resources/assets/celestialfall';OUT=ROOT/'build/title-cinema';OUT.mkdir(parents=True,exist_ok=True)
meta=json.loads((RES/'cinema/scene.json').read_text());meshes={}
for name in meta['meshes']:
 raw=(RES/'cinema'/f'{name}.mesh').read_bytes();meshes[name]=np.frombuffer(raw[8:],'<f4').reshape(-1,14).copy()
raw=(RES/'cinema/performance.anim').read_bytes();_,n,bones,fps,intro,loop=struct.unpack_from('<4sIIIff',raw);stride=bones*12+9;frames=np.frombuffer(raw[24:],'<f4').reshape(n,stride)
ctx=moderngl.create_standalone_context(backend='egl')
prog=ctx.program(vertex_shader='''#version 330
in vec3 p;in vec2 uv;in vec4 col;uniform mat4 vp;out vec2 t;out vec4 c;
void main(){t=uv;c=col;gl_Position=vp*vec4(p,1.);}
''',fragment_shader='''#version 330
uniform sampler2D tex;in vec2 t;in vec4 c;out vec4 color;
void main(){color=texture(tex,t)*c;if(color.a<.004)discard;}
''')
textures={}
for name,file in [('stage','stage.png'),('bomi','bomi.png')]:
 im=Image.open(RES/'textures/cinema'/file).convert('RGBA');tex=ctx.texture(im.size,4,im.tobytes());tex.filter=(moderngl.LINEAR,moderngl.LINEAR);tex.repeat_x=False;tex.repeat_y=False;textures[name]=tex
prog['tex']=0
static={}
for name in ['stage','glow']:
 a=meshes[name];d=np.column_stack([a[:,:3],a[:,6:12]]).astype('f4');vb=ctx.buffer(d.tobytes());static[name]=(vb,ctx.vertex_array(prog,[(vb,'3f 2f 4f','p','uv','col')]))
def normalize(v):return np.asarray(v)/np.linalg.norm(v)
def view(eye,target):
 f=normalize(target-eye);s=normalize(np.cross(f,[0,1,0]));u=np.cross(s,f);m=np.eye(4);m[:3,:3]=[s,u,-f];m[:3,3]=-m[:3,:3]@eye;return m
def projection(fov,aspect):
 f=1/math.tan(math.radians(fov)/2);n=.05;far=90
 return np.array([[f/aspect,0,0,0],[0,f,0,0],[0,0,-(far+n)/(far-n),-2*far*n/(far-n)],[0,0,-1,0]])
def sample(t):
 t=t if t<intro else intro+(t-intro)%loop;k=t*fps;i=int(k);return frames[i]*(1-(k-i))+frames[min(i+1,n-1)]*(k-i)
def transformed(name,m,blink):
 a=meshes[name];b=a[:,12].astype(int);v=np.einsum('nij,nj->ni',m[b],np.column_stack([a[:,:3],np.ones(len(a))]));normal=np.einsum('nij,nj->ni',m[b,:,:3],a[:,3:6]);shade=np.where(a[:,13]>.5,1,.66+.31*np.maximum(0,normal@np.array(meta['sun']))+.09*np.maximum(0,normal@normalize([.5,.3,-.8])))
 c=a[:,8:12].copy();c[:,:3]*=shade[:,None]
 return np.column_stack([v,a[:,6:8],c]).astype('f4')
def quad(points,uv,color):return np.array([[*points[i],*uv[i],*color] for i in [0,1,2,0,2,3]],dtype='f4')
def drawdata(data,tex):
 textures[tex].use(0);vb=ctx.buffer(data.tobytes());vao=ctx.vertex_array(prog,[(vb,'3f 2f 4f','p','uv','col')]);vao.render();vao.release();vb.release()
def render(t,width=1536,height=864,ui=True):
 frame=sample(t);m=frame[:bones*12].reshape(bones,3,4);ex=frame[bones*12:];eye=ex[:3].copy();target=ex[3:6].copy();fov=ex[6]
 if width/height<1.25:
  eye[0]=.15;target[0]=-.08;eye[2]+=1.15;target[1]=1.42;fov=49
 vp=projection(fov,width/height)@view(eye,target);prog['vp'].write(vp.astype('f4').T.tobytes())
 color=ctx.texture((width,height),4);depth=ctx.depth_renderbuffer((width,height));fb=ctx.framebuffer([color],depth);fb.use();ctx.viewport=(0,0,width,height);fb.clear(*meta['sky'],1,depth=1);ctx.enable(moderngl.DEPTH_TEST);ctx.disable(moderngl.CULL_FACE);ctx.disable(moderngl.BLEND)
 textures['stage'].use(0);static['stage'][1].render()
 # Ground contact shadow, same soft-alpha 3D quad as runtime.
 ctx.enable(moderngl.BLEND);ctx.blend_func=(moderngl.SRC_ALPHA,moderngl.ONE_MINUS_SRC_ALPHA)
 root=m[0,:,3];x=root[0];z=root[2];r=.43
 uv=np.array([[128.5,383.5],[255.5,383.5],[255.5,256.5],[128.5,256.5]])/512
 drawdata(quad([(x-r,.036,z+r),(x+r,.036,z+r),(x+r,.036,z-r),(x-r,.036,z-r)],uv,(1,1,1,.34)),'stage')
 ctx.disable(moderngl.BLEND)
 for name,tex in [('bomi','bomi'),('prop','stage')]:drawdata(transformed(name,m,ex[7]),tex)
 if ex[7]>.55:drawdata(transformed('blink',m,ex[7]),'stage')
 ctx.enable(moderngl.BLEND);ctx.depth_mask=False
 drawdata(transformed('glass',m,ex[7]),'stage');textures['stage'].use(0);static['glow'][1].render()
 # Petals drift through the actual set, deliberately absent from menu hit areas.
 pieces=[]
 for i in range(52):
  x=math.sin(i*7.4)*4+.18*math.sin(t*.45+i);y=.3+((i*.731-t*.065)%3.9);z=2-(i*1.373%25);r=.017+(i%3)*.007;a=t*.6+i
  points=[(x-r*math.cos(a),y-r,z),(x+r*math.cos(a),y-r*.2,z+.01),(x+r*.7*math.cos(a),y+r,z),(x-r*.7*math.cos(a),y+r*.4,z)]
  pieces.extend(quad(points,[(.02,.02)]*4,(.91,.97,1,.65)))
 drawdata(np.array(pieces,dtype='f4'),'stage');ctx.depth_mask=True
 im=Image.frombytes('RGB',(width,height),fb.read(components=3)).transpose(Image.Transpose.FLIP_TOP_BOTTOM)
 fb.release();depth.release();color.release()
 if ui:im=overlay(im)
 return im

def overlay(im,mode=None):
 import importlib.util
 spec=importlib.util.spec_from_file_location('title_overlay',ROOT/'tools/render_botand_title.py');ui=importlib.util.module_from_spec(spec);spec.loader.exec_module(ui)
 w,h=im.size;name=mode or ('portrait' if w/h<1.18 else 'mobile' if w==1280 else 'desktop')
 data=json.loads((OUT/'ui-scenes.json').read_text())[name]
 # Same 32 full-viewport shade bands, with non-overlapping raster edges.
 layer=Image.new('RGBA',im.size);d=ImageDraw.Draw(layer)
 for i in range(32):
  d.rectangle([round(w*i/64),0,round(w*(i+1)/64)-1,h-1],fill=(7,23,42,int(100*(1-i/31)**2)))
 im=Image.alpha_composite(im.convert('RGBA'),layer)
 data['scene']['commands']=data['scene']['commands'][32:]
 overlay=ui.render(data,pixel_scale=1)
 if overlay.size!=im.size:overlay=overlay.resize(im.size,Image.Resampling.LANCZOS)
 return Image.alpha_composite(im,overlay).convert('RGB')
def save_image(im,path):
 b=io.BytesIO();im.save(b,format='PNG');payload=b.getvalue()
 with Path(path).open('wb') as f:f.write(payload);f.flush();os.fsync(f.fileno())
 Image.open(path).verify()

if __name__=='__main__':
 for t in [0,2,6.8,11.7]:
  im=render(t);save_image(im,OUT/f'cinema-{t:g}.png')
 print('Preview renderer:',ctx.info['GL_RENDERER'])
