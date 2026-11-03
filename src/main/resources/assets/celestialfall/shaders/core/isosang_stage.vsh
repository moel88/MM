#version 150
// Lee So-sang's G's own space (2.83.0): its sky, its ground and the giant sword in it. Position is world-aligned,
// relative to the camera (the sky's is the way it looks).
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec3 pos;
out vec2 uv;
out vec4 tint;
void main(){gl_Position=ProjMat*ModelViewMat*vec4(Position,1.0);pos=Position;uv=UV0;tint=Color;}
