#version 150
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
    if(Material>4.5 && Material<5.5) p.xz+=vec2(.013,.009)*sin(Time*.65+p.z*.74+p.x*.3)*smoothstep(2.,4.,p.y);
    if(Material>5.5 && Material<6.5) {
        p.x+=.12*sin(Time*.5+Position.z);
        p.y=.3+mod(Position.y-Time*.09,3.8);
    }
    rigged=Bone>=0.0?1.0:0.0;
    world=p.xyz;normal=normalize(mat3(skin)*Normal);uv=UV;tint=Color;material=Material;
    shadowCoord=LightMatrix*p;
    gl_Position=ViewProjection*p;
}
