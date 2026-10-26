package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.stand.ArrowStabPose.Fate;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import static dev.sangwoo.celestialfall.client.stand.ArrowStabPose.*;

/**
 * The stand arrow's sounds (2.71.0, user request: "효과음이 너무 AI같아"): recordings, not made-up tones, each laid on
 * the stab's clock where the body does what it is the sound of (the cue sheet, {@link #CUES}): the clothes as the arm
 * moves, the breaths (the sigh of the doubt, the deep breath, the breath caught before the blow), the blow going in,
 * the gasp, the knees on the ground (the sound of the very ground they kneel on), the heart beating faster and faster
 * as the arrow is taken in, the low rumble swelling under it and cut dead as everything goes still; then, as the
 * choice shows, the awakening's or the rejection's (the heart's last beats and the body falling onto the ground).
 * <p>
 * Every client plays them from the stab's clock it keeps (StandArrowClient: so they stay on the body it draws however
 * late the word came), the choice's from when it shows there; a sound heard more than {@link #LATE} after its time is
 * let go. Cut short, the stab's sounds fade out and no more come. Loud for the one stabbed and those near, fading out
 * to CARRY blocks (not placed in the world: as the stand's other sounds).
 */
final class StandArrowSounds {
    private StandArrowSounds(){}
    /** The tracks: the stab itself (from its start), and its choice's (from when it shows), chosen or rejected. */
    static final int STAB=0,CHOSEN=1,REJECTED=2;
    /** The sound of the ground under the body (the block's own fall sound), for a knee or the body coming down on it. */
    static final String GROUND="ground";
    /**
     * One sound: its track, when (seconds on the stab's clock, or after its choice shows), the sound (an id in this
     * mod's sounds, or GROUND), how loud, its pitch, and when it is hushed (fades out; NaN: it plays out).
     */
    record Cue(int track,float at,String sound,float volume,float pitch,float until){
        Cue(int track,float at,String sound,float volume,float pitch){this(track,at,sound,volume,pitch,Float.NaN);}
    }
    /** The cue sheet (by time within each track). */
    static final Cue[] CUES={};
    /** A cue heard this long after its time (seconds) still plays; later it is let go (it would not go with the body). */
    static final float LATE=.3f;
    /** Fading out (ticks). */
    static final int FADE=3;

    /** A sound of the stab: not placed in the world, as loud as the stab is here, faded out when it is hushed or cut short. */
    static final class Sound extends AbstractSoundInstance implements TickableSoundInstance {
        private final float full;private int fading=-1;private boolean done,begun;
        Sound(Identifier id,float volume,float pitch){
            super(id,SoundCategory.PLAYERS,Random.create());
            this.volume=full=volume;this.pitch=pitch;attenuationType=SoundInstance.AttenuationType.NONE;relative=true;x=y=z=0;
        }
        void fade(){if(fading<0)fading=0;}
        @Override public boolean canPlay(){return !done&&(fading<0||begun);}
        @Override public boolean isDone(){return done;}
        @Override public void tick(){
            begun=true;
            if(fading<0)return;
            fading++;volume=full*Math.max(0,1-fading/(float)FADE);
            if(fading>=FADE)done=true;
        }
    }
    /** A cue playing: its sound, and when it is hushed (the stab's clock, NaN never). */
    record Playing(Sound sound,float until){}

    /** When cue {@code c} sounds on the stab's clock (NaN: not on this stab's tracks, or not yet known). */
    static float when(Cue c,StandArrowClient.Stab s){
        if(c.track()==STAB)return c.at();
        boolean ours=c.track()==CHOSEN&&s.fate==Fate.AWAKEN||c.track()==REJECTED&&s.fate==Fate.DIE;
        return ours?s.shows()+c.at():Float.NaN;
    }
    /** When cue {@code c} is hushed on the stab's clock (NaN: never). */
    static float hushed(Cue c,StandArrowClient.Stab s){
        if(Float.isNaN(c.until()))return Float.NaN;
        return c.track()==STAB?c.until():s.shows()+c.until();
    }

    /**
     * A tick of stab {@code s} ({@code t} its clock this tick): every cue whose time has come is played (or let go,
     * heard too late), every one whose time to be hushed has come fades out; {@code volume} how loud it is here.
     */
    static void tick(MinecraftClient mc,StandArrowClient.Stab s,float t,float volume){
        if(s.cutShort())return;
        for(int i=0;i<CUES.length;i++){
            if(s.played.get(i))continue;
            var c=CUES[i];float at=when(c,s);
            // (the tick that reaches it plays it: the cues are on whole ticks)
            if(Float.isNaN(at)||t+.026f<at)continue;
            s.played.set(i);
            if(t-at>LATE||volume<=.01f)continue;
            var sound=sound(mc,s,c,volume);
            if(sound==null)continue;
            mc.getSoundManager().play(sound);
            s.playing.add(new Playing(sound,hushed(c,s)));
        }
        s.playing.removeIf(p->{
            if(p.sound().isDone())return true;
            if(!Float.isNaN(p.until())&&t+.026f>=p.until())p.sound().fade();
            return false;
        });
    }
    /** The sound for cue {@code c}: this mod's, or the fall sound of the ground under the body. */
    private static Sound sound(MinecraftClient mc,StandArrowClient.Stab s,Cue c,float volume){
        if(!GROUND.equals(c.sound()))return new Sound(CelestialFall.id(c.sound()),volume*c.volume(),c.pitch());
        if(mc.world==null||!(mc.world.getEntityById(s.entity) instanceof PlayerEntity p))return null;
        BlockPos at=p.getBlockPos();
        var state=mc.world.getBlockState(at);
        if(state.isAir()){at=at.down();state=mc.world.getBlockState(at);}
        if(state.isAir())return null;
        var group=state.getSoundGroup();
        return new Sound(group.getFallSound().getId(),volume*c.volume()*group.getVolume(),c.pitch()*group.getPitch());
    }
    /** Cut short: the stab's sounds fade out (and no more come). */
    static void cut(StandArrowClient.Stab s){for(var p:s.playing)p.sound().fade();}
    /** Gone (the world left): stopped at once. */
    static void stop(MinecraftClient mc,StandArrowClient.Stab s){
        for(var p:s.playing)if(!p.sound().isDone())mc.getSoundManager().stop(p.sound());
        s.playing.clear();
    }
    /** The cues of a track in order (the tests). */
    static List<Cue> track(int track){var out=new ArrayList<Cue>();for(var c:CUES)if(c.track()==track)out.add(c);return out;}
}
