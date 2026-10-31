package dev.sangwoo.celestialfall.boss;

import dev.sangwoo.celestialfall.AtomicManager;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.CombatState;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import dev.sangwoo.celestialfall.stand.KagenoManager;
import dev.sangwoo.celestialfall.stand.KagenoRules;
import dev.sangwoo.celestialfall.stand.StarPlatinumAttack;
import dev.sangwoo.celestialfall.stand.StarPlatinumInhale;
import dev.sangwoo.celestialfall.stand.StarPlatinumRules;
import dev.sangwoo.celestialfall.stand.StarPlatinumRush;
import dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * What is coming at a boss (2.69.0, BossBrain): every player's blow in the making near it, read off the server's own
 * state of their moves as they make them (a Star Platinum user's punches, rush, inhale and time stop, a bare hand's
 * swing, Kageno's casts, Lee So-sang's sword since 2.77.0, an Atomic), each with the ground it will strike, when, and
 * what a guard does against it. A
 * boss sees a move only {@link BossRules#REACTION} ticks after it began, as a quick player would.
 */
public final class BossThreats {
    private BossThreats(){}

    /**
     * Ground a blow strikes: a lane ahead of a spot along a way (from {@code near} to {@code far} ahead, {@code sideMin}
     * to {@code sideMax} to the right, {@code low} to {@code high} up), or a ring ({@code radius} round the spot).
     */
    public record Zone(double x,double y,double z,float yaw,double near,double far,double sideMin,double sideMax,double low,double high,double radius){
        public static Zone lane(Vec3d at,float yaw,double near,double far,double half,double low,double high){return new Zone(at.x,at.y,at.z,yaw,near,far,-half,half,low,high,0);}
        public static Zone ring(Vec3d at,double radius,double low,double high){return new Zone(at.x,at.y,at.z,0,0,0,0,0,low,high,radius);}
        /** Whether a body {@code half} across standing at ({@code px}, {@code py}, {@code pz}) is in it. */
        public boolean covers(double px,double py,double pz,double half){
            double dx=px-x,dy=py-y,dz=pz-z;
            if(dy<low||dy>high)return false;
            if(radius>0)return dx*dx+dz*dz<=(radius+half)*(radius+half);
            double r=Math.toRadians(yaw),fx=-Math.sin(r),fz=Math.cos(r);
            double ahead=dx*fx+dz*fz,side=dx*-fz+dz*fx;
            return ahead>=near-half&&ahead<=far+half&&side>=sideMin-half&&side<=sideMax+half;
        }
        public boolean covers(Vec3d p,double half){return covers(p.x,p.y,p.z,half);}
    }
    /**
     * A blow in the making: whose, what, when it began (it is seen REACTION after), when it lands (the next of its
     * blows, for one that goes on) and until when it goes on, where, whether a guard takes it or breaks under it,
     * whether it holds (an inhale, a beatdown), how much it harms, whether it is a time stop being called (struck, the
     * caller stops calling it) or ground to get out of (an Atomic's column).
     */
    public record Threat(ServerPlayerEntity from,String what,long seen,long hitAt,long until,Zone zone,boolean guardable,boolean breaksGuard,boolean holds,float damage,boolean timeStop,boolean flee){
        public boolean seenBy(long now){return now>=seen+BossRules.REACTION;}
    }

    /** Everything coming at {@code boss} now (none of its own). */
    public static List<Threat> against(BossPlayer boss,long now){
        var out=new ArrayList<Threat>();
        var world=boss.getServerWorld();
        for(var p:world.getPlayers()){
            if(p==boss||p instanceof BossPlayer||!p.isAlive()||p.isSpectator())continue;
            double d2=p.squaredDistanceTo(boss);
            // a time stop reaches far; the rest is near
            long called=StarPlatinumTimeStop.calledAt(p);
            if(called!=Long.MIN_VALUE&&!StarPlatinumTimeStop.stopping(p)&&d2<(StarPlatinumRules.TS_RANGE+12)*(StarPlatinumRules.TS_RANGE+12))
                out.add(new Threat(p,"time_stop",called,called+StarPlatinumRules.TS_WIND,called+StarPlatinumRules.TS_WIND,Zone.ring(p.getPos(),StarPlatinumRules.TS_RANGE,-64,64),false,false,true,999,true,false));
            if(d2>16*16)continue;
            punches(p,now,out);
            bare(p,now,out);
            kageno(p,now,out);
            isosang(p,now,out);
        }
        for(var c:AtomicManager.columns(world)){
            double dx=boss.getX()-c[0],dz=boss.getZ()-c[2];
            if(dx*dx+dz*dz<(c[4]+6)*(c[4]+6))out.add(new Threat(null,"atomic",now-BossRules.REACTION,now,now+40,Zone.ring(new Vec3d(c[0],c[1],c[2]),c[4]+1.5,-2,c[4]*2.5),false,false,false,40,false,true));
        }
        return out;
    }

    /** A Star Platinum user's punch, rush and inhale. */
    private static void punches(ServerPlayerEntity p,long now,List<Threat> out){
        var pu=StarPlatinumAttack.punch(p);
        if(pu!=null&&pu.combo()>=1&&!pu.landed()){
            int c=pu.combo();long hit=pu.start()+StarPlatinumRules.CONTACT[c];
            if(now<=hit){
                double far=c==StarPlatinumRules.FINGER?StarPlatinumRules.FINGER_REACH:c>=4?StarPlatinumRules.REACH_BIG:StarPlatinumRules.REACH;
                double half=c==StarPlatinumRules.FINGER?StarPlatinumRules.FINGER_HALF_WIDTH:StarPlatinumRules.HALF_WIDTH;
                boolean heavy=c==StarPlatinumRules.HEAVY,beat=c==StarPlatinumRules.BEATDOWN,finger=c==StarPlatinumRules.FINGER;
                float damage=beat?60:StarPlatinumRules.DAMAGE[c]+(finger?20:0);
                out.add(new Threat(p,"punch"+c,Math.min(pu.start(),now),hit,hit,Zone.lane(p.getPos(),pu.yaw(),StarPlatinumRules.NEAR,far,half,StarPlatinumRules.LOW,StarPlatinumRules.HIGH),
                    c<=3||finger,heavy||beat,beat,damage,false,false));
            }
        }
        long rush=StarPlatinumRush.startedAt(p);
        if(rush!=Long.MIN_VALUE){
            long next=Math.max(now,rush+StarPlatinumRules.RUSH_FIRST);
            out.add(new Threat(p,"rush",rush,next,rush+StarPlatinumRules.RUSH_LENGTH,Zone.lane(p.getPos(),MathHelper.wrapDegrees(p.getYaw()),StarPlatinumRules.NEAR,StarPlatinumRules.RUSH_REACH,StarPlatinumRules.RUSH_HALF_WIDTH,StarPlatinumRules.LOW,StarPlatinumRules.HIGH),
                true,false,false,StarPlatinumRules.RUSH_TOTAL,false,false));
        }
        long inhale=StarPlatinumInhale.startedAt(p);
        if(inhale!=Long.MIN_VALUE){
            long next=Math.max(now,inhale+StarPlatinumRules.INHALE_CATCH);
            out.add(new Threat(p,"inhale",inhale,next,inhale+StarPlatinumRules.INHALE_LENGTH,Zone.lane(p.getPos(),MathHelper.wrapDegrees(p.getYaw()),StarPlatinumRules.NEAR,StarPlatinumRules.INHALE_REACH,StarPlatinumRules.INHALE_HALF_WIDTH,StarPlatinumRules.LOW,StarPlatinumRules.HIGH),
                true,false,true,20,false,false));
        }
    }
    /** A bare hand's swing (CombatManager): a ray of 3.15 blocks from the eye along the look. */
    private static void bare(ServerPlayerEntity p,long now,List<Threat> out){
        var s=CombatManager.peek(p);
        if(s==null||s.attack<1||s.hitDone)return;
        long hit=s.attackStart+CombatState.WINDUP[s.attack];
        if(now>hit)return;
        out.add(new Threat(p,"bare"+s.attack,s.attackStart,hit,hit,Zone.lane(p.getPos(),MathHelper.wrapDegrees(p.getYaw()),-.2,3.3,.55,-.6,2.6),true,false,false,CombatState.DAMAGE[s.attack],false,false));
    }
    /** Kageno's casts that can be got out of the way of once begun (R's and H's take whom they take as they begin). */
    private static void kageno(ServerPlayerEntity p,long now,List<Threat> out){
        for(var c:KagenoManager.casts()){
            if(c.player!=p)continue;
            int age=c.age();Vec3d at=p.getPos();float yaw=MathHelper.wrapDegrees(p.getYaw());
            switch(c.skill){
                case KagenoRules.M1->{
                    long hit=c.start+KagenoRules.M1_HIT;
                    if(now<=hit)out.add(new Threat(p,"kageno_m1",c.start,hit,hit,new Zone(at.x,at.y,at.z,c.yaw,-.1,3.4,-3.4,3.4,-2,3,0),c.combo<4,false,false,c.combo==4?10:6,false,false));
                }
                case KagenoRules.E->{
                    if(age<KagenoRules.E_FINISH)out.add(new Threat(p,"kageno_e",c.start,Math.max(now,c.start+6),c.start+KagenoRules.E_FINISH,Zone.lane(at,yaw,-.15,3.6,.65,-2.2,2.2),age<KagenoRules.E_PLANT,false,false,20,false,false));
                }
                case KagenoRules.T->{
                    long hit=c.start+KagenoRules.T_STRIKE;
                    if(now<=hit+40)out.add(new Threat(p,"kageno_t",c.start,Math.max(now,hit),hit+40,new Zone(c.origin.x,c.origin.y,c.origin.z,c.yaw,-2.6,8.6,-4.6,6.4,-2,7,0),true,false,false,15,false,false));
                }
                case KagenoRules.RUSH->{
                    // the fire slashes fly up the line the caster faces (5 blocks a tick from a block out)
                    if(age<=KagenoRules.RUSH_SLASHES[2]+6)out.add(new Threat(p,"kageno_slash",c.start,Math.max(now,c.start+KagenoRules.RUSH_SLASHES[0]),c.start+KagenoRules.RUSH_SLASHES[2]+6,
                        Zone.lane(c.origin,yaw,0,KagenoRules.RUSH_RANGE,KagenoRules.RUSH_SLASH_HALF_WIDTH,-1.5,3.5),true,false,true,13,false,false));
                }
                case KagenoRules.Y->{
                    long hit=c.start+KagenoRules.Y_STOMP;
                    if(now<=c.start+KagenoRules.Y_FLASH)out.add(new Threat(p,"kageno_y",c.start,Math.max(now,hit),c.start+KagenoRules.Y_FLASH,Zone.ring(at,4,-2,4),false,false,true,16,false,false));
                }
                default->{}
            }
        }
    }
    /**
     * Lee So-sang's sword (2.77.0): the next blow of the hit in flight, in its way (the ring's all round them); and the
     * finisher's slash while it flies (2.77.2): the rest of its way ahead of where its band is now; the empowered first's
     * swords and the empowered fourth's giant sword while they fly (2.80.0, 2.80.1).
     */
    private static void isosang(ServerPlayerEntity p,long now,List<Threat> out){
        var w=dev.sangwoo.celestialfall.isosang.IsosangAttack.flight(p);
        if(w!=null){
            double flown=now-w.thrown(),back=IsosangRules.waveFront(flown)-IsosangRules.waveCurve(flown)-IsosangRules.WAVE_DEPTH;
            out.add(new Threat(p,"isosang4",w.thrown(),now,w.thrown()+IsosangRules.WAVE_TICKS,
                Zone.lane(w.from(),w.yaw(),back,IsosangRules.WAVE_RANGE,IsosangRules.WAVE_HALF_END,IsosangRules.LOW,IsosangRules.HIGH),false,false,false,IsosangRules.total(4),false,false));
        }
        // (2.80.1) the empowered fourth's giant sword in flight: the rest of its way ahead of its point, unguardable
        var g=dev.sangwoo.celestialfall.isosang.IsosangAttack.giant(p);
        if(g!=null){
            double head=IsosangRules.giantHead(now-g.thrown(),g.most());
            out.add(new Threat(p,"isosang6",g.thrown(),now,g.thrown()+(long)Math.ceil(IsosangRules.giantArrives(g.most())),
                Zone.lane(g.from(),g.yaw(),Math.max(IsosangRules.NEAR,head-IsosangRules.GIANT_SPEED),g.most(),IsosangRules.GIANT_HALF_WIDTH,IsosangRules.LOW,IsosangRules.HIGH),false,false,false,IsosangRules.damage(IsosangRules.THRUST,0),false,false));
        }
        // (2.80.0) the empowered first's swords in flight: each a lane on along its way, reaching a body near it at once
        for(var sw:dev.sangwoo.celestialfall.isosang.IsosangAttack.swords(p)){
            float yaw=(float)Math.toDegrees(Math.atan2(-sw.dir().x,sw.dir().z));
            out.add(new Threat(p,"isosang5",now-3,now+1,now+1+(long)Math.ceil(sw.left()/IsosangRules.SWORD_SPEED),
                Zone.lane(sw.at(),yaw,-.5,sw.left(),IsosangRules.SWORD_RADIUS+.2,IsosangRules.LOW,IsosangRules.HIGH),true,false,false,IsosangRules.damage(IsosangRules.THROWS,0),false,false));
        }
        var h=dev.sangwoo.celestialfall.isosang.IsosangAttack.hit(p);
        if(h==null||!IsosangRules.valid(h.combo())||h.blows()>=IsosangRules.blows(h.combo()))return;
        int c=h.combo(),i=h.blows();long hit=h.start()+IsosangRules.blowAt(c,i);
        if(now>hit)return;
        // (2.80.1: the empowered fourth throws its giant sword from where its leap back takes its user, all along its way)
        double room=dev.sangwoo.celestialfall.isosang.IsosangAttack.room(p);
        Vec3d at=c==IsosangRules.THRUST?p.getPos().subtract(Vec3d.fromPolar(0,h.yaw()).multiply(IsosangRules.backstep(IsosangRules.blowAt(c,0),room)-IsosangRules.backstep(now-h.start(),room))):p.getPos();
        Zone zone=c==2?Zone.ring(p.getPos(),IsosangRules.SPIN_RADIUS,IsosangRules.LOW,IsosangRules.HIGH)
            :Zone.lane(at,h.yaw(),IsosangRules.near(c,i),IsosangRules.reach(c,i),IsosangRules.halfWidth(c,i),IsosangRules.LOW,IsosangRules.HIGH);
        float damage=IsosangRules.total(c,dev.sangwoo.celestialfall.isosang.IsosangAttack.charged(p)||c>=IsosangRules.THROWS);
        out.add(new Threat(p,"isosang"+c,h.start(),hit,h.start()+IsosangRules.lastBlow(c),zone,!IsosangRules.unblockable(c),false,false,damage,false,false));
    }
    /** How far a dash has carried its dasher {@code ticks} ticks in (blocks; CombatState.dashSpeed, the whole dash after DASH_TICKS). */
    public static double dashDistance(int ticks){double d=0;for(int i=0;i<Math.min(ticks,CombatState.DASH_TICKS);i++)d+=CombatState.dashSpeed(i);return d;}
}
