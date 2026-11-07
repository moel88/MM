package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import net.minecraft.util.math.Vec3d;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.*;
import static dev.sangwoo.celestialfall.isosang.IsosangRules.*;

/**
 * T's three slashes made bigger and grander (2.100.1, user request: "T스킬 3참격을 조금만 더 키우고 이펙트를 더 크고 멋있게
 * 바꿔줘 3참격에 신경을 엄청 써줘 멋있게"). The blades themselves are a fifth bigger (SwordLunge.LENGTH, WIDTH); round
 * each one:
 * <ul>
 * <li>its sword's aura (검기): three hard-edged lenses of its own outline, ever wider (azure, royal blue, indigo), swelling
 * in the snap as it is cut and settling, gone as it cools ({@link #aura});</li>
 * <li>lilac lightning crawling round it while it is bright, with short branches flicking off it (over it: {@link #lightning});</li>
 * <li>the cutting edge at its head as it is drawn on (a white-hot streak and a star) and a flare of white heat down its
 * heart as it is cut ({@link #edge});</li>
 * <li>a bigger burst where it passes the foe ({@link #burst}), a ring of light running out on the ground under the foe
 * ({@link #ground}), a stronger light and jolt;</li>
 * <li>the last one, the finisher, the biggest of all, with a deep blue impact frame (as Y's last swords': every other T
 * in a row, {@link #finishes}).</li>
 * </ul>
 * Their size, place, randomness and timing otherwise as the user tuned them (2.96.3 to 2.96.6); white only in their
 * hearts, at their heads and in the snap (blue the colour of them all); no extra lines like slashes beside them.
 */
final class LungeSlashes {
    private LungeSlashes(){}

    // ───────────────────────────── their shape ─────────────────────────────

    /**
     * The share of a slash's width {@code x} along it (0 its far tip, 1 its near tip): its band's outline in the shader
     * (kageno_flame style 7), sharp at both tips, fullest some 55% along.
     */
    static double profile(double x){
        double c=Math.max(0,Math.min(1,x));
        return Math.max(Math.pow(Math.max(Math.sin(Math.PI*Math.pow(c,1.16)),0),.62),.02);
    }
    /** Across slash {@code b} at {@code p}, facing {@code camera} (unit), or null where it is end-on. */
    static Vec3d side(SwordLunge.Blade b,Vec3d camera,Vec3d p){
        Vec3d s=b.way().crossProduct(camera.subtract(p));return s.lengthSquared()<1e-8?null:s.normalize();
    }
    /** A camera-facing lens of slash {@code b}'s own outline, {@code width} at its fullest, from its far tip as far as {@code head}. */
    static void lens(EffectMesh m,SwordLunge.Blade b,double head,double width,int rgb,float alpha){
        if(alpha<=.004f||head<=.01||width<=0)return;
        Vec3d pa=null,pc=null;
        for(int i=0;i<=LENS;i++){
            double u=head*i/LENS;Vec3d p=b.point(u),s=side(b,m.camera(),p);if(s==null)continue;
            double w=profile(u)*width*.5;Vec3d a=p.add(s.multiply(w)),c=p.subtract(s.multiply(w));
            if(pa!=null)m.quad(pa,a,c,pc,rgb,alpha);
            pa=a;pc=c;
        }
    }
    static final int LENS=16;

    // ───────────────────────────── its aura (the light pass, under it) and its lightning (over it) ─────────────────────────────

    /**
     * The snap as slash {@code j} is cut, {@code t} seconds into T: in over a frame or two as its head runs on, then out
     * over a tenth of a second.
     */
    static float snap(int j,float t){return FxLib.kick(t,lungeSlash(j)+SNAP_AT,SNAP_TAU);}
    static final float SNAP_AT=.02f,SNAP_TAU=.08f;
    /**
     * Its aura (검기): three hard-edged lenses of its outline AURA times as wide as it is (azure, royal blue, indigo, ever
     * fainter: its own hard-edged bands carried on outward), a third wider in the snap as it is cut, settling to its
     * size while it is bright and gone as it cools (SwordLunge.lit).
     */
    static void aura(EffectMesh m,SwordLunge.Blade b,int j,float t,float k){
        float lit=SwordLunge.lit(j,t),snap=snap(j,t);if(k*Math.max(lit,snap)<=.01f)return;
        double head=SwordLunge.drawn(j,t),swell=1+SWELL*snap,w=b.width();
        for(int i=0;i<AURA.length;i++){
            float a=k*AURA_ALPHA[i]*Math.min(1.2f,AURA_LIT[i]*lit+AURA_SNAP[i]*snap);
            lens(m,b,head,w*AURA[i]*swell,AURA_RGB[i],a);
        }
    }
    static final double[] AURA={2.0,3.4,5.6};
    static final int[] AURA_RGB={AZURE,BLUE,0x4a46ff};
    static final float[] AURA_ALPHA={.3f,.18f,.11f},AURA_LIT={.55f,.5f,.4f},AURA_SNAP={.6f,.75f,.9f};
    static final double SWELL=.35;
    /**
     * Lilac lightning crawling along slash {@code j} while it is bright (her sword's lightning; over the ink, the band of
     * the slash under it): two bolts, one on each side of its middle (where it passes before the foe and crosses the
     * others: kept clear), their ends just outside its edges ({@link #off}) so they wrap round it,
     * their ends hopping every fifteenth of a second and their jags every thirtieth (SwordFormation.bolt), with a short
     * branch now and then flicking off it; only along what is drawn, gone as its light goes.
     */
    static void lightning(Fx c,EffectMesh m,SwordLunge.Blade b,int j,float t,float k){
        float at=lungeSlash(j),e=k*Ease.smooth(t,at,at+.03f)*(1-Ease.smooth(t,at+BOLT0,at+BOLT1));if(e<=.01f)return;
        double head=SwordLunge.drawn(j,t);int q=(int)Math.floor((t-at)*15),flick=(int)Math.floor(t*24);
        for(int s=0;s<2;s++){
            int seed=j*977+s*131+q*17+11;
            double[] span=boltSpan(j,s,q);double u0=span[0],u1=Math.min(span[1],head);if(u1-u0<.04)continue;
            Vec3d p0=b.point(u0),p1=b.point(u1),s0=side(b,c.camera,p0),s1=side(b,c.camera,p1);if(s0==null||s1==null)continue;
            Vec3d a=p0.add(s0.multiply(off(seed+2)*profile(u0)*b.width()*.5)),z=p1.add(s1.multiply(off(seed+3)*profile(u1)*b.width()*.5));
            float f=e*(.65f+.35f*(float)Ease.hash(seed+4+flick*7));
            SwordFormation.bolt(m,a,z,seed,t,BOLT_JAG,BOLT_WIDTH,WHITE,LILAC,f);
            if(Ease.hash(seed+5)<.6){
                double ub=(u0+u1)*.5;Vec3d pb=b.point(ub),sb=side(b,c.camera,pb);if(sb==null)continue;
                double sg=Ease.hash(seed+6)<.5?-1:1;
                Vec3d end=pb.add(sb.multiply(sg*(.45+.4*Ease.hash(seed+7)))).add(b.way().multiply((Ease.hash(seed+8)-.5)*.5));
                SwordFormation.bolt(m,pb,end,seed+9,t,BOLT_JAG*.55,BOLT_WIDTH*.75,WHITE,LILAC,.75f*f);
            }
        }
    }
    /**
     * Where bolt {@code s} of slash {@code j} runs over its {@code q}-th fifteenth of a second (its shares of the way from
     * the far tip): on its own side of the middle, [.05, .42] or [.58, .95], a sixth to a third of the slash long.
     */
    static double[] boltSpan(int j,int s,int q){
        int seed=j*977+s*131+q*17+11;double lo=s==0?.05:.58;
        double u0=lo+.12*Ease.hash(seed),u1=Math.min(lo+.37,u0+.16+.2*Ease.hash(seed+1));
        return new double[]{u0,u1};
    }
    /**
     * How far off its line a bolt's end is (a share of its half width there, either side): just outside its edge, so the
     * bolts wrap round it rather than hide under its band (the first preview: under it, they were not seen).
     */
    static double off(int seed){double h=Ease.hash(seed);return (h<.5?-1:1)*(OFF0+(OFF1-OFF0)*Ease.hash(seed+101));}
    static final double OFF0=.75,OFF1=1.7;
    static final float BOLT0=.12f,BOLT1=.26f;
    static final double BOLT_JAG=.2,BOLT_WIDTH=.06;

    // ───────────────────────────── over it ─────────────────────────────

    /**
     * Over the ink: the cutting edge at its head as it is drawn on (a white-hot streak behind its head, a star, a glow),
     * and a flare of white heat down its heart in the snap as it is cut, a few frames (its own white heart widened, not
     * the whole of it white).
     */
    static void edge(Fx c,EffectMesh m,SwordLunge.Blade b,int j,float t,float k){
        float at=lungeSlash(j);double head=SwordLunge.drawn(j,t);
        float draw=k*Ease.smooth(t,at-.01f,at)*(1-Ease.smooth(t,at+SwordLunge.DRAW,at+SwordLunge.DRAW+.05f));
        if(draw>.01f){
            Vec3d p=b.point(head);
            m.needle(b.point(Math.max(0,head-STREAK)),p,b.width()*.7,.85,WHITE,.55f*draw);
            m.softDisc(p,.7,AZURE,.35f*draw);m.softDisc(p,.3,WHITE,.6f*draw);glint8(m,p,.6,WHITE,.8f*draw);
        }
        float flare=k*FxLib.kick(t,at+SNAP_AT,FLARE_TAU);
        if(flare>.01f){lens(m,b,head,b.width()*.42,WHITE,.85f*flare);lens(m,b,head,b.width()*.9,PALE,.3f*flare);}
    }
    /** How far behind its head the white-hot streak reaches (a share of its length), and how quickly its heart's flare goes (seconds). */
    static final double STREAK=.22;
    static final float FLARE_TAU=.045f;

    // ───────────────────────────── on the foe and the ground ─────────────────────────────

    /** How big slash {@code j}'s burst is where it passes the foe (IsosangEmpower.burst's size: 2.96.2 to 2.100.0's .35), the last the biggest. */
    static double burst(int j){return j==LUNGE_SLASHES-1?BURST_LAST:BURST;}
    static final double BURST=.55,BURST_LAST=.85;
    /** A ring of light running out on the ground under the foe as slash {@code j} cuts (a thin white one inside it), the last the widest. */
    static void ground(EffectMesh m,Vec3d foe,int j,float t,float k){
        float at=lungeSlash(j);if(t<at-.01f||t>at+RING_LIFE)return;
        Vec3d g=foe.subtract(0,SwordLunge.FOE_MID-.05,0);double r=ring(j);
        float a=k*Ease.smooth(t,at-.01f,at+.01f);
        FxLib.ring(m,g,.3+r*Ease.out(t,at,at+RING_LIFE*.9f),.13,AZURE,.5f*a*(1-Ease.unit(t,at,at+RING_LIFE)));
        FxLib.ring(m,g,.25+r*.72*Ease.out(t,at,at+RING_LIFE*.6f),.045,WHITE,.45f*a*(1-Ease.unit(t,at,at+RING_LIFE*.6f)));
    }
    static double ring(int j){return j==LUNGE_SLASHES-1?RING_LAST:RING;}
    static final double RING=3.4,RING_LAST=5.2;
    static final float RING_LIFE=.4f;
    /** How hard slash {@code j}'s jolt is (the camera's shake; 2.96.2 to 2.100.0: .3 each), the last the hardest. */
    static float jolt(int j){return j==LUNGE_SLASHES-1?JOLT_LAST:JOLT;}
    static final float JOLT=.42f,JOLT_LAST=.7f;
    /** Slash {@code j}'s light on the foe: how far it reaches (blocks) and how bright (2.96.2 to 2.100.0: 8 and .7 each). */
    static double lightReach(int j){return j==LUNGE_SLASHES-1?15:11;}
    static float lightBright(int j){return j==LUNGE_SLASHES-1?1.3f:.95f;}

    // ───────────────────────────── the finisher ─────────────────────────────

    /**
     * The impact frame as the last slash cuts (deep blue, Y's: SwordCage.CAGE_LOOK; GGrand.impact's few frames), for T
     * {@code c}'s camera within 40 blocks of the foe (gone by 70), when this T {@link #finishes}.
     */
    static void frame(Fx c,FxFrame f,Vec3d foe){
        int last=LUNGE_SLASHES-1;boolean fin=finishes(c.cast);
        if(!fin||foe==null||!SwordLunge.cuts(c.cast.id(),last))return;
        float imp=GGrand.impact(c.sec,lungeSlash(last));if(imp<=0)return;
        float near=(float)Math.max(0,Math.min(1,(70-c.camera.distanceTo(foe))/30));
        if(near>0)f.impact(IMPACT*near*imp,0,SwordCage.CAGE_LOOK);
    }
    static final float IMPACT=.72f;
    /**
     * Whether T {@code cast}'s last slash flashes the impact frame: one used on its own does; used again as she stands on
     * the far sword (IsosangClient.chainedAfter, 0.7 s apart) every other one does, from the first ({@link #after}: else a
     * flicker every 0.7 s). Decided once, as this client first draws it (the T before it still known then).
     */
    static boolean finishes(StandClient.Cast cast){
        Boolean known=FINISH.get(cast.id());if(known!=null)return known;
        var before=IsosangClient.chainedAfter(cast);
        boolean v=after(before==null?null:FINISH.getOrDefault(before.id(),Boolean.TRUE));
        if(FINISH.size()>256)FINISH.clear();
        FINISH.put(cast.id(),v);return v;
    }
    /** Whether a T flashes, after one that did ({@code true}), did not ({@code false}), or none ({@code null}: on its own or the first of a row). */
    static boolean after(Boolean before){return before==null||!before;}
    private static final Map<UUID,Boolean> FINISH=new HashMap<>();
    static void clear(){FINISH.clear();}
}
