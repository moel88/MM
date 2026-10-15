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
attrs=(I*13)(0x3033,1,0x3040,8,0x3024,8,0x3023,8,0x3022,8,0x3021,8,0x3038)
config=P();count=I()
assert fn(egl,'eglChooseConfig',U,[P,C.POINTER(I),C.POINTER(P),I,C.POINTER(I)])(display,attrs,C.byref(config),1,C.byref(count)) and count.value
ctxattrs=(I*7)(0x3098,3,0x30FB,2,0x30FD,1,0x3038)
context=fn(egl,'eglCreateContext',P,[P,P,P,C.POINTER(I)])(display,config,None,ctxattrs)
assert context,'OpenGL context creation failed'
w,h=512,320
surface=fn(egl,'eglCreatePbufferSurface',P,[P,P,C.POINTER(I)])(display,config,(I*5)(0x3057,w,0x3056,h,0x3038))
assert fn(egl,'eglMakeCurrent',U,[P,P,P,P])(display,surface,surface,context)

def g(name,result,args):return fn(gl,name,result,args)
create=g('glCreateShader',U,[U]);source=g('glShaderSource',None,[U,I,C.POINTER(C.c_char_p),C.POINTER(I)])
compile_=g('glCompileShader',None,[U]);getshader=g('glGetShaderiv',None,[U,U,C.POINTER(I)])
root=Path(__file__).resolve().parents[1]
shaders=root/'src/main/resources/assets/celestialfall/shaders/anime'
def shader(kind,name):
    sid=create(kind);raw=C.c_char_p((shaders/name).read_bytes());source(sid,1,C.byref(raw),None);compile_(sid)
    okay=I();getshader(sid,0x8B81,C.byref(okay))
    if not okay.value:
        buf=C.create_string_buffer(8192);g('glGetShaderInfoLog',None,[U,I,P,P])(sid,8192,None,buf)
        raise AssertionError(buf.value.decode())
    return sid
program=g('glCreateProgram',U,[])()
for kind,name in [(0x8B31,'fullscreen.vert'),(0x8B30,'anime.frag')]:
    sid=shader(kind,name);g('glAttachShader',None,[U,U])(program,sid)
g('glLinkProgram',None,[U])(program)
okay=I();g('glGetProgramiv',None,[U,U,C.POINTER(I)])(program,0x8B82,C.byref(okay));assert okay.value,'Shader link failed'
g('glUseProgram',None,[U])(program)
vao=U();g('glGenVertexArrays',None,[I,C.POINTER(U)])(1,C.byref(vao));g('glBindVertexArray',None,[U])(vao)
location=g('glGetUniformLocation',I,[U,C.c_char_p]);u1i=g('glUniform1i',None,[I,I]);u1f=g('glUniform1f',None,[I,F]);u2f=g('glUniform2f',None,[I,F,F])
def uniform(name,value):
    loc=location(program,name.encode())
    if isinstance(value,tuple):u2f(loc,*value)
    elif isinstance(value,int):u1i(loc,value)
    else:u1f(loc,value)
for name,value in {'SceneTex':0,'BloomTex':1,'Resolution':(w,h),'Focus':(.5,.5),'Time':10.3,'Strength':1.0,'Bloom':1.35,'Power':0.0,'Distortion':1.0,'Speed':0.0,'Mono':0.0,'AtomicCharge':0.0,'AtomicInk':0.0,'HeavenTone':0.0}.items():uniform(name,value)
fixture=Image.new('RGBA',(w,h),(12,9,25,255));d=ImageDraw.Draw(fixture)
d.polygon([(0,235),(70,170),(145,231),(230,150),(350,215),(430,175),(512,210),(512,320),(0,320)],fill=(39,32,49,255))
d.line([(256,260),(246,38)],fill=(235,210,255,255),width=10)
d.ellipse((140,30,370,95),outline=(189,122,255,255),width=3)
d.line([(180,140),(256,175),(355,119)],fill=(170,100,255,255),width=3)
input_pixels=np.ascontiguousarray(np.asarray(fixture)[::-1])
textures=(U*4)();fbos=(U*4)();g('glGenTextures',None,[I,C.POINTER(U)])(4,textures);g('glGenFramebuffers',None,[I,C.POINTER(U)])(4,fbos)
bindtex=g('glBindTexture',None,[U,U]);active=g('glActiveTexture',None,[U]);bindfbo=g('glBindFramebuffer',None,[U,U])
for n in range(4):
    tw,th=(w,h) if n in [0,3] else (w//4,h//4)
    bindtex(0x0DE1,textures[n]);g('glTexImage2D',None,[U,I,I,I,I,I,U,U,P])(0x0DE1,0,0x8058,tw,th,0,0x1908,0x1401,input_pixels.ctypes.data if n==0 else None)
    for key,value in [(0x2801,0x2601),(0x2800,0x2601),(0x2802,0x812F),(0x2803,0x812F)]:g('glTexParameteri',None,[U,U,I])(0x0DE1,key,value)
    bindfbo(0x8D40,fbos[n]);g('glFramebufferTexture2D',None,[U,U,U,U,I])(0x8D40,0x8CE0,0x0DE1,textures[n],0)
    assert g('glCheckFramebufferStatus',U,[U])(0x8D40)==0x8CD5
viewport=g('glViewport',None,[I,I,I,I]);draw=g('glDrawArrays',None,[U,I,I]);read=g('glReadPixels',None,[I,I,I,I,U,U,P])
def pass_(mode,src,dst):
    tw,th=(w,h) if dst==3 else (w//4,h//4)
    uniform('Mode',mode);uniform('Texel',(1/w,1/h) if src==0 else (4/w,4/h))
    active(0x84C0);bindtex(0x0DE1,textures[src]);bindfbo(0x8D40,fbos[dst]);viewport(0,0,tw,th);draw(4,0,3)
active(0x84C1);bindtex(0x0DE1,textures[0]);pass_(0,0,1);pass_(1,1,2)
active(0x84C1);bindtex(0x0DE1,textures[2])
def composite(**values):
    for key,value in values.items():uniform(key,float(value))
    pass_(2,0,3)
    pixels=np.empty((h,w,4),np.uint8);read(0,0,w,h,0x1908,0x1401,pixels.ctypes.data)
    assert g('glGetError',U,[])()==0,'OpenGL error'
    return pixels
baseline=composite(Strength=0)
assert np.max(np.abs(baseline.astype(int)-input_pixels.astype(int)))<=1,'Disabled effect changed the frame'
bloom=composite(Strength=1)
assert ((bloom[:,:,:3].astype(int)-baseline[:,:,:3].astype(int)).max(axis=2)>15).sum()>1500,'Bloom did not spread outside bright geometry'
mono=composite(Mono=1)
assert np.max(np.ptp(mono[:,:,:3].astype(int),axis=2))<=1,'Impact frame is not monochrome'
action=composite(Mono=0,Power=1,Speed=1)
assert np.mean(np.abs(action.astype(int)-bloom.astype(int)))>1,'Distortion/speed-lines did not alter the frame'
atomic=composite(Mono=0,Power=0,Speed=0,AtomicCharge=1)
assert atomic[30,30,:3].mean()<bloom[30,30,:3].mean(),'Atomic charge did not darken the background'
ink=composite(AtomicCharge=0,AtomicInk=1)
assert (ink[:,:,:3].astype(int)<=bloom[:,:,:3].astype(int)+1).all(),'Atomic ink must never add a white field'
assert (ink[:,:,:3].astype(float)>=bloom[:,:,:3]*.24-1).all(),'Scene must remain visible beneath debris'
assert ((bloom[:,:,:3].astype(int)-ink[:,:,:3].astype(int)).max(axis=2)>4).sum()>1000,'Missing dark ink debris'
ink2=composite(Time=11.0)
assert (np.abs(ink.astype(int)-ink2.astype(int)).max(axis=2)>4).sum()>500,'Ink streaks must move'
reset=composite(AtomicCharge=0,AtomicInk=0,Time=10.3)
assert np.max(np.abs(reset.astype(int)-bloom.astype(int)))<=1,'Atomic uniforms leaked into the normal effect'
heaven=composite(HeavenTone=1,Power=1,Speed=1)
assert heaven[:,:,:1].mean()>action[:,:,:1].mean(),'Golden technique should raise the warm channel'
reset_heaven=composite(HeavenTone=0,Power=0,Speed=0)
assert np.max(np.abs(reset_heaven.astype(int)-bloom.astype(int)))<=1,'Heaven tint leaked into other spells'
out=root/'build/shader-check' ;out.mkdir(parents=True,exist_ok=True)
for name,pixels in [('fixture',input_pixels),('bloom',bloom),('impact',mono),('action',action),('atomic-charge',atomic),('atomic-ink',ink),('heaven-gold',heaven)]:Image.fromarray(pixels[::-1]).save(out/(name+'.png'))
report={'shader_compile':'passed','shader_link':'passed','three_pass_render':'passed','disabled_passthrough':'passed','bloom_spread':'passed','monochrome_impact':'passed','distortion_speed_lines':'passed','atomic_charge_reset':'passed','atomic_ink_reset':'passed','atomic_ink_shader_no_whiteout':'passed','heaven_tint_reset':'passed','renderer':g('glGetString',C.c_char_p,[U])(0x1F01).decode(),'note':'Synthetic fixture on headless OpenGL, not Minecraft gameplay. Tests world shader only; AtomicHud owns the 2.3.2 three-second whiteout.'}
(out/'result.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))
fn(egl,'eglMakeCurrent',U,[P,P,P,P])(display,None,None,None)
fn(egl,'eglDestroyContext',U,[P,P])(display,context)
fn(egl,'eglDestroySurface',U,[P,P])(display,surface)
fn(egl,'eglTerminate',U,[P])(display)
