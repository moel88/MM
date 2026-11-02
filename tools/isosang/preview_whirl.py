"""Top-down preview of Lee So-sang's H whirlpool (the ink shader, kageno_flame mode 16 style 15) on headless Mesa.

Not a game screenshot: a developer's look at the shape before a shoot (never sent as in-game verification). It draws the
formation's blue ground (style 14) and the whirlpool at SwordFormation's sizes (whirl, eye) seen from straight above,
north up, 20.5 blocks either side, blended and then its light added (crescentGain .18, Hot 1.8) as the ground pass does.
Needs libEGL (apt-get install libegl1 libegl-mesa0).

    python3 -I tools/isosang/preview_whirl.py src/main/resources/assets/celestialfall/shaders/core .78,.95,1.25 560 out.png
"""
import ctypes as C, ctypes.util, os, sys, math
import numpy as np
from PIL import Image, ImageDraw
os.environ.setdefault('EGL_PLATFORM','surfaceless');os.environ.setdefault('LIBGL_ALWAYS_SOFTWARE','1')
egl=C.CDLL('libEGL.so.1');gl=C.CDLL('libGL.so.1')
I=C.c_int;U=C.c_uint;P=C.c_void_p;F=C.c_float
def fn(lib,name,res,args):
    f=getattr(lib,name);f.restype=res;f.argtypes=args;return f
display=fn(egl,'eglGetDisplay',P,[P])(None)
assert fn(egl,'eglInitialize',U,[P,C.POINTER(I),C.POINTER(I)])(display,None,None)
assert fn(egl,'eglBindAPI',U,[U])(0x30A2)
attrs=(I*13)(0x3033,1,0x3040,8,0x3024,8,0x3023,8,0x3022,8,0x3021,8,0x3038)
config=P();count=I()
assert fn(egl,'eglChooseConfig',U,[P,C.POINTER(I),C.POINTER(P),I,C.POINTER(I)])(display,attrs,C.byref(config),1,C.byref(count))
ctx=fn(egl,'eglCreateContext',P,[P,P,P,C.POINTER(I)])(display,config,None,(I*7)(0x3098,3,0x30FB,2,0x30FD,1,0x3038))
W=H=int(sys.argv[3]) if len(sys.argv)>3 else 560
surf=fn(egl,'eglCreatePbufferSurface',P,[P,P,C.POINTER(I)])(display,config,(I*5)(0x3057,W,0x3056,H,0x3038))
assert fn(egl,'eglMakeCurrent',U,[P,P,P,P])(display,surf,surf,ctx)
def g(name,res,args):return fn(gl,name,res,args)
root=sys.argv[1]
def sh(kind,path):
    sid=g('glCreateShader',U,[U])(kind);raw=C.c_char_p(open(path,'rb').read());g('glShaderSource',None,[U,I,C.POINTER(C.c_char_p),C.POINTER(I)])(sid,1,C.byref(raw),None);g('glCompileShader',None,[U])(sid)
    ok=I();g('glGetShaderiv',None,[U,U,C.POINTER(I)])(sid,0x8B81,C.byref(ok))
    if not ok.value:
        buf=C.create_string_buffer(16384);g('glGetShaderInfoLog',None,[U,I,P,P])(sid,16384,None,buf);raise SystemExit('COMPILE FAILED '+path+'\n'+buf.value.decode())
    return sid
prog=g('glCreateProgram',U,[])()
for k,n in [(0x8B31,'kageno_flame.vsh'),(0x8B30,'kageno_flame.fsh')]:g('glAttachShader',None,[U,U])(prog,sh(k,root+'/'+n))
for i,n in enumerate(['Position','UV0','Color']):g('glBindAttribLocation',None,[U,U,C.c_char_p])(prog,i,n.encode())
g('glLinkProgram',None,[U])(prog);ok=I();g('glGetProgramiv',None,[U,U,C.POINTER(I)])(prog,0x8B82,C.byref(ok));assert ok.value,'link failed'
g('glUseProgram',None,[U])(prog)
loc=lambda n:g('glGetUniformLocation',I,[U,C.c_char_p])(prog,n.encode())
ident=(F*16)(1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1)
g('glUniformMatrix4fv',None,[I,I,U,P])(loc('ModelViewMat'),1,0,ident);g('glUniformMatrix4fv',None,[I,I,U,P])(loc('ProjMat'),1,0,ident)
g('glUniform4f',None,[I,F,F,F,F])(loc('ColorModulator'),1,1,1,1);g('glUniform1i',None,[I,I])(loc('Mode'),16);g('glUniform1f',None,[I,F])(loc('Age'),0.0)
hotloc=loc('Hot')
vao=U();g('glGenVertexArrays',None,[I,C.POINTER(U)])(1,C.byref(vao));g('glBindVertexArray',None,[U])(vao)
vbo=U();g('glGenBuffers',None,[I,C.POINTER(U)])(1,C.byref(vbo));g('glBindBuffer',None,[U,U])(0x8892,vbo)
for i,(sz,off) in enumerate([(3,0),(2,12),(4,20)]):
    g('glEnableVertexAttribArray',None,[U])(i);g('glVertexAttribPointer',None,[U,I,U,U,I,P])(i,sz,0x1406,0,36,P(off))
fbo=U();tex=U();g('glGenFramebuffers',None,[I,C.POINTER(U)])(1,C.byref(fbo));g('glGenTextures',None,[I,C.POINTER(U)])(1,C.byref(tex))
g('glBindTexture',None,[U,U])(0x0DE1,tex);g('glTexImage2D',None,[U,I,I,I,I,I,U,U,P])(0x0DE1,0,0x8058,W,H,0,0x1908,0x1401,None)
g('glBindFramebuffer',None,[U,U])(0x8D40,fbo);g('glFramebufferTexture2D',None,[U,U,U,U,I])(0x8D40,0x8CE0,0x0DE1,tex,0)
g('glViewport',None,[I,I,I,I])(0,0,W,H)
g('glEnable',None,[U])(0x0BE2)
def quad(half,span,k,code,gb,bb,alpha):
    s=half/span
    v=[]
    for cx,cy in [(-1,-1),(1,-1),(1,1),(-1,-1),(1,1),(-1,1)]:
        # uv.x = east = screen x; uv.y = south = -screen y (north up)
        v+= [cx*s,cy*s,0, cx*k,-cy*k, code/255.0,gb,bb,alpha]
    arr=(F*len(v))(*v);g('glBufferData',None,[U,C.c_ssize_t,P,U])(0x8892,C.sizeof(arr),arr,0x88E0)
    g('glDrawArrays',None,[U,I,I])(4,0,6)
def smooth(t,a,b):
    x=min(max((t-a)/(b-a),0),1);return x*x*(3-2*x)
def frame(R,eye_share,tsec,span,seed=5,alpha=1.0,ground=True):
    g('glClearColor',None,[F,F,F,F])(.36,.42,.30,1);g('glClear',None,[U])(0x4000)
    clock=min(max(tsec/2,0),1);eb=min(max(eye_share,0),1)
    # pass 1: blended
    g('glBlendFunc',None,[U,U])(0x0302,0x0303);g('glUniform1f',None,[I,F])(hotloc,0.0)
    # the formation's blue ground, gone with the whirlpool (SwordFormation.tint)
    if ground:quad(10.6,span,1.0,14*16+(seed&15),0,0,.58*smooth(tsec,.7,.85)*(1-smooth(tsec,1.04,1.2)))
    if R>0.02:quad(R*1.4,span,1.4,15*16+((seed+5)&15),round(clock*255)/255,round(eb*255)/255,alpha)
    # pass 2: additive glow (crescentGain .18, Hot 1.8)
    g('glBlendFunc',None,[U,U])(0x0302,1);g('glUniform1f',None,[I,F])(hotloc,1.8)
    if R>0.02:quad(R*1.4,span,1.4,15*16+((seed+5)&15),round(clock*255)/255,round(eb*255)/255,alpha*.18)
    px=np.empty((H,W,4),np.uint8);g('glReadPixels',None,[I,I,I,I,U,U,P])(0,0,W,H,0x1908,0x1401,px.ctypes.data)
    err=g('glGetError',U,[])();assert err==0,hex(err)
    return Image.fromarray(px[::-1].copy()).convert('RGB')
def plan(t):
    # SwordFormation.whirl and eye (2.82.7)
    R=14.5*smooth(t,.70,.86) if t>=.70 else 0
    E=5.0*smooth(t,.73,.85)
    return R,(E/R/.6 if R>1e-6 else 0)
times=[float(v) for v in sys.argv[2].split(',')]
tiles=[]
for t in times:
    R,es=plan(t)
    a=smooth(t,.70,.72)*(1-smooth(t,1.43,1.49))
    im=frame(R,es,t,20.5,alpha=a)
    d=ImageDraw.Draw(im);d.rectangle((0,0,170,18),fill=(0,0,0));d.text((4,4),f't={t:.2f}s R={R:.1f} eye={es*.6*R:.1f}',fill=(255,255,0))
    tiles.append(im)
cols=3;rows=(len(tiles)+cols-1)//cols
sheet=Image.new('RGB',(W*cols,H*rows))
for i,im in enumerate(tiles):sheet.paste(im,((i%cols)*W,(i//cols)*H))
sheet.save(sys.argv[4] if len(sys.argv)>4 else 'preview.png');print('ok')
