package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.CinematicDirector;
import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.isosang.IsosangAttack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.*;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;
import static dev.sangwoo.celestialfall.isosang.IsosangRules.*;

/**
 * Lee So-sang's Y as drawn (2.97.0, user request: "이번에는 Y스킬 거대한 검 6개를 적 위에 소환하고 2개씩 빠르게 적에게
 * 떨궈서 원 모양으로 적을 가두고 잉크가 많이 튀는 스킬이야", the user's two videos of it (9e90441f and c6bb25f6) read on a
 * sheet and confirmed: "맞음, 이대로"):
 * <ul>
 * <li>she dips on her sword's pommel and leaps up off it (IsosangRules.cageLeap: the formation lifts her, raised), turns
 * to the foe and reaches out at it with her free hand, her sword trailing behind her, light gathering in that hand;</li>
 * <li>six giant swords of light (the giant sword of R and the empowered fourth, SpiritSword, some 3.2 blocks: the videos'
 * are the same sword, its broad blade, its groove and its guard's swept wings) show one after another in the air over
 * the foe, points down, each out of a burst of starlight, at heights of their own round a ring, and hang there;</li>
 * <li>she comes down on her sword again as they come down on the foe two at a time (IsosangRules.CAGE_DROPS: each as a
 * long streak of light out of the sky), sticking into the ground round it in a ring, each pair with a flash of blue and
 * a ring of light along the ground; she stands on her sword, her hand out at the foe, while they strike;</li>
 * <li>ink bursts up out of the ground round the ring in tall columns, breaks into clumps, flakes and drops that fly off
 * and fall, splashing the ground black all round (the user: "잉크가 많이 튀는"); the swords stand round the foe till they
 * are gone in light at 1.55 s.</li>
 * </ul>
 * Her camera: round to her side, then low behind her as she comes down, then back to her ({@link #shot},
 * IsosangClient.cageCamera). Every effect is a function of the swing's clock and of where its ring stands (following the
 * foe as drawn until the server sets it, IsosangRules.CAGE_PLANT).
 */
final class SwordCage {
    private SwordCage(){}
    /** How long Y is drawn for (seconds, IsosangClient.life): until the last splash of its ink has dried. */
    static final float LIFE=2.75f;

    // ───────────────────────────── the body ─────────────────────────────
    // (the videos: a dip on the pommel and up off it, turning to the foe; in the air her free hand reached out at it, her
    // sword drawn back low behind her, a knee up; down on the pommel again, a dip, and standing there her hand out at the
    // foe as the swords strike and the ink bursts; the hand lowered as her camera comes back to her)
    static final R6.Pose Y_DIP=w(.03,-.07,-4,7,0,d(-52,40),d(-12,66),d(-64,-28),d(-84,6),d(-86,-6));
    static final R6.Pose Y_SPRING=w(.04,.02,-6,-2,0,d(-34,70),d(-58,98),d(4,-58),d(-72,8),d(-100,-8));
    static final R6.Pose Y_REACH=w(.02,.03,-14,-6,0,d(-30,132),d(-56,158),d(-10,-6),d(-44,8),d(-104,-10));
    static final R6.Pose Y_REACH2=w(.02,.04,-16,-4,0,d(-26,138),d(-50,164),d(-6,-4),d(-50,8),d(-100,-10));
    static final R6.Pose Y_DROP=w(.03,.02,-10,2,0,d(-34,120),d(-56,150),d(-14,-8),d(-80,6),d(-92,-6));
    static final R6.Pose Y_LAND=w(.03,-.09,-8,8,0,d(-40,110),d(-50,140),d(-20,-8),d(-84,7),d(-88,-7));
    static final R6.Pose Y_POINT=w(.02,0,-14,3,0,d(-58,40),d(-30,60),d(-22,-4),d(-88,5),d(-89,-5));
    static final R6.Pose Y_POINT2=w(.02,0,-12,2,0,d(-60,36),d(-28,56),d(-19,-2),d(-88,5),d(-89,-5));
    static final R6.Pose Y_LOWER=w(.01,0,-4,2,0,d(-64,20),d(10,20),d(-60,-10),d(-89,4),d(-90,-4));
    static final Anim CLIP=Anim.joints(
        k(0,STANCE),
        k(.05,Y_DIP,Anim.Ease.OUT),
        k(.12,Y_SPRING),
        k(.23,Y_REACH,Anim.Ease.OUT),
        k(.38,Y_REACH2),
        k(.47,Y_DROP),
        k(.53,Y_LAND,Anim.Ease.OUT),
        k(.66,Y_POINT,Anim.Ease.OUT),
        k(1.02,Y_POINT2),
        k(1.2,Y_LOWER),
        k(1.36,SwordFormation.H_ON),
        k(CAGE_LENGTH/20f,STANCE)).after(IsosangSlash::grounded);
    /** While her hand reaches out at the foe (seconds: in the air, and standing on the pommel), its light gathering. */
    static final float REACH0=.18f,REACH1=.46f,POINT0=.58f,POINT1=1.18f;

    // ───────────────────────────── the ring ─────────────────────────────

    /** Where each Y's ring stood under its foe as drawn the last time (until the server sets it), and how it goes over to the server's. */
    private static final Map<UUID,Vec3d> FOLLOW=new LinkedHashMap<>();
    private record Settle(Vec3d from,float at){}
    private static final Map<UUID,Settle> SETTLE=new LinkedHashMap<>();
    static void clear(){FOLLOW.clear();SETTLE.clear();}
    /** How many ticks the ring takes to go over from where it followed the foe to where the server set it. */
    static final float SETTLE_TICKS=3;
    /**
     * Where Y's ring stands now (its middle on the ground): until the server sets it (IsosangRules.CAGE_PLANT) on the ground
     * under its foe as drawn (so long as it is still there and within the reach's leash), else where it was last, else
     * the server's word; once set, the server's, gone over to from where it followed the foe over {@link #SETTLE_TICKS}.
     */
    static Vec3d ring(StandClient.Cast c,float delta){
        var told=IsosangClient.cage(c.id());if(told==null)return null;
        float a=c.age(delta);
        if(told.planted()){
            var s=SETTLE.get(c.id());
            if(s==null){Vec3d from=FOLLOW.get(c.id());s=new Settle(from==null?told.at():from,a);SETTLE.put(c.id(),s);while(SETTLE.size()>32)SETTLE.remove(SETTLE.keySet().iterator().next());}
            return s.from().lerp(told.at(),Ease.smooth(a,s.at(),s.at()+SETTLE_TICKS));
        }
        if(a<CAGE_PLANT+SETTLE_TICKS){
            Vec3d feet=foe(c,delta);
            if(feet!=null){
                var v=IsosangClient.volley(c.id());var mc=MinecraftClient.getInstance();
                Vec3d g=IsosangAttack.cageGround(mc.world,mc.player,feet,v==null||v.centre()==null?feet.y:v.centre().y);
                FOLLOW.put(c.id(),g);while(FOLLOW.size()>32)FOLLOW.remove(FOLLOW.keySet().iterator().next());
                return g;
            }
        }
        Vec3d last=FOLLOW.get(c.id());return last!=null?last:told.at();
    }
    /** Where Y's foe stands as drawn now (its feet), while it is followed; null for none. */
    static Vec3d foe(StandClient.Cast c,float delta){
        var v=IsosangClient.volley(c.id());var mc=MinecraftClient.getInstance();
        if(v==null||v.target()<0||mc.world==null||!(mc.world.getEntityById(v.target()) instanceof LivingEntity e)||e.isRemoved())return null;
        if(v.centre()!=null&&!SwordVolley.leashed(v.centre(),e))return null;
        return e.getLerpedPos(delta);
    }
    /** Where Y's foe is struck (its chest as drawn), else the ring's middle a player's chest high. */
    static Vec3d chest(StandClient.Cast c,float delta,Vec3d ring){
        var v=IsosangClient.volley(c.id());var mc=MinecraftClient.getInstance();
        if(v!=null&&v.target()>=0&&mc.world!=null&&mc.world.getEntityById(v.target()) instanceof LivingEntity e&&!e.isRemoved()){
            Vec3d at=e.getLerpedPos(delta);if(Math.hypot(at.x-ring.x,at.z-ring.z)<CAGE_RADIUS+1)return at.add(0,e.getHeight()*.55,0);
        }
        return ring.add(0,1.0,0);
    }
    /** Where she stood as Y began (her feet on the pommel). */
    static Vec3d her(StandClient.Cast c){return c.origin();}
    /** The way from the ring toward her (level, unit), else back along Y's way. */
    static Vec3d toHer(StandClient.Cast c,Vec3d ring){
        Vec3d d=her(c).subtract(ring).multiply(1,0,1);
        return d.lengthSquared()<.01?Vec3d.fromPolar(0,c.yaw()).multiply(-1):d.normalize();
    }

    // ───────────────────────────── the swords ─────────────────────────────

    /**
     * The swords: SpiritSword at {@link #SCALE} (some 3.7 blocks pommel to point: the videos' are two to three times the
     * foe's height, hanging high over it, a sword's length and more over its head). Hanging over the ring {@link #HOVER_R} out from its middle at heights of their own
     * ({@link #HOVER_UP}, over its ground to the middle of the grip) round it ({@link #angle}: from the way toward her,
     * right +, a sixth of a turn apart), points down; each shows at {@link #shows} (SHOW0, then one every SHOW_STEP: the
     * videos' 0.1 to 0.29 s) out of a burst of starlight, growing to its size; down on the foe over {@link #FALL} before it
     * strikes the ground (IsosangRules.cageLands), quicker and quicker, to stand in it round the ring ({@link #STUCK_OUT}
     * out, its grip {@link #STUCK_UP} up, leaning {@link #TILT} degrees its point in: a crown of swords round the foe);
     * gone in light over [GONE0, GONE1] (the videos: by 1.6 s).
     */
    static final double SCALE=1.05,HOVER_R=2.2,STUCK_OUT=CAGE_RADIUS+.35,STUCK_UP=2.0,TILT=12;
    static final double[] HOVER_UP={8.1,9.2,7.6,8.8,7.9,9.5};
    static final float SHOW0=.1f,SHOW_STEP=.038f,FALL=.1f,GONE0=1.44f,GONE1=CAGE_FREE/20f+.03f;
    /** Sword {@code k}'s angle round the ring (degrees from the way toward her, right +). */
    static double angle(int k){return 30+60*k;}
    /** When sword {@code k} shows (seconds). */
    static float shows(int k){return SHOW0+k*SHOW_STEP;}
    /** When sword {@code k} strikes the ground (seconds). */
    static float lands(int k){return cageLands(k)/20f;}
    /** Out from the ring's middle toward sword {@code k} (level, unit). */
    static Vec3d radial(Vec3d toHer,int k){
        Vec3d right=new Vec3d(-toHer.z,0,toHer.x);double a=Math.toRadians(angle(k));
        return toHer.multiply(Math.cos(a)).add(right.multiply(Math.sin(a)));
    }
    /** A sword now: its grip, the way its blade points (grip to point), its guard's way, how big (of SCALE), how plain, how white-hot, how inked, and what it does (0 hanging, 1 coming down, 2 in the ground). */
    record Blade(Vec3d grip,Vec3d dir,Vec3d across,double size,float alpha,float heat,float ink,int phase){
        Vec3d tip(){return grip.add(dir.multiply(SpiritSword.TIP*SCALE*size));}
        Vec3d[] frame(){return SpiritSword.frame(dir,across);}
    }
    /** Where sword {@code k} hangs (its grip) and where it stands in the ground (its grip, its blade's way). */
    static Vec3d hung(Vec3d ring,Vec3d toHer,int k,float t){
        double bob=.06*Math.sin(t*5.3+k*1.9);
        return ring.add(radial(toHer,k).multiply(HOVER_R)).add(0,HOVER_UP[k]+bob,0);
    }
    static Vec3d stuckGrip(Vec3d ring,Vec3d toHer,int k){return ring.add(radial(toHer,k).multiply(STUCK_OUT)).add(0,STUCK_UP,0);}
    static Vec3d stuckDir(Vec3d toHer,int k){
        double a=Math.toRadians(TILT);return radial(toHer,k).multiply(-Math.sin(a)).add(0,-Math.cos(a),0).normalize();
    }
    /**
     * Sword {@code k} of a Y whose ring stands at {@code ring}, she at {@code herAt}, {@code t} seconds in (null when it
     * is not drawn); {@code end} when Y was called off (NaN if not): the swords not down by then go at once, those in the
     * ground fade.
     */
    static Blade blade(Vec3d ring,Vec3d herAt,int k,float t,float end){
        float show=shows(k),land=lands(k),fall=land-FALL;
        if(t<show-.01f||t>=GONE1)return null;
        boolean called=!Float.isNaN(end);
        if(called&&end<land&&t>=end+.1f)return null;
        Vec3d th=herAt.subtract(ring).multiply(1,0,1);th=th.lengthSquared()<.01?new Vec3d(0,0,1):th.normalize();
        float gone=called?Math.min(1,Ease.smooth(t,end,end+.3f)):0;
        // its guard's way hanging: square to the way toward her (its flat toward her, as the videos show their broad sides);
        // in the ground, round the ring (its flat out)
        Vec3d hangAcross=new Vec3d(-th.z,0,th.x),r=radial(th,k),stuckAcross=new Vec3d(-r.z,0,r.x);
        if(t<fall){
            float grow=Ease.out(t,show,show+.1f);
            float heat=.55f*(1-Ease.smooth(t,show,show+.22f))+.05f;
            float a=Ease.smooth(t,show-.01f,show+.06f)*(1-gone);
            return new Blade(hung(ring,th,k,t),new Vec3d(0,-1,0),hangAcross,.6+.4*grow,a,heat,0,0);
        }
        if(t<land){
            double u=Ease.unit(t,fall,land),v=Math.pow(u,1.6);
            Vec3d g=hung(ring,th,k,fall).lerp(stuckGrip(ring,th,k),v);
            Vec3d dir=new Vec3d(0,-1,0).lerp(stuckDir(th,k),v).normalize(),ac=hangAcross.lerp(stuckAcross,v);
            return new Blade(g,dir,ac.lengthSquared()<1e-6?stuckAcross:ac.normalize(),1,1-gone,.25f+.3f*(float)v,0,1);
        }
        float heat=.5f*FxLib.kick(t,land,.12f)+.03f;
        float ink=.7f*Ease.smooth(t,GONE0,GONE1)+.6f*gone;
        float a=(1-Ease.smooth(t,GONE0+.04f,GONE1))*(1-gone);
        return new Blade(stuckGrip(ring,th,k),stuckDir(th,k),stuckAcross,1,a,heat,Math.min(1,ink),2);
    }
    /** All six swords of Y {@code c} now (null where one is not drawn), its ring at {@code ring}. */
    static Blade[] blades(StandClient.Cast c,Vec3d ring,float t){
        float end=IsosangClient.cageEnd(c.id());Blade[] out=new Blade[CAGE_SWORDS];
        for(int k=0;k<CAGE_SWORDS;k++)out[k]=blade(ring,her(c),k,t,end);
        return out;
    }
    /** Whether drop {@code j} of Y {@code id} strikes (not one due after it was called off). */
    static boolean falls(UUID id,int j){float end=IsosangClient.cageEnd(id);return Float.isNaN(end)||CAGE_DROPS[j]/20f<=end;}
    /** Whether blow {@code i} of Y {@code id} lands (not one due after it was called off). */
    static boolean strikes(UUID id,int i){float end=IsosangClient.cageEnd(id);return Float.isNaN(end)||blowAt(CAGE,i)/20f<=end;}

    // ───────────────────────────── the ink ─────────────────────────────

    /**
     * The ink bursting up round the ring (the videos: from 0.7 s black columns shoot up round the foe, taller than the
     * swords, break into clumps by 1.1 s that fly off and drift and fall, gone by 1.45 s; the ground round it splashed
     * black): {@link #COLUMNS} columns, each a brush stroke drawn up out of the ground in a blink (twice, crossed, fixed in
     * the world), eaten away from below.
     */
    static final int COLUMNS=16;
    static final float COLUMN0=.68f,COLUMN_SPREAD=.26f,COLUMN_UP=.14f;
    /** Column {@code n}: its foot (forward of the ring's middle toward her, to her right, blocks), its height, width, lean and when it shoots up. */
    record Column(double fw,double rt,double height,double width,double lean,float born,int seed){}
    static Column column(int n){
        int seed=n*53+11;double a=Math.PI*2*(n+.35*Ease.hash(seed))/COLUMNS,r=.55+1.75*Ease.hash(seed+1);
        return new Column(Math.cos(a)*r,Math.sin(a)*r,3.5+4.0*Ease.hash(seed+2),.9+.7*Ease.hash(seed+3),.15+.35*Ease.hash(seed+4),COLUMN0+COLUMN_SPREAD*(float)Ease.hash(seed+5),seed);
    }
    /** How far up column {@code n} is drawn {@code t} seconds in (0 to 1), and how much of it is eaten from below. */
    static float columnUp(Column col,float t){return Ease.out(t,col.born(),col.born()+COLUMN_UP);}
    static float columnEaten(Column col,float t){return Ease.smooth(t,col.born()+.3f,col.born()+.64f);}
    /**
     * The ink flung off (the videos: big clumps breaking off the columns and flying off, drifting, falling; flakes and
     * drops with them): {@link #CLUMPS} clumps, {@link #FLAKES} flakes and {@link #DROPS} drops, each out of a column
     * some way up it as it bursts, out and up from the ring, falling under gravity; each that reaches the ground leaves a
     * splash there.
     */
    static final int CLUMPS=32,FLAKES=40,DROPS=32;
    static final double GRAVITY=12;
    /** A piece of ink: its kind (0 clump, 1 flake, 2 drop), where and when it leaves, its way and speed, its size, its spin. */
    record Bit(int kind,Vec3d from,Vec3d velocity,float born,double size,double spin,int seed){
        /** Where it is {@code s} seconds after it left (it falls), and how fast it goes then. */
        Vec3d at(double s){return from.add(velocity.multiply(s)).add(0,-.5*GRAVITY*s*s,0);}
        Vec3d moving(double s){return velocity.add(0,-GRAVITY*s,0);}
        /** When (seconds after it left) it comes down to the height {@code floor}. */
        double lands(double floor){
            double a=.5*GRAVITY,b=-velocity.y,cc=floor-from.y;double disc=b*b-4*a*cc;
            return disc<0?99:(-b+Math.sqrt(disc))/(2*a);
        }
    }
    /** Piece {@code i} of kind {@code kind} round a ring at {@code ring}, toward her {@code toHer}. */
    static Bit bit(Vec3d ring,Vec3d toHer,int kind,int i){
        int seed=kind*1009+i*67+5;Column col=column(i%COLUMNS);
        Vec3d right=new Vec3d(-toHer.z,0,toHer.x);
        Vec3d foot=ring.add(toHer.multiply(col.fw())).add(right.multiply(col.rt()));
        // (clumps break off low on the columns and many come down round it; flakes and drops fly from higher up)
        double up=col.height()*(kind==0?.1+.35*Ease.hash(seed):.25+.6*Ease.hash(seed));
        Vec3d out=foot.subtract(ring).multiply(1,0,1);out=out.lengthSquared()<1e-4?toHer:out.normalize();
        double spread=(Ease.hash(seed+1)-.5)*1.4,ca=Math.cos(spread),sa=Math.sin(spread);
        out=new Vec3d(out.x*ca-out.z*sa,0,out.x*sa+out.z*ca);
        double speed=kind==0?2.2+3.6*Ease.hash(seed+2):kind==1?1.6+3.2*Ease.hash(seed+2):3.5+5*Ease.hash(seed+2);
        double rise=kind==0?1.2+3.2*Ease.hash(seed+3):kind==1?2+3*Ease.hash(seed+3):2.5+4*Ease.hash(seed+3);
        double size=kind==0?.2+.28*Ease.hash(seed+4):kind==1?.12+.16*Ease.hash(seed+4):.05+.07*Ease.hash(seed+4);
        float born=col.born()+COLUMN_UP*(float)(.4+.6*Ease.hash(seed+5))+.06f*(float)Ease.hash(seed+6);
        return new Bit(kind,foot.add(0,up,0),out.multiply(speed).add(0,rise,0),born,size,1.5+3*Ease.hash(seed+7),seed);
    }
    /** How long a piece of ink is drawn at most after it leaves (seconds), and how long it fades over at its end. */
    static final float BIT_LIFE=1f,BIT_FADE=.25f;
    /** The splashes round the ring as the columns burst (the videos: the ground round the foe black all over): {@link #SPLASHES} of them. */
    static final int SPLASHES=10;

    // ───────────────────────────── the passes ─────────────────────────────

    /** The ink pass: the columns of ink, the pool under the ring, the splashes round it and where the flung ink lands. */
    static void ink(Fx c){
        float t=c.sec;Vec3d ring=ring(c.cast,c.delta);if(ring==null)return;
        Vec3d th=toHer(c.cast,ring),right=new Vec3d(-th.z,0,th.x);double floor=ring.y+.02;
        float end=IsosangClient.cageEnd(c.cast.id());
        boolean burst=Float.isNaN(end)||end>=COLUMN0;
        // the ground under the ring darkens as the swords come down (a pool soaked in), drying as the ink goes
        float pool=t-CAGE_DROPS[0]/20f;
        if(pool>=0&&pool<1.4f&&falls(c.cast.id(),0)){
            float dry=Ease.smooth(pool,.6f,1.4f);
            stain(new Vec3d(ring.x,floor-.004,ring.z),(CAGE_RADIUS*2+1.4)*(.6+.4*Ease.out(pool,0,.2f)),0,1,13,dry,.62f*(1-Ease.smooth(dry,.5f,1)));
        }
        if(!burst)return;
        // the splashes round it as the columns burst
        for(int n=0;n<SPLASHES;n++){
            int seed=n*71+19;double a=Math.PI*2*(n+.5*Ease.hash(seed))/SPLASHES,r=1.2+1.9*Ease.hash(seed+1);
            float born=COLUMN0+.05f+COLUMN_SPREAD*(float)Ease.hash(seed+2);
            Vec3d p=ring.add(th.multiply(Math.cos(a)*r)).add(right.multiply(Math.sin(a)*r));
            splatAt(new Vec3d(p.x,floor+.002*(n%5),p.z),1.3+1.2*Ease.hash(seed+3),Math.PI*2*Ease.hash(seed+4),1+.6*Ease.hash(seed+5),seed,t-born,.92f);
        }
        // where the flung clumps come down (the drops too small to show)
        for(int i=0;i<CLUMPS;i++){
            Bit b=bit(ring,th,0,i);double l=b.lands(floor);if(l>BIT_LIFE+.3)continue;
            Vec3d p=b.at(l);splatAt(new Vec3d(p.x,floor+.003+.001*(i%4),p.z),b.size()*4.2,Math.PI*2*Ease.hash(b.seed()+9),1.3,b.seed(),t-b.born()-(float)l,.88f);
        }
        // the columns: brush strokes drawn up out of the ground, twice crossed, eaten from below
        for(int n=0;n<COLUMNS;n++){
            Column col=column(n);if(t<col.born()||t>=col.born()+.7f)continue;
            float up=columnUp(col,t),eaten=columnEaten(col,t);if(up<=0||eaten>=1)continue;
            Vec3d foot=ring.add(th.multiply(col.fw())).add(right.multiply(col.rt()));foot=new Vec3d(foot.x,floor,foot.z);
            Vec3d out=foot.subtract(ring).multiply(1,0,1);out=out.lengthSquared()<1e-4?th:out.normalize();
            Vec3d top=foot.add(0,col.height(),0).add(out.multiply(col.lean()*col.height()));
            Vec3d to=foot.add(top.subtract(foot).multiply(up));
            Vec3d side=new Vec3d(-out.z,0,out.x);
            strip(foot,to,side,col.width(),4,col.seed()&15,eaten,0,.96f);
            strip(foot,to,out,col.width()*.85,4,(col.seed()+5)&15,eaten,0,.96f);
        }
    }
    /** The blended pass: the swords' bodies, the flung ink, and the ink edge under each burst on the foe. */
    static void alpha(Fx c,EffectMesh m){
        float t=c.sec;Vec3d ring=ring(c.cast,c.delta);if(ring==null)return;
        for(var b:blades(c.cast,ring,t))if(b!=null)SpiritSword.body(m,c.camera,b.grip(),b.frame(),SCALE*b.size(),.95f*b.alpha(),b.heat(),b.ink());
        float end=IsosangClient.cageEnd(c.cast.id());
        if(Float.isNaN(end)||end>=COLUMN0){
            Vec3d th=toHer(c.cast,ring);double floor=ring.y;
            for(int kind=0;kind<3;kind++){
                int n=kind==0?CLUMPS:kind==1?FLAKES:DROPS;
                for(int i=0;i<n;i++){
                    Bit b=bit(ring,th,kind,i);double s=t-b.born();if(s<0||s>=BIT_LIFE)continue;
                    double l=b.lands(floor);if(s>=l)continue;
                    Vec3d q=b.at(s);float a=(1-Ease.smooth((float)s,BIT_LIFE-BIT_FADE,BIT_LIFE))*Ease.smooth((float)s,0,.03f)*near(q,c.camera);
                    // (a clump breaks into smaller ones as it flies: the videos' clumps go to shards)
                    double size=b.size()*(kind==0?1-.35*Ease.smooth((float)s,.2f,.7f):1);
                    if(kind==0)blot(m,q,b.moving(s),size,b.seed(),INK,.95f*a);
                    else if(kind==1)flake(m,q,size,s,b.spin(),b.seed(),INK,.95f*a);
                    else drop(m,q,b.moving(s),size,b.seed(),INK,.95f*a);
                }
            }
        }
        Vec3d chest=chest(c.cast,c.delta,ring);
        for(int i=0;i<blows(CAGE);i++)if(strikes(c.cast.id(),i))IsosangEmpower.burstInk(c,m,hitAt(chest,i),blowAt(CAGE,i)/20f,HIT_BURST,i*13+7);
    }
    /** Where blow {@code i} bursts on the foe: round its chest, a little off each time. */
    static Vec3d hitAt(Vec3d chest,int i){return chest.add((Ease.hash(i*7+1)-.5)*.5,(Ease.hash(i*7+2)-.5)*.7,(Ease.hash(i*7+3)-.5)*.5);}
    /** How big each blow's burst on the foe is (IsosangEmpower.burst's size), and each drop's flash. */
    static final double HIT_BURST=.42;
    /**
     * The light under the ink: each sword's outline and glow (SpiritSword.glow), the burst of starlight each shows out of,
     * the light gathering in her hand, the streak of light each comes down as and the flash, the ring of light along the
     * ground and the spokes of light as each two strike, and a burst on the foe at each blow.
     */
    static void additive(Fx c,EffectMesh m){
        float t=c.sec;Vec3d ring=ring(c.cast,c.delta);if(ring==null)return;
        Vec3d th=toHer(c.cast,ring);
        var blades=blades(c.cast,ring,t);
        for(int k=0;k<CAGE_SWORDS;k++){
            var b=blades[k];if(b==null)continue;
            SpiritSword.glow(m,b.grip(),b.frame(),SCALE*b.size(),b.alpha(),b.heat(),b.ink());
            // the burst of starlight it shows out of
            float show=shows(k);
            if(t>=show-.01f&&t<show+.2f){
                float a=Ease.smooth(t,show-.01f,show+.01f)*(1-Ease.unit(t,show,show+.2f));
                Vec3d o=facing(b.grip().add(b.dir().multiply(SpiritSword.TIP*SCALE*.4)),c.camera);
                m.softDisc(o,.8,BLUE,.22f*a);m.softDisc(o,.3,PALE,.45f*a);glint8(m,o,.45*(.6+.4*Ease.out(t,show,show+.08f)),WHITE,.75f*a);
            }
            // coming down: its streak of light out of the sky
            float land=lands(k);
            if(b.phase()>=1&&t<land+.2f){
                Vec3d top=hung(ring,th,k,land-FALL).add(0,7,0),tip=b.tip();
                float a=Ease.smooth(t,land-FALL-.01f,land-FALL+.02f)*(1-Ease.smooth(t,land+.04f,land+.2f));
                m.softLine(top,tip,1.0,BLUE,.34f*a);m.softLine(top,tip,.45,AZURE,.58f*a);m.line(top,tip,.16,WHITE,.92f*a);
            }
        }
        // each two coming down: a flash at the ring, a ring of light along the ground, spokes of light out of it
        for(int j=0;j<CAGE_DROPS.length;j++){
            float at=CAGE_DROPS[j]/20f;if(t<at-.01f||t>=at+.4f||!falls(c.cast.id(),j))continue;
            float a=Ease.smooth(t,at-.01f,at+.01f),u=Ease.unit(t,at,at+.3f);
            Vec3d g=ring.add(0,.1,0),near=facing(ring.add(0,.9,0),c.camera);
            m.softDisc(near,2.6*(.7+.3*u),BLUE,.4f*a*(1-u));m.softDisc(near,1.0,PALE,.6f*a*(1-u)*(1-u));m.softDisc(near,.35,WHITE,.8f*a*(1-u)*(1-u));
            FxLib.ring(m,g,.6+3.4*Ease.out(t,at,at+.32f),.16,AZURE,.75f*a*(1-u));
            FxLib.ring(m,g,.4+2.2*Ease.out(t,at,at+.24f),.07,WHITE,.85f*a*(1-u));
            for(int s=0;s<10;s++){
                int seed=j*97+s*13;double ang=Math.PI*2*(s+Ease.hash(seed))/10;
                Vec3d dir=new Vec3d(Math.cos(ang),.08+.22*Ease.hash(seed+1),Math.sin(ang)).normalize();
                double len=(1.4+1.6*Ease.hash(seed+2))*Ease.out(t,at,at+.14f);
                Vec3d p0=g.add(0,.3,0).add(dir.multiply(.5)),p1=p0.add(dir.multiply(len));
                m.needle(p0,p1,.12,.3,s%3==0?WHITE:s%3==1?PALE:AZURE,.8f*a*(1-Ease.smooth(t,at+.06f,at+.22f)));
            }
        }
        // the light gathering in her hand as it reaches out at the foe
        float hand=Ease.window(t,REACH0,REACH1,.08f,.06f)+Ease.window(t,POINT0,POINT1,.06f,.14f);
        if(hand>.01f){
            Vec3d h=hand(c,t);if(h!=null){Vec3d o=facing(h,c.camera);m.softDisc(o,.5,BLUE,.35f*hand);m.softDisc(o,.18,PALE,.7f*hand);glint8(m,o,.32+.06*Math.sin(t*40),WHITE,.65f*hand);}
        }
        // a burst on the foe at each blow
        Vec3d chest=chest(c.cast,c.delta,ring);
        for(int i=0;i<blows(CAGE);i++)if(strikes(c.cast.id(),i))IsosangEmpower.burst(c,m,hitAt(chest,i),new Vec3d(0,-1,0),blowAt(CAGE,i)/20f,HIT_BURST,i*13+7);
        // the swords gone in light: a scatter of motes off each as it goes
        if(t>=GONE0&&t<GONE1+.25f)for(int k=0;k<CAGE_SWORDS;k++){
            Vec3d g=stuckGrip(ring,th,k),d=stuckDir(th,k);
            for(int s=0;s<5;s++){
                int seed=k*31+s*7;float born=GONE0+.1f*(float)Ease.hash(seed);float u=Ease.unit(t,born,born+.4f);if(u<=0||u>=1)continue;
                Vec3d p=g.add(d.multiply(SpiritSword.TIP*SCALE*(.1+.6*Ease.hash(seed+1)))).add(0,.9*u,0);
                glint8(m,facing(p,c.camera),.12*(1-u),s%2==0?PALE:WHITE,.7f*(1-u));
            }
        }
    }
    /** Where her free hand is now (its light: her pose on her clock, up her leap); null with no pose. */
    static Vec3d hand(Fx c,float t){
        var p=R6.poseOf(CLIP.frame(t));
        Vec3d feet=her(c.cast).add(0,cageLeap(t*20),0);
        return KagenoClips.freeHand(feet,c.cast.yaw(),p);
    }
    /** Over the ink: a few stars on the swords hanging and standing, a star at each sword's point as it strikes. */
    static void overlay(Fx c,EffectMesh m){
        float t=c.sec;Vec3d ring=ring(c.cast,c.delta);if(ring==null)return;
        var blades=blades(c.cast,ring,t);
        for(int k=0;k<CAGE_SWORDS;k++){
            var b=blades[k];if(b==null||b.phase()==1)continue;
            for(int s=0;s<3;s++){int seed=k*29+s*11;Vec3d q=b.grip().add(b.dir().multiply(SpiritSword.TIP*SCALE*b.size()*(.25+.6*Ease.hash(seed))));glint8(m,q,.1+.07*Ease.hash(seed+1),s==0?WHITE:s==1?PALE:LILAC,.75f*b.alpha()*twinkle(t,seed));}
            float land=lands(k);
            if(t>=land-.01f&&t<land+.16f&&b.phase()==2){float a=(1-Ease.unit(t,land,land+.16f));Vec3d at=b.grip().add(b.dir().multiply(SpiritSword.TIP*SCALE*.75));glint8(m,facing(at,c.camera),.55*a+.1,WHITE,.85f*a);}
        }
    }
    /** Lights: the swords hanging, each two as they strike, the ink's burst darkening nothing (ink casts no light); the afterglow short as they come down. */
    static void frame(Fx c,FxFrame f){
        float t=c.sec;Vec3d ring=ring(c.cast,c.delta);if(ring==null)return;
        float hang=Ease.window(t,SHOW0,CAGE_DROPS[0]/20f,.15f,.05f);
        if(hang>.01f)f.light(ring.add(0,6.3,0),0x9fc8ff,7,.5f*hang);
        for(int j=0;j<CAGE_DROPS.length;j++){float k=falls(c.cast.id(),j)?FxLib.kick(t,CAGE_DROPS[j]/20f,.15f):0;if(k>.01f)f.light(ring.add(0,1,0),0xcfe4ff,9,.9f*k);}
        float stand=Ease.window(t,CAGE_DROPS[0]/20f,GONE1,.05f,.15f);
        if(stand>.01f)f.light(ring.add(0,1.6,0),0x8cbcff,5,.35f*stand);
        float hand=Ease.window(t,REACH0,REACH1,.08f,.06f)+Ease.window(t,POINT0,POINT1,.06f,.14f);
        if(hand>.01f){Vec3d h=hand(c,t);if(h!=null)f.light(h,0x9fc8ff,3,.5f*hand);}
        // quick as they come down (the streaks): short afterglow, else each leaves copies of itself
        if(t>=CAGE_DROPS[0]/20f-FALL-.02f&&t<CAGE_DROPS[CAGE_DROPS.length-1]/20f+.12f)f.afterglow(.05f);
    }
    /** A jolt as each two strike (over two frames) and a smaller one as the ink bursts. */
    static float shake(StandClient.Cast cast,float sec){
        float v=0;
        for(int j=0;j<CAGE_DROPS.length;j++)if(falls(cast.id(),j))v=Math.max(v,(j==2?.5f:.35f)*bump(sec,CAGE_DROPS[j]/20f,.07f));
        if(falls(cast.id(),2))v=Math.max(v,.22f*bump(sec,COLUMN0+.1f,.12f));
        return v;
    }

    // ───────────────────────────── her camera ─────────────────────────────

    /**
     * How much her Y's shot holds her camera {@code t} seconds in (0 to 1, IsosangClient.cageCamera): swung in over
     * CAGE_SIDE_IN, let go of over [CAGE_BACK0, CAGE_BACK1] (and over 0.3 s from when it was called off).
     */
    static double held(StandClient.Cast c,float t){
        double w=Ease.smooth(t,0,CAGE_SIDE_IN)*(1-Ease.smooth(t,CAGE_BACK0,CAGE_BACK1));
        float end=IsosangClient.cageEnd(c.id());if(!Float.isNaN(end))w*=1-Ease.smooth(t,end,end+.3f);
        return w;
    }
    /**
     * Her Y's shot {@code t} seconds in (the videos): first at her side ({@link #SIDE}: a little behind her, round to the
     * side the formation's middle is, about level with her up in her leap), she high in the air on one side and the swords
     * over the foe on the other, turning slowly round; then round low behind her as she comes
     * down ({@link #BEHIND}, over [CAGE_BEHIND0, CAGE_BEHIND1]), she on her sword to one side, the foe beyond her, the
     * camera pressing slowly in. It goes round her (its angle and its distance eased), never through her; short of a
     * block between her and it.
     */
    static CinematicDirector.Pose shot(StandClient.Cast c,float t,float delta){
        Vec3d ring=ring(c,delta);if(ring==null)return null;
        Vec3d h=her(c);Vec3d u=ring.subtract(h).multiply(1,0,1);
        Vec3d right=u.lengthSquared()<.25?new Vec3d(-Vec3d.fromPolar(0,c.yaw()).z,0,Vec3d.fromPolar(0,c.yaw()).x):new Vec3d(-u.z,0,u.x).normalize();
        Vec3d[] f=framing(h,ring,c.yaw(),side(c,h,right),t);
        Vec3d cam=clear(h.add(0,1.1,0),f[0]),d=f[1].subtract(cam);
        float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z)),pitch=(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));
        return new CinematicDirector.Pose(cam,yaw,pitch,0);
    }
    /**
     * Where the shot's camera stands and what it looks at {@code t} seconds into Y ({@link #shot}; she at {@code h}, its
     * ring at {@code ring}, its way {@code yaw} for when they are one over the other, the camera round on side {@code s}
     * of her: 1 her right, -1 her left): {camera, look}.
     */
    static Vec3d[] framing(Vec3d h,Vec3d ring,float yaw,double s,float t){
        Vec3d u=ring.subtract(h).multiply(1,0,1);double far=u.length();
        u=far<.5?Vec3d.fromPolar(0,yaw):u.multiply(1/far);
        Vec3d right=new Vec3d(-u.z,0,u.x);
        double w=Ease.smooth(t,CAGE_BEHIND0,CAGE_BEHIND1);
        double turn=SIDE[0]+4*Ease.unit(t,CAGE_SIDE_IN,CAGE_BEHIND0),press=.4*Ease.unit(t,CAGE_BEHIND1,CAGE_BACK0);
        double ang=Math.toRadians(turn+(BEHIND[0]-turn)*w);
        double r=SIDE[1]+(BEHIND[1]-press-SIDE[1])*w,up=SIDE[2]+(BEHIND[2]-SIDE[2])*w;
        Vec3d cam=h.add(u.multiply(-Math.cos(ang)*r)).add(right.multiply(s*Math.sin(ang)*r)).add(0,up,0);
        // at her side it looks between her up in her leap (as high as she is now) and the swords over the foe (the videos:
        // she on one side, near, the swords on the other); behind her, past her at the foe, raised as she still comes down
        // so she is not lost off the top of the picture as it swings round
        double leap=cageLeap(t*20);
        Vec3d a=h.add(0,leap+SIDE_HER,0).subtract(cam).normalize(),b=ring.add(0,SIDE_SWORDS,0).subtract(cam).normalize();
        Vec3d mid=a.multiply(SIDE_WEIGH).add(b.multiply(1-SIDE_WEIGH));mid=mid.lengthSquared()<1e-6?b:mid.normalize();
        Vec3d look1=cam.add(mid.multiply(8)),look2=h.lerp(ring.add(0,1.3,0),.68).add(0,leap*DOWN_FOLLOW,0);
        return new Vec3d[]{cam,look1.lerp(look2,w)};
    }
    /**
     * The side shot: its angle off straight behind her (degrees), its distance from her (the videos: she near the camera,
     * some 4 to 5 blocks, the swords far beyond) and how high over where she stood (about her feet's height up in her
     * leap); how high over her feet her middle is, and over the ring's ground the point under the swords it looks toward:
     * it looks between the two, hers weighed the more ({@link #SIDE_WEIGH}: the videos' she up at one side, the swords
     * beside her, the foe low under them; she clear of the health bar at the screen's top left, which hid her head when the
     * camera stood low and looked at the swords). The shot behind her: its angle, distance and height. How far what it
     * looks at behind her is raised for each block she is still up in her leap ({@link #DOWN_FOLLOW}: it follows her down).
     */
    static final double[] SIDE={66,4.8,2.2},BEHIND={34,4.5,.55};
    static final double SIDE_HER=.9,SIDE_SWORDS=3.4,SIDE_WEIGH=.55,DOWN_FOLLOW=1.4;
    /** Which side of her the camera goes round to (1 her right, -1 her left): the formation's middle's (over its swords), her right if it is ahead or behind. */
    static double side(StandClient.Cast c,Vec3d h,Vec3d right){
        var v=IsosangClient.volley(c.id());if(v==null||v.centre()==null)return 1;
        double k=v.centre().subtract(h).dotProduct(right);return k<-.5?-1:1;
    }
    /** The camera at {@code cam} pulled in toward {@code from} short of a block between them. */
    static Vec3d clear(Vec3d from,Vec3d cam){
        var mc=MinecraftClient.getInstance();if(mc.world==null)return cam;
        var hit=mc.world.raycast(new RaycastContext(from,cam,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,mc.player));
        if(hit.getType()==HitResult.Type.MISS)return cam;
        Vec3d d=cam.subtract(from);double l=d.length();if(l<1e-4)return cam;
        return from.add(d.multiply(Math.max(.3,from.distanceTo(hit.getPos())-.3)/l));
    }
}
