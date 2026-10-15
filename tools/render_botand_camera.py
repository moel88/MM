"""Render actual baked Java animation meshes through EGL. Not a Minecraft gameplay capture."""
from pathlib import Path
import ctypes as C,ctypes.util,os,json,io,zipfile,subprocess
import numpy as np
from PIL import Image,ImageDraw,ImageFont
root=Path(__file__).resolve().parents[1];out=root/'build/botand-motion';out.mkdir(exist_ok=True)
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
w,h=1280,720
surf=fn(egl,'eglCreatePbufferSurface',P,[P,P,C.POINTER(I)])(display,config,(I*5)(0x3057,w,0x3056,h,0x3038));assert fn(egl,'eglMakeCurrent',U,[P,P,P,P])(display,surf,surf,ctx)
program=g('glCreateProgram',U,[])()
for kind,src in [(0x8B31,b'#version 150\nin vec3 p;in vec2 uv;in vec3 normal;uniform mat4 mvp;out vec2 tex;out float shade;void main(){gl_Position=mvp*vec4(p,1);tex=uv;shade=.65+.35*max(0.,dot(normalize(normal),normalize(vec3(-1,2,-3))));}'),(0x8B30,b'#version 150\nin vec2 tex;in float shade;uniform sampler2D skin;uniform vec4 solid;out vec4 frag;void main(){vec4 color=solid.a>0.?solid:texture(skin,tex);if(color.a<.1)discard;frag=vec4(color.rgb*shade,1);}')] :
 s=g('glCreateShader',U,[U])(kind);raw=C.c_char_p(src);g('glShaderSource',None,[U,I,C.POINTER(C.c_char_p),P])(s,1,C.byref(raw),None);g('glCompileShader',None,[U])(s)
 ok=I();g('glGetShaderiv',None,[U,U,C.POINTER(I)])(s,0x8B81,C.byref(ok));assert ok.value;g('glAttachShader',None,[U,U])(program,s)
g('glLinkProgram',None,[U])(program);g('glUseProgram',None,[U])(program)
vao=U();vbo=U();g('glGenVertexArrays',None,[I,C.POINTER(U)])(1,C.byref(vao));g('glBindVertexArray',None,[U])(vao);g('glGenBuffers',None,[I,C.POINTER(U)])(1,C.byref(vbo));g('glBindBuffer',None,[U,U])(0x8892,vbo)
for name,size,offset in [(b'p',3,0),(b'uv',2,12),(b'normal',3,20)]:
 loc=g('glGetAttribLocation',I,[U,C.c_char_p])(program,name);g('glEnableVertexAttribArray',None,[U])(loc);g('glVertexAttribPointer',None,[U,I,U,U,I,P])(loc,size,0x1406,0,32,P(offset))
jar=next((root/'.gradle/loom-cache/minecraftMaven').rglob('*.jar'))
with zipfile.ZipFile(jar) as z: skin=Image.open(io.BytesIO(z.read('assets/minecraft/textures/entity/player/wide/steve.png'))).convert('RGBA')
tex=np.asarray(skin).copy();texture=U();g('glGenTextures',None,[I,C.POINTER(U)])(1,C.byref(texture));g('glBindTexture',None,[U,U])(0x0DE1,texture)
g('glTexImage2D',None,[U,I,I,I,I,I,U,U,P])(0x0DE1,0,0x1908,64,64,0,0x1908,0x1401,tex.ctypes.data)
for axis in [0x2800,0x2801]:g('glTexParameteri',None,[U,U,I])(0x0DE1,axis,0x2600)
g('glEnable',None,[U])(0x0B71);g('glDisable',None,[U])(0x0B44);g('glViewport',None,[I,I,I,I])(0,0,w,h)
def camera(eye):
 eye=np.array(eye,float);focus=np.array([0,.92,0]);f=focus-eye;f/=np.linalg.norm(f);r=np.cross(f,[0,1,0]);r/=np.linalg.norm(r);u=np.cross(r,f)
 v=np.eye(4);v[:3,:3]=[r,u,-f];v[:3,3]=-v[:3,:3]@eye;s=1/np.tan(np.deg2rad(32)/2);n,far=.05,40
 proj=np.array([[s,0,0,0],[0,s,0,0],[0,0,-(far+n)/(far-n),-2*far*n/(far-n)],[0,0,-1,0]])
 return np.ascontiguousarray(proj@v,np.float32)
mat=camera((2.7,1.9,-4.2));g('glUniformMatrix4fv',None,[I,I,U,P])(g('glGetUniformLocation',I,[U,C.c_char_p])(program,b'mvp'),1,1,mat.ctypes.data)
font='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf';small=ImageFont.truetype(font,15);label=ImageFont.truetype(font,21);title=ImageFont.truetype(font,34)

# Synthetic scene, actual exported camera state + player geometry. No game footage.
import math,random
track=json.loads((out/'camera-track.json').read_text())
hands=json.loads((out/'hands.json').read_text())
solid_loc=g('glGetUniformLocation',I,[U,C.c_char_p])(program,b'solid')
mvp_loc=g('glGetUniformLocation',I,[U,C.c_char_p])(program,b'mvp')
u4=g('glUniform4f',None,[I,F,F,F,F]);umat=g('glUniformMatrix4fv',None,[I,I,U,P])
buffer=g('glBufferData',None,[U,C.c_ssize_t,P,U]);drawarrays=g('glDrawArrays',None,[U,I,I])
clearcolor=g('glClearColor',None,[F,F,F,F]);clear=g('glClear',None,[U]);read=g('glReadPixels',None,[I,I,I,I,U,U,P])
font='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
small=ImageFont.truetype(font,14);label=ImageFont.truetype(font,20);title=ImageFont.truetype(font,30);big=ImageFont.truetype(font,42)
geometry={}
def cube(x,y,z,sx,sy,sz,color):
 x1,x2=x-sx/2,x+sx/2;y1,y2=y,y+sy;z1,z2=z-sz/2,z+sz/2
 faces=[([(x1,y1,z1),(x2,y1,z1),(x2,y2,z1),(x1,y2,z1)],(0,0,-1)),([(x2,y1,z2),(x1,y1,z2),(x1,y2,z2),(x2,y2,z2)],(0,0,1)),([(x1,y1,z2),(x1,y1,z1),(x1,y2,z1),(x1,y2,z2)],(-1,0,0)),([(x2,y1,z1),(x2,y1,z2),(x2,y2,z2),(x2,y2,z1)],(1,0,0)),([(x1,y2,z1),(x2,y2,z1),(x2,y2,z2),(x1,y2,z2)],(0,1,0)),([(x1,y1,z2),(x2,y1,z2),(x2,y1,z1),(x1,y1,z1)],(0,-1,0))]
 rows=geometry.setdefault(color,[])
 for v,n in faces:
  for j in [0,1,2,0,2,3]:rows.append([*v[j],0,0,*n])
random.seed(42)
cube(-10,-.3,0,100,.25,86,(.36,.43,.43))
for x in range(-42,24,2):
 for z in range(-28,30,2):
  v=random.choice([.43,.46,.49]);cube(x,-.05,z,1.94,.05,1.94,(v,v+.04,v+.075))
for x in range(-42,24,4):
 for z in [-31,33]:
  cube(x,-.06,z,3.95,.07,5,(.20,.35,.28))
for x,z in [(-36,-26),(-24,-26),(-10,-26),(4,-26),(16,-24),(-38,24),(-24,26),(-10,26),(4,26),(16,24)]:
 height=random.choice([9,12,15]);cube(x,0,z,7,height,6,(.52,.57,.61));cube(x,height,z,7.3,.28,6.3,(.24,.29,.36))
 for y in range(2,height,3):
  for dx in [-2,0,2]:
   cube(x+dx,y,z-3.03,1.05,1.6,.08,(.22,.40,.51));cube(x+dx,y,z+3.03,1.05,1.6,.08,(.22,.40,.51))
for x,z in [(-36,-17),(-26,-18),(-12,-17),(0,-17),(12,-17),(-36,18),(-24,18),(-12,18),(0,18),(12,18)]:
 cube(x,0,z,3.8,.4,3.8,(.25,.31,.35));cube(x,.4,z,.7,3.9,.7,(.31,.23,.17));cube(x,3,z,3.5,2.8,3.4,(.19,.36,.28));cube(x-.4,5,z,2.6,1.2,2.6,(.25,.44,.32))
for x,z in [(-31,-16),(-18,-16),(-4,-16),(10,-16),(-31,16),(-18,16),(-4,16),(10,16)]:
 cube(x,0,z,.28,4.5,.28,(.18,.24,.30));cube(x,4.35,z,.7,.4,.7,(.96,.87,.58))
scene=[(color,np.ascontiguousarray(rows,dtype=np.float32)) for color,rows in geometry.items()]
def triangles(parts):return np.ascontiguousarray(np.concatenate([np.asarray(part,np.float32).reshape(-1,4,8)[:,[0,1,2,0,2,3]].reshape(-1,8) for part in parts]))
meshes={name:[triangles(parts) for parts in clip] for name,clip in data.items()}
meshes['idle']=[triangles(json.loads((out/'idle.json').read_text()))]*72
handmesh=triangles(hands['idle'])
def projection():
 f=1/math.tan(math.radians(70)/2);near,far=.05,240
 return np.array([[f/(w/h),0,0,0],[0,f,0,0],[0,0,-(far+near)/(far-near),-2*far*near/(far-near)],[0,0,-1,0]],np.float32)
proj=projection()
def view(row):
 eye=np.array(row['eye']);yaw,pitch=np.radians([row['yaw'],row['pitch']]);f=np.array([-np.sin(yaw)*np.cos(pitch),-np.sin(pitch),np.cos(yaw)*np.cos(pitch)])
 r=np.cross(f,[0,1,0]);r/=np.linalg.norm(r);up=np.cross(r,f);v=np.eye(4);v[:3,:3]=[r,up,-f];v[:3,3]=-v[:3,:3]@eye
 return np.ascontiguousarray(proj@v,np.float32)
def drawmesh(rows,matrix,color=None):
 u4(solid_loc,*(list(color)+[1] if color else [0,0,0,0]));umat(mvp_loc,1,1,matrix.ctypes.data);buffer(0x8892,rows.nbytes,rows.ctypes.data,0x88E8);drawarrays(4,0,len(rows))
notes={'ORBIT':('RMB + DRAG','Independent camera / avatar keeps its heading'), 'RUN':('CTRL  /  W','Toggle running; no need to hold Ctrl'), 'SHIFT LOCK':('SHIFT  /  A','Mouse steers the camera / shoulder offset'), 'ZOOM IN':('WHEEL UP','Smooth zoom into automatic first-person'), 'ZOOM OUT':('WHEEL DOWN','Return to third-person / 14-block distance limit'), 'WALL CLEARANCE':('OBSTACLE TEST','Immediate pull-in / eased return to clear space'), 'GUARD':('HOLD F','Guard pressure rises with blocked hits'), 'GUARD BREAK':('MOVE ONLY  /  1.00 s','90% slow / attacks, use, dash, guard and jump locked'), 'RECOVER':('CONTROL RESTORED','Actions resume after 20 server ticks'), '4 HIT COMBO':('LMB','Existing motion engine and contact timing retained')}
video=out/'Botand_Camera_Preview.mp4'
process=subprocess.Popen(['ffmpeg','-y','-f','rawvideo','-pix_fmt','rgb24','-s',f'{w}x{h}','-r','30','-i','-','-an','-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(video)],stdin=subprocess.PIPE,stdout=subprocess.DEVNULL,stderr=open(out/'camera-ffmpeg.log','w'))
pixels=np.empty((h,w,4),np.uint8);stills={}
for index,row in enumerate(track):
 matrix=view(row);clearcolor(.49,.68,.83,1);clear(0x4100)
 for color,rows in scene:drawmesh(rows,matrix,color)
 if not row['firstPerson']:
  angle=math.radians(180-row['bodyYaw']);c,s=math.cos(angle),math.sin(angle);model=np.array([[c,0,s,row['player'][0]],[0,1,0,0],[-s,0,c,row['player'][2]],[0,0,0,1]],np.float32)
  drawmesh(meshes[row['clip']][row['meshFrame']],np.ascontiguousarray(matrix@model,np.float32))
 else:
  clear(0x0100);drawmesh(handmesh,proj)
 read(0,0,w,h,0x1908,0x1401,pixels.ctypes.data);canvas=Image.fromarray(pixels[::-1]).convert('RGB');d=ImageDraw.Draw(canvas,'RGBA')
 d.rectangle((0,0,w,86),fill=(11,18,29,233));d.text((27,17),'BOTAND 2.1',font=title,fill='#f1f5fb');d.text((272,24),'CLASSIC THIRD-PERSON',font=label,fill='#acb7cb')
 d.text((28,58),'PRODUCTION CAMERA MATH + ACTUAL MOTION MESHES / SYNTHETIC RENDER, NOT GAME FOOTAGE',font=small,fill='#8b9ead')
 chapter=row['chapter'];key,note=notes[chapter];accent='#fb6b87' if chapter=='GUARD BREAK' else '#b8acff'
 d.rounded_rectangle((23,105,440,195),12,fill=(14,23,36,226));d.text((40,113),chapter,font=title,fill='#ffffff');d.text((41,155),key,font=label,fill=accent)
 d.rectangle((0,h-98,w,h),fill=(11,18,29,236));d.text((27,h-80),note,font=label,fill='#e9eff8')
 lock='ON' if row['shiftLock'] else 'OFF';mode='FIRST PERSON' if row['firstPerson'] else 'THIRD PERSON'
 d.text((28,h-42),f'{mode}     SHIFT LOCK {lock}     CAMERA {row["distance"]:.2f} / 14.00 blocks',font=small,fill='#9eafc3')
 d.text((w-116,h-43),f'{row["time"]:05.2f} / 32',font=small,fill='#9eafc3')
 d.rectangle((0,h-5,int(w*index/len(track)),h),fill=accent)
 if row['shiftLock'] or row['firstPerson']:
  d.line((w/2-5,h/2,w/2+5,h/2),fill='#f4f7ff',width=2);d.line((w/2,h/2-5,w/2,h/2+5),fill='#f4f7ff',width=2)
 if chapter in ['GUARD','GUARD BREAK','RECOVER']:
  pressure=100 if chapter=='GUARD BREAK' else max(0,(row['time']-28)*200) if chapter=='GUARD' else 0
  d.rounded_rectangle((w/2-166,h-154,w/2+166,h-115),8,fill=(13,20,32,240));d.rectangle((w/2-150,h-140,w/2+150*pressure/50-150,h-129),fill=accent)
 # Small overhead diagram clarifies independent camera and avatar headings.
 cx,cz=w-126,209;scale=6.6;d.rounded_rectangle((w-241,104,w-14,328),12,fill=(13,23,35,222));d.text((w-223,117),'CAMERA / PLAYER',font=small,fill='#aab9cd')
 for n in range(-3,4):
  d.line((cx+n*28,151,cx+n*28,299),fill=(85,105,125,55));d.line((w-225,cz+n*22,w-30,cz+n*22),fill=(85,105,125,55))
 px,pz=row['player'][0],row['player'][2];ex,ez=row['eye'][0],row['eye'][2];sx,sy=cx+(ex-px)*scale,cz+(ez-pz)*scale
 d.line((cx,cz,sx,sy),fill='#788eaa',width=2);d.ellipse((cx-6,cz-6,cx+6,cz+6),fill='#72e2bd');d.ellipse((sx-4,sy-4,sx+4,sy+4),fill='#b8acff')
 a=math.radians(row['bodyYaw']);d.line((cx,cz,cx-math.sin(a)*22,cz+math.cos(a)*22),fill='#72e2bd',width=3)
 if index in [70,210,380,568,690,762,868,937]:stills[index]=canvas.copy()
 process.stdin.write(canvas.tobytes())
 if index%180==0:print('camera frames',index,flush=True)
process.stdin.close();assert process.wait()==0
sheet=Image.new('RGB',(1600,1020),'#0b121d');sd=ImageDraw.Draw(sheet);sd.text((26,18),'BOTAND 2.1 / CAMERA & CONTROL',font=title,fill='#f0f5fa');sd.text((27,62),'Actual exported camera paths + player meshes. Synthetic preview, not game footage.',font=label,fill='#92a9bc')
selected=[70,380,568,868]
for i,n in enumerate(selected):sheet.paste(stills[n].resize((780,439),Image.Resampling.LANCZOS),(10+i%2*800,105+i//2*453))
sheet.save(out/'Botand_Camera_Overview.png');stills[380].save(out/'Botand_Shift_Lock.png')
print(video,flush=True)
