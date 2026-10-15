#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec2 uv;
out vec4 tint;
out float viewDepth;
void main(){
    vec4 view=ModelViewMat*vec4(Position,1.0);
    gl_Position=ProjMat*view;uv=UV0;tint=Color;viewDepth=length(view.xyz);
}
