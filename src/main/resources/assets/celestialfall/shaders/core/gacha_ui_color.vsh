#version 150
// The gacha screens' coloured shapes (2.40.0): Minecraft's position_color (gacha_ui_color.json blends normally,
// gacha_ui_color_add.json adds light).
in vec3 Position;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec4 vertexColor;
void main() {
    gl_Position=ProjMat*ModelViewMat*vec4(Position,1.0);
    vertexColor=Color;
}
