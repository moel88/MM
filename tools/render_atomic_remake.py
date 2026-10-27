"""Actual AtomicActor meshes + remade audio, fixed review camera; not Minecraft footage."""
from pathlib import Path
import ctypes as C,ctypes.util,os,json,io,zipfile,subprocess,struct
import numpy as np
from PIL import Image,ImageDraw,ImageFont
root=Path(__file__).resolve().parents[1];out=root/'build/atomic-remake'
os.environ.setdefault('EGL_PLATFORM','surfaceless');os.environ.setdefault('LIBGL_ALWAYS_SOFTWARE','1')
egl=C.CDLL(ctypes.util.find_library('EGL'));gl=C.CDLL(ctypes.util.find_library('GL'))
I=C.c_int;U=C.c_uint;P=C.c_void_p;F=C.c_float
def fn(lib,n,r,a):f=getattr(lib,n);f.restype=r;f.argtypes=a;return f
def g(n,r,a):return fn(gl,n,r,a)
display=fn(egl,'eglGetDisplay',P,[P])(None);assert fn(egl,'eglInitialize',U,[P,C.POINTER(I),C.POINTER(I)])(display,None,None)
assert fn(egl,'eglBindAPI',U,[U])(0x30A2)
config=P();count=I();attrs=(I*15)(0x3033,1,0x3040,8,0x3024,8,0x3023,8,0x3022,8,0x3021,8,0x3025,24,0x3038)
assert fn(egl,'eglChooseConfig',U,[P,C.POINTER(I),C.POINTER(P),I,C.POINTER(I)])(display,attrs,C.byref(config),1,C.byref(count))
ctx=fn(egl,'eglCreateContext',P,[P,P,P,C.POINTER(I)])(display,config,None,(I*7)(0x3098,3,0x30FB,2,0x30FD,1,0x3038))
w,h=960,540
surf=fn(egl,'eglCreatePbufferSurface',P,[P,P,C.POINTER(I)])(display,config,(I*5)(0x3057,w,0x3056,h,0x3038));assert fn(egl,'eglMakeCurrent',U,[P,P,P,P])(display,surf,surf,ctx)
program=g('glCreateProgram',U,[])()
for kind,src in [(0x8B31,b'#version 150\nin vec3 p;in vec2 uv;in vec3 normal;uniform mat4 mvp;out vec2 tex;out float shade;void main(){gl_Position=mvp*vec4(p,1);tex=uv;shade=.90+.10*max(0.,dot(normalize(normal),normalize(vec3(-1,2,-3))));}'),(0x8B30,b'#version 150\nin vec2 tex;in float shade;uniform sampler2D skin;out vec4 frag;void main(){vec4 color=texture(skin,tex);if(color.a<.1)discard;frag=vec4(color.rgb*shade,1);}')] :
 s=g('glCreateShader',U,[U])(kind);raw=C.c_char_p(src);g('glShaderSource',None,[U,I,C.POINTER(C.c_char_p),P])(s,1,C.byref(raw),None);g('glCompileShader',None,[U])(s)
 ok=I();g('glGetShaderiv',None,[U,U,C.POINTER(I)])(s,0x8B81,C.byref(ok));assert ok.value;g('glAttachShader',None,[U,U])(program,s)
g('glLinkProgram',None,[U])(program);g('glUseProgram',None,[U])(program)
vao=U();vbo=U();g('glGenVertexArrays',None,[I,C.POINTER(U)])(1,C.byref(vao));g('glBindVertexArray',None,[U])(vao);g('glGenBuffers',None,[I,C.POINTER(U)])(1,C.byref(vbo));g('glBindBuffer',None,[U,U])(0x8892,vbo)
for name,size,offset in [(b'p',3,0),(b'uv',2,12),(b'normal',3,20)]:
 loc=g('glGetAttribLocation',I,[U,C.c_char_p])(program,name);g('glEnableVertexAttribArray',None,[U])(loc);g('glVertexAttribPointer',None,[U,I,U,U,I,P])(loc,size,0x1406,0,32,P(offset))

jar=next((root/'.gradle/loom-cache/minecraftMaven').rglob('*.jar'))
with zipfile.ZipFile(jar) as z: face=Image.open(io.BytesIO(z.read('assets/minecraft/textures/entity/player/wide/steve.png'))).convert('RGBA')
assets=root/'src/main/resources/assets/celestialfall/textures/entity'
textures=[]
for skin in [Image.open(assets/'atomic_costume.png').convert('RGBA'),face,Image.open(assets/'atomic_blade.png').convert('RGBA')]:
 tex=np.asarray(skin).copy();texture=U();g('glGenTextures',None,[I,C.POINTER(U)])(1,C.byref(texture));g('glBindTexture',None,[U,U])(0x0DE1,texture)
 g('glTexImage2D',None,[U,I,I,I,I,I,U,U,P])(0x0DE1,0,0x1908,skin.width,skin.height,0,0x1908,0x1401,tex.ctypes.data)
 for axis in [0x2800,0x2801]:g('glTexParameteri',None,[U,U,I])(0x0DE1,axis,0x2600)
 textures.append(texture)
# Color-only shader for the exact runtime column meshes.
color_program=g('glCreateProgram',U,[])()
for kind,src in [(0x8B31,b'#version 150\nin vec3 p;in vec4 col;uniform mat4 mvp;out vec4 tint;void main(){gl_Position=mvp*vec4(p,1);tint=col;}'),(0x8B30,b'#version 150\nin vec4 tint;out vec4 frag;void main(){frag=tint;}')]:
 shader=g('glCreateShader',U,[U])(kind);raw=C.c_char_p(src);g('glShaderSource',None,[U,I,C.POINTER(C.c_char_p),P])(shader,1,C.byref(raw),None);g('glCompileShader',None,[U])(shader)
 ok=I();g('glGetShaderiv',None,[U,U,C.POINTER(I)])(shader,0x8B81,C.byref(ok));assert ok.value;g('glAttachShader',None,[U,U])(color_program,shader)
g('glLinkProgram',None,[U])(color_program)
color_vao=U();color_vbo=U();g('glGenVertexArrays',None,[I,C.POINTER(U)])(1,C.byref(color_vao));g('glBindVertexArray',None,[U])(color_vao);g('glGenBuffers',None,[I,C.POINTER(U)])(1,C.byref(color_vbo));g('glBindBuffer',None,[U,U])(0x8892,color_vbo)
for name,size,offset in [(b'p',3,0),(b'col',4,12)]:
 loc=g('glGetAttribLocation',I,[U,C.c_char_p])(color_program,name);g('glEnableVertexAttribArray',None,[U])(loc);g('glVertexAttribPointer',None,[U,I,U,U,I,P])(loc,size,0x1406,0,28,P(offset))
g('glEnable',None,[U])(0x0B71);g('glDisable',None,[U])(0x0B44);g('glViewport',None,[I,I,I,I])(0,0,w,h)
def matrix(shot):
 radius,height,focus,az,fov,_=shot;angle=np.deg2rad(90+az);eye=np.array([np.cos(angle)*radius,height,np.sin(angle)*radius])
 target=np.array([0,focus,0]);f=target-eye;f/=np.linalg.norm(f);r=np.cross(f,[0,1,0]);r/=np.linalg.norm(r);u=np.cross(r,f)
 view=np.eye(4);view[:3,:3]=[r,u,-f];view[:3,3]=-view[:3,:3]@eye
 scale=1/np.tan(np.deg2rad(fov)/2);near,far=.05,1800
 proj=np.array([[scale/(w/h),0,0,0],[0,scale,0,0],[0,0,-(far+near)/(far-near),-2*far*near/(far-near)],[0,0,-1,0]])
 return np.ascontiguousarray(proj@view,dtype=np.float32)
def draw(rows,mat,colored=True,texture=None):
 if not len(rows):return
 prog,arr,buf=(color_program,color_vao,color_vbo) if colored else (program,vao,vbo)
 g('glUseProgram',None,[U])(prog);g('glBindVertexArray',None,[U])(arr);g('glBindBuffer',None,[U,U])(0x8892,buf)
 g('glUniformMatrix4fv',None,[I,I,U,P])(g('glGetUniformLocation',I,[U,C.c_char_p])(prog,b'mvp'),1,1,mat.ctypes.data)
 if texture is not None:g('glBindTexture',None,[U,U])(0x0DE1,texture)
 rows=np.ascontiguousarray(rows.reshape(-1,4,rows.shape[1])[:,[0,1,2,0,2,3]].reshape(-1,rows.shape[1]))
 g('glBufferData',None,[U,C.c_ssize_t,P,U])(0x8892,rows.nbytes,rows.ctypes.data,0x88E8);g('glDrawArrays',None,[U,I,I])(4,0,len(rows))
stage=[]
def quad(a,b,c,d,rgb):
 color=[(rgb>>16&255)/255,(rgb>>8&255)/255,(rgb&255)/255,1];stage.extend([list(p)+color for p in [a,b,c,d]])
def box(x,z,width,depth,height):
 x0=x-width/2;x1=x+width/2;z0=z-depth/2;z1=z+depth/2
 quad((x0,0,z0),(x1,0,z0),(x1,height,z0),(x0,height,z0),0x576978)
 quad((x0,0,z1),(x1,0,z1),(x1,height,z1),(x0,height,z1),0x798A96)
 quad((x0,0,z0),(x0,0,z1),(x0,height,z1),(x0,height,z0),0x657784)
 quad((x1,0,z0),(x1,0,z1),(x1,height,z1),(x1,height,z0),0x8195A0)
 quad((x0,height,z0),(x1,height,z0),(x1,height,z1),(x0,height,z1),0xA5B4BD)
quad((-110,-.05,-110),(110,-.05,-110),(110,-.05,110),(-110,-.05,110),0x73818A)
for x in range(-80,81,8):quad((x,-.04,-80),(x+.03,-.04,-80),(x+.03,-.04,80),(x,-.04,80),0x87959D)
for z in range(-80,81,8):quad((-80,-.04,z),(80,-.04,z),(80,-.04,z+.03),(-80,-.04,z+.03),0x87959D)
for x in [-29,29]:quad((x,0,-22),(x+.3,0,-22),(x+.3,0,22),(x,0,22),0xEF5C79)
for z in [-22,22]:quad((-29,0,z),(29,0,z),(29,0,z+.3),(-29,0,z+.3),0xEF5C79)
for x in [-72,-48,-24,0,24,48,72]:box(x,-62,14,12,12+(x//24%3)*6)
for z in [-30,0,30]:box(-64,z,12,18,17);box(64,z,12,18,13)
stage=np.asarray(stage,np.float32)
font='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf';large=ImageFont.truetype(font,24);small=ImageFont.truetype(font,14)
file=(out/'scene.bin').open('rb');frames,fps=struct.unpack('>ii',file.read(8))
cmd=['ffmpeg','-v','error','-y','-f','rawvideo','-pix_fmt','rgb24','-s',f'{w}x{h}','-r',str(fps),'-i','-','-i',str(root/'build/audio-remake/Atomic_Sound_Remake.wav'),'-t','37','-c:v','libx264','-preset','fast','-crf','20','-c:a','aac','-b:a','160k','-pix_fmt','yuv420p','-movflags','+faststart',str(out/'Botand_Atomic_Updraft_2.6.1.mp4')]
process=subprocess.Popen(cmd,stdin=subprocess.PIPE);stills=[]
chosen={185:'7.71 s / COLUMN BEHIND WHITE',363:'15.13 s / RESTORED LUMINOUS CORE',400:'16.67 s / FAST UPWARD STREAMS',402:'16.75 s / FAST UPWARD STREAMS',405:'16.88 s / FAST UPWARD STREAMS',792:'33 s / WIDTH 56 - HEIGHT 570',840:'35 s / WIDTH 28 - HEIGHT 570',876:'36.5 s / WIDTH 2.4 - HEIGHT 570'}
overview=None
for i in range(frames):
 age=struct.unpack('>f',file.read(4))[0];shot=struct.unpack('>6f',file.read(24));flash=struct.unpack('>?',file.read(1))[0]
 meshes=[]
 for size in [8,8,8,7,7,7]:
  count=struct.unpack('>i',file.read(4))[0];meshes.append(np.frombuffer(file.read(count*size*4),dtype='>f4').astype(np.float32).reshape(-1,size))
 mat=matrix(shot)
 g('glDepthMask',None,[U])(1);g('glDisable',None,[U])(0x0BE2);g('glClearColor',None,[F,F,F,F])(.14,.18,.27,1);g('glClear',None,[U])(0x4100)
 draw(stage,mat)
 for tex,mesh in zip(textures,meshes[:3]):draw(mesh,mat,False,tex)
 draw(meshes[3],mat);g('glDepthMask',None,[U])(0);g('glEnable',None,[U])(0x0BE2)
 g('glBlendFunc',None,[U,U])(0x0302,0x0303);draw(meshes[4],mat)
 g('glBlendFunc',None,[U,U])(0x0302,1);draw(meshes[5],mat)
 pixels=np.empty((h,w,4),np.uint8);g('glReadPixels',None,[I,I,I,I,U,U,P])(0,0,w,h,0x1908,0x1401,pixels.ctypes.data)
 scene=Image.fromarray(pixels[::-1]).convert('RGB');image=scene.copy()
 if flash:
  image.paste((255,255,255),(0,0,w,h));image.paste(scene.resize((320,180)),(w-336,90))
  d=ImageDraw.Draw(image);d.rectangle((w-336,270,w-16,301),fill='#241340');d.text((w-328,278),'WORLD BEHIND WHITE / review inset',font=small,fill='white')
 d=ImageDraw.Draw(image);d.rectangle((0,0,w,70),fill='#100D1E');d.text((22,12),'BOTAND 2.6.1 / ATOMIC UPDRAFT',font=large,fill='#EBD7FF')
 d.text((22,46),'Actual mod actor + column meshes | review stage | not Minecraft gameplay footage',font=small,fill='#AFA1C5')
 d.rectangle((0,h-51,w,h),fill='#100D1E')
 phase='RAISE / CHANT / SLASH' if age<154 else 'WHITE + ACTIVE COLUMN' if flash else 'FULL POWER' if age<660 else 'HORIZONTAL CONTRACTION'
 d.text((22,h-40),f'{age/20:05.2f}s  |  {phase}',font=small,fill='#DEC1FF')
 d.text((22,h-20),f'Diameter {shot[5]*2:05.1f} blocks   /   column height 570 blocks   /   37 second skill',font=small,fill='#ABA0BB')
 process.stdin.write(image.tobytes())
 if i in chosen:
  review=scene.copy();rd=ImageDraw.Draw(review);rd.rectangle((0,0,w,42),fill='#160E27');rd.text((18,10),chosen[i],font=large,fill='#EBD7FF');stills.append(review)
 if i==363:
  overview_matrix=matrix((730,200,285,25,60,28))
  g('glDepthMask',None,[U])(1);g('glDisable',None,[U])(0x0BE2);g('glClear',None,[U])(0x4100)
  draw(stage,overview_matrix);draw(meshes[3],overview_matrix)
  g('glDepthMask',None,[U])(0);g('glEnable',None,[U])(0x0BE2);g('glBlendFunc',None,[U,U])(0x0302,0x0303);draw(meshes[4],overview_matrix)
  g('glBlendFunc',None,[U,U])(0x0302,1);draw(meshes[5],overview_matrix)
  g('glReadPixels',None,[I,I,I,I,U,U,P])(0,0,w,h,0x1908,0x1401,pixels.ctypes.data)
  overview=Image.fromarray(pixels[::-1]).convert('RGB');od=ImageDraw.Draw(overview)
  od.rectangle((0,0,w,42),fill='#160E27');od.text((18,10),'570 BLOCKS / separate height review camera',font=large,fill='#EBD7FF')
process.stdin.close();assert process.wait()==0
stills.append(overview)
sheet=Image.new('RGB',(1440,884),'#100D1E');d=ImageDraw.Draw(sheet);d.text((20,16),'BOTAND 2.6.1 / actual mesh review - white overlay omitted in this sheet',font=large,fill='#EBD7FF')
for i,im in enumerate(stills):sheet.paste(im.resize((480,270)),((i%3)*480,74+(i//3)*270))
sheet.save(out/'Botand_Atomic_Updraft_2.6.1.png')
print('Rendered 889 actual actor/column frames; review inset labels the hidden world during white exposure.')
