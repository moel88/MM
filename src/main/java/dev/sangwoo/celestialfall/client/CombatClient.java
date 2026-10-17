package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.combat.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public final class CombatClient {
    public record Snapshot(int serial,int attack,long attackStart,boolean guard,float pressure,long brokenUntil,long dashStart,long dashReady,float forward,float side,long combatUntil) {
        public boolean broken(double now){return now<brokenUntil;}
        public float attackAge(double now){return (float)(now-attackStart);}
        public float dashAge(double now){return (float)(now-dashStart);}
    }
    public static final Snapshot EMPTY=new Snapshot(0,0,-1000,false,0,0,-1000,0,1,0,0);
    private static final Map<UUID,Snapshot> STATES=new HashMap<>();
    private static final Map<UUID,Movement> MOVEMENT=new HashMap<>();
    private static final class Movement {
        double x,y,z;float phase,previousPhase,amount,previousAmount,forward,side,sprint,combat,guard;
        boolean ready,grounded=true;float smoothPressure;
        /** Vertical speed (blocks per tick), how airborne the body looks (0..1), the fastest fall since leaving the ground. */
        float vy,previousVy,air,previousAir,fall;
        /** World tick of the last landing and how hard it was (0..1). */
        long landAt=-1000;float landPower;
    }
    private static KeyBinding DASH,GUARD;
    private static final DashInput DASH_INPUT=new DashInput();
    private static boolean guardHeld;
    private static int ticks,lastPunch=-100;
    private static Object world;
    private CombatClient() {}
    public static void initialize() {
        HitStunClient.initialize();
        BotandCamera.initialize();
        DASH=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.botand.dash",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_C,"category.botand"));
        GUARD=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.botand.guard",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_F,"category.botand"));
        ClientPlayNetworking.registerGlobalReceiver(CombatManager.STATE,(mc,handler,b,response)->{
            UUID id=b.readUuid();var dimension=b.readIdentifier();
            Snapshot s=new Snapshot(b.readInt(),b.readUnsignedByte(),b.readLong(),b.readBoolean(),b.readFloat(),b.readLong(),b.readLong(),b.readLong(),b.readFloat(),b.readFloat(),b.readLong());
            mc.execute(()->{if(MapClient.enabled()&&mc.world!=null&&mc.world.getRegistryKey().getValue().equals(dimension))STATES.put(id,s);});
        });
        ClientTickEvents.END_CLIENT_TICK.register(CombatClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->clear());
        HudRenderCallback.EVENT.register(CombatClient::hud);
    }
    public static void clear(){STATES.clear();MOVEMENT.clear();DASH_INPUT.clear();guardHeld=false;world=null;PoseBlend.clear();}
    public static void dashKey(long window,int key,int scanCode,int action) {
        var mc=MinecraftClient.getInstance();
        if(DASH==null||window!=mc.getWindow().getHandle()||!DASH.matchesKey(key,scanCode))return;
        if(action==GLFW.GLFW_PRESS||action==GLFW.GLFW_RELEASE)
            DASH_INPUT.key(action==GLFW.GLFW_PRESS,combatInput());
    }
    public static Snapshot snapshot(UUID id){return STATES.getOrDefault(id,EMPTY);}
    public static boolean connected(){return MapClient.enabled()&&ClientPlayNetworking.canSend(CombatManager.INPUT);}
    public static boolean actionLocked(){var mc=MinecraftClient.getInstance();return MapClient.enabled()&&(StandClient.locked()||HitStunClient.localStunned()||mc.player!=null&&mc.world!=null&&snapshot(mc.player.getUuid()).broken(mc.world.getTime()));}
    /** The articulated rig drives this player (on the ground and in a jump or fall; not for sneaking, swimming, gliding, sleeping or riding). */
    public static boolean animate(AbstractClientPlayerEntity p) {
        return MapClient.enabled()&&p.isAlive()&&!p.isSpectator()&&!p.isSneaking()&&!p.isInSneakingPose()&&!p.isSwimming()&&!p.isFallFlying()&&!p.isSleeping()&&!p.hasVehicle()
            &&(p.isOnGround()||!p.isClimbing()&&!p.isTouchingWater())&&AtomicClient.actor(p.getUuid())==null&&HeavenClient.actor(p.getUuid())==null;
    }
    public static boolean combatInput() {
        if(dev.sangwoo.celestialfall.client.integration.CinematicsBridge.active())return false;
        var mc=MinecraftClient.getInstance();return mc.player!=null&&mc.world!=null&&connected()&&mc.currentScreen==null&&!CinematicDirector.isActive()&&!actionLocked()&&mc.player.isAlive()&&!mc.player.isSpectator();
    }
    public static boolean punch() {
        var mc=MinecraftClient.getInstance();
        if(!combatInput()||!CombatManager.bare(mc.player))return false;
        if(!HitStunClient.aimCapturedTarget()&&mc.crosshairTarget!=null&&mc.crosshairTarget.getType()==HitResult.Type.BLOCK)return false;
        if(ticks-lastPunch>=3) {
            BotandCamera.aim();
            // Rotation is sent before the custom attack so its contact uses the same aim.
            mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(mc.player.getYaw(),mc.player.getPitch(),mc.player.isOnGround()));
            send(0,0,0);lastPunch=ticks;
        }
        return true;
    }
    private static void send(int action,int a,int b) {if(!connected())return;var buffer=PacketByteBufs.create();buffer.writeByte(action);buffer.writeByte(a);buffer.writeByte(b);ClientPlayNetworking.send(CombatManager.INPUT,buffer);}
    private static void tick(MinecraftClient mc) {
        if(!MapClient.enabled()){clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        ticks++;
        boolean pressed=combatInput()&&GUARD.isPressed();
        if(pressed!=guardHeld||pressed&&ticks%5==0) {
            if(pressed){BotandCamera.aim();sendLook();}
            send(pressed?1:2,0,0);
        }
        guardHeld=pressed;
        boolean dashQueued=false;while(DASH.wasPressed())dashQueued=true;
        if(DASH_INPUT.consume(DASH.isPressed(),dashQueued,combatInput()&&!mc.isPaused())) {
            if(BotandCamera.controls()) {
                BotandCamera.faceCamera();sendLook();
                send(3,(mc.options.forwardKey.isPressed()?1:0)-(mc.options.backKey.isPressed()?1:0),(mc.options.leftKey.isPressed()?1:0)-(mc.options.rightKey.isPressed()?1:0));
            } else send(3,(int)Math.signum(mc.player.input.movementForward),(int)Math.signum(mc.player.input.movementSideways));
        }
        if(combatInput()&&mc.options.attackKey.isPressed()&&ticks-lastPunch>=4)punch();
        if(mc.isPaused())return;
        long now=mc.world.getTime();Set<UUID> alive=new HashSet<>();
        for(var p:mc.world.getPlayers()) {
            alive.add(p.getUuid());Movement m=MOVEMENT.computeIfAbsent(p.getUuid(),id->new Movement());
            double dx=m.ready?p.getX()-m.x:0,dz=m.ready?p.getZ()-m.z:0,dy=m.ready?p.getY()-m.y:0;
            m.x=p.getX();m.y=p.getY();m.z=p.getZ();m.ready=true;
            // Airborne look and landings: a landing after a real fall dips the body, harder for a faster fall.
            boolean ground=p.isOnGround();
            m.previousVy=m.vy;m.vy=Math.abs(dy)>3?0:(float)dy;m.previousAir=m.air;m.air=approach(m.air,ground?0:1,.5f);
            if(!ground)m.fall=Math.min(m.fall,m.vy);
            else{if(!m.grounded&&m.fall<-.3f){m.landAt=now;m.landPower=Math.min(1,.35f+(-m.fall-.3f)/.6f);}m.fall=0;}
            m.grounded=ground;
            double distance=Math.hypot(dx,dz);if(distance>1.5)distance=0;
            double yaw=Math.toRadians(p.getYaw()),f=-Math.sin(yaw)*dx+Math.cos(yaw)*dz,side=Math.cos(yaw)*dx+Math.sin(yaw)*dz;
            m.previousPhase=m.phase;m.previousAmount=m.amount;
            float speed=(float)Math.min(1,distance/.2);
            m.amount=approach(m.amount,p.isOnGround()?speed:0,.35f);
            if(distance>.005) {m.forward=approach(m.forward,(float)(f/Math.max(distance,.001)),.35f);m.side=approach(m.side,(float)(side/Math.max(distance,.001)),.35f);}
            m.sprint=approach(m.sprint,p.isSprinting()?1:0,.25f);
            if(p.isOnGround())m.phase+=(float)(distance/(1.7+.6*m.sprint)*Math.PI*2);
            Snapshot s=snapshot(p.getUuid());m.combat=approach(m.combat,now<s.combatUntil?1:0,.18f);m.guard=approach(m.guard,s.guard?1:0,.45f);
            m.smoothPressure=approach(m.smoothPressure,s.pressure,.25f);
            if(animate(p)) {p.prevBodyYaw=p.bodyYaw;p.bodyYaw=MathHelper.lerpAngleDegrees(.4f,p.bodyYaw,p.getYaw());}
        }
        if(ticks%100==0){MOVEMENT.keySet().retainAll(alive);STATES.keySet().retainAll(alive);}
    }
    private static float approach(float a,float b,float step){return MathHelper.lerp(step,a,b);}
    private static void sendLook(){var mc=MinecraftClient.getInstance();mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(mc.player.getYaw(),mc.player.getPitch(),mc.player.isOnGround()));}
    /** Which part of the articulated motion plays now (a new value for every hit, dash and punch), so the model can crossfade between them. */
    public static Object state(AbstractClientPlayerEntity p,float delta) {
        Snapshot s=snapshot(p.getUuid());double now=p.getWorld().getTime()+delta;
        var reaction=HitStunClient.reaction(p);
        if(reaction!=null)return "hit"+reaction.hitAt();
        if(s.broken(now))return "broken";
        if(s.dashAge(now)>=0&&s.dashAge(now)<CombatState.DASH_TICKS)return "dash"+s.dashStart();
        if(s.attack>0&&s.attack<=4&&s.attackAge(now)>=0&&s.attackAge(now)<CombatState.DURATION[s.attack])return "attack"+s.attackStart();
        return "base";
    }
    /** Ticks to fade into a state: a flinch lands at once, a punch or dash quickly, anything else gently. */
    public static float fade(Object state) {
        String s=String.valueOf(state);
        return s.startsWith("hit")?1.2f:s.startsWith("dash")||s.startsWith("attack")?1.6f:3;
    }
    public static MotionPose pose(AbstractClientPlayerEntity p,float delta) {
        Movement m=MOVEMENT.get(p.getUuid());Snapshot s=snapshot(p.getUuid());double now=p.getWorld().getTime()+delta;
        if(m==null)m=new Movement();
        var reaction=HitStunClient.reaction(p);
        if(reaction!=null)return MotionClips.hitReaction((float)(now-reaction.hitAt()),reaction.strike());
        MotionPose base=MotionClips.locomotion(MathHelper.lerp(delta,m.previousPhase,m.phase),MathHelper.lerp(delta,m.previousAmount,m.amount),m.forward,m.side,m.sprint,m.combat,p.age+delta);
        // In the air the knees draw up while rising and the legs reach down while falling; a landing dips the hips.
        float air=MathHelper.lerp(delta,m.previousAir,m.air);
        if(air>.001f)base=MotionPose.blend(base,MotionClips.air(MathHelper.lerp(delta,m.previousVy,m.vy)/.42f,p.age+delta),air);
        MotionClips.land(base,m.landPower*MotionClips.dip((float)(now-m.landAt)));
        // Any blow makes the body flinch away from it for a moment.
        if(p.hurtTime>0&&p.maxHurtTime>0)MotionClips.flinch(base,MotionClips.recoil(p.maxHurtTime-p.hurtTime+delta),(float)Math.sin(Math.toRadians(p.getDamageTiltYaw()))*.7f);
        if(s.broken(now)) {
            MotionPose broken=MotionClips.broken((float)(now-s.brokenUntil+20));
            // The staggered upper body remains visible while the slowed feet still take steps.
            for(int i=4;i<6;i++)for(int j=0;j<3;j++)broken.joint[i][j]=MathHelper.lerp(m.amount,broken.joint[i][j],base.joint[i][j]);
            for(int i=0;i<2;i++)broken.knee[i]=MathHelper.lerp(m.amount,broken.knee[i],base.knee[i]);
            return broken;
        }
        if(s.dashAge(now)>=0&&s.dashAge(now)<CombatState.DASH_TICKS)return MotionClips.dash(s.dashAge(now),s.forward,s.side);
        if(s.attack>0&&s.attack<=4&&s.attackAge(now)>=0&&s.attackAge(now)<CombatState.DURATION[s.attack]) {
            MotionPose attack=MotionClips.attack(s.attack,s.attackAge(now));
            // Keep the moving feet underneath an upper-body punch rather than skating in a fixed stance.
            for(int i=4;i<6;i++)for(int j=0;j<3;j++)attack.joint[i][j]=MathHelper.lerp(m.amount*.7f,attack.joint[i][j],base.joint[i][j]);
            for(int i=0;i<2;i++)attack.knee[i]=MathHelper.lerp(m.amount*.7f,attack.knee[i],base.knee[i]);
            return MotionPose.blend(base,attack,MotionClips.smooth(s.attackAge(now)/2));
        }
        if(m.guard>.01f)return MotionPose.blend(base,MotionClips.guard(p.age+delta,s.pressure),m.guard);
        return base;
    }
    public static void hud(DrawContext c,float delta) {
        var mc=MinecraftClient.getInstance();if(!MapClient.enabled()||mc.player==null||mc.world==null||mc.options.hudHidden||CinematicDirector.isActive()||mc.player.isSpectator())return;
        Snapshot s=snapshot(mc.player.getUuid());double now=mc.world.getTime()+delta;
        if(!s.guard&&s.pressure<=0&&now>=s.combatUntil&&now>=s.dashReady)return;
        int x=c.getScaledWindowWidth()/2,y=c.getScaledWindowHeight()-72;boolean broken=s.broken(now);
        int color=broken?0xffff566b:s.pressure>70?0xffffb454:0xff7cdce8;
        var m=MOVEMENT.get(mc.player.getUuid());float value=m==null?s.pressure:m.smoothPressure;
        if(!BotandHud.enabled()) {
        c.fill(x-62,y-2,x+62,y+10,0xb0111722);c.fill(x-60,y,x+60,y+6,0xff303a48);
        c.fill(x-60,y,x-60+(int)(120*MathHelper.clamp(value/100,0,1)),y+6,color);
        for(int i=1;i<4;i++)c.fill(x-60+i*30,y,x-59+i*30,y+6,0xaa111722);
        c.drawCenteredTextWithShadow(mc.textRenderer,Text.translatable(broken?"hud.botand.break":"hud.botand.guard"),x,y-13,color);
        }
        int dash=(int)(100*MathHelper.clamp(1-(s.dashReady-now)/CombatState.DASH_COOLDOWN,0,1));
        c.fill(x+71,y-2,x+104,y+10,0xb0111722);c.fill(x+73,y+6,x+73+dash*29/100,y+8,0xffa99aff);
        c.drawTextWithShadow(mc.textRenderer,Text.literal("C"),x+84,y-2,0xffdcd5ff);
        if(s.attack>0&&s.attack<=4) {
            for(int i=1;i<=4;i++)c.fill(x-22+(i-1)*13,y+15,x-14+(i-1)*13,y+18,i<=s.attack?0xffffd890:0xff3e4654);
        }
    }
}
