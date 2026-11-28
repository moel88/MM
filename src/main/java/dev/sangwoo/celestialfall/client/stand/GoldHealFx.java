package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.AMBER;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.GOLD;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.PALE;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.WHITE;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.on;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.right;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.way;

/**
 * The light of Gold Experience's healing (2.149.0), drawn with its rush's on the effect layer (StandRushFx calls it pass
 * by pass): gold, and the green of new leaves (it gives life). It is one punch with the fist left in, and what it gives
 * goes in through the fist. Nothing in it is a blow's light: no spiked bursts, no ink-edged stars; soft light, rings,
 * and small crisp leaves with dark edges that rise turning about whoever is healed.
 * <ul>
 * <li>The fist glows as it is drawn and driven; as it goes in, light blooms round it with two rings opening along the
 * arm's line. While it is in, beads of light run down the arm into it and the place it is in glows.</li>
 * <li>Healed (the one tapped for, or since 2.153.0 its user, healed all at once by the hold): two rings rise round the
 * healed body from its feet to over its head, a ring runs out along the ground under it, and leaves and motes of light
 * wind up about it. (Till 2.152.0 the hold healed blow by blow: a small ring off the fist with each, leaves rising about
 * its user all the while, a ring at their feet twice a second.)</li>
 * </ul>
 * None of it over the whole screen. Every effect is a function of the healing's clock.
 */
final class GoldHealFx {
    /** The green of new leaves: its dark edge, deep, fresh, pale. */
    static final int LEAF_INK=0x0f3a16,LEAF_DEEP=0x2fae4a,LEAF=0x7df06a,LEAF_PALE=0xe2ffc8;
    /** Seconds the bloom on a healed body lasts, its rings take to rise, and the leaves about it last. */
    static final float BLOOM=.45f,RISE=.7f,LEAVES=1.1f;
    /** How many leaves and motes wind up about a body the fist healed. */
    static final int LEAVES_N=14,MOTES_N=9;
    /** How many beads run down the arm while the fist is in. */
    static final int BEADS=4;
    record Shown(GoldHeal.Heal heal,float t,StandPresence.Body body,Vec3d user,float userHeight){}
    private static final List<Shown> FRAME=new ArrayList<>();
    private GoldHealFx(){}

    /** Gathers this frame's healings (within 96 blocks of the camera); returns whether any show. */
    static boolean prepare(MinecraftClient mc,float tickDelta,Vec3d camera){
        FRAME.clear();
        if(mc.world==null)return false;
        for(var e:GoldHeal.all().entrySet()){
            var h=e.getValue();var user=mc.world.getPlayerByUuid(e.getKey());
            if(user==null||user.getPos().squaredDistanceTo(camera)>96*96)continue;
            float t=h.age(tickDelta);
            if(t<0||t>GoldHeal.guarded(h)+LEAVES+.4f)continue;
            // (the healed body is where it stood as the fist went in, moved with it if it is still there: its user, for the hold)
            if(h.touched()&&mc.world.getEntityById(h.whom) instanceof LivingEntity w&&w.isAlive())h.whomAt=w.getLerpedPos(tickDelta).add(0,w.getHeight()*.6,0);
            FRAME.add(new Shown(h,t,GoldPresence.body(e.getKey()),user.getLerpedPos(tickDelta),user.getHeight()));
        }
        return !FRAME.isEmpty();
    }
    /** How much of something at {@code p} shows this near the camera: nothing within half a block (the healed player's own eyes), all of it from 1.3. */
    static float clear(EffectMesh m,Vec3d p){return Ease.smooth((float)m.camera().distanceTo(p),.5f,1.3f);}

    // ───────────────────────────── the leaves ─────────────────────────────

    /**
     * Leaf {@code k} of {@code n} about a body, {@code u} of its life gone (0 to 1): {its angle round the body (radians),
     * how far up it is of the way from the feet to over the head (0 to 1), how far out from the body's middle (blocks),
     * how far it has turned about itself (radians), how strongly it shows}. It winds up round the body, half of them
     * each way, drifting out a little, turning as it goes.
     */
    static double[] leaf(int k,int seed,float u,double radius){
        double start=Ease.hash(seed+k*17)*Math.PI*2,way=k%2==0?1:-1;
        double angle=start+way*Math.PI*2*(.5+.4*Ease.hash(seed+k*29))*u;
        double high=.04+(.72+.3*Ease.hash(seed+k*31))*(1-(1-u)*(1-u));
        double out=radius*(.85+.3*Ease.hash(seed+k*37))+.25*u;
        double turn=Ease.hash(seed+k*41)*Math.PI*2+way*(4+3*Ease.hash(seed+k*43))*u;
        double show=Math.min(1,u/.12)*(u<.7?1:Math.max(0,1-(u-.7)/.3));
        return new double[]{angle,high,out,turn,show};
    }
    /** Where leaf {@code l} is about a body standing at {@code feet}, {@code height} tall. */
    static Vec3d leafAt(double[] l,Vec3d feet,double height){return feet.add(Math.cos(l[0])*l[2],l[1]*(height+.3),Math.sin(l[0])*l[2]);}
    /** A leaf at {@code p}: a pointed blade on a plane of its own (never turned to the camera), its dark edge under it; {@code size} its length. */
    private static void leaf(EffectMesh m,Vec3d p,double[] l,double size,float alpha,boolean ink){
        if(alpha<=0)return;
        // its stem to its tip, tilted up, turning about the upright; its blade's width across, turning with it
        double a=l[3];Vec3d along=new Vec3d(Math.cos(a)*.8,.6,Math.sin(a)*.8),across=new Vec3d(-Math.sin(a),.25*Math.cos(a*1.7),Math.cos(a)).normalize();
        Vec3d from=p.subtract(along.multiply(size*.5)),to=p.add(along.multiply(size*.5));
        if(ink){m.needle(from.subtract(along.multiply(size*.12)),to.add(along.multiply(size*.12)),across,size*.62,.42,LEAF_INK,.85f*alpha);return;}
        m.needle(from,to,across,size*.44,.42,LEAF,alpha);
        m.needle(from.add(along.multiply(size*.1)),to.subtract(along.multiply(size*.12)),across,size*.14,.45,LEAF_PALE,.9f*alpha);
    }
    private static void leaves(EffectMesh m,Vec3d feet,double height,double radius,int n,int seed,float u,float alpha,boolean ink){
        for(int k=0;k<n;k++){
            float mine=(float)((u-.25*Ease.hash(seed+k*13))/.75);if(mine<=0||mine>=1)continue;
            var l=leaf(k,seed,mine,radius);Vec3d p=leafAt(l,feet,height);
            leaf(m,p,l,.2+.08*Ease.hash(seed+k*7),(float)l[4]*alpha*clear(m,p),ink);
        }
    }

    // ───────────────────────────── the passes ─────────────────────────────

    /** Under the light: the leaves, each over its dark edge. */
    static void alpha(EffectMesh m){
        for(var s:FRAME){
            var h=s.heal;
            if(!h.touched()||h.whomAt==null||h.whom<0)continue;
            float u=(s.t-h.touchedAt)/LEAVES;if(u<0||u>=1)continue;
            leaves(m,feet(h),h.whomHeight,h.whomWidth*.5+.3,LEAVES_N,h.seed,u,1,true);
            leaves(m,feet(h),h.whomHeight,h.whomWidth*.5+.3,LEAVES_N,h.seed,u,1,false);
        }
    }
    static void additive(EffectMesh m){
        for(var s:FRAME)mend(m,s);
    }
    static Vec3d feet(GoldHeal.Heal h){return h.whomAt.subtract(0,h.whomHeight*.6,0);}
    /** The fist: its glow as it is drawn and driven, the bloom as it goes in, and while it is in the beads of light running down the arm into it. */
    static void fist(EffectMesh m,Shown s){
        var h=s.heal;float t=s.t;if(s.body==null)return;
        Vec3d fist=on(GoldHeal.fist(h,t),s.body),shoulder=on(GoldHeal.shoulder(h,t),s.body);
        float g=GoldHeal.glow(h,t),near=clear(m,fist);
        if(g>0){
            float pulse=.5f+.5f*(float)Math.sin(t*Math.PI*2*5);
            m.softDisc(fist,.3+.06*pulse,LEAF_DEEP,.3f*g*near);m.softDisc(fist,.17+.03*pulse,GOLD,.5f*g*near);m.softDisc(fist,.08,WHITE,.5f*g*near);
        }
        // driven: a short smear of light behind the fist
        if(t>GoldHeal.OUT_FROM&&t<GoldHeal.TOUCH+.08f){
            float a=Ease.smooth(t,GoldHeal.OUT_FROM+.02f,GoldHeal.OUT_FROM+.07f)*(1-Ease.smooth(t,GoldHeal.TOUCH,GoldHeal.TOUCH+.08f));
            Vec3d from=on(GoldHeal.fist(h,GoldHeal.OUT_FROM),s.body),along=fist.subtract(from);
            if(a>0&&along.lengthSquared()>1e-4){m.softTaper(from.add(along.multiply(.2)),fist,.02,.3,LEAF_DEEP,.4f*a*near);m.softTaper(from.add(along.multiply(.2)),fist,.01,.16,GOLD,.55f*a*near);}
        }
        // in: light blooms round it, two rings opening along the arm's line
        float u=(t-GoldHeal.TOUCH)/BLOOM;
        if(u>=0&&u<1&&(h.self||!h.touched()||h.whom>=0)){
            Vec3d w=fist.subtract(shoulder);if(w.lengthSquared()<1e-6)w=way(s.body.yaw());w=w.normalize();
            Vec3d r=w.crossProduct(new Vec3d(0,1,0));if(r.lengthSquared()<1e-4)r=new Vec3d(1,0,0);r=r.normalize();Vec3d up=r.crossProduct(w).normalize();
            float a=Ease.smooth(t,GoldHeal.TOUCH-.005f,GoldHeal.TOUCH+.02f)*(float)Math.pow(1-u,.8)*near;double grow=Ease.out(u,0,1);
            m.softDisc(fist,.5+.8*grow,LEAF_DEEP,.3f*a);m.softDisc(fist,.3+.45*grow,GOLD,.42f*a);m.softDisc(fist,.15+.1*grow,WHITE,.55f*a*(1-u));
            FxLib.ringPlane(m,fist.subtract(w.multiply(.15)),r,up,.18+1.0*grow,.06,PALE,.7f*a*(1-u));
            FxLib.ringPlane(m,fist.subtract(w.multiply(.3)),r,up,.12+.65*grow,.04,LEAF,.6f*a*(1-u));
        }
        // while it is in: beads of light run down the arm into the fist
        float on=GoldHeal.on(h,t);
        if(on>0)for(int k=0;k<BEADS;k++){
            double v=(t*2.6+k/(double)BEADS)%1;
            Vec3d q=shoulder.lerp(fist,.15+.85*v);float a=(float)Math.sin(v*Math.PI)*on*clear(m,q);
            m.softDisc(q,.1,k%2==0?LEAF_DEEP:AMBER,.4f*a);m.star(q,.06,k%2==0?LEAF_PALE:PALE,.9f*a);
        }
    }
    /** What blooms on whom the fist went into (another, or its user). */
    static void mend(EffectMesh m,Shown s){
        var h=s.heal;float t=s.t;
        fist(m,s);
        if(!h.touched()||h.whom<0||h.whomAt==null)return;
        float since=t-h.touchedAt;if(since<0)return;
        Vec3d feet=feet(h);double radius=h.whomWidth*.5+.32;
        // two rings rise round the body from its feet to over its head
        for(int k=0;k<2;k++){
            float v=(since-k*.14f)/RISE;if(v<0||v>=1)continue;
            double rise=1-(1-v)*(1-v);float a=Math.min(1,v/.08f)*(1-Ease.smooth(v,.55f,1));
            Vec3d c=feet.add(0,rise*(h.whomHeight+.25),0);
            FxLib.ringPlane(m,c,new Vec3d(1,0,0),new Vec3d(0,0,1),radius*(1+.12*v),.07,k==0?GOLD:LEAF,.75f*a);
            FxLib.ringPlane(m,c,new Vec3d(1,0,0),new Vec3d(0,0,1),radius*(1+.12*v),.025,k==0?PALE:LEAF_PALE,.8f*a);
        }
        // a ring runs out along the ground under it
        float g=since/.5f;
        if(g<1){double out=Ease.out(g,0,1);FxLib.ring(m,feet.add(0,.04,0),.3+1.5*out,.09*(1-.5*g),LEAF_DEEP,.6f*(1-g));FxLib.ring(m,feet.add(0,.045,0),.3+1.5*out,.03,LEAF_PALE,.7f*(1-g));}
        // motes of light wind up about it
        float l=since/LEAVES;
        if(l<1)for(int k=0;k<MOTES_N;k++){
            float mine=(float)((l-.3*Ease.hash(h.seed+k*23+5))/.7);if(mine<=0||mine>=1)continue;
            double ang=Ease.hash(h.seed+k*13+7)*Math.PI*2+(k%2==0?-1:1)*Math.PI*1.4*mine;
            Vec3d q=feet.add(Math.cos(ang)*(radius+.05),(.1+.95*mine)*(h.whomHeight+.2),Math.sin(ang)*(radius+.05));
            float a=(float)(Math.sin(mine*Math.PI))*clear(m,q);
            m.softDisc(q,.09,k%3==0?LEAF_DEEP:AMBER,.35f*a);m.star(q,.05+.03*Ease.hash(k*3),k%3==0?LEAF_PALE:PALE,.9f*a);
        }
    }
    /** The effect layer: the fist's glow and the healed body's bloom light the world about them, green-gold. */
    static void frame(FxFrame f,Vec3d camera){
        for(var s:FRAME){
            var h=s.heal;
            if(s.body!=null){float g=GoldHeal.glow(h,s.t);if(g>0)f.light(on(GoldHeal.fist(h,s.t),s.body),0xdcffb4,4,.9f*g);}
            if(h.touched()&&h.whom>=0&&h.whomAt!=null){
                float u=(s.t-h.touchedAt)/(BLOOM+.3f);
                if(u>=0&&u<1)f.light(h.whomAt,0xdcffb4,7,1.8f*(1-u));
            }
        }
    }
}
