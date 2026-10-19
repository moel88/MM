package dev.sangwoo.celestialfall.client.gacha;

import dev.sangwoo.celestialfall.client.menu.MenuSettings;
import dev.sangwoo.celestialfall.gacha.GachaRules.Grade;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import java.util.List;

/** The pull scenes' sounds (2.39.0): the intro's own sand and chime, and Minecraft's bells for the reveals, at the menu's volume. */
final class GachaSound {
    private GachaSound(){}
    private static final SoundEvent SAND=SoundEvent.of(new Identifier("celestialfall","cinema_sand")),CHIME=SoundEvent.of(new Identifier("celestialfall","cinema_chime"));
    static void play(SoundEvent s,float pitch,float volume){
        double v=MenuSettings.volume();if(v<=0)return;
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(s,pitch,(float)(volume*v)));
    }
    static void begin(List<Grade> grades){play(SAND,1,.55f);}
    /** The cues between the scene's last frame ({@code from}) and this one ({@code to}). */
    static void cues(List<Grade> grades,float from,float to){
        if(grades.size()==1){
            var g=grades.get(0);float r=GachaStage.reveal(g);
            if(crossed(from,to,1.6f*GachaStage.SECOND))play(SoundEvents.BLOCK_BEACON_POWER_SELECT,1.35f,.22f);
            if(crossed(from,to,r))reveal(g,0);
            return;
        }
        for(int i=0;i<grades.size();i++)if(crossed(from,to,GachaStage.lightAt(grades,i)))reveal(grades.get(i),i);
    }
    private static void reveal(Grade g,int i){
        play(CHIME,.82f+.06f*i+(g==Grade.R?0:g==Grade.SR?.12f:.25f),g==Grade.R?.35f:.5f);
        if(g==Grade.SR)play(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,1.35f,.7f);
        if(g==Grade.SSR){play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,1,.55f);play(SoundEvents.BLOCK_BEACON_ACTIVATE,1.2f,.4f);}
    }
    static void result(List<Grade> grades){play(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,grades.contains(Grade.SSR)?1.4f:1.1f,.4f);}
    private static boolean crossed(float from,float to,float at){return from<at&&to>=at;}
}
