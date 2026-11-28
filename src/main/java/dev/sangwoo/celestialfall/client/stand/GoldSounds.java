package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * Gold Experience's sounds as each client plays them (2.153.0): the user's recordings (its R's shout and its Y's two
 * healings, tools/stand/cut_gold_sounds.py) and the ones laid together from recordings for its T and its H
 * (tools/stand/make_gold_sounds.py). Each skill keeps a cue sheet ({@link Cue}s on its own clock, {@link Run}) that its
 * client ticks: a cue plays on the tick that reaches it (or is let go, heard more than LATE after its time), is hushed
 * when its time comes, and a run cut short fades out what plays and plays no more. As the stands' other sounds they are
 * not placed in the world: loud for those they are for, fading out to CARRY blocks for the rest ({@link #volume}).
 */
final class GoldSounds {
    private GoldSounds(){}
    /** How far off they are heard at all (blocks), loud near: the stands' other sounds'. */
    static final double CARRY=40;
    /** A cue heard this long after its time (seconds) still plays; later it is let go (it would not go with what it is the sound of). */
    static final float LATE=.3f;
    /** Fading out (ticks). */
    static final int FADE=3;
    /** One sound on a sheet: when (seconds on the skill's clock), the sound (in this mod's sounds), how loud, its pitch, and when it is hushed (NaN: it plays out). */
    record Cue(float at,String sound,float volume,float pitch,float until){
        Cue(float at,String sound,float volume){this(at,sound,volume,1,Float.NaN);}
        Cue(float at,String sound,float volume,float pitch){this(at,sound,volume,pitch,Float.NaN);}
    }
    /** A sound of Gold Experience's: not placed in the world, as loud as it is here, faded out when it is hushed or cut short. */
    static final class Sound extends AbstractSoundInstance implements TickableSoundInstance {
        private final float full;private int fading=-1;private boolean done,begun;
        Sound(String id,float volume,float pitch){
            super(CelestialFall.id(id),SoundCategory.PLAYERS,Random.create());
            this.volume=full=volume;this.pitch=pitch;attenuationType=SoundInstance.AttenuationType.NONE;relative=true;x=y=z=0;
        }
        void fade(){if(fading<0)fading=0;}
        boolean fading(){return fading>=0;}
        @Override public boolean canPlay(){return !done&&(fading<0||begun);}
        @Override public boolean isDone(){return done;}
        @Override public void tick(){
            begun=true;
            if(fading<0)return;
            fading++;volume=full*Math.max(0,1-fading/(float)FADE);
            if(fading>=FADE)done=true;
        }
    }
    /** A cue sheet as one skill plays it: which cues have played, and those still playing with when each is hushed. */
    static final class Run {
        final Cue[] cues;final BitSet played=new BitSet();
        private final List<Sound> playing=new ArrayList<>();private final List<Float> until=new ArrayList<>();
        private boolean cut;
        Run(Cue... cues){this.cues=cues;}
        /** A tick at {@code t} seconds on the skill's clock, its sounds {@code volume} loud here (0: out of hearing). */
        void tick(MinecraftClient mc,float t,float volume){
            if(cut)return;
            for(int i=0;i<cues.length;i++){
                if(played.get(i))continue;
                var c=cues[i];
                // (the tick that reaches it plays it: the cues are on whole ticks)
                if(t+.026f<c.at())continue;
                played.set(i);
                if(t-c.at()>LATE||volume<=.01f)continue;
                var s=new Sound(c.sound(),volume*c.volume(),c.pitch());
                mc.getSoundManager().play(s);
                playing.add(s);until.add(c.until());
            }
            for(int i=playing.size()-1;i>=0;i--){
                var s=playing.get(i);
                if(s.isDone()){playing.remove(i);until.remove(i);continue;}
                float u=until.get(i);
                if(!Float.isNaN(u)&&t+.026f>=u)s.fade();
            }
        }
        /** Cut short: what plays fades out, and no more comes. */
        void cut(){cut=true;for(var s:playing)s.fade();}
        /** Whether it was cut short. */
        boolean wasCut(){return cut;}
        /** Hushes now what is playing of the cues whose sound is {@code id} (the rest go on). */
        void hush(String id){for(var s:playing)if(s.getId().getPath().equals(id))s.fade();}
        /** Gone (the world left): stopped at once. */
        void stop(MinecraftClient mc){
            for(var s:playing)if(!s.isDone())mc.getSoundManager().stop(s);
            playing.clear();until.clear();cut=true;
        }
        /** For the checks: how many of its cues have played. */
        int heard(){return played.cardinality();}
    }
    /**
     * How loud a sound is here (before the game's own volume): all of it if it is {@code ours} (this client's player is
     * one of those it is for), fading out to CARRY blocks from the nearest of {@code from} for anyone else.
     */
    static float volume(MinecraftClient mc,boolean ours,Vec3d... from){
        if(mc.player==null)return 0;
        double far=Double.MAX_VALUE;
        if(ours)far=0;
        else for(var at:from)if(at!=null)far=Math.min(far,at.distanceTo(mc.player.getPos()));
        if(far==Double.MAX_VALUE)return 0;
        return (float)(CelestialFall.CONFIG.effectVolume*Math.max(0,1-far/CARRY));
    }
    /** One sound now, off any sheet (a blow turned back, a fly swatted): returns it, or null if it is out of hearing. */
    static Sound play(MinecraftClient mc,String id,float volume,float pitch){
        if(volume<=.01f)return null;
        var s=new Sound(id,volume,pitch);mc.getSoundManager().play(s);return s;
    }
}
