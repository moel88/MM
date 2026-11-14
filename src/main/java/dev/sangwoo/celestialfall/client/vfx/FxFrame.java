package dev.sangwoo.celestialfall.client.vfx;

import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What the effects ask of the effect layer for one frame, collected from every layered cast: coloured point
 * lights on the world, ground decals, one light-shaft source, the impact frame, the chromatic split and (2.132.1) the
 * screen inside X Chara's rain of knives.
 * Pure Java (no GL), so the selection is testable.
 */
public final class FxFrame {
    public static final int MAX_LIGHTS=12,MAX_DECALS=10;
    /** A point light: colour already multiplied by its intensity (1 = a bright lamp), radius in blocks. */
    public record Light(Vec3d pos,float r,float g,float b,double radius){
        double peak(){return Math.max(r,Math.max(g,b));}
    }
    /**
     * A ground decal centred on {@code center} along the horizontal unit {@code along}: {@code halfLength} by
     * {@code halfWidth} blocks. {@code heat} 1 glows white-pink and cools to a dark burn at 0.
     */
    public record Decal(Vec3d center,Vec3d along,double halfLength,double halfWidth,float heat,float alpha,float seed){}
    private final List<Light> lights=new ArrayList<>();
    private final List<Decal> decals=new ArrayList<>();
    private Vec3d shaft;private int shaftRgb;private float shaftStrength;
    private float impact,impactMode,chroma,afterglow=1;
    /**
     * The impact frame's colours: first {@code shapes} on {@code deep} (black silhouettes), then {@code shade}
     * silhouettes and {@code hue} shapes on {@code pale}.
     */
    public record ImpactLook(int deep,int shapes,int pale,int shade,int hue){}
    /** Kageno's violet (the default). */
    public static final ImpactLook VIOLET=new ImpactLook(0x1A0038,0xFFF2FF,0xFCF2FF,0x0D001A,0x4D0585);
    private ImpactLook impactLook=VIOLET;

    public void light(Vec3d pos,int rgb,double radius,float intensity){
        if(!(intensity>0)||!(radius>0)||pos==null)return;
        lights.add(new Light(pos,((rgb>>16)&255)/255f*intensity,((rgb>>8)&255)/255f*intensity,(rgb&255)/255f*intensity,radius));
    }
    public void decal(Vec3d center,Vec3d along,double halfLength,double halfWidth,float heat,float alpha,float seed){
        if(!(alpha>0)||!(halfLength>0)||!(halfWidth>0))return;
        Vec3d flat=new Vec3d(along.x,0,along.z);
        if(flat.lengthSquared()<1e-8)return;
        decals.add(new Decal(center,flat.normalize(),halfLength,halfWidth,Math.max(0,Math.min(1,heat)),Math.min(1,alpha),seed));
    }
    /** The brightest shaft source wins. */
    public void shaft(Vec3d pos,int rgb,float strength){
        if(strength>shaftStrength&&pos!=null){shaft=pos;shaftRgb=rgb;shaftStrength=strength;}
    }
    /** Impact frame 0..1; {@code mode} 0 is white shapes on black, 1 dark shapes on white. The strongest wins. */
    public void impact(float amount,float mode){impact(amount,mode,VIOLET);}
    /** An impact frame in its own colours (2.88.0, Lee So-sang's G: deep amber and white gold in place of the violet). */
    public void impact(float amount,float mode,ImpactLook look){
        if(amount>impact){impact=Math.min(1,amount);impactMode=mode;impactLook=look;}
    }
    /** Chromatic split in screen fractions per unit of distance from the focus. */
    public void chroma(float amount){if(amount>chroma)chroma=amount;}
    private float rain,rainBeat,rainFlash;
    /**
     * (2.132.1) The screen inside X Chara's rain of knives, 0..1 (the strongest wins): sunk into violet and dark at its
     * edges, torn in sliding slices, streaked with light falling down it, scanned; {@code beat} 0..1 its pulse as the
     * knives land (harder tearing, a brighter violet), {@code flash} 0..1 a white-violet flash of it (the last volley).
     */
    public void rain(float amount,float beat,float flash){
        if(amount>rain){rain=Math.min(1,amount);rainBeat=Math.max(0,Math.min(1,beat));rainFlash=Math.max(0,Math.min(1,flash));}
    }
    /**
     * How long the afterglow lasts this frame, as a share of the usual (0.01..1, the shortest asked wins): something
     * flying fast would otherwise leave fading copies of itself behind it, side by side like extra strokes (2.82.6:
     * down to .01, for the edge of Lee So-sang's ink sweeping in at a hundred blocks a second, whose copies showed even
     * at .05 where frames come close together in game time, as in a slowed shot).
     */
    public void afterglow(float share){if(share>=0&&share<afterglow)afterglow=Math.max(.01f,share);}

    /** The lights that reach the camera's surroundings most, brightest first, at most {@link #MAX_LIGHTS}. */
    public List<Light> strongest(Vec3d camera){
        return lights.stream().filter(l->l.pos.squaredDistanceTo(camera)<Math.pow(l.radius+96,2))
            .sorted(Comparator.comparingDouble((Light l)->-score(l,camera))).limit(MAX_LIGHTS).toList();
    }
    static double score(Light l,Vec3d camera){
        double d2=l.pos.squaredDistanceTo(camera);
        return l.peak()*l.radius*l.radius/(1+d2*.02);
    }
    /** The decals nearest the camera, at most {@link #MAX_DECALS}. */
    public List<Decal> nearest(Vec3d camera){
        return decals.stream().sorted(Comparator.comparingDouble((Decal d)->d.center.squaredDistanceTo(camera))).limit(MAX_DECALS).toList();
    }
    public boolean empty(){return lights.isEmpty()&&decals.isEmpty()&&shaftStrength<=0&&impact<=0&&chroma<=0&&rain<=0;}
    public Vec3d shaft(){return shaft;}
    public int shaftRgb(){return shaftRgb;}
    public float shaftStrength(){return shaftStrength;}
    public float impact(){return impact;}
    public float impactMode(){return impactMode;}
    public ImpactLook impactLook(){return impactLook;}
    public float chroma(){return chroma;}
    public float rain(){return rain;}
    public float rainBeat(){return rainBeat;}
    public float rainFlash(){return rainFlash;}
    public float afterglow(){return afterglow;}
    public int lightCount(){return lights.size();}
    public int decalCount(){return decals.size();}
}
