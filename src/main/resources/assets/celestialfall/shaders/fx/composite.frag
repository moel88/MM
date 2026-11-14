#version 150
// The effect layer back onto the screen: bloom, afterglow trails and light shafts added, light above white
// turned white-hot instead of clipping to a flat colour, the finisher's impact frame, and (2.132.1) the screen inside
// X Chara's rain of knives.
uniform sampler2D HdrTex;
uniform sampler2D BloomTex;
uniform sampler2D HistTex;
uniform sampler2D LightTex;
uniform sampler2D ShaftTex;
uniform sampler2D SceneTex;
uniform sampler2D BaseTex;
uniform float BloomGain;
uniform float Afterglow;
uniform float ShaftGain;
uniform vec3 ShaftTint;
uniform float Impact;
uniform float ImpactMode;
// the impact frame's colours (FxFrame.ImpactLook: Kageno's violet unless a skill asks for its own)
uniform vec3 ImpactDeep;
uniform vec3 ImpactShapes;
uniform vec3 ImpactPale;
uniform vec3 ImpactShade;
uniform vec3 ImpactHue;
uniform float Chroma;
uniform vec2 Focus;
// (2.132.1) the screen inside X Chara's rain of knives (FxFrame.rain; 2.132.5, gently: "은은하게 30초 내내"): how much, a
// clock (game seconds), the screen's size
uniform float Rain;
uniform float RainTime;
uniform vec2 Screen;
in vec2 texCoord;
out vec4 fragColor;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
vec3 hot(vec3 c){
    c=max(c,0.0);float m=max(c.r,max(c.g,c.b));
    if(m<=1.0)return c;
    // Light a little over white keeps its colour; only light far over it burns white.
    return mix(c/m,vec3(1.0),1.0-exp(-(m-1.0)*.35));
}
void main(){
    vec2 uv=texCoord;
    vec3 c=texture(HdrTex,uv).rgb;
    if(Chroma>.00001){vec2 d=(uv-Focus)*Chroma;c.r=texture(HdrTex,uv+d).r;c.b=texture(HdrTex,uv-d).b;}
    vec3 trail=max(texture(HistTex,texCoord).rgb-texture(LightTex,texCoord).rgb,0.0);
    c+=texture(BloomTex,texCoord).rgb*BloomGain+trail*Afterglow+texture(ShaftTex,texCoord).rgb*ShaftTint*ShaftGain;
    c=hot(c);
    if(Rain>.001){
        // inside the rain of knives, gently (2.132.5): a violet air over it (a little of the colour gone, the rest pushed
        // toward violet)
        float l=dot(c,vec3(.2126,.7152,.0722));
        vec3 graded=mix(vec3(l),c,.74)*vec3(.96,.86,1.12)+vec3(.012,0.0,.034);
        c=mix(c,graded,.28*Rain);
        // its edges a little darker and violet
        vec2 q=(texCoord-.5)*vec2(Screen.x/max(Screen.y,1.0),1.0);
        float edge=smoothstep(.42,1.08,length(q));
        c=mix(c,c*.6+vec3(.07,.0,.16)*.35,edge*.26*Rain);
        // thin light falling down the screen like rain: few lines, faint, each column its own speed (slower than 2.132.4's)
        float cols=120.0,col=floor(texCoord.x*cols),hc=hash(vec2(col,7.0));
        if(hc>.62){
            float across=abs(fract(texCoord.x*cols)-.5)*2.0;
            float y=fract(texCoord.y*(.5+.5*hc)+RainTime*(.55+.7*hc)+hc*9.0);
            float streak=smoothstep(.0,.015,y)*(1.0-smoothstep(.015,.26,y))*(1.0-smoothstep(.0,.4,across));
            c+=mix(vec3(.55,.22,1.0),vec3(.95,.92,1.0),pow(max(0.0,1.0-y/.26),8.0))*streak*.18*Rain;
        }
    }
    if(Impact>.001){
        // Flat impact frame: the effects' shapes (the light they added) and the dark silhouettes of the world
        // (bodies, pillars) over a flat ground. First white shapes on deep violet, then dark shapes on white
        // (violet unless the skill asks for its own colours, FxFrame.ImpactLook).
        vec3 added=max(texture(HdrTex,texCoord).rgb-texture(BaseTex,texCoord).rgb,0.0);
        float fx=smoothstep(.10,.18,dot(added,vec3(.2126,.7152,.0722)));
        float sil=1.0-smoothstep(.26,.34,dot(texture(SceneTex,texCoord).rgb,vec3(.2126,.7152,.0722)));
        vec3 a=mix(mix(ImpactDeep,vec3(.0),sil),ImpactShapes,fx);
        vec3 b=mix(mix(ImpactPale,ImpactShade,sil),ImpactHue,fx);
        c=mix(c,mix(a,b,clamp(ImpactMode,0.0,1.0)),clamp(Impact,0.0,1.0));
    }
    fragColor=vec4(c,1.0);
}
