"""Actual AtomicActor meshes + remade audio, fixed review camera; not Minecraft footage."""
from pathlib import Path
import ctypes as C,ctypes.util,os,json,io,zipfile,subprocess,struct
import numpy as np
from PIL import Image,ImageDraw,ImageFont
root=Path(__file__).resolve().parents[1];out=root/'build/audio-remake'
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
g('glEnable',None,[U])(0x0B71);g('glDisable',None,[U])(0x0B44);g('glViewport',None,[I,I,I,I])(0,0,w,h)
eye=np.array([3.0,2.3,5.8]);focus=np.array([0,1.7,0]);forward=focus-eye;forward/=np.linalg.norm(forward);right=np.cross(forward,[0,1,0]);right/=np.linalg.norm(right);up=np.cross(right,forward)
view=np.eye(4);view[:3,:3]=[right,up,-forward];view[:3,3]=-view[:3,:3]@eye
scale=1/np.tan(np.deg2rad(48)/2);near,far=.05,30
proj=np.array([[scale/(w/h),0,0,0],[0,scale,0,0],[0,0,-(far+near)/(far-near),-2*far*near/(far-near)],[0,0,-1,0]])
mvp=np.ascontiguousarray(proj@view,dtype=np.float32)
g('glUniformMatrix4fv',None,[I,I,U,P])(g('glGetUniformLocation',I,[U,C.c_char_p])(program,b'mvp'),1,1,mvp.ctypes.data)
font='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
large=ImageFont.truetype(font,28);small=ImageFont.truetype(font,15)
file=(out/'atomic-motion.bin').open('rb');frames,fps=struct.unpack('>ii',file.read(8))
cmd=['ffmpeg','-v','error','-y','-f','rawvideo','-pix_fmt','rgb24','-s',f'{w}x{h}','-r',str(fps),'-i','-','-i',str(out/'Atomic_Sound_Remake.wav'),'-t',str(frames/fps),'-c:v','libx264','-preset','fast','-crf','19','-c:a','aac','-b:a','192k','-pix_fmt','yuv420p','-movflags','+faststart',str(out/'Botand_Atomic_Audio_Sync.mp4')]
process=subprocess.Popen(cmd,stdin=subprocess.PIPE)
stills=[]
for i in range(frames):
 age=struct.unpack('>f',file.read(4))[0]
 g('glClearColor',None,[F,F,F,F])(.18,.17,.22,1);g('glClear',None,[U])(0x4100)
 for texture in textures:
  count=struct.unpack('>i',file.read(4))[0];vertices=np.frombuffer(file.read(count*8*4),dtype='>f4').astype(np.float32)
  rows=np.ascontiguousarray(vertices.reshape(-1,4,8)[:,[0,1,2,0,2,3]].reshape(-1,8))
  g('glBindTexture',None,[U,U])(0x0DE1,texture)
  g('glBufferData',None,[U,C.c_ssize_t,P,U])(0x8892,rows.nbytes,rows.ctypes.data,0x88E8);g('glDrawArrays',None,[U,I,I])(4,0,len(rows))
 pixels=np.empty((h,w,4),np.uint8);g('glReadPixels',None,[I,I,I,I,U,U,P])(0,0,w,h,0x1908,0x1401,pixels.ctypes.data)
 image=Image.fromarray(pixels[::-1]).convert('RGB');draw=ImageDraw.Draw(image)
 draw.rectangle((0,0,w,68),fill='#131021');draw.text((24,14),'BOTAND 2.3 / ATOMIC AUDIO SYNC',font=large,fill='#edddff')
 draw.text((24,47),'Actual actor mesh / fixed review camera / synthetic render, not Minecraft gameplay',font=small,fill='#a899c5')
 phase='READY' if age<8 else 'I...' if age<34 else 'AM...' if age<74 else 'ATOMIC.' if age<88 else 'SLASH' if age<98 else 'FOLLOW-THROUGH' if age<142 else 'RECOVER'
 draw.text((24,89),phase,font=large,fill='#cb9bff');draw.text((24,129),f'{age/20:05.2f} s / 1.0x audio',font=small,fill='#b9a7d0')
 draw.rectangle((0,463,w,h),fill='#131021');draw.text((24,474),'Sword motion: 4.40 - 4.90 s',font=small,fill='#ded1ef')
 draw.line((24,508,936,508),fill='#3e3055',width=4)
 for t in [1.7,3.7,4.4,4.9]:
  x=24+t/7.5*912;draw.line((x,502,x,516),fill='#af77ef',width=2);draw.text((x-15,521),str(t),font=small,fill='#8e7aa7')
 draw.ellipse((24+i/(frames-1)*912-5,503,24+i/(frames-1)*912+5,513),fill='#e9cdff')
 process.stdin.write(image.tobytes())
 if i in [114,132,139,147]:stills.append(image.copy())
process.stdin.close();assert process.wait()==0
sheet=Image.new('RGB',(1920,1080))
for i,im in enumerate(stills):sheet.paste(im,((i%2)*960,(i//2)*540))
sheet.save(out/'Atomic_Sync_Overview.png')
print('Rendered 225 actual actor frames with the new audio at native speed')
