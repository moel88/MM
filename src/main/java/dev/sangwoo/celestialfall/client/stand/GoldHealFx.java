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
 * by pass): gold, and the green of new leaves (it gives life). Nothing in it is a blow's light: no spiked bursts, no
 * ink-edged stars; soft light, rings, and small crisp leaves with dark edges that rise turning about whoever is healed.
 * <ul>
 * <li>Tapped: the hand glows as it goes out; as it is laid on, light blooms where it rests, two rings rise round the
 * healed body from its feet to over its head, a ring runs out along the ground under it, and leaves and motes of light
 * wind up about it.</li>
 * <li>Held: every fist that lands on its user leaves a small soft spark of gold and green where it stops; while it
 * lasts leaves rise turning about them, a ring opens at their feet twice a second and a soft light sits on them.</li>
 * </ul>
 * None of it over the whole screen. Every effect is a function of the healing's clock.
 */
final class GoldHealFx {
    /** The green of new leaves: its dark edge, deep, fresh, pale. */
    static final int LEAF_INK=0x0f3a16,LEAF_DEEP=0x2fae4a,LEAF=0x7df06a,LEAF_PALE=0xe2ffc8;
    /** Seconds the bloom on a healed body lasts, its rings take to rise, and the leaves about it last. */
    static final float BLOOM=.45f,RISE=.7f,LEAVES=1.1f;
    /** How many leaves and motes wind up about a body the hand healed; how many rise at a time about one being healed blow by blow. */
    static final int LEAVES_N=14,MOTES_N=9,SELF_LEAVES=10;
    /** Seconds a fist's spark on its user lasts. */
    static final float SPARK=.14f;
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
            if(t<0||t>(h.self?h.ends():GoldHeal.LENGTH)+LEAVES+.4f)continue;
            // (the tap's healed body is where it stood as the hand touched it, moved with it if it is still there)
            if(!h.self&&h.touched()&&mc.world.getEntityById(h.whom) instanceof LivingEntity w&&w.isAlive())h.whomAt=w.getLerpedPos(tickDelta).add(0,w.getHeight()*.6,0);
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
    /** The leaves that rise about one healed blow by blow: each over and over, {@code clock} seconds on. */
    private static void risingLeaves(EffectMesh m,Vec3d feet,double height,int seed,float clock,float alpha,boolean ink){
        for(int k=0;k<SELF_LEAVES;k++){
            double period=.95+.35*Ease.hash(seed+k*11),at=clock/period+Ease.hash(seed+k*19);
            int round=(int)Math.floor(at);float u=(float)(at-round);
            var l=leaf(k,seed+round*53,u,.5);Vec3d p=leafAt(l,feet,height);
            leaf(m,p,l,.17+.07*Ease.hash(seed+k*7),(float)l[4]*alpha*clear(m,p),ink);
        }
    }

    // ───────────────────────────── the passes ─────────────────────────────

    /** Under the light: the leaves, each over its dark edge. */
    static void alpha(EffectMesh m){
        for(var s:FRAME){
            var h=s.heal;
            if(h.self){
                float on=selfOn(s);if(on<=0)continue;
                risingLeaves(m,s.user,s.userHeight,h.seed,s.t,on,true);risingLeaves(m,s.user,s.userHeight,h.seed,s.t,on,false);
                continue;
            }
            if(!h.touched()||h.whomAt==null||h.whom<0)continue;
            float u=(s.t-h.touchedAt)/LEAVES;if(u<0||u>=1)continue;
            leaves(m,feet(h),h.whomHeight,h.whomWidth*.5+.3,LEAVES_N,h.seed,u,1,true);
            leaves(m,feet(h),h.whomHeight,h.whomWidth*.5+.3,LEAVES_N,h.seed,u,1,false);
        }
    }
    static void additive(EffectMesh m){
        for(var s:FRAME){
            if(s.heal.self)self(m,s);else mend(m,s);
        }
    }
    static Vec3d feet(GoldHeal.Heal h){return h.whomAt.subtract(0,h.whomHeight*.6,0);}
    /** How much the light about one being healed blow by blow is on: with the barrage, and a moment after its last blow. */
    static float selfOn(Shown s){
        var h=s.heal;float end=h.ends();
        return Ease.smooth(s.t,GoldRush.RISE*.6f,GoldRush.RISE+.1f)*(1-Ease.smooth(s.t,end,end+.5f));
    }

    /** Tapped: the hand's glow, and what blooms on whom it is laid. */
    static void mend(EffectMesh m,Shown s){
        var h=s.heal;float t=s.t;
        if(s.body!=null){
            float g=GoldHeal.glow(h,t);
            if(g>0){
                Vec3d hand=on(GoldHeal.hand(h,t),s.body);float pulse=.5f+.5f*(float)Math.sin(t*Math.PI*2*4);
                m.softDisc(hand,.3+.06*pulse,LEAF_DEEP,.3f*g);m.softDisc(hand,.17+.03*pulse,GOLD,.5f*g);m.softDisc(hand,.08,WHITE,.5f*g);
                for(int k=0;k<6;k++){
                    double a=t*(5+2*Ease.hash(k*7+h.seed))+k*Math.PI/3,b=(Ease.hash(k*11)-.5)*1.4;
                    Vec3d q=hand.add(Math.cos(a)*Math.cos(b)*.22,Math.sin(b)*.22,Math.sin(a)*Math.cos(b)*.22);
                    m.star(q,.045,k%2==0?LEAF_PALE:PALE,.8f*g);
                }
            }
        }
        if(!h.touched())return;
        float since=t-h.touchedAt;if(since<0)return;
        if(h.whom<0||h.whomAt==null){
            // laid on no one (they had gone): the hand's light goes out in a small puff
            if(s.body!=null&&since<.25f){float a=1-since/.25f;Vec3d hand=on(GoldHeal.hand(h,h.touchedAt),s.body);m.softDisc(hand,.2+.5*since,LEAF_DEEP,.3f*a);}
            return;
        }
        Vec3d at=h.whomAt,feet=feet(h);double radius=h.whomWidth*.5+.32;
        Vec3d w=way(h.yaw()),r=right(w),up=new Vec3d(0,1,0);
        // light blooms where the hand rests
        float u=since/BLOOM;
        if(u<1){
            float a=Ease.smooth(since,0,.03f)*(float)Math.pow(1-u,.8),near=clear(m,at);
            double grow=Ease.out(u,0,1);
            m.softDisc(at,.55+.9*grow,LEAF_DEEP,.3f*a*near);m.softDisc(at,.35+.5*grow,GOLD,.42f*a*near);m.softDisc(at,.16+.1*grow,WHITE,.55f*a*(1-u)*near);
            FxLib.ringPlane(m,at.subtract(w.multiply(.2)),r,up,.2+1.1*grow,.06,PALE,.7f*a*(1-u)*near);
            FxLib.ringPlane(m,at.subtract(w.multiply(.2)),r,up,.12+.7*grow,.04,LEAF,.6f*a*(1-u)*near);
        }
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
    /** Held: each fist's soft spark where it stops on its user, a ring opening at their feet twice a second, a soft light on them. */
    static void self(EffectMesh m,Shown s){
        var h=s.heal;float t=s.t,on=selfOn(s);if(on<=0)return;
        Vec3d chest=s.user.add(0,s.userHeight*.6,0);float near=clear(m,chest);
        float beat=.5f+.5f*(float)Math.sin(t*Math.PI*2*2.5f);
        m.softDisc(chest,1.05+.08*beat,LEAF_DEEP,.13f*on*near);m.softDisc(chest,.7,GOLD,.1f*on*near);
        // the fists that have landed in the last SPARK seconds: where each stopped on them
        if(s.body!=null){
            float arms=GoldHeal.armSize(h,t);var f=GoldHeal.fist(Math.min(t,h.ends()));
            if(f!=null)for(int j=0;j<3;j++){
                int k=(int)f[0]-1-j;if(k<0)continue;
                float landed=GoldHeal.LEAD+(k+1)*GoldHeal.HALF,u=(t-landed)/SPARK;if(u<0||u>=1)continue;
                Vec3d p=on(GoldRush.grown(GoldRush.fist(k,h.seed),k&1,arms*(1+GoldHeal.STRETCH)),s.body);
                float a=(float)Math.pow(1-u,.7)*on*clear(m,p);double grow=Ease.out(u,0,1);
                m.softDisc(p,.2+.2*grow,k%2==0?LEAF_DEEP:AMBER,.4f*a);m.softDisc(p,.1+.06*grow,PALE,.55f*a);
                m.star(p,.1+.08*grow,k%2==0?LEAF_PALE:WHITE,.75f*a*(1-u));
                Vec3d w=way(s.body.yaw()),r=right(w);
                FxLib.ringPlane(m,p,r,new Vec3d(0,1,0),.08+.3*grow,.03,k%2==0?LEAF:GOLD,.6f*a*(1-u));
            }
        }
        // a ring opens at their feet twice a second
        for(int k=0;k<2;k++){
            double u=(t*2+k*.5)%1;
            FxLib.ring(m,s.user.add(0,.04,0),.35+.85*u,.06*(1-.6*u),k==0?LEAF_DEEP:GOLD,(float)(.5*(1-u)*Math.min(1,u/.15))*on);
        }
        // motes rise off them
        for(int k=0;k<8;k++){
            double period=.8+.4*Ease.hash(h.seed+k*7),at=t/period+Ease.hash(h.seed+k*13);double u=at-Math.floor(at);
            double ang=Ease.hash(h.seed+k*5+(int)Math.floor(at)*3)*Math.PI*2;
            Vec3d q=s.user.add(Math.cos(ang)*.42,.2+(s.userHeight+.3)*u,Math.sin(ang)*.42);
            float a=(float)Math.sin(u*Math.PI)*on*clear(m,q);
            m.star(q,.045,k%2==0?LEAF_PALE:PALE,.8f*a);
        }
    }
    /** The effect layer: the hand's glow and the healed body's bloom light the world about them, green-gold. */
    static void frame(FxFrame f,Vec3d camera){
        for(var s:FRAME){
            var h=s.heal;
            if(h.self){float on=selfOn(s);if(on>0)f.light(s.user.add(0,s.userHeight*.6,0),0xd4ffb0,5,.9f*on);continue;}
            if(s.body!=null){float g=GoldHeal.glow(h,s.t);if(g>0)f.light(on(GoldHeal.hand(h,s.t),s.body),0xdcffb4,4,.9f*g);}
            if(h.touched()&&h.whom>=0&&h.whomAt!=null){
                float u=(s.t-h.touchedAt)/(BLOOM+.3f);
                if(u>=0&&u<1)f.light(h.whomAt,0xdcffb4,7,1.8f*(1-u));
            }
        }
    }
}
