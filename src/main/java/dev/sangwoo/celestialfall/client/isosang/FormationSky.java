package dev.sangwoo.celestialfall.client.isosang;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoRenderer;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.vfx.FxLayer;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.*;

/**
 * The night over the formation (2.99.0, user request: "H 배경이나 그런것도 예쁘게 너가 할 수 있는 만큼 대폭 꾸며봐"): her
 * sword is the moon's trace (월흔), so as the formation stands a moonlit night falls over it, round it as far as
 * {@link #SKY_R} blocks:
 * <ul>
 * <li>the sky: a dome of night, from a deep navy overhead through blue to a pale lilac glow low behind the mountains,
 * the land beyond the mountains sinking into a dusky mist ({@link #dome}, {@link #shore});</li>
 * <li>a crescent moon rising from behind the mountain a little right of the formation's far side, ashen where it is
 * dark, a soft halo round it brighter on its lit side, a little star in its arms as on her sword ({@link #moon});</li>
 * <li>stars, a faint river of them across the sky, a shooting star now and then ({@link #stars}, {@link #meteors});</li>
 * <li>on the still water: moonlight glittering on the far side, under the moon, and the stars overhead mirrored in it
 * ({@link #water});</li>
 * <li>fireflies of pale light drifting slowly round over the water along the shore ({@link #fireflies}).</li>
 * </ul>
 * Night falls from the crown on (the zenith first, the glow low over the mountains last) and lifts as the formation
 * ends (the horizon first). It is the formation's own world: seen from outside it (a camera more than {@link #INSIDE1}
 * blocks from its middle) none of it is drawn. Kept as the user tuned them: the mountains (the user's paintings), the
 * clouds over them, the stream under them, the still water and its darker flow, the dark navy smoke and mist at the
 * rim, the whirlpool. The dome, the moon and the stars are drawn on the ground pass before the clouds and the mountains
 * (so those stand before them; the effects keep no depth of their own), in draw calls of their own
 * (KagenoFire.inkInterlude), softly into whatever of the world stands within {@link #SOFT} blocks behind them.
 */
final class FormationSky {
    private FormationSky(){}

    // ───────────────────────────── the clock ─────────────────────────────

    /**
     * How dark the sky is at the elevation {@code el} (degrees from the sky's eye, 0 to 1): night falls from NIGHT0 (just
     * after the crown breaks, H_PLANT) over NIGHT_FALL seconds at each height, the zenith first and the lowest ring of the
     * dome NIGHT_SPREAD seconds after; as the formation ends ({@code end}) it lifts from DAWN0 after it over DAWN_FALL
     * seconds, the lowest first and the zenith DAWN_SPREAD after.
     */
    static float night(float t,float end,double el){
        float n=(float)MathHelper.clamp((el-EL_MIN)/(90-EL_MIN),0,1);
        float in=NIGHT0+NIGHT_SPREAD*(1-n),out=end+DAWN0+DAWN_SPREAD*n;
        return Ease.smooth(t,in,in+NIGHT_FALL)*(1-Ease.smooth(t,out,out+DAWN_FALL));
    }
    static final float NIGHT0=.75f,NIGHT_SPREAD=.7f,NIGHT_FALL=1f,DAWN0=.12f,DAWN_SPREAD=.4f,DAWN_FALL=.5f;
    /** Whether any of the night is up at {@code t}. */
    static boolean up(float t,float end){return t>NIGHT0&&t<end+DAWN0+DAWN_SPREAD+DAWN_FALL+.05f;}
    /**
     * How much of it a camera sees (0 to 1): all of it within INSIDE0 blocks of the formation's middle (across the ground:
     * her camera looks in from some 14 blocks out, more as she stands farther out), none from INSIDE1 (from outside it
     * would be a dark bowl over the land).
     */
    static float inside(Vec3d centre,Vec3d camera){
        return 1-Ease.smooth((float)Math.hypot(camera.x-centre.x,camera.z-centre.z),INSIDE0,INSIDE1);
    }
    static final float INSIDE0=20,INSIDE1=25;

    // ───────────────────────────── the dome ─────────────────────────────

    /**
     * The sky: a sphere SKY_R blocks round its eye, SKY_EYE blocks over the formation's middle (about where her camera
     * looks from), drawn from EL_MIN degrees under the eye's horizon (into the ground: the land beyond, as far as it
     * reaches, fades into it, SOFT) to the zenith, in rings at RINGS degrees and SKY_SEGS ways round.
     */
    static final double SKY_R=26,SKY_EYE=4.5,EL_MIN=-16;
    static final double[] RINGS={-16,-10,-6,-3,-1,1,3,5,7,9,12,15,19,24,30,37,45,55,66,78,90};
    static final int SKY_SEGS=96;
    /** Blocks over which the dome and the stars fade into the world standing behind them (the far land into mist). */
    static final float SOFT=10;
    static Vec3d eye(Vec3d centre){return centre.add(0,SKY_EYE,0);}
    /** The way at the azimuth {@code az} (radians, the world's x toward its z) and elevation {@code el} (degrees). */
    static Vec3d dir(double az,double el){double e=Math.toRadians(el);return new Vec3d(Math.cos(e)*Math.cos(az),Math.sin(e),Math.cos(e)*Math.sin(az));}
    static Vec3d onSky(Vec3d centre,double az,double el,double r){return eye(centre).add(dir(az,el).multiply(r));}
    /** The azimuth of the way she faced as the formation began (from her, over its middle, to its far side). */
    static double forward(float yaw){Vec3d f=Vec3d.fromPolar(0,yaw);return Math.atan2(f.z,f.x);}
    /**
     * The sky's colours by elevation (degrees from its eye; its alpha SKY_ALPHA): a dusky mist under the horizon, the
     * glow brightest a little over it (GLOW_EL: just over the mountains' tops as her camera sees them), periwinkle, blue,
     * a deep navy overhead.
     */
    static final double[] STOP_EL={-16,-8,-3,0,3,6,9,13,18,25,33,45,62,90};
    static final int[] STOP_RGB={0x2c2e60,0x3b3e73,0x55588f,0x8183bb,0xaeaddb,0xb6b4e2,0x9ca1db,0x737fc9,0x515fb3,0x35449a,0x24327d,0x182464,0x111a51,0x0b1243};
    static final double GLOW_EL=6;
    static final float SKY_ALPHA=.94f;
    static int skyRgb(double el){
        if(el<=STOP_EL[0])return STOP_RGB[0];
        for(int i=1;i<STOP_EL.length;i++)if(el<=STOP_EL[i]){double u=(el-STOP_EL[i-1])/(STOP_EL[i]-STOP_EL[i-1]);return mix(STOP_RGB[i-1],STOP_RGB[i],u*u*(3-2*u));}
        return STOP_RGB[STOP_RGB.length-1];
    }
    /**
     * The sky's colour the way {@code d} (a unit vector from its eye), the moon the way {@code toMoon}: its height's colour,
     * lightened toward MOON_MIST round the moon (most low down, MOON_GLOW_W radians wide), a little uneven round it (faint
     * banks, never moving).
     */
    static int skyAt(Vec3d d,double az,double el,Vec3d toMoon){
        int c=skyRgb(el);
        if(toMoon!=null){
            double a=Math.acos(MathHelper.clamp(d.dotProduct(toMoon),-1,1)),g=Math.exp(-.5*(a/MOON_GLOW_W)*(a/MOON_GLOW_W));
            c=mix(c,MOON_MIST,MOON_GLOW*g*(1-.55*MathHelper.clamp((el-EL_MIN)/(90-EL_MIN),0,1)));
        }
        double v=1+.045*Math.sin(3*az+1.3)*Math.cos(Math.toRadians(el)*2)+.03*Math.sin(7*az-2+el*.05);
        return scale(c,v);
    }
    static final int MOON_MIST=0xd8dbff;
    static final double MOON_GLOW=.42,MOON_GLOW_W=.36;

    /**
     * The dome (the blended pass): every ring from the lowest, coloured by its height and the moon, darkening as night
     * falls there; then the moon's dark disc over it (its unlit part ashen, hiding the stars behind it).
     */
    static void dome(Fx c,EffectMesh m,Vec3d centre,float t,float end,float seen,Vec3d moon){
        Vec3d e=eye(centre);Vec3d toMoon=moon==null?null:moon.subtract(e).normalize();
        int n=SKY_SEGS,rings=RINGS.length;
        Vec3d[][] p=new Vec3d[rings][n+1];int[][] col=new int[rings][n+1];float[] a=new float[rings];
        for(int j=0;j<rings;j++){
            a[j]=SKY_ALPHA*night(t,end,RINGS[j])*seen;
            for(int i=0;i<=n;i++){
                double az=Math.PI*2*i/n;Vec3d d=dir(az,RINGS[j]);
                p[j][i]=e.add(d.multiply(SKY_R));col[j][i]=skyAt(d,az,RINGS[j],toMoon);
            }
        }
        for(int j=0;j+1<rings;j++){
            if(a[j]<=.002f&&a[j+1]<=.002f)continue;
            for(int i=0;i<n;i++){
                m.vertex(p[j][i],col[j][i],a[j]);m.vertex(p[j][i+1],col[j][i+1],a[j]);
                m.vertex(p[j+1][i+1],col[j+1][i+1],a[j+1]);m.vertex(p[j+1][i],col[j+1][i],a[j+1]);
            }
        }
        if(moon!=null){
            // the moon's disc: the sky's own colour there without the moon's glow, a little darker (its unlit part ashen)
            double el=Math.toDegrees(Math.asin(MathHelper.clamp(toMoon.y,-1,1)));
            float al=SKY_ALPHA*night(t,end,el)*seen*moonShown(c,moon)*moonLight(t,end);
            if(al>.005f)disc(m,moon,MOON_R*1.005,scale(skyRgb(el),.86),Math.min(1,al*1.04f),.03);
        }
    }

    // ───────────────────────────── the moon ─────────────────────────────

    /**
     * The moon: MOON_D blocks across the ground from the formation's middle, MOON_AZ to the right of its far side (where
     * the mountain right of the far gap stands: it rises from behind it), its middle MOON_UP blocks up once risen (her
     * camera, some 14 blocks behind the middle and 5.4 up, sees it some 10° over the horizon, 18° right of the middle),
     * MOON_R blocks round. It rises from under the ground (MOON_LOW) between MOON_RISE0 and MOON_RISE1 and, as the
     * formation ends, sinks a little as it fades.
     */
    static final double MOON_AZ=Math.toRadians(30),MOON_D=21.5,MOON_UP=11.5,MOON_LOW=-2.8,MOON_R=2.3;
    static final float MOON_RISE0=1.3f,MOON_RISE1=3.9f;
    static Vec3d moon(Vec3d centre,float yaw,float t,float end){
        double az=forward(yaw)+MOON_AZ;
        double up=MOON_LOW+(MOON_UP-MOON_LOW)*Ease.smooth(t,MOON_RISE0,MOON_RISE1)-1.6*Ease.smooth(t,end,end+.9f);
        return centre.add(Math.cos(az)*MOON_D,up,Math.sin(az)*MOON_D);
    }
    /** How bright the moon is (0 to 1): coming as it starts to rise, going as the formation ends. */
    static float moonLight(float t,float end){return Ease.smooth(t,MOON_RISE0-.1f,MOON_RISE0+.9f)*(1-Ease.smooth(t,end+.05f,end+.75f));}
    /** How much of the moon a camera sees (it thins away close by: from 12 blocks to 6; a camera that close is near its side of the sky). */
    static float moonShown(Fx c,Vec3d moon){return Ease.smooth((float)moon.distanceTo(c.camera),6,12);}
    /**
     * The moon's face at (x, y) in its own plane (its radii; x to the camera's left, as cameraPlane gives it, y up), 0 to
     * 1: lit where its disc is and the shadow's disc (MOON_SHADOW of its radius, MOON_CUT behind it, away from the way
     * MOON_LIT it is lit from) is not: a crescent lit low on its left as the camera sees it (toward the formation's
     * middle, the moon standing right of it), its horns opening up and to the right, its soft limb and terminator;
     * faint seas on it.
     */
    static float moonLit(double x,double y){
        double r=Math.hypot(x,y),disc=1-smooth01((r-.965)/.06);
        double sx=x+Math.cos(MOON_LIT)*MOON_CUT,sy=y+Math.sin(MOON_LIT)*MOON_CUT,ds=Math.hypot(sx,sy);
        double lit=disc*smooth01((ds-MOON_SHADOW+.05)/.1);
        double seas=1-.17*blob(x-.58,y+.36,.17)-.13*blob(x-.74,y+.02,.12)-.12*blob(x-.36,y+.72,.15);
        return (float)(lit*seas);
    }
    /** The moon's ashen light where it is dark (of its lit part's). */
    static float moonAsh(double x,double y){
        double r=Math.hypot(x,y),disc=1-smooth01((r-.965)/.06);
        double sx=x+Math.cos(MOON_LIT)*MOON_CUT,sy=y+Math.sin(MOON_LIT)*MOON_CUT,ds=Math.hypot(sx,sy);
        return (float)(disc*(1-smooth01((ds-MOON_SHADOW+.05)/.1))*MOON_ASH);
    }
    static final double MOON_LIT=Math.toRadians(-40),MOON_CUT=.42,MOON_SHADOW=.97;
    static final float MOON_ASH=.07f;
    /** The moon's rings (of its radius) and ways round as drawn. */
    static final double[] MOON_RINGS={0,.16,.32,.46,.58,.68,.77,.84,.89,.93,.955,.975,.99,1.005,1.03};
    static final int MOON_SEGS=72;

    /**
     * The moon's light (the light pass): its crescent (a little hotter than white on the effect layer, so it glows), its
     * ashen dark part, a halo round it brighter on its lit side, and a little star in its arms (as on her sword: its
     * crescent and star).
     */
    static void moonLight(Fx c,EffectMesh m,Vec3d moon,float k){
        if(k<=.005f)return;
        Vec3d[] uv=cameraPlane(m,moon);if(uv==null)return;
        Vec3d u=uv[0].multiply(MOON_R),v=uv[1].multiply(MOON_R);
        face(m,moon,u,v,false,k);
        // the halo: rings of light round its limb, fading out, the lit side the brighter
        halo(m,moon,uv,MOON_R*.99,MOON_R*1.7,MOON,.32f*k);
        halo(m,moon,uv,MOON_R*.99,MOON_R*3.0,PALE,.13f*k);
        halo(m,moon,uv,MOON_R*1.2,MOON_R*5.2,LILAC,.06f*k);
        halo(m,moon,uv,MOON_R*1.6,MOON_R*8.5,AZURE,.035f*k);
        // the little star in its arms, twinkling
        double a=MOON_LIT+Math.PI;
        Vec3d s=moon.add(uv[0].multiply(Math.cos(a)*MOON_R*.5)).add(uv[1].multiply(Math.sin(a)*MOON_R*.5));
        float tw=.75f+.25f*(float)Math.sin(c.sec*2.3);
        glint8(m,s,MOON_R*.24,WHITE,.85f*k*tw);m.softDisc(s,MOON_R*.22,PALE,.3f*k);
    }
    /** The moon's face round {@code o} in the plane (u, v) (u its radius to the right, v up), mirrored upside down if {@code mirrored}: its lit crescent and ashen dark. */
    static void face(EffectMesh m,Vec3d o,Vec3d u,Vec3d v,boolean mirrored,float k){
        int n=MOON_SEGS,rr=MOON_RINGS.length;
        Vec3d[][] p=new Vec3d[rr][n+1];float[][] a=new float[rr][n+1];
        for(int j=0;j<rr;j++)for(int i=0;i<=n;i++){
            double ang=Math.PI*2*i/n,x=MOON_RINGS[j]*Math.cos(ang),y=MOON_RINGS[j]*Math.sin(ang);
            p[j][i]=o.add(u.multiply(x)).add(v.multiply(y));
            double my=mirrored?-y:y;a[j][i]=(moonLit(x,my)+moonAsh(x,my))*k;
        }
        for(int j=0;j+1<rr;j++)for(int i=0;i<n;i++){
            if(a[j][i]+a[j][i+1]+a[j+1][i]+a[j+1][i+1]<=.004f)continue;
            m.vertex(p[j][i],MOON,a[j][i]);m.vertex(p[j][i+1],MOON,a[j][i+1]);m.vertex(p[j+1][i+1],MOON,a[j+1][i+1]);m.vertex(p[j+1][i],MOON,a[j+1][i]);
        }
    }
    /** A ring of light round {@code o} facing the camera, from {@code r0} (full) to {@code r1} (none), brighter on the side MOON_LIT. */
    static void halo(EffectMesh m,Vec3d o,Vec3d[] uv,double r0,double r1,int rgb,float alpha){
        if(alpha<=.003f)return;int n=48;
        for(int i=0;i<n;i++){
            double a0=Math.PI*2*i/n,a1=Math.PI*2*(i+1)/n;
            float s0=alpha*(float)(.55+.45*Math.cos(a0-MOON_LIT)),s1=alpha*(float)(.55+.45*Math.cos(a1-MOON_LIT));
            Vec3d d0=uv[0].multiply(Math.cos(a0)).add(uv[1].multiply(Math.sin(a0))),d1=uv[0].multiply(Math.cos(a1)).add(uv[1].multiply(Math.sin(a1)));
            m.vertex(o.add(d0.multiply(r0)),rgb,s0);m.vertex(o.add(d1.multiply(r0)),rgb,s1);m.vertex(o.add(d1.multiply(r1)),rgb,0);m.vertex(o.add(d0.multiply(r1)),rgb,0);
        }
    }

    // ───────────────────────────── the stars ─────────────────────────────

    /**
     * A star: the way it lies from the sky's eye (azimuth radians, elevation degrees), how big it looks (radians across
     * its core, the same from wherever it is seen), how bright, its colour, how fast and from where it twinkles, when it
     * comes out, and its kind (0 a point, 1 a bright one with rays, 2 one of the river's).
     */
    record SkyStar(double az,double el,double size,float bright,int rgb,float rate,float phase,float born,int kind){}
    private static final Map<UUID,SkyStar[]> STARS=new LinkedHashMap<>();
    /** How many stars, and how many more in the river across the sky. */
    static final int STAR_COUNT=700,RIVER_COUNT=320;
    /** When the stars come out (each its own time between), and from how far under SKY_R they lie (in front of the dome). */
    static final float STARS0=1.5f,STARS1=3.9f;
    static final double STAR_R=SKY_R-.6;
    /**
     * The stars of the formation of swing {@code id} (the same on every client), its user having faced {@code yaw}: all
     * round, more of them low (from 7° up), most faint, one in eighteen or so bright with rays; and RIVER_COUNT faint
     * ones close about the river of stars ({@link #river}).
     */
    static SkyStar[] stars(UUID id,float yaw){
        var l=STARS.get(id);if(l!=null)return l;
        int s0=(int)(id.getMostSignificantBits()*29+id.getLeastSignificantBits()*13);
        List<SkyStar> out=new ArrayList<>();
        for(int i=0;i<STAR_COUNT;i++){
            int seed=s0+733*(i+1);double mag=Math.pow(Ease.hash(seed+3),3.2);
            double az=Math.PI*2*Ease.hash(seed+1),el=7+78*Math.pow(Ease.hash(seed+2),1.5);
            int kind=mag>.75?1:0;
            out.add(new SkyStar(az,el,kind==1?.0036+.002*mag:.0025+.0022*mag,(float)(.38+.62*Math.sqrt(mag)),colour(seed+4),(float)(.25+.85*Ease.hash(seed+5)),
                (float)Ease.hash(seed+6),STARS0+(STARS1-STARS0)*(float)Ease.hash(seed+7),kind));
        }
        double f=forward(yaw);
        for(int i=0;i<RIVER_COUNT;i++){
            int seed=s0+1171*(i+1);
            double s=RIVER_FROM+(RIVER_TO-RIVER_FROM)*Ease.hash(seed+1),off=(Ease.hash(seed+2)+Ease.hash(seed+3)+Ease.hash(seed+4)-1.5)*RIVER_HALF;
            Vec3d d=river(f,s,off);double el=Math.toDegrees(Math.asin(MathHelper.clamp(d.y,-1,1)));if(el<6)continue;
            out.add(new SkyStar(Math.atan2(d.z,d.x),el,.0021+.0015*Ease.hash(seed+5),(float)(.3+.45*Ease.hash(seed+6)),colour(seed+7),(float)(.3+.9*Ease.hash(seed+8)),
                (float)Ease.hash(seed+9),STARS0+.4f+(STARS1-STARS0)*(float)Ease.hash(seed+10),2));
        }
        l=out.toArray(new SkyStar[0]);
        STARS.put(id,l);while(STARS.size()>16)STARS.remove(STARS.keySet().iterator().next());
        return l;
    }
    /** A star's colour: mostly white and pale blue, some lilac, a few moonlight. */
    static int colour(int seed){double h=Ease.hash(seed);return h<.4?WHITE:h<.72?PALE:h<.9?LILAC:MOON;}
    /** How brightly a star shows at {@code t} (0 to its brightness and a little over as it comes out): twinkling slowly, fading into the glow low down. */
    static float starAlpha(SkyStar s,float t,float end){
        float in=Ease.smooth(t,s.born(),s.born()+.3f),pop=1+.9f*Ease.smooth(t,s.born(),s.born()+.12f)*(1-Ease.smooth(t,s.born()+.12f,s.born()+.45f));
        float out=1-Ease.smooth(t,end,end+.45f),low=Ease.smooth((float)s.el(),5,13);
        float tw=.64f+.36f*(float)Math.sin(Math.PI*2*(s.rate()*t+s.phase()));
        return s.bright()*in*pop*out*low*tw;
    }
    /**
     * The river of stars across the sky (as the Milky Way): a great circle through the way RIVER_A (low on the left of
     * the far side) and RIVER_B (higher, a little left of it), so it rises across the left of her camera's sky, clear of
     * the sword in the stars over the far side and of the moon right of it; the way {@code s} radians along it from
     * RIVER_A toward RIVER_B, {@code off} radians off it.
     */
    static Vec3d river(double f,double s,double off){
        Vec3d a=dir(f+RIVER_A_AZ,RIVER_A_EL),b=dir(f+RIVER_B_AZ,RIVER_B_EL);
        Vec3d n=a.crossProduct(b).normalize(),t=n.crossProduct(a).normalize();
        Vec3d on=a.multiply(Math.cos(s)).add(t.multiply(Math.sin(s)));
        return on.multiply(Math.cos(off)).add(n.multiply(Math.sin(off))).normalize();
    }
    static final double RIVER_A_AZ=Math.toRadians(-60),RIVER_A_EL=0,RIVER_B_AZ=Math.toRadians(-12),RIVER_B_EL=30,RIVER_FROM=-.15,RIVER_TO=3.3,RIVER_HALF=.085;
    /**
     * The stars (the light pass): each a soft point (a bright one with four rays and four shorter), as big as it looks
     * from the camera wherever that is; the river's faint glow; the shooting stars. None within a few blocks of the camera.
     */
    static void stars(Fx c,EffectMesh m,Vec3d centre,float t,float end,float seen){
        Vec3d e=eye(centre);
        for(var s:stars(c.cast.id(),SwordFormation.yaw(c.cast))){
            float a=starAlpha(s,t,end)*seen;if(a<=.01f)continue;
            Vec3d p=e.add(dir(s.az(),s.el()).multiply(STAR_R));double d=p.distanceTo(c.camera);
            a*=Ease.smooth((float)d,4,8);if(a<=.01f)continue;
            double r=s.size()*d;
            point(m,p,r,s.rgb(),a);
            if(s.kind()==1){glint8(m,p,r*4.6,s.rgb(),.55f*a);m.softDisc(p,r*3,s.rgb(),.18f*a);}
        }
        // the river's glow: soft clouds of faint light along it, uneven
        float g=seen*Ease.smooth(t,STARS0+.6f,STARS1+.4f)*(1-Ease.smooth(t,end,end+.5f));
        if(g>.01f){
            double f=forward(SwordFormation.yaw(c.cast));
            for(int i=0;i<RIVER_GLOWS;i++){
                double s=RIVER_FROM+(RIVER_TO-RIVER_FROM)*(i+.5)/RIVER_GLOWS;Vec3d d=river(f,s,0);
                double el=Math.toDegrees(Math.asin(MathHelper.clamp(d.y,-1,1)));if(el<4)continue;
                double clump=.65+.35*Math.sin(5*s+1)+.2*Math.sin(13*s-2);if(clump<=0)continue;
                Vec3d p=e.add(d.multiply(STAR_R+.2));
                float a=(float)(.1*clump)*g*Ease.smooth((float)el,4,14)*Ease.smooth((float)p.distanceTo(c.camera),6,12);
                m.softDisc(p,3.2+1.1*Math.sin(7*s),i%2==0?LILAC:PALE,a);
                // its dusty heart, a little off its line, now on one side, now the other
                m.softDisc(e.add(river(f,s+.03,.035*Math.sin(3*s+2)).multiply(STAR_R+.15)),1.7+.6*Math.sin(11*s),WHITE,.55f*a);
            }
        }
        constellation(c,m,centre,t,end,seen);
        meteors(c,m,centre,t,end,seen);
    }
    static final int RIVER_GLOWS=70;

    /**
     * The sword in the stars (검자리): seven stars over the formation's far side as one of its swords stands, its point
     * down (pommel, grip, the guard's two ends and its middle, the blade's middle, the point; each {degrees round from the
     * far side's way, elevation in degrees}), a little brighter than the rest; once they are out, faint lines of light
     * drawn between them one after another from SWORD0 (pommel to point, then the guard), and they stay.
     */
    static final double[][] SWORD_STARS={{0,27},{0,24.6},{-3.3,22.4},{0,22.4},{3.3,22.4},{0,17.4},{0,11.6}};
    static final int[][] SWORD_LINES={{0,1},{1,3},{3,5},{5,6},{3,2},{3,4}};
    static final float SWORD0=3.9f,SWORD_STEP=.22f,SWORD_DRAW=.3f;
    static Vec3d swordStar(Vec3d centre,float yaw,int k){
        return eye(centre).add(dir(forward(yaw)+Math.toRadians(SWORD_STARS[k][0]),SWORD_STARS[k][1]).multiply(STAR_R-.1));
    }
    /** How far the k-th line of the sword in the stars is drawn at {@code t} (0 to 1). */
    static float swordLine(int k,float t){return Ease.smooth(t,SWORD0+SWORD_STEP*k,SWORD0+SWORD_STEP*k+SWORD_DRAW);}
    static void constellation(Fx c,EffectMesh m,Vec3d centre,float t,float end,float seen){
        float yaw=SwordFormation.yaw(c.cast),out=(1-Ease.smooth(t,end,end+.45f))*seen;if(out<=.01f)return;
        Vec3d[] p=new Vec3d[SWORD_STARS.length];
        for(int k=0;k<p.length;k++){
            p[k]=swordStar(centre,yaw,k);double d=p[k].distanceTo(c.camera);
            float a=out*Ease.smooth(t,SWORD0-.9f+.1f*k,SWORD0-.5f+.1f*k)*(.8f+.2f*(float)Math.sin(t*(1.3+.4*k)+k))*Ease.smooth((float)d,6,12);
            if(a<=.01f)continue;
            point(m,p[k],.0048*d,WHITE,a);glint8(m,p[k],.017*d,k==6||k==0?WHITE:PALE,.55f*a);m.softDisc(p[k],.013*d,AZURE,.22f*a);
        }
        for(int k=0;k<SWORD_LINES.length;k++){
            float u=swordLine(k,t);if(u<=.001f)continue;
            Vec3d a=p[SWORD_LINES[k][0]],b=p[SWORD_LINES[k][1]],h=a.add(b.subtract(a).multiply(u));double d=h.distanceTo(c.camera);
            float al=out*Ease.smooth((float)d,6,12);if(al<=.01f)continue;
            m.softLine(a,h,.0045*d,PALE,.42f*al);m.softLine(a,h,.0016*d,WHITE,.35f*al);
            // its drawing head, bright as it goes
            if(u<1){m.softDisc(h,.01*d,WHITE,.7f*al);glint8(m,h,.012*d,WHITE,.5f*al);}
        }
    }
    /** A soft point of light round {@code p}, {@code r} round (full at its middle, none at its edge), facing the camera. */
    static void point(EffectMesh m,Vec3d p,double r,int rgb,float alpha){
        Vec3d[] uv=cameraPlane(m,p);if(uv==null)return;int n=6;
        for(int i=0;i<n;i++){
            double a0=Math.PI*2*i/n,a1=Math.PI*2*(i+1)/n;
            Vec3d p0=p.add(uv[0].multiply(Math.cos(a0)*r)).add(uv[1].multiply(Math.sin(a0)*r)),p1=p.add(uv[0].multiply(Math.cos(a1)*r)).add(uv[1].multiply(Math.sin(a1)*r));
            m.vertex(p,rgb,alpha);m.vertex(p0,rgb,0);m.vertex(p1,rgb,0);m.vertex(p,rgb,alpha);
        }
    }

    // ───────────────────────────── shooting stars ─────────────────────────────

    /**
     * A shooting star: when it flies (seconds into H) and for how long, the way it starts from (azimuth radians,
     * elevation degrees), its heading over the sky (radians from straight along it to the right, down negative) and how
     * far it goes (radians over the sky).
     */
    record Meteor(float at,float life,double az,double el,double heading,double length){}
    /** The k-th shooting star of the formation of swing {@code id}: about every METEOR_EVERY seconds from METEOR0, over the far side's sky (low enough for her camera to see it), falling to the left or the right. */
    static Meteor meteor(UUID id,float yaw,int k){
        int seed=(int)(id.getLeastSignificantBits()*7+id.getMostSignificantBits()*11)+977*(k+1);
        float at=METEOR0+METEOR_EVERY*k+1.8f*(float)(Ease.hash(seed)-.5);
        double az=forward(yaw)+(Ease.hash(seed+1)-.5)*Math.toRadians(130),el=14+16*Ease.hash(seed+2);
        boolean left=Ease.hash(seed+3)<.5;double down=Math.toRadians(22+22*Ease.hash(seed+4));
        return new Meteor(at,.6f+.25f*(float)Ease.hash(seed+5),az,el,left?Math.PI+down:-down,.3+.15*Ease.hash(seed+6));
    }
    static final float METEOR0=3.6f,METEOR_EVERY=4.4f;
    /** The shooting stars flying at {@code t} (none once the formation has ended). */
    static List<Meteor> meteors(UUID id,float yaw,float t,float end){
        List<Meteor> out=new ArrayList<>();
        int k0=Math.max(0,(int)Math.floor((t-METEOR0)/METEOR_EVERY)-1);
        for(int k=k0;k<=k0+2;k++){Meteor me=meteor(id,yaw,k);if(t>=me.at()&&t<me.at()+me.life()&&me.at()+me.life()<end)out.add(me);}
        return out;
    }
    /** Where a shooting star is {@code s} radians along its way (a unit vector from the sky's eye). */
    static Vec3d meteorAt(Meteor me,double s){
        Vec3d p=dir(me.az(),me.el()),east=new Vec3d(-Math.sin(me.az()),0,Math.cos(me.az()));
        Vec3d upward=p.crossProduct(east).normalize().negate();
        Vec3d head=east.multiply(Math.cos(me.heading())).add(upward.multiply(Math.sin(me.heading()))).normalize();
        return p.multiply(Math.cos(s)).add(head.multiply(Math.sin(s))).normalize();
    }
    /** The shooting stars (the light pass): a bright head racing along, its tail drawn out behind it and fading, all gone in about half a second. */
    static void meteors(Fx c,EffectMesh m,Vec3d centre,float t,float end,float seen){
        Vec3d e=eye(centre);
        for(var me:meteors(c.cast.id(),SwordFormation.yaw(c.cast),t,end)){
            float u=(t-me.at())/me.life(),a=seen*Ease.smooth(u,0,.1f)*(1-Ease.smooth(u,.68f,1));if(a<=.01f)continue;
            double head=me.length()*Math.pow(u,.75),tail=Math.max(0,head-me.length()*.55*(1-.6*u));
            Vec3d h=e.add(meteorAt(me,head).multiply(STAR_R-.2)),q=e.add(meteorAt(me,tail).multiply(STAR_R-.2)),mid=e.add(meteorAt(me,(head+tail)/2).multiply(STAR_R-.2));
            double w=.0065*h.distanceTo(c.camera);
            m.softTaper(q,mid,0,w*.9,PALE,.6f*a);m.softTaper(mid,h,w*.9,w*1.5,WHITE,.9f*a);m.softTaper(q,h,0,w*4,AZURE,.18f*a);
            point(m,h,w*2.4,WHITE,a);glint8(m,h,w*4,WHITE,.5f*a);m.softDisc(h,w*7,AZURE,.28f*a);
        }
    }

    // ───────────────────────────── the land round it ─────────────────────────────

    /**
     * The ground beyond the water, under the mountains and out to the dome (the blended pass): a dusky veil lying on it,
     * from nothing at the water's edge to as dark as the dome's foot by the time it meets it, so the daylit land round the
     * formation sinks into the night with it.
     */
    static void shore(EffectMesh m,Vec3d centre,float t,float end,float seen){
        float a0=night(t,end,0)*seen;if(a0<=.005f)return;
        Vec3d g=centre.add(0,.035,0);int n=96;
        double[] r=SHORE_R;float[] al=SHORE_ALPHA;int[] col=SHORE_RGB;
        for(int i=0;i<n;i++){
            double b0=Math.PI*2*i/n,b1=Math.PI*2*(i+1)/n;
            for(int j=0;j+1<r.length;j++){
                Vec3d p00=g.add(Math.cos(b0)*r[j],0,Math.sin(b0)*r[j]),p01=g.add(Math.cos(b1)*r[j],0,Math.sin(b1)*r[j]);
                Vec3d p10=g.add(Math.cos(b0)*r[j+1],0,Math.sin(b0)*r[j+1]),p11=g.add(Math.cos(b1)*r[j+1],0,Math.sin(b1)*r[j+1]);
                m.vertex(p00,col[j],al[j]*a0);m.vertex(p01,col[j],al[j]*a0);m.vertex(p11,col[j+1],al[j+1]*a0);m.vertex(p10,col[j+1],al[j+1]*a0);
            }
        }
    }
    static final double[] SHORE_R={SwordFormation.WATER_EDGE-.3,SwordFormation.WATER_EDGE+1.3,SwordFormation.WATER_EDGE+3.5,SwordFormation.WATER_EDGE+7,SKY_R+.5};
    static final float[] SHORE_ALPHA={0,.5f,.68f,.8f,.88f};
    static final int[] SHORE_RGB={0x23285a,0x262b5c,0x2d3164,0x34386c,0x3b3e73};

    // ───────────────────────────── the water ─────────────────────────────

    /** The water's height over the formation's ground (its rings lie .045 over it). */
    static double waterLevel(Vec3d centre){return centre.y+.05;}
    /** How much water there is {@code r} blocks from the formation's middle to show light (0 to 1): none in its bare middle (its soft edge some 4.6 to 6.2 out), nor past the mountains' feet. */
    static float wet(double r){
        return Ease.smooth((float)r,WET_IN0,WET_IN1)*(1-Ease.smooth((float)r,(float)SwordFormation.WATER_EDGE-.9f,(float)SwordFormation.WATER_EDGE-.1f));
    }
    static final float WET_IN0=4.6f,WET_IN1=6.2f;
    /** {@code p} mirrored in the water at the height {@code w}. */
    static Vec3d mirror(Vec3d p,double w){return new Vec3d(p.x,2*w-p.y,p.z);}
    /** Where, on the water at the height {@code w}, the camera at {@code camera} sees the point {@code q} under it (null if it does not). */
    static Vec3d onWater(Vec3d camera,Vec3d q,double w){
        double dy=q.y-camera.y;if(camera.y<=w+.02||dy>=-1e-6)return null;
        double s=(w-camera.y)/dy;if(s<=0||s>=1)return null;
        return camera.add(q.subtract(camera).multiply(s));
    }
    /**
     * Light on the water (the light pass, on the water): the moon's path of light ({@link #moonPath}) and the brighter
     * stars overhead mirrored in the water where the camera sees water.
     */
    static void water(Fx c,EffectMesh m,Vec3d centre,float t,float end,float seen){
        float life=SwordFormation.flowLife(t,end)*seen;if(life<=.01f)return;
        double w=waterLevel(centre);float k=life*moonLight(t,end)*night(t,end,0);
        if(k>.01f)moonPath(c,m,centre,moon(centre,SwordFormation.yaw(c.cast),t,end),w,t,k);
        // the stars mirrored in it: those bright enough, where the camera sees them on its water
        Vec3d e=eye(centre);
        for(var s:stars(c.cast.id(),SwordFormation.yaw(c.cast))){
            if(s.bright()<MIRROR_BRIGHT)continue;
            float a=starAlpha(s,t,end)*life*MIRRORED;if(a<=.01f)continue;
            Vec3d p=e.add(dir(s.az(),s.el()).multiply(STAR_R)),q=onWater(c.camera,mirror(p,w),w);if(q==null)continue;
            float wt=wet(Math.hypot(q.x-centre.x,q.z-centre.z));if(wt<=.01f)continue;
            point(m,q.add(0,.012,0),s.size()*q.distanceTo(c.camera)*1.15,s.rgb(),a*wt);
        }
    }
    /**
     * The moon's path of light on the water: as on water the glitter always runs from whoever looks at it toward the
     * light, glints within GLINT_HALF radians either side of the way from the camera to the moon's foot (brightest
     * along its middle) sparkle, and a faint sheen lies along it, only where there is water (so none on the formation's
     * bare middle). The glints are fixed in the world (one in each square of a grid GLINT_STEP blocks round the
     * formation's middle, each twinkling in its own time): the camera picks which of them shine; they never slide.
     */
    static void moonPath(Fx c,EffectMesh m,Vec3d centre,Vec3d moon,double w,float t,float k){
        Vec3d cam=c.camera;double dx=moon.x-cam.x,dz=moon.z-cam.z,len=Math.hypot(dx,dz);if(len<2)return;
        double ux=dx/len,uz=dz/len;
        int n=(int)Math.ceil(SwordFormation.WATER_EDGE/GLINT_STEP),s0=c.cast.id().hashCode()*7+5;
        for(int i=-n;i<=n;i++)for(int j=-n;j<=n;j++){
            int seed=s0+i*7919+j*104729;
            double px=centre.x+(i+Ease.hash(seed+1)-.5)*GLINT_STEP,pz=centre.z+(j+Ease.hash(seed+2)-.5)*GLINT_STEP;
            float wt=wet(Math.hypot(px-centre.x,pz-centre.z));if(wt<=.01f)continue;
            double g=onPath(cam,moon,px,pz);if(g<.04)continue;double ax=px-cam.x,az=pz-cam.z;
            double period=.9+1.5*Ease.hash(seed+3),ph=((t+period*Ease.hash(seed+4))%period)/period;
            float spark=(float)Math.max(0,1-Math.abs(ph-.5)/.08),a=k*wt*(float)g*(.42f+.58f*spark*spark);if(a<=.01f)continue;
            Vec3d p=new Vec3d(px,w+.02,pz);double d=p.distanceTo(cam),size=GLINT_SIZE*d*(.75+.5*Ease.hash(seed+5));
            // a short flat dash across the camera's view, lying on the water, and its bright point
            Vec3d flat=new Vec3d(-az,0,ax).normalize().multiply(size*(3+3*Ease.hash(seed+6)));
            m.softLine(p.subtract(flat),p.add(flat),size*1.2,(seed&3)==0?WHITE:MOON,.8f*a);
            point(m,p,size*1.15,WHITE,a);
            if(spark>.55f)glint8(m,p,size*3.2,WHITE,.45f*a*spark);
        }
        // the sheen along it: soft flat patches of pale light, where there is water
        for(double along=2;along<len;along+=SHEEN_STEP){
            double px=cam.x+ux*along,pz=cam.z+uz*along;float wt=wet(Math.hypot(px-centre.x,pz-centre.z));if(wt<=.01f)continue;
            Vec3d p=new Vec3d(px,w+.01,pz);
            flatGlow(m,p,new Vec3d(ux,0,uz).multiply(SHEEN_STEP*.75),new Vec3d(-uz,0,ux).multiply(Math.max(.15,GLINT_HALF*1.5*along)),PALE,SHEEN*k*wt);
        }
    }
    /**
     * How much the point (x, z) on the water lies on the moon's path of light as the camera at {@code cam} sees it (0 to
     * 1): 1 on the way from the camera to the moon's foot, falling off GLINT_HALF radians either side of it; none under
     * the camera or behind it.
     */
    static double onPath(Vec3d cam,Vec3d moon,double x,double z){
        double dx=moon.x-cam.x,dz=moon.z-cam.z,len=Math.hypot(dx,dz);if(len<2)return 0;
        double ux=dx/len,uz=dz/len,ax=x-cam.x,az=z-cam.z,along=ax*ux+az*uz;if(along<1.2)return 0;
        double off=(az*ux-ax*uz)/along;return Math.exp(-.5*(off/GLINT_HALF)*(off/GLINT_HALF));
    }
    /** The path's glints: how far apart (blocks), how far either side of its middle they shine (radians, as seen from the camera), how big (of their distance). */
    static final double GLINT_STEP=.5,GLINT_HALF=.05,GLINT_SIZE=.0042,SHEEN_STEP=1.2;
    /** How bright the path's sheen is. */
    static final float SHEEN=.2f;
    /** Which stars show in the water (the brightest of them) and how strongly (of their own light). */
    static final float MIRROR_BRIGHT=.5f,MIRRORED=.45f;
    /** A soft patch of light lying flat round {@code p}: an ellipse {@code u} and {@code v} round, full at its middle and fading to nothing at its rim. */
    static void flatGlow(EffectMesh m,Vec3d p,Vec3d u,Vec3d v,int rgb,float alpha){
        if(alpha<=.003f)return;int n=16;
        for(int i=0;i<n;i++){
            double a0=Math.PI*2*i/n,a1=Math.PI*2*(i+1)/n;
            Vec3d r0=p.add(u.multiply(Math.cos(a0))).add(v.multiply(Math.sin(a0))),r1=p.add(u.multiply(Math.cos(a1))).add(v.multiply(Math.sin(a1)));
            m.vertex(p,rgb,alpha);m.vertex(r0,rgb,0);m.vertex(r1,rgb,0);m.vertex(p,rgb,alpha);
        }
    }

    // ───────────────────────────── fireflies ─────────────────────────────

    /**
     * Fireflies (the light pass, over the water before the mountains): FIREFLIES soft lights drifting slowly round over
     * the water along the shore (9.8 to 12.2 blocks out, half a block to 3.4 up), bobbing, each glowing up and fading
     * away again in its own time, a faint trail behind it.
     */
    static void fireflies(Fx c,EffectMesh m){
        float t=c.sec,end=SwordFormation.end(c.cast);Vec3d centre=SwordFormation.centre(c.cast);
        float on=Ease.smooth(t,FIREFLIES0,FIREFLIES0+1.4f)*(1-Ease.smooth(t,end,end+.6f))*inside(centre,c.camera);if(on<=.01f)return;
        int s0=c.cast.id().hashCode()*3+17;
        for(int i=0;i<FIREFLIES;i++){
            int seed=s0+523*(i+1);
            double r=9.8+2.4*Ease.hash(seed+1),spin=(Ease.hash(seed+2)<.5?-1:1)*(.025+.04*Ease.hash(seed+3)),bob=1.6+1.4*Ease.hash(seed+6),base=.5+2.9*Ease.hash(seed+5);
            double az=Math.PI*2*Ease.hash(seed+4)+spin*t,h=base+.28*Math.sin(t*bob+seed);
            Vec3d p=centre.add(Math.cos(az)*r,h,Math.sin(az)*r);
            double period=2.2+2.4*Ease.hash(seed+7),ph=((t+period*Ease.hash(seed+8))%period)/period;
            float glow=(float)Math.pow(Math.sin(Math.PI*ph),2),a=on*(.15f+.85f*glow)*near(p,c.camera)*Ease.smooth((float)p.distanceTo(c.camera),2.5f,5);
            if(a<=.01f)continue;
            int col=i%3==0?CYAN:i%3==1?PALE:LILAC;
            Vec3d back=centre.add(Math.cos(az-spin*.9)*r,base+.28*Math.sin((t-.9)*bob+seed),Math.sin(az-spin*.9)*r);
            m.softTaper(back,p,0,.05,col,.25f*a);
            point(m,p,.09,WHITE,a);m.softDisc(p,.4,col,.36f*a);
        }
    }
    static final int FIREFLIES=22;
    static final float FIREFLIES0=2.2f;

    // ───────────────────────────── drawing ─────────────────────────────

    /**
     * The night's dome, moon and stars and the dusk on the land round it (in the ground pass's interlude, before the
     * clouds and the mountains): the dome and the moon's dark disc blended, the stars and the moon's light added, the
     * land's veil blended.
     */
    static void sky(Fx c){
        float t=c.sec,end=SwordFormation.end(c.cast);if(!up(t,end))return;
        Vec3d centre=SwordFormation.centre(c.cast);float seen=inside(centre,c.camera);if(seen<=.005f)return;
        Vec3d moon=moon(centre,SwordFormation.yaw(c.cast),t,end);
        float shine=moonLight(t,end)*moonShown(c,moon)*seen;
        draw(c,false,SOFT,1,m->dome(c,m,centre,t,end,seen,shine>.005f?moon:null));
        draw(c,true,SOFT,1,m->stars(c,m,centre,t,end,seen));
        if(shine>.005f)draw(c,true,SOFT,MOON_GAIN,m->moonLight(c,m,moon,shine));
        draw(c,false,.35f,1,m->shore(m,centre,t,end,seen));
    }
    /** The moon's light on the effect layer (a little over white, so it blooms). */
    static final float MOON_GAIN=1.2f;
    /** The light on the water (in the interlude after the mountains, before the swords stand over it). */
    static void water(Fx c){
        float t=c.sec,end=SwordFormation.end(c.cast);if(!up(t,end))return;
        Vec3d centre=SwordFormation.centre(c.cast);float seen=inside(centre,c.camera);if(seen<=.005f)return;
        draw(c,true,.04f,1,m->water(c,m,centre,t,end,seen));
    }
    /**
     * One draw call of its own: {@code body} into a fresh batch, drawn on the effect layer (soft into the world within
     * {@code soft} blocks, {@code gain} its light) or, without it, straight to the screen; blended or added.
     */
    static void draw(Fx c,boolean additive,float soft,float gain,Consumer<EffectMesh> body){
        var buf=Tessellator.getInstance().getBuffer();
        buf.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
        body.accept(new EffectMesh(buf,c.matrix,c.camera));
        var built=buf.end();
        if(KagenoRenderer.layering())FxLayer.draw(built,additive,gain,soft);
        else{
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            if(additive)RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA,com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
            else RenderSystem.defaultBlendFunc();
            BufferRenderer.drawWithGlobalProgram(built);
            RenderSystem.defaultBlendFunc();
        }
    }

    // ───────────────────────────── small things ─────────────────────────────

    /** A disc round {@code p} facing the camera, {@code r} round, solid out to {@code 1-edge} of it and fading to nothing at its rim. */
    static void disc(EffectMesh m,Vec3d p,double r,int rgb,float alpha,double edge){
        Vec3d[] uv=cameraPlane(m,p);if(uv==null)return;int n=48;
        for(int i=0;i<n;i++){
            double a0=Math.PI*2*i/n,a1=Math.PI*2*(i+1)/n;
            Vec3d d0=uv[0].multiply(Math.cos(a0)).add(uv[1].multiply(Math.sin(a0))),d1=uv[0].multiply(Math.cos(a1)).add(uv[1].multiply(Math.sin(a1)));
            Vec3d i0=p.add(d0.multiply(r*(1-edge))),i1=p.add(d1.multiply(r*(1-edge)));
            m.vertex(p,rgb,alpha);m.vertex(i0,rgb,alpha);m.vertex(i1,rgb,alpha);m.vertex(p,rgb,alpha);
            m.vertex(i0,rgb,alpha);m.vertex(p.add(d0.multiply(r)),rgb,0);m.vertex(p.add(d1.multiply(r)),rgb,0);m.vertex(i1,rgb,alpha);
        }
    }
    static double smooth01(double x){x=MathHelper.clamp(x,0,1);return x*x*(3-2*x);}
    static double blob(double x,double y,double r){return Math.exp(-(x*x+y*y)/(2*r*r));}
    static int mix(int a,int b,double u){
        u=MathHelper.clamp(u,0,1);
        int r=(int)Math.round(((a>>16)&255)+(((b>>16)&255)-((a>>16)&255))*u),g=(int)Math.round(((a>>8)&255)+(((b>>8)&255)-((a>>8)&255))*u),bl=(int)Math.round((a&255)+((b&255)-(a&255))*u);
        return (r<<16)|(g<<8)|bl;
    }
    static int scale(int c,double k){
        int r=(int)Math.min(255,Math.round(((c>>16)&255)*k)),g=(int)Math.min(255,Math.round(((c>>8)&255)*k)),b=(int)Math.min(255,Math.round((c&255)*k));
        return (r<<16)|(g<<8)|b;
    }
}
