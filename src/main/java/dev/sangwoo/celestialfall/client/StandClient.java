package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.stand.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.sound.SoundCategory;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import static dev.sangwoo.celestialfall.stand.KagenoRules.*;

public final class StandClient {
    private static final ItemStack SWORD=new ItemStack(CelestialFall.ATOMIC_SWORD);
    public record Target(UUID uuid,int entity,Vec3d position){}
    /** {@code aims}: T held's slashes, the way each was thrown (degrees, NaN before it leaves; the server's). */
    public record Cast(UUID id,UUID caster,Identifier dimension,int skill,int combo,long start,long serverNow,long received,Vec3d origin,float yaw,List<Target> targets,float[] aims){
        public Cast(UUID id,UUID caster,Identifier dimension,int skill,int combo,long start,long serverNow,long received,Vec3d origin,float yaw,List<Target> targets){
            this(id,caster,dimension,skill,combo,start,serverNow,received,origin,yaw,targets,new float[]{Float.NaN,Float.NaN,Float.NaN});
        }
        public float age(float delta){return MinecraftClient.getInstance().world==null?9999:new KagenoCastClock(serverNow-start,received).age(ticks,delta);}
        public boolean includes(UUID id){return caster.equals(id)||targets.stream().anyMatch(t->t.uuid.equals(id));}
    }
    public record Mark(UUID id,int entity,long until){}
    public record Status(long until,float boost,long[] ready,List<Mark> marks){}
    public static final Map<UUID,Cast> CASTS=new LinkedHashMap<>();
    public static final Map<UUID,Status> STATES=new HashMap<>();
    /** World tick at which each stand user's overdrive (Q) was switched on, for the activation glow. */
    public static final Map<UUID,Long> OVERDRIVE=new HashMap<>();
    private static Object world,player;private static long nextPunch,lastThemeUntil;private static int ticks;
    private static UUID handedOff,cancelledPose;private static boolean holdingT;private static final Set<Integer> captured=new HashSet<>();
    private static SoundInstance theme;
    /** In-game "N Hits" combo counter of the local player. */
    public static int hitCount;public static long hitTime=-1000,comboStart=-1000;
    private StandClient(){}
    public static ItemStack sword(){return SWORD;}
    public static boolean visible(PlayerEntity p){return StandManager.kageno(p)&&!p.isInvisible()&&!p.isSleeping();}
    public static void initialize(){
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(dev.sangwoo.celestialfall.client.kageno.KagenoClips::trackGuards);
        KagenoSword.initialize();KagenoFire.initialize();dev.sangwoo.celestialfall.client.atomic.AtomicShader.initialize();dev.sangwoo.celestialfall.client.vfx.Vfx.initialize();dev.sangwoo.celestialfall.client.vfx.FxLayer.initialize();
        KagenoManager.setClientFrozen(StandClient::frozen);
        ClientPlayNetworking.registerGlobalReceiver(KagenoManager.CAST,(mc,h,b,r)->{
            UUID id=b.readUuid(),caster=b.readUuid();var dim=b.readIdentifier();int skill=b.readUnsignedByte(),combo=b.readUnsignedByte();long start=b.readLong(),serverNow=b.readLong();Vec3d origin=vec(b);float yaw=b.readFloat();int n=b.readVarInt();
            if(skill<0||skill>H&&skill!=P||n<0||n>4096)return;
            var targets=new ArrayList<Target>();for(int i=0;i<n;i++)targets.add(new Target(b.readUuid(),b.readInt(),vec(b)));
            float[] aims=new float[3];for(int i=0;i<aims.length;i++){float a=b.readFloat();aims[i]=Float.isFinite(a)?a:Float.NaN;}
            mc.execute(()->{if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return;observe(mc);acceptCast(new Cast(id,caster,dim,skill,combo,start,serverNow,ticks,origin,yaw,List.copyOf(targets),aims));});
        });
        ClientPlayNetworking.registerGlobalReceiver(KagenoManager.STOP,(mc,h,b,r)->{UUID id=b.readUuid();mc.execute(()->{var c=CASTS.remove(id);if(c!=null&&c.age(0)>=DURATION[c.skill]-2)KagenoAudio.finish(id);else KagenoAudio.stop(id);});});
        ClientPlayNetworking.registerGlobalReceiver(KagenoManager.STATUS,(mc,h,b,r)->{
            UUID id=b.readUuid();var dim=b.readIdentifier();long until=b.readLong();float boost=b.readFloat();long[] ready=new long[9];for(int i=0;i<9;i++)ready[i]=b.readLong();int n=b.readVarInt();if(n<0||n>4096)return;
            var marks=new ArrayList<Mark>();for(int i=0;i<n;i++)marks.add(new Mark(b.readUuid(),b.readInt(),b.readLong()));
            mc.execute(()->{if(mc.world==null||!MapClient.enabled()||!mc.world.getRegistryKey().getValue().equals(dim))return;observe(mc);
                long now=mc.world.getTime();var old=STATES.get(id);
                // A fresh activation runs 30 s; refreshes and late joins never replay the glow.
                if(until-now>=585&&(old==null||old.until()<=now))OVERDRIVE.put(id,now);
                if(until<=now)OVERDRIVE.remove(id);
                STATES.put(id,new Status(until,boost,ready,marks));});
        });
        ClientPlayNetworking.registerGlobalReceiver(KagenoManager.DAMAGE,(mc,h,b,r)->{int entity=b.readVarInt();float amount=b.readFloat(),yaw=b.readFloat();if(!Float.isFinite(amount)||amount<0||amount>10000)return;mc.execute(()->{if(MapClient.enabled())dev.sangwoo.celestialfall.client.kageno.HitFeedback.hit(mc,entity,amount,yaw);});});
        ClientPlayNetworking.registerGlobalReceiver(KagenoManager.HITS,(mc,h,b,r)->{int n=b.readVarInt();mc.execute(()->{if(mc.world!=null){int count=Math.max(0,Math.min(9999,n));if(count<=1||count<hitCount)comboStart=mc.world.getTime();hitCount=count;hitTime=mc.world.getTime();}});});
        ClientTickEvents.START_CLIENT_TICK.register(StandClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((h,mc)->mc.execute(()->clear(mc)));
        WorldRenderEvents.START.register(c->dev.sangwoo.celestialfall.client.kageno.BodyGhost.world(true));
        WorldRenderEvents.LAST.register(KagenoRenderer::render);
        WorldRenderEvents.END.register(c->dev.sangwoo.celestialfall.client.kageno.BodyGhost.world(false));
        HudRenderCallback.EVENT.register(KagenoRenderer::hud);
    }
    public static void acceptCast(Cast incoming){
        if(incoming.id.equals(cancelledPose))return;
        Cast old=CASTS.get(incoming.id);
        // Targets and T held's slash aims may change during a cast. Their refresh must not reset pose/camera/audio time.
        Cast cast=old==null?incoming:new Cast(old.id,old.caster,old.dimension,old.skill,old.combo,old.start,old.serverNow,old.received,old.origin,old.yaw,incoming.targets,incoming.aims);
        if(cast.age(0)<DURATION[cast.skill]){
            if(old==null){dev.sangwoo.celestialfall.client.kageno.KagenoClips.begin(cast);
                // a skill with a cutscene (2.64.0, user request: "컷씬 나오는 스킬 사용할때는 시전자의 몸이 0.2초 동안 하늘색으로 변해")
                if(cast.skill==H&&cast.age(0)<4)CutsceneFlash.flash(cast.caster);
                if(cast.skill==H&&MinecraftClient.getInstance().player!=null&&cast.includes(MinecraftClient.getInstance().player.getUuid()))dev.sangwoo.celestialfall.client.integration.CinematicsBridge.nativeCutscene();}
            CASTS.put(cast.id,cast);if(old==null)KagenoAudio.begin(cast);
        }
    }
    private static Vec3d vec(net.minecraft.network.PacketByteBuf b){return new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());}
    private static void observe(MinecraftClient mc){if(world!=mc.world||player!=mc.player){clear(mc);world=mc.world;player=mc.player;}}
    public static void clear(MinecraftClient mc){CooldownHud.clear();cancelledPose=handedOff=null;dev.sangwoo.celestialfall.client.kageno.KagenoClips.clear();KagenoAudio.clear();CASTS.clear();STATES.clear();OVERDRIVE.clear();dev.sangwoo.celestialfall.client.kageno.HitFeedback.clear();holdingT=false;captured.clear();nextPunch=lastThemeUntil=0;world=player=null;if(theme!=null)mc.getSoundManager().stop(theme);theme=null;}
    private static void tick(MinecraftClient mc){
        observe(mc);if(mc.world==null||mc.player==null||!MapClient.enabled()){clear(mc);return;}
        ticks++;long now=mc.world.getTime();
        CASTS.values().removeIf(c->!c.dimension.equals(mc.world.getRegistryKey().getValue())||c.age(0)>=DURATION[c.skill]||c.age(0)<0||deadCaster(mc,c));
        KagenoAudio.retain(CASTS.keySet());
        dev.sangwoo.celestialfall.client.kageno.RushDash.drive(mc);
        // held by Star Platinum's inhale (2.61.0): drawn in and kept before the stand by this client, as the dash is
        dev.sangwoo.celestialfall.client.stand.StandInhale.drive(mc);
        STATES.entrySet().removeIf(e->mc.world.getPlayerByUuid(e.getKey())==null);
        if(holdingT){if(!StandManager.kageno(mc.player)||mc.currentScreen!=null||!mc.isWindowFocused()){send(CANCEL);holdingT=false;}else if(ticks%3==0)send(PULSE);}
        for(var c:CASTS.values())if(KagenoRules.frozen(c.skill,c.age(0))){
            var caster=mc.world.getPlayerByUuid(c.caster);if(caster!=null&&c.skill!=R)KagenoManager.freeze(caster);
            for(var t:c.targets){var e=mc.world.getEntityById(t.entity);if(e instanceof LivingEntity l&&e.getUuid().equals(t.uuid))KagenoManager.freeze(l);}
        }
        // H: the gameplay camera comes back with the last cutscene shot's orientation (hard cut, no blend).
        var cine=cinematic();
        if(cine!=null&&cine.skill==H&&cine.age(0)>=KagenoRules.H_CUT&&!cine.id.equals(handedOff)){handedOff=cine.id;dev.sangwoo.celestialfall.client.kageno.KagenoCamera.handoff(cine);}
        if(StandManager.kageno(mc.player)&&mc.options.attackKey.isPressed()&&!dev.sangwoo.celestialfall.client.commerce.CommerceClient.fruitInHand()&&!dev.sangwoo.celestialfall.client.meteor.MeteorClient.pickaxeInHand())punch();
        var state=STATES.get(mc.player.getUuid());long until=state!=null&&visible(mc.player)?state.until:0;
        if(until>now&&lastThemeUntil==0){
            theme=new PositionedSoundInstance(CelestialFall.KAGENO_THEME.getId(),SoundCategory.RECORDS,(float)CelestialFall.CONFIG.effectVolume,1,net.minecraft.util.math.random.Random.create(),false,0,SoundInstance.AttenuationType.NONE,0,0,0,true);
            mc.getSoundManager().play(theme);lastThemeUntil=until;
        }else if(until<=now&&lastThemeUntil!=0){if(theme!=null)mc.getSoundManager().stop(theme);theme=null;lastThemeUntil=0;}
    }
    private static boolean deadCaster(MinecraftClient mc,Cast c){var p=mc.world.getPlayerByUuid(c.caster);return p!=null&&(!p.isAlive()||!StandManager.kageno(p));}
    /** Read movement before the camera applies the stand movement lock. */
    public static void cancelPoseOnMovement(net.minecraft.client.input.Input input){
        var mc=MinecraftClient.getInstance();
        if(mc.player==null||mc.currentScreen!=null||!mc.isWindowFocused()||!MapClient.enabled())return;
        if(Math.abs(input.movementForward)<.01f&&Math.abs(input.movementSideways)<.01f)return;
        var c=action(mc.player.getUuid());if(c==null||c.skill!=P)return;
        cancelledPose=c.id;send(POSE_CANCEL);CASTS.remove(c.id);KagenoAudio.stop(c.id);
    }
    public static boolean frozen(LivingEntity e){return e.isAlive()&&MapClient.enabled()&&(AtomicClient.locked(e.getUuid())||dev.sangwoo.celestialfall.client.stand.StandArrowClient.holds(e)||dev.sangwoo.celestialfall.client.stand.StandBeatdown.holds(e)||dev.sangwoo.celestialfall.client.stand.StandTimeStop.holds(e)||CASTS.values().stream().anyMatch(c->(KagenoRules.frozen(c.skill,c.age(0))&&(c.skill!=R&&c.caster.equals(e.getUuid())||c.targets.stream().anyMatch(t->t.uuid.equals(e.getUuid())))||KagenoRules.trapped(c.skill,c.age(0)+(c.skill==RUSH?TRAP_LEAD:0))&&c.targets.stream().anyMatch(t->t.uuid.equals(e.getUuid())))));}
    /**
     * Ticks by which a T+ target's own client lets go before the server's spin cut: this clock runs behind the
     * server's by the packet's way here, and a target still held when the cut's throw arrives would lose it.
     */
    static final int TRAP_LEAD=2;
    /** Action lock and locomotion lock differ: R's caster can walk at half speed, and E's walks through its thrusts. */
    public static boolean locked(){var mc=MinecraftClient.getInstance();if(mc.player==null||!MapClient.enabled())return false;if(frozen(mc.player))return true;return action(mc.player.getUuid())!=null;}
    public static boolean movementLocked(){var mc=MinecraftClient.getInstance();if(mc.player==null||!MapClient.enabled())return false;if(frozen(mc.player))return true;var cast=action(mc.player.getUuid());return cast!=null&&cast.skill()!=R&&!dev.sangwoo.celestialfall.client.kageno.KagenoAim.aimingThrust(cast);}
    public static boolean rWalking(UUID id){var cast=action(id);return cast!=null&&cast.skill()==R;}
    /** Whether this player walks through E's thrusts (2.57.0; at a fifth of their speed since 2.58.0, KagenoRules.E_SLOW). */
    public static boolean eWalking(UUID id){var cast=action(id);return cast!=null&&cast.skill()==E&&KagenoRules.eWalking(cast.age(0));}
    /**
     * A skill's slowdown from the server is on this player's speed: R's, E's (2.57.0) or Star Platinum's rush's
     * (2.57.0). It leaves a tick or two after the client's own skill ends.
     */
    public static boolean skillSlowed(net.minecraft.entity.player.PlayerEntity p){
        var speed=p.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED);
        return speed!=null&&(speed.getModifier(KagenoManager.R_SLOW)!=null||speed.getModifier(KagenoManager.E_SLOW)!=null||speed.getModifier(dev.sangwoo.celestialfall.stand.StarPlatinumRush.SLOW)!=null);
    }
    /** Q shadow mark: some stand user's live mark sits on this entity (drawn as a black silhouette). */
    public static boolean shadowMarked(net.minecraft.entity.Entity e){
        var mc=MinecraftClient.getInstance();if(mc.world==null||!MapClient.enabled()||STATES.isEmpty())return false;long now=mc.world.getTime();
        for(var s:STATES.values())for(var m:s.marks())if(m.until()>now&&m.entity()==e.getId()&&m.id().equals(e.getUuid()))return true;
        return false;
    }
    /** The local player's own shadow marks also reveal the target through walls with a violet outline. */
    public static boolean shadowOutlined(net.minecraft.entity.Entity e){
        var mc=MinecraftClient.getInstance();if(mc.world==null||mc.player==null||!MapClient.enabled())return false;var s=STATES.get(mc.player.getUuid());if(s==null)return false;long now=mc.world.getTime();
        for(var m:s.marks())if(m.until()>now&&m.entity()==e.getId()&&m.id().equals(e.getUuid()))return true;
        return false;
    }
    /** R caster during the gray phase (cyan outline). */
    public static boolean rOutlined(net.minecraft.entity.Entity e){
        if(!MapClient.enabled()||!(e instanceof net.minecraft.entity.player.PlayerEntity))return false;
        for(var c:CASTS.values())if(c.skill==R&&c.caster.equals(e.getUuid())){float a=c.age(0);if(a>=KagenoRules.R_GRAY&&a<KagenoRules.R_COLOR)return true;}
        return false;
    }
    /**
     * Veil writes its copy of the world back over the frame after the game has drawn the entity outlines, so R's cyan
     * outline and the Q mark outlines never showed. While one of them is live the outline framebuffer (still holding
     * this frame's outlines) is drawn again on top, before the HUD.
     */
    public static void redrawOutlines(){
        var mc=MinecraftClient.getInstance();if(mc.world==null||mc.worldRenderer==null||!MapClient.enabled())return;
        boolean any=false;
        for(var p:mc.world.getPlayers())if(rOutlined(p)){any=true;break;}
        if(!any&&mc.player!=null){var s=STATES.get(mc.player.getUuid());long now=mc.world.getTime();if(s!=null)for(var m:s.marks())if(m.until()>now){any=true;break;}}
        if(any)mc.worldRenderer.drawEntityOutlinesFramebuffer();
    }
    /** World grayscale amount for the local viewer: in over 4 frames, back to colour over ≈8 frames. */
    public static float monoAmount(float delta){
        var p=MinecraftClient.getInstance().player;if(p==null||!p.isAlive())return 0;float best=0;
        for(var c:CASTS.values())if(c.skill==R&&c.includes(p.getUuid())){
            float a=c.age(delta);
            float in=Math.max(0,Math.min(1,(a-KagenoRules.R_GRAY)/1.33f)),out=1-Math.max(0,Math.min(1,(a-KagenoRules.R_COLOR)/2.67f));
            best=Math.max(best,Math.min(in,out));
        }
        return best;
    }
    public static boolean monochrome(){var p=MinecraftClient.getInstance().player;return p!=null&&p.isAlive()&&CASTS.values().stream().anyMatch(c->c.skill==R&&KagenoRules.frozen(R,c.age(0))&&c.includes(p.getUuid()));}
    /** H: on the participants' screens the struck appear in no cutscene shot; they come back at the hard cut. */
    public static boolean cutsceneHidden(Entity e){var c=cinematic();return c!=null&&!c.caster.equals(e.getUuid())&&c.includes(e.getUuid())&&c.age(0)<KagenoRules.H_CUT;}
    /** H: after the cut each target leans back ≈35° (stagger) and is upright again 0.7 s later; its feet stay put. */
    public static float stagger(Entity e,float delta){
        for(var c:CASTS.values())if(c.skill==H&&!c.caster.equals(e.getUuid())&&c.includes(e.getUuid())){
            float t=(c.age(delta)-KagenoRules.H_CUT)/20f;
            if(t>=0&&t<.7f){float u=t/.7f;return 35*(1-u*u*(3-2*u));}
        }
        return 0;
    }
    public static Cast cinematic(){var mc=MinecraftClient.getInstance();if(!MapClient.enabled()||mc.player==null||!mc.player.isAlive())return null;return CASTS.values().stream().filter(c->c.skill==H&&c.age(0)<DURATION[H]&&c.includes(mc.player.getUuid())).findFirst().orElse(null);}
    /** The local player's E is in its thrusts: he walks and his facing follows his camera (user request, 2.38.2). */
    public static boolean aimingThrust(){var p=MinecraftClient.getInstance().player;if(p==null)return false;var c=action(p.getUuid());return c!=null&&dev.sangwoo.celestialfall.client.kageno.KagenoAim.aimingThrust(c);}
    /** The local player is throwing T held's slashes: his facing follows his aim until the dash ({@link dev.sangwoo.celestialfall.client.kageno.RushAim}). */
    public static boolean aimingRush(){
        var p=MinecraftClient.getInstance().player;if(p==null)return false;var c=action(p.getUuid());
        return c!=null&&dev.sangwoo.celestialfall.client.kageno.RushAim.aiming(c);
    }
    public static Cast action(UUID id){return CASTS.values().stream().filter(c->c.caster.equals(id)&&c.age(0)<inputEnd(c.skill)).reduce((a,b)->b).orElse(null);}
    public static Cast visualCast(){var mc=MinecraftClient.getInstance();if(mc.player==null)return null;Cast cinematic=cinematic();if(cinematic!=null)return cinematic;return CASTS.values().stream().filter(c->c.origin.squaredDistanceTo(mc.player.getPos())<32*32).min(Comparator.comparingDouble(c->c.caster.equals(mc.player.getUuid())?-1:c.origin.squaredDistanceTo(mc.player.getPos()))).orElse(null);}
    public static void send(int action){if(!ClientPlayNetworking.canSend(KagenoManager.INPUT))return;var b=PacketByteBufs.create();b.writeByte(action);ClientPlayNetworking.send(KagenoManager.INPUT,b);}
    public static boolean punch(){
        var mc=MinecraftClient.getInstance();if(!StandManager.kageno(mc.player))return false;
        if(skillInput()&&mc.world.getTime()>=nextPunch){BotandCamera.prepareInteraction();send(M1);nextPunch=mc.world.getTime()+1;}
        return true;
    }
    /** The pose holds the body still but not the keys that end it: P again or any skill (the server releases the pose first). */
    private static boolean skillInput(){
        if(CombatClient.combatInput())return true;
        var mc=MinecraftClient.getInstance();if(mc.player==null||mc.world==null)return false;var c=action(mc.player.getUuid());
        return c!=null&&c.skill()==P&&!frozen(mc.player)&&CombatClient.connected()&&mc.currentScreen==null&&!CinematicDirector.isActive()&&mc.player.isAlive()&&!mc.player.isSpectator()
            &&!HitStunClient.localStunned()&&!CombatClient.snapshot(mc.player.getUuid()).broken(mc.world.getTime());
    }
    /** Item-use is independent of the stand keys; a held fruit/stone never determines ownership. */
    public static boolean useSkill(){return false;}
    public static boolean key(long window,int key,int action,int modifiers){
        var mc=MinecraftClient.getInstance();if(window!=mc.getWindow().getHandle())return false;
        if(action==GLFW.GLFW_RELEASE&&captured.remove(key)){if(key==GLFW.GLFW_KEY_T){if(holdingT)send(T_UP);holdingT=false;}return true;}
        if(captured.contains(key))return true;
        if(mc.currentScreen!=null||!StandManager.kageno(mc.player)||key==GLFW.GLFW_KEY_Q&&(modifiers&GLFW.GLFW_MOD_CONTROL)!=0||key==GLFW.GLFW_KEY_G&&(modifiers&GLFW.GLFW_MOD_ALT)!=0)return false;
        if(action!=GLFW.GLFW_PRESS)return false;
        int skill=switch(key){case GLFW.GLFW_KEY_E->E;case GLFW.GLFW_KEY_R->R;case GLFW.GLFW_KEY_T->T_DOWN;case GLFW.GLFW_KEY_Y->Y;case GLFW.GLFW_KEY_Q->Q;case GLFW.GLFW_KEY_H->H;case GLFW.GLFW_KEY_G->G;case GLFW.GLFW_KEY_P->POSE;default->-1;};
        if(skill<0){
            if(!CombatClient.actionLocked()&&key==GLFW.GLFW_KEY_I){mc.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(mc.player));return true;}
            if(key==GLFW.GLFW_KEY_ENTER){mc.setScreen(new net.minecraft.client.gui.screen.ChatScreen(""));return true;}
            return false;
        }
        captured.add(key);if(skillInput()){BotandCamera.prepareInteraction();send(skill);if(skill==T_DOWN)holdingT=true;}
        return true;
    }
    public static float cameraBlend(float delta){var c=cinematic();return c==null?0:dev.sangwoo.celestialfall.client.kageno.KagenoCamera.blend(c,delta);}
    public static double cameraFov(float delta){var c=cinematic();return c==null?60:dev.sangwoo.celestialfall.client.kageno.KagenoCamera.fov(c,delta);}
    public static CinematicDirector.Pose camera(float delta){var c=cinematic();return c==null?null:dev.sangwoo.celestialfall.client.kageno.KagenoCamera.pose(c,delta);}
}
