package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.AtomicTimeline;
import dev.sangwoo.celestialfall.CelestialFall;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * G's in-game UI: the red "IN COMBAT, ITEMS AND / STAND STORAGE DISABLED" notice shown to the
 * caster from the first skill frame, plus a software version of the screen grade for when the
 * post processor is unavailable.
 */
public final class AtomicHud {
    private static final Text LINE1=Text.literal("IN COMBAT, ITEMS AND"),LINE2=Text.literal("STAND STORAGE DISABLED");
    private AtomicHud(){}
    public static void render(DrawContext context,float delta){
        var mc=MinecraftClient.getInstance();if(mc.world==null||mc.player==null||mc.options.hudHidden)return;
        int w=context.getScaledWindowWidth(),h=context.getScaledWindowHeight();
        if(!(CelestialFall.CONFIG.postProcessing&&AnimePostProcessor.available())&&!StandClient.monochrome())fallback(context,mc,delta,w,h);
        for(var cast:AtomicClient.CASTS.values()){
            if(!cast.caster.equals(mc.player.getUuid())||cast.age(delta)>=AtomicTimeline.END)continue;
            notice(context,mc,w,h);break;
        }
    }
    /** Two centred lines in the upper left (10.8–35.5 % of the width, 17.6–24.3 % of the height). */
    private static void notice(DrawContext context,MinecraftClient mc,int w,int h){
        float scale=Math.max(.5f,h*.026f/7f);var tr=mc.textRenderer;
        float cx=w*.2315f,top=h*.176f,lineGap=h*.039f;
        var m=context.getMatrices();
        for(int line=0;line<2;line++){
            Text text=line==0?LINE1:LINE2;
            m.push();m.translate(cx,top+line*lineGap,0);m.scale(scale,scale,1);
            int x=-tr.getWidth(text)/2;
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)if(dx!=0||dy!=0)context.drawText(tr,text,x+dx,dy,0xff1e050a,false);
            context.drawText(tr,text,x,0,0xffd00409,false);
            m.pop();
        }
    }
    private static void fallback(DrawContext context,MinecraftClient mc,float delta,int w,int h){
        var g=AtomicImmersion.at(mc.gameRenderer.getCamera().getPos(),delta).grade();
        if(g.tint()>0)context.fill(0,0,w,h,((int)(g.tint()*60)<<24)|0xa070d0);
        if(g.frame()>=2&&CelestialFall.CONFIG.screenFlash)context.fill(0,0,w,h,g.frame()==3||g.frame()==4?0xff010101:0xfffdfdfa);
        else if(g.mix()>0){
            int lo=g.lo(),hi=g.hi();
            int c=((((lo>>16)&255)+((hi>>16)&255))/2<<16)|((((lo>>8)&255)+((hi>>8)&255))/2<<8)|(((lo&255)+(hi&255))/2);
            context.fill(0,0,w,h,((int)(g.mix()*235)<<24)|c);
        }
    }
}
