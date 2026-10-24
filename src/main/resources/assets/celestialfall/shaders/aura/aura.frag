#version 150
// The aura round a stand and its user (2.53.0): a violet glow wrapping each whole body, licking upwards in tongues that
// flow up out of it and flicker. It is violet all through, brightest along the body's edge, and nothing is drawn on the
// body itself: no white rim ("몸에 흰색 테두리는 없애고"). Premultiplied: it lays its violet over what is behind it and
// adds a little light, never so much that it burns to white.
uniform sampler2D MaskA;     // the stands (alpha: how solid)
uniform sampler2D MaskB;     // their users
uniform sampler2D Halo;      // both, blurred: red the stands, green the users (half size)
uniform sampler2D Noise;     // tiling noise: red smooth, green finer
uniform vec2 Texel;          // one screen pixel
uniform float Scale;         // screen pixels a block, where the bearers are
uniform vec2 Anchor;         // the stand on screen (pixels), so the flames move with it
uniform vec3 Phase;          // the flow of the three layers of flame (texture units, wrapped)
uniform vec2 Gain;           // the stands', their users'
uniform float Flare;         // 0..1: bursting into its pose
uniform float Front;         // 1 when the users are nearer the camera than the stands, 0 when the stands are
in vec2 texCoord;
out vec4 fragColor;

vec3 tone(float heat){
    // deep violet out in the tongues, a brighter violet close to the body
    return mix(vec3(0.34,0.13,0.78),vec3(0.58,0.32,1.0),heat);
}
void main(){
    vec2 px=gl_FragCoord.xy;
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
    if(total<=0.002)discard;
    float heat=clamp(max(h0.x,h0.y)*1.4,0.0,1.0);
    float cover=min(total,1.0)*0.72;
    fragColor=vec4(tone(heat)*cover*1.25,cover);
}
