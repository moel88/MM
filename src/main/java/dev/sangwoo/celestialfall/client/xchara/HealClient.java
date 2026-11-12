package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.xchara.XCharaHeal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.xchara.XCharaRules.*;

/**
 * X Chara's E with their hack on, the restore, as this client knows and draws it (2.119.0, user request: "Q활성화
 * E스킬부터 만들자 간단하게 패널 누르는 모션 나오고 몸에 연두 이펙트 둘러지고 65힐"; the server's XCharaHeal). Simple, as
 * asked, and mine to make up but for those three things:
 * <ul>
 * <li>The press: the arm that holds the panel comes up in front of them, the panel with it, and its hand taps the panel
 * twice (the second tap is the press: the heal lands on it); they look down at it; then the arm goes back to its place
 * ({@link #lift}, {@link #tap}: XCharaIdle.arms poses the arm, HackPanel brings the panel up to the hand as it taps
 * and lights its rim and keys lime).</li>
 * <li>The lime light round the body as the heal lands ({@link #EFFECT} seconds): rings of lime rising round them from
 * their feet to over their head, small lime pixels winding up round them, a few plus signs rising, a ring running out
 * on the ground and a soft lime glow. It stays round them as they walk on. Drawn with the effects (KagenoRenderer);
 * its lime laid over what is behind it, its light added; everything a function of its own clock.</li>
 * <li>Its sound, made for it (tools/xchara/make_e_sound.py: two key taps and a soft chime rising): played as it starts,
 * faded out if it stops short.</li>
 * </ul>
 * Stopped short before the press lands (their user struck), the arm goes back at once and nothing more is drawn.
 */
public final class HealClient {
    private HealClient(){}
    /** One E: its age (ticks) as this client was told of it and this client's tick then; when it stopped short (its age, NaN if it did not); whether its heal landed. */
    static final class Cast {
        final float told;final int received;float stopped=Float.NaN;boolean healed;float amount;XCharaClient.Voice voice;
        Cast(float told,int received){this.told=told;this.received=received;}
        float age(float delta){return told+(StandClient.ticks()-received)+delta;}
    }
    private static final Map<UUID,Cast> CASTS=new HashMap<>();
    /** When each player's E is ready again (the server's world time), as told. */
    private static final Map<UUID,Long> READY=new HashMap<>();
    /** For the capture tools: what this client heard of it. */
    public static final List<String> HEARD=new ArrayList<>();
    private static Object world;
    /** Its lime, a deeper one for outlines, a pale one, and white. */
    static final int LIME=0x9dff3a,DEEP=0x2e8a12,PALE=0xdcffb0,WHITE=0xffffff;
    /** The press's two taps (seconds into it; the second is the server's HEAL_AT), and how long the lime light shows after the heal (seconds). */
    static final float TAP1=.27f,TAP2=HEAL_AT/20f,EFFECT=1.1f;
    /** How long one is kept (ticks): its press and the light after it. */
    static final float LIFE=HEAL_AT+EFFECT*20+4;

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(XCharaHeal.EVENT,(mc,handler,b,response)->{
            UUID user=b.readUuid();Identifier dim=b.readIdentifier();int type=b.readUnsignedByte();
            long start=b.readLong(),now=b.readLong(),ready=b.readLong();float healed=b.readFloat();
            if(type>XCharaHeal.STOPPED||now<start||!Float.isFinite(healed))return;
            mc.execute(()->accept(mc,user,dim,type,start,now,ready,healed));
        });
        ClientTickEvents.START_CLIENT_TICK.register(HealClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(HealClient::clear));
    }
    static void clear(){for(var c:CASTS.values())if(c.voice!=null)c.voice.fade();CASTS.clear();READY.clear();world=null;}
    private static void accept(MinecraftClient mc,UUID user,Identifier dim,int type,long start,long now,long ready,float healed){
        if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return;
        if(world!=mc.world){clear();world=mc.world;}
        READY.put(user,ready);
        if(type==XCharaHeal.START){
            if(now-start>HEAL_LENGTH)return;
            var c=new Cast(now-start,StandClient.ticks());CASTS.put(user,c);
            sound(mc,user,c);
            return;
        }
        var c=CASTS.get(user);if(c==null)return;
        if(type==XCharaHeal.HEALED){c.healed=true;c.amount=healed;heard(String.format(java.util.Locale.ROOT,"healed %.1f, %.0f ticks in",healed,c.age(0)));}
        else if(Float.isNaN(c.stopped)&&!c.healed){c.stopped=c.age(0);if(c.voice!=null)c.voice.fade();heard(String.format(java.util.Locale.ROOT,"stopped short %.0f ticks in",c.stopped));}
    }
    private static void heard(String line){HEARD.add(line);while(HEARD.size()>64)HEARD.remove(0);}
    private static void sound(MinecraftClient mc,UUID user,Cast c){
        if(mc.player==null)return;
        java.util.function.Supplier<Float> loud=user.equals(mc.player.getUuid())?()->(float)CelestialFall.CONFIG.effectVolume:()->{
            var m=MinecraftClient.getInstance();var p=m.world==null?null:m.world.getPlayerByUuid(user);
            return p==null?0f:KnifeClient.loudness(m,p.getPos());
        };
        c.voice=new XCharaClient.Voice(CelestialFall.id("xchara_e"),loud);
        heard(String.format(java.util.Locale.ROOT,"xchara_e, told %.0f ticks in, at %.2f",c.told,c.voice.full()));
        if(c.voice.full()>.01f&&c.told<=2)mc.getSoundManager().play(c.voice);
    }
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(world!=null||!CASTS.isEmpty())clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        CASTS.entrySet().removeIf(e->{
            var c=e.getValue();float age=c.age(0);
            var p=mc.world.getPlayerByUuid(e.getKey());
            return age>LIFE||age<-1||!Float.isNaN(c.stopped)&&age>c.stopped+6||p!=null&&!p.isAlive();
        });
    }
    /** When this player's E is ready again, as the server told it (0 if none). */
    public static long ready(UUID id){return READY.getOrDefault(id,0L);}
    /** Forgets this player's cooldown (their character is not X Chara now: the server forgot it too). */
    public static void forget(UUID id){READY.remove(id);}
    /** Whether E holds the local player where they stand now (XCharaRules.HEAL_HOLD): their keys do not walk them. */
    public static boolean movementLocked(){
        var mc=MinecraftClient.getInstance();if(mc.player==null||!MapClient.enabled())return false;
        var c=CASTS.get(mc.player.getUuid());
        return c!=null&&Float.isNaN(c.stopped)&&c.age(0)<HEAL_HOLD;
    }
    /** For the capture tools: this player's E as "age,healed,stopped", or null. */
    public static String describe(UUID id,float delta){
        var c=CASTS.get(id);return c==null?null:String.format(java.util.Locale.ROOT,"%.2f,%d,%d",c.age(delta),c.healed?1:0,Float.isNaN(c.stopped)?0:1);
    }

    // ───────────────────────────── the press ─────────────────────────────

    /**
     * How far the panel's arm is up in front of them {@code t} seconds into the press (0 at its place by their side, 1
     * up): up in a fifth of a second, there through the taps, back down by the end; stopped short at {@code stopped}
     * seconds (NaN: not), back at once.
     */
    static float lift(float t,float stopped){
        float up=Ease.smooth(t,0,.2f)*(1-Ease.smooth(t,.6f,HEAL_LENGTH/20f));
        return Float.isNaN(stopped)?up:up*(1-Ease.smooth(t,stopped,stopped+.15f));
    }
    /** How far the hand is down on the panel then (0 off it, 1 on it): two quick taps, the second the press. */
    static float tap(float t){return Math.max(bump(t,TAP1),bump(t,TAP2));}
    private static float bump(float t,float at){return Ease.smooth(t,at-.06f,at)*(1-Ease.smooth(t,at,at+.09f));}
    /** How lime the panel is lit then: a touch on the first tap, full on the press, fading after. */
    static float lit(float t){return Math.max(.35f*bump(t,TAP1),Ease.smooth(t,TAP2-.03f,TAP2)*(1-Ease.smooth(t,TAP2+.15f,TAP2+.6f)));}
    /** This player's press this frame as {lift, tap, lit}, or null if they are not in one. */
    public static float[] press(UUID id,float delta){
        var c=CASTS.get(id);if(c==null)return null;
        float t=c.age(delta)/20f,stopped=Float.isNaN(c.stopped)?Float.NaN:c.stopped/20f;
        float lift=lift(t,stopped);if(lift<=.001f&&t>.3f)return null;
        boolean cut=!Float.isNaN(stopped);
        return new float[]{lift,cut&&t>stopped?0:tap(t),cut?0:lit(t)};
    }

    // ───────────────────────────── the lime light round the body ─────────────────────────────

    /** How many rings rise, pixels wind up and plus signs rise; how far out from their middle the rings are (blocks). */
    static final int RINGS=3,PIXELS=14,PLUSES=5;static final double RADIUS=.62;
    /** Ring {@code k} at {@code s} seconds after the heal: how high it is of the way from their feet to over their head (0 to 1) and how strongly it shows; null while it does not. */
    static double[] ring(int k,float s){
        float born=k*.13f,life=.6f,u=(s-born)/life;if(u<0||u>=1)return null;
        double rise=1-(1-u)*(1-u);
        return new double[]{rise,Math.min(1,u/.08)*(1-Ease.smooth(u,.55f,1))};
    }
    /** Pixel {@code j} then: its angle round them (radians), how high (0 to 1), its half size and how strongly it shows; null while it does not. */
    static double[] pixel(int j,int seed,float s){
        float born=(float)(.5*GlitchFx.h(j,0,seed,51)),life=(float)(.45+.2*GlitchFx.h(j,0,seed,52)),u=(s-born)/life;if(u<0||u>=1)return null;
        double angle=GlitchFx.h(j,0,seed,53)*Math.PI*2+(j%2==0?1:-1)*Math.PI*2*.9*u;
        return new double[]{angle,.05+.95*u,.03+.02*GlitchFx.h(j,0,seed,54),Math.min(1,u/.1)*(u<.8?1:1-(u-.8)/.2)};
    }
    /** Plus sign {@code j} then: its angle round them, how far out, how high (blocks from their feet), its half size and how strongly it shows; null while it does not. */
    static double[] plus(int j,int seed,float s){
        float born=(float)(.08+.4*GlitchFx.h(j,0,seed,61)),life=.6f,u=(s-born)/life;if(u<0||u>=1)return null;
        return new double[]{GlitchFx.h(j,0,seed,62)*Math.PI*2,.75+.2*GlitchFx.h(j,0,seed,63),.5+1.0*GlitchFx.h(j,0,seed,64)+.55*u,.085,Math.min(1,u/.12)*(1-Ease.smooth(u,.6f,1))};
    }
    /** One player's light this frame: their feet, their height, seconds since the heal and a seed. */
    record Shown(Vec3d feet,double height,float s,int seed){}
    private static List<Shown> shown=List.of();
    /** Every frame before the effects: whose light shows. True if any does. */
    public static boolean prepare(MinecraftClient mc,float delta,Vec3d camera){
        if(mc.world==null||CASTS.isEmpty()){shown=List.of();return false;}
        var out=new ArrayList<Shown>();
        for(var e:CASTS.entrySet()){
            var c=e.getValue();if(!c.healed)continue;
            float s=c.age(delta)/20f-TAP2;if(s<0||s>=EFFECT)continue;
            var p=mc.world.getPlayerByUuid(e.getKey());if(p==null||p.isInvisible()||p.getPos().squaredDistanceTo(camera)>96*96)continue;
            out.add(new Shown(p.getLerpedPos(delta),p.getHeight(),s,(int)(e.getKey().getLeastSignificantBits()^c.received)));
        }
        shown=out;return !out.isEmpty();
    }
    private static final Vec3d X=new Vec3d(1,0,0),Z=new Vec3d(0,0,1);
    /** The lime itself, laid over what is behind it. */
    public static void alpha(EffectMesh m){for(var s:shown)draw(m,s,false);}
    /** Its light, added. */
    public static void additive(EffectMesh m){for(var s:shown)draw(m,s,true);}
    private static void draw(EffectMesh m,Shown sh,boolean light){
        float s=sh.s();Vec3d feet=sh.feet(),mid=feet.add(0,sh.height()*.5,0);double top=sh.height()+.2;
        float near=GlitchFx.near(mid,m.camera());if(near<=0)return;
        for(int k=0;k<RINGS;k++){
            var r=ring(k,s);if(r==null)continue;
            Vec3d c=feet.add(0,.05+r[0]*top,0);float a=(float)r[1]*near;
            if(light)FxLib.ringPlane(m,c,X,Z,RADIUS,.035,PALE,.55f*a);
            else{DanceBurst.band(m,c,X,Z,RADIUS,.2,DEEP,.5f*a);DanceBurst.band(m,c,X,Z,RADIUS,.12,LIME,.85f*a);}
        }
        for(int j=0;j<PIXELS;j++){
            var p=pixel(j,sh.seed(),s);if(p==null)continue;
            Vec3d at=feet.add(Math.cos(p[0])*RADIUS,p[1]*top,Math.sin(p[0])*RADIUS);var f=GlitchFx.facing(m.camera(),at);float a=(float)p[3]*near;
            if(light)GlitchFx.square(m,at,f,p[2]*2.2,LIME,.3f*a);
            else{GlitchFx.square(m,at,f,p[2]+.01,DEEP,.9f*a);GlitchFx.square(m,at,f,p[2],j%3==0?PALE:LIME,a);}
        }
        for(int j=0;j<PLUSES;j++){
            var p=plus(j,sh.seed(),s);if(p==null)continue;
            Vec3d at=feet.add(Math.cos(p[0])*p[1],p[2],Math.sin(p[0])*p[1]);var f=GlitchFx.facing(m.camera(),at);float a=(float)p[4]*near;double h=p[3],w=h*.32;
            if(light){GlitchFx.square(m,at,f,h*1.8,LIME,.22f*a);continue;}
            GlitchFx.bar(m,at,f,h+.012,w+.012,DEEP,.9f*a);GlitchFx.bar(m,at,f,w+.012,h+.012,DEEP,.9f*a);
            GlitchFx.bar(m,at,f,h,w,LIME,a);GlitchFx.bar(m,at,f,w,h,LIME,a);
        }
        // a ring running out on the ground as the heal lands, and a soft glow about them
        if(s<.35f){
            double r=.3+1.0*Ease.smooth(s,0,.32f);float a=Math.min(1,s/.03f)*(1-Ease.smooth(s,.1f,.35f))*near;
            if(light)FxLib.ringPlane(m,feet.add(0,.04,0),X,Z,r,.04,PALE,.5f*a);
            else DanceBurst.band(m,feet.add(0,.04,0),X,Z,r,.16,LIME,.7f*a);
        }
        if(light)m.softDisc(mid,1.15,LIME,.2f*Math.min(1,s/.04f)*(1-Ease.smooth(s,.15f,.8f))*near);
    }
    /** The lime light it throws on the world round them. */
    public static void frame(FxFrame f,Vec3d camera){
        for(var sh:shown)f.light(sh.feet().add(0,sh.height()*.5,0),LIME,5,.6f*Math.min(1,sh.s()/.04f)*(1-Ease.smooth(sh.s(),.15f,.9f)));
    }
}
