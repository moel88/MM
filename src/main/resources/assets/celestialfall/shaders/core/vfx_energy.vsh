#version 150
// Botand VFX: unit meshes placed by ModelViewMat (static GPU meshes), camera-relative sprites, and
// the energy streak field (mode 17) whose quads are moved entirely here.
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int Mode;
uniform float Time;
uniform vec4 Params;
uniform vec4 Params2;
uniform vec4 Params3;
uniform vec2 ScreenSize;
out vec2 uv;
out vec4 tint;
out vec3 viewPos;
out vec3 viewNrm;
out vec3 local;

const float TAU=6.2831853;

// Streak field around the pillar (mode 17). Every quad carries random seeds (Position, Color) and its
// corner (UV0); the model matrix only places the pillar's base, in blocks. Each streak climbs the
// pillar's surface layer on its own lane and speed, wraps from the base back to the top, and is turned
// toward the camera. Params: x pillar radius, y height (blocks from the base), z blast radius R,
// w brightness. Params2: x speed scale, y share of the streaks drawn, z 1 when the camera is inside the
// pillar. Params3: x height of the surge front above the base (blocks, < 0 when none), y surge strength.
void streak(){
    float r=Params.x,h=Params.y,R=Params.z;
    vec3 s=Position;vec4 q=Color;
    bool bolt=q.b>0.9;
    float len=(bolt?0.12:0.045+0.085*q.r)*h;
    float width=(bolt?0.02:0.006+0.011*q.g)*R;
    float speed=(bolt?15.0:8.0+6.0*s.z)*R*Params2.x;
    float span=h+len;
    // Head height above the base; the streak enters below the ground and leaves through the top.
    float head=fract(s.y+Time*speed/span)*span;
    float tail=head-len;
    float top=min(head,h),bottom=max(tail,0.0);
    float alive=step(bottom+0.01,top)*step(q.a,Params2.y);
    // A slow helix: lanes turn a fifth of a radian on the way up.
    float rho=r*(bolt?0.99:0.9+0.1*s.z);
    float a0=s.x*TAU+0.2*bottom/h,a1=s.x*TAU+0.2*top/h;
    vec3 pt=vec3(cos(a0)*rho,bottom,sin(a0)*rho),ph=vec3(cos(a1)*rho,top,sin(a1)*rho);
    vec3 vt=(ModelViewMat*vec4(pt,1.0)).xyz,vh=(ModelViewMat*vec4(ph,1.0)).xyz;
    vec3 axis=vh-vt,mid=0.5*(vt+vh);
    vec3 side=cross(axis,mid);
    float sl=length(side);
    side=sl>1e-6?side/sl:vec3(1.0,0.0,0.0);
    // Never thinner than about 1.5 pixels; thinner streaks are widened and dimmed by the same factor.
    float depth=max(0.05,-mid.z);
    float pixel=2.0*depth/(ProjMat[1][1]*max(ScreenSize.y,1.0));
    float w=max(width,1.5*pixel);
    float thin=width/w;
    // Only the half facing the camera shows (the opaque body hides the rest); from inside, all of it.
    vec3 n=normalize(mat3(ModelViewMat)*vec3(cos(0.5*(a0+a1)),0.0,sin(0.5*(a0+a1))));
    float facing=dot(n,normalize(-mid));
    float vis=Params2.z>0.5?1.0:smoothstep(-0.06,0.12,facing);
    // Fade at the very top so streaks leave into the sky instead of being cut.
    float leave=1.0-smoothstep(h*0.93,h,head);
    // Surge: streaks whose heads ride the surge front flare up (a band of light, not the whole pillar).
    float boost=Params3.x<0.0?0.0:Params3.y*exp(-abs(head-Params3.x)/(0.35*R));
    float level=Params.w*vis*thin*alive*(bolt?1.5:0.7+0.4*q.a)*(1.0+0.9*boost)*mix(1.0,leave,step(h,head+len));
    vec3 pos=mix(vt,vh,UV0.y)+side*(UV0.x*2.0-1.0)*0.5*w;
    if(alive<0.5||level<0.002)pos=vec3(0.0,0.0,-1e6);
    gl_Position=ProjMat*vec4(pos,1.0);
    // local: x bottom and z top of the visible part (blocks above the base), y its length in widths.
    uv=UV0;tint=vec4(bolt?1.0:0.0,boost,0.0,level);viewPos=pos;viewNrm=n;local=vec3(bottom,(top-bottom)/max(w,1e-3),top);
}

void main(){
    if(Mode==17){streak();return;}
    vec4 view=ModelViewMat*vec4(Position,1.0);
    gl_Position=ProjMat*view;
    uv=UV0;tint=Color;viewPos=view.xyz;local=Position;
    // On the unit cylinder the radial direction is the surface normal (the xz scale is uniform); on the
    // unit dome (mode 15, uniform scale) it is the position itself.
    viewNrm=mat3(ModelViewMat)*(Mode==15?Position:vec3(Position.x,0.0,Position.z));
}
