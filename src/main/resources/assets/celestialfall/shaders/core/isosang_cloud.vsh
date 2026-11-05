#version 150
in vec3 Position;
out vec2 screen;
void main(){screen=Position.xy*.5+.5;gl_Position=vec4(Position.xy,0.,1.);}
