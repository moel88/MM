"""
A look at X Chara's great X slash's blades outside the game (2.136.0): the game's own fragment shader
(kageno_flame.fsh, mode 18 styles 3 to 6) drawn on the blades' ribbons as client/xchara/XBlade lays them, over a
plain sky and floor, from a few cameras. For tuning the shader in seconds instead of a ten minute capture.
What it draws is NOT a game screen: never send it as a check of the game (CLAUDE.md); the game's effect layer
(bloom, the second glow pass) is only roughly stood in for, and nothing but the ribbons is drawn.

    pip install moderngl
    xvfb-run -a python3 tools/xchara/preview_xblade.py OUT.png 0.25,0.48,0.56,0.62 game,side

Times are seconds into the skill; cameras: game (behind its user), side, front, q (ahead and above).
"""
import moderngl,numpy as np,math,sys
from PIL import Image
import os
FSH=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..','src','main','resources','assets','celestialfall','shaders','core','kageno_flame.fsh')
VSH='''#version 150
in vec3 Position;in vec2 UV0;in vec4 Color;uniform mat4 MVP;out vec2 uv;out vec4 tint;
void main(){gl_Position=MVP*vec4(Position,1.0);uv=UV0;tint=Color;}'''
BG_V='''#version 150
in vec2 P;out vec2 q;void main(){q=P;gl_Position=vec4(P,0.0,1.0);}'''
BG_F='''#version 150
in vec2 q;out vec4 o;uniform mat4 INV;uniform vec3 EYE;
void main(){vec4 a=INV*vec4(q,1.0,1.0);vec3 far=a.xyz/a.w;vec3 d=normalize(far-EYE);
 vec3 sky=mix(vec3(.72,.82,.98),vec3(.42,.6,.95),clamp(d.y*1.6,0.0,1.0));
 if(d.y<-0.001){float t=-EYE.y/d.y;vec3 p=EYE+d*t;vec2 g=floor(p.xz/3.0);float c=mod(g.x+g.y,2.0);
  vec2 f=fract(p.xz);float line=step(.96,max(f.x,f.y));
  vec3 gr=mix(vec3(.5,.5,.47),vec3(.42,.42,.4),c)*(1.0-.12*line);float fog=clamp(t/90.0,0.0,1.0);o=vec4(mix(gr,vec3(.6,.72,.95),fog*fog),1.0);
 }else o=vec4(sky,1.0);}'''
FL_F='''#version 150
in vec2 uv;in vec4 tint;out vec4 o;void main(){o=tint;}'''
def persp(fov,aspect,n=.05,f=300):
    t=1/math.tan(math.radians(fov)/2);m=np.zeros((4,4));m[0,0]=t/aspect;m[1,1]=t;m[2,2]=(f+n)/(n-f);m[2,3]=2*f*n/(n-f);m[3,2]=-1;return m
def look(eye,at,up=(0,1,0)):
    eye=np.array(eye,float);at=np.array(at,float);f=at-eye;f/=np.linalg.norm(f);s=np.cross(f,up);s/=np.linalg.norm(s);u=np.cross(s,f)
    m=np.eye(4);m[0,:3]=s;m[1,:3]=u;m[2,:3]=-f;m[:3,3]=-m[:3,:3]@eye;return m
# ── the rules (XSlashRules) ──
NEAR,SPEED,RANGE,GROW=2.0,2.0,18.0,3.4
HALF0,HALF,TALL0,TALL=2.5,5.0,1.5,2.8
def sm(x,a,b):
    x=max(0,min(1,(x-a)/(b-a))) if b!=a else float(x>=a);return x*x*(3-2*x)
def half(fw):return HALF0+(HALF-HALF0)*sm(fw,NEAR,NEAR+GROW)
def tall(fw):return TALL0+(TALL-TALL0)*sm(fw,NEAR,NEAR+GROW)
# ── the blade (client/xchara/XBlade: keep these the same) ──
P=dict(OUT=.105,IN=-.095,POW=1.15,SWEEP=.13,WAKE=3.4,N=40,HALO=.55)
def at(fw,side,up):return np.array([-side,up,fw])  # from the origin, f=+z, the game's right for it -x
def arm(fw,h,tl,a,upTo=1,open_=1,sweep=0,p=P,inn_k=None,out_k=None):
    inn_k=p['IN'] if inn_k is None else inn_k;out_k=p['OUT'] if out_k is None else out_k
    sx=h if a==0 else -h;sy=2*tl;ex=-sx;ey=0
    nx=-tl if a==0 else tl;ny=h;nl=math.hypot(nx,ny);nx/=nl;ny/=nl
    L=math.hypot(ex-sx,ey-sy);n=max(2,int(math.ceil(p['N']*upTo))+1);deep=open_*(.35+.65*upTo)
    inn=[];out=[];al=[]
    for i in range(n):
        t=upTo*i/(n-1);cx=sx+(ex-sx)*t;cy=sy+(ey-sy)*t;s=max(0,math.sin(math.pi*i/(n-1)))**p['POW']*deep
        back=sweep*L*(2*t-1)**2
        inn.append(at(fw-back,cx+nx*inn_k*L*s,cy+ny*inn_k*L*s));out.append(at(fw-back,cx+nx*out_k*L*s,cy+ny*out_k*L*s));al.append(t)
    return inn,out,al
def ribbon(inn,out,al,style,seed,eat,b,alpha):
    code=(style&15)*16+(seed&15);v=[]
    def pt(p,u,vv):v.extend([p[0],p[1],p[2],u,vv,code/255,eat,b,alpha])
    for i in range(len(inn)-1):
        pt(inn[i],al[i],0);pt(inn[i+1],al[i+1],0);pt(out[i+1],al[i+1],1)
        pt(inn[i],al[i],0);pt(out[i+1],al[i+1],1);pt(out[i],al[i],1)
    return v
def grown(fw):return sm(fw,NEAR,NEAR+2.5)
def scene(t,p=P):
    """The X's ribbons t seconds into the skill, as XBlade.ribbons lays them (without the edge turned to the camera)."""
    A=((.185,.27),(.39,.47));FIRE=.5
    fw=NEAR if t<FIRE else min(RANGE,NEAR+SPEED*(t-FIRE)*20)
    h=half(fw);tl=tall(fw);end=FIRE+(RANGE-NEAR)/SPEED/20;eat=0 if t<end else min(1,(t-end)/.14)
    flying=t>=FIRE;grow=grown(fw) if flying else 0;sweep=p['SWEEP']*grow
    halos=[];rest=[];faces=[]
    for a in range(2):
        fr,to=A[a];cut=sm(t,fr,to)
        if cut<=.01:continue
        s=t-fr;open_=1-math.exp(-s/.03)*math.cos(s*45);fresh=1-sm(t,to-.02,to+.1)
        inn,out,al=arm(fw,h,tl,a,cut,open_,sweep,p)
        hi,ho,ha=arm(fw,h,tl,a,cut,open_,sweep,p,p['IN']*2.6-.02,p['OUT']*2.2+.02)
        halos+=ribbon(hi,ho,ha,6,a,eat,0,p['HALO']*(1-.5*fresh))
        if flying and grow>.02:
            L=p['WAKE']*grow*(h/HALF)
            back=[o-np.array([0,0,L*max(0,math.sin(math.pi*u))**.6]) for o,u in zip(out,al)]
            rest+=ribbon(back,out,al,4,a*5+3,eat,0,.9*grow)
        if flying:
            for lag,col,al_ in ((.07,0,.22),(.044,2,.3),(.022,1,.4)):
                if t-lag<FIRE:continue
                f2=min(RANGE,NEAR+SPEED*(t-lag-FIRE)*20);gi,go,ga=arm(f2,half(f2),tall(f2),a,1,1,p['SWEEP']*grown(f2),p)
                rest+=ribbon(gi,go,ga,5,col,eat,0,al_*(1-eat))
        elif fresh>.05:
            for col,off in ((1,.12),(2,-.12)):
                d=np.array([-off*fresh,-off*fresh*.3,0])
                rest+=ribbon([x+d for x in inn],[x+d for x in out],al,5,col,eat,0,.6*fresh)
        faces+=ribbon(inn,out,al,3,a*7+1,eat,fresh,1)
    return np.array(halos+rest+faces,'f4')
def render(ctx,progs,t,eye,target,fov=70,size=(960,540),p=P,age=None,hot=1.0):
    prog,bg,flat=progs;W,H=size
    fbo=ctx.framebuffer([ctx.texture((W,H),4,dtype='f4')]);fbo.use();ctx.viewport=(0,0,W,H)
    proj=persp(fov,W/H);view=look(eye,target);mvp=proj@view;inv=np.linalg.inv(mvp)
    ctx.disable(moderngl.BLEND);ctx.disable(moderngl.DEPTH_TEST);ctx.disable(moderngl.CULL_FACE)
    bg['INV'].write(inv.T.astype('f4').tobytes());bg['EYE'].value=tuple(eye)
    q=ctx.buffer(np.array([-1,-1,1,-1,-1,1,1,-1,1,1,-1,1],'f4'));ctx.vertex_array(bg,[(q,'2f','P')]).render()
    v=scene(t,p)
    if len(v):
        ctx.enable(moderngl.BLEND);ctx.blend_func=(moderngl.SRC_ALPHA,moderngl.ONE_MINUS_SRC_ALPHA)
        prog['MVP'].write(mvp.T.astype('f4').tobytes());prog['Age'].value=(t if age is None else age)*20;prog['Mode'].value=18
        prog['Hot'].value=hot;prog['ColorModulator'].value=(1,1,1,1)
        vb=ctx.buffer(v.tobytes());ctx.vertex_array(prog,[(vb,'3f 2f 4f','Position','UV0','Color')]).render()
    img=np.frombuffer(fbo.read(components=4,dtype='f4'),'f4').reshape(H,W,4)[::-1,:,:3].copy()
    # the effect layer, roughly: what is over white blooms
    from PIL import ImageFilter
    over=np.clip(img-1.0,0,4);img=np.clip(img,0,1)
    if over.max()>0:
        o=Image.fromarray((np.clip(over/2,0,1)*255).astype('u1')).filter(ImageFilter.GaussianBlur(7))
        img=np.clip(img+np.asarray(o).astype('f4')/255*2*.55,0,1)
    return Image.fromarray((img*255).astype('u1'))
def make():
    ctx=moderngl.create_standalone_context()
    prog=ctx.program(vertex_shader=VSH,fragment_shader=open(FSH).read())
    bg=ctx.program(vertex_shader=BG_V,fragment_shader=BG_F)
    flat=ctx.program(vertex_shader=VSH,fragment_shader=FL_F)
    return ctx,(prog,bg,flat)
CAMS={'game':((0,3.8,-4.5),(0,1.4,2.5),70),'side':((-7,3,1),(0,1.8,6),60),'front':((2.5,2.2,16),(0,2,4),60),'q':((-9,7,-3),(0,1.5,7),60)}
def sheet(out,times,cams=('game','side','front'),size=(800,450)):
    ctx,progs=make();W,H=size
    sh=Image.new('RGB',(W*len(times),H*len(cams)))
    for r,c in enumerate(cams):
        eye,tg,fov=CAMS[c]
        for i,t in enumerate(times):sh.paste(render(ctx,progs,t,eye,tg,fov,size),(i*W,r*H))
    sh.save(out)
if __name__=='__main__':
    sheet(sys.argv[1],[float(x) for x in sys.argv[2].split(',')],tuple(sys.argv[3].split(',')) if len(sys.argv)>3 else ('game','side','front'))
