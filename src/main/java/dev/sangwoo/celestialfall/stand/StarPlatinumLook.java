package dev.sangwoo.celestialfall.stand;

import java.util.Arrays;

/**
 * What Star Platinum's model draws in one frame (2.51.0): how much of each stance ({@link StarPlatinumStance}, the
 * weights add up to 1), the wind of the art's stance and a gust through the hair, scarf and cloth (0 to 1), how far it
 * has come out of its user (0 inside the user's body, 1 at its place beside them), how solid it is, its size, and as it
 * follows its user its lean (degrees forward), its roll (degrees, to its right) and its speed (blocks a second). The
 * colour multiplies its texture (an afterimage is drawn bluer), the shadow is how dark its shadow is (none for an
 * afterimage) at full solidity, and the glow how strongly its aura shines round it (2.52.0: 1 for a stand out at rest,
 * more as it bursts into its pose, none for the figure or an afterimage). A look can be taking over from another pose
 * (2.56.0: a punch from the one before it): {@code under} is that pose's stances (adding up to 1 too) and the body is
 * drawn {@code over} of the way from that pose to this one's stances, turn by turn (1, the default: none under). That
 * pose can itself be turning from one under it (2.57.0: a punch stopped short turns from where it was back to the
 * guard, and it may have been taking over from the punch before; the rush and a punch take over from each other):
 * {@code under2} and {@code over2}. An afterimage of the rush's flying arms (2.57.0) is drawn {@code armsOnly}: its
 * arms and fists, nothing else, each arm as strongly as {@code showL} and {@code showR} say (2.58.0: the arm that
 * strikes out shows, the one drawn back fades). Each arm is drawn {@code armL} and {@code armR} times its size, from
 * its shoulder (2.58.0: bigger through the rush, and bigger still as it stretches out to its fist). Its right hand's
 * first two fingers (2.60.0, the Star Finger) show held out straight as far as {@code fingerOpen} says (0 none: a
 * fist), and stretch {@code fingerExt} of the way (0 to 1) from their own length out to {@code fingerTip}, a point in
 * the world (as far as {@code hasTip}).
 */
public final class StarPlatinumLook {
    public final float[] weights=new float[StarPlatinumStance.NAMES.size()],under=new float[StarPlatinumStance.NAMES.size()],under2=new float[StarPlatinumStance.NAMES.size()];
    public float wind,gust,out=1,alpha=1,scale=1,lean,roll,speed,over=1,over2=1;
    public float red=1,green=1,blue=1,shadow=1,glow;
    public boolean armsOnly;
    public float armL=1,armR=1,showL=1,showR=1;
    public float fingerOpen,fingerExt;
    public double fingerX,fingerY,fingerZ;public boolean hasTip;
    public StarPlatinumLook(){weights[StarPlatinumStance.IDLE]=1;}
    /** Wholly in one stance, standing still, nothing else on. */
    public StarPlatinumLook only(int stance){
        Arrays.fill(weights,0);weights[stance]=1;Arrays.fill(under,0);Arrays.fill(under2,0);over=over2=1;wind=stance==StarPlatinumStance.HERO?1:0;gust=lean=roll=speed=glow=0;out=alpha=scale=1;red=green=blue=shadow=1;armsOnly=false;armL=armR=showL=showR=1;fingerOpen=fingerExt=0;hasTip=false;return this;
    }
    public StarPlatinumLook set(StarPlatinumLook o){
        System.arraycopy(o.weights,0,weights,0,weights.length);System.arraycopy(o.under,0,under,0,under.length);System.arraycopy(o.under2,0,under2,0,under2.length);over=o.over;over2=o.over2;
        wind=o.wind;gust=o.gust;out=o.out;alpha=o.alpha;scale=o.scale;lean=o.lean;roll=o.roll;speed=o.speed;red=o.red;green=o.green;blue=o.blue;shadow=o.shadow;glow=o.glow;armsOnly=o.armsOnly;armL=o.armL;armR=o.armR;showL=o.showL;showR=o.showR;
        fingerOpen=o.fingerOpen;fingerExt=o.fingerExt;fingerX=o.fingerX;fingerY=o.fingerY;fingerZ=o.fingerZ;hasTip=o.hasTip;return this;
    }
    /** How much of stance {@code i} the look has, its poses under and its own taken together. */
    public float share(int i){return over>=1?weights[i]:weights[i]*over+below(i)*(1-over);}
    /** How much of stance {@code i} the pose under has, with the one under it. */
    float below(int i){return over2>=1?under[i]:under[i]*over2+under2[i]*(1-over2);}
    /** The poses under taken into the look's own stances (weighed together, not turned from one pose to the other). */
    public StarPlatinumLook flatten(){
        if(over<1){for(int i=0;i<weights.length;i++)weights[i]=share(i);}
        Arrays.fill(under,0);Arrays.fill(under2,0);over=over2=1;
        return this;
    }
    /** This look becomes {@code a} taken {@code k} of the way (0 to 1) to {@code b}; either may be this one (any pose under either is taken into its stances). */
    public StarPlatinumLook mix(StarPlatinumLook a,StarPlatinumLook b,float k){
        for(int i=0;i<weights.length;i++){float wa=a.share(i),wb=b.share(i);weights[i]=wa+(wb-wa)*k;}
        Arrays.fill(under,0);Arrays.fill(under2,0);over=over2=1;
        wind=a.wind+(b.wind-a.wind)*k;gust=a.gust+(b.gust-a.gust)*k;out=a.out+(b.out-a.out)*k;alpha=a.alpha+(b.alpha-a.alpha)*k;
        scale=a.scale+(b.scale-a.scale)*k;lean=a.lean+(b.lean-a.lean)*k;roll=a.roll+(b.roll-a.roll)*k;speed=a.speed+(b.speed-a.speed)*k;
        red=a.red+(b.red-a.red)*k;green=a.green+(b.green-a.green)*k;blue=a.blue+(b.blue-a.blue)*k;shadow=a.shadow+(b.shadow-a.shadow)*k;glow=a.glow+(b.glow-a.glow)*k;
        armL=a.armL+(b.armL-a.armL)*k;armR=a.armR+(b.armR-a.armR)*k;showL=a.showL+(b.showL-a.showL)*k;showR=a.showR+(b.showR-a.showR)*k;
        // the fingers (2.60.0): as open and stretched as the one they come from is, out to its point
        StarPlatinumLook f=a.fingerOpen>=b.fingerOpen?a:b;
        fingerOpen=a.fingerOpen+(b.fingerOpen-a.fingerOpen)*k;fingerExt=a.fingerExt+(b.fingerExt-a.fingerExt)*k;
        fingerX=f.fingerX;fingerY=f.fingerY;fingerZ=f.fingerZ;hasTip=f.hasTip;
        return this;
    }
}
