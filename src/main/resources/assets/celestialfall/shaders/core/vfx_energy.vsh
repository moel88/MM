#version 150
// Botand VFX: unit meshes placed by ModelViewMat (static GPU meshes) or camera-relative sprites.
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec2 uv;
out vec4 tint;
out vec3 viewPos;
out vec3 viewNrm;
out vec3 local;
void main(){
    vec4 view=ModelViewMat*vec4(Position,1.0);
    gl_Position=ProjMat*view;
    uv=UV0;tint=Color;viewPos=view.xyz;local=Position;
    // On the unit cylinder the radial direction is the surface normal (the xz scale is uniform).
    viewNrm=mat3(ModelViewMat)*vec3(Position.x,0.0,Position.z);
}
