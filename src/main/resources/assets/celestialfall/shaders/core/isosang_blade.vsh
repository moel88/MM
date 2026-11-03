#version 150
// Lee So-sang's G's giant sword (2.83.0): its blade, lit as it is drawn. Position world-aligned, relative to the camera.
in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec3 pos;
out vec2 uv;
out vec4 tint;
out vec3 nrm;
void main(){gl_Position=ProjMat*ModelViewMat*vec4(Position,1.0);pos=Position;uv=UV0;tint=Color;nrm=Normal;}
