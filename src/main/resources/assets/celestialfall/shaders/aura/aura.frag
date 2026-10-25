#version 150
// The aura round a stand and its user (2.53.0): a violet glow wrapping each whole body, licking upwards in tongues that
// flow up out of it and flicker. It is violet all through, brightest along the body's edge, and nothing is drawn on the
// body itself: no white rim ("몸에 흰색 테두리는 없애고"). Premultiplied: it lays its violet over what is behind it and
// adds a little light, never so much that it burns to white.
// And the sky-blue glow of a body using a skill with a cutscene (2.65.0, "몸 전체가 하늘색으로 빛이 나야하는 쪽에
// 가까움 그러니까 몸 색깔을 바꾸는건 X"): sky-blue light spreading out round its whole outline, palest and brightest
// right at it, mostly added light, and along the body's edges just inside its outline a little light (as a light behind
// it would cast); the body's own colours are left as they are.
uniform sampler2D MaskA;     // the stands (alpha: how solid)
uniform sampler2D MaskB;     // their users
uniform sampler2D MaskC;     // the bodies glowing sky blue
uniform sampler2D Halo;      // all three, blurred: red the stands, green the users, the glowing blue (wide) and alpha (close) (half size)
uniform sampler2D Noise;     // tiling noise: red smooth, green finer
uniform vec2 Texel;          // one screen pixel
uniform float Scale;         // screen pixels a block, where the bearers are
uniform vec2 Anchor;         // the stand on screen (pixels), so the flames move with it
uniform vec3 Phase;          // the flow of the three layers of flame (texture units, wrapped)
uniform vec2 Gain;           // the stands', their users'
uniform float Flare;         // 0..1: bursting into its pose
uniform float Front;         // 1 when the users are nearer the camera than the stands, 0 when the stands are
uniform float Glow;          // 1 when any body glows sky blue
in vec2 texCoord;
out vec4 fragColor;

vec3 tone(float heat){
    // deep violet out in the tongues, a brighter violet close to the body
    return mix(vec3(0.34,0.13,0.78),vec3(0.58,0.32,1.0),heat);
}
vec4 aura(vec2 px){
    // the flames' pattern in blocks round the bearers, flowing upwards
    vec2 q=(px-Anchor)/max(Scale,1.0);
    float n1=texture(Noise,q*vec2(0.55,0.32)+vec2(0.0,-Phase.x)).r;
    float n2=texture(Noise,q*vec2(1.1,0.7)+vec2(0.37,-Phase.y)).g;
    float n3=texture(Noise,q*vec2(2.4,1.6)+vec2(0.71,-Phase.z)).r;
    float f=n1*0.55+n2*0.3+n3*0.15;
    // the glow at a point rises from the body below it: further where the flicker is high (a tongue), swaying
    float lift=Scale*(0.04+0.32*f*f);
    float sway=Scale*0.07*(n2-0.5);
    vec2 h=texture(Halo,(px+vec2(sway,-lift))*Texel).rg;
    vec2 h0=texture(Halo,px*Texel).rg;
    vec2 m=vec2(texture(MaskA,texCoord).a,texture(MaskB,texCoord).a);
    vec2 cov=smoothstep(vec2(0.02),vec2(0.18),m);
    // only outside the bodies, and the one further back keeps its glow off the nearer one's body
    vec2 behind=vec2(Front,1.0-Front);
    vec2 overNear=behind*vec2(cov.y,cov.x);
    vec2 glow=max(h*(0.55+0.9*f),h0*0.8)*(1.0-cov)*(1.0-overNear);
    vec2 amount=smoothstep(vec2(0.05),vec2(0.55),glow)*Gain*(1.0+0.6*Flare);
    float total=amount.x+amount.y;
    if(total<=0.002)return vec4(0.0);
    float heat=clamp(max(h0.x,h0.y)*1.4,0.0,1.0);
    float cover=min(total,1.0)*0.72;
    return vec4(tone(heat)*cover*1.25,cover);
}
vec4 sky(vec2 px){
    if(Glow<=0.0)return vec4(0.0);
    float cov=smoothstep(0.02,0.18,texture(MaskC,texCoord).a);
    vec4 h=texture(Halo,px*Texel);
    float wide=h.b,close=h.a;
    if(wide<=0.003&&close<=0.003)return vec4(0.0);
    vec3 sky=vec3(0.45,0.84,1.0),pale=vec3(0.84,0.97,1.0);
    // outside the body: a broad soft light all round it, and a brighter, paler one close to its outline
    float out1=smoothstep(0.0,0.32,wide)*(1.0-cov);
    float out2=smoothstep(0.1,0.5,close)*(1.0-cov);
    // on the body, only just inside its outline: light along its edges (as a light behind it would), its own colours
    // left as they are everywhere else
    float rim=cov*(1.0-smoothstep(0.55,0.92,close))*smoothstep(0.02,0.3,close);
    vec3 col=sky*(1.15*out1+0.35*rim)+mix(sky,pale,0.65)*(0.95*out2);
    return vec4(col,0.26*out1+0.2*out2);
}
void main(){
    vec2 px=gl_FragCoord.xy;
    vec4 au=aura(px),gl=sky(px);
    // the glow over the aura
    vec4 o=vec4(gl.rgb+au.rgb*(1.0-gl.a),gl.a+au.a*(1.0-gl.a));
    if(o.a<=0.002&&o.r+o.g+o.b<=0.006)discard;
    fragColor=o;
}
