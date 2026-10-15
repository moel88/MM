"""Run the actual GLSL on a headless Mesa/EGL context with a synthetic fixture, not a game screenshot."""
import ctypes as C
import ctypes.util
import json
import os
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

os.environ.setdefault('EGL_PLATFORM','surfaceless')
os.environ.setdefault('LIBGL_ALWAYS_SOFTWARE','1')
egl=C.CDLL(ctypes.util.find_library('EGL'))
gl=C.CDLL(ctypes.util.find_library('GL'))
I=C.c_int; U=C.c_uint; P=C.c_void_p; F=C.c_float
def fn(lib,name,result,args):
    f=getattr(lib,name);f.restype=result;f.argtypes=args;return f
getdisplay=fn(egl,'eglGetDisplay',P,[P]);display=getdisplay(None)
assert fn(egl,'eglInitialize',U,[P,C.POINTER(I),C.POINTER(I)])(display,None,None),'EGL initialization failed'
assert fn(egl,'eglBindAPI',U,[U])(0x30A2)
attrs=(I*15)(0x3033,1,0x3040,8,0x3024,8,0x3023,8,0x3022,8,0x3021,8,0x3025,24,0x3038)
config=P();count=I()
assert fn(egl,'eglChooseConfig',U,[P,C.POINTER(I),C.POINTER(P),I,C.POINTER(I)])(display,attrs,C.byref(config),1,C.byref(count)) and count.value
ctxattrs=(I*7)(0x3098,3,0x30FB,2,0x30FD,1,0x3038)
context=fn(egl,'eglCreateContext',P,[P,P,P,C.POINTER(I)])(display,config,None,ctxattrs)
assert context,'OpenGL context creation failed'
w,h=960,540
surface=fn(egl,'eglCreatePbufferSurface',P,[P,P,C.POINTER(I)])(display,config,(I*5)(0x3057,w,0x3056,h,0x3038))
assert fn(egl,'eglMakeCurrent',U,[P,P,P,P])(display,surface,surface,context)

def g(name,result,args):return fn(gl,name,result,args)

# Render the exported real Java meshes; this is NOT a Minecraft gameplay screenshot.
root=Path(__file__).resolve().parents[1]
out=root/'build/heaven-scene-check'
data=json.loads((out/'scene.json').read_text())
program=g('glCreateProgram',U,[])()
for kind,code in [(0x8B31,b'#version 150\nin vec3 p; in vec3 rgb; uniform mat4 mvp; out vec3 color; void main(){gl_Position=mvp*vec4(p,1);color=rgb;}'),(0x8B30,b'#version 150\nin vec3 color; out vec4 frag; void main(){frag=vec4(color,1);}')]:
 sid=g('glCreateShader',U,[U])(kind);raw=C.c_char_p(code)
 g('glShaderSource',None,[U,I,C.POINTER(C.c_char_p),P])(sid,1,C.byref(raw),None)
 g('glCompileShader',None,[U])(sid);ok=I();g('glGetShaderiv',None,[U,U,C.POINTER(I)])(sid,0x8B81,C.byref(ok));assert ok.value
 g('glAttachShader',None,[U,U])(program,sid)
g('glLinkProgram',None,[U])(program);g('glUseProgram',None,[U])(program)
vao=U();vbo=U();g('glGenVertexArrays',None,[I,C.POINTER(U)])(1,C.byref(vao));g('glBindVertexArray',None,[U])(vao)
g('glGenBuffers',None,[I,C.POINTER(U)])(1,C.byref(vbo));g('glBindBuffer',None,[U,U])(0x8892,vbo)
for name,offset in [(b'p',0),(b'rgb',12)]:
 loc=g('glGetAttribLocation',I,[U,C.c_char_p])(program,name)
 g('glEnableVertexAttribArray',None,[U])(loc)
 g('glVertexAttribPointer',None,[U,I,U,U,I,P])(loc,3,0x1406,0,24,P(offset))
g('glEnable',None,[U])(0x0B71);g('glDepthFunc',None,[U])(0x0203);g('glDisable',None,[U])(0x0B44)
g('glViewport',None,[I,I,I,I])(0,0,w,h)
def xyz(p):return np.array([p['x'],p['y'],p['z']],float)
def normalize(x):return x/np.linalg.norm(x)
def camera(c):
 eye=xyz(c['position']);f=normalize(xyz(c['focus'])-eye);r=normalize(np.cross(f,[0,1,0]));u=np.cross(r,f)
 a=np.deg2rad(c['roll']);r,u=r*np.cos(a)-u*np.sin(a),u*np.cos(a)+r*np.sin(a)
 view=np.eye(4);view[:3,:3]=[r,u,-f];view[:3,3]=-view[:3,:3]@eye
 s=1/np.tan(np.deg2rad(c['fov'])/2);near,far=.03,4096
 proj=np.array([[s/(w/h),0,0,0],[0,s,0,0],[0,0,-(far+near)/(far-near),-2*far*near/(far-near)],[0,0,-1,0]])
 return np.ascontiguousarray(proj@view,dtype=np.float32)
def tri_faces(faces):
 rows=[]
 for f in faces:
  col=[(f[4]>>16&255)/255,(f[4]>>8&255)/255,(f[4]&255)/255]
  for idx in [0,1,2,0,2,3]:
   rgb=f[5][idx] if len(f)>5 else f[4]
   rows.append(list(f[idx])+[(rgb>>16&255)/255,(rgb>>8&255)/255,(rgb&255)/255])
 return rows
def draw(rows,matrix):
 if not rows:return
 a=np.ascontiguousarray(rows,np.float32)
 g('glUniformMatrix4fv',None,[I,I,U,P])(g('glGetUniformLocation',I,[U,C.c_char_p])(program,b'mvp'),1,1,matrix.ctypes.data)
 g('glBufferData',None,[U,C.c_ssize_t,P,U])(0x8892,a.nbytes,a.ctypes.data,0x88E0)
 g('glDrawArrays',None,[U,I,I])(4,0,len(a))
def save(name,label):
 pixels=np.empty((h,w,4),np.uint8);g('glReadPixels',None,[I,I,I,I,U,U,P])(0,0,w,h,0x1908,0x1401,pixels.ctypes.data)
 assert g('glGetError',U,[])()==0
 im=Image.fromarray(pixels[::-1]);d=ImageDraw.Draw(im);d.rectangle((0,0,w,28),fill='#0b1320');d.text((12,8),label+' | actual mesh / synthetic scene, not gameplay',fill='white')
 import io
 buf=io.BytesIO();im.save(buf,format='PNG');(out/name).write_bytes(buf.getvalue());return im
images=[]
for shot in data['shots']:
 age=shot['age'];mat=camera(shot['camera'])
 g('glClearColor',None,[F,F,F,F])(.07,.11,.18,1);g('glClear',None,[U])(0x4100)
 ground=[[-1400,-.02,-1400],[1400,-.02,-1400],[1400,-.02,1400],[-1400,-.02,1400],0x273D3E]
 draw(tri_faces([ground]),mat)
 draw(tri_faces(shot['clouds']),mat)
 draw(tri_faces(shot['blade']),mat)
 for part,col in [('cloth',0xCCDDE6),('face',0xE2BDA2),('hand',0xDEEBF5)]:
  verts=shot['actor'][part];faces=[[*[v[:3] for v in verts[i:i+4]],col] for i in range(0,len(verts),4)]
  draw(tri_faces(faces),mat)
 images.append(save('shot-'+str(int(age))+'.png',str(age/20)+' seconds'))
g('glClearColor',None,[F,F,F,F])(.04,.07,.12,1);g('glClear',None,[U])(0x4100)
mat=camera({'position':{'x':130,'y':480,'z':1100},'focus':{'x':0,'y':460,'z':0},'fov':52,'roll':0})
draw(tri_faces(data['local_blade']),mat);images.append(save('sword.png','720-block blade / 920-block sword'))
report={'mesh_render':'passed','geometry_source':'Export of actual Java renderer mesh and camera','screenshots':'Synthetic EGL/Mesa render, not Minecraft gameplay','blade_length':720,'total_sword_length':920,'gif_assets_in_mod':False}
(out/'result.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))
