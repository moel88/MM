package dev.sangwoo.celestialfall.boss;

import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.CombatState;
import dev.sangwoo.celestialfall.combat.CounterStance;
import dev.sangwoo.celestialfall.combat.HitStun;
import dev.sangwoo.celestialfall.map.MapGate;
import dev.sangwoo.celestialfall.stand.KagenoManager;
import dev.sangwoo.celestialfall.stand.StandSummon;
import dev.sangwoo.celestialfall.stand.StarPlatinumAttack;
import dev.sangwoo.celestialfall.stand.StarPlatinumBeatdown;
import dev.sangwoo.celestialfall.stand.StarPlatinumInhale;
import dev.sangwoo.celestialfall.stand.StarPlatinumRush;
import dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * A boss's brain (2.69.0): what every boss does with its body, on the server, every tick before its body moves.
 * <p>
 * Neutral, it stands where it came, whole, its eyes on whoever comes near. Struck by a player, it is angry with
 * everyone within its range (BossRules.RANGE): it goes after the one with the least health, then the next, until nobody
 * is left within its range a moment (BossRules.CALM_AFTER), when it goes back and is whole again.
 * <p>
 * Fighting, it reads every blow coming at it as a quick player would (BossThreats, BossRules.REACTION after it began):
 * what no guard takes, breaks a guard or holds it, it dashes out of (the way out that keeps it near whom it fights);
 * what a guard takes it guards (facing it), and what it can strike first it strikes first. A player calling a time stop
 * near it is struck before the time stops, or got away from. It does not strike anyone standing in a counter
 * (CounterStance): it waits it out out of their reach. Each kind of boss has its own way of attacking ({@link #attack}).
 */
public class BossBrain {
    /** Ticks ahead it looks at what is coming (a blow further off it answers later). */
    static final int LOOKAHEAD=8;
    /** How far from whom it fights it keeps when it waits (blocks): out of a punch's reach, in a lunge's. */
    static final double WAIT=4.6;
    protected final BossPlayer boss;
    final BossRules.Poise poise=new BossRules.Poise();
    protected final Random random=new Random();
    private boolean angry,returning;private long lonely=-1,returnSince;
    /** Whom it fights now, and the last player who struck it (its reward's if nobody's blow felled it). */
    protected ServerPlayerEntity target;private ServerPlayerEntity lastStriker;
    /** Its guard is held up until this tick, facing this one. */
    private long guardUntil=-1;private Entity guardFacing;
    /** Its circling way while it waits (1 or -1), and when it turns. */
    private int circle=1;private long circleUntil;
    // this tick's keys and look
    protected Vec3d move=Vec3d.ZERO;protected boolean sprint,jump;
    private float faceYaw,lookYaw,pitch;private boolean face,look;private float turn=40;
    protected final BossPath path=new BossPath();
    /** What it does this tick, in words (for /botand boss and the logs). */
    protected String doing="";

    public BossBrain(BossPlayer boss){this.boss=boss;}
    public boolean angry(){return angry;}
    public ServerPlayerEntity target(){return target;}
    public String doing(){return doing;}
    public ServerPlayerEntity lastStriker(){return lastStriker!=null&&!lastStriker.isRemoved()?lastStriker:null;}
    public String describe(){return (angry?"전투 중":returning?"돌아가는 중":"대기")+(target!=null?" · 대상 "+target.getName().getString():"")+(doing.isEmpty()?"":" · "+doing);}

    /** A player struck it (or caught it): it is angry, with everyone within its range. */
    void provoked(ServerPlayerEntity by){
        lastStriker=by;
        if(!angry){angry=true;returning=false;lonely=-1;onAngry(boss.getWorld().getTime());Bosses.sendListAll(boss.getServer());}
    }
    /** It is being taken off: every key it holds goes up. */
    void forget(){releaseKeys();target=null;}
    protected void onAngry(long now){}

    final void tick(){
        var world=boss.getServerWorld();long now=world.getTime();
        poise.tick(now,held());
        move=Vec3d.ZERO;sprint=jump=false;face=look=false;turn=40;doing="";
        if(!MapGate.enabled(world)){steer();return;}
        if(angry)fight(now);else calm(now);
        steer();
    }

    // ---- calm

    private void calm(long now){
        target=null;
        if(StandSummon.out(boss))StandSummon.toggle(boss);
        guard(false,now);
        var home=boss.home;double d=flat(home);
        if(returning){
            if(d<=1.2||now-returnSince>BossRules.HOME_TIMEOUT){
                if(d>1.2){boss.requestTeleport(home.x,home.y,home.z);boss.setVelocity(Vec3d.ZERO);}
                returning=false;path.clear();
            }else{goTo(home,now,true);doing="돌아가는 중";return;}
        }
        // where it came: whole again, facing the way it came facing, its eyes on whoever is near
        if(boss.getHealth()<boss.getMaxHealth())boss.heal(BossRules.heal(boss.getHealth(),boss.getMaxHealth()));
        if(d>.7)goTo(home,now,false);
        else faceTo(boss.homeYaw,8);
        ServerPlayerEntity near=null;double best=10*10;
        for(var p:boss.getServerWorld().getPlayers())if(fightable(p)&&p.squaredDistanceTo(boss)<best){best=p.squaredDistanceTo(boss);near=p;}
        if(near!=null)lookAt(near);
    }
    private void calmDown(long now){
        angry=false;returning=true;returnSince=now;target=null;
        releaseKeys();guard(false,now);path.clear();
        Bosses.sendListAll(boss.getServer());
    }

    // ---- fighting

    private void fight(long now){
        var candidates=candidates();
        if(candidates.isEmpty()){
            if(lonely<0)lonely=now;
            if(now-lonely>=BossRules.CALM_AFTER){calmDown(now);return;}
            releaseKeys();guard(false,now);doing="찾는 중";
            return;
        }
        lonely=-1;
        target=BossRules.choose(candidates,target,committed(now),12f);
        if(target!=null&&!StandSummon.out(boss)&&StandSummon.canBeOut(boss)&&!CombatManager.incapacitated(boss))StandSummon.toggle(boss);
        if(!free()){if(target!=null)face(target,30);doing="묶임";return;}
        if(defend(now))return;
        if(guarding()&&now>=guardUntil)guard(false,now);
        if(target==null)return;
        // whom it fights stands in a counter: no blow into it; it waits it out out of their reach
        if(CounterStance.active(target)){releaseKeys();keepAway(target,WAIT+.6,now);doing="반격 대기";return;}
        attack(now);
    }
    /** Those it may fight now: players within its range, alive, on the map, not watching. */
    private List<BossRules.Candidate<ServerPlayerEntity>> candidates(){
        var out=new ArrayList<BossRules.Candidate<ServerPlayerEntity>>();
        for(var p:boss.getServerWorld().getPlayers()){
            if(!fightable(p))continue;
            double d=Math.sqrt(p.squaredDistanceTo(boss));
            if(d<=BossRules.RANGE)out.add(new BossRules.Candidate<>(p,p.getHealth()+p.getAbsorptionAmount(),d));
        }
        return out;
    }
    static boolean fightable(ServerPlayerEntity p){return !(p instanceof BossPlayer)&&p.isAlive()&&!p.isRemoved()&&!p.isSpectator()&&!p.isCreative()&&MapGate.enabled(p.getWorld());}
    /** Whether it is in the middle of something against whom it fights (it does not turn to another then). */
    protected boolean committed(long now){return false;}
    /** Free to do anything: nothing holds it. */
    protected boolean free(){
        return boss.isAlive()&&!CombatManager.incapacitated(boss)&&!HitStun.stunned(boss)&&!KagenoManager.frozen(boss)&&!StarPlatinumBeatdown.involved(boss)&&!StarPlatinumTimeStop.holds(boss);
    }
    /** Held where it is or kept from acting (for its poise). */
    private boolean held(){return boss.isAlive()&&(CombatManager.incapacitated(boss)||HitStun.stunned(boss));}

    /** Its own way of attacking whom it fights (a boss with no stand walks up and fights with its fists). */
    protected void attack(long now){
        double d=flat(target.getPos());
        face(target,40);
        if(d>2.6)goTo(target.getPos(),now,d>4);
        else if(StarPlatinumAttack.punches(boss))StarPlatinumAttack.request(boss);
        doing="공격";
    }

    // ---- what comes at it

    /** Something comes at it: out of its way, behind its guard, or a blow of its own first. Returns whether it answered something. */
    private boolean defend(long now){
        BossThreats.Threat urgent=null;var at=boss.getPos();double half=boss.getWidth()*.5;
        for(var t:BossThreats.against(boss,now)){
            if(!t.seenBy(now)||now>t.until())continue;
            long left=t.hitAt()-now;
            if(left>LOOKAHEAD)continue;
            if(!t.zone().covers(at,half))continue;
            if(urgent==null||t.hitAt()<urgent.hitAt()||t.hitAt()==urgent.hitAt()&&t.damage()>urgent.damage())urgent=t;
        }
        if(urgent==null)return guarding()&&now<guardUntil&&holdGuard(now);
        int left=(int)Math.max(0,urgent.hitAt()-now);
        if(urgent.timeStop()){
            doing="시간 정지 저지";
            if(answerTimeStop(urgent,now))return true;
            flee(urgent.from().getPos(),now,true);return true;
        }
        if(urgent.flee()){doing="피하기";flee(new Vec3d(urgent.zone().x(),urgent.zone().y(),urgent.zone().z()),now,true);return true;}
        if(strikeFirst(urgent,left,now)){doing="선타";return true;}
        boolean canDash=dashReady(now);
        boolean bad=!urgent.guardable()||urgent.breaksGuard()||urgent.holds()||urgent.damage()>=14;
        if(bad&&canDash&&left>=2){var dir=escape(urgent,left,true);if(dir!=null&&dash(dir,now)){doing="대시 회피 "+urgent.what();return true;}}
        if(urgent.guardable()&&canGuard(now)){
            releaseKeys();
            guardFacing=urgent.from();guardUntil=Math.max(urgent.hitAt(),Math.min(urgent.until(),now+30))+3;
            doing="가드 "+urgent.what();return holdGuard(now);
        }
        if(canDash&&left>=2){var dir=escape(urgent,left,true);if(dir!=null&&dash(dir,now)){doing="대시 회피 "+urgent.what();return true;}}
        var dir=escape(urgent,left,false);
        if(dir!=null){move=dir;sprint=true;doing="비키기 "+urgent.what();return true;}
        return false;
    }
    /** A blow of its own that lands before {@code threat} does and stops it (a boss with a stand of its own knows of one). */
    protected boolean strikeFirst(BossThreats.Threat threat,int left,long now){return false;}
    /** A player calls a time stop near it: whether it struck (or went to strike) them before time stops. */
    protected boolean answerTimeStop(BossThreats.Threat threat,long now){return false;}
    /**
     * The best way out of {@code threat}'s ground in {@code left} ticks, dashing or walking: one that ends clear of it with
     * room to go and ground to land on, near whom it fights (to strike back) and away from whoever threw it; null if none.
     */
    protected Vec3d escape(BossThreats.Threat threat,int left,boolean dashing){
        var at=boss.getPos();double half=boss.getWidth()*.5+.2;
        double reach=dashing?BossThreats.dashDistance(left):.24*Math.max(0,left-1);
        double full=dashing?BossThreats.dashDistance(CombatState.DASH_TICKS):reach;
        Vec3d best=null;double bestScore=-1e9;
        for(int i=0;i<16;i++){
            double a=i*Math.PI/8;var dir=new Vec3d(Math.cos(a),0,Math.sin(a));
            if(threat.zone().covers(at.add(dir.multiply(reach)),half))continue;
            if(!room(dir,full))continue;
            var end=at.add(dir.multiply(full));double score=random.nextDouble()*.3;
            if(target!=null)score-=Math.abs(end.distanceTo(target.getPos())-3.2)*.7;
            if(threat.from()!=null)score+=Math.min(4,end.distanceTo(threat.from().getPos()))*.4;
            if(score>bestScore){bestScore=score;best=dir;}
        }
        return best;
    }
    /** Whether it can go {@code dist} along {@code dir} without a wall in the way and with ground at the end. */
    protected boolean room(Vec3d dir,double dist){
        var world=boss.getServerWorld();var box=boss.getBoundingBox();
        for(double s=.5;s<=dist+1e-6;s+=.5){
            if(!world.isSpaceEmpty(boss,box.offset(dir.multiply(s)).offset(0,.3,0)))return false;
        }
        var end=boss.getPos().add(dir.multiply(dist));
        for(int dy=0;dy<=3;dy++){var below=BlockPos.ofFloored(end.x,end.y-.5-dy,end.z);if(!world.getBlockState(below).getCollisionShape(world,below).isEmpty())return true;}
        return false;
    }
    /** Away from {@code from}, dashing if it can. */
    protected void flee(Vec3d from,long now,boolean dashIfCan){
        releaseKeys();guard(false,now);
        var away=boss.getPos().subtract(from).multiply(1,0,1);
        if(away.lengthSquared()<1e-4)away=Vec3d.fromPolar(0,boss.getYaw()+180);
        away=away.normalize();
        if(dashIfCan&&dashReady(now)&&room(away,BossThreats.dashDistance(CombatState.DASH_TICKS))&&dash(away,now))return;
        move=away;sprint=true;faceDir(away,40);
    }

    // ---- keys

    protected boolean dashReady(long now){var s=CombatManager.peek(boss);return s==null||now>=s.dashReady&&!s.dashing(now)&&!s.locked(now);}
    protected boolean canGuard(long now){var s=CombatManager.peek(boss);return s==null||!s.locked(now)&&!s.dashing(now)&&!s.attacking(now);}
    protected boolean guarding(){var s=CombatManager.peek(boss);return s!=null&&s.guarding;}
    /** Dashes along {@code dir} (whatever way it faces). */
    protected boolean dash(Vec3d dir,long now){
        releaseKeys();guard(false,now);
        var f=Vec3d.fromPolar(0,boss.getYaw());var left=new Vec3d(f.z,0,-f.x);
        return CombatManager.dashInput(boss,(float)dir.dotProduct(f),(float)dir.dotProduct(left));
    }
    private boolean holdGuard(long now){
        if(guardFacing!=null&&guardFacing.isAlive())face(guardFacing,90);
        return CombatManager.guardInput(boss,true);
    }
    protected void guard(boolean up,long now){
        if(up)CombatManager.guardInput(boss,true);
        else{guardUntil=-1;guardFacing=null;if(guarding())CombatManager.guardInput(boss,false);}
    }
    /** Every key it holds down goes up (its rush, its inhale). */
    protected void releaseKeys(){
        if(StarPlatinumRush.rushing(boss))StarPlatinumRush.release(boss);
        if(StarPlatinumInhale.inhaling(boss))StarPlatinumInhale.release(boss);
    }

    // ---- moving and looking

    /** Toward {@code to}, round what stands in the way, running if {@code run}. */
    protected void goTo(Vec3d to,long now,boolean run){
        var way=path.next(boss,to,now).subtract(boss.getPos()).multiply(1,0,1);
        if(way.lengthSquared()<1e-4)return;
        move=way.normalize();sprint=run;
        if(!face)faceDir(move,run?30:20);
    }
    /** Keeps {@code dist} from {@code e}, circling them, facing them. */
    protected void keepAway(Entity e,double dist,long now){
        face(e,30);
        var to=boss.getPos().subtract(e.getPos()).multiply(1,0,1);double d=Math.sqrt(to.lengthSquared());
        if(d<1e-3)to=Vec3d.fromPolar(0,boss.getYaw()+180);else to=to.multiply(1/d);
        if(now>=circleUntil){circle=random.nextBoolean()?1:-1;circleUntil=now+30+random.nextInt(50);}
        var around=new Vec3d(-to.z*circle,0,to.x*circle);
        double radial=MathHelper.clamp((dist-d)*.9,-1,1);
        var want=to.multiply(radial).add(around.multiply(.55));
        if(!room(want.lengthSquared()<1e-4?around:want.normalize(),1.2)){circle=-circle;circleUntil=now+40;want=to.multiply(radial).add(around.multiply(-.55));}
        move=want.lengthSquared()>1?want.normalize():want;
    }
    protected void face(Entity e,float rate){
        var d=e.getPos().subtract(boss.getPos());
        faceYaw=(float)(MathHelper.atan2(d.z,d.x)*180/Math.PI)-90;face=true;turn=rate;
        double flat=Math.sqrt(d.x*d.x+d.z*d.z);pitch=(float)MathHelper.clamp(-Math.toDegrees(Math.atan2(e.getEyeY()-boss.getEyeY(),Math.max(.1,flat))),-40,40);
    }
    protected void faceDir(Vec3d dir,float rate){faceYaw=(float)(MathHelper.atan2(dir.z,dir.x)*180/Math.PI)-90;face=true;turn=rate;pitch=0;}
    protected void faceTo(float yaw,float rate){faceYaw=yaw;face=true;turn=rate;pitch=0;}
    /** Turns its head (not its body) to {@code e}. */
    private void lookAt(Entity e){var d=e.getPos().subtract(boss.getPos());lookYaw=(float)(MathHelper.atan2(d.z,d.x)*180/Math.PI)-90;look=true;}
    /** Its yaw toward {@code e} now (degrees). */
    protected float yawTo(Entity e){var d=e.getPos().subtract(boss.getPos());return (float)(MathHelper.atan2(d.z,d.x)*180/Math.PI)-90;}
    /** Snaps it to face {@code e} at once (a blow goes the way its user faces as it starts). */
    protected void aim(Entity e){float y=yawTo(e);boss.setYaw(y);boss.setHeadYaw(y);faceYaw=y;face=true;turn=180;}
    protected double flat(Vec3d p){double dx=p.x-boss.getX(),dz=p.z-boss.getZ();return Math.sqrt(dx*dx+dz*dz);}
    /** This tick's keys and look, put on its body: the way it faces, and its walk as a player's keys would make it. */
    private void steer(){
        float yaw=boss.getYaw();
        if(face)yaw=yaw+MathHelper.clamp(MathHelper.wrapDegrees(faceYaw-yaw),-turn,turn);
        boss.setYaw(yaw);boss.setPitch(pitch);
        float head=yaw;if(look)head=yaw+MathHelper.clamp(MathHelper.wrapDegrees(lookYaw-yaw),-60,60);
        boss.setHeadYaw(head);
        var f=Vec3d.fromPolar(0,yaw);var left=new Vec3d(f.z,0,-f.x);
        double fw=move.dotProduct(f),sd=move.dotProduct(left),len=Math.hypot(fw,sd);
        if(len>1){fw/=len;sd/=len;}
        boss.forwardSpeed=(float)fw;boss.sidewaysSpeed=(float)sd;
        var s=CombatManager.peek(boss);long now=boss.getWorld().getTime();
        boolean slowed=s!=null&&(s.locked(now)||s.guarding||s.slowed(now))||StarPlatinumRush.rushing(boss)||StarPlatinumInhale.inhaling(boss);
        boss.setSprinting(sprint&&fw>.8&&!slowed);
        if(move.lengthSquared()>.01&&boss.horizontalCollision&&boss.isOnGround())jump=true;
        boss.setJumping(jump&&!(s!=null&&s.locked(now)));
    }
}
