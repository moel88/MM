package dev.sangwoo.celestialfall.client.samurai;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.CelestialFall;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * The samurai's G's frames drawn flat over the screen (2.143.0, its two's own, in the GUI's projection after the world):
 * the ink strokes of the dash and the burst as it arrives, the flash's line on black, its ink washed over the black
 * bodies on white, the torn line through the white bodies on black, the brush circle with the character for death, and
 * the grey and the white the world comes back through. The pictures are drawn by tools/samurai/make_ink.py (1280 by 720:
 * shown filling the screen, cropped at the sides or top and bottom where it is not that shape).
 */
public final class SamuraiInk {
    private SamuraiInk(){}
    static final int W=1280,H=720;
    private static Identifier picture(String name){return CelestialFall.id("textures/samurai/"+name+".png");}
    static final Identifier DASH_A=picture("dash_a"),DASH_B=picture("dash_b"),BURST=picture("burst"),CUT_LINE=picture("cut_line"),WASH=picture("wash"),SHI=picture("shi");
    /** How long the white stays over the world as it comes back (seconds). */
    static final float WHITE_OUT=.14f;

    /** Draws what the cutscene's part has over this client's frame now (nothing outside its cutscene, but the white fading off as the world comes back). */
    public static void render(float delta){
        var mc=MinecraftClient.getInstance();
        int w=mc.getWindow().getScaledWidth(),h=mc.getWindow().getScaledHeight();
        var d=SamuraiClient.cinematic();
        if(d==null){
            float white=whiteOut(delta);
            if(white>.003f){
                var c=new DrawContext(mc,mc.getBufferBuilders().getEntityVertexConsumers());
                c.fill(0,0,w,h,Math.round(white*255)<<24|0xffffff);c.draw();
            }
            return;
        }
        var part=SamuraiScene.part(d.sec(delta));
        var c=new DrawContext(mc,mc.getBufferBuilders().getEntityVertexConsumers());
        switch(part){
            case DASH_WHITE,OUT_WHITE->c.fill(0,0,w,h,0xffffffff);
            case DASH_INK_A->cover(c,DASH_A,w,h,0,d.side<0);
            case DASH_INK_B->cover(c,DASH_B,w,h,0,d.side<0);
            case DASH_BURST->cover(c,BURST,w,h,0,d.side<0);
            case CUT_LINE->{
                c.fill(0,0,w,h,0xff000000);
                // one thin line across the black, a little off level
                var m=c.getMatrices();m.push();m.translate(w*.5f,h*.5f,0);m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-7.5f*d.side));
                float half=Math.max(1,h*.0035f);
                m.scale(1,half,1);c.fill(-w,-1,w,1,0xffffffff);m.pop();
            }
            case CUT_WASH->cover(c,WASH,w,h,0,d.side<0);
            case CUT_BODIES->cover(c,CUT_LINE,w,h,.058f,false);
            case CUT_SIGN->cover(c,SHI,w,h,0,false);
            case CUT_BLACK->c.fill(0,0,w,h,0xff000000);
            case OUT_GREY->c.fill(0,0,w,h,0xffb8b8b8);
            default->{}
        }
        c.draw();
    }
    /** How white the frame still is as the world comes back, for one of some G's two (0 when none). */
    static float whiteOut(float delta){
        var mc=MinecraftClient.getInstance();if(mc.player==null)return 0;
        float white=0;
        for(var d:SamuraiClient.all()){
            if(!d.has(mc.player)||!d.inScene()||d.stopped())continue;
            float s=d.sec(delta)-SamuraiScene.WORLD;
            if(s>=0&&s<WHITE_OUT){float u=1-s/WHITE_OUT;white=Math.max(white,u*u);}
        }
        return white;
    }
    /** A picture over the whole screen, keeping its shape (cropped where the screen's is another), {@code down} of the screen's height lower, mirrored left for right if asked. */
    private static void cover(DrawContext c,Identifier picture,int w,int h,float down,boolean mirror){
        float scale=Math.max(w/(float)W,h/(float)H);
        float pw=W*scale,ph=H*scale;
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        var m=c.getMatrices();m.push();
        m.translate(w*.5f,h*.5f+down*h,0);m.scale(mirror?-pw/W:pw/W,ph/H,1);
        if(mirror)RenderSystem.disableCull();
        c.drawTexture(picture,-W/2,-H/2,0,0,W,H,W,H);
        c.draw();
        if(mirror)RenderSystem.enableCull();
        m.pop();
    }
}
