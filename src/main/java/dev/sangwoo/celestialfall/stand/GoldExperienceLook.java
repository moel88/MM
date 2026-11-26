package dev.sangwoo.celestialfall.stand;

/**
 * How Gold Experience looks in a frame as a stand beside its user (2.146.0; the figure "/botand gold" stands has none
 * and goes by its stance): how much of each stance it holds, how far it is out of its user (0 inside them, 1 at its
 * place), how solid and how large it is, how strongly its golden aura wraps it and its user, its colour (its
 * afterimages are tinted), how dark its shadow is, and how it leans as it follows its user (degrees: forward, and to
 * the side it goes).
 */
public final class GoldExperienceLook {
    public final float[] weights=new float[GoldExperienceStance.NAMES.size()];
    public float out=1,alpha=1,scale=1,glow,red=1,green=1,blue=1,shadow=1,lean,roll;
    public GoldExperienceLook only(int stance){java.util.Arrays.fill(weights,0);weights[stance]=1;return this;}
    public GoldExperienceLook set(GoldExperienceLook o){
        System.arraycopy(o.weights,0,weights,0,weights.length);
        out=o.out;alpha=o.alpha;scale=o.scale;glow=o.glow;red=o.red;green=o.green;blue=o.blue;shadow=o.shadow;lean=o.lean;roll=o.roll;
        return this;
    }
    /** This look made {@code k} of the way from {@code a} to {@code b} (either may be this one). */
    public GoldExperienceLook mix(GoldExperienceLook a,GoldExperienceLook b,float k){
        for(int i=0;i<weights.length;i++)weights[i]=a.weights[i]+(b.weights[i]-a.weights[i])*k;
        out=a.out+(b.out-a.out)*k;alpha=a.alpha+(b.alpha-a.alpha)*k;scale=a.scale+(b.scale-a.scale)*k;glow=a.glow+(b.glow-a.glow)*k;
        red=a.red+(b.red-a.red)*k;green=a.green+(b.green-a.green)*k;blue=a.blue+(b.blue-a.blue)*k;shadow=a.shadow+(b.shadow-a.shadow)*k;
        lean=a.lean+(b.lean-a.lean)*k;roll=a.roll+(b.roll-a.roll)*k;
        return this;
    }
}
