package dev.sangwoo.celestialfall.client.gacha;

import dev.sangwoo.celestialfall.client.menu.MenuSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;

/**
 * The gacha's sounds (user request, 2.40.0: holy and luxurious, every one): a choir, glass and church bells, a harp,
 * sparkles and a cathedral's reverb, made by tools/gacha/make_sounds.py (no samples). They play at the menu's volume; a
 * scene's sounds stop with it when it is skipped or the screen closes.
 */
final class GachaSound {
    private GachaSound(){}
    enum Cue {
        OPEN("open",true),HOVER("hover",false),CLICK("click",false),PANEL("panel",false),BACK("back",false),DENIED("denied",false),PULL("pull",false),
        ONE_START("one_start",true),GATHER("gather",true),REVEAL_R("reveal_r",true),REVEAL_SR("reveal_sr",true),REVEAL_SSR("reveal_ssr",true),
        TEN_START("ten_start",true),RISE("rise",true),LIGHT_R("light_r",true),LIGHT_SR("light_sr",true),LIGHT_SSR("light_ssr",true),WHITEOUT("whiteout",true),
        CARD("card",true),CARD_SR("card_sr",true),CARD_SSR("card_ssr",true),SSR_FANFARE("fanfare",true),STAR("star",true),RESULT("result",true);
        final SoundEvent event;
        /** Long enough to be cut short when the screen closes (a click or a tick is left to finish). */
        final boolean held;
        Cue(String name,boolean held){event=SoundEvent.of(new Identifier("celestialfall","gacha_"+name));this.held=held;}
    }
    private static final List<SoundInstance> SCENE=new ArrayList<>(),SCREEN=new ArrayList<>();

    static void play(Cue c){play(c,1,1);}
    static void play(Cue c,float pitch,float volume){var s=start(c,pitch,volume);if(s!=null&&c.held){SCREEN.removeIf(i->!playing(i));SCREEN.add(s);}}
    /** A sound belonging to the running scene. */
    static void scene(Cue c,float pitch,float volume){var s=start(c,pitch,volume);if(s!=null){SCENE.removeIf(i->!playing(i));SCENE.add(s);}}
    /** The sounds are mastered loud; this leaves room for several ringing at once without clipping the game's output. */
    static final float GAIN=.55f;
    private static SoundInstance start(Cue c,float pitch,float volume){
        double v=MenuSettings.volume();if(v<=0)return null;
        var s=PositionedSoundInstance.master(c.event,pitch,(float)(volume*v*GAIN));
        MinecraftClient.getInstance().getSoundManager().play(s);return s;
    }
    private static boolean playing(SoundInstance s){return MinecraftClient.getInstance().getSoundManager().isPlaying(s);}
    static void stopScene(){var m=MinecraftClient.getInstance().getSoundManager();for(var s:SCENE)m.stop(s);SCENE.clear();}
    /** Everything long the gacha is playing (the screen closed). */
    static void stopAll(){stopScene();var m=MinecraftClient.getInstance().getSoundManager();for(var s:SCREEN)m.stop(s);SCREEN.clear();}
}
