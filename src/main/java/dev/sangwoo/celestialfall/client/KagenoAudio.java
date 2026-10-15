package dev.sangwoo.celestialfall.client;
import dev.sangwoo.celestialfall.CelestialFall;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.*;
import net.minecraft.sound.SoundCategory;
import java.util.*;
import static dev.sangwoo.celestialfall.stand.KagenoRules.*;

/** Original effects; H intentionally retains its original music. State refreshes never replay cues; Q owns a separate music instance. */
final class KagenoAudio {
    private static final Map<UUID,SoundInstance> PLAYING=new HashMap<>();
    private KagenoAudio(){}
    static void begin(StandClient.Cast c){
        var mc=MinecraftClient.getInstance();if(mc.player==null||c.age(0)>3||PLAYING.containsKey(c.id()))return;
        String name=switch(c.skill()){case M1->"m1_"+c.combo();case E->"e";case R->"r";case T->"t";case RUSH->"rush";case Y->"y";case H->"h";default->null;};if(name==null)return;
        float volume=(float)(CelestialFall.CONFIG.effectVolume*(c.includes(mc.player.getUuid())?1:Math.max(0,1-mc.player.getPos().distanceTo(c.origin())/40)));
        var sound=new PositionedSoundInstance(CelestialFall.id("kageno_"+name),SoundCategory.PLAYERS,volume,1,net.minecraft.util.math.random.Random.create(),false,0,SoundInstance.AttenuationType.NONE,0,0,0,true);
        PLAYING.put(c.id(),sound);mc.getSoundManager().play(sound);
    }
    static void stop(UUID id){var sound=PLAYING.remove(id);if(sound!=null)MinecraftClient.getInstance().getSoundManager().stop(sound);}
    static void retain(Set<UUID> ids){for(var id:new ArrayList<>(PLAYING.keySet()))if(!ids.contains(id))stop(id);}
    static void clear(){for(var id:new ArrayList<>(PLAYING.keySet()))stop(id);}
}
