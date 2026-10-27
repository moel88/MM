package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.stand.StandHolder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Star Platinum called out and back (2.72.0, user request: "하나는 스타플래티나 q로 꺼낼때 나는 소리고 하나는 q로 다시
 * 넣을때 나는 소리야", the user's two recordings: "Stand Summon (Voiceline)", the call "Star Platinum!", and "Stand
 * Disappear"). As a stand starts to come out at its user's call (Q), every client that sees it come out hears the call;
 * as it starts to go back at their call, the sound of it going. Each starts as the stand does on this client
 * (StandPresence), the call's "s" with its first frame, its "Taa" as it bursts into its pose. A stand that comes out or
 * goes back otherwise (the arrow's awakening, its user dead, asleep, off the map, a beatdown's cutscene taking it in and
 * out) is silent: the server says which were called (StandHolder.botand$standCalled). Called again before the last
 * sound is over, that one fades out over a few ticks (StandAttack.Shout) as the next starts. Loud for its user and
 * those near, fading out to CARRY blocks (not placed in the world: as the stand's other sounds). The cut recordings:
 * tools/stand/cut_summon.py.
 */
public final class StandCall {
    static final String SUMMON="star_platinum_summon",DISMISS="star_platinum_dismiss";
    static final float VOLUME=1f,CARRY=40;
    /** Each user's last call heard, while it may still be sounding. */
    private static final Map<UUID,StandAttack.Shout> PLAYING=new HashMap<>();
    private StandCall(){}

    /**
     * The sound for a stand starting to come out ({@code out}) or go back, its user's tracked value saying whether it
     * is out ({@code isOut}) and whether its user called it so ({@code called}): the call or the going, or none (it
     * did not come out or go back at its user's call, or this client has its own reason, such as its user asleep).
     */
    static String cue(boolean out,boolean isOut,boolean called){
        if(!called||isOut!=out)return null;
        return out?SUMMON:DISMISS;
    }

    /** This user's stand starts to come out ({@code out}) or go back now, as this client draws it. */
    static void changed(MinecraftClient mc,PlayerEntity p,boolean out){
        var last=PLAYING.remove(p.getUuid());
        if(last!=null&&!last.isDone())last.fade();
        var holder=(StandHolder)p;
        String sound=cue(out,holder.botand$isStandOut(),holder.botand$standCalled());
        if(sound==null)return;
        float volume=volume(mc,p);if(volume<=.01f)return;
        var s=new StandAttack.Shout(CelestialFall.id(sound),volume);
        mc.getSoundManager().play(s);
        PLAYING.put(p.getUuid(),s);
    }

    /** How loud a stand's call is here (0 if too far), as its other sounds. */
    private static float volume(MinecraftClient mc,PlayerEntity user){
        if(mc.player==null)return 0;
        double far=user==mc.player?0:user.getPos().distanceTo(mc.player.getPos());
        return (float)(CelestialFall.CONFIG.effectVolume*VOLUME*Math.max(0,1-far/CARRY));
    }

    /** A world left behind: whatever is still sounding stops. */
    static void clear(MinecraftClient mc){
        for(var s:PLAYING.values())if(!s.isDone())mc.getSoundManager().stop(s);
        PLAYING.clear();
    }

    /** This user's last call heard while it is still sounding (for the capture tools' logs; null if none). */
    public static net.minecraft.client.sound.SoundInstance playing(UUID user){
        var s=PLAYING.get(user);return s==null||!MinecraftClient.getInstance().getSoundManager().isPlaying(s)?null:s;
    }
}
