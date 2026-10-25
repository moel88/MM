package dev.sangwoo.celestialfall.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

/**
 * A skill with a cutscene flashes its user's body sky blue as it is used (2.64.0, user request: "컷씬 나오는 스킬
 * 사용할때는 시전자의 몸이 0.2초 동안 하늘색으로 변해"): Star Platinum's beatdown (G) and Kageno's H. For DURATION
 * seconds from then their body is drawn over in sky blue (CutsceneFlashMixin: the body again, its skin's outer layer
 * left out, in a flat sky blue that no light darkens), in over two frames and out over the last few.
 */
public final class CutsceneFlash {
    /** How long it lasts (seconds), how quickly it comes in and from when it goes out. */
    public static final float DURATION=.2f,IN=.035f,OUT=.14f;
    /** The sky blue (the menus' and the gacha's), and how strongly it covers the body at its fullest. */
    public static final float RED=.5f,GREEN=.86f,BLUE=1f,COVER=.88f;
    /** A plain white texture: what the flash is drawn with. */
    public static final Identifier WHITE=dev.sangwoo.celestialfall.CelestialFall.id("textures/misc/white.png");
    private static final Map<UUID,Long> STARTED=new HashMap<>();
    private static long ticks;
    private CutsceneFlash(){}
    public static void initialize(){
        ClientTickEvents.START_CLIENT_TICK.register(mc->{
            if(mc.isPaused())return;ticks++;
            STARTED.values().removeIf(t->ticks-t>10);
        });
    }
    /** This player used a skill with a cutscene now. */
    public static void flash(UUID player){STARTED.put(player,ticks);}
    /** How strongly this one's body is flashed this frame (0 none, 1 fully sky blue). */
    public static float strength(Entity e,float delta){
        var at=STARTED.get(e.getUuid());if(at==null||!MapClient.enabled())return 0;
        return strength((ticks-at+Math.max(0,Math.min(1,delta)))/20f);
    }
    /** How strongly it shows {@code t} seconds after the skill was used. */
    public static float strength(float t){
        if(t<0||t>=DURATION)return 0;
        float in=smooth(t/IN),out=1-smooth((t-OUT)/(DURATION-OUT));
        return in*out;
    }
    static float smooth(float v){v=v<0?0:v>1?1:v;return v*v*(3-2*v);}
    /** For the capture tools: how strongly the local player is flashed now. */
    public static float local(float delta){var p=MinecraftClient.getInstance().player;return p==null?0:strength(p,delta);}
}
