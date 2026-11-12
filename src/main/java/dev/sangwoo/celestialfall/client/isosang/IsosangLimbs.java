package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.combat.MotionPose;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * (2.104.0, the user: "R6을 빼고 모든 모션을 전부 자연스럽게 만들어줘", G aside) Lee So-sang's arms and legs bent as a
 * person's are. Her clips are written with the R6 kit's one-piece limbs (a shoulder to the hand, a hip to the foot, the
 * blade's way); every frame they give is bent here, on the player model's two-piece limbs (MotionRig's elbows and
 * knees), keeping what the keys say:
 * <ul>
 * <li>The sword arm still puts the hand on the line the key put it, the blade along the same way: the elbow folds the
 * forearm toward the side the blade leaves the hand (the upper arm leaning the other way), the more the further the
 * arm reaches across her or up, and the wrist (grip) takes what is left. Never so far the forearm passes the blade.</li>
 * <li>The free arm keeps its line too, its elbow a little bent, its forearm folding toward the limb's front and a
 * little in toward her (the elbow out and back), straight when it points ahead at something.</li>
 * <li>A leg standing on the ground has its foot drawn in under the hip as the body comes down, the knee bent to reach it
 * (a crouch is bent knees over the feet, not legs splayed in an A; the back leg of a lunge longer), the knee over the
 * foot. In the air a leg holds the key's stance (as if she stood on the frame's own floor: the crouch she comes down in
 * is the one she lands in), tucked the more both feet are off the ground; a leg the key lifts keeps its foot on the line
 * the key put it along, the knee bent the more it is raised (a raised knee, a leg swung back with its shin up). A foot
 * stands on the ground where the held one meets it, and stays a moment on what she springs off (the leg straight)
 * before it tucks; no foot ever goes into the ground.</li>
 * </ul>
 * The ground is wherever she stands then in the clip's frame ({@link Ground}: the formation's pommel, the steed sword,
 * the leap of her cage), so every bend is a function of the clip's clock. G keeps the R6 kit as it is.
 */
public final class IsosangLimbs {
    private IsosangLimbs(){}

    /** How high the ground she stands on is in her clip's frame (blocks over the frame's feet) at a second of the cast. */
    @FunctionalInterface public interface Ground{double at(float sec);}
    /** The floor under her clip's feet (the frame's own ground). */
    public static final Ground FLOOR=sec->0;
    /** The fix of a clip whose ground is {@code ground}: every frame bent ({@link #bend}). */
    public static Anim.Fix natural(Ground ground){return (sec,f)->bend(f,supports(ground,sec));}
    /**
     * How long her feet take to leave what she stood on (seconds): a foot springing off it, or what she stands on dropping
     * away from under her (H's leap goes up at 27 blocks a second), stays reaching for it, the leg straight, and only over
     * this long gives itself to the air (tucks): the ground is looked at over this past, a step at a time
     * ({@link #supports}), so no knee flips from the spring to the tuck in a frame. Coming down, the legs hold the key's
     * stance in the air (so the crouch she lands in is the one she comes down in) and the ground is looked at as it is.
     */
    static final double LEAVE_WINDOW=.1;
    static final int LOOKS=10;
    /** What ground {@code ground} has under her at {@code sec}, and, for each step back over LEAVE_WINDOW, the higher of it then and now. */
    static double[] supports(Ground ground,float sec){
        double now=ground.at(sec);double[] s=new double[LOOKS];s[0]=now;
        for(int k=1;k<LOOKS;k++)s[k]=Math.max(now,ground.at(sec-(float)(LEAVE_WINDOW*k/(LOOKS-1))));
        return s;
    }
    /** How high a pommel of the formation stands as her clips reckon it where it is not the first one's (blocks: its swords stand 1.25 to 1.75). */
    static final double POMMEL=1.5;
    /** How long the ground takes to drop away from under her feet as she leaves what she stood on (seconds). */
    static final double LEAVE=.12;
    /**
     * H's (2.82.4): the ground until she leaps, the pommel struck under her from as it is struck (IsosangRules.H_PLANT)
     * while her plan has her dive onto it (IsosangRules.planned), and the ground again as she steps off at its end.
     */
    public static final Ground FORMATION=sec->{
        double pommel=sec>=IsosangRules.H_PLANT?IsosangRules.FORMATION_STAND*(1-smooth(sec,IsosangRules.H_DROP,IsosangRules.H_DROP+LEAVE)):0;
        return pommel-IsosangRules.planned(sec);
    };
    /**
     * T's (2.96.0): the pommel she coils on, dropping away as she sets off along the dash down to the ground in front of
     * the foe (IsosangRules.lungeHeight), the ground there and under her flight on (out of sight), the far pommel as she
     * is seen again on it.
     */
    public static final Ground LUNGE=sec->{
        double a=sec*20;
        if(a<IsosangRules.LUNGE_GO)return 0;
        if(a<IsosangRules.LUNGE_LAND)return POMMEL*(IsosangRules.lungeDash(a)-smooth(a,IsosangRules.LUNGE_GO,IsosangRules.LUNGE_GO+LEAVE*20));
        if(a<IsosangRules.LUNGE_HOP)return 0;
        if(a<IsosangRules.LUNGE_HOP+IsosangRules.Z_TRAVEL)return POMMEL*smooth(a,IsosangRules.LUNGE_HOP+IsosangRules.Z_TRAVEL-1,IsosangRules.LUNGE_HOP+IsosangRules.Z_TRAVEL)-IsosangRules.lungeHeight(a,POMMEL,POMMEL);
        return 0;
    };
    /** When after her leap her steed sword starts coming up under her feet (seconds: SwordFinale.steedIn, her own sword pulled 0.02 s after it, RISE*.4 on). */
    static final double STEED_IN=.02+.12*.4;
    /** Y's (2.97.0): the pommel she leaps off and comes down on again (IsosangRules.cageLeap, on Y's ticks). */
    public static final Ground CAGE=sec->{double a=sec*20;return a>0&&a<IsosangRules.CAGE_LEAP_LAND?-IsosangRules.cageLeap(a):0;};
    /**
     * H again's (2.101.1): the pommel she springs off, her steed sword coming up under her feet as she lands on it
     * (IsosangRules.FINALE_RIDE) and under them as she rides it round, and the ground as she steps off it and comes down
     * (IsosangRules.finaleDrawn).
     */
    public static final Ground FINALE=sec->{
        double s=IsosangRules.FORMATION_STAND;
        if(sec<IsosangRules.FINALE_LEAP)return 0;
        if(sec<IsosangRules.FINALE_RIDE)return (s-IsosangRules.finaleDrawn(sec,s))*(1-smooth(sec,IsosangRules.FINALE_LEAP+STEED_IN,IsosangRules.FINALE_RIDE));
        if(sec<IsosangRules.FINALE_DESCEND)return 0;
        if(sec<IsosangRules.FINALE_LAND)return -IsosangRules.finaleDrawn(sec,s)*smooth(sec,IsosangRules.FINALE_DESCEND,IsosangRules.FINALE_DESCEND+LEAVE);
        return 0;
    };

    static final double ARM_UP=R6.ARM_UPPER,ARM_LO=R6.ARM_LOWER,LEG_UP=R6.LEG_UPPER,LEG_LO=R6.LEG_LOWER,LEG=LEG_UP+LEG_LO;
    /** The free arm's forearm turned in toward her this much when it hangs (degrees). */
    static final double TURN_IN=25;
    /** How much a crouch draws a standing foot in under its hip (of the way the key put it): a leg out ahead or to the side, and one reaching back. */
    static final double DRAW_FRONT=.60,DRAW_BACK=.76;
    /** How much of the foot's way along the ground turns a standing knee out over it. */
    static final double KNEE_OUT=.35;
    /** A raised leg's knee bent at most this much as it is lifted behind and ahead (degrees), and tucked this much more with both feet high; the foot kept on the line the key put the leg along. */
    static final double RAISE_BACK=65,RAISE_FRONT=110,TUCK=40;

    /**
     * Frame {@code f} (a solved R6 frame, its limbs straight) with its elbows and knees bent, the ground {@code ground}
     * blocks over the frame's feet (and there lately).
     */
    public static R6.Frame bend(R6.Frame f,double ground){return bend(f,new double[]{ground});}
    /** {@link #bend} with what is under her now and lately ({@link #supports}: the first, now). */
    static R6.Frame bend(R6.Frame f,double[] supports){
        R6.Pose p=R6.poseOf(f);
        MotionPose m=f.joints().copy();
        float grip=f.grip();
        // ---- the sword arm: the hand on its line, the blade its way, the elbow folded toward the blade's side
        V d=V.of(p.rArm).unit(),b=V.of(p.blade).unit();
        V side=b.off(d);double sin=side.len();
        if(sin>1e-4){
            double want=swordBend(d)*smooth(sin,.12,.35);
            double th=fold(Math.toRadians(want),Math.atan2(sin,b.dot(d)));
            if(th>1e-5){
                Arm a=arm(d,side.times(1/sin),th);
                grip=(float)-Math.toDegrees(Math.atan2(b.dot(a.lower),b.dot(a.lowerFront)));
                m.joint[MotionPose.RIGHT_ARM]=R6.angles(R6.limbTurn(a.upper.dir(),a.front.dir()));
                m.elbow[0]=(float)-Math.toDegrees(th);
            }
        }
        // ---- the free arm: a little bent, the forearm toward the limb's front and in toward her
        V l=V.of(p.lArm).unit();
        double lth=Math.toRadians(clamp(15+55*Math.max(0,l.x)+12*Math.max(0,l.y)-15*smooth(l.z,.35,.85)*(1-Math.abs(l.y)),3,88));
        V front0=V.of(R6.swing(l.dir())).off(l).unit();
        V in=turn(front0,l,-Math.toRadians(TURN_IN*smooth(-l.y,.2,.8)));
        Arm free=arm(l,in,lth);
        m.joint[MotionPose.LEFT_ARM]=R6.angles(R6.limbTurn(free.upper.dir(),free.front.dir()));
        m.elbow[1]=(float)-Math.toDegrees(lth);
        // ---- the legs
        Quaterniond q=torso(f);
        V[] legs={V.of(p.rLeg).unit(),V.of(p.lLeg).unit()};
        Vector3d[] hips=new Vector3d[2];double[] feet=new double[2];
        for(int i=0;i<2;i++){hips[i]=hip(q,f,i);feet[i]=hips[i].y+LEG*hit(q,legs[i]).y;}
        // how far both feet are off it (a leap's tuck), over what she stood on lately
        double airborne=0;
        for(double s:supports)airborne+=smooth(Math.min(feet[0],feet[1])-s,.08,.4)/supports.length;
        for(int i=0;i<2;i++){
            Leg leg=leg(q,hips[i],legs[i],supports,airborne);
            m.joint[i==0?MotionPose.RIGHT_LEG:MotionPose.LEFT_LEG]=R6.angles(R6.limbTurn(leg.upper.dir(),leg.front.dir()));
            m.knee[i]=(float)Math.toDegrees(leg.bend);
        }
        return new R6.Frame(m,grip,f.fw(),f.up(),f.rt(),f.yaw(),f.pitch(),f.roll());
    }

    /**
     * How far the sword arm's elbow is bent with its hand on the line {@code d} (degrees): a little as it holds the sword
     * (just as the game's hold bends it, {@link #hold}: the stance is that hold), the more the further it reaches across
     * her (to her left) or up, a little less straight out ahead.
     */
    static double swordBend(V d){return clamp(SWORD_BEND+reaching(d),3,92);}
    static double reaching(V d){return 70*Math.max(0,-d.x)+35*Math.max(0,d.y)-10*Math.max(0,d.z)*(1-Math.abs(d.y));}
    /** The sword arm's bend where it is the game's hold (degrees, less what its line adds: so the stance is bent as the hold). */
    static final double SWORD_BEND=-Math.toDegrees(IsosangSlash.HOLD_ELBOW)-reaching(V.of(R6.poseOf(hold()).rArm).unit());
    /**
     * How the game holds her sword between swings, as a frame (MotionPlayerModel's "isosang" arm, IsosangSlash.HOLD_*:
     * the arm, its elbow's bend, the sword turned as the item's display turns it): the stance (IsosangSlash.STANCE) is it.
     */
    static R6.Frame hold(){
        MotionPose m=new MotionPose();
        m.joint[MotionPose.RIGHT_ARM]=new float[]{(float)Math.toDegrees(IsosangSlash.HOLD_PITCH),(float)Math.toDegrees(IsosangSlash.HOLD_YAW),(float)Math.toDegrees(IsosangSlash.HOLD_ROLL)};
        m.elbow[0]=(float)Math.toDegrees(IsosangSlash.HOLD_ELBOW);
        return new R6.Frame(m,IsosangSlash.DISPLAY_TILT,0,0,0,0,0,0);
    }
    /** An arm bent at its elbow: its upper piece and that one's front, its forearm and that one's front (body space). */
    record Arm(V upper,V front,V lower,V lowerFront){}
    /**
     * An arm whose hand stays on the line {@code d} from the shoulder, its elbow bent {@code th} radians with the forearm
     * folding toward {@code fold} (a unit way across {@code d}): the upper arm leans away from it.
     */
    static Arm arm(V d,V fold,double th){
        double a=ARM_UP,b=ARM_LO,r=R6.armReach(Math.toDegrees(th));
        double cos=clamp((a*a+r*r-b*b)/(2*a*r),-1,1),sin=Math.sqrt(1-cos*cos);
        V upper=d.times(cos).minus(fold.times(sin)),front=fold.times(cos).plus(d.times(sin));
        V lower=upper.times(Math.cos(th)).plus(front.times(Math.sin(th))),lowerFront=front.times(Math.cos(th)).minus(upper.times(Math.sin(th)));
        return new Arm(upper,front,lower,lowerFront);
    }
    /**
     * The sword arm's bend, {@code want} radians but never so far the forearm turns past the blade ({@code blade}
     * radians off the arm's line, toward the side it folds to): its way off the line kept within 85% of the blade's.
     */
    static double fold(double want,double blade){
        double limit=.85*blade;
        if(off(want)<=limit)return want;
        double lo=0,hi=want;
        for(int i=0;i<24;i++){double mid=(lo+hi)/2;if(off(mid)<=limit)lo=mid;else hi=mid;}
        return lo;
    }
    /** How far the forearm turns off the shoulder-to-hand line with the elbow bent {@code th} radians (the triangle's angle at the hand). */
    static double off(double th){return Math.asin(clamp(ARM_UP*Math.sin(th)/R6.armReach(Math.toDegrees(th)),-1,1));}

    /** A leg bent at its knee: its thigh and that one's front (the knee's way), and the knee's bend (radians). */
    record Leg(V upper,V front,double bend){}
    /**
     * Leg {@code i} of a frame turned by {@code q}: its hip at {@code hip} (the hit's frame), its straight way {@code w}
     * (body space), the ground under her now and lately ({@link #supports}); {@code airborne} how far both feet are off it.
     * Three legs blended: standing on the ground under her now; as the key holds it in the air (its stance as if she stood
     * on the frame's own floor, so a crouch she lands in is the crouch she comes down in, tucked the more she is off the
     * ground); and a leg the key lifts off its floor (on its line, the knee bent with the lift). It stands on the ground
     * where the leg it holds in the air would meet it (so coming down there is no reaching for it: the held leg is the
     * standing one as they meet), and lately has (so a leg springing off stays on it a moment, straight).
     */
    static Leg leg(Quaterniond q,Vector3d hip,V w,double[] supports,double airborne){
        double ground=supports[0];
        Vector3d foot=new Vector3d(hip).add(hit(q,w).mul(LEG));
        double hx=foot.x-hip.x,hz=foot.z-hip.z,reach=Math.max(Math.hypot(hx,hz),.05);
        Vector3d forward=q.transform(new Vector3d(0,0,1));forward.y=0;
        if(forward.lengthSquared()<1e-8)forward.set(0,0,1);else forward.normalize();
        double ahead=(hx*forward.x+hz*forward.z)/reach;
        // the knee over the foot: turned out the way the foot is out to her side (not for a foot ahead or behind her)
        double lateral=(hx*forward.z-hz*forward.x)/reach;
        V side=body(q,new Vector3d(forward.z*lateral,0,-forward.x*lateral));
        Leg stood=stand(q,hip,foot,hx,hz,ahead,side,ground);
        Leg held=ground==0?stood:stand(q,hip,foot,hx,hz,ahead,side,0);
        // in the air: the foot on the line the key put the leg along, the knee bent the more it is lifted (the thigh over
        // the line, the shin under it: a raised knee, a leg swung back with its shin up)
        double lifted=Math.toDegrees(Math.acos(clamp(-w.y,-1,1)));
        double kick=lerp(RAISE_BACK*smooth(lifted,15,85),RAISE_FRONT*smooth(lifted,10,80),smooth(w.z,-.3,.4));
        Leg raised=reach(w.times(R6.legReach(kick)),V.of(R6.swing(w.dir())).off(w).unit());
        // (always re-solved, the tuck or none, so a blended leg is the same leg as the tuck fades to nothing)
        Leg air=tucked(mixed(raised,held,1-smooth(foot.y,.035,.2)),TUCK*airborne);
        // standing where the held foot is on the ground, now or lately (and not at all once the hip is higher than a leg reaches)
        double airFoot=hip.y+hit(q,foot(air)).y,planted=0;
        for(double s:supports)planted+=(1-smooth(airFoot-s,0,MEET))*(1-smooth(hip.y-s-.995*LEG,0,.06))/supports.length;
        Leg leg=mixed(air,stood,planted);
        // no foot into the ground: one that would go there reaches just to it, the knee its way (every leg solved again to
        // where its foot is, so the blends are true legs and the ground's edge leaves no step)
        Vector3d end=new Vector3d(hip).add(hit(q,foot(leg)));
        if(end.y<ground&&hip.y-ground>.05)end.y=ground;
        return reach(body(q,end.sub(hip)),leg.front);
    }
    /**
     * A leg standing on the ground {@code ground} blocks up: its foot drawn in under the hip along the ground from where
     * the key put it ({@code foot}, {@code hx} and {@code hz} out from the hip) as the body comes down, the knee bent to
     * reach it, over the foot; straight the way the key put it once the ground is out of its reach.
     */
    static Leg stand(Quaterniond q,Vector3d hip,Vector3d foot,double hx,double hz,double ahead,V side,double ground){
        double hipHeight=hip.y-ground;
        double low=clamp((LEG-hipHeight)/(LEG*.5),0,1);
        double draw=1-(1-lerp(DRAW_BACK,DRAW_FRONT,smooth(ahead,-.6,.15)))*smooth(low,0,1);
        Vector3d target=new Vector3d(hip.x+hx*draw,ground,hip.z+hz*draw);
        target.lerp(foot,smooth(hipHeight,LEG-.05,LEG+.1));
        V to=body(q,target.sub(hip,new Vector3d()));
        V dn=to.unit();
        return reach(to,V.of(R6.swing(dn.dir())).off(dn).unit().plus(side.off(dn).times(KNEE_OUT)));
    }
    /**
     * {@link #stand} turned round, on the frame's own floor (2.117.0, X Chara's plain swings: feet that stay where they are
     * put while the body moves over them): the straight way (body space) of leg {@code i} (0 right, 1 left) of pose
     * {@code p} whose foot, drawn in under the hip as a crouch draws it, stands at ({@code right}, {@code ahead}) of the
     * hit's frame (blocks from where its user stands). A point further off than the leg reaches from that hip is reached
     * for as far as the leg goes (the foot short of it, on the way to it); with the hip as high as the leg is long the
     * leg hangs straight.
     */
    public static R6.Dir standingAt(R6.Pose p,int i,double right,double ahead){
        Quaterniond q=new Quaterniond().rotationAxis(Math.toRadians(p.yaw),0,1,0).rotateAxis(Math.toRadians(p.pitch),1,0,0).rotateAxis(Math.toRadians(p.roll),0,0,1);
        double xLeft=i==0?-IsosangSlash.HIP_SIDE:IsosangSlash.HIP_SIDE;
        Vector3d hip=q.transform(new Vector3d(xLeft,-IsosangSlash.HIP_DOWN,0)).add(-p.rt,p.up+KagenoClips.PIVOT,p.fw);
        double hx=-right-hip.x,hz=ahead-hip.z,far=Math.hypot(hx,hz),height=hip.y;
        double most=Math.sqrt(Math.max(0,LEG*LEG-height*height));
        if(far<1e-6||most<1e-6)return body(q,new Vector3d(0,-1,0)).dir().norm();
        Vector3d forward=q.transform(new Vector3d(0,0,1));forward.y=0;
        if(forward.lengthSquared()<1e-8)forward.set(0,0,1);else forward.normalize();
        // (as stand draws it: by how low the hip is and whether the foot is ahead or behind, less as the hip nears a leg's length)
        double low=clamp((LEG-height)/(LEG*.5),0,1);
        double draw=1-(1-lerp(DRAW_BACK,DRAW_FRONT,smooth((hx*forward.x+hz*forward.z)/Math.max(far,.05),-.6,.15)))*smooth(low,0,1);
        double straight=smooth(height,LEG-.05,LEG+.1);draw=draw*(1-straight)+straight;
        double out=Math.min(far/draw,most);
        return body(q,new Vector3d(hx/far*out,-Math.sqrt(Math.max(0,LEG*LEG-out*out)),hz/far*out)).dir().norm();
    }
    /** {@code l} with its knee bent {@code more} degrees further, its foot on the same line from the hip (tucked). */
    static Leg tucked(Leg l,double more){
        return reach(foot(l).unit().times(R6.legReach(Math.min(150,Math.toDegrees(l.bend)+more))),l.front);
    }
    /** Where leg {@code l}'s foot is from its hip (body space). */
    static V foot(Leg l){
        V shin=l.upper.times(Math.cos(l.bend)).minus(l.front.times(Math.sin(l.bend)));
        return l.upper.times(LEG_UP).plus(shin.times(LEG_LO));
    }
    /** How near the held foot comes over the ground before it stands on it (blocks: a centimetre). */
    static final double MEET=.01;
    static Leg mixed(Leg a,Leg b,double t){return t>=1?b:t<=0?a:mix(a,b,t);}
    /** A leg from its hip to {@code to} (body space), its knee toward {@code knee}: as long as it must be (it can be no longer than straight). */
    static Leg reach(V to,V knee){
        double a=LEG_UP,b=LEG_LO,d=clamp(to.len(),.08,LEG);
        V dn=to.unit(),side=knee.off(dn);
        side=side.len()<1e-6?V.of(R6.swing(dn.dir())).off(dn).unit():side.unit();
        double cos=clamp((a*a+d*d-b*b)/(2*a*d),-1,1),sin=Math.sqrt(1-cos*cos);
        V upper=dn.times(cos).plus(side.times(sin));
        double bend=Math.PI-Math.acos(clamp((a*a+b*b-d*d)/(2*a*b),-1,1));
        return new Leg(upper,side.times(cos).minus(dn.times(sin)),bend);
    }
    /** Two legs' turns slerped (the thigh and its front together) and their bends lerped: {@code t} of the way from {@code a} to {@code b}. */
    static Leg mix(Leg a,Leg b,double t){
        Quaternionf qa=R6.limbTurn(a.upper.dir(),a.front.dir()),qb=R6.limbTurn(b.upper.dir(),b.front.dir());
        Quaternionf qm=new Quaternionf(qa).slerp(qb,(float)t).normalize();
        // the model's +y is down the leg, its -z the front; body space is the model's negation
        Vector3f down=qm.transform(new Vector3f(0,1,0)),front=qm.transform(new Vector3f(0,0,-1));
        return new Leg(new V(-down.x,-down.y,-down.z),new V(-front.x,-front.y,-front.z),a.bend+(b.bend-a.bend)*t);
    }

    /** The torso's turn in the hit's frame (KagenoClips: the facing along +z, the character's left +x). */
    static Quaterniond torso(R6.Frame f){
        return new Quaterniond().rotationAxis(Math.toRadians(f.yaw()),0,1,0).rotateAxis(Math.toRadians(f.pitch()),1,0,0).rotateAxis(Math.toRadians(f.roll()),0,0,1);
    }
    /** Hip {@code i} (0 right, 1 left) of frame {@code f} in the hit's frame (IsosangSlash.planted's). */
    static Vector3d hip(Quaterniond q,R6.Frame f,int i){
        double xLeft=i==0?-IsosangSlash.HIP_SIDE:IsosangSlash.HIP_SIDE;
        return q.transform(new Vector3d(xLeft,-IsosangSlash.HIP_DOWN,0)).add(-f.rt(),f.up()+KagenoClips.PIVOT,f.fw());
    }
    /** A body-space way in the hit's frame. */
    static Vector3d hit(Quaterniond q,V v){return q.transform(new Vector3d(-v.x,v.y,v.z));}
    /** A way in the hit's frame in body space. */
    static V body(Quaterniond q,Vector3d h){Vector3d t=q.transformInverse(new Vector3d(h));return new V(-t.x,t.y,t.z);}
    /** {@code v} turned {@code angle} radians about the unit axis {@code k} ({@code v} across it). */
    static V turn(V v,V k,double angle){return v.times(Math.cos(angle)).plus(k.cross(v).times(Math.sin(angle)));}

    /** A body-space vector: right, up, forward (R6.Dir's). */
    record V(double x,double y,double z){
        static V of(R6.Dir d){return new V(d.rt(),d.up(),d.fw());}
        R6.Dir dir(){return new R6.Dir(x,y,z);}
        V plus(V o){return new V(x+o.x,y+o.y,z+o.z);}
        V minus(V o){return new V(x-o.x,y-o.y,z-o.z);}
        V times(double s){return new V(x*s,y*s,z*s);}
        double dot(V o){return x*o.x+y*o.y+z*o.z;}
        V cross(V o){return new V(y*o.z-z*o.y,z*o.x-x*o.z,x*o.y-y*o.x);}
        double len(){return Math.sqrt(dot(this));}
        V unit(){double l=len();return l<1e-12?new V(0,-1,0):times(1/l);}
        /** The part of this across the unit axis {@code k}. */
        V off(V k){return minus(k.times(dot(k)));}
    }

    static double clamp(double v,double lo,double hi){return v<lo?lo:v>hi?hi:v;}
    static double lerp(double a,double b,double t){return a+(b-a)*t;}
    static double smooth(double v,double from,double to){double u=clamp((v-from)/(to-from),0,1);return u*u*(3-2*u);}
}
