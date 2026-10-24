package dev.sangwoo.celestialfall.stand;

import java.util.Arrays;

/**
 * What Star Platinum's model draws in one frame (2.51.0): how much of each stance ({@link StarPlatinumStance}, the
 * weights add up to 1), the wind of the art's stance and a gust through the hair, scarf and cloth (0 to 1), how far it
 * has come out of its user (0 inside the user's body, 1 at its place beside them), how solid it is, its size, and as it
 * follows its user its lean (degrees forward), its roll (degrees, to its right) and its speed (blocks a second). The
 * colour multiplies its texture (an afterimage is drawn bluer), and the shadow is how dark its shadow is (none for an
 * afterimage) at full solidity.
 */
public final class StarPlatinumLook {
    public final float[] weights=new float[StarPlatinumStance.NAMES.size()];
    public float wind,gust,out=1,alpha=1,scale=1,lean,roll,speed;
    public float red=1,green=1,blue=1,shadow=1;
    public StarPlatinumLook(){weights[StarPlatinumStance.IDLE]=1;}
    /** Wholly in one stance, standing still, nothing else on. */
    public StarPlatinumLook only(int stance){
        Arrays.fill(weights,0);weights[stance]=1;wind=stance==StarPlatinumStance.HERO?1:0;gust=lean=roll=speed=0;out=alpha=scale=1;red=green=blue=shadow=1;return this;
    }
    public StarPlatinumLook set(StarPlatinumLook o){
        System.arraycopy(o.weights,0,weights,0,weights.length);
        wind=o.wind;gust=o.gust;out=o.out;alpha=o.alpha;scale=o.scale;lean=o.lean;roll=o.roll;speed=o.speed;red=o.red;green=o.green;blue=o.blue;shadow=o.shadow;return this;
    }
    /** This look becomes {@code a} taken {@code k} of the way (0 to 1) to {@code b}; either may be this one. */
    public StarPlatinumLook mix(StarPlatinumLook a,StarPlatinumLook b,float k){
        for(int i=0;i<weights.length;i++)weights[i]=a.weights[i]+(b.weights[i]-a.weights[i])*k;
        wind=a.wind+(b.wind-a.wind)*k;gust=a.gust+(b.gust-a.gust)*k;out=a.out+(b.out-a.out)*k;alpha=a.alpha+(b.alpha-a.alpha)*k;
        scale=a.scale+(b.scale-a.scale)*k;lean=a.lean+(b.lean-a.lean)*k;roll=a.roll+(b.roll-a.roll)*k;speed=a.speed+(b.speed-a.speed)*k;
        red=a.red+(b.red-a.red)*k;green=a.green+(b.green-a.green)*k;blue=a.blue+(b.blue-a.blue)*k;shadow=a.shadow+(b.shadow-a.shadow)*k;
        return this;
    }
}
