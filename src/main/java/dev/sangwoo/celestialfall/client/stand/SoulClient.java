package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.BotandCamera;
import dev.sangwoo.celestialfall.client.CinematicDirector;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.combat.CombatState;
import dev.sangwoo.celestialfall.combat.MotionClips;
import dev.sangwoo.celestialfall.combat.MotionPose;
import dev.sangwoo.celestialfall.stand.GoldExperienceRules;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The soul out of a body Gold Experience's overdrive punch landed on (2.148.0, user request: "적은 유체이탈이 돼 그
 * 상태에서는 속도가 2배가 평타도 꽂을 수 있는데 평타가 통과가 되고 유체이탈 상태에서는 아무에게도 안 보이고 자신에게만
 * 보여 ... 그동안 본체는 아무것도 못하고 맞고만 있어야해"). The server holds the body: incapacitated where it stands, all
 * harm to it doubled, and it tells everyone near whose soul is out and until when (GoldExperienceOverdrive). The soul
 * is nothing but what the struck player's own client shows them, here: a see-through copy of their body that their
 * keys walk about (SoulWalk: twice as fast, jumping, held by the world's blocks), their camera with it, throwing the
 * bare fists' blows at their attack key, which pass through everything (nothing is sent: no one else sees it and it
 * touches nothing). Their body stays in the world where the blow left it, dazed, drawn for them too (in the first
 * person as well, where their own body is otherwise not). As the time is up the soul flies back into the body and the
 * camera with it. While a cutscene has their screen the soul waits where it is.
 * <p>
 * For everyone: which bodies are soulless now (their daze's stars are golden, StandPunchFx; the numbers of the harm
 * dealt to them are doubled, HitFeedback).
 */
public final class SoulClient {
    /** Ticks the soul takes to come out of the body as it begins (it fades in) and to fly back into it at the end. */
    static final int OUT=5,BACK=6;
    /**
     * How see-through the soul's own skin is and its tint (it is lit from within: no shade on it), and the pale blue
     * laid over all of it, how strong (the first take had only the skin, at .62: a dark skin read as a second body, not
     * as a soul).
     */
    static final float ALPHA=.55f,RED=.78f,GREEN=.9f,BLUE=1f,VEIL=.24f;
    static final int PALE=0xbcdcff;
    /** The pale veil is drawn this much bigger than the body about its middle (so it lies over the skin, not in it). */
    static final float SWELL=1.03f;
    /** A soul's stride is this much longer than a body's (it goes twice as fast: its legs do not quite). */
    static final float STRIDE=1.45f;
    /** Half its width and its height (a player's). */
    static final double HALF=.3,HEIGHT=1.8,STEP=.6;
    private static final Map<Integer,Long> SOULLESS=new HashMap<>();
    private static Soul soul;
    private static Object world;
    private static long ticks;
    private static boolean drawing;
    private SoulClient(){}

    private static final class Soul {
        long until;final long began;
        SoulWalk.Step at;Vec3d prev;float yaw,prevYaw;
        /** Its walk as the body's is kept (CombatClient.Movement): the cycle's phase, how much it walks, which way, running, in the air. */
        float phase,prevPhase,amount,prevAmount,forward=1,side,sprint,air,prevAir,vy,prevVy;
        /** Its blows: which of the four, the tick it began, another asked for. */
        int combo;long attackAt=-1000;boolean queued;
        /** The keys this tick. */
        float keyForward,keyLeft,camera;boolean jump,run,locked,steered;
        /** Flying back into the body: since which tick, from where. */
        long backAt=-1;Vec3d backFrom;
        Soul(Vec3d feet,float yaw,long until){at=new SoulWalk.Step(feet,Vec3d.ZERO,true);prev=feet;this.yaw=prevYaw=yaw;this.until=until;began=ticks;}
    }

    public static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(SoulClient::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(SoulClient::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(SoulClient::clear));
    }
    static void clear(){SOULLESS.clear();soul=null;world=null;}

    /** The server's word: entity {@code id}'s soul is out of its body until {@code until} (or back in it). */
    static void told(MinecraftClient mc,boolean out,int id,long now,long until){
        if(world!=mc.world){clear();world=mc.world;}
        boolean mine=mc.player!=null&&mc.player.getId()==id;
        if(!out){
            SOULLESS.remove(id);
            if(mine&&soul!=null&&soul.backAt<0){soul.backAt=ticks;soul.backFrom=soul.at.feet();}
            return;
        }
        SOULLESS.put(id,until);
        if(!mine)return;
        if(soul==null)soul=new Soul(mc.player.getPos(),mc.player.getYaw(),until);
        else{
            // struck again while out (or flying back): it stays out, from where it is
            if(soul.backAt>=0){soul.at=new SoulWalk.Step(place(mc,1),Vec3d.ZERO,false);soul.prev=soul.at.feet();soul.backAt=-1;}
            soul.until=until;
        }
    }
    /** Whether this entity's soul is out of its body now, as far as this client knows. */
    public static boolean soulless(Entity e){
        if(e==null||SOULLESS.isEmpty())return false;
        Long until=SOULLESS.get(e.getId());return until!=null&&e.getWorld().getTime()<until;
    }
    /** What the harm dealt to {@code e} is multiplied by (the number its dealer sees is what went in). */
    public static float taken(Entity e){return soulless(e)?GoldExperienceRules.OVER_TAKEN:1;}
    /** Whether the local player's soul is out of their body. */
    public static boolean out(){return soul!=null;}
    /** Whether {@code e} is being drawn as its own soul now (its renderer's hooks for the body stand aside, CloneClient.doubling). */
    public static boolean drawing(Entity e){return drawing&&e==MinecraftClient.getInstance().player;}
    /** For the checks: the local soul as "x,y,z,yaw,speed,combo,back" (blocks; blocks a tick), or null. */
    public static String describe(){
        var s=soul;if(s==null)return null;
        return String.format(java.util.Locale.ROOT,"%.2f,%.2f,%.2f,%.0f,%.3f,%d,%d",s.at.feet().x,s.at.feet().y,s.at.feet().z,MathHelper.wrapDegrees(s.yaw),s.at.feet().subtract(s.prev).horizontalLength(),s.combo,s.backAt>=0?1:0);
    }

    // ───────────────────────────── its keys ─────────────────────────────

    /**
     * The movement keys this tick, while the soul is out: they are the soul's (the body's are none). {@code camera} is
     * the way the camera looks, {@code locked} whether the body would face it (the shift lock, the first person),
     * {@code walking} whether the keys work at all now (no cutscene). Returns whether it took them.
     */
    public static boolean steer(float forward,float left,boolean jump,float camera,boolean locked,boolean run,boolean walking){
        var s=soul;if(s==null)return false;
        s.steered=walking&&s.backAt<0;
        s.keyForward=s.steered?forward:0;s.keyLeft=s.steered?left:0;s.jump=s.steered&&jump;s.run=run;s.camera=camera;s.locked=locked;
        return true;
    }
    /**
     * The attack key, while the soul is out: its bare fists' next blow, which passes through everything (nothing is
     * sent). Returns whether the soul took the press.
     */
    public static boolean attack(){
        var s=soul;if(s==null)return false;
        if(s.backAt>=0||!BotandCamera.controls())return true;
        long age=ticks-s.attackAt;
        if(s.combo>0&&age<CombatState.DURATION[s.combo]){if(s.combo<4&&age>=CombatState.DURATION[s.combo]-6)s.queued=true;return true;}
        blow(s);
        return true;
    }
    private static void blow(Soul s){
        long age=ticks-s.attackAt;
        s.combo=s.combo>0&&s.combo<4&&age<CombatState.DURATION[s.combo]+14?s.combo+1:1;s.attackAt=ticks;s.queued=false;
        var mc=MinecraftClient.getInstance();
        if(mc.player==null)return;
        var at=place(mc,1);
        // heard by them alone, where the soul is: a fist through the air
        mc.world.playSound(at.x,at.y+1.2,at.z,SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE,SoundCategory.PLAYERS,.5f,s.combo==4?.8f:1.05f,false);
        // their own eyes see the arm go (the first person): the game's swing, on this client alone
        if(BotandCamera.STATE.firstPerson())((net.minecraft.entity.LivingEntity)mc.player).swingHand(Hand.MAIN_HAND,false);
    }

    private static void tick(MinecraftClient mc){
        if(mc.world!=world){clear();world=mc.world;}
        if(mc.world==null||mc.isPaused())return;
        ticks++;
        long now=mc.world.getTime();
        SOULLESS.values().removeIf(until->now>=until);
        var s=soul;if(s==null)return;
        var p=mc.player;
        if(p==null||!p.isAlive()||!MapClient.enabled()||p.isSpectator()){soul=null;return;}
        s.prev=s.at.feet();s.prevYaw=s.yaw;s.prevPhase=s.phase;s.prevAmount=s.amount;s.prevAir=s.air;s.prevVy=s.vy;
        if(s.backAt<0&&now>=s.until){s.backAt=ticks;s.backFrom=s.at.feet();}
        if(s.backAt>=0){
            if(ticks-s.backAt>=BACK)soul=null;
            return;
        }
        if(s.queued&&ticks-s.attackAt>=CombatState.DURATION[s.combo])blow(s);
        boolean striking=s.combo>0&&ticks-s.attackAt<CombatState.DURATION[s.combo];
        float f=s.steered?s.keyForward:0,l=s.steered?s.keyLeft:0;
        s.at=SoulWalk.step(s.at,f,l,s.jump,s.run&&f>0,s.camera,(feet,want)->move(mc,feet,want));
        // fallen out of the world: back to the body
        if(s.at.feet().y<mc.world.getBottomY()-16){s.at=new SoulWalk.Step(p.getPos(),Vec3d.ZERO,false);s.prev=s.at.feet();}
        // it faces as a body does: the way the camera looks in the shift lock and as it strikes, else the way it walks
        if(s.locked||striking)s.yaw=s.camera;
        else if(Math.hypot(f,l)>.01)s.yaw+=MathHelper.clamp(MathHelper.wrapDegrees(s.camera-(float)Math.toDegrees(Math.atan2(l,f))-s.yaw),-36,36);
        double dx=s.at.feet().x-s.prev.x,dz=s.at.feet().z-s.prev.z,dy=s.at.feet().y-s.prev.y,distance=Math.hypot(dx,dz);
        double yaw=Math.toRadians(s.yaw),ahead=-Math.sin(yaw)*dx+Math.cos(yaw)*dz,side=Math.cos(yaw)*dx+Math.sin(yaw)*dz;
        float speed=(float)Math.min(1,distance/.2);boolean ground=s.at.ground();
        s.amount=MathHelper.lerp(.35f,s.amount,ground?speed:0);
        if(distance>.005){s.forward=MathHelper.lerp(.35f,s.forward,(float)(ahead/distance));s.side=MathHelper.lerp(.35f,s.side,(float)(side/distance));}
        s.sprint=MathHelper.lerp(.25f,s.sprint,s.run&&f>0?1:0);
        if(ground)s.phase+=(float)(distance/((1.7+.6*s.sprint)*STRIDE)*Math.PI*2);
        s.air=MathHelper.lerp(.5f,s.air,ground?0:1);s.vy=(float)dy;
    }
    /** How much of a move of {@code want} a soul with its feet at {@code feet} can make: the world's blocks hold it, and it steps up what a body steps up. */
    private static Vec3d move(MinecraftClient mc,Vec3d feet,Vec3d want){
        Box box=new Box(feet.x-HALF,feet.y,feet.z-HALF,feet.x+HALF,feet.y+HEIGHT,feet.z+HALF);
        Vec3d got=Entity.adjustMovementForCollisions(null,want,box,mc.world,List.of());
        boolean blocked=got.x!=want.x||got.z!=want.z,down=want.y<0&&got.y!=want.y;
        if(blocked&&down){
            Vec3d up=Entity.adjustMovementForCollisions(null,new Vec3d(0,STEP,0),box,mc.world,List.of());
            Vec3d over=Entity.adjustMovementForCollisions(null,new Vec3d(want.x,0,want.z),box.offset(up),mc.world,List.of());
            Vec3d settle=Entity.adjustMovementForCollisions(null,new Vec3d(0,-up.y+want.y,0),box.offset(up).offset(over),mc.world,List.of());
            Vec3d stepped=up.add(over).add(settle);
            if(stepped.horizontalLengthSquared()>got.horizontalLengthSquared())return stepped;
        }
        return got;
    }

    // ───────────────────────────── where it is ─────────────────────────────

    /** Where the soul's feet are this frame: where it walks, or on its way back into the body. */
    private static Vec3d place(MinecraftClient mc,float delta){
        var s=soul;
        if(s.backAt>=0){
            float k=MathHelper.clamp((ticks-s.backAt+delta)/BACK,0,1);k=k*k*(3-2*k);
            return s.backFrom.lerp(mc.player.getLerpedPos(delta),k);
        }
        return s.prev.lerp(s.at.feet(),MathHelper.clamp(delta,0,1));
    }
    /** How much of the soul shows this frame (0 to 1): fading in as it comes out of the body, out as it goes back into it. */
    static float shown(float delta){
        var s=soul;if(s==null)return 0;
        float in=MathHelper.clamp((ticks-s.began+delta)/OUT,0,1);
        float back=s.backAt<0?0:MathHelper.clamp((ticks-s.backAt+delta)/BACK,0,1);
        return in*in*(3-2*in)*(1-back*back);
    }
    /** The soul's feet and its body's this frame, for the light between them (null if none is out). */
    static Vec3d[] ends(float delta){
        var mc=MinecraftClient.getInstance();
        if(soul==null||mc.player==null)return null;
        return new Vec3d[]{place(mc,delta),mc.player.getLerpedPos(delta)};
    }
    /**
     * Where the local player's camera is off their body this frame (their soul's place less their body's), or null:
     * the camera goes with the soul, in the first person too (they look out of its eyes).
     */
    public static Vec3d offset(float delta){
        var mc=MinecraftClient.getInstance();
        if(soul==null||mc.player==null)return null;
        return place(mc,delta).subtract(mc.player.getLerpedPos(delta));
    }
    /** The soul's limbs this frame (the body's own walk, jump and bare fists' blows, on the soul's own clocks). */
    public static MotionPose pose(float delta){
        var s=soul;if(s==null)return null;
        float d=MathHelper.clamp(delta,0,1),time=ticks-s.began+d,amount=MathHelper.lerp(d,s.prevAmount,s.amount);
        MotionPose base=MotionClips.locomotion(MathHelper.lerp(d,s.prevPhase,s.phase),amount,s.forward,s.side,s.sprint,0,time);
        float air=MathHelper.lerp(d,s.prevAir,s.air);
        if(air>.001f)base=MotionPose.blend(base,MotionClips.air(MathHelper.lerp(d,s.prevVy,s.vy)/.42f,time),air);
        float age=ticks-s.attackAt+d;
        if(s.combo>0&&age<CombatState.DURATION[s.combo]){
            MotionPose attack=MotionClips.attack(s.combo,age);
            for(int i=4;i<6;i++)for(int j=0;j<3;j++)attack.joint[i][j]=MathHelper.lerp(amount*.7f,attack.joint[i][j],base.joint[i][j]);
            for(int i=0;i<2;i++)attack.knee[i]=MathHelper.lerp(amount*.7f,attack.knee[i],base.knee[i]);
            return MotionPose.blend(base,attack,MotionClips.smooth(age/2));
        }
        return base;
    }

    // ───────────────────────────── drawing ─────────────────────────────

    /** Every layer but the body's own skin goes nowhere, and the skin is drawn see-through, pale and lit from within: a soul. */
    private static final class Veil implements VertexConsumerProvider {
        final VertexConsumerProvider to;final RenderLayer skin;final float alpha;
        final RenderLayer lit;final boolean flat;
        Veil(VertexConsumerProvider to,Identifier skin,float alpha,boolean flat){this.to=to;this.skin=RenderLayer.getEntityTranslucent(skin);lit=RenderLayer.getEntityTranslucentEmissive(skin);this.alpha=alpha;this.flat=flat;}
        @Override public VertexConsumer getBuffer(RenderLayer layer){
            if(layer!=skin)return NOTHING;
            return flat?new Flat(to.getBuffer(RenderLayer.getDebugQuads()),alpha):new Tint(to.getBuffer(lit),alpha);
        }
    }
    /** The body's shape in the one pale colour (no texture, no light). */
    private static final class Flat implements VertexConsumer {
        final VertexConsumer to;final int a;double x,y,z;
        Flat(VertexConsumer to,float alpha){this.to=to;a=Math.max(0,Math.min(255,Math.round(alpha*255)));}
        @Override public VertexConsumer vertex(double x,double y,double z){this.x=x;this.y=y;this.z=z;return this;}
        @Override public VertexConsumer color(int r,int g,int b,int al){return this;}
        @Override public VertexConsumer texture(float u,float v){return this;}
        @Override public VertexConsumer overlay(int u,int v){return this;}
        @Override public VertexConsumer light(int u,int v){return this;}
        @Override public VertexConsumer normal(float nx,float ny,float nz){return this;}
        @Override public void next(){to.vertex(x,y,z).color(PALE>>16&255,PALE>>8&255,PALE&255,a).next();}
        @Override public void fixedColor(int r,int g,int b,int al){}
        @Override public void unfixColor(){}
    }
    private static final class Tint implements VertexConsumer {
        final VertexConsumer to;final float alpha;
        Tint(VertexConsumer to,float alpha){this.to=to;this.alpha=alpha;}
        @Override public VertexConsumer vertex(double x,double y,double z){to.vertex(x,y,z);return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){to.color(Math.round(r*RED),Math.round(g*GREEN),Math.round(b*BLUE),Math.round(a*alpha));return this;}
        @Override public VertexConsumer texture(float u,float v){to.texture(u,v);return this;}
        @Override public VertexConsumer overlay(int u,int v){to.overlay(u,v);return this;}
        @Override public VertexConsumer light(int u,int v){to.light(240,240);return this;}
        @Override public VertexConsumer normal(float x,float y,float z){to.normal(x,y,z);return this;}
        @Override public void next(){to.next();}
        @Override public void fixedColor(int r,int g,int b,int a){}
        @Override public void unfixColor(){}
    }
    private static final VertexConsumer NOTHING=new VertexConsumer(){
        @Override public VertexConsumer vertex(double x,double y,double z){return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){return this;}
        @Override public VertexConsumer texture(float u,float v){return this;}
        @Override public VertexConsumer overlay(int u,int v){return this;}
        @Override public VertexConsumer light(int u,int v){return this;}
        @Override public VertexConsumer normal(float x,float y,float z){return this;}
        @Override public void next(){}
        @Override public void fixedColor(int r,int g,int b,int a){}
        @Override public void unfixColor(){}
    };
    /**
     * Draws the soul (the local player again through their own renderer, where it walks, in its own pose, see-through),
     * and in the first person their body where it stands (the game draws no body for its own eyes).
     */
    private static void render(WorldRenderContext ctx){
        var mc=MinecraftClient.getInstance();var s=soul;var p=mc.player;
        if(s==null||p==null||mc.world==null||ctx.consumers()==null||CinematicDirector.isActive())return;
        float delta=ctx.tickDelta();Vec3d cam=ctx.camera().getPos();var dispatcher=mc.getEntityRenderDispatcher();
        float swing=p.handSwingProgress,lastSwing=p.lastHandSwingProgress;
        if(!ctx.camera().isThirdPerson()){
            Vec3d at=p.getLerpedPos(delta);
            p.handSwingProgress=p.lastHandSwingProgress=0;
            try{dispatcher.render(p,at.x-cam.x,at.y-cam.y,at.z-cam.z,MathHelper.lerp(delta,p.prevYaw,p.getYaw()),delta,ctx.matrixStack(),ctx.consumers(),dispatcher.getLight(p,delta));}
            finally{p.handSwingProgress=swing;p.lastHandSwingProgress=lastSwing;}
            return;
        }
        float show=shown(delta);
        if(show<.02f||BotandCamera.STATE.collisionDistance<.7)return;
        Vec3d at=place(mc,delta);float face=MathHelper.lerpAngleDegrees(MathHelper.clamp(delta,0,1),s.prevYaw,s.yaw);
        float by=p.bodyYaw,pby=p.prevBodyYaw,hy=p.headYaw,phy=p.prevHeadYaw,y=p.getYaw(),py=p.prevYaw,pt=p.getPitch(),ppt=p.prevPitch;int hurt=p.hurtTime;
        p.bodyYaw=p.prevBodyYaw=face;p.headYaw=p.prevHeadYaw=face;p.setYaw(face);p.prevYaw=face;p.setPitch(0);p.prevPitch=0;p.hurtTime=0;
        p.handSwingProgress=p.lastHandSwingProgress=0;
        drawing=true;dispatcher.setRenderShadows(false);
        try{
            dispatcher.render(p,at.x-cam.x,at.y-cam.y,at.z-cam.z,face,delta,ctx.matrixStack(),new Veil(ctx.consumers(),p.getSkinTexture(),ALPHA*show,false),0xF000F0);
            // and the pale veil over it, a little bigger about its middle
            var m=ctx.matrixStack();double half=p.getHeight()*.5;
            m.push();m.translate(at.x-cam.x,at.y-cam.y+half,at.z-cam.z);m.scale(SWELL,SWELL,SWELL);m.translate(0,-half,0);
            try{dispatcher.render(p,0,0,0,face,delta,m,new Veil(ctx.consumers(),p.getSkinTexture(),VEIL*show,true),0xF000F0);}
            finally{m.pop();}
            if(ctx.consumers() instanceof VertexConsumerProvider.Immediate immediate)immediate.draw(RenderLayer.getDebugQuads());
        }finally{
            drawing=false;dispatcher.setRenderShadows(mc.options.getEntityShadows().getValue());
            p.bodyYaw=by;p.prevBodyYaw=pby;p.headYaw=hy;p.prevHeadYaw=phy;p.setYaw(y);p.prevYaw=py;p.setPitch(pt);p.prevPitch=ppt;p.hurtTime=hurt;
            p.handSwingProgress=swing;p.lastHandSwingProgress=lastSwing;
        }
    }
}
