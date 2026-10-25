#version 150
// Star Platinum's time stop (2.67.0, client/stand/TimeStopView): the stopped world, drawn over the frame (or, for whom
// it stopped, over the still of the moment it stopped). A sphere (Center, Radius: blocks, camera-relative) is the reach
// of the stopped time: what is inside it is the stopped world, a cold violet-blue monochrome (Grade); the world outside
// and the sky join it once the wave has spread over all of it (Outer). A band of the negative runs just behind the
// wave's front (Band blocks wide, Negative strong; BandSide 1 behind it as it spreads, -1 outside it as it falls back),
// and the front itself glows (Rim). Flash whitens the whole frame. Without the scene's depth (a shader pack draws the
// frame) the wave is a circle on the screen round where Center shows (CenterScreen, ScreenRadius).
uniform sampler2D SceneTex;
uniform sampler2D DepthTex;
uniform mat4 InvProjView;
uniform vec3 Center;
uniform float Radius;
uniform float Band;
uniform float BandSide;
uniform float Negative;
uniform float Rim;
uniform float Grade;
uniform float Outer;
uniform float Flash;
uniform float HasDepth;
uniform vec2 CenterScreen;
uniform float ScreenRadius;
uniform float Aspect;
in vec2 texCoord;
out vec4 fragColor;

const vec3 LUMA=vec3(.299,.587,.114);

// the stopped world: the luminance, a little harder, through a cold ramp (deep violet shadows, blue mids, white lights)
vec3 stopped(vec3 c){
    float l=clamp((dot(c,LUMA)-.5)*1.12+.5,0.,1.);
    vec3 lo=vec3(.07,.05,.18),mid=vec3(.40,.43,.68),hi=vec3(.93,.95,1.);
    return l<.5?mix(lo,mid,l*2.):mix(mid,hi,l*2.-1.);
}
// the negative of the wave's front: inverted, half drained of colour, tinted violet
vec3 negative(vec3 c){
    vec3 n=1.-c;
    return clamp(mix(vec3(dot(n,LUMA)),n,.55)*vec3(1.,.86,1.22),0.,1.);
}
vec3 world(vec2 uv,float d){
    vec4 p=InvProjView*vec4(uv*2.-1.,d*2.-1.,1.);
    return p.xyz/p.w;
}

void main(){
    vec3 c=texture(SceneTex,texCoord).rgb;
    float dist,width;
    if(HasDepth>.5){
        float d=texture(DepthTex,texCoord).r;
        // the sky (nothing drawn there) is as far as can be: outside the sphere
        dist=d>=1.?1e6:length(world(texCoord,d)-Center);
        width=1.;
    }else{
        // no depth: the wave as a circle on the screen, its radius a share of the screen's height
        vec2 q=(texCoord-CenterScreen)*vec2(Aspect,1.);
        dist=length(q);width=.012;
    }
    float r=HasDepth>.5?Radius:ScreenRadius;
    float inside=1.-smoothstep(r-1.2*width,r+.4*width,dist);
    float w=max(inside*Grade,Outer);
    vec3 col=mix(c,stopped(c),clamp(w,0.,1.));
    // the negative band just behind the front (or just outside it as it falls back)
    float behind=(r-dist)*BandSide;
    float band=Negative*step(0.,behind)*(1.-smoothstep(0.,Band*width,behind));
    col=mix(col,negative(c),clamp(band,0.,1.));
    // the front glows
    float rim=Rim*exp(-pow((dist-r)/(.7*width),2.));
    col+=rim*vec3(.78,.62,1.);
    col=mix(col,vec3(.96,.93,1.),clamp(Flash,0.,1.));
    fragColor=vec4(col,1.);
}
