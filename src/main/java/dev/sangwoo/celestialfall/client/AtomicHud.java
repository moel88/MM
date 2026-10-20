package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.AtomicTimeline;
import dev.sangwoo.celestialfall.CelestialFall;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.client.gacha.GachaFont;

/**
 * G's in-game UI: the danger banner ("인컴뱃") shown to the caster from the first skill frame, plus a software version of
 * the screen grade for when the post processor is unavailable.
 */
public final class AtomicHud {
    /**
     * The banner's lines (user request, 2.42.0: centred at the top as in the user's screenshot, its "IN DANGER / Your life
     * is at risk! / (Stand Storage disabled.)" in Korean). The serif face holds only these letters and ASCII: run
     * tools/hud/build_font.py again when they change.
     */
    static final String[] LINES={"위험","목숨이 위태롭습니다!","(스탠드 보관 불가)"};
    /** The fill (lighter at the top), a darker red for the last line, the dark rim and the black drop shadow. */
    private static final int RED_TOP=0xffe4402c,RED=0xffc92a1f,DARK_RED=0xffa82219,RIM=0xff2c0706,SHADOW=0xe6080204;
    /** Ticks the banner takes to come in at the cast and to go at its end. */
    private static final float IN=5,OUT=10;
    private AtomicHud(){}
    public static void render(DrawContext context,float delta){
        var mc=MinecraftClient.getInstance();if(mc.world==null||mc.player==null||mc.options.hudHidden)return;
        int w=context.getScaledWindowWidth(),h=context.getScaledWindowHeight();
        if(!(CelestialFall.CONFIG.postProcessing&&AnimePostProcessor.available())&&!StandClient.monochrome())fallback(context,mc,delta,w,h);
        for(var cast:AtomicClient.CASTS.values()){
            float age=cast.age(delta);
            if(!cast.caster.equals(mc.player.getUuid())||age>=AtomicTimeline.END)continue;
            notice(context,w,h,age);break;
        }
    }
    /**
     * Three lines centred at the top, a bold red serif with a dark rim and a black shadow down and to the right, where the
     * screenshot has them unless the HP art is in the way ({@link DangerBanner}).
     */
    private static void notice(DrawContext context,int w,int h,float age){
        float alpha=smooth(age/IN)*(1-smooth((age-(AtomicTimeline.END-OUT))/OUT));
        if(alpha<=.002f)return;
        float cx=w/2f,widest=0;
        for(int i=0;i<LINES.length;i++)widest=Math.max(widest,GachaFont.width(LINES[i],DangerBanner.SIZE[i]*h,GachaFont.Weight.SERIF));
        var place=DangerBanner.place(w,h,widest,BotandHud.enabled());
        // a one-pixel rim (thicker on big windows) and the rise as it comes in
        float k=(float)MinecraftClient.getInstance().getWindow().getScaleFactor();
        float rim=Math.max(1,Math.round(h*k*.0013f))/k,rise=(1-smooth(age/IN))*h*.01f;
        for(int i=0;i<LINES.length;i++){
            float size=place.size(i,h),y=place.middle(i,h)+rise,drop=size*.18f;
            GachaFont.text(LINES[i],cx+drop,y+drop,size,GachaFont.Weight.SERIF).align(.5f).color(SHADOW).alpha(alpha*.92f).draw(context);
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)if(dx!=0||dy!=0)
                GachaFont.text(LINES[i],cx+dx*rim,y+dy*rim,size,GachaFont.Weight.SERIF).align(.5f).color(RIM).alpha(alpha).draw(context);
            GachaFont.text(LINES[i],cx,y,size,GachaFont.Weight.SERIF).align(.5f).gradient(i==2?RED:RED_TOP,i==2?DARK_RED:RED).alpha(alpha).draw(context);
        }
        RenderSystem.enableDepthTest();
    }
    private static float smooth(float x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
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
