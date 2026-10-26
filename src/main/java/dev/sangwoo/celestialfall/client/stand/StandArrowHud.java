package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.gacha.GachaFont;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.stand.ArrowStabPose.Fate;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * What the stand arrow chose, in words, for the one who drove it in (2.70.0), as its choice shows (StandArrowClient):
 * chosen, a small gold "스탠드 각성" over the stand's name, large, gold shading to white with a violet glow, its letters
 * drawing together as it comes in; rejected, "화살에게 선택받지 못했다" in dark red, sinking a little as it fades in.
 * Both fade out after a few seconds (the one rejected sees theirs until they die: the game's death screen has the same
 * words where these would be). Kept clear of the HP art at the top left (on a small screen it reaches the middle). The
 * gacha's lettering (GachaFont); every value a smooth function of the stab's clock.
 */
public final class StandArrowHud {
    private static StandArrowClient.Stab shown;
    private StandArrowHud(){}
    public static void initialize(){HudRenderCallback.EVENT.register(StandArrowHud::render);}
    /** The local player's stab has made its choice. */
    static void chosen(StandArrowClient.Stab s){shown=s;}

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
        if(s.fate==Fate.AWAKEN){
            float a=Ease.smooth(t,.08f,.42f)*(1-Ease.smooth(t,3.6f,4.6f));if(a<=0)return;
            float name=Math.min(h*.085f,w*.07f),small=name*.36f;
            String stand=dev.sangwoo.celestialfall.progression.StandStats.get(s.outcome).korean();
            float y=clear(w,h*.33f,GachaFont.width(stand,name,GachaFont.Weight.HEAVY,.04f)/2,name*1.25f,name*.3f)-(1-Ease.out(t,.08f,.6f))*h*.02f;
            GachaFont.text("스탠드 각성",w/2f,y-name*.78f,small,GachaFont.Weight.BOLD).align(.5f).color(0xffffd889).alpha(a*Ease.smooth(t,.2f,.55f))
                .spacing(.35f).shadow(0x99000000,1.2f,0,small*.08f).draw(d);
            // the name's letters drawing together as it comes in, the glow flaring and settling
            float spacing=.04f+.22f*(1-Ease.out(t,.08f,.7f)),flare=1+1.4f*(float)Math.exp(-Math.max(0,t-.12f)/.35f);
            GachaFont.text(stand,w/2f,y,name,GachaFont.Weight.HEAVY).align(.5f).gradient(0xffffe7a6,0xfffff8ec).alpha(a).spacing(spacing)
                .glow(0xcc9b7cff,name*.32f,flare).shadow(0xb0000000,name*.08f,0,name*.06f).draw(d);
        }else if(s.fate==Fate.DIE){
            // gone as the game's death screen comes up (it has its own words for it, where these would be)
            if(!mc.player.isAlive()||mc.currentScreen instanceof net.minecraft.client.gui.screen.DeathScreen){shown=null;return;}
            float a=Ease.smooth(t,.15f,.45f)*(1-Ease.smooth(t,3.4f,4.4f));if(a<=0)return;
            float size=Math.min(h*.05f,w*.045f),y=clear(w,h*.36f,GachaFont.width("화살에게 선택받지 못했다",size,GachaFont.Weight.BOLD,.12f)/2,size*.8f,size*.4f)+Ease.out(t,.15f,1.2f)*h*.012f;
            GachaFont.text("화살에게 선택받지 못했다",w/2f,y,size,GachaFont.Weight.BOLD).align(.5f).gradient(0xffc0283c,0xff6e0a16).alpha(a)
                .spacing(.12f).glow(0x99300008,size*.4f,1).shadow(0xcc000000,size*.1f,0,size*.07f).draw(d);
        }
    }
}
