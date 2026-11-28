package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.stand.GoldExperienceRules;
import dev.sangwoo.celestialfall.stand.GoldFlyEntity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * The sounds of Gold Experience's fly (2.153.0, T: laid together from recordings, tools/stand/make_gold_sounds.py): a
 * housefly's buzz, in the world where the fly is drawn (GoldLife.flyAt: in front of whom it belongs to) and following
 * it, heard by everyone near it (fading out to 12 blocks), from the moment the part struck off becomes the fly (a small
 * sparkle then) until it is swatted or goes back into the body (a sparkle swelling backwards then, the buzz dying away
 * as it goes). Every client keeps them from the flies it has (GoldFlyEntity), on each fly's own clock.
 */
public final class GoldFlySounds {
    private GoldFlySounds(){}
    /** How loud its buzz is (before the game's own volume and the distance), and how long it takes to come in (ticks). */
    static final float BUZZ=.7f;static final int IN=6;
    /** The moments on the fly's clock (ticks since the blow): it becomes the fly; it starts back into the body. */
    static final int FORMS=GoldExperienceRules.FLY_FORMS,BACK=GoldExperienceRules.FLY_FORMS+GoldExperienceRules.FLY_LASTS;
    /** The buzz, following the fly. */
    static final class Buzz extends AbstractSoundInstance implements TickableSoundInstance {
        final int fly;private float level,target;private boolean done;
        Buzz(int fly,Vec3d at){
            super(CelestialFall.id("gold_life_fly_buzz"),SoundCategory.PLAYERS,Random.create());
            this.fly=fly;repeat=true;repeatDelay=0;attenuationType=SoundInstance.AttenuationType.LINEAR;relative=false;
            x=at.x;y=at.y;z=at.z;volume=.001f;pitch=1;
        }
        /** How loud it should be now (0 to 1 of BUZZ): it eases there over IN ticks; 0 and there, it is done. */
        void toward(float t){target=t;}
        @Override public boolean isDone(){return done;}
        @Override public boolean shouldAlwaysPlay(){return true;}
        @Override public void tick(){
            var mc=MinecraftClient.getInstance();
            if(mc.world==null||!(mc.world.getEntityById(fly) instanceof GoldFlyEntity f)||!f.isAlive())target=0;
            else{var at=GoldLife.flyAt(f,0);if(at!=null){x=at.x;y=at.y;z=at.z;}}
            level+=Math.signum(target-level)*Math.min(Math.abs(target-level),1f/IN);
            volume=Math.max(.001f,(float)(CelestialFall.CONFIG.effectVolume*BUZZ*level));
            if(target<=0&&level<=0)done=true;
        }
    }
    /** What this client has played of each fly (by entity id): its buzz, whether its sparkle as it became the fly and its going back have played. */
    private static final class Fly {Buzz buzz;boolean formed,back;}
    private static final Map<Integer,Fly> FLIES=new HashMap<>();

    public static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(GoldFlySounds::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(()->stop(mc)));
    }
    private static void stop(MinecraftClient mc){
        for(var f:FLIES.values())if(f.buzz!=null&&!f.buzz.isDone())mc.getSoundManager().stop(f.buzz);
        FLIES.clear();
    }
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(!FLIES.isEmpty())stop(mc);return;}
        if(mc.isPaused())return;
        Set<Integer> seen=new HashSet<>();
        for(var e:mc.world.getEntities()){
            if(!(e instanceof GoldFlyEntity fly)||!fly.isAlive()||!fly.clientReady())continue;
            seen.add(fly.getId());
            var f=FLIES.computeIfAbsent(fly.getId(),k->new Fly());
            double age=fly.clientAge(0);
            var at=GoldLife.flyAt(fly,0);if(at==null)at=fly.getPos();
            float near=GoldSounds.volume(mc,false,at);
            // it becomes the fly: a small sparkle, and the buzz comes in (one seen only later starts buzzing at once)
            if(!f.formed&&age>=FORMS-1){
                f.formed=true;
                if(age<FORMS+4)GoldSounds.play(mc,"gold_life_fly_born",near,1);
            }
            if(f.formed&&!f.back&&f.buzz==null){f.buzz=new Buzz(fly.getId(),at);mc.getSoundManager().play(f.buzz);}
            if(f.buzz!=null&&!f.back)f.buzz.toward(1);
            // its time up: it goes back into the body, a sparkle swelling backwards as it goes, the buzz dying away
            if(!f.back&&age>=BACK){
                f.back=true;
                if(age<BACK+4)GoldSounds.play(mc,"gold_life_fly_back",near,1);
                if(f.buzz!=null)f.buzz.toward(0);
            }
        }
        // gone (swatted, or back in the body): its buzz dies away (Buzz.tick sees it gone)
        FLIES.entrySet().removeIf(e->{
            if(seen.contains(e.getKey()))return false;
            var b=e.getValue().buzz;if(b!=null)b.toward(0);
            return true;
        });
    }
    /** For the checks: how many flies this client is buzzing for now. */
    public static int buzzing(){int n=0;for(var f:FLIES.values())if(f.buzz!=null&&!f.buzz.isDone()&&!f.back)n++;return n;}
}
