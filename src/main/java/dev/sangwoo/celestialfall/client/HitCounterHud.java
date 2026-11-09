package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.combat.HitCounter;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * The local player's "N Hits" (2.108.0, user request: "히트 수 표시가 카게노 H에만 있는데 모든 스탠드에 모든 스킬 평타에 히트
 * 수가 뜨게 바꿔줘"): every character's, as the server counts them (HitCounter), where it was Kageno's alone. Its look is
 * the one Kageno's had from the reference UI: the count flashes red #F62223 on every hit and eases back to white in
 * 0.25 s with a small drift and tilt, and the purple line below it counts the seconds since the combo's first hit; 2.5 s
 * after the last hit it fades out (0.25 s), or 2.5 s after the end of a hold the server tells of (the beatdown keeps it
 * open from its opening punch to its landing, through the cutscene's words: HitCounter.hold).
 * <p>
 * It stands where Kageno's did, the reference's place (the left, 0.31 down, {@link #place}), over the cooldown list's boxes
 * there (user request: "히트 수 표시 쿨타임이랑 겹치게 해도 돼 지금 위치가 너무 안 좋아 쿨타임 표시보다 히트 수 표시가 위에
 * 나오게 해줘"): drawn after the list (its HUD callback is registered after CooldownHud's, CelestialFallClient), where
 * Kageno's was drawn before it and so under its boxes (seen only in his H's cutscene, which draws no list). A first try
 * stood it beside the list's first box; the user found that place bad. In a cutscene it is drawn by CinematicHud, at the
 * same place.
 */
public final class HitCounterHud {
    /** The local player's count (0: none yet). */
    public static int count;
    /** This client's clock (ticks) at the last hit heard, at the combo's first, and until when the server holds it open (HitCounter.hold). */
    static double hitAt=-1e9,comboAt=-1e9,openUntil=-1e9;
    /** Seconds after the last hit it shows, the last {@link #FADE} of them fading. */
    public static final float SHOWN=2.75f,FADE=.25f;
    private HitCounterHud(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(HitCounter.CHANNEL,(mc,h,b,r)->{
            int n=b.readVarInt();int added=b.isReadable()?b.readVarInt():1,hold=b.isReadable()?b.readVarInt():0;
            mc.execute(()->heard(n,added,hold,StandClient.ticks()));
        });
        // after CooldownHud's own: drawn over the cooldown list's boxes
        HudRenderCallback.EVENT.register(HitCounterHud::render);
        ClientPlayConnectionEvents.DISCONNECT.register((h,mc)->mc.execute(HitCounterHud::clear));
    }
    public static void clear(){count=0;hitAt=comboAt=openUntil=-1e9;}

    /**
     * The count {@code n} heard at {@code now}, {@code added} of it this tick (a 2.107 server tells only the count: one),
     * held open {@code hold} ticks on: a combo begins when all of it came now, or when it is lower than the one before;
     * with none added (a hold begun or ended) the count stands as it was.
     */
    static void heard(int n,int added,int hold,double now){
        int c=Math.max(0,Math.min(HitCounter.MOST,n));
        if(added>0||c!=count){
            if(c<=Math.max(1,added)||c<count)comboAt=now;
            hitAt=now;
        }
        count=c;openUntil=now+Math.max(0,hold);
    }
    /** Whether it shows {@code since} seconds after the last hit (or the end of a hold). */
    static boolean shows(int count,float since){return count>0&&since>=0&&since<SHOWN;}
    /** Seconds {@code now} after the later of the last hit and the hold (no later than now): it fades from SHOWN-FADE of them. */
    static float since(double now){return (float)((now-Math.max(hitAt,Math.min(now,openUntil)))/20);}

    /** Draws it: from the HUD (HudRenderCallback), or from a cutscene's overlay (CinematicHud) while the HUD is not drawn. */
    public static void render(DrawContext draw,float delta){
        var mc=MinecraftClient.getInstance();if(mc.player==null||mc.world==null||!MapClient.enabled()||mc.options.hudHidden)return;
        double now=StandClient.ticks()+delta;
        // it shows while held open, and fades after; its red flash and pop go with the last hit alone
        float since=since(now),flash=(float)((now-hitAt)/20);
        if(!shows(count,since))return;
        int width=draw.getScaledWindowWidth(),height=draw.getScaledWindowHeight();
        double[] at=place(width,height);
        String text=count+(count==1?" Hit":" Hits");
        float scale=Math.max(1.2f,height*.0315f/7f),alpha=1-Ease.smooth(since,SHOWN-FADE,SHOWN);int a=Math.max(8,(int)(alpha*255));
        float k=Math.max(0,Math.min(1,flash/.25f));int r=(int)(246+(248-246)*k),g=(int)(34+(251-34)*k*k*.6f+(251-34)*k*.4f),b=(int)(35+(249-35)*k*k*.6f+(249-35)*k*.4f);
        float pop=1-Ease.smooth(flash,0,.37f);
        var mat=draw.getMatrices();mat.push();mat.translate((float)at[0]-pop*width*.0037f,(float)at[1],0);
        mat.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(-3*pop));mat.scale(scale,scale,1);
        draw.drawText(mc.textRenderer,text,0,-4,(a<<24)|(r<<16)|(Math.min(255,g)<<8)|Math.min(255,b),true);mat.pop();
        float combo=(float)((now-comboAt)/20);
        mat.push();mat.translate((float)at[0],(float)at[1]+scale*9,0);mat.scale(scale*.55f,scale*.55f,1);
        draw.drawText(mc.textRenderer,mc.player.getName().getString()+" : "+String.format(java.util.Locale.ROOT,"%.2f",Math.max(0,combo)),0,0,(a<<24)|0x9156e0,true);mat.pop();
    }
    /** Where the count's line stands (its left, the middle of its height) on a {@code width} by {@code height} screen: the reference's place. */
    static double[] place(int width,int height){return new double[]{width*.042,height*.31};}
}
