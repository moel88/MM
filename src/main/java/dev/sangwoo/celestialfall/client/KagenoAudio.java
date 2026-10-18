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
        // Start offsets measured by cross-correlating each recording with the 60 fps review's audio
        // (spectral flux, r = 0.64-0.93): the sounds belong this long after the first motion frame.
        // E's is the user's own recording, trimmed to start with the cast: its first sound falls on the neon flare,
        // the barrage's end on the pull-back and the blast on the finisher. T held's was re-cut to its remade timeline
        // (tools/kageno/recut_rush.py) and starts with the cast too.
        int lead=switch(c.skill()){case M1->c.combo()==1?8:c.combo()==4?10:9;case E->0;case R->7;case T->12;case RUSH->0;case Y->12;case H->3;default->0;};
        int delay=Math.max(0,lead-(int)c.age(0));
        PLAYING.put(c.id(),sound);if(delay>0)mc.getSoundManager().play(sound,delay);else mc.getSoundManager().play(sound);
    }
    static void stop(UUID id){var sound=PLAYING.remove(id);if(sound!=null)MinecraftClient.getInstance().getSoundManager().stop(sound);}
    /** A cast that ran its full length lets its (delayed) recording finish instead of cutting it. */
    static void finish(UUID id){PLAYING.remove(id);}
    static void retain(Set<UUID> ids){for(var id:new ArrayList<>(PLAYING.keySet()))if(!ids.contains(id))finish(id);}
    static void clear(){for(var id:new ArrayList<>(PLAYING.keySet()))stop(id);}
}
