#version 150
uniform sampler2D SceneTex;
uniform float Amount;
in vec2 texCoord;
out vec4 fragColor;
void main(){
    vec4 scene=texture(SceneTex,texCoord);
    float gray=dot(scene.rgb,vec3(.299,.587,.114));
    fragColor=vec4(mix(scene.rgb,vec3(gray),clamp(Amount,0.,1.)),scene.a);
}
