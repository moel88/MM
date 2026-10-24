#version 150
// The aura's bodies at half size: red the stands, green their users (how solid each is, from the masks' alpha).
uniform sampler2D MaskA;
uniform sampler2D MaskB;
uniform vec2 Texel;
in vec2 texCoord;
out vec4 fragColor;
float a(sampler2D s){
    vec2 o=Texel*0.5;
    return (texture(s,texCoord+vec2(-o.x,-o.y)).a+texture(s,texCoord+vec2(o.x,-o.y)).a
           +texture(s,texCoord+vec2(-o.x,o.y)).a+texture(s,texCoord+vec2(o.x,o.y)).a)*0.25;
}
void main(){
    fragColor=vec4(a(MaskA),a(MaskB),0.0,1.0);
}
