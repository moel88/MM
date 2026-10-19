#version 150
// The gacha's pull scenes (2.39.0): the title cinema's vertex stage, plus motes of light that drift up through the dark
// and (2.40.0) sparks circling the hourglass.
in vec3 Position;
in vec3 Normal;
in vec2 UV;
in vec4 Color;
in float Bone;
in float Material;
uniform mat4 ViewProjection;
uniform mat4 LightMatrix;
uniform mat4 Bones[32];
uniform float Time;
uniform vec3 Camera;
uniform vec3 SparkCenter;
uniform float SparkRadius;
uniform float SparkHeight;
out vec3 world;
out vec3 normal;
out vec2 uv;
out vec4 tint;
out vec4 shadowCoord;
out float material;
out float rigged;
void main() {
    mat4 skin=mat4(1.0);
    if(Bone>=0.0) skin=Bones[int(Bone+.1)];
    vec4 p=skin*vec4(Position,1.0);
    vec3 n=normalize(mat3(skin)*Normal);
    if(Material>5.5 && Material<6.5) {
        // a mote: Normal holds its rise speed, the height it rises through and where that starts; UV is its corner.
        // Only these glints of light face the eye (CLAUDE.md); they are a few millimetres across.
        float h=fract(sin(dot(Position.xz,vec2(12.9898,78.233)))*43758.5453);
        vec3 c=vec3(Position.x+.04*sin(Time*.7+Position.z*3.1),Normal.z+mod(Position.y+Time*Normal.x,Normal.y),Position.z+.04*cos(Time*.6+Position.x*2.7));
        vec3 f=normalize(Camera-c),r=normalize(cross(vec3(0,1,0),f)),u=cross(f,r);
        float size=.005+.009*h;
        p=vec4(c+(r*(UV.x-.5)+u*(UV.y-.5))*size*2.,1.);
        n=f;
    }
    if(Material>10.5 && Material<11.5) {
        // a spark circling the held hourglass (2.40.0): Position holds its angle, its share of the radius and where in
        // its rise it starts; Normal its turning speed, rise speed and size. Light, so it faces the eye like the motes.
        float a=Position.x+Time*Normal.x;
        float r=SparkRadius*Position.y*(1.+.18*sin(Time*1.3+Position.x*3.));
        float y=SparkHeight*(fract(Position.z+Time*Normal.y)-.5);
        vec3 c=SparkCenter+vec3(cos(a)*r,y,sin(a)*r);
        vec3 f=normalize(Camera-c),rr=normalize(cross(vec3(0,1,0),f)),u=cross(f,rr);
        p=vec4(c+(rr*(UV.x-.5)+u*(UV.y-.5))*Normal.z*2.,1.);
        n=f;
    }
    rigged=Bone>=0.0&&Bone<6.5?1.0:0.0;
    world=p.xyz;normal=n;uv=UV;tint=Color;material=Material;
    shadowCoord=LightMatrix*p;
    gl_Position=ViewProjection*p;
}
