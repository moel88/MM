#version 150
// The world under the effect layer: refracted by the distortion the effects wrote, lit by the effects' lights
// (positions rebuilt from the depth buffer, normals from neighbouring depths) and marked by ground decals.
// baseOut is the world as the effects find it; hdrOut also carries the decals' glow, so only that glow and
// the effects themselves reach the bloom (bloom = effect layer minus base).
uniform sampler2D SceneTex;
uniform sampler2D DistortTex;
uniform sampler2D DepthTex;
uniform sampler2D NoiseTex;
uniform vec2 Texel;
uniform mat4 InvViewProj;
uniform vec3 Anchor;
uniform float LightGain;
uniform int LightCount;
uniform vec4 LightPos[12];
uniform vec4 LightColor[12];
uniform int DecalCount;
uniform vec4 DecalA[10];
uniform vec4 DecalB[10];
uniform vec4 DecalC[10];
in vec2 texCoord;
out vec4 baseOut;
out vec4 hdrOut;

vec3 rel(vec2 uv,float d){vec4 p=InvViewProj*vec4(uv*2.0-1.0,d*2.0-1.0,1.0);return p.xyz/p.w;}
vec3 at(vec2 uv){return rel(uv,texture(DepthTex,uv).r);}

void main(){
    vec2 off=texture(DistortTex,texCoord).rg;
    vec3 scene=texture(SceneTex,clamp(texCoord+off,Texel,1.0-Texel)).rgb;
    vec3 emissive=vec3(0.0);
    float d=texture(DepthTex,texCoord).r;
    if(d<.99999&&(LightCount>0||DecalCount>0)){
        vec3 P=rel(texCoord,d);
        vec3 px1=at(texCoord+vec2(Texel.x,0.0)),px0=at(texCoord-vec2(Texel.x,0.0));
        vec3 py1=at(texCoord+vec2(0.0,Texel.y)),py0=at(texCoord-vec2(0.0,Texel.y));
        vec3 dx=length(px1-P)<length(P-px0)?px1-P:P-px0;
        vec3 dy=length(py1-P)<length(P-py0)?py1-P:P-py0;
        vec3 N=cross(dx,dy);
        N=dot(N,N)>1e-12?normalize(N):vec3(0.0,1.0,0.0);
        if(dot(N,P)>0.0)N=-N;
        vec2 world=P.xz+Anchor.xz;
        for(int i=0;i<10;i++){
            if(i>=DecalCount)break;
            vec4 A=DecalA[i],B=DecalB[i],C=DecalC[i];
            vec3 local=P-A.xyz;
            if(abs(local.y)>1.6||N.y<.55)continue;
            vec3 side=normalize(cross(vec3(0.0,1.0,0.0),B.xyz));
            float x=dot(local,B.xyz)/A.w,y=dot(local,side)/B.w;
            if(abs(x)>1.2||abs(y)>3.5)continue;
            float seed=C.z;
            // A ragged gash along the decal: its centre line wanders, it is widest in the middle and torn at the edges.
            float y0=(texture(NoiseTex,vec2(x*.21+seed*.131,seed*.071)).r-.5)*.8;
            float wx=pow(max(1.0-x*x,0.0),.55);
            float n1=texture(NoiseTex,world*.13+seed*.37).r,n2=texture(NoiseTex,world*.41+seed*.11).g,ridge=texture(NoiseTex,world*.09+seed*.23).b;
            float dy=abs(y-y0)/max(wx,.03)+(n2-.5)*.34;
            float alpha=C.y*(1.0-smoothstep(.92,1.18,abs(x)));
            float crevice=1.0-smoothstep(.17,.23,dy);
            float burn=1.0-smoothstep(.4,1.5,dy+(n1-.5)*.6);
            float crack=smoothstep(.84,.95,ridge)*(1.0-smoothstep(.9,2.6,dy))*step(.24,dy);
            scene*=1.0-alpha*(.45*burn+.45*crack);
            scene=mix(scene,vec3(.03,.003,.06),crevice*alpha*.94);
            float heat=C.x;
            float core=(1.0-smoothstep(.0,.2,dy))*crevice;
            vec3 hot=mix(vec3(.5,.07,1.0),vec3(1.0,.5,.96),heat);
            emissive+=alpha*heat*(hot*(core*3.0+crevice*1.1)+vec3(.85,.22,1.0)*crack*1.6+vec3(.42,.06,.86)*burn*.4*heat);
        }
        vec3 light=vec3(0.0);
        for(int i=0;i<12;i++){
            if(i>=LightCount)break;
            vec3 L=LightPos[i].xyz-P;float dist=length(L),r=LightPos[i].w;
            float f=clamp(1.0-dist*dist/(r*r),0.0,1.0);f*=f;
            float ndl=clamp(dot(N,L/max(dist,1e-3))*.8+.2,0.0,1.0);
            light+=LightColor[i].rgb*f*ndl/(1.0+dist*dist*.3);
        }
        scene+=(scene*.9+.05)*light*LightGain;
    }
    baseOut=vec4(scene,1.0);
    hdrOut=vec4(scene+emissive,1.0);
}
