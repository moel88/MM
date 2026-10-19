#version 150
uniform vec4 ColorModulator;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    if(vertexColor.a<=0.)discard;
    fragColor=vertexColor*ColorModulator;
}
