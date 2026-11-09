package dev.sangwoo.celestialfall.client.isosang;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.stand.StandArrowClient;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Lee So-sang's sword only while she uses it (2.105.0, user request "검은 평타나 스킬을 쓸때만 나오게"): in her hand
 * while a swing of hers or a skill moves her body (IsosangClient.animated: her basic attack's and the empowered
 * combo's hits, R, H through its formation, T, Y, G, H again), gone while she stands, walks, guards or dashes.
 *
 * <p>It comes into her hand in a flash of light along the blade, stars at the hand and the point (as it does as her
 * first hit begins, 참월: that hit keeps its own), and goes back into the light it came from as the swing's body
 * motion is over: for a moment the blade is a line of light where it was, which breaks up from the hand to the point
 * into motes of light that drift up and fade, a star at the point. From one's own eyes it rises into the picture as
 * it comes and sinks out of it as it goes ({@link #held}). While her next combo is empowered (E) and her sword is
 * away, the glow her sword had is in her open right hand instead ({@link #charge}).
 *
 * <p>When it came and went is seen where it is drawn: every frame, for every one of her players in the world, before
 * the effects are drawn (KagenoRenderer); where the sword was is taken from where the sword itself was drawn in the
 * world this frame (IsosangSwordRenderer, {@link #record}), so the light is on the blade as it was on screen.
 */
public final class IsosangSheath {
    private IsosangSheath(){}
    /** Whether her sword is in this player's hand now. */
    public static boolean drawn(UUID id){return IsosangClient.animated(id)!=null;}

    /** Seconds the light of its coming lasts, and of its going. */
    static final float COME=.16f,GO=.55f;
    /** Seconds it takes to rise into the picture from one's own eyes as it comes, and to sink out of it as it goes. */
    static final float RAISE=.1f,LOWER=.15f;
    /** Along the blade from the hand (blocks): the guard and the point (IsosangSlash's, the sword as drawn in a hand). */
    static final double GUARD=IsosangSlash.GUARD,TIP=IsosangSlash.TIP;
    /** The motes the blade breaks into as it goes. */
    static final int MOTES=16;

    private static final class State{
        boolean drawn;float changed=Float.NEGATIVE_INFINITY,seen=Float.NEGATIVE_INFINITY;
        Vec3d hand,dir;long frame=-1;
    }
    /** One coming or going of the sword: whose, which, when (this client's ticks), and where the blade was as it went. */
    private record Event(UUID id,boolean in,float at,Vec3d hand,Vec3d dir,int seed){}
    private record Shown(Event e,float t,Vec3d hand,Vec3d dir){}
    private record Glow(Vec3d at,float strength,float time,int seed){}
    private static final Map<UUID,State> STATES=new HashMap<>();
    private static final List<Event> EVENTS=new ArrayList<>();
    private static final List<Shown> SHOWN=new ArrayList<>();
    private static final List<Glow> GLOWS=new ArrayList<>();
    private static long frames;
    private static UUID recording;
    private static Object world;

    /** This client's clock (ticks, with the frame's share of one): the one her swings' ages are told against. */
    static float clock(float delta){return StandClient.ticks()+delta;}

    /** The player whose sword the held-item feature is about to draw in the world (null: none). */
    public static void recording(UUID id){recording=id;}
    /**
     * Where the sword is being drawn (IsosangSwordRenderer, with the model's origin, where the hand holds it, and its
     * blade up the model's y): kept for its holder, if it is drawn in the world for one.
     */
    static void record(MatrixStack matrices){
        if(recording==null)return;
        var mc=MinecraftClient.getInstance();if(mc.world==null)return;
        var m=matrices.peek().getPositionMatrix();
        Vector3f o=m.transformPosition(new Vector3f()),up=m.transformDirection(new Vector3f(0,1,0));
        // back from the view's frame to the world's (the stack's base turns the world to the view)
        var back=RenderSystem.getInverseViewRotationMatrix();back.transform(o);back.transform(up);
        if(up.lengthSquared()<1e-10f)return;up.normalize();
        Vec3d cam=mc.gameRenderer.getCamera().getPos(),hand=cam.add(o.x,o.y,o.z);
        // (a shadow pass of a shader pack draws from somewhere else: only where the holder is)
        var p=mc.world.getPlayerByUuid(recording);
        if(p==null||hand.squaredDistanceTo(p.getLerpedPos(mc.getTickDelta()).add(0,1,0))>9)return;
        var s=STATES.computeIfAbsent(recording,k->new State());
        s.hand=hand;s.dir=new Vec3d(up.x,up.y,up.z);s.frame=frames;
    }

    /**
     * Every frame before the effects (KagenoRenderer): sees whose sword came or went since the last frame and what of
     * its light shows now. True if anything does.
     */
    public static boolean prepare(MinecraftClient mc,float delta,Vec3d camera){
        SHOWN.clear();GLOWS.clear();
        if(mc.world==null){STATES.clear();EVENTS.clear();return false;}
        if(world!=mc.world){STATES.clear();EVENTS.clear();world=mc.world;}
        float now=clock(delta);
        for(var p:mc.world.getPlayers()){
            if(!StandClient.isosang(p))continue;
            UUID id=p.getUuid();var s=STATES.get(id);boolean d=drawn(id);
            if(s==null){s=new State();s.drawn=d;STATES.put(id,s);}
            else if(d!=s.drawn){
                // (seen a moment ago: one who comes into sight with it already out, or away, is just as they are)
                if(now-s.seen<5)begin(id,s,d,now,delta);
                s.drawn=d;s.changed=now;
            }
            s.seen=now;
            // while the next combo is empowered and the sword is away, the glow in the open right hand
            if(!d&&p.getPos().squaredDistanceTo(camera)<96*96){
                float g=IsosangClient.glow(id,delta)*IsosangIdle.weight(id,delta);
                if(g>.01f)GLOWS.add(new Glow(hand(p,delta),g,now,id.hashCode()));
            }
        }
        STATES.values().removeIf(s->now-s.seen>200);
        EVENTS.removeIf(e->(now-e.at)/20f>=(e.in?COME:GO)||now<e.at-1);
        for(var e:EVENTS){
            float t=(now-e.at)/20f;
            if(e.in){
                // its coming: on the blade as it is drawn now
                var s=STATES.get(e.id);
                if(s!=null&&s.frame==frames&&s.hand!=null&&s.hand.squaredDistanceTo(camera)<96*96)SHOWN.add(new Shown(e,t,s.hand,s.dir));
            }else if(e.hand.squaredDistanceTo(camera)<96*96)SHOWN.add(new Shown(e,t,e.hand,e.dir));
        }
        frames++;
        return !SHOWN.isEmpty()||!GLOWS.isEmpty();
    }
    private static void begin(UUID id,State s,boolean in,float now,float delta){
        int seed=(int)(id.getLeastSignificantBits()*31+(long)now);
        if(in){
            var swing=IsosangClient.animated(id);if(swing==null)return;
            // the first hit (참월) has its own flash as the sword comes; one seen only as it is well under way, none
            float age=swing.age(delta)/20f;
            if(swing.combo()==1||age>COME*.75f)return;
            EVENTS.add(new Event(id,true,now-age*20,null,null,seed));
        }else{
            // where it was last drawn, if a moment ago
            if(s.hand==null||frames-s.frame>8)return;
            EVENTS.add(new Event(id,false,now,s.hand,s.dir,seed));
        }
    }
    /** Where her open right hand's fingers are in the stance at rest, in the world this frame (for the light in it). */
    static Vec3d hand(PlayerEntity p,float delta){
        if(!(p instanceof net.minecraft.client.network.AbstractClientPlayerEntity c))return p.getLerpedPos(delta).add(0,1,0);
        var pose=CombatClient.pose(c,delta);
        Vector3f tip=IsosangIdle.fingers(c.age+delta,"slim".equals(c.getModel())?.5f:1);
        return StandArrowClient.world(p,delta,pose,tip);
    }
    /** A point brought {@code by} blocks toward the camera (a light in a hand is drawn in front of it, not inside its block). */
    static Vec3d toward(Vec3d p,Vec3d camera,double by){Vec3d d=camera.subtract(p);double l=d.length();return l<1e-6?p:p.add(d.multiply(Math.min(by,l*.5)/l));}

    /**
     * How far her sword is up in the picture from one's own eyes (0 to 1): coming up as it comes into her hand, going
     * down out of the picture as it goes.
     */
    public static float held(UUID id,float delta){
        boolean d=drawn(id);var s=STATES.get(id);float now=clock(delta);
        if(s==null||s.drawn!=d||now-s.seen>2)return d?1:0;
        return raised(d,(now-s.changed)/20f);
    }
    /** How far up in the picture from one's own eyes {@code t} seconds after it came ({@code drawn}) or went. */
    static float raised(boolean drawn,float t){return drawn?Ease.smooth(t,0,RAISE):1-Ease.smooth(t,0,LOWER);}
    /** Whether the sword is to be drawn from one's own eyes now (up, or still on its way down). */
    public static boolean inSight(UUID id,float delta){return held(id,delta)>.001f;}

    /** The light, added (the effects' additive pass). */
    public static void additive(EffectMesh m){
        for(var s:SHOWN){if(s.e.in)come(m,s);else go(m,s);}
        for(var g:GLOWS)glow(m,g);
    }
    /** Its coming: a white line along the blade over a blue glow, stars blooming at the hand and the point. */
    static void come(EffectMesh m,Shown s){
        float t=s.t,a=Ease.smooth(t,0,.025f)*(1-Ease.smooth(t,.08f,COME));
        Vec3d g=s.hand.add(s.dir.multiply(GUARD)),tip=s.hand.add(s.dir.multiply(TIP));
        m.softLine(g,tip,.3,IsosangSlash.AZURE,.3f*a);m.line(g,tip,.07,IsosangSlash.WHITE,.9f*a);
        float k=Ease.smooth(t,.02f,.05f)*(1-Ease.smooth(t,.07f,.14f));
        m.softDisc(s.hand,.32*k+.05,IsosangSlash.BLUE,.45f*k);m.star(s.hand,.4*k+.05,IsosangSlash.PALE,k);m.star(s.hand,.22*k+.03,IsosangSlash.WHITE,k);
        m.softDisc(tip,.36*k+.05,IsosangSlash.BLUE,.45f*k);m.star(tip,.5*k+.05,IsosangSlash.PALE,k);m.star(tip,.27*k+.03,IsosangSlash.WHITE,k);
    }
    /**
     * Its going: the blade a line of light where it was, taken from the hand to the point as it breaks up into motes
     * that drift up and out, twinkle and fade; a star at the hand as it goes and one at the point as the last of it
     * goes.
     */
    static void go(EffectMesh m,Shown s){
        float t=s.t;Vec3d g=s.hand.add(s.dir.multiply(GUARD)),tip=s.hand.add(s.dir.multiply(TIP));
        // the line, gone from the hand toward the point
        float from=Ease.smooth(t,0,.18f),line=1-Ease.smooth(t,.1f,.22f);
        if(line>0&&from<1){
            Vec3d a=g.add(tip.subtract(g).multiply(from));
            m.softLine(a,tip,.24,IsosangSlash.AZURE,.32f*line);m.line(a,tip,.05,IsosangSlash.PALE,.75f*line);m.line(a,tip,.022,IsosangSlash.WHITE,.9f*line);
        }
        // the motes, each loosed as the break reaches its place
        Vec3d side=s.dir.crossProduct(new Vec3d(0,1,0));side=side.lengthSquared()<1e-6?new Vec3d(1,0,0):side.normalize();
        for(int k=0;k<MOTES;k++){
            double u=(k+.5)/MOTES;float born=(float)(.18*u),age=t-born,life=.3f+.18f*(float)Ease.hash(s.e.seed+k*7);
            if(age<0||age>=life)continue;
            float v=age/life;
            Vec3d at=g.add(tip.subtract(g).multiply(u));
            double drift=Ease.hash(s.e.seed+k*13)-.5,rise=.35+.3*Ease.hash(s.e.seed+k*17);
            Vec3d p=at.add(side.multiply(drift*.5*v)).add(0,rise*(1-Math.pow(1-v,2))*life,0);
            float tw=.65f+.35f*(float)Math.sin(age*40+k*1.7),fade=(1-v)*Ease.smooth(age,0,.03f)*tw;
            int col=k%3==0?IsosangSlash.WHITE:k%3==1?IsosangSlash.PALE:IsosangSlash.AZURE;
            if(k%2==0)m.star(p,.07+.05*(1-v),col,fade);else m.softDisc(p,.045+.035*(1-v),col,.85f*fade);
        }
        float h=Ease.smooth(t,0,.02f)*(1-Ease.smooth(t,.04f,.14f));
        if(h>0){m.softDisc(s.hand,.26*h+.04,IsosangSlash.BLUE,.4f*h);m.star(s.hand,.3*h+.04,IsosangSlash.PALE,h);}
        float e=Ease.smooth(t,.12f,.17f)*(1-Ease.smooth(t,.2f,.32f));
        if(e>0){m.softDisc(tip,.3*e+.04,IsosangSlash.BLUE,.4f*e);m.star(tip,.36*e+.04,IsosangSlash.PALE,e);m.star(tip,.18*e+.02,IsosangSlash.WHITE,e);}
    }
    /** The empowered glow in her open right hand: a soft blue light breathing, a small star in it, three motes going round it. */
    static void glow(EffectMesh m,Glow g){
        float pulse=.75f+.25f*(float)Math.sin(g.time*.35f),a=g.strength*pulse;
        Vec3d at=toward(g.at,m.camera(),.16);
        m.softDisc(at,.22,IsosangSlash.BLUE,.36f*a);m.softDisc(at,.09,IsosangSlash.PALE,.55f*a);m.star(at,.11+.03*pulse,IsosangSlash.WHITE,.6f*a);
        for(int k=0;k<3;k++){
            double turn=g.time/20.0*2.4+k*2*Math.PI/3;
            Vec3d p=g.at.add(Math.cos(turn)*.16,.04*Math.sin(turn*1.7+k),Math.sin(turn)*.16);
            m.softDisc(p,.035,k==0?IsosangSlash.WHITE:IsosangSlash.PALE,.75f*a);
        }
    }
    /** A little light on the world as it comes and goes, and from the glow in her hand. */
    public static void frame(FxFrame f,Vec3d camera){
        for(var s:SHOWN){
            float a=s.e.in?Ease.smooth(s.t,0,.03f)*(1-Ease.smooth(s.t,.06f,COME)):1-Ease.smooth(s.t,0,.3f);
            if(a>0)f.light(s.hand.add(s.dir.multiply(TIP*.5)),IsosangSlash.AZURE,2.2,.4f*a);
        }
    }
    /** Forgets everything (a new world). */
    public static void clear(){STATES.clear();EVENTS.clear();SHOWN.clear();GLOWS.clear();}
}
