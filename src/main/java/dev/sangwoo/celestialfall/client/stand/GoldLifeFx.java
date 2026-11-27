package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.stand.GoldExperienceRules;
import dev.sangwoo.celestialfall.stand.GoldFlyEntity;
import dev.sangwoo.celestialfall.stand.GoldFlyPath;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.stand.GoldHealFx.LEAF;
import static dev.sangwoo.celestialfall.client.stand.GoldHealFx.LEAF_DEEP;
import static dev.sangwoo.celestialfall.client.stand.GoldHealFx.LEAF_INK;
import static dev.sangwoo.celestialfall.client.stand.GoldHealFx.LEAF_PALE;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.AMBER;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.GOLD;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.GUARD_BLUE;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.GUARD_LIGHT;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.PALE;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.WHITE;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.burst;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.on;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.right;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.way;

/**
 * The light of Gold Experience's T, giving life (2.151.0), drawn with its rush's on the effect layer (StandRushFx calls it
 * pass by pass): gold, and the green of new leaves where life is given, as its healing's. None of it over the screen.
 * <ul>
 * <li>The frog: light gathers into the item held up (2.151.1: the stand plays no part in it, it turns of itself); as it
 * turns light blooms on the hand, a ring opens round it and leaves wind up out of it; motes trail its leap to the shoulder. Each
 * blow it turns back: a ring bursts off it toward the striker, a thread of gold runs to them and bursts on them. Its end:
 * light blooms on the hand again as it is the item once more (or bursts where it is, ended early).</li>
 * <li>The punch: the fist's light gathers as it winds up, a short smear as it is thrown, a burst on whom it lands on
 * (blue rings off a guard that takes it).</li>
 * <li>The fly: a trail of gold behind the part flying off, and as it shrinks into the fly a flash, a ring and new leaves;
 * a faint glow about the fly, a mote or two trailing it. Swatted: it bursts, dark specks and gold, and gold blooms where
 * the part comes back.</li>
 * </ul>
 */
final class GoldLifeFx {
    record Shown(GoldLife.Life life,float t,PlayerEntity user,StandPresence.Body body,float delta){}
    record Fly(GoldFlyEntity fly,Vec3d at,double age,LivingEntity owner){}
    record Down(LivingEntity owner,GoldLife.Down down,float t){}
    private static final List<Shown> FRAME=new ArrayList<>();
    private static final List<Fly> FLIES=new ArrayList<>();
    private static final List<Down> DOWNS=new ArrayList<>();
    private GoldLifeFx(){}

    static boolean prepare(MinecraftClient mc,float tickDelta,Vec3d camera){
        FRAME.clear();FLIES.clear();DOWNS.clear();
        if(mc.world==null)return false;
        for(var e:GoldLife.all().entrySet()){
            var l=e.getValue();var user=mc.world.getPlayerByUuid(e.getKey());
            if(user==null||user.getPos().squaredDistanceTo(camera)>96*96)continue;
            float t=l.age(tickDelta);
            if(t<0||t>(l.frog?GoldLife.over(l)+.6f:GoldLife.PUNCH_GUARDED+1.2f))continue;
            FRAME.add(new Shown(l,t,user,GoldPresence.body(e.getKey()),tickDelta));
        }
        for(var e:mc.world.getEntities()){
            if(!(e instanceof GoldFlyEntity f)||!f.isAlive()||f.squaredDistanceTo(camera)>72*72)continue;
            var at=GoldLife.flyAt(f,tickDelta);if(at==null)continue;
            FLIES.add(new Fly(f,at,f.clientAge(tickDelta),mc.world.getEntityById(f.ownerId()) instanceof LivingEntity o?o:null));
        }
        for(var e:GoldLife.downs().entrySet()){
            if(!(mc.world.getEntityById(e.getKey()) instanceof LivingEntity o)||o.squaredDistanceTo(camera)>96*96)continue;
            float t=e.getValue().age(tickDelta);if(t<0||t>1.2f)continue;
            DOWNS.add(new Down(o,e.getValue(),t));
        }
        return !FRAME.isEmpty()||!FLIES.isEmpty()||!DOWNS.isEmpty();
    }
    /** How much of something at {@code p} shows this near the camera: nothing within half a block, all of it from 1.3. */
    static float clear(EffectMesh m,Vec3d p){return Ease.smooth((float)m.camera().distanceTo(p),.5f,1.3f);}

    // ───────────────────────────── where things are ─────────────────────────────

    /** The way {@code user}'s body faces this frame, and its right. */
    static Vec3d facing(PlayerEntity user,float delta){return way(MathHelper.lerpAngleDegrees(delta,user.prevBodyYaw,user.bodyYaw));}
    /** Where the raised hand holds the item (GoldLife's place for it). */
    static Vec3d hand(PlayerEntity user,float delta){
        Vec3d f=facing(user,delta),r=right(f);double k=user.getHeight()/1.8,side=user.getMainArm()==Arm.LEFT?-1:1;
        return user.getLerpedPos(delta).add(f.multiply(GoldLife.HAND_AHEAD*k)).add(r.multiply(side*GoldLife.HAND_SIDE*k)).add(0,GoldLife.HAND_UP*k+.1,0);
    }
    /** Where the frog sits on the other shoulder. */
    static Vec3d shoulder(PlayerEntity user,float delta){
        Vec3d f=facing(user,delta),r=right(f);double k=user.getHeight()/1.8,side=user.getMainArm()==Arm.LEFT?-1:1;
        return user.getLerpedPos(delta).add(r.multiply(-side*.37*k)).add(0,1.62*k,0);
    }
    /** Where the raised hand holds the item this frame: where GoldFrog drew it, or (not drawn: first person) about there. */
    static Vec3d hand(Shown s){var d=GoldLife.drawn(s.user.getUuid(),0);return d!=null?d:hand(s.user,s.delta);}
    /** About where the frog is {@code t} seconds into it (GoldLife's places for the hand and the shoulder). */
    static Vec3d frogNear(Shown s){
        float[] w=GoldLife.seat(s.life,s.t);
        return hand(s.user,s.delta).lerp(shoulder(s.user,s.delta),w[0]).add(0,.34*Math.sin(Math.PI*w[1]),0);
    }
    /** Where the frog is {@code t} seconds into it: where GoldFrog drew it this frame, moved by how it moved since then for another moment. */
    static Vec3d frog(Shown s){
        var d=GoldLife.drawn(s.user.getUuid(),1);if(d==null)return frogNear(s);
        float now=s.life.age(s.delta);
        if(Math.abs(s.t-now)<1e-4f)return d;
        return d.add(frogNear(s).subtract(frogNear(new Shown(s.life,now,s.user,s.body,s.delta))));
    }
    /** Whom the punch landed on, at their middle on the side it came from. */
    static Vec3d struckAt(LivingEntity e,PlayerEntity user,float delta){
        Vec3d c=e.getLerpedPos(delta).add(0,e.getHeight()*.6,0),to=user.getLerpedPos(delta).add(0,e.getHeight()*.6,0).subtract(c);
        return to.lengthSquared()<1e-6?c:c.add(to.normalize().multiply(e.getWidth()*.5+.08));
    }

    // ───────────────────────────── leaves ─────────────────────────────

    /** A small crisp leaf at {@code p} turning on a plane of its own (never to the camera), its dark edge under it. */
    static void leaf(EffectMesh m,Vec3d p,double turn,double size,float alpha,boolean ink){
        if(alpha<=0)return;
        Vec3d along=new Vec3d(Math.cos(turn)*.8,.6,Math.sin(turn)*.8),across=new Vec3d(-Math.sin(turn),.25*Math.cos(turn*1.7),Math.cos(turn)).normalize();
        Vec3d from=p.subtract(along.multiply(size*.5)),to=p.add(along.multiply(size*.5));
        if(ink){m.needle(from.subtract(along.multiply(size*.12)),to.add(along.multiply(size*.12)),across,size*.62,.42,LEAF_INK,.85f*alpha);return;}
        m.needle(from,to,across,size*.44,.42,LEAF,alpha);
        m.needle(from.add(along.multiply(size*.1)),to.subtract(along.multiply(size*.12)),across,size*.14,.45,LEAF_PALE,.9f*alpha);
    }
    /** Leaves winding up and out of {@code p}, {@code u} of their life gone (0 to 1). */
    static void sprout(EffectMesh m,Vec3d p,int n,int seed,float u,double radius,float alpha,boolean ink){
        if(u<0||u>=1)return;
        for(int k=0;k<n;k++){
            double ang=Ease.hash(seed+k*13)*Math.PI*2+(k%2==0?1:-1)*Math.PI*1.2*u;
            double out=radius*(.4+.8*Ease.out(u,0,1))*(.8+.4*Ease.hash(seed+k*7));
            Vec3d q=p.add(Math.cos(ang)*out,(.05+.55*Ease.hash(seed+k*19))*Ease.out(u,0,1)-.15*u*u,Math.sin(ang)*out);
            float a=Math.min(1,u/.1f)*(1-Ease.smooth(u,.6f,1))*alpha*clear(m,q);
            leaf(m,q,Ease.hash(seed+k*29)*Math.PI*2+(k%2==0?5:-5)*u,.13+.05*Ease.hash(seed+k*3),a,ink);
        }
    }

    // ───────────────────────────── the passes ─────────────────────────────

    static void alpha(EffectMesh m){
        for(var s:FRAME){
            if(!s.life.frog)continue;
            float since=s.t-GoldLife.BORN;
            Vec3d hand=hand(s);
            sprout(m,hand,9,s.life.seed,since/.9f,.45,1,true);sprout(m,hand,9,s.life.seed,since/.9f,.45,1,false);
            if(Float.isNaN(s.life.early)){
                float back=s.t-GoldLife.END+.12f;
                sprout(m,hand,7,s.life.seed+5,back/.8f,.4,1,true);sprout(m,hand,7,s.life.seed+5,back/.8f,.4,1,false);
            }
        }
        for(var f:FLIES){
            double u=f.age/GoldExperienceRules.FLY_FORMS;
            sprout(m,f.at,8,f.fly.seed(),(float)((u-.6)/2.2),.4,1,true);sprout(m,f.at,8,f.fly.seed(),(float)((u-.6)/2.2),.4,1,false);
        }
    }
    static void additive(EffectMesh m){
        for(var s:FRAME){if(s.life.frog)frog(m,s);else punch(m,s);}
        for(var f:FLIES)fly(m,f);
        for(var d:DOWNS)swat(m,d);
    }

    /** The frog's light: gathering into the item, its bloom as it turns, its leap's motes, the blows it turns back, its end. */
    static void frog(EffectMesh m,Shown s){
        var l=s.life;float t=s.t;
        Vec3d hand=hand(s);
        // light gathering into the item held up: a glow swelling on it, motes drawn in, a ring closing on it
        float g=GoldLife.gathered(l,t);
        if(g>0){
            float near=clear(m,hand),pulse=.5f+.5f*(float)Math.sin(t*Math.PI*2*7);
            m.softDisc(hand,.14+.16*g+.03*pulse*g,AMBER,.34f*g*near);m.softDisc(hand,.06+.06*g,PALE,.5f*g*near);
            FxLib.ring(m,hand,.42-.3*g,.025+.02*g,GOLD,.55f*g*near);
        }
        // (not right before the camera: its user's own first person, where the hand is not drawn; nor once it ended before it turned)
        if(clear(m,hand)>.5f&&(Float.isNaN(l.early)||l.early>=GoldLife.BORN))FxLib.converge(m,hand,.55,9,t,GoldLife.GATHER_FROM-.03f,GoldLife.BORN,GOLD,l.seed+3);
        // as it turns: light blooms on the hand, a ring opens round it
        bloom(m,hand,t-GoldLife.BORN,l.seed,1);
        // the leap to the shoulder (and back): motes trailing it
        float[] w=GoldLife.seat(l,t);
        if(w[1]>0){
            for(int k=0;k<5;k++){
                float lag=k*.035f;var back=new Shown(l,t-lag,s.user,s.body,s.delta);Vec3d q=frog(back);
                float a=(1-k/5f)*(float)Math.sin(Math.PI*w[1])*clear(m,q);
                m.star(q,.05+.02*(5-k)/5.0,k%2==0?PALE:GOLD,.85f*a);
            }
        }
        // each blow it turned back: a ring off it toward the striker, a thread of gold to them, a burst on them
        for(var r:l.reflected){
            float since=t-r.at();if(since<0||since>.5f)continue;
            Vec3d from=frog(s);var mc=MinecraftClient.getInstance();
            if(!(mc.world.getEntityById(r.striker()) instanceof LivingEntity striker))continue;
            Vec3d to=striker.getLerpedPos(s.delta).add(0,striker.getHeight()*.62,0),go=to.subtract(from);
            if(go.lengthSquared()<1e-6)continue;
            Vec3d dir=go.normalize(),side=dir.crossProduct(new Vec3d(0,1,0));if(side.lengthSquared()<1e-4)side=new Vec3d(1,0,0);side=side.normalize();Vec3d up=side.crossProduct(dir).normalize();
            float u=since/.3f,a=u<1?(1-u):0;double grow=Ease.out(Math.min(1,u),0,1);
            if(a>0){
                FxLib.ringPlane(m,from.add(dir.multiply(.1)),side,up,.12+.55*grow,.05,GOLD,.75f*a);
                FxLib.ringPlane(m,from.add(dir.multiply(.1)),side,up,.1+.38*grow,.02,WHITE,.6f*a);
                m.softDisc(from,.3,AMBER,.35f*a);
            }
            // the thread: its head runs out to them in a tenth of a second, its tail after it
            float head=Math.min(1,since/.1f),tail=Math.min(1,Math.max(0,(since-.06f)/.12f));
            if(tail<1){
                Vec3d h=from.add(go.multiply(head)),tl=from.add(go.multiply(tail));
                m.softTaper(tl,h,.02,.09,GOLD,.7f);m.softTaper(tl,h,.008,.035,WHITE,.85f);
                m.star(h,.12,PALE,.9f);
            }
            float b=(since-.1f)/.35f;
            if(b>=0&&b<1){
                float ab=(float)Math.pow(1-b,.7);
                burst(m,to.subtract(dir.multiply(striker.getWidth()*.5)),.5*(.8+.3*Ease.out(b,0,.4f)),.36,9,l.seed+(int)(r.at()*100),PALE,GOLD,.85f*ab,.75f*ab);
                m.softDisc(to,.75,AMBER,.25f*ab);
                FxLib.ringPlane(m,to.subtract(dir.multiply(.3)),side,up,.2+.7*Ease.out(b,0,1),.06,GOLD,.6f*ab);
            }
        }
        // the end: light blooms on the hand again as it is the item once more; ended early, it bursts where it is
        if(Float.isNaN(l.early))bloom(m,hand,t-GoldLife.END+.12f,l.seed+11,.8f);
        else{
            float u=(t-l.early)/.4f;
            if(u>=0&&u<1){
                Vec3d p=frog(new Shown(l,l.early,s.user,s.body,s.delta));float a=(float)Math.pow(1-u,.8);
                m.softDisc(p,.25+.5*Ease.out(u,0,1),AMBER,.4f*a);m.star(p,.35*(1-u),WHITE,.7f*a);
                FxLib.sparks(m,p,10,.35,.04,PALE,a,l.seed,(float)Ease.out(u,0,1));
            }
        }
    }
    /** Light blooming at {@code p} {@code since} seconds on: soft gold, a white heart, a ring opening, motes rising. */
    static void bloom(EffectMesh m,Vec3d p,float since,int seed,float strength){
        float u=since/.55f;if(u<0||u>=1)return;
        float a=strength*Ease.smooth(since,-.005f,.03f)*(float)Math.pow(1-u,.8)*clear(m,p);double grow=Ease.out(u,0,1);
        m.softDisc(p,.35+.55*grow,LEAF_DEEP,.25f*a);m.softDisc(p,.25+.35*grow,GOLD,.45f*a);m.softDisc(p,.1+.06*grow,WHITE,.6f*a*(1-u));
        m.star(p,.22*(1-u)+.05,WHITE,.6f*a);
        FxLib.ring(m,p,.12+.7*grow,.04,PALE,.65f*a*(1-u));
        for(int k=0;k<6;k++){
            double ang=Ease.hash(seed+k*11)*Math.PI*2;Vec3d q=p.add(Math.cos(ang)*.25*grow,.6*grow*Ease.hash(seed+k*17),Math.sin(ang)*.25*grow);
            m.star(q,.05,k%2==0?LEAF_PALE:PALE,.85f*a*(1-u));
        }
    }
    /** The punch's light: gathering on the fist, the smear as it is thrown, the burst on whom it landed on. */
    static void punch(EffectMesh m,Shown s){
        var l=s.life;float t=s.t;
        if(s.body!=null){
            Vec3d fist=on(GoldLife.fist(l,t),s.body);float near=clear(m,fist),g=GoldLife.glow(l,t);
            if(g>0){
                float pulse=.5f+.5f*(float)Math.sin(t*Math.PI*2*6);
                m.softDisc(fist,.3+.06*pulse,AMBER,.3f*g*near);m.softDisc(fist,.15,PALE,.5f*g*near);
                FxLib.converge(m,fist,.6,8,t,.02f,GoldLife.LOAD,GOLD,l.seed);
            }
            if(t>GoldLife.LOAD&&t<GoldLife.CONTACT+.06f&&Float.isNaN(l.stopped)){
                float a=Ease.smooth(t,GoldLife.LOAD,GoldLife.LOAD+.04f)*(1-Ease.smooth(t,GoldLife.CONTACT,GoldLife.CONTACT+.06f));
                Vec3d from=on(GoldLife.fist(l,GoldLife.LOAD),s.body),along=fist.subtract(from);
                if(a>0&&along.lengthSquared()>1e-4){m.softTaper(from.add(along.multiply(.25)),fist,.02,.26,AMBER,.45f*a*near);m.softTaper(from.add(along.multiply(.25)),fist,.01,.12,PALE,.6f*a*near);}
            }
        }
        if(!l.landed()||l.struck<0)return;
        float u=(t-l.landedAt)/.45f;if(u<0||u>=1)return;
        if(!(MinecraftClient.getInstance().world.getEntityById(l.struck) instanceof LivingEntity e))return;
        Vec3d at=struckAt(e,s.user,s.delta),go=e.getLerpedPos(s.delta).subtract(s.user.getLerpedPos(s.delta)).multiply(1,0,1);
        if(go.lengthSquared()<1e-6)go=way(l.yaw);go=go.normalize();
        Vec3d side=right(go),up=new Vec3d(0,1,0);float a=(float)Math.pow(1-u,.7);
        if(l.guarded){
            burst(m,at,.42,.4,9,l.seed,GUARD_LIGHT,GUARD_BLUE,.7f*a,.6f*a);
            FxLib.ringPlane(m,at,side,up,.2+.6*Ease.out(u,0,1),.06,GUARD_BLUE,.7f*a*(1-u));
        }else{
            burst(m,at,.5*(.8+.25*Ease.out(u,0,.4f)),.36,9,l.seed,PALE,GOLD,.85f*a,.75f*a);
            m.softDisc(at,.7,AMBER,.28f*a);m.softDisc(at,.18,WHITE,.45f*a*(1-u));
            FxLib.ringPlane(m,at.add(go.multiply(.1)),side,up,.18+.7*Ease.out(u,0,1),.06,GOLD,.6f*a*(1-u));
        }
    }
    /** About a fly: the trail of the part flying off and the flash it becomes the fly in; its faint glow and a mote trailing it; its return. */
    static void fly(EffectMesh m,Fly f){
        double age=f.age,forms=GoldExperienceRules.FLY_FORMS;int seed=f.fly.seed();
        float near=clear(m,f.at);
        if(age<forms+4){
            // the part's trail: where it was a moment ago, back along its arc
            if(age<forms&&f.owner!=null)for(int k=1;k<=6;k++){
                double back=age-k*.6;if(back<0)break;
                Vec3d q=pathAt(f,back);float a=(1-k/7f)*clear(m,q);
                m.softDisc(q,.12,AMBER,.25f*a);m.star(q,.05,PALE,.7f*a);
            }
            // it becomes the fly: a flash, a ring, motes
            double u=(age-forms*.6)/(forms*.75);
            if(u>=0&&u<1){
                float a=(float)Math.sin(Math.PI*u)*near;
                m.softDisc(f.at,.25+.35*u,GOLD,.45f*a);m.softDisc(f.at,.1,WHITE,.7f*a);m.star(f.at,.35*a,WHITE,.65f*a);
                FxLib.ring(m,f.at,.12+.5*u,.035,PALE,.6f*a);
            }
        }
        // its faint glow while it flies, and a mote or two trailing
        double flying=age-forms;
        if(flying>0&&flying<GoldExperienceRules.FLY_LASTS+GoldExperienceRules.FLY_RETURN){
            float shimmer=.6f+.4f*(float)Math.sin(age*.9+seed);
            m.softDisc(f.at,.2,AMBER,.1f*shimmer*near);
            for(int k=0;k<2;k++){
                double back=age-2-k*2.5;Vec3d q=pathAt(f,back);
                m.star(q,.035,k==0?PALE:GOLD,.5f*(1-k*.4f)*clear(m,q));
            }
        }
        // its return: motes as it flies back, a flash as it is the part again
        double ret=flying-GoldExperienceRules.FLY_LASTS;
        if(ret>0&&ret<GoldExperienceRules.FLY_RETURN+2){
            float u=(float)(ret/GoldExperienceRules.FLY_RETURN);
            if(u>.55f&&u<1.25f){float a=(float)Math.sin(Math.PI*Math.min(1,(u-.55f)/.7f))*near;m.softDisc(f.at,.3,GOLD,.45f*a);m.star(f.at,.25,WHITE,.6f*a);}
        }
    }
    /** Where the fly (or the part flying off) was {@code age} ticks after the blow, about whom it belongs to as they stand now. */
    static Vec3d pathAt(Fly f,double age){
        if(f.owner==null)return f.at;
        var o=f.owner;var at=o.getPos();
        var p=GoldFlyPath.at(f.fly.seed(),f.fly.part(),Math.max(0,age),at.x,at.y,at.z,f.fly.clientHeading,o.getStandingEyeHeight(),o.getHeight(),f.fly.from());
        return new Vec3d(p[0],p[1],p[2]);
    }
    /** A swat: the fly bursts (dark specks and gold), gold blooms where the part comes back, and a ring where they fall. */
    static void swat(EffectMesh m,Down d){
        float t=d.t;var o=d.owner;var dn=d.down;
        if(dn.where()!=null){
            float u=t/.5f;
            if(u<1){
                float a=(float)Math.pow(1-u,.7)*clear(m,dn.where());
                burst(m,dn.where(),.32*(.8+.3*Ease.out(u,0,.4f)),.36,8,o.getId()*7+dn.part(),PALE,GOLD,.8f*a,.7f*a);
                for(int k=0;k<9;k++){
                    double ang=Ease.hash(o.getId()+k*5)*Math.PI*2,el=(Ease.hash(o.getId()+k*7)-.3)*1.2;
                    Vec3d dir=new Vec3d(Math.cos(ang)*Math.cos(el),Math.sin(el),Math.sin(ang)*Math.cos(el));
                    Vec3d q=dn.where().add(dir.multiply(.08+.5*Ease.out(u,0,1))).add(0,-.3*u*u,0);
                    m.needle(q,q.add(dir.multiply(.08)),.03,.4,k%3==0?0x1a1a1e:k%3==1?PALE:GOLD,.85f*a);
                }
            }
        }
        // the part back on the body: gold blooms at its place
        double[] socket=GoldFlyPath.socket(dn.part(),o.getHeight());
        float bodyYaw=o.bodyYaw;var sp=GoldFlyPath.world(o.getX(),o.getY(),o.getZ(),bodyYaw,socket);
        bloom(m,new Vec3d(sp[0],sp[1],sp[2]),t,o.getId()*3+dn.part(),.7f);
        // where they fall onto their back: a ring along the ground
        float g=(t-GoldLife.FALLEN+.04f)/.45f;
        if(g>=0&&g<1){Vec3d feet=o.getPos().add(0,.05,0);FxLib.ring(m,feet,.4+1.1*Ease.out(g,0,1),.07*(1-.5*g),AMBER,.45f*(1-g));}
    }
    /** The effect layer: the light gathering, the turning and the end light the hand, a blow turned back lights the striker, the punch whom it lands on, a swat its owner. */
    static void frame(FxFrame f,Vec3d camera){
        for(var s:FRAME){
            var l=s.life;
            if(l.frog){
                float u=(s.t-GoldLife.BORN)/.6f;if(u>=0&&u<1)f.light(hand(s),0xffe6a0,5,1.6f*(1-u));
                float g=GoldLife.gathered(l,s.t);if(g>0)f.light(hand(s),0xffd27a,3.5f,.9f*g);
                for(var r:l.reflected){float since=s.t-r.at();if(since>=0&&since<.4f&&MinecraftClient.getInstance().world.getEntityById(r.striker()) instanceof LivingEntity e)f.light(e.getPos().add(0,e.getHeight()*.6,0),0xffd27a,6,1.8f*(1-since/.4f));}
            }else if(l.landed()&&l.struck>=0){
                float u=(s.t-l.landedAt)/.4f;
                if(u>=0&&u<1&&MinecraftClient.getInstance().world.getEntityById(l.struck) instanceof LivingEntity e)f.light(e.getPos().add(0,e.getHeight()*.6,0),l.guarded?0xc8d4ff:0xffd27a,6,1.6f*(1-u));
            }
        }
        for(var x:FLIES){double u=(x.age-GoldExperienceRules.FLY_FORMS*.6)/GoldExperienceRules.FLY_FORMS;if(u>=0&&u<1)f.light(x.at,0xffe6a0,4,1.2f*(float)Math.sin(Math.PI*u));}
        for(var d:DOWNS)if(d.down.where()!=null&&d.t<.4f)f.light(d.down.where(),0xffd27a,4,1.2f*(1-d.t/.4f));
    }
}
