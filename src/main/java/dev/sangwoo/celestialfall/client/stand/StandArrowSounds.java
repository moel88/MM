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
 * moves, the blow going in, the knees on the ground (the sound of the very ground they kneel on), the heart beating
 * faster and faster as the arrow is taken in, the low rumble swelling under it and cut dead as everything goes still;
 * then, as the choice shows, the awakening's or the rejection's (the heart's last beats and the body falling onto the
 * ground). 2.71.1 (user request: "목소리나 한숨은 다 빼고 화살 꽂는 소리만 새로 갈아엎어서 새로 만들어줘", "꽂고
 * 빛날때 판타지스러운 소리로"): no voice any more (the sighs, breaths, gasps and the choke are gone); the blow is a magic
 * one (metal and glass that ring and sparkle as it lights up), and as the arrow glows and is drawn in, the light is
 * drawn in too (a shimmer swelling to the stillness).
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
    /**
     * The sound of the ground under the body, the block's own: its fall sound (a knee or the body coming down on it) or
     * its step sound (a foot set down on it); as loud and pitched as the game's own (fall: the block's volume x .5 and
     * pitch x .75; step: x .15) at a cue's volume and pitch of 1.
     */
    static final String GROUND="ground",STEP="step";
    /**
     * One sound: its track, when (seconds on the stab's clock, or after its choice shows), the sound (an id in this
     * mod's sounds, or GROUND), how loud, its pitch, and when it is hushed (fades out; NaN: it plays out).
     */
    record Cue(int track,float at,String sound,float volume,float pitch,float until){
        Cue(int track,float at,String sound,float volume,float pitch){this(track,at,sound,volume,pitch,Float.NaN);}
    }
    /** When the one it rejects lands, on a tick. */
    private static final float LANDS=Math.round(LANDS_AT*20)/20f;
    /** The cue sheet (by time within each track; the stages are ArrowStabPose's, the cut points tools/stand/cut_arrow_sounds.py's). */
    static final Cue[] CUES={
        // brought up before the eyes; lowered to the chest (the doubt)
        new Cue(STAB,.05f,"stand_arrow_cloth_1",.5f,1),
        new Cue(STAB,1.1f,"stand_arrow_cloth_2",.45f,1),
        // taken up and raised to the sky; turned point down and aimed; the thunder rolling far off, swelling to the
        // stillness and cut as it comes; the heart, faintly
        new Cue(STAB,2.95f,"stand_arrow_cloth_3",.45f,1),
        new Cue(STAB,3.95f,"stand_arrow_cloth_2",.35f,.9f),
        new Cue(STAB,4f,"stand_arrow_rumble",.75f,1,8.75f),
        new Cue(STAB,4.2f,"stand_arrow_heart",.25f,1),
        new Cue(STAB,4.75f,"stand_arrow_heart",.32f,1),
        // the arm driving it down; in, and it lights up (the magic blow, ringing on)
        new Cue(STAB,5.25f,"stand_arrow_swing",.5f,1),
        new Cue(STAB,HIT,"stand_arrow_stab",1,1),
        // the heart from then on (BEATS), louder and faster (the last ones only their first sound: hushed before the
        // second); each knee coming down on the ground; from the second, as the arrow glows and is drawn in, the light
        // drawn in with it (a shimmer swelling to the stillness, where it ends)
        new Cue(STAB,5.95f,"stand_arrow_heart",.55f,1),
        new Cue(STAB,6.7f,"stand_arrow_heart",.62f,1.02f),
        new Cue(STAB,KNEE,"stand_arrow_knee",.9f,1),
        new Cue(STAB,KNEE,GROUND,1,1),
        new Cue(STAB,7.3f,"stand_arrow_heart",.7f,1.04f),
        new Cue(STAB,KNEES,"stand_arrow_knee_2",.7f,1),
        new Cue(STAB,KNEES,GROUND,.8f,1),
        new Cue(STAB,KNEES,"stand_arrow_glow",.9f,1),
        new Cue(STAB,7.8f,"stand_arrow_heart",.78f,1.06f),
        new Cue(STAB,8.15f,"stand_arrow_heart",.85f,1.08f,8.25f),
        new Cue(STAB,8.45f,"stand_arrow_heart",.9f,1.1f,8.55f),
        new Cue(STAB,8.65f,"stand_arrow_heart",.95f,1.11f,8.75f),
        new Cue(STAB,8.8f,"stand_arrow_heart",1,1.12f,8.9f),
        // chosen: one great beat and the thunderclap as the stand bursts out of them, the air of it, a singing bowl ringing
        // on; getting up (the clothes, the left foot set down, then the right)
        new Cue(CHOSEN,0,"stand_arrow_heart",1,.85f),
        new Cue(CHOSEN,0,"stand_arrow_awaken",1,1),
        new Cue(CHOSEN,.05f,"stand_arrow_burst",.6f,1),
        new Cue(CHOSEN,.15f,"stand_arrow_bowl",.4f,1),
        new Cue(CHOSEN,.95f,"stand_arrow_cloth_1",.5f,.9f),
        new Cue(CHOSEN,1.2f,STEP,1,1),
        new Cue(CHOSEN,1.65f,STEP,1,1),
        // rejected: a beat, a last weaker beat, and the body falling on its face; then nothing
        new Cue(REJECTED,0,"stand_arrow_heart",.7f,.9f),
        new Cue(REJECTED,.55f,"stand_arrow_heart",.42f,.82f),
        new Cue(REJECTED,LANDS,"stand_arrow_bodyfall",1,1),
        new Cue(REJECTED,LANDS,GROUND,1,1),
    };
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
    /** The sound for cue {@code c}: this mod's, or the ground's under the body (its fall or step sound). */
    private static Sound sound(MinecraftClient mc,StandArrowClient.Stab s,Cue c,float volume){
        boolean fall=GROUND.equals(c.sound());
        if(!fall&&!STEP.equals(c.sound()))return new Sound(CelestialFall.id(c.sound()),volume*c.volume(),c.pitch());
        if(mc.world==null||!(mc.world.getEntityById(s.entity) instanceof PlayerEntity p))return null;
        BlockPos at=p.getBlockPos();
        var state=mc.world.getBlockState(at);
        if(state.isAir()){at=at.down();state=mc.world.getBlockState(at);}
        if(state.isAir())return null;
        var group=state.getSoundGroup();
        return fall?new Sound(group.getFallSound().getId(),volume*c.volume()*group.getVolume()*.5f,c.pitch()*group.getPitch()*.75f)
            :new Sound(group.getStepSound().getId(),volume*c.volume()*group.getVolume()*.15f,c.pitch()*group.getPitch());
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
