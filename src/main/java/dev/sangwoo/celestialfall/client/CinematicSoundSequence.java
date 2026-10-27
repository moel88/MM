package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.CinematicAudioPlan;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

/** One owner per cast. No pitch changes, time stretching, distance attenuation or replay. */
final class CinematicSoundSequence {
    private final List<CinematicAudioPlan.Cue> cues;
    private int segment=-1;
    private PositionedSoundInstance playing;
    CinematicSoundSequence(List<CinematicAudioPlan.Cue> cues) { this.cues=cues; }
    boolean tick(MinecraftClient mc,int age,float volume) {
        int next=CinematicAudioPlan.segment(cues,age);
        if(next==segment)return false;
        stop(mc);segment=next;
        if(next<0 || volume<=0 || !CinematicAudioPlan.startable(cues.get(next),age))return false;
        playing=new PositionedSoundInstance(new Identifier(CelestialFall.ID,cues.get(next).sound()),
            SoundCategory.PLAYERS,volume,1.0f,net.minecraft.util.math.random.Random.create(),
            false,0,SoundInstance.AttenuationType.NONE,0,0,0,true);
        mc.getSoundManager().play(playing);return true;
    }
    void stop(MinecraftClient mc) {
        if(playing!=null)mc.getSoundManager().stop(playing);
        playing=null;
    }
}
