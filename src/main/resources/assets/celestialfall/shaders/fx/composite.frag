#version 150
// The effect layer back onto the screen: bloom, afterglow trails and light shafts added, light above white
// turned white-hot instead of clipping to a flat colour, and the finisher's impact frame.
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
in vec2 texCoord;
out vec4 fragColor;
vec3 hot(vec3 c){
    c=max(c,0.0);float m=max(c.r,max(c.g,c.b));
    if(m<=1.0)return c;
    // Light a little over white keeps its colour; only light far over it burns white.
    return mix(c/m,vec3(1.0),1.0-exp(-(m-1.0)*.35));
}
void main(){
    vec3 c=texture(HdrTex,texCoord).rgb;
    if(Chroma>.00001){vec2 d=(texCoord-Focus)*Chroma;c.r=texture(HdrTex,texCoord+d).r;c.b=texture(HdrTex,texCoord-d).b;}
    vec3 trail=max(texture(HistTex,texCoord).rgb-texture(LightTex,texCoord).rgb,0.0);
    c+=texture(BloomTex,texCoord).rgb*BloomGain+trail*Afterglow+texture(ShaftTex,texCoord).rgb*ShaftTint*ShaftGain;
    c=hot(c);
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
