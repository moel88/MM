package dev.sangwoo.celestialfall.client.title;

import dev.sangwoo.celestialfall.client.menu.MenuSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import java.util.ArrayList;
import java.util.List;

/** Owns only this menu's Foley and streamed BGM. Vanilla music yields while the menu is open. */
final class CinemaSound implements AutoCloseable {
    private final List<SoundInstance> owned=new ArrayList<>();
    private SoundInstance ambience,music;private float volume=-1;
    private boolean initialized,musicStarted;
    void update(List<CinemaTimeline.Cue> cues,boolean musicReady){
        var mc=MinecraftClient.getInstance();var manager=mc.getSoundManager();float current=(float)MenuSettings.volume();
        if(!initialized){initialized=true;mc.getMusicTracker().stop();}
        // Music has its own category, independent of UI/Foley volume. It is NOT an idle-loop cue.
        // Defer a muted start until music is enabled, without replaying it on resize or idle loops.
        if(musicReady&&!musicStarted&&mc.options.getSoundVolume(SoundCategory.MUSIC)>0&&mc.options.getSoundVolume(SoundCategory.MASTER)>0){
            music=new PositionedSoundInstance(new Identifier("celestialfall","cinema_title_satellites"),SoundCategory.MUSIC,
                .68f,1,Random.create(),true,0,SoundInstance.AttenuationType.NONE,0,0,0,true);
            manager.play(music);musicStarted=true;
        }
        owned.removeIf(sound->!manager.isPlaying(sound));
        if(Math.abs(current-volume)>.001f){if(ambience!=null){manager.stop(ambience);owned.remove(ambience);}volume=current;ambience=null;}
        if(current<=0)return;
        if(ambience==null){ambience=play("cinema_ambience",.14f,true);}
        for(var cue:cues)play(cue.sound(),cue.volume(),false);
    }
    private SoundInstance play(String name,float gain,boolean loop){
        var sound=new PositionedSoundInstance(new Identifier("celestialfall",name),SoundCategory.AMBIENT,
            gain*volume,1,Random.create(),loop,0,SoundInstance.AttenuationType.NONE,0,0,0,true);
        MinecraftClient.getInstance().getSoundManager().play(sound);owned.add(sound);return sound;
    }
    @Override public void close(){var manager=MinecraftClient.getInstance().getSoundManager();for(var sound:owned)manager.stop(sound);if(music!=null)manager.stop(music);owned.clear();ambience=null;music=null;}
}
