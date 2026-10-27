package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.gacha.GachaFont;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.stand.ArrowStabPose.Fate;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * What the stand arrow chose, in words, for the one who drove it in, as its choice shows (StandArrowClient): only a
 * rejection, "화살에게 선택받지 못했다" in dark red, sinking a little as it fades in; it fades out after a few seconds
 * (the one rejected sees it until they die: the game's death screen has the same words where these would be). Chosen,
 * nothing: 2.70.0 had a small gold "스탠드 각성" over the stand's name, large, and 2.71.2 took them away (user request:
 * "스탠드 각성 스타플래티나 이거 안 뜨게 해줘"; the stand bursting out of them says it). Kept clear of the HP art at the
 * top left (on a small screen it reaches the middle). The gacha's lettering (GachaFont); every value a smooth function of
 * the stab's clock.
 */
public final class StandArrowHud {
    private static StandArrowClient.Stab shown;
    private StandArrowHud(){}
    public static void initialize(){HudRenderCallback.EVENT.register(StandArrowHud::render);}
    /** The local player's stab has made its choice (words only for a rejection). */
    static void chosen(StandArrowClient.Stab s){if(s.fate==Fate.DIE)shown=s;}

    /**
     * Where words meant {@code at} (their baseline's height, {@code half} wide either side of the middle, reaching
     * {@code above} over it and {@code below} under it) go: there, or, where the HP art at the top left reaches them (a
     * small screen: the art takes up to 44% of its width), just below the art (as the danger banner, DangerBanner).
     */
    static float clear(int w,float at,float half,float above,float below){
        if(!dev.sangwoo.celestialfall.client.BotandHud.enabled())return at;
        double right=dev.sangwoo.celestialfall.client.BotandHudLayout.right(w)+4,bottom=dev.sangwoo.celestialfall.client.BotandHudLayout.bottom(w)+6;
        if(w/2f-half>=right||at-above>=bottom)return at;
        return (float)(bottom+above);
    }

    static void render(DrawContext d,float delta){
        var mc=MinecraftClient.getInstance();
        var s=shown;
        if(s==null)return;
        if(!MapClient.enabled()||mc.player==null||!s.user.equals(mc.player.getUuid())||s.cutShort()){shown=null;return;}
        if(mc.options.hudHidden)return;
        float t=s.age(delta)-s.shows();
        if(t<0)return;
        if(t>5){shown=null;return;}
        int w=d.getScaledWindowWidth(),h=d.getScaledWindowHeight();
        // gone as the game's death screen comes up (it has its own words for it, where these would be)
        if(!mc.player.isAlive()||mc.currentScreen instanceof net.minecraft.client.gui.screen.DeathScreen){shown=null;return;}
        float a=Ease.smooth(t,.15f,.45f)*(1-Ease.smooth(t,3.4f,4.4f));if(a<=0)return;
        float size=Math.min(h*.05f,w*.045f),y=clear(w,h*.36f,GachaFont.width("화살에게 선택받지 못했다",size,GachaFont.Weight.BOLD,.12f)/2,size*.8f,size*.4f)+Ease.out(t,.15f,1.2f)*h*.012f;
        GachaFont.text("화살에게 선택받지 못했다",w/2f,y,size,GachaFont.Weight.BOLD).align(.5f).gradient(0xffc0283c,0xff6e0a16).alpha(a)
            .spacing(.12f).glow(0x99300008,size*.4f,1).shadow(0xcc000000,size*.1f,0,size*.07f).draw(d);
    }
}
