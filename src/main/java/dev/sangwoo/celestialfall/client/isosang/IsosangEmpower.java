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
import dev.sangwoo.celestialfall.isosang.IsosangAttack;
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
 * <li>The empowered fourth (2.80.1, user request: "4타 뒤로 5블록 이동하고 그리고 검 안 날리는 거 같은데? … 파란색 검이
 * 날아가는 거 안 보여?", and their video of it at a tenth of the speed): the right arm raised as a giant sword of blue
 * light shows at their left (the sword itself, two and a half times its size, its grip low behind them, its point high
 * ahead), a leap 5 blocks back as the arm is drawn to the hip, and the throw: the sword flies on point first, levelling,
 * a white-hot streak behind it, and stops some way ahead, where a fan of blue blades bursts out of it, great strokes of
 * ink sweep round it in a pale bubble and it hangs there grip toward them a moment until ink swirls in round it and it
 * fades; whom it passes is struck and thrown the way it flew. The caster's own camera draws back for it.</li>
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

    // (2.80.3, user request: "4타 찌르는 걸 더 자연스럽게 다듬어주고 … 찌르는 모션이 너무 이상하다": the right arm stabbed
    // ahead at nothing while the giant sword flew from their left, and swung up high on its way back. Now as the user's
    // video of it shows: the sword raised a moment as the giant sword is called at their left, then down in a crouch as
    // they leap back, the left hand drawn back low by the giant sword's grip and the sword out low behind them at the right;
    // the left hand is driven forward as the giant sword is thrust, the body turning into it and leaning in, held low a
    // moment, and up into the stance.)
    /** The sword raised high at the right a moment, leaning back a little: the giant sword called at their left. */
    static final R6.Pose G_CALL=w(.02,-.02,-6,-5,-1,d(62,18),d(78,-40),d(-62,-38),12,-168);
    /** Down in a crouch as they leap back, turned to the left: the left hand drawn back low by the giant sword's grip, the sword out low behind them at the right. */
    static final R6.Pose G_COCK=w(-.04,-.2,14,9,0,d(-14,96),d(-8,138),d(-48,-142),150,-24);
    /** The thrust: the left hand driven straight ahead at the chest, the body turned into it (the left shoulder forward) and leaning in; the sword swung back behind at the right. */
    static final R6.Pose G_DRIVE=w(.1,-.24,-16,15,0,d(-10,124),d(-8,158),d(4,-6),164,-12);
    static final R6.Pose G_HOLD=w(.11,-.26,-18,15,0,d(-12,126),d(-10,160),d(2,-4),164,-12);
    /** Still low, easing up. */
    static final R6.Pose G_EASE=w(.08,-.2,-14,12,0,d(-20,118),d(-14,150),d(-6,-10),160,-16);
    /** Up out of it: the left hand down, the sword brought round in front toward the stance. */
    static final R6.Pose G_RISE=w(.04,-.07,-4,4,0,d(-46,48),d(12,22),d(-66,-30),70,-110);
    /**
     * The empowered fourth (IsosangRules.THRUST; the user's videos of E: the giant sword beside them at once, its point far
     * ahead a tenth of a second on, the giant sword there for some 0.4 s): the step back is real (IsosangClient.drive).
     */
    static final Anim THRUST=Anim.joints(
        k(0,STANCE),
        k(.04,G_CALL),
        k(.11,G_COCK),
        k(blow(IsosangRules.THRUST,0),G_DRIVE,Anim.Ease.IN),
        k(.26,G_HOLD),
        k(.55,G_EASE),
        k(.9,G_RISE),
        k(IsosangRules.LENGTH[IsosangRules.THRUST]/20f,STANCE))
        .stops(Anim.stop(blow(IsosangRules.THRUST,0),.06,.1))
        .after(IsosangSlash::grounded);

    /**
     * The giant sword (2.80.1, the user's video of the empowered fourth at a tenth of the speed: a sword of blue light at
     * their left as they leap back; a frame on it flies point first and stops some way ahead, hanging there a moment as blue
     * blades burst out of it and ink swirls round it and swallows it). 2.80.2 (user request: "검 모델링 제대로 만들어 영상이랑
     * 똑같은 검 모델로", and the outline they drew round it): its own model, SpiritSword; not the sword in their hand. 2.80.3
     * (user request: "4타 찌르는 걸 더 자연스럽게 다듬어주고 검도 더 날카롭게 만들어 주고 찌르는 모션이 너무 이상하다 검 크기도
     * 2배 더 줄이고"): half the size (3.5 blocks), sharp, and thrust: it shows at their left already aimed at where it will
     * stop and is driven point first along its own line (2.80.1-2.80.2 flew tilted 30° up, its point above its way, sliding
     * on rather than thrust). Its length from the middle of its grip to its point (blocks).
     */
    static final double GIANT_LEN=SpiritSword.TIP;
    /** Where it shows beside them: its grip by their left hip (up and right of their feet, blocks), as they leap. */
    static final double HOVER_UP=.85,HOVER_RT=-.9;
    /** How high its point aims where it stops (blocks over the feet it was thrown from): a standing body's chest. */
    static final double AIM_UP=1.25;
    /** Where the burst is when it stops: this far along it from the grip (near its point, which went in). */
    static final double BURST_ALONG=.8;
    /** When it shows (seconds into the hit); how long it hangs where it stopped before it fades, and the fading (seconds). */
    static final float GIANT_IN=.01f,GIANT_FULL=.09f,GIANT_HANG=.28f,GIANT_FADE=.2f;
    /** How far the caster's own camera draws back for the empowered fourth (blocks). */
    static final double PULL=2.0;
    static double pull(float sec){return PULL*Ease.smooth(sec,.04f,.36f)*(1-Ease.smooth(sec,.9f,1.4f));}

    /**
     * How far back the leap may carry them (IsosangAttack.room, in this client's blocks as the server finds it in its own),
     * held per swing. (The server's class is only touched with a world to look in: it would register the mod's items.)
     */
    private static final Map<UUID,Double> ROOMS=new LinkedHashMap<>();
    static double room(StandClient.Cast c){
        var r=ROOMS.get(c.id());if(r!=null)return r;
        var e=caster(c);if(e==null)return IsosangRules.BACKSTEP;
        double room=IsosangAttack.room(e.getWorld(),e,c.origin(),c.yaw());
        ROOMS.put(c.id(),room);while(ROOMS.size()>32)ROOMS.remove(ROOMS.keySet().iterator().next());
        return room;
    }
    private static Entity caster(StandClient.Cast c){var mc=MinecraftClient.getInstance();return mc==null||mc.world==null?null:mc.world.getPlayerByUuid(c.caster());}
    /** Where the leap has carried them at the hit's second {@code sec} (the plan the server and their own client follow). */
    static Vec3d leapt(StandClient.Cast c,float sec){return c.origin().subtract(Vec3d.fromPolar(0,c.yaw()).multiply(IsosangRules.backstep(sec*20,room(c))));}
    /** Where they throw the giant sword from (their feet as the leap has carried them at its blow): the server's word, else the plan. */
    static Vec3d throwFrom(StandClient.Cast c){
        var told=IsosangClient.swords(c.id());if(told!=null&&told[0]!=null)return told[0].from();
        return c.origin().subtract(Vec3d.fromPolar(0,c.yaw()).multiply(IsosangRules.backstep(IsosangRules.blowAt(IsosangRules.THRUST,0),room(c))));
    }
    /** How far ahead of where it was thrown from its point flies (the server's word, else this client's blocks as the server finds them; held). */
    private static final Map<UUID,Double> MOSTS=new LinkedHashMap<>();
    static double most(StandClient.Cast c){
        var told=IsosangClient.swords(c.id());if(told!=null&&told[0]!=null)return told[0].end();
        var m=MOSTS.get(c.id());if(m!=null)return m;
        var e=caster(c);if(e==null)return IsosangRules.GIANT_RANGE;
        double most=IsosangAttack.giantMost(e.getWorld(),e,throwFrom(c),c.yaw());
        MOSTS.put(c.id(),most);while(MOSTS.size()>32)MOSTS.remove(MOSTS.keySet().iterator().next());
        return most;
    }
    /** The second into the hit the giant sword stops. */
    static float arrive(StandClient.Cast c){return blow(IsosangRules.THRUST,0)+(float)(IsosangRules.giantArrives(most(c))/20);}
    /** Where its point stops (world): on the way they faced, as far as it flies, at the chest. */
    static Vec3d target(StandClient.Cast c){return world(throwFrom(c),c.yaw(),v(most(c),AIM_UP,0));}
    /**
     * How far ahead of their feet its grip shows (blocks) so that, aimed at {@code most} ahead, its point is just where the
     * server's way begins (IsosangRules.GIANT_START) as it is thrown: the grip, at their left by their hip, and the point it
     * aims at fix the line, and its point lies its length along it.
     */
    static double hoverFw(double most){
        double c2=(AIM_UP-HOVER_UP)*(AIM_UP-HOVER_UP)+HOVER_RT*HOVER_RT,a=Math.max(0,most-IsosangRules.GIANT_START),x=a+GIANT_LEN;
        for(int i=0;i<6;i++)x=a+GIANT_LEN*x/Math.sqrt(x*x+c2);
        return most-x;
    }
    /** Its grip as it shows beside them at the hit's second {@code sec} (world): their left hip as the leap carries them. */
    static Vec3d hoverGrip(StandClient.Cast c,float sec){return world(leapt(c,sec),c.yaw(),v(hoverFw(most(c)),HOVER_UP,HOVER_RT));}
    /** The way it points, from its grip at {@code grip} to where its point will stop. */
    static Vec3d aimFrom(StandClient.Cast c,Vec3d grip){return target(c).subtract(grip).normalize();}
    /** The way it is thrust (from its grip as it is thrown to where its point stops). */
    static Vec3d aim(StandClient.Cast c){return aimFrom(c,world(throwFrom(c),c.yaw(),v(hoverFw(most(c)),HOVER_UP,HOVER_RT)));}
    /** Where its grip is when it stops (world): its length back along its way from where its point stopped. */
    static Vec3d gripAtEnd(StandClient.Cast c){return target(c).subtract(aim(c).multiply(GIANT_LEN));}
    /** Where it bursts when it stops: near its point (world). */
    static Vec3d end(StandClient.Cast c){return gripAtEnd(c).add(aim(c).multiply(GIANT_LEN*BURST_ALONG));}
    /** Whether the giant sword flies: the server threw it, or the swing was not stopped short before it could. */
    static boolean flies(StandClient.Cast c){var told=IsosangClient.swords(c.id());return told!=null&&told[0]!=null||!IsosangClient.stopped(c.id());}
    /** The giant sword as drawn: the middle of its grip, the way it points (grip to point), how strongly it shows and its size (1 its full size). */
    record Giant(Vec3d grip,Vec3d dir,float alpha,double scale){
        Vec3d point(){return grip.add(dir.multiply(GIANT_LEN*scale));}
        Vec3d butt(){return grip.add(dir.multiply(SpiritSword.POMMEL*scale));}
        Vec3d at(double along){return grip.add(dir.multiply(along*scale));}
    }
    /** Where its point is as it is thrust (beside them, aimed at where it stops). */
    static Vec3d thrownTip(StandClient.Cast c){return world(throwFrom(c),c.yaw(),v(hoverFw(most(c)),HOVER_UP,HOVER_RT)).add(aim(c).multiply(GIANT_LEN));}
    /**
     * The giant sword at the hit's second {@code t} (null before it shows, once it is gone, or if the hit stopped short
     * before it was thrown): beside them at their left as they leap, coming in, aimed at where it will stop (the way they
     * faced, at the chest); thrust, it is driven point first along its own line, its point at the server's pace from where
     * it was to where it stops (IsosangRules.giantHead); a jolt back as it stops, then it hangs there a moment, bobbing a
     * little, and fades as the ink swallows it.
     */
    static Giant giant(StandClient.Cast c,float t){
        float show=Ease.smooth(t,GIANT_IN,GIANT_FULL);if(show<=0||!flies(c))return null;
        float at=blow(IsosangRules.THRUST,0),arr=arrive(c);
        float alpha=show*(1-Ease.smooth(t,arr+GIANT_HANG,arr+GIANT_HANG+GIANT_FADE));if(alpha<=0)return null;
        double scale=.9+.1*show;
        if(t<at){Vec3d grip=hoverGrip(c,t);return new Giant(grip,aimFrom(c,grip),alpha,scale);}
        Vec3d dir=aim(c),f=Vec3d.fromPolar(0,c.yaw());double most=most(c);
        Vec3d grip0=world(throwFrom(c),c.yaw(),v(hoverFw(most),HOVER_UP,HOVER_RT));
        // its point's way ahead is the server's (giantHead), along its own line
        double head=IsosangRules.giantHead((t-at)*20,most),along=(head-IsosangRules.GIANT_START)/Math.max(1e-6,dir.dotProduct(f));
        Vec3d grip=grip0.add(dir.multiply(Math.max(0,along)));
        grip=grip.subtract(dir.multiply(.18*bump(t,arr,.05f))).add(0,.03*Math.sin(Math.max(0,t-arr)*7.5),0);
        return new Giant(grip,dir,alpha,scale);
    }
    /** When (the hit's second) the giant sword reached {@code e}: as its point passed where they stood as it struck them (the server's word), else where they are. */
    static float struckAt(Fx c,LivingEntity e){
        Vec3d pos=e.getLerpedPos(c.delta);for(var tg:c.cast.targets())if(tg.entity()==e.getId()){pos=tg.position();break;}
        double fw=pos.subtract(throwFrom(c.cast)).dotProduct(Vec3d.fromPolar(0,c.cast.yaw()));
        return blow(IsosangRules.THRUST,0)+(float)(IsosangRules.giantReaches(fw,e.getWidth()*.5)/20);
    }

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
            if(!flies(c.cast))return;
            float yaw=c.cast.yaw(),at=blow(IsosangRules.THRUST,0),arr=arrive(c.cast),shift=arr-at;
            Vec3d from=throwFrom(c.cast),f=Vec3d.fromPolar(0,yaw),right=new Vec3d(-f.z,0,f.x);double most=most(c.cast);
            Vec3d burst=end(c.cast),endFeet=new Vec3d(burst.x,from.y,burst.z);
            // great strokes of ink round where it stopped (the videos: a black crescent over it from the left, another at
            // the right), then ink swirling in round it as it fades (2.80.1); their times as if it stopped at the blow
            for(var s:STROKES)stroke(endFeet,yaw,s,t-shift);
            for(var s:VORTEX)stroke(endFeet,yaw,s,t-shift);
            // a long dark streak under its way, laid deep blue as its point passes and drying black
            int n=8;double a0=.8,a1=most-.3;
            for(int k=0;k<n&&a1>a0;k++){
                double p0=a0+(a1-a0)*k/n,p1=a0+(a1-a0)*(k+1)/n;float pass=at+(float)(IsosangRules.giantReaches((p0+p1)*.5,0)/20);
                if(t<pass-.01f)break;
                float erase=Ease.smooth(t,pass+.45f,pass+.85f);if(erase>=1)continue;
                strip(floor(c,world(from,yaw,v(p0,0,0))),floor(c,world(from,yaw,v(p1,0,0))),right,.75,3,13+k,erase,.3f+.7f*Ease.smooth(t,pass+.04f,pass+.32f),.8f*Ease.smooth(t,pass-.01f,pass+.04f));
            }
            // ink thrown down round where it stopped, a pool spreading under it (2.80.2)
            Vec3d tip=floor(c,end(c.cast));
            splatAt(tip,1.4,yaw*Math.PI/180,1.5,77,t-arr-.03f,.85f);splatAt(floor(c,gripAtEnd(c.cast)),.9,yaw*Math.PI/180+.6,1.4,79,t-arr-.06f,.75f);
            for(int k=0;k<7;k++){
                double ang=Ease.hash(k*7+5)*6.28,r=.6+1.5*Ease.hash(k*11+3);
                splatAt(tip.add(Math.cos(ang)*r,0,Math.sin(ang)*r),.45+.35*Ease.hash(k*13),ang,1.3,k*17+5,t-arr-.05f-.04f*(float)Ease.hash(k*19),.8f);
            }
            for(var e:thrustStruck(c)){float hit=struckAt(c,e);for(int k=0;k<3;k++){Vec3d p=floor(c,e.getLerpedPos(c.delta));splatAt(p.add((Ease.hash(k*23+e.getId())-.5)*1.2,0,(Ease.hash(k*29+e.getId())-.5)*1.2),.5,k,1.2,k*31+e.getId(),t-hit-.06f,.7f);}}
        }
    }
    /** A stroke of ink at {@code t} (its own clock), by a hit at {@code feet} facing {@code yaw}. */
    private static void stroke(Vec3d feet,float yaw,Arc s,float t){
        var sw=band(feet,yaw,s,t);
        if(sw!=null&&t<s.eat1())KagenoFire.inkRibbon(sw.inner(),sw.outer(),sw.along(),sw.n(),s.style(),s.seed(),Ease.smooth(t,s.eat0(),s.eat1()),inks(s,sw,t),Ease.smooth(t,s.t0(),s.t0()+.015f));
    }
    /**
     * The strokes of ink round where the giant sword stopped, in the frame of the ground under its burst facing the way it
     * flew, timed as if it stopped at the blow (0.15 s): one sweeping over it from the left, its plane standing up across
     * its way and leaning back; one low round its right; two great crescents over the ground.
     */
    static final Arc[] STROKES={
        // (2.80.3: smaller, round a sword half the size, lying level at the chest)
        arc(v(-.45,.95,0),70,180,1.7,2.35,-150,-20,.155f,.22f,.62f,.9f,.17f,.32f,9,3,2),
        arc(v(-.9,.4,.22),15,90,1.45,2.05,20,150,.17f,.24f,.66f,.95f,.19f,.36f,9,9,2),
        // (2.80.2, the video: a great black crescent sweeping the ground round it from the left, and a lesser one at its right)
        arc(v(0,.1,0),6,0,2.6,3.5,-210,15,.155f,.25f,.72f,1.02f,.16f,.3f,9,15,2),
        arc(v(.45,.15,0),9,180,2.0,2.6,40,205,.17f,.26f,.74f,1.04f,.18f,.32f,9,45,2)};
    /**
     * Ink swirling in round it as it fades (2.80.1, the video: dark strokes whirling round the sword a third of a second
     * after it stopped, swallowing it): strokes sweeping round it at even speed, low to high, each drying black at once
     * (2.80.3: round a sword half the size, lying level at the chest).
     */
    static final Arc[] VORTEX={
        arc(v(0,1.25,0),12,40,1.05,1.75,-60,170,.43f,.53f,.56f,.74f,.43f,.47f,9,21,1),
        arc(v(0,.85,0),18,220,1.2,1.95,120,350,.46f,.56f,.6f,.78f,.46f,.5f,9,27,1),
        arc(v(0,1.65,0),25,300,.9,1.5,200,420,.49f,.58f,.62f,.8f,.49f,.53f,9,33,1),
        arc(v(0,.5,0),8,120,1.3,2.05,10,250,.5f,.6f,.64f,.84f,.5f,.54f,9,39,1),
        arc(v(0,2.0,0),20,90,.75,1.3,300,520,.52f,.61f,.66f,.84f,.52f,.56f,9,51,1),
        arc(v(0,1.05,0),15,270,1.45,2.1,-20,200,.54f,.63f,.68f,.86f,.54f,.58f,9,57,1)};

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
            // (2.80.2) the giant sword itself: its own model of blue light (SpiritSword)
            var g=giant(c.cast,t);
            if(g!=null)SpiritSword.body(m,c.camera,g.grip(),g.dir(),g.scale(),g.alpha(),heat(t),ink(c.cast,t));
            if(!flies(c.cast))return;
            for(var sh:shards(c,t))shardBody(m,sh);
            int k=0;for(var e:thrustStruck(c)){float at=struckAt(c,e);burstInk(c,m,chestOf(c,e,at),at,.85,k++*13);}
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
            giantGlow(c,m,t);
            if(!flies(c.cast))return;
            trail(c,m,t);
            var g=giant(c.cast,t);if(g!=null)speed(c,m,t,g);
            fan(c,m,t);
            for(var sh:shards(c,t))shardGlow(m,sh);
            streaks(c,m,t);
            bubble(c,m,t);
            int k=0;Vec3d f=Vec3d.fromPolar(0,c.cast.yaw());
            for(var e:thrustStruck(c)){float at=struckAt(c,e);burst(c,m,chestOf(c,e,at),f,at,.85,k++*13);}
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
            if(!flies(c.cast))return;
            float at=arrive(c.cast);Vec3d tip=end(c.cast);
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
     * The streak of light the giant sword leaves as it flies (the videos: a long white-hot streak from where it was thrown
     * to it, a star at its point), drawn in from its tail as it goes and gone a moment after it stops.
     */
    static void trail(Fx c,EffectMesh m,float t){
        float at=blow(IsosangRules.THRUST,0),arr=arrive(c.cast);if(t<at||t>=arr+.16f)return;
        var g=giant(c.cast,t);if(g==null)return;
        Vec3d tip0=thrownTip(c.cast),head=g.point();
        Vec3d tail=tip0.add(head.subtract(tip0).multiply(Ease.smooth(t,at+.02f,arr+.14f)));
        float a=1-Ease.smooth(t,arr+.02f,arr+.16f),fly=1-Ease.smooth(t,arr,arr+.05f);
        m.softLine(tail,head,.7,BLUE,.3f*a);m.softLine(tail,head,.3,AZURE,.6f*a);m.line(tail,head,.07,WHITE,.95f*a);
        if(fly>0){m.softLine(g.grip(),head,.45,AZURE,.25f*fly);m.star(head,.38,WHITE,.85f*fly);m.softDisc(head,.32,PALE,.4f*fly);}
    }
    /** A see-through bubble round where it stopped (the video: a pale sphere's rim round the bursting blades), its rim facing the camera. */
    static void bubble(Fx c,EffectMesh m,float t){
        float arr=arrive(c.cast),a=Ease.smooth(t,arr+.08f,arr+.16f)*(1-Ease.smooth(t,arr+.4f,arr+.6f));if(a<=.01f)return;
        Vec3d o=end(c.cast).add(0,-.2,0);Vec3d[] uv=cameraPlane(m,o);if(uv==null)return;
        double r=1.9+.4*Ease.out(t,arr+.08f,arr+.6f);float vis=seen(o,c.camera);
        FxLib.ringPlane(m,o,uv[0],uv[1],r,.05,PALE,.35f*a*vis);FxLib.ringPlane(m,o,uv[0],uv[1],r*.98,.22,AZURE,.08f*a*vis);m.softDisc(o,r,AZURE,.05f*a*vis);
    }
    /**
     * Where the giant sword stops (the videos): a flash and a fan of long thin blue blades bursting out across its way and
     * back from it, and quick rings.
     */
    static void fan(Fx c,EffectMesh m,float t){
        float at=arrive(c.cast);if(t<at-.01f||t>=at+.4f)return;
        Vec3d tip=end(c.cast),f=Vec3d.fromPolar(0,c.cast.yaw()),right=new Vec3d(-f.z,0,f.x);
        float a=Ease.smooth(t,at-.01f,at+.015f),grow=Ease.out(t,at,at+.09f),fade=1-Ease.smooth(t,at+.14f,at+.4f);
        float flash=a*(1-Ease.smooth(t,at+.02f,at+.16f));
        if(flash>0){m.softDisc(tip,1.1,BLUE,.3f*flash);m.softDisc(tip,.42,PALE,.35f*flash);m.star(tip,.62,WHITE,.6f*flash);}
        for(int k=0;k<10;k++){
            double ang=Math.PI*(.15+.7*Ease.hash(k*7+101))*(k%2==0?1:-1),back=-.15-.55*Ease.hash(k*11+102),len=(.8+1.05*Ease.hash(k*13+103))*grow;
            Vec3d dir=right.multiply(Math.cos(ang)).add(0,Math.sin(Math.abs(ang))*(k%3==0?-.5:1),0).add(f.multiply(back)).normalize();
            Vec3d q=tip.add(dir.multiply(.14));int col=k%3==0?PALE:AZURE;
            m.needle(q,q.add(dir.multiply(len)),.065,.4,col,.6f*a*fade);m.softLine(q,q.add(dir.multiply(len*.8)),.15,BLUE,.14f*a*fade);
        }
        Vec3d up=new Vec3d(0,1,0);
        for(int r=0;r<2;r++){float r0=at+r*.05f;FxLib.ringPlane(m,tip,right,up,.22+1.7*Ease.out(t,r0,r0+.25f),.06,r==0?PALE:AZURE,.5f*a*(1-Ease.unit(t,r0,r0+.25f)));}
    }
    /** How white-hot the giant sword is at the hit's second {@code t}: as it shows beside them (the video: a blade of white light), cooling as it is thrown. */
    static float heat(float t){float at=blow(IsosangRules.THRUST,0);return .26f*(1-Ease.smooth(t,at-.02f,at+.12f));}
    /** How far the ink has swallowed it: from just before it fades, as the ink swirls in round it. */
    static float ink(StandClient.Cast c,float t){float arr=arrive(c);return Ease.smooth(t,arr+GIANT_HANG-.06f,arr+GIANT_HANG+GIANT_FADE);}
    /**
     * The giant sword's light (2.80.2): its own white-hot edges and glow (SpiritSword.glow), the flash it shows out of and
     * the light gathering into it as it does.
     */
    static void giantGlow(Fx c,EffectMesh m,float t){
        var g=giant(c.cast,t);if(g==null)return;
        SpiritSword.glow(m,g.grip(),g.dir(),g.scale(),g.alpha(),heat(t),ink(c.cast,t));
        Vec3d mid=g.at(GIANT_LEN*.45);
        // it comes out of a flash of light, sparks drawn in to its blade
        float pop=FxLib.kick(t,GIANT_IN,.1f);if(pop>.01f){m.star(mid,.45,WHITE,.55f*pop);m.softDisc(mid,.7,PALE,.22f*pop);}
        for(int k=0;k<10;k++){
            float t0=.06f*(float)Ease.hash(k*71+3),life=.1f,age=t-t0;if(age<0||age>=life)continue;
            Vec3d target=g.at(SpiritSword.BLADE_BASE+(GIANT_LEN-SpiritSword.BLADE_BASE)*Ease.hash(k*73));
            Vec3d from=target.add(new Vec3d(Ease.hash(k*79)-.5,Ease.hash(k*83)-.3,Ease.hash(k*89)-.5).normalize().multiply(1.15+.85*Ease.hash(k*97)));
            float u=Ease.unit(t,t0,t0+life);u=u*u;
            Vec3d q=from.add(target.subtract(from).multiply(u)),tail=from.add(target.subtract(from).multiply(Math.max(0,u-.4f)));
            m.line(tail,q,.028,k%2==0?WHITE:PALE,.65f*(1-.5f*u));m.softLine(tail,q,.1,AZURE,.2f);
        }
    }
    /** Lines of speed streaming past it as it flies (2.80.2): thin and pale, along its way. */
    static void speed(Fx c,EffectMesh m,float t,Giant g){
        float at=blow(IsosangRules.THRUST,0),arr=arrive(c.cast);if(t<at||t>=arr+.12f)return;
        float a=Ease.smooth(t,at,at+.02f)*(1-Ease.smooth(t,arr,arr+.12f));
        Vec3d dir=Vec3d.fromPolar(0,c.cast.yaw()),side=new Vec3d(-dir.z,0,dir.x),mid=g.at(GIANT_LEN*.5);
        for(int k=0;k<10;k++){
            double off=(Ease.hash(k*53)-.5)*2.2,h=(Ease.hash(k*59)-.4)*1.7,len=1.6+1.8*Ease.hash(k*61),lag=1.2*Ease.hash(k*67);
            Vec3d q=mid.add(side.multiply(off)).add(0,h,0).subtract(dir.multiply(lag));
            m.line(q.subtract(dir.multiply(len)),q,.03,k%3==0?WHITE:PALE,.6f*a);
        }
    }
    /**
     * A blue crystal blade burst out of the giant sword where it stops (2.80.2, the video: long flat blades fanning out
     * across its way and back from it, see-through, white along their ridges): a flat diamond fixed in the world, from its
     * base to its tip, {@code width} across in the plane of {@code side}.
     */
    record Shard(Vec3d base,Vec3d tip,Vec3d side,double width,float alpha){}
    static java.util.List<Shard> shards(Fx c,float t){
        var out=new java.util.ArrayList<Shard>();float at=arrive(c.cast);if(t<at-.01f||t>=at+.44f)return out;
        Vec3d p=end(c.cast),f=Vec3d.fromPolar(0,c.cast.yaw()),right=new Vec3d(-f.z,0,f.x),up=new Vec3d(0,1,0);
        float grow=Ease.out(t,at,at+.07f),fade=Ease.smooth(t,at-.01f,at+.01f)*(1-Ease.smooth(t,at+.16f,at+.44f));
        for(int k=0;k<12;k++){
            double ang=Math.PI*(.1+.8*Ease.hash(k*7+1))*(k%2==0?1:-1),back=-.1-.55*Ease.hash(k*11+2),rise=(k%3==0?-.45:.8)*Math.sin(Math.abs(ang));
            Vec3d d=right.multiply(Math.cos(ang)).add(up.multiply(rise)).add(f.multiply(back)).normalize();
            double len=(1.0+1.2*Ease.hash(k*13+3))*grow,drift=.3*Ease.out(t,at+.05f,at+.44f);
            Vec3d base=p.add(d.multiply(.25+drift)),tip=base.add(d.multiply(len));
            Vec3d side=d.crossProduct(new Vec3d(Ease.hash(k*17)-.5,Ease.hash(k*19)-.5,Ease.hash(k*23)-.5));
            if(side.lengthSquared()<1e-4)side=d.crossProduct(up);
            out.add(new Shard(base,tip,side.normalize(),.22+.16*Ease.hash(k*29),fade));
        }
        return out;
    }
    /** A crystal blade's faces (the blended pass): light blue, see-through, a little darker toward its base. */
    static void shardBody(EffectMesh m,Shard s){
        if(s.alpha()<=.01f||s.tip().distanceTo(s.base())<.05)return;
        Vec3d mid=s.base().add(s.tip().subtract(s.base()).multiply(.32)),l=mid.add(s.side().multiply(s.width()*.5)),r=mid.subtract(s.side().multiply(s.width()*.5));
        m.triangle(s.base(),l,s.tip(),0x7cccff,.62f*s.alpha());m.triangle(s.base(),s.tip(),r,0x4f9ff0,.62f*s.alpha());
    }
    /** A crystal blade's light: white along its ridge, blue along its edges, a glow round it. */
    static void shardGlow(EffectMesh m,Shard s){
        if(s.alpha()<=.01f||s.tip().distanceTo(s.base())<.05)return;
        Vec3d mid=s.base().add(s.tip().subtract(s.base()).multiply(.32)),l=mid.add(s.side().multiply(s.width()*.5)),r=mid.subtract(s.side().multiply(s.width()*.5));
        float a=s.alpha();
        m.line(s.base(),s.tip(),.028,WHITE,.55f*a);m.softLine(s.base(),s.tip(),.45,BLUE,.16f*a);
        m.line(s.base(),l,.024,PALE,.5f*a);m.line(l,s.tip(),.024,PALE,.5f*a);m.line(s.base(),r,.024,AZURE,.45f*a);m.line(r,s.tip(),.024,AZURE,.45f*a);
    }
    /** White streaks shot out from where it stops (2.80.2, the video: thin white lines flying out a moment after the blades). */
    static void streaks(Fx c,EffectMesh m,float t){
        float at=arrive(c.cast);if(t<at||t>=at+.75f)return;
        Vec3d p=end(c.cast);
        for(int k=0;k<12;k++){
            float t0=at+.02f+.14f*(float)Ease.hash(k*31+7),life=.28f+.2f*(float)Ease.hash(k*37),age=t-t0;
            if(age<0||age>=life)continue;
            double th=Ease.hash(k*41)*6.283,ph=(Ease.hash(k*43)-.4)*1.1;
            Vec3d d=new Vec3d(Math.cos(th)*Math.cos(ph),Math.sin(ph),Math.sin(th)*Math.cos(ph)).normalize();
            double r=.8+4.5*Ease.out(age,0,life),len=.4+.6*Ease.hash(k*47);
            Vec3d a=p.add(d.multiply(r)),b=p.add(d.multiply(r+len));
            float alpha=(1-age/life)*Ease.smooth(age,0,.03f);
            m.line(a,b,.03,WHITE,.7f*alpha);m.softLine(a,b,.18,AZURE,.22f*alpha);
        }
    }
    /**
     * Whom the giant sword struck (the server's word, told tick by tick as it strikes), and until it has flown its way
     * those in its way the server has not told of yet, as the server picks them.
     */
    static java.util.List<LivingEntity> thrustStruck(Fx c){
        var out=new java.util.ArrayList<LivingEntity>();var world=c.mc.world;if(world==null)return out;
        var told=IsosangClient.struck(c.cast.id(),0);float arr=arrive(c.cast);
        if(told!=null)for(int id:told)if(world.getEntityById(id) instanceof LivingEntity e)out.add(e);
        if(c.sec>arr+.15f&&told!=null||c.sec>arr+.3f)return out;
        Vec3d from=throwFrom(c.cast),f=Vec3d.fromPolar(0,c.cast.yaw()),right=new Vec3d(-f.z,0,f.x);double most=most(c.cast);
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(from,from).expand(most+3),e->e.isAlive()&&!e.isSpectator()&&e.canHit()&&!e.getUuid().equals(c.cast.caster())
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            if(out.contains(e))continue;
            Vec3d o=e.getPos().subtract(from);
            if(IsosangRules.giantOver(1e9,most,o.dotProduct(f),o.dotProduct(right),o.y,e.getWidth()*.5))out.add(e);
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
            var g=giant(c.cast,t);if(g!=null)f.light(g.grip().add(g.dir().multiply(GIANT_LEN*.5)),0x6aa0ff,7,.7f*g.alpha());
            if(!flies(c.cast))return;
            float at=blow(IsosangRules.THRUST,0),arr=arrive(c.cast),k=FxLib.kick(t,arr,.2f);
            if(k>.01f)f.light(end(c.cast),0xb8d8ff,10,1.5f*k);
            // fast as it flies: short afterglow, else the sword leaves copies of itself along its way
            if(t<IsosangRules.LENGTH[IsosangRules.THRUST]/20f)f.afterglow(t<arr+.08f?.05f:.3f);
        }
    }
    static void distort(Fx c,FxDistort d){}
    /** Shake: each throw a little; the giant sword's throw a little and its stop hard (each over two frames). */
    static float shake(StandClient.Cast cast,float sec){
        int combo=cast.combo();float v=0;
        if(combo==IsosangRules.THROWS)for(int i=0;i<3;i++)v=Math.max(v,.5f*bump(sec,blow(combo,i),.06f));
        if(combo==IsosangRules.THRUST){v=Math.max(v,.9f*bump(sec,blow(combo,0),.08f));if(flies(cast))v=Math.max(v,2.2f*bump(sec,arrive(cast),.14f));}
        return v;
    }

    // ───────────────────────────── the swords themselves ─────────────────────────────

    private static IsosangSwordRenderer.Spectral spectral;
    private static ItemStack stack;
    /**
     * The swords of light as models (AFTER_ENTITIES): the empowered first's thrown swords flying, drawn see-through, lit
     * through. (The empowered fourth's giant sword is its own model in the effect layer since 2.80.2: SpiritSword.)
     */
    static void models(WorldRenderContext ctx){
        var mc=MinecraftClient.getInstance();if(mc.world==null||IsosangClient.SWINGS.isEmpty())return;
        if(spectral==null){spectral=new IsosangSwordRenderer.Spectral();stack=new ItemStack(dev.sangwoo.celestialfall.isosang.Isosang.SWORD);}
        var camera=ctx.camera().getPos();var matrices=ctx.matrixStack();var buffers=ctx.consumers();if(buffers==null)return;float delta=ctx.tickDelta();
        for(var c:IsosangClient.SWINGS.values()){
            float t=c.age(delta)/20f;
            if(c.combo()==IsosangRules.THROWS){
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
