"""Render actual baked Java animation meshes through EGL. Not a Minecraft gameplay capture."""
from pathlib import Path
import ctypes as C,ctypes.util,os,json,io,zipfile,subprocess
import numpy as np
from PIL import Image,ImageDraw,ImageFont
root=Path(__file__).resolve().parents[1];out=root/'build/botand-dummies';out.mkdir(exist_ok=True)
data=json.loads((out/'meshes.json').read_text())
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
w,h=400,460
surf=fn(egl,'eglCreatePbufferSurface',P,[P,P,C.POINTER(I)])(display,config,(I*5)(0x3057,w,0x3056,h,0x3038));assert fn(egl,'eglMakeCurrent',U,[P,P,P,P])(display,surf,surf,ctx)
program=g('glCreateProgram',U,[])()
for kind,src in [(0x8B31,b'#version 150\nin vec3 p;in vec2 uv;in vec3 normal;uniform mat4 mvp;out vec2 tex;out float shade;void main(){gl_Position=mvp*vec4(p,1);tex=uv;shade=.65+.35*max(0.,dot(normalize(normal),normalize(vec3(-1,2,-3))));}'),(0x8B30,b'#version 150\nin vec2 tex;in float shade;uniform sampler2D skin;out vec4 frag;void main(){vec4 color=texture(skin,tex);if(color.a<.1)discard;frag=vec4(color.rgb*shade,1);}')] :
 s=g('glCreateShader',U,[U])(kind);raw=C.c_char_p(src);g('glShaderSource',None,[U,I,C.POINTER(C.c_char_p),P])(s,1,C.byref(raw),None);g('glCompileShader',None,[U])(s)
 ok=I();g('glGetShaderiv',None,[U,U,C.POINTER(I)])(s,0x8B81,C.byref(ok));assert ok.value;g('glAttachShader',None,[U,U])(program,s)
g('glLinkProgram',None,[U])(program);g('glUseProgram',None,[U])(program)
vao=U();vbo=U();g('glGenVertexArrays',None,[I,C.POINTER(U)])(1,C.byref(vao));g('glBindVertexArray',None,[U])(vao);g('glGenBuffers',None,[I,C.POINTER(U)])(1,C.byref(vbo));g('glBindBuffer',None,[U,U])(0x8892,vbo)
for name,size,offset in [(b'p',3,0),(b'uv',2,12),(b'normal',3,20)]:
 loc=g('glGetAttribLocation',I,[U,C.c_char_p])(program,name);g('glEnableVertexAttribArray',None,[U])(loc);g('glVertexAttribPointer',None,[U,I,U,U,I,P])(loc,size,0x1406,0,32,P(offset))
skin=Image.open(root/'src/main/resources/assets/celestialfall/textures/entity/training_dummy.png').convert('RGBA')
tex=np.asarray(skin).copy();texture=U();g('glGenTextures',None,[I,C.POINTER(U)])(1,C.byref(texture));g('glBindTexture',None,[U,U])(0x0DE1,texture)
g('glTexImage2D',None,[U,I,I,I,I,I,U,U,P])(0x0DE1,0,0x1908,256,256,0,0x1908,0x1401,tex.ctypes.data)
for axis in [0x2800,0x2801]:g('glTexParameteri',None,[U,U,I])(0x0DE1,axis,0x2600)
g('glEnable',None,[U])(0x0B71);g('glDisable',None,[U])(0x0B44);g('glViewport',None,[I,I,I,I])(0,0,w,h)
def camera():
 eye=np.array([1.5,1.7,-4.7]);focus=np.array([0,.92,0]);f=focus-eye;f/=np.linalg.norm(f);r=np.cross(f,[0,1,0]);r/=np.linalg.norm(r);u=np.cross(r,f)
 v=np.eye(4);v[:3,:3]=[r,u,-f];v[:3,3]=-v[:3,:3]@eye;s=1/np.tan(np.deg2rad(30)/2);n,far=.05,40
 proj=np.array([[s/(w/h),0,0,0],[0,s,0,0],[0,0,-(far+n)/(far-n),-2*far*n/(far-n)],[0,0,-1,0]])
 return np.ascontiguousarray(proj@v,np.float32)
mat=camera();g('glUniformMatrix4fv',None,[I,I,U,P])(g('glGetUniformLocation',I,[U,C.c_char_p])(program,b'mvp'),1,1,mat.ctypes.data)
font='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf';small=ImageFont.truetype(font,16);label=ImageFont.truetype(font,23);title=ImageFont.truetype(font,34)
labels=['IDLE','ATTACK','GUARD','COMBO TRAINING'];desc=['Stays still','Shared 4-hit combat','Guard + 1-second break','200 HP / hit reactions']
frames=[]
for frame in range(72):
 canvas=Image.new('RGB',(1600,696),'#11161e');draw=ImageDraw.Draw(canvas)
 draw.text((30,20),'BOTAND 2.4 / TRAINING DUMMIES',font=title,fill='#f0f2f5')
 draw.text((32,67),'Actual model + texture + motion engine / synthetic render, not Minecraft gameplay',font=small,fill='#a5b3c4')
 for i,(kind,clip) in enumerate(data.items()):
  rows=np.concatenate([np.asarray(part,np.float32).reshape(-1,4,8)[:,[0,1,2,0,2,3]].reshape(-1,8) for part in clip[frame]])
  g('glBufferData',None,[U,C.c_ssize_t,P,U])(0x8892,rows.nbytes,rows.ctypes.data,0x88E8)
  g('glClearColor',None,[F,F,F,F])(.087,.112,.149,1);g('glClear',None,[U])(0x4100);g('glDrawArrays',None,[U,I,I])(4,0,len(rows))
  pixels=np.empty((h,w,4),np.uint8);g('glReadPixels',None,[I,I,I,I,U,U,P])(0,0,w,h,0x1908,0x1401,pixels.ctypes.data)
  tile=Image.fromarray(pixels[::-1]).convert('RGB');x=i*400;y=114;canvas.paste(tile,(x,y))
  draw.text((x+24,128),f'{200 if kind=="combo" else 20} HP',font=label,fill='#dce4f1')
  draw.text((x+24,575),labels[i],font=label,fill='#eff3f7');draw.text((x+24,610),desc[i],font=small,fill='#a5b3c4')
  draw.line((x+24,646,x+376,646),fill='#303e50',width=2)
 draw.text((30,669),'Gray cyclops / articulated elbows and knees / server-authoritative combat',font=small,fill='#8394a9')
 frames.append(canvas)
frames[4].save(out/'Botand_Dummies_Overview.png')
cmd=['ffmpeg','-y','-f','rawvideo','-pix_fmt','rgb24','-s','1600x696','-r','24','-i','-','-an','-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(out/'Botand_Dummies_Preview.mp4')]
p=subprocess.Popen(cmd,stdin=subprocess.PIPE,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
for _ in range(2):
 for frame in frames:p.stdin.write(frame.tobytes())
p.stdin.close();err=p.stderr.read();assert p.wait()==0,err.decode();print('Rendered exact dummy meshes / 4 roles / 72 frames')
