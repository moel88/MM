package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.client.stand.StandPunchFx;
import dev.sangwoo.celestialfall.client.vfx.FxDistort;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.*;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;

/**
 * Lee So-sang's E as drawn (2.80.0, user request: "E스킬을 사용하면 평타 쿨타임이 즉시 초기화가 되고 다음 평타가 강화 돼 …
 * 1타는 영상 처럼 검을 3개 던지고 4타는 뒤로 이동하고 거대한 검을 찔러"), from the user's two videos of it, read from above
 * and from behind and confirmed by the user ("맞음, 이대로"):
 * <ul>
 * <li>E itself (no motion in the videos: it was used before they begin): a flash of moonlight at the sword hand, rings of
 * light spreading over the ground, stars rising round them; the sword glows from then until the empowered combo is over
 * (IsosangSwordRenderer.charge).</li>
 * <li>The empowered first: three quick throws, low at the front-right, at the right hand, and with the left hand to the
 * left, each a burst of starlight out of which a sword of light flies the way they look (IsosangRules' flight; the
 * server strikes the first body it reaches), a black ink line and stars left along its way; where each came out, a white
 * ring with a flower in it, turning to a bubble of ink; then down in a crouch, lightning along the blade, and up again
 * for the second.</li>
 * <li>The empowered fourth: the right arm raised and drawn back, then a step back as a giant see-through blade is thrust
 * past them far ahead; at its point a fan of blue blades bursts out, great strokes of ink sweep round it and the giant
 * sword (the sword itself, three times its size, lying along the thrust, its grip toward them) hangs there a moment and
 * fades; whom it strikes is thrown along it. The caster's own camera draws back for it.</li>
 * </ul>
 * Every effect is a function of the swing's clock, as IsosangSlash's.
 */
final class IsosangEmpower {
    private IsosangEmpower(){}

    // ───────────────────────────── the empowered first: three swords thrown ─────────────────────────────

    /** Wound: the sword raised over the right shoulder, turned a little to the right. */
    static final R6.Pose T_WIND=w(.06,-.05,-12,-2,-1,d(45,55),d(60,8),d(-62,-40),24,-160);
    /** The first throw: the sword flicked down to the front-right, low (a sword flies out of the burst there). */
    static final R6.Pose T_ONE=w(.12,-.12,8,10,2,d(-22,40),d(-40,8),d(-50,-70),24,-160);
    static final R6.Pose T_RISE=w(.12,-.1,0,6,0,d(10,70),d(-2,38),d(-55,-50),24,-160);
    /** Cocked high at the right, then the second: flung out at the right hand, at shoulder height. */
    static final R6.Pose T_COCK=w(.12,-.1,-14,2,-2,d(40,90),d(24,56),d(-60,-30),24,-160);
    static final R6.Pose T_TWO=w(.14,-.12,6,6,2,d(18,30),d(8,-6),d(-55,-60),24,-160);
    /** The left hand drawn across to the right shoulder, the body turned right; then flung out to the left: the third. */
    static final R6.Pose T_CROSS=w(.14,-.14,-10,6,0,d(0,60),d(-16,28),d(20,40),24,-160);
    static final R6.Pose T_LOAD=w(.14,-.18,-22,8,-2,d(-20,80),d(-32,48),d(30,62),24,-160);
    static final R6.Pose T_THREE=w(.16,-.2,20,8,3,d(-30,70),d(-44,40),d(10,-55),24,-160);
    static final R6.Pose T_FOLLOW=w(.16,-.22,24,9,3,d(-32,72),d(-46,40),d(0,-76),24,-160);
    /** Down in a crouch, the sword low at the right with lightning along it (the videos), until the second may begin. */
    static final R6.Pose T_CROUCH=w(.2,-.34,8,14,0,d(-40,60),d(-50,28),d(-40,-40),30,-150);
    static final R6.Pose T_LOW=w(.22,-.3,4,12,0,d(-36,62),d(-46,28),d(-44,-46),30,-150);
    /**
     * The empowered first (IsosangRules.THROWS; the user's videos of E: the bursts 0.07, 0.15 and 0.33 s after it begins,
     * the second hit 0.8 s on): it ends held up at the right as the first does (H1_HOLD), where the second begins.
     */
    static final Anim THROWS=Anim.joints(
        k(0,STANCE),
        k(.05,T_WIND),
        k(blow(IsosangRules.THROWS,0),T_ONE,Anim.Ease.IN),
        k(.14,T_RISE),
        k(.17,T_COCK),
        k(blow(IsosangRules.THROWS,1),T_TWO,Anim.Ease.IN),
        k(.25,T_CROSS),
        k(.31,T_LOAD),
        k(blow(IsosangRules.THROWS,2),T_THREE,Anim.Ease.IN),
        k(.42,T_FOLLOW),
        k(.55,T_CROUCH),
        k(.66,T_LOW),
        k(step(IsosangRules.THROWS),H1_HOLD),
        k(.95,H1_LOWER),
        k(IsosangRules.LENGTH[IsosangRules.THROWS]/20f,STANCE))
        .stops(Anim.stop(blow(IsosangRules.THROWS,0),.02,.03),Anim.stop(blow(IsosangRules.THROWS,1),.02,.03),Anim.stop(blow(IsosangRules.THROWS,2),.025,.04))
        .after(IsosangSlash::grounded);

    // ───────────────────────────── the empowered fourth: the giant sword ─────────────────────────────

    /** The right arm raised forward, the sword up, leaning back a little: the giant blade shows beside them. */
    static final R6.Pose G_RAISE=w(.04,-.04,-10,-6,-1,d(40,24),d(70,-14),d(-14,-24),20,-160);
    /** Drawn back to the right hip, the point ahead (a thrust's chamber), as the step back begins. */
    static final R6.Pose G_DRAW=w(.02,-.12,-18,2,-2,d(-12,118),d(-6,8),d(-10,-30),26,-150);
    /** The thrust: the arm straight ahead at the shoulder, the right shoulder forward, leaning in; the left arm back. */
    static final R6.Pose G_THRUST=w(.1,-.2,20,12,2,d(4,12),d(-6,-12),d(-24,-150),16,-170);
    static final R6.Pose G_HOLD=w(.1,-.21,22,12,2,d(2,14),d(-8,-10),d(-26,-148),16,-170);
    /** Drawn back up, and lowered. */
    static final R6.Pose G_BACK=w(.06,-.08,-4,2,0,d(20,60),d(52,40),d(-62,-60),18,-164);
    /**
     * The empowered fourth (IsosangRules.THRUST; the user's videos of E: the giant blade beside them at once, its point far
     * ahead a tenth of a second on, the giant sword there for some 0.4 s): the step back is real (IsosangClient.drive).
     */
    static final Anim THRUST=Anim.joints(
        k(0,STANCE),
        k(.05,G_RAISE),
        k(.1,G_DRAW),
        k(blow(IsosangRules.THRUST,0),G_THRUST,Anim.Ease.IN),
        k(.25,G_HOLD),
        k(.6,G_HOLD),
        k(.85,G_BACK),
        k(1.15,H1_LOWER),
        k(IsosangRules.LENGTH[IsosangRules.THRUST]/20f,STANCE))
        .stops(Anim.stop(blow(IsosangRules.THRUST,0),.06,.1))
        .after(IsosangSlash::grounded);

    /** The giant sword's size (times the sword's model: about 6.5 blocks), and where it hangs along the thrust (blocks ahead of where they thrust from: its grip; up: its grip, its point lower). */
    static final double GIANT=3,GIANT_GRIP=2.0,GIANT_HIGH=1.0,GIANT_DIP=6;
    /** When the giant sword shows and fades (seconds into the hit). */
    static final float GIANT_IN=.11f,GIANT_FULL=.19f,GIANT_FADE=.42f,GIANT_GONE=.62f;
    /** How far the caster's own camera draws back for the empowered fourth (blocks). */
    static final double PULL=2.0;
    static double pull(float sec){return PULL*Ease.smooth(sec,.04f,.36f)*(1-Ease.smooth(sec,.9f,1.4f));}

    /** Where the empowered fourth thrusts from: its user's place as it began, the step back behind it (the server's plan). */
    static Vec3d thrustFrom(StandClient.Cast c){return c.origin().subtract(Vec3d.fromPolar(0,c.yaw()).multiply(IsosangRules.BACKSTEP));}
    /** The thrust's point at the blow (blocks ahead of where it thrusts from, at chest height). */
    static Vec3d thrustTip(StandClient.Cast c){return world(thrustFrom(c),c.yaw(),v(IsosangRules.THRUST_REACH,.95,0));}

    // ───────────────────────────── the swords' ways ─────────────────────────────

    /** A sword's way as drawn: from where, which way, how far it flies, and when (seconds into its swing) it was thrown. */
    record Way(Vec3d from,Vec3d dir,double end,float at,boolean landed){
        /** How far it has flown at the swing's second {@code t} (blocks). */
        double flown(float t){return IsosangRules.swordFlown((t-at)*20,end);}
        Vec3d point(double d){return from.add(dir.multiply(d));}
        /** Seconds into the swing it reaches {@code d} blocks along. */
        float reach(double d){return at+(float)(d/IsosangRules.SWORD_SPEED/20);}
    }
    /** The local caster's own throws foreseen until the server's word comes (their aim then, held). */
    private static final Map<Long,Way> FORESEEN=new LinkedHashMap<>();
    /**
     * Sword {@code i}'s way: the server's word, else (the caster's own client, the throw due) the way their sight goes now
     * as the server picks it, held from the first frame it is foreseen; null until either.
     */
    static Way way(Fx c,int i){
        var told=IsosangClient.swords(c.cast.id());
        if(told!=null&&told[i]!=null)return new Way(told[i].from(),told[i].dir(),told[i].end(),told[i].thrown()/20f,told[i].landed());
        float at=blow(IsosangRules.THROWS,i);
        var mc=c.mc;if(mc.player==null||!c.cast.caster().equals(mc.player.getUuid())||c.sec<at)return null;
        long key=c.cast.id().getLeastSignificantBits()*5+i;var w=FORESEEN.get(key);if(w!=null)return w;
        w=foresee(mc,mc.player,i,at);FORESEEN.put(key,w);while(FORESEEN.size()>48)FORESEEN.remove(FORESEEN.keySet().iterator().next());
        return w;
    }
    /** The way the server throws sword {@code i} for this caster now (IsosangAttack.toss: out of its place, at what their sight meets first). */
    static Way foresee(MinecraftClient mc,Entity p,int i,float at){
        float yaw=p.getYaw();Vec3d f=Vec3d.fromPolar(0,yaw),right=new Vec3d(-f.z,0,f.x);double[] o=IsosangRules.SWORD_FROM[i];
        Vec3d from=p.getPos().add(f.multiply(o[0])).add(0,o[1],0).add(right.multiply(o[2]));
        Vec3d eye=p.getEyePos(),look=p.getRotationVec(1f);double far=IsosangRules.SWORD_AIM;
        var block=mc.world.raycast(new RaycastContext(eye,eye.add(look.multiply(far)),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        if(block.getType()!=HitResult.Type.MISS)far=eye.distanceTo(block.getPos());
        for(var e:mc.world.getEntitiesByClass(LivingEntity.class,new Box(eye,eye.add(look.multiply(far))).expand(1),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit())){
            var hit=e.getBoundingBox().expand(.3).raycast(eye,eye.add(look.multiply(far)));
            if(hit.isPresent())far=Math.min(far,eye.distanceTo(hit.get()));
        }
        Vec3d aim=eye.add(look.multiply(Math.max(IsosangRules.SWORD_AIM_MIN,far))),dir=aim.subtract(from);
        dir=dir.lengthSquared()<1e-6?look:dir.normalize();double most=IsosangRules.SWORD_RANGE;
        var wall=mc.world.raycast(new RaycastContext(from,from.add(dir.multiply(most)),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        if(wall.getType()!=HitResult.Type.MISS)most=from.distanceTo(wall.getPos());
        return new Way(from,dir,most,at,false);
    }
    /** Where sword {@code i} comes out (the server's word, else its place beside them as the hit faces). */
    static Vec3d outOf(Fx c,int i){
        var w=way(c,i);if(w!=null)return w.from();
        double[] o=IsosangRules.SWORD_FROM[i];return world(anchor(c),c.cast.yaw(),v(o[0],o[1],o[2]));
    }
    /** Whom sword {@code i} struck (the server's word), and their chest as it reached them. */
    static LivingEntity struckBy(Fx c,int i){
        var ids=IsosangClient.struck(c.cast.id(),i);if(ids==null||ids.length==0||c.mc.world==null)return null;
        return c.mc.world.getEntityById(ids[0]) instanceof LivingEntity e?e:null;
    }

    // ───────────────────────────── E's flash ─────────────────────────────

    /** Where the sword hand is as E is used (no motion drives the body then: the stance's right hand, the way the body faces). */
    static Vec3d hand(Fx c){
        var e=c.mc.world==null?null:c.mc.world.getPlayerByUuid(c.cast.caster());
        float yaw=e==null?c.cast.yaw():e.getBodyYaw();Vec3d feet=e==null?c.cast.origin():e.getLerpedPos(c.delta);
        return world(feet,yaw,v(.32,1.0,.36));
    }

    // ───────────────────────────── the passes ─────────────────────────────

    /** The ink: the swords' lines and the bubbles' ink, the stains under them; the strokes round the giant sword's point, the streak under it. */
    static void ink(Fx c){
        float t=c.sec;int combo=c.cast.combo();
        if(combo==IsosangRules.THROWS){
            for(int i=0;i<IsosangRules.SWORD_FROM.length;i++){
                var w=way(c,i);if(w==null)continue;
                // the line of ink it leaves, laid as it passes and drying from where it began
                double d=w.flown(t);if(d<=.05)continue;
                int n=6;Vec3d side=w.dir().crossProduct(UP);side=side.lengthSquared()<1e-4?c.right:side.normalize();
                for(int k=0;k<n;k++){
                    double a=d*k/n,b=d*(k+1)/n;float pass=w.reach((a+b)*.5);
                    float erase=Ease.smooth(t,pass+.1f,pass+.42f),ink=Ease.smooth(t,pass,pass+.12f);
                    if(erase>=1)continue;
                    strip(w.point(a),w.point(b),side,.13,3,7+i,erase,.35f+.65f*ink,.9f);
                }
                // a stain under where it struck
                var e=struckBy(c,i);
                if(e!=null){float at=w.reach(w.end());Vec3d p=floor(c,e.getLerpedPos(c.delta));for(int k=0;k<3;k++)splatAt(p.add((Ease.hash(i*31+k)-.5)*1.4,0,(Ease.hash(i*37+k)-.5)*1.4),.5+.3*Ease.hash(i*41+k),Ease.hash(i*43+k)*6.28,1.2,i*53+k,t-at-.04f,.75f);}
                // the bubble's ink where it came out: a few drops thrown down to the ground
                float at=blow(IsosangRules.THROWS,i)+.14f;
                Vec3d o=outOf(c,i);Vec3d g=floor(c,o);
                for(int k=0;k<2;k++)splatAt(g.add((Ease.hash(i*59+k)-.5)*.9,0,(Ease.hash(i*61+k)-.5)*.9),.35+.2*Ease.hash(i*67+k),Ease.hash(i*71+k)*6.28,1.1,i*73+k,t-at-.06f,.6f);
            }
        }else if(combo==IsosangRules.THRUST){
            Vec3d from=thrustFrom(c.cast);float yaw=c.cast.yaw(),at=blow(IsosangRules.THRUST,0);
            // great strokes of ink round the point (the videos: a black crescent over it from the left, another at the right)
            for(var s:STROKES){var sw=band(from,yaw,s,t);if(sw!=null&&t<s.eat1())KagenoFire.inkRibbon(sw.inner(),sw.outer(),sw.along(),sw.n(),s.style(),s.seed(),Ease.smooth(t,s.eat0(),s.eat1()),inks(s,sw,t),Ease.smooth(t,s.t0(),s.t0()+.015f));}
            // a long dark streak under the thrust, deep blue as it is laid and black as it dries
            if(t>=at-.02f&&t<at+.9f){
                Vec3d a=floor(c,world(from,yaw,v(.8,0,0))),b=floor(c,world(from,yaw,v(IsosangRules.THRUST_REACH-.3,0,0)));
                strip(a,b,c.right,1.1,3,13,Ease.smooth(t,at+.5f,at+.9f),.3f+.7f*Ease.smooth(t,at+.05f,at+.35f),.8f*Ease.smooth(t,at-.02f,at+.04f));
            }
            // ink thrown down round the point
            Vec3d tip=floor(c,thrustTip(c.cast));
            for(int k=0;k<7;k++){
                double ang=Ease.hash(k*7+5)*6.28,r=.8+2.2*Ease.hash(k*11+3);
                splatAt(tip.add(Math.cos(ang)*r,0,Math.sin(ang)*r),.6+.5*Ease.hash(k*13),ang,1.3,k*17+5,t-at-.05f-.04f*(float)Ease.hash(k*19),.8f);
            }
            for(var e:thrustStruck(c))for(int k=0;k<3;k++){Vec3d p=floor(c,e.getLerpedPos(c.delta));splatAt(p.add((Ease.hash(k*23+e.getId())-.5)*1.2,0,(Ease.hash(k*29+e.getId())-.5)*1.2),.5,k,1.2,k*31+e.getId(),t-at-.06f,.7f);}
        }
    }
    /**
     * The strokes of ink round the giant sword's point, in the thrust's frame from where it thrusts: one sweeping over it
     * from the left, its plane standing up across the thrust and leaning back; one low round its right.
     */
    static final Arc[] STROKES={
        arc(v(IsosangRules.THRUST_REACH-.6,1.0,0),70,180,2.0,2.7,-150,-20,.155f,.22f,.62f,.9f,.17f,.32f,9,3,2),
        arc(v(IsosangRules.THRUST_REACH-1.2,.4,.3),15,90,1.7,2.3,20,150,.17f,.24f,.66f,.95f,.19f,.36f,9,9,2)};

    /** The blended pass: the bubbles of ink the swords came out of, the ink edge under every burst. */
    static void alpha(Fx c,EffectMesh m){
        float t=c.sec;int combo=c.cast.combo();
        if(combo==IsosangRules.THROWS){
            for(int i=0;i<IsosangRules.SWORD_FROM.length;i++){
                float at=blow(IsosangRules.THROWS,i),u0=at+.12f;if(t<u0||t>=at+.62f)continue;
                Vec3d o=outOf(c,i);float a=Ease.smooth(t,u0,u0+.05f)*(1-Ease.smooth(t,at+.38f,at+.62f))*seen(o,c.camera);
                double r=.5+.14*Ease.out(t,u0,u0+.3f);
                m.softDisc(o,r*1.05,INK,.55f*a);m.softDisc(o,r*.8,NAVY,.35f*a);
            }
            for(int i=0;i<IsosangRules.SWORD_FROM.length;i++){var e=struckBy(c,i);var w=way(c,i);if(e==null||w==null)continue;float at=w.reach(w.end());burstInk(c,m,chestOf(c,e,at),at,.55,i*7);}
        }else if(combo==IsosangRules.THRUST){
            float at=blow(IsosangRules.THRUST,0);
            // the giant see-through blade thrust past them (a pale body; its edges in the light pass)
            ghost(c,m,t,false);
            int k=0;for(var e:thrustStruck(c))burstInk(c,m,chestOf(c,e,at),at,.85,k++*13);
        }
    }
    /** The light: E's flash; the bursts, the swords flying, the bubbles' rings, the lightning on the blade; the giant blade's edges, the fan of blades at its point, the giant sword's glow. */
    static void additive(Fx c,EffectMesh m){
        float t=c.sec;int combo=c.cast.combo();
        if(combo==0){flash(c,m,t);return;}
        if(combo==IsosangRules.THROWS){
            for(int i=0;i<IsosangRules.SWORD_FROM.length;i++){
                float at=blow(IsosangRules.THROWS,i);Vec3d o=outOf(c,i);
                // the burst of starlight it comes out of
                if(t>=at-.01f&&t<at+.18f){
                    float a=Ease.smooth(t,at-.01f,at+.01f)*(float)Math.pow(1-Ease.unit(t,at,at+.18f),.8)*seen(o,c.camera);double s=.62+.38*Ease.out(t,at,at+.05f);
                    Vec3d near=facing(o,c.camera);
                    StandPunchFx.burst(m,near,.6*s,.4,9,i*31+5,AZURE,BLUE,.9f*a,.35f*a);
                    m.softDisc(near,.9*s,BLUE,.35f*a);m.star(near,.75*s,WHITE,a);m.star(near,.4*s,PALE,a);
                }
                // the white ring with a flower in it, where it came out, then the rim of the bubble of ink
                ring(c,m,t,o,at,i);
                // the sword flying, and the stars it leaves
                var w=way(c,i);if(w!=null)sword(c,m,t,w,i);
            }
            for(int i=0;i<IsosangRules.SWORD_FROM.length;i++){var e=struckBy(c,i);var w=way(c,i);if(e==null||w==null)continue;float at=w.reach(w.end());burst(c,m,chestOf(c,e,at),w.dir(),at,.55,i*7);}
            lightning(c,m,t);
        }else if(combo==IsosangRules.THRUST){
            ghost(c,m,t,true);
            fan(c,m,t);
            giantGlow(c,m,t);
            float at=blow(IsosangRules.THRUST,0);int k=0;Vec3d f=Vec3d.fromPolar(0,c.cast.yaw());
            for(var e:thrustStruck(c))burst(c,m,chestOf(c,e,at),f,at,.85,k++*13);
        }
    }
    /** Over the ink: the stars along the swords' lines and E's rising stars. */
    static void overlay(Fx c,EffectMesh m){
        float t=c.sec;int combo=c.cast.combo();
        if(combo==0){risingStars(c,m,t);return;}
        if(combo==IsosangRules.THROWS){
            for(int i=0;i<IsosangRules.SWORD_FROM.length;i++){
                var w=way(c,i);if(w==null)continue;
                for(int k=0;k<10;k++){
                    int seed=i*97+k*13;double d=w.end()*(k+.3+.4*Ease.hash(seed))/10;float born=w.reach(d),age=t-born,life=.35f+.25f*(float)Ease.hash(seed+1);
                    if(age<0||age>=life)continue;
                    Vec3d side=w.dir().crossProduct(UP);side=side.lengthSquared()<1e-4?c.right:side.normalize();
                    Vec3d p=w.point(d).add(side.multiply((Ease.hash(seed+2)-.5)*.3)).add(0,(Ease.hash(seed+3)-.5)*.25+.15*age,0);
                    glint8(m,p,.08+.07*Ease.hash(seed+4),k%3==0?WHITE:k%3==1?PALE:LILAC,(1-age/life)*Ease.smooth(age,0,.03f)*twinkle(t,seed));
                }
            }
        }else if(combo==IsosangRules.THRUST){
            float at=blow(IsosangRules.THRUST,0);Vec3d tip=thrustTip(c.cast);
            if(t>=at&&t<at+.5f){float a=Ease.smooth(t,at,at+.03f)*(1-Ease.smooth(t,at+.2f,at+.5f));crescentMoon(m,tip.add(0,.9,0),.42,Math.PI*.6,MOON,.8f*a);glint8(m,tip,.5,WHITE,.7f*a);}
        }
    }

    // ───────────────────────────── the pieces ─────────────────────────────

    /** A struck target's chest, held from the first frame the blow reached it. */
    private static final Map<Long,Vec3d> CHESTS=new LinkedHashMap<>();
    static Vec3d chestOf(Fx c,LivingEntity e,float at){
        long key=c.cast.id().getMostSignificantBits()*37+e.getId();var p=CHESTS.get(key);if(p!=null)return p;
        p=chest(c,e);if(c.sec<at)return p;
        CHESTS.put(key,p);while(CHESTS.size()>96)CHESTS.remove(CHESTS.keySet().iterator().next());
        return p;
    }
    /** A burst on a struck target, as the sword's hits' (IsosangSlash.impacts): blue spikes round a pale heart, a crescent moon in it, a quick ring, sparks the way it went. */
    static void burst(Fx c,EffectMesh m,Vec3d p,Vec3d way,float at,double size,int seed){
        float t=c.sec;if(t<at-.01f||t>=at+.3f)return;
        Vec3d side=way.crossProduct(UP);side=side.lengthSquared()<1e-4?c.right:side.normalize();Vec3d up=side.crossProduct(way).normalize();
        float vis=seen(p,c.camera),a=Ease.smooth(t,at-.01f,at+.01f),u=Ease.unit(t,at,at+.16f);
        float ba=a*(float)Math.pow(1-Ease.unit(t,at,at+.2f),.7)*vis;double br=(.62+.38*Ease.out(t,at,at+.06f))*size;
        Vec3d near=facing(p,c.camera);
        if(ba>0){StandPunchFx.burst(m,near,br,.36,9,seed,AZURE,BLUE,.85f*ba,.32f*ba);crescentMoon(m,near,br*.56,Math.PI*2*Ease.hash(seed+19)+.8*(t-at),MOON,.9f*ba);}
        float flash=a*(1-u)*vis;if(flash>0){m.softDisc(near,.7*size/.55,BLUE,.28f*flash);m.softDisc(near,.16*(1-u)+.04,WHITE,.7f*flash);}
        FxLib.ringPlane(m,p,side,up,.15+.85*size/.55*Ease.out(t,at,at+.15f),.07,WHITE,.7f*a*(1-Ease.unit(t,at,at+.15f)));
        float sp=a*(1-Ease.smooth(t,at+.05f,at+.2f));
        for(int k=0;k<12;k++){
            double ang=(Ease.hash(k*3+seed)-.5)*2.4,rise=(Ease.hash(k*5+seed)-.5)*1.6;
            Vec3d dir=way.multiply(Math.cos(ang)).add(side.multiply(Math.sin(ang))).add(up.multiply(rise)).normalize();
            double len=(.35+.6*Ease.hash(k*7+seed))*Ease.out(t,at,at+.12f);Vec3d q=p.add(dir.multiply(br*.8+len*.3));
            m.needle(q,q.add(dir.multiply(len*.6)),.05,.3,k%4==0?WHITE:k%4==1?PALE:k%4==2?LILAC:AZURE,sp);
        }
    }
    /** The ink edge under a burst. */
    static void burstInk(Fx c,EffectMesh m,Vec3d p,float at,double size,int seed){
        float t=c.sec;if(t<at-.01f||t>=at+.3f)return;
        float ba=Ease.smooth(t,at-.01f,at+.01f)*(float)Math.pow(1-Ease.unit(t,at,at+.2f),.7)*seen(p,c.camera);
        double br=(.62+.38*Ease.out(t,at,at+.06f))*size;
        StandPunchFx.burst(m,facing(p,c.camera),br*1.22,.36,9,seed,INK,INK,.75f*ba,.75f*ba);
    }
    /**
     * A thrown sword flying: a white-hot line along the blade, its glow and a star at its point, at its head; gone with
     * a little scatter of light where it stops. (The sword itself, a thing of light, is drawn by {@link #models}.)
     */
    static void sword(Fx c,EffectMesh m,float t,Way w,int i){
        if(t<w.at())return;
        double d=w.flown(t);float stopped=w.reach(w.end());
        if(t>=stopped+.12f)return;
        float a=(1-Ease.smooth(t,stopped,stopped+.08f));
        Vec3d head=w.point(d),tail=w.point(Math.max(0,d-SWORD_LEN)),glow=w.point(Math.max(0,d-SWORD_LEN*1.8));
        // (the videos: a white-hot blade of light, blue round it, a streak of light behind)
        m.softLine(glow,head,.55,BLUE,.32f*a);m.softLine(tail,head,.26,AZURE,.7f*a);m.line(tail,head,.09,WHITE,a);
        m.star(head,.45,WHITE,a);m.softDisc(head,.4,PALE,.55f*a);m.softDisc(tail.add(head).multiply(.5),.5,AZURE,.25f*a);
        // where it stops: a little scatter of light
        if(t>=stopped){
            float u=Ease.unit(t,stopped,stopped+.12f);
            for(int k=0;k<6;k++){
                double ang=Ease.hash(i*11+k)*6.28,rise=(Ease.hash(i*13+k)-.3);
                Vec3d dir=new Vec3d(Math.cos(ang),rise,Math.sin(ang)).normalize();
                Vec3d q=w.point(w.end()).add(dir.multiply(.15+.6*u));m.needle(q,q.add(dir.multiply(.25*(1-u))),.04,.3,k%2==0?WHITE:PALE,1-u);
            }
        }
    }
    /** How long the thrown swords are (blocks: the sword's model at {@link #SWORD_SCALE}). */
    static final double SWORD_SCALE=.5,SWORD_LEN=2.18*SWORD_SCALE;
    /**
     * The white ring with a flower in it where a sword came out (the videos: a ring that blooms there a moment after the
     * burst, then darkens to a bubble of ink with a pale rim and the flower's outline, and fades), facing the camera.
     */
    static void ring(Fx c,EffectMesh m,float t,Vec3d o,float at,int i){
        float u0=at+.06f;if(t<u0||t>=at+.62f)return;
        Vec3d[] uv=cameraPlane(m,o);if(uv==null)return;
        float vis=seen(o,c.camera),white=Ease.smooth(t,u0,u0+.03f)*(1-Ease.smooth(t,at+.12f,at+.2f))*vis,rim=Ease.smooth(t,at+.12f,at+.17f)*(1-Ease.smooth(t,at+.38f,at+.62f))*vis;
        double r=.32+.28*Ease.out(t,u0,u0+.1f)+.14*Ease.out(t,at+.12f,at+.42f);
        if(white>0){FxLib.ringPlane(m,o,uv[0],uv[1],r,.07,WHITE,.95f*white);FxLib.ringPlane(m,o,uv[0],uv[1],r,.16,AZURE,.35f*white);flower(m,o,uv,r*.55,PALE,.8f*white,i);m.star(o,r*.25,WHITE,.7f*white);}
        if(rim>0){FxLib.ringPlane(m,o,uv[0],uv[1],r*1.05,.05,PALE,.55f*rim);flower(m,o,uv,r*.6,AZURE,.55f*rim,i);}
    }
    /** A five-petalled flower's outline in the plane of {@code uv}. */
    static void flower(EffectMesh m,Vec3d o,Vec3d[] uv,double r,int rgb,float alpha,int seed){
        int n=60;Vec3d prev=null;double turn=Ease.hash(seed*7+3)*6.28;
        for(int k=0;k<=n;k++){
            double a=turn+2*Math.PI*k/n,rr=r*(.55+.45*Math.abs(Math.cos(2.5*(a-turn))));
            Vec3d p=o.add(uv[0].multiply(Math.cos(a)*rr)).add(uv[1].multiply(Math.sin(a)*rr));
            if(prev!=null)m.line(prev,p,.035,rgb,alpha);prev=p;
        }
    }
    /** Lightning along the blade as they crouch after the third throw (the videos), flickering. */
    static void lightning(Fx c,EffectMesh m,float t){
        float a=Ease.smooth(t,.38f,.44f)*(1-Ease.smooth(t,.7f,.8f));if(a<=.01f)return;
        var hb=bladeNow(c);Vec3d guard=hb[0].add(hb[1].multiply(GUARD)),tip=hb[0].add(hb[1].multiply(TIP));
        Vec3d side=hb[1].crossProduct(UP);side=side.lengthSquared()<1e-4?c.right:side.normalize();Vec3d up=side.crossProduct(hb[1]).normalize();
        int frame=(int)(t*30);
        for(int s=0;s<2;s++){
            Vec3d prev=guard;int n=7;
            for(int k=1;k<=n;k++){
                double u=k/(double)n;int seed=frame*17+s*31+k;
                Vec3d p=guard.add(tip.subtract(guard).multiply(u)).add(side.multiply((Ease.hash(seed)-.5)*.16)).add(up.multiply((Ease.hash(seed+5)-.5)*.16));
                m.line(prev,p,.035,s==0?WHITE:LILAC,.85f*a);m.softLine(prev,p,.14,AZURE,.3f*a);prev=p;
            }
        }
        m.softLine(guard,tip,.3,BLUE,.25f*a);
    }
    /**
     * The giant see-through blade thrust past them (the videos: it shows beside them at once, its point far ahead a tenth
     * of a second on, and fades as the giant sword hangs there): a long flat blade, its ridge along the middle, its point at
     * the thrust's tip; the pale body in the blended pass, the bright edges and ridge in the light.
     */
    static void ghost(Fx c,EffectMesh m,float t,boolean light){
        if(t<.02f||t>=.34f)return;
        float a=Ease.smooth(t,.02f,.06f)*(1-Ease.smooth(t,.18f,.34f));if(a<=.01f)return;
        Vec3d from=thrustFrom(c.cast);float yaw=c.cast.yaw();
        double reach=1.6+(IsosangRules.THRUST_REACH+.2-1.6)*Ease.out(t,.03f,.15f),base=.4,half=.78,high=.95;
        Vec3d b0=world(from,yaw,v(base,high,-half)),b1=world(from,yaw,v(base,high,half)),ridge0=world(from,yaw,v(base,high+.08,0));
        Vec3d s0=world(from,yaw,v(base+(reach-base)*.82,high,-half*.42)),s1=world(from,yaw,v(base+(reach-base)*.82,high,half*.42)),tip=world(from,yaw,v(reach,high,0));
        if(!light){
            m.triangle(b0,ridge0,s0,PALE,.32f*a);m.triangle(ridge0,tip,s0,PALE,.32f*a);
            m.triangle(ridge0,b1,s1,PALE,.32f*a);m.triangle(ridge0,s1,tip,PALE,.32f*a);
            return;
        }
        m.softLine(b0,s0,.35,AZURE,.35f*a);m.softLine(s0,tip,.35,AZURE,.35f*a);m.softLine(b1,s1,.35,AZURE,.35f*a);m.softLine(s1,tip,.35,AZURE,.35f*a);
        m.line(b0,s0,.06,WHITE,.8f*a);m.line(s0,tip,.06,WHITE,.8f*a);m.line(b1,s1,.06,WHITE,.8f*a);m.line(s1,tip,.06,WHITE,.8f*a);
        m.line(ridge0,tip,.08,WHITE,.9f*a);m.softLine(ridge0,tip,.5,CYAN,.3f*a);
        m.star(tip,.5,WHITE,a);
    }
    /**
     * At the giant blade's point as it strikes (the videos): a flash and a fan of long thin blue blades bursting out
     * across the thrust and back from it, and quick rings.
     */
    static void fan(Fx c,EffectMesh m,float t){
        float at=blow(IsosangRules.THRUST,0);if(t<at-.01f||t>=at+.55f)return;
        Vec3d tip=thrustTip(c.cast),f=Vec3d.fromPolar(0,c.cast.yaw()),right=new Vec3d(-f.z,0,f.x);
        float a=Ease.smooth(t,at-.01f,at+.015f),grow=Ease.out(t,at,at+.09f),fade=1-Ease.smooth(t,at+.2f,at+.55f);
        float flash=a*(1-Ease.smooth(t,at+.02f,at+.16f));
        if(flash>0){m.softDisc(tip,1.8,BLUE,.45f*flash);m.softDisc(tip,.8,PALE,.6f*flash);m.star(tip,1.5,WHITE,flash);}
        for(int k=0;k<22;k++){
            double ang=Math.PI*(.15+.7*Ease.hash(k*7+1))*(k%2==0?1:-1),back=-.15-.55*Ease.hash(k*11+2),len=(1.6+2.2*Ease.hash(k*13+3))*grow;
            Vec3d dir=right.multiply(Math.cos(ang)).add(0,Math.sin(Math.abs(ang))*(k%3==0?-.5:1),0).add(f.multiply(back)).normalize();
            Vec3d q=tip.add(dir.multiply(.2));int col=k%4==0?WHITE:k%4==1?PALE:AZURE;
            m.needle(q,q.add(dir.multiply(len)),.09,.4,col,.85f*a*fade);m.softLine(q,q.add(dir.multiply(len*.8)),.22,BLUE,.18f*a*fade);
        }
        Vec3d up=new Vec3d(0,1,0);
        for(int r=0;r<2;r++){float r0=at+r*.05f;FxLib.ringPlane(m,tip,right,up,.3+2.6*Ease.out(t,r0,r0+.25f),.09,r==0?WHITE:AZURE,.7f*a*(1-Ease.unit(t,r0,r0+.25f)));}
    }
    /** The giant sword's glow while it hangs there (round the model {@link #models} draws). */
    static void giantGlow(Fx c,EffectMesh m,float t){
        float a=giantAlpha(t);if(a<=.01f)return;
        Vec3d[] ends=giantEnds(c.cast);
        // (the videos: a sword of blue light, bright along its edges and its ridge)
        m.softLine(ends[0],ends[1],1.6,BLUE,.3f*a);m.softLine(ends[0],ends[1],.7,AZURE,.45f*a);m.line(ends[0],ends[1],.08,PALE,.6f*a);
        Vec3d mid=ends[0].add(ends[1].subtract(ends[0]).multiply(.25));m.softDisc(mid,1.0,AZURE,.25f*a);
    }
    /** How strongly the giant sword shows (0 to 1) at the hit's second {@code t}. */
    static float giantAlpha(float t){return Ease.smooth(t,GIANT_IN,GIANT_FULL)*(1-Ease.smooth(t,GIANT_FADE,GIANT_GONE));}
    /** The giant sword's pommel end and point (world). */
    static Vec3d[] giantEnds(StandClient.Cast c){
        Vec3d grip=world(thrustFrom(c),c.yaw(),v(GIANT_GRIP,GIANT_HIGH,0)),dir=giantDir(c);
        return new Vec3d[]{grip.add(dir.multiply(-5.1/16*GIANT)),grip.add(dir.multiply(29.74/16*GIANT))};
    }
    /** The way the giant sword points: along the thrust, dipping {@link #GIANT_DIP} degrees toward its point. */
    static Vec3d giantDir(StandClient.Cast c){Vec3d f=Vec3d.fromPolar(0,c.yaw());double dip=Math.toRadians(GIANT_DIP);return f.multiply(Math.cos(dip)).add(0,-Math.sin(dip),0);}
    /** Whom the thrust struck (the server's word), else those in its way as the server picks them. */
    static java.util.List<LivingEntity> thrustStruck(Fx c){
        var out=new java.util.ArrayList<LivingEntity>();var world=c.mc.world;if(world==null)return out;
        var told=IsosangClient.struck(c.cast.id(),0);
        if(told!=null){for(int id:told)if(world.getEntityById(id) instanceof LivingEntity e)out.add(e);return out;}
        if(c.sec>blow(IsosangRules.THRUST,0)+.15f)return out;
        Vec3d from=thrustFrom(c.cast),f=Vec3d.fromPolar(0,c.cast.yaw()),right=new Vec3d(-f.z,0,f.x);
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(from,from).expand(IsosangRules.THRUST_REACH+3),e->e.isAlive()&&!e.isSpectator()&&e.canHit()&&!e.getUuid().equals(c.cast.caster()))){
            Vec3d o=e.getPos().subtract(from);
            if(IsosangRules.lane(IsosangRules.THRUST,0,o.dotProduct(f),o.dotProduct(right),o.y,e.getWidth()*.5))out.add(e);
            if(out.size()>=6)break;
        }
        return out;
    }
    /** E's flash: moonlight at the sword hand, rings of light spreading over the ground round them. */
    static void flash(Fx c,EffectMesh m,float t){
        if(t>=.8f)return;
        Vec3d hand=hand(c);float a=Ease.smooth(t,0,.03f)*(1-Ease.smooth(t,.1f,.3f));
        if(a>0){m.softDisc(hand,.9,BLUE,.4f*a);m.star(hand,.8,WHITE,a);m.star(hand,.45,PALE,a);crescentMoon(m,hand,.38,Math.PI*.55+t*2,MOON,.9f*a);}
        var e=c.mc.world==null?null:c.mc.world.getPlayerByUuid(c.cast.caster());Vec3d feet=e==null?c.cast.origin():e.getLerpedPos(c.delta);
        Vec3d floor=feet.add(0,.04,0),u=new Vec3d(1,0,0),w=new Vec3d(0,0,1);
        for(int r=0;r<2;r++){float r0=r*.08f,ra=Ease.smooth(t,r0,r0+.04f)*(1-Ease.smooth(t,r0+.25f,r0+.6f));if(ra>0)FxLib.ringPlane(m,floor,u,w,.4+2.0*Ease.out(t,r0,r0+.5f),r==0?.08:.05,r==0?PALE:CYAN,.75f*ra);}
        // a soft column of light up the body as it is used
        float col=Ease.smooth(t,0,.04f)*(1-Ease.smooth(t,.12f,.4f));
        if(col>0)m.softLine(feet.add(0,.1,0),feet.add(0,2.1,0),.9,AZURE,.25f*col);
    }
    /** E's stars, rising round them in a spiral and twinkling out. */
    static void risingStars(Fx c,EffectMesh m,float t){
        var e=c.mc.world==null?null:c.mc.world.getPlayerByUuid(c.cast.caster());Vec3d feet=e==null?c.cast.origin():e.getLerpedPos(c.delta);
        for(int k=0;k<12;k++){
            int seed=k*29+11;float born=.02f+.12f*(float)Ease.hash(seed),age=t-born,life=.45f+.25f*(float)Ease.hash(seed+1);
            if(age<0||age>=life)continue;
            double ang=6.28*k/12+age*3,r=.55+.25*Ease.hash(seed+2);
            Vec3d p=feet.add(Math.cos(ang)*r,.3+2.0*Ease.out(age,0,life)*(.6+.4*Ease.hash(seed+3)),Math.sin(ang)*r);
            glint8(m,p,.1+.06*Ease.hash(seed+4),k%3==0?WHITE:k%3==1?PALE:LILAC,(1-age/life)*Ease.smooth(age,0,.04f)*twinkle(t,seed));
        }
    }

    // ───────────────────────────── the effect layer, the camera ─────────────────────────────

    static void frame(Fx c,FxFrame f){
        float t=c.sec;int combo=c.cast.combo();
        if(combo==0){float k=FxLib.kick(t,.02f,.18f);if(k>.01f)f.light(hand(c),0x8cbcff,6,.9f*k);return;}
        if(combo==IsosangRules.THROWS){
            for(int i=0;i<IsosangRules.SWORD_FROM.length;i++){
                float at=blow(IsosangRules.THROWS,i),k=FxLib.kick(t,at,.1f);if(k>.01f)f.light(outOf(c,i),0x8cbcff,5,.8f*k);
                var w=way(c,i);if(w!=null&&t>=w.at()&&t<w.reach(w.end()))f.light(w.point(w.flown(t)),0x9fc8ff,3.5,.6f);
            }
            // quick throws: short afterglow, else each sword leaves copies of itself along its way
            if(t<IsosangRules.LENGTH[IsosangRules.THROWS]/20f)f.afterglow(t<.6f?.08f:.3f);
        }else if(combo==IsosangRules.THRUST){
            float at=blow(IsosangRules.THRUST,0),k=FxLib.kick(t,at,.2f);
            if(k>.01f)f.light(thrustTip(c.cast),0xb8d8ff,10,1.5f*k);
            float g=giantAlpha(t);if(g>.01f){Vec3d[] e=giantEnds(c.cast);f.light(e[0].add(e[1]).multiply(.5),0x6aa0ff,7,.7f*g);}
            if(t<IsosangRules.LENGTH[IsosangRules.THRUST]/20f)f.afterglow(t<.3f?.06f:.3f);
        }
    }
    static void distort(Fx c,FxDistort d){}
    /** Shake: each throw a little, the thrust hard (each over two frames). */
    static float shake(StandClient.Cast cast,float sec){
        int combo=cast.combo();float v=0;
        if(combo==IsosangRules.THROWS)for(int i=0;i<3;i++)v=Math.max(v,.5f*bump(sec,blow(combo,i),.06f));
        if(combo==IsosangRules.THRUST)v=Math.max(v,2.2f*bump(sec,blow(combo,0),.14f));
        return v;
    }

    // ───────────────────────────── the swords themselves ─────────────────────────────

    private static IsosangSwordRenderer.Spectral spectral;
    private static ItemStack stack;
    /**
     * The swords of light as models (AFTER_ENTITIES): the empowered first's thrown swords flying, and the empowered
     * fourth's giant sword hanging along its thrust, its grip toward them; drawn see-through, lit through.
     */
    static void models(WorldRenderContext ctx){
        var mc=MinecraftClient.getInstance();if(mc.world==null||IsosangClient.SWINGS.isEmpty())return;
        if(spectral==null){spectral=new IsosangSwordRenderer.Spectral();stack=new ItemStack(dev.sangwoo.celestialfall.isosang.Isosang.SWORD);}
        var camera=ctx.camera().getPos();var matrices=ctx.matrixStack();var buffers=ctx.consumers();if(buffers==null)return;float delta=ctx.tickDelta();
        for(var c:IsosangClient.SWINGS.values()){
            float t=c.age(delta)/20f;
            if(c.combo()==IsosangRules.THRUST){
                float a=giantAlpha(t);if(a<=.01f)continue;
                Vec3d[] ends=giantEnds(c);Vec3d grip=world(thrustFrom(c),c.yaw(),v(GIANT_GRIP,GIANT_HIGH,0));
                double grow=.92+.08*Ease.out(t,GIANT_IN,GIANT_FULL);
                draw(matrices,buffers,camera,grip,giantDir(c),GIANT*grow,a,0xffffff);
            }else if(c.combo()==IsosangRules.THROWS){
                var told=IsosangClient.swords(c.id());if(told==null)continue;
                for(var s:told){
                    if(s==null)continue;
                    var w=new Way(s.from(),s.dir(),s.end(),s.thrown()/20f,s.landed());
                    if(t<w.at()||t>=w.reach(w.end())+.05f)continue;
                    double d=w.flown(t);float a=1-Ease.smooth(t,w.reach(w.end()),w.reach(w.end())+.05f);
                    // (its point at its head: the grip trails behind)
                    draw(matrices,buffers,camera,w.point(d).subtract(w.dir().multiply(29.74/16*SWORD_SCALE)),w.dir(),SWORD_SCALE,a,0xffffff);
                }
            }
        }
    }
    /** The sword's model with its grip at {@code grip}, pointing along {@code dir}, flat (its guard's wings level), {@code scale} times its size. */
    static void draw(net.minecraft.client.util.math.MatrixStack matrices,net.minecraft.client.render.VertexConsumerProvider buffers,Vec3d camera,Vec3d grip,Vec3d dir,double scale,float alpha,int tint){
        Vec3d y=dir.normalize(),x=y.crossProduct(UP);
        if(x.lengthSquared()<1e-4)x=new Vec3d(1,0,0);
        x=x.normalize();Vec3d z=x.crossProduct(y).normalize();
        matrices.push();
        matrices.translate(grip.x-camera.x,grip.y-camera.y,grip.z-camera.z);
        matrices.multiply(new Quaternionf().setFromNormalized(new Matrix3f(new Vector3f((float)x.x,(float)x.y,(float)x.z),new Vector3f((float)y.x,(float)y.y,(float)y.z),new Vector3f((float)z.x,(float)z.y,(float)z.z))));
        matrices.scale((float)scale,(float)scale,(float)scale);
        matrices.translate(-.5,-.51,-.5);
        IsosangSwordRenderer.Spectral.alpha=alpha;IsosangSwordRenderer.Spectral.tint=tint;
        spectral.render(stack,ModelTransformationMode.NONE,matrices,buffers,LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV);
        IsosangSwordRenderer.Spectral.alpha=1;
        matrices.pop();
    }
}
