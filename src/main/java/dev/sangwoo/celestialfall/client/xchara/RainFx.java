package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.xchara.RainGround;
import dev.sangwoo.celestialfall.xchara.RainRules;
import dev.sangwoo.celestialfall.xchara.ShotRules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Everything the effects' layer draws of X Chara's rain of knives (H with their hack off; 2.132.5, user request:
 * "20블록으로 줄이고 떨어지는 칼들도 대폭 줄이고 떨어지는 속도도 줄여 진짜 비 처럼 만들어 그리고 적을 찌르는건 없애고 그냥 비만
 * 떨어지게 비는 30초동안 계속 떨어져"; the screen inside it "은은하게 30초 내내"). X Chara's purple is all of it; white is in
 * thin cores, small stars and the knives themselves. 2.132.0 to 2.132.4's run, stab, binding and last volley are gone.
 * <ul>
 * <li>The call ({@link #call}, BladeRain's swing): purple drawn onto the knife as it is raised; as the rain is called a
 * beam of light up off its point into the sky, a ring and a star where it meets the sky, a ring over the ground under
 * them.</li>
 * <li>The rain ({@link #zone}, RainClient's cast, as long as it rains where it was called): the circle in the sky over it
 * ({@link #sky}), wide as it is, turning slowly; out of it the knives (RainRules: when each comes, where it comes down,
 * how high from), lit as the hack lights them, falling straight down slowly with a short streak over each, coming out of
 * the sky in blocks of purple and a star ({@link #knives}). One stands a moment where it lands and breaks up into
 * pixels: in the ground a ring and pixels splashing up; in a body (where the server said it met it, or where this client
 * sees it meet it first) a star, a ring, spikes thrown up, pixels; on its own user, harmlessly, broken on a small ring
 * over their head. A faint ring over the ground where it ends ({@link #edge}). What the camera cannot see is not drawn,
 * what is far off is drawn coarser (2.132.4's).</li>
 * <li>The screen inside it ({@link #screen}): for whoever stands in it, a violet air, its edges a little darker, thin
 * light falling down it (FxFrame.rain, the layer's composite), gently, all the while it rains: no tearing, no impact
 * frames (2.132.1 to 2.132.4 had them).</li>
 * </ul>
 * Every part of it is a function of the rain's own clock, its seed and what the server told, the same on every client.
 */
final class RainFx {
    private RainFx(){}
    static final int INK=GlitchFx.INK,DEEP=GlitchFx.DEEP,PURPLE=GlitchFx.PURPLE,GLOW=GlitchFx.GLOW,LILAC=GlitchFx.LILAC,WHITE=GlitchFx.WHITE,NAVY=0x1c1470;
    static final Vec3d UP=new Vec3d(0,1,0),X=new Vec3d(1,0,0),Z=new Vec3d(0,0,1),DOWN=new Vec3d(0,-1,0);
    private static double h(int j,int seed,int salt){return RainRules.h(j,seed,salt);}
    /** The world this client draws (none in the tests: the ground is then flat at the rain's middle). */
    static net.minecraft.client.world.ClientWorld world(){var mc=net.minecraft.client.MinecraftClient.getInstance();return mc==null?null:mc.world;}

    // ───────────────────────────── shapes ─────────────────────────────

    /** A ring lying on the ground at {@code g}: its band laid over what is behind it (the blended pass), or its light (a thin white line in a purple glow). */
    static void groundRing(EffectMesh m,Vec3d g,double r,double wide,float a,boolean light){groundRing(m,g,r,wide,a,light,0);}
    /** The same with {@code n} sides (0: as many as its size has it). */
    static void groundRing(EffectMesh m,Vec3d g,double r,double wide,float a,boolean light,int n){
        if(a<=0||r<=0)return;
        if(light){if(n>0)FxLib.ringPlane(m,g,X,Z,r,wide*1.5,GLOW,.42f*a,n);else FxLib.ringPlane(m,g,X,Z,r,wide*1.5,GLOW,.42f*a);}
        else{if(n>0)DanceBurst.band(m,g,X,Z,r,wide,PURPLE,.85f*a,n);else DanceBurst.band(m,g,X,Z,r,wide,PURPLE,.85f*a);}
    }
    /** A stroke lying flat on the ground from {@code a} to {@code b}, {@code wide} across. */
    static void flat(EffectMesh m,Vec3d a,Vec3d b,double wide,int rgb,float alpha){
        Vec3d d=b.subtract(a);if(d.horizontalLengthSquared()<1e-8||alpha<=0)return;
        Vec3d side=new Vec3d(-d.z,0,d.x).normalize().multiply(wide*.5);
        m.quad(a.subtract(side),a.add(side),b.add(side),b.subtract(side),rgb,alpha);
    }
    /** A dark patch lying on the ground round {@code g}, its edge ragged. */
    static void patch(EffectMesh m,Vec3d g,double r,int rgb,float alpha,int seed){
        if(alpha<=0||r<=0)return;int n=22;Vec3d last=null;
        for(int i=0;i<=n;i++){
            double a=Math.PI*2*(i%n)/n,rr=r*(.62+.38*h(i%n,seed,51));
            Vec3d p=g.add(Math.cos(a)*rr,0,Math.sin(a)*rr);
            if(last!=null){m.vertex(g,rgb,alpha);m.vertex(last,rgb,alpha*.15f);m.vertex(p,rgb,alpha*.15f);m.vertex(g,rgb,alpha);}
            last=p;
        }
    }

    // ───────────────────────────── the call ─────────────────────────────

    /** The circle in the sky the knives come out of: this high over the rain's middle (blocks: just over where they come out, their rods' ends). */
    static final double SKY_UP=RainRules.SKY+RainRules.SKY_MORE+1.2;
    /** How long the beam up into the sky lasts after the call (seconds). */
    static final float BEAM_LIFE=.45f;
    /**
     * The call (the swing's own effects): purple drawn onto the knife's blade as it is raised (motes drawn in onto it,
     * its glow coming up); as the rain is called a beam of light up off its point into the sky over where it rains, a
     * ring and a star where it meets the sky, a ring over the ground under them, pixels thrown up.
     */
    static void call(Fx c,EffectMesh m,boolean light){
        float sec=c.sec,stop=KnifeClient.until(c.cast.id());
        int seed=(int)(c.cast.start()*31+11);
        Vec3d[] k=BladeRain.knifeAt(c.pos,c.yaw,Math.min(sec,BladeRain.END_AT));Vec3d hand=k[0],dir=k[1];
        Vec3d heel=hand.add(dir.multiply(KnifeCombo.HEEL)),tip=hand.add(dir.multiply(KnifeCombo.TIP));
        // gathering onto the knife as it is raised (the hack's light coming onto it)
        if(sec<BladeRain.CALL_AT+.05f&&sec<stop){
            float u=Ease.smooth(sec,.05f,BladeRain.CALL_AT);
            if(light){m.softLine(heel,tip,.4*u,GLOW,.34f*u);m.softLine(heel,tip,.05,LILAC,.4f*u);}
            for(int j=0;j<14;j++){
                float born=.06f+.26f*(float)h(j,seed,401),life=.14f,ms=sec-born;if(ms<0||ms>=life)continue;
                Vec3d to=hand.add(dir.multiply(KnifeCombo.HEEL+(KnifeCombo.TIP-KnifeCombo.HEEL)*h(j,seed,402)));
                Vec3d off=DanceBurst.unit(j,0,seed,403).multiply(.7+.8*h(j,seed,404));
                float g=ms/life;double kk=1-g*g;Vec3d at=to.add(off.multiply(kk));
                float near=GlitchFx.near(at,c.camera);if(near<=0)continue;
                if(light)m.softTaper(at.add(off.multiply(.3*kk+.05)),at,.01,.07,j%3==0?WHITE:GLOW,.7f*near);
                else GlitchFx.square(m,at,GlitchFx.facing(c.camera,at),.032,j%2==0?PURPLE:DEEP,.9f*near);
            }
        }
        if(BladeRain.CALL_AT>stop+1e-4f)return;
        float s=sec-BladeRain.CALL_AT;if(s<0)return;
        Vec3d sky=c.origin.add(0,SKY_UP,0),from=BladeRain.knifeAt(c.pos,c.yaw,BladeRain.CALL_AT)[0].add(dir.multiply(KnifeCombo.TIP));
        // the beam: up off the point into the sky, quick, thinning out from the bottom as it goes
        if(s<BEAM_LIFE){
            float fade=Ease.smooth(s,0,.02f)*(1-Ease.smooth(s,.08f,BEAM_LIFE));
            Vec3d top=from.lerp(sky,Ease.out(s,0,.07f));Vec3d bottom=from.lerp(sky,Ease.smooth(s,.12f,BEAM_LIFE));
            if(light){m.softTaper(bottom,top,.9,.5,GLOW,.5f*fade);m.softTaper(bottom,top,.12,.08,WHITE,.85f*fade);}
            else{m.needle(bottom,top,.4,.5,DEEP,.75f*fade);m.needle(bottom,top,.26,.5,PURPLE,.9f*fade);}
        }
        // where it meets the sky: a star, and a ring running out flat under the circle
        float ss=s-.06f;
        if(ss>=0&&ss<.5f){
            float fade=1-ss/.5f;
            groundRing(m,sky.add(0,-.1,0),.6+5.5*Ease.out(ss,0,.45f),.3,fade,light);
            if(light&&ss<.14f)m.star(sky,2.4*(1-ss/.14f)+.4,LILAC,.8f*(1-ss/.14f));
            if(light&&ss<.2f)m.softDisc(sky,3.2,GLOW,.3f*(1-ss/.2f));
        }
        // under them: a ring over the ground and pixels thrown up
        if(s<.5f){
            Vec3d g=c.pos.add(0,.06,0);float fade=1-s/.5f;
            groundRing(m,g,.4+2.4*Ease.out(s,0,.4f),.26,fade,light);
            GlitchFx.pixels(m,from,UP,.5,4.5,.045,14,seed+11,s,light,c.camera);
        }
    }
    /** The call's light on the world: on the knife as it is raised, at the call along the beam and up in the sky. */
    static void callFrame(Fx c,FxFrame f){
        float sec=c.sec,stop=KnifeClient.until(c.cast.id());
        Vec3d[] k=BladeRain.knifeAt(c.pos,c.yaw,Math.min(sec,BladeRain.END_AT));
        if(sec>.05f&&sec<Math.min(stop,BladeRain.DOWN_AT))f.light(k[0].add(k[1].multiply(KnifeCombo.TIP*.6)),0x9a3cff,4,.5f*Ease.smooth(sec,.05f,BladeRain.CALL_AT));
        float s=sec-BladeRain.CALL_AT;
        if(s>=0&&s<.4f&&BladeRain.CALL_AT<=stop+1e-4f){
            float fade=1-s/.4f;
            f.light(c.pos.add(0,2.2,0),0xb060ff,12,1.2f*fade);
            f.light(c.origin.add(0,SKY_UP-1,0),0xb060ff,16,1.1f*fade);
        }
    }

    // ───────────────────────────── the circle in the sky ─────────────────────────────

    /** The circle (blocks out at its least), how quick its parts turn (degrees a second: its dashes, its ticks the other way, its star). */
    static final double CIRCLE=2.6,DASH_TURN=40,TICK_TURN=-26,STAR_TURN=12;
    /** The circle lying flat at {@code g}: two rings and a thin one out past them, dashes turning between them and ticks round the outside the other way, a star of two triangles in it turning slowly, squares of the hack lying in it. */
    static void circle(EffectMesh m,Vec3d g,float sec,float on,double scale,float beat,int seed,boolean light){
        double R=CIRCLE*scale;float a=on*(.75f+.45f*beat);if(a<=0)return;
        if(light){
            FxLib.ringPlane(m,g,X,Z,R,.26,GLOW,.5f*a);FxLib.ringPlane(m,g,X,Z,R*.72,.16,GLOW,.42f*a);FxLib.ringPlane(m,g,X,Z,R*1.1,.08,GLOW,.3f*a);
        }else{
            patch(m,g,R*.95,NAVY,.24f*on,seed);
            DanceBurst.band(m,g,X,Z,R,.17,PURPLE,.92f*a);DanceBurst.band(m,g,X,Z,R*.72,.11,DEEP,.92f*a);DanceBurst.band(m,g,X,Z,R*1.1,.05,PURPLE,.75f*a);
        }
        double dash=Math.toRadians(DASH_TURN*sec),tick=Math.toRadians(TICK_TURN*sec),star=Math.toRadians(STAR_TURN*sec);
        for(int i=0;i<24;i++){
            double a0=dash+i*Math.PI*2/24,a1=a0+Math.PI*2/24*.55,r=R*.86;
            Vec3d p0=g.add(Math.cos(a0)*r,.01,Math.sin(a0)*r),p1=g.add(Math.cos(a1)*r,.01,Math.sin(a1)*r);
            flat(m,p0,p1,light?.14:.07,light?GLOW:PURPLE,(light?.4f:.9f)*a);
        }
        for(int i=0;i<36;i++){
            double ang=tick+i*Math.PI*2/36,r0=R*1.0,r1=R*(i%3==0?1.2:1.12);
            Vec3d p0=g.add(Math.cos(ang)*r0,.01,Math.sin(ang)*r0),p1=g.add(Math.cos(ang)*r1,.01,Math.sin(ang)*r1);
            flat(m,p0,p1,light?.1:.045,light?GLOW:PURPLE,(light?.35f:.85f)*a);
        }
        for(int tri=0;tri<2;tri++)for(int i=0;i<3;i++){
            double a0=star+tri*Math.PI/3+i*Math.PI*2/3,a1=a0+Math.PI*2/3,r=R*.7;
            Vec3d p0=g.add(Math.cos(a0)*r,.012,Math.sin(a0)*r),p1=g.add(Math.cos(a1)*r,.012,Math.sin(a1)*r);
            flat(m,p0,p1,light?.12:.05,light?GLOW:DEEP,(light?.32f:.85f)*a);
        }
        // squares of the hack lying in it, flickering (a new set four times a second: it rains a long while)
        int slot=(int)Math.floor(sec*4);
        for(int j=0;j<10;j++){
            if(h(j+slot*17,seed,611)>.6)continue;
            double ang=Math.PI*2*h(j+slot*13,seed,612),r=R*(.25+.7*h(j+slot*7,seed,613)),half=(.06+.1*h(j+slot*5,seed,614))*scale;
            Vec3d p=g.add(Math.cos(ang)*r,.015,Math.sin(ang)*r);
            Vec3d u=X.multiply(half),v=Z.multiply(half);
            if(light)m.quad(p.subtract(u).subtract(v),p.add(u).subtract(v),p.add(u).add(v),p.subtract(u).add(v),GLOW,.3f*a);
            else m.quad(p.subtract(u).subtract(v),p.add(u).subtract(v),p.add(u).add(v),p.subtract(u).add(v),j%3==0?LILAC:PURPLE,.9f*a);
        }
    }
    /** How much of the circle is there {@code age} ticks after the call (cut short at {@code cut}): opening as the call goes up, before the first knife comes; closing after the last has come. */
    static float skyOn(float age,float cut){
        float end=Math.min(RainRules.OPEN+RainRules.POUR,cut);
        return Ease.smooth(age,0,RainRules.OPEN+2)*(1-Ease.smooth(age,end,end+12));
    }
    /** Its slow breath (0 to 1): the rain is long and even, no volleys to beat with. */
    static float breath(float sec){return .5f+.5f*(float)Math.sin(sec*Math.PI*2/2.4);}
    /** The circle in the sky ({@code c} its middle): the ground's circle wide as the rain, turning slowly, its light breathing as the knives come out of it. */
    static void sky(EffectMesh m,Vec3d c,float sec,float on,int seed,boolean light){
        if(on<=0)return;
        double scale=(RainRules.ZONE+.7)/CIRCLE*(.55+.45*Ease.out(on,0,1));
        float beat=.25f*breath(sec);
        circle(m,c,-sec*.6f,on,scale,beat,seed+31,light);
        if(light){FxLib.ringPlane(m,c,X,Z,(RainRules.ZONE+.7)*1.2,.3,GLOW,.16f*on);m.softDisc(c.add(0,-.3,0),3.2,GLOW,.08f*on*(.85f+.3f*breath(sec)));}
    }

    // ───────────────────────────── where it ends ─────────────────────────────

    /** The faint ring over the ground where the rain ends: this many pieces round it, each lying on the ground under it (looked for once). */
    static final int EDGE=120;
    /** The edge's ring, laid over the ground under it all the way round (a piece with no ground under it is not drawn). */
    static void edge(EffectMesh m,RainClient.Rain r,float on,boolean light){
        if(on<=0)return;
        double[] g=r.knives.edge(r);double R=RainRules.ZONE;
        for(int i=0;i<EDGE;i++){
            int j=(i+1)%EDGE;if(Double.isNaN(g[i])||Double.isNaN(g[j])||Math.abs(g[i]-g[j])>1.1)continue;
            double a0=Math.PI*2*i/EDGE,a1=Math.PI*2*(i+1)/EDGE;
            Vec3d p0=new Vec3d(r.middle.x+Math.cos(a0)*R,g[i]+.05,r.middle.z+Math.sin(a0)*R),p1=new Vec3d(r.middle.x+Math.cos(a1)*R,g[j]+.05,r.middle.z+Math.sin(a1)*R);
            if(light)flat(m,p0,p1,.34,GLOW,.2f*on);else flat(m,p0,p1,.12,PURPLE,.4f*on);
        }
    }

    // ───────────────────────────── the knives ─────────────────────────────
    // (2.132.5: falling slowly straight down out of the circle in the sky, some 350 in the air at once, for 30 seconds;
    // drawn here, flat, as 2.132.4's: what the camera cannot see is not drawn ({@link View}), what is far off is drawn
    // coarser ({@link #FAR}), and what each knife is (where it comes down, the ground there, how it is turned) is worked
    // out once ({@link Knives}))

    /** A knife of the rain is this much of the knife in the hand (2.132.2's); so its point is this far from its grip (blocks). */
    static final double SIZE=.6,TIP=ShotRules.TIP*SIZE;
    /** How big a knife is as it comes out of the sky, of its size (it grows to it as it shows). */
    static double size(float shown){return .5+.5*shown;}
    /** A knife comes out of the sky over this many ticks; one landed stands this long (ticks), gone over the last GOING of it; one falling as the rain is cut short is gone over VANISH; one that meets its own user breaks on them after BREAKS. */
    static final float IN=3,STUCK=4,GOING=2,VANISH=2,BREAKS=1;
    /** How deep a knife's point goes in where it lands: into the ground, into a body (from over them: their head and shoulders). */
    static final double GROUND_DEEP=.3,BODY_DEEP=.38;
    /** How long the streak over a falling knife is (blocks: 2.132.5, slower, shorter than 2.132.4's 2.1 to 3). */
    static final double STREAK=1.2,STREAK_MORE=.6;

    /** One knife this frame: its grip (its rod's middle), the way it points (straight down), how much of it shows, the seconds since it came, whether it falls, whether it has landed, and in a body. */
    record Pose(Vec3d grip,Vec3d dir,float shown,float lit,boolean flying,boolean in,boolean body){
        Vec3d tip(){return grip.add(dir.multiply(TIP*size(shown)));}
    }
    /**
     * A rain's knives as this client has worked them out (once each: some 350 of them a frame, both passes): where each
     * comes down from its middle, how high over it it comes from, the ground under it (NaN: none, it is not drawn), how
     * it is turned, how long its streak is; and the body each met (-1 none; the server's word, or where this client saw it
     * meet one first: then it stands in it), when (ticks after the call), where in them (over their feet as they were
     * drawn then), and whether it struck them or broke on its own user. Also the ground under the ring where it ends.
     */
    static final class Knives{
        final double[] dx=new double[RainRules.BLADES],dz=new double[RainRules.BLADES],sky=new double[RainRules.BLADES],ground=new double[RainRules.BLADES],streak=new double[RainRules.BLADES];
        final float[] roll=new float[RainRules.BLADES];
        final java.util.BitSet known=new java.util.BitSet(RainRules.BLADES);
        final int[] on=new int[RainRules.BLADES];
        final float[] at=new float[RainRules.BLADES],relX=new float[RainRules.BLADES],relY=new float[RainRules.BLADES],relZ=new float[RainRules.BLADES];
        final double[] stood=new double[RainRules.BLADES*3];
        final java.util.BitSet harmless=new java.util.BitSet(RainRules.BLADES),told=new java.util.BitSet(RainRules.BLADES);
        double[] edge;
        Knives(){java.util.Arrays.fill(on,-1);}
        /** Knife {@code k} of {@code r} worked out: where it comes down, how high from, the ground there, how it is turned. */
        void learn(RainClient.Rain r,int k){
            double[] s=RainRules.spot(k,r.seed);dx[k]=s[0];dz[k]=s[1];sky[k]=RainRules.sky(k,r.seed);
            var w=world();
            ground[k]=w==null?r.middle.y:RainGround.at(w,r.middle.x+s[0],r.middle.z+s[1],r.middle.y+sky[k],r.middle.y-RainRules.DEEP);
            roll[k]=(float)(h(k,r.seed,8)*360);streak[k]=STREAK+STREAK_MORE*h(k,r.seed,51);
            known.set(k);
        }
        /** The ground under the ring where it ends, all the way round (looked for once). */
        double[] edge(RainClient.Rain r){
            if(edge!=null)return edge;
            var w=world();edge=new double[EDGE];
            for(int i=0;i<EDGE;i++){
                double a=Math.PI*2*i/EDGE;
                edge[i]=w==null?r.middle.y:RainGround.at(w,r.middle.x+Math.cos(a)*RainRules.ZONE,r.middle.z+Math.sin(a)*RainRules.ZONE,r.middle.y+SKY_UP-.5,r.middle.y-RainRules.DEEP);
            }
            return edge;
        }
        /** Knife {@code k} stands in {@code e} from {@code age} (ticks after the call), its point {@code y} over their feet as they are drawn ({@code feet}); harmless on its own user. */
        void meet(int k,LivingEntity e,Vec3d feet,double x,double y,double z,float age,boolean harmless){
            on[k]=e.getId();at[k]=age;relX[k]=(float)(x-feet.x);relY[k]=(float)Math.max(.1,y);relZ[k]=(float)(z-feet.z);
            stood[k*3]=feet.x;stood[k*3+1]=feet.y;stood[k*3+2]=feet.z;
            if(harmless)this.harmless.set(k);else this.harmless.clear(k);
        }
    }

    /**
     * What the camera sees this frame (2.132.4): its eye, the ways it looks (ahead, up, left: unit), and how far the sides
     * of its sight go out ahead (the tangents of half of it across and up, taken a little wider than the projection's:
     * the camera shakes).
     */
    static final class View{
        final Vec3d eye,ahead,up,left;final double tanX,tanY,secX,secY;
        View(Vec3d eye,Vec3d ahead,Vec3d up,double tanX,double tanY){
            this.eye=eye;this.ahead=ahead;this.up=up;this.left=up.crossProduct(ahead).normalize();
            this.tanX=tanX;this.tanY=tanY;secX=Math.sqrt(1+tanX*tanX);secY=Math.sqrt(1+tanY*tanY);
        }
        /** Whether anything within {@code r} of {@code p} may be seen. */
        boolean sees(Vec3d p,double r){
            double dx=p.x-eye.x,dy=p.y-eye.y,dz=p.z-eye.z,z=dx*ahead.x+dy*ahead.y+dz*ahead.z;
            if(z<-r)return false;
            double x=Math.abs(dx*left.x+dy*left.y+dz*left.z),y=Math.abs(dx*up.x+dy*up.y+dz*up.z);
            return x<=z*tanX+r*secX&&y<=z*tanY+r*secY;
        }
        /** Whether anything within {@code r} of the line from {@code a} to {@code b} may be seen (its ends and middle, each a quarter of its length wider). */
        boolean sees(Vec3d a,Vec3d b,double r){
            double q=a.distanceTo(b)*.25+r;
            return sees(a,q)||sees(b,q)||sees(new Vec3d((a.x+b.x)*.5,(a.y+b.y)*.5,(a.z+b.z)*.5),q);
        }
    }
    /** How much wider than the projection's the sight is taken to be (its tangents: the camera's shake and bob). */
    static final double WIDER=1.15;
    private static Fx viewFor;private static View viewNow;
    /** What the camera sees as {@code c} is drawn (once a frame), or null if that cannot be told: then all of it is drawn. */
    static View view(Fx c){
        if(viewFor==c)return viewNow;
        viewFor=c;viewNow=null;
        try{
            var cam=c.mc.gameRenderer.getCamera();var f=cam.getHorizontalPlane();var u=cam.getVerticalPlane();
            var proj=com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix();
            double tx=1/proj.m00(),ty=1/proj.m11();
            Vec3d ahead=new Vec3d(f.x(),f.y(),f.z()),up=new Vec3d(u.x(),u.y(),u.z());
            if(tx>0&&ty>0&&tx<40&&ty<40&&ahead.lengthSquared()>.5&&up.lengthSquared()>.5)viewNow=new View(c.camera,ahead.normalize(),up.normalize(),tx*WIDER,ty*WIDER);
        }catch(RuntimeException ex){viewNow=null;}
        return viewNow;
    }

    /** The rim round a knife of the rain, and its glow's reach past it, this many times the knife in the hand's (it is smaller and seen far off). */
    static final double RIM_MORE=2.2,GLOW_MORE=1.6;
    /**
     * The outline a knife of the rain is drawn round: the knife's own (HackKnife), with ten points along its edge (2.132.2),
     * one far off ({@link #FAR}) with three, and further still with two (2.132.4).
     */
    static final double[][] OUTLINE=HackKnife.outline(10),MITER=HackKnife.miters(OUTLINE),OUTLINE_FAR=HackKnife.outline(3),MITER_FAR=HackKnife.miters(OUTLINE_FAR),
        OUTLINE_FARTHEST=HackKnife.outline(2),MITER_FARTHEST=HackKnife.miters(OUTLINE_FARTHEST);
    /**
     * How far from the camera a knife is drawn coarser (blocks: fewer points, one band of rim, only the one of its two
     * planes more across the camera's sight, its glow laid along it), and coarser still; how far a ring where one lands
     * has fewer sides, and how far one landing is drawn at all.
     */
    static final double FAR=14,FARTHEST=24,LANDS_SEEN=22;
    /** Over how far either side of FAR a knife is drawn both ways, the near way going as the far way comes (blocks). */
    static final double SWAP=1;
    /** How many sides the ring is where a knife lands on the ground, and far off. */
    static final int LAND_SIDES=12,LAND_SIDES_FAR=8;
    /** How far round a knife's line what is drawn of it may reach (blocks: its width, its rim and glow), and round where one lands. */
    static final double KNIFE_REACH=.75,LAND_REACH=1.2;
    /** How much of a knife shows by the camera: none within NEAR0 of its line (its point to the top of its streak), all of it from NEAR1 on. */
    static final double NEAR0=2.0,NEAR1=5.5;
    static float near(Vec3d tip,Vec3d top,Vec3d camera){
        Vec3d d=top.subtract(tip);double l=d.lengthSquared(),u=l<1e-9?0:Math.max(0,Math.min(1,camera.subtract(tip).dotProduct(d)/l));
        double far=camera.distanceTo(tip.add(d.multiply(u)));
        return Ease.smooth((float)far,(float)NEAR0,(float)NEAR1);
    }
    /** The points of up to three rings round a knife's outline in the world, worked out into these and not into new vectors. */
    private static final int MOST=Math.max(OUTLINE.length,Math.max(OUTLINE_FAR.length,OUTLINE_FARTHEST.length));
    private static final double[][] RX=new double[3][MOST],RY=new double[3][MOST],RZ=new double[3][MOST];
    /** Ring {@code slot}: every point of the outline (model units: across {@code x}, along {@code y}, from the grip at gx, gy, gz) in the world, pushed out {@code out} units along its miter. */
    private static void ringInto(int slot,double gx,double gy,double gz,Vec3d x,Vec3d y,double[][] o,double[][] mi,double out,double unit){
        double[] px=RX[slot],py=RY[slot],pz=RZ[slot];
        for(int i=0;i<o.length;i++){
            double qx=(o[i][0]+mi[i][0]*out)*unit,qy=(o[i][1]+mi[i][1]*out)*unit;
            px[i]=gx+x.x*qx+y.x*qy;py[i]=gy+x.y*qx+y.y*qy;pz[i]=gz+x.z*qx+y.z*qy;
        }
    }
    private static void v(EffectMesh m,int slot,int i,int rgb,int alpha){m.vertex(RX[slot][i],RY[slot][i],RZ[slot][i],rgb,alpha);}
    /** A band between rings {@code in} and {@code out} all the way round ({@code n} points). */
    private static void band(EffectMesh m,int in,int out,int n,int rgb,int ain,int aout){
        for(int i=0;i<n;i++){int j=i+1==n?0:i+1;v(m,in,i,rgb,ain);v(m,in,j,rgb,ain);v(m,out,j,rgb,aout);v(m,out,i,rgb,aout);}
    }
    /**
     * A knife of the rain as the hack lights it, drawn flat: its outline (HackKnife.OUTLINE, the model's measures) filled
     * white with a crisp purple rim (the blended pass), or a purple glow round it (the light); on two planes across each
     * other along it, so that it shows from every side. One far off ({@code lod} 1, past FAR; 2, past FARTHEST) has
     * fewer points and one band of rim, is drawn on only the one of its planes more across the sight of the camera at
     * {@code camera}, and its glow is laid along it ({@link #farGlow}), not round its outline.
     */
    static void blade(EffectMesh m,Pose p,float roll,float a,boolean light,int lod,Vec3d camera){
        if(a<=0)return;
        double unit=ShotRules.SCALE*SIZE*size(p.shown())/16;
        Vec3d y=p.dir(),sp=BladeShot.spine(y,roll),across=y.crossProduct(sp).normalize(),grip=p.grip();
        boolean coarse=lod>0;
        if(light&&coarse){farGlow(m,p,unit,a);return;}
        double gx=grip.x,gy=grip.y,gz=grip.z;
        double[][] o=lod==0?OUTLINE:lod==1?OUTLINE_FAR:OUTLINE_FARTHEST,mi=lod==0?MITER:lod==1?MITER_FAR:MITER_FARTHEST;int n=o.length;
        int first=0,last=1;
        if(coarse){double vx=camera.x-gx,vy=camera.y-gy,vz=camera.z-gz;if(Math.abs(vx*across.x+vy*across.y+vz*across.z)>=Math.abs(vx*sp.x+vy*sp.y+vz*sp.z))last=0;else first=1;}
        for(int plane=first;plane<=last;plane++){
            Vec3d x=plane==0?sp:across;
            if(light){
                double r0=HackKnife.RIM*RIM_MORE,r1=r0+HackKnife.GLOW_REACH*GLOW_MORE;
                ringInto(0,gx,gy,gz,x,y,o,mi,r0,unit);ringInto(1,gx,gy,gz,x,y,o,mi,r1,unit);
                band(m,0,1,n,GLOW,EffectMesh.alpha(.5f*a),0);
                continue;
            }
            // the white: the blade (a fan from inside it: it is convex, from the rod's top round the point to the heel) and the rod
            ringInto(0,gx,gy,gz,x,y,o,mi,0,unit);
            double qx=(HackKnife.SPINE_X-HackKnife.DEPTH*.45)*unit,qy=(HackKnife.Y_HEEL+HackKnife.BLADE*.3)*unit;
            double mx=gx+x.x*qx+y.x*qy,my=gy+x.y*qx+y.y*qy,mz=gz+x.z*qx+y.z*qy;
            int white=EffectMesh.alpha(.97f*a),rim=EffectMesh.alpha(a);
            for(int i=1;i<n-2;i++){m.vertex(mx,my,mz,WHITE,white);v(m,0,i,WHITE,white);v(m,0,i+1,WHITE,white);m.vertex(mx,my,mz,WHITE,white);}
            v(m,0,0,WHITE,white);v(m,0,1,WHITE,white);v(m,0,n-2,WHITE,white);v(m,0,n-1,WHITE,white);
            // the rim: lilac by the knife, then vivid purple (far off, the vivid all of it)
            ringInto(2,gx,gy,gz,x,y,o,mi,HackKnife.RIM*RIM_MORE,unit);
            if(coarse){band(m,0,2,n,HackKnife.VIVID,rim,rim);continue;}
            ringInto(1,gx,gy,gz,x,y,o,mi,HackKnife.RIM_LILAC*RIM_MORE,unit);
            band(m,0,1,n,HackKnife.LILAC,rim,rim);band(m,1,2,n,HackKnife.VIVID,rim,rim);
        }
    }
    /** How wide the glow laid along a knife far off is at its heel and at its point (blocks). */
    static final double FAR_GLOW_HEEL=.72,FAR_GLOW_TIP=.38;
    /** A knife far off: its glow laid along it from its rod's end to its point, facing the camera. */
    static void farGlow(EffectMesh m,Pose p,double unit,float a){
        Vec3d end=p.grip().subtract(p.dir().multiply(HackKnife.Y_HEEL*unit)),tip=p.tip();
        m.softTaper(end,tip,FAR_GLOW_HEEL,FAR_GLOW_TIP,GLOW,.42f*a);
    }
    /** The knives drawn and how long they took (both passes) the last whole frame, for the capture tools. */
    private static Fx counting;private static int drawing,drawnLast;private static long nanos,microsLast;
    private static void count(Fx c,int drawn,long ns,boolean light){
        if(counting!=c){if(counting!=null){drawnLast=drawing;microsLast=nanos/1000;}counting=c;drawing=0;nanos=0;}
        if(!light)drawing=drawn;nanos+=ns;
    }
    static int drawnLast(){return drawnLast;}
    static long microsLast(){return microsLast;}

    /** The bodies a rain's knives may meet this frame (looked for once a frame, for both passes): alive, to be struck, under the rain. */
    private static Fx bodiesFor;private static java.util.List<LivingEntity> bodiesNow=java.util.List.of();
    static java.util.List<LivingEntity> bodies(Fx c,RainClient.Rain r){
        if(bodiesFor==c)return bodiesNow;
        bodiesFor=c;var m=r.middle;
        bodiesNow=c.mc.world==null?java.util.List.of():c.mc.world.getEntitiesByClass(LivingEntity.class,
            new net.minecraft.util.math.Box(m.x-RainRules.ZONE-1,m.y-RainRules.DEEP-3,m.z-RainRules.ZONE-1,m.x+RainRules.ZONE+1,m.y+SKY_UP+1,m.z+RainRules.ZONE+1),
            e->e.isAlive()&&!e.isSpectator()&&e.canHit()&&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity));
        return bodiesNow;
    }
    /**
     * Where knife {@code k} of {@code r} is {@code age} ticks after the call (the rain cut short at {@code cut}), or null
     * while there is nothing of it to draw: falling straight down out of the sky; in the ground where it lands; in a body
     * it met (the server's word of whom, or the first this client sees it meet, {@code bodies}), drawn where that one is
     * drawn now.
     */
    static Pose pose(RainClient.Rain r,int k,float age,float cut,java.util.List<LivingEntity> bodies,float delta){
        var kn=r.knives;double comes=RainRules.comes(k);
        if(age<comes||comes>=cut)return null;
        double ground=kn.ground[k];if(Double.isNaN(ground))return null;
        Vec3d m=r.middle;double x=m.x+kn.dx[k],z=m.z+kn.dz[k],sky=m.y+kn.sky[k];
        var w=world();
        // the server's word of a body it met, if this client had it meet none (or another)
        float[] told=r.told.get(k);
        if(told!=null&&!kn.told.get(k)){
            kn.told.set(k);
            if(w!=null&&w.getEntityById((int)told[0]) instanceof LivingEntity e){
                // it did no harm (its own user, one it may not harm, one no harm takes): it breaks on them harmlessly
                if(kn.on[k]!=e.getId()){
                    Vec3d feet=e.getLerpedPos(delta);double hy=feet.y+told[1];
                    kn.meet(k,e,feet,x,told[1]-BODY_DEEP,z,(float)Math.min(age,comes+(sky-hy)/RainRules.SPEED),told[2]<=0);
                }else if(told[2]<=0)kn.harmless.set(k);else kn.harmless.clear(k);
            }
        }
        float t=Math.min(age,cut);
        // standing in a body it met: where that one is drawn now (where they were if they are gone)
        if(kn.on[k]>=0){
            float since=age-kn.at[k];
            boolean harmless=kn.harmless.get(k);float stays=harmless?BREAKS:STUCK;
            if(since>=stays)return null;
            Vec3d feet=w!=null&&w.getEntityById(kn.on[k]) instanceof LivingEntity e?e.getLerpedPos(delta):new Vec3d(kn.stood[k*3],kn.stood[k*3+1],kn.stood[k*3+2]);
            Vec3d tip=feet.add(kn.relX[k],kn.relY[k],kn.relZ[k]);
            float shown=Ease.smooth((float)(kn.at[k]-comes),0,IN)*(1-Ease.smooth(since,stays-(harmless?.5f:GOING),stays));
            if(shown<=0)return null;
            Vec3d dir=shiver(k,since);
            return new Pose(tip.subtract(dir.multiply(TIP*size(shown))),dir,shown,(float)((kn.at[k]-comes)/20),false,true,true);
        }
        double land=ground-GROUND_DEEP;
        double lands=comes+(sky-land)/RainRules.SPEED;
        boolean cutShort=lands>cut;
        if(cutShort&&age>=cut+VANISH||!cutShort&&age>=lands+STUCK)return null;
        double y=Math.max(land,sky-(t-comes)*RainRules.SPEED);
        // falling: does it meet a body this client sees (and the server has not said otherwise)?
        if(y>land&&told==null&&age<cut&&!bodies.isEmpty()){
            double from=sky-Math.max(0,t-1-comes)*RainRules.SPEED;
            for(var e:bodies){
                var b=e.getBoundingBox();Vec3d feet=e.getLerpedPos(delta);
                double ox=feet.x-e.getX(),oy=feet.y-e.getY(),oz=feet.z-e.getZ();
                if(!RainRules.over(x,z,b.minX+ox,b.maxX+ox,b.minZ+oz,b.maxZ+oz))continue;
                double hit=RainRules.meets(from,y,b.minY+oy,b.maxY+oy,ground);
                if(Double.isNaN(hit))continue;
                kn.meet(k,e,feet,x,hit-feet.y-BODY_DEEP,z,(float)Math.min(age,comes+(sky-hit)/RainRules.SPEED),e.getUuid().equals(r.owner));
                return pose(r,k,age,cut,java.util.List.of(),delta);
            }
        }
        boolean in=!cutShort&&t>=lands;
        float shown=Ease.smooth((float)(t-comes),0,IN)*(cutShort?1-Ease.smooth(age,cut,cut+VANISH):1-Ease.smooth(age,(float)lands+STUCK-GOING,(float)lands+STUCK));
        if(shown<=0)return null;
        Vec3d dir=in?shiver(k,(float)(age-lands)):DOWN;
        Vec3d tip=new Vec3d(x,in?land:y,z);
        return new Pose(tip.subtract(dir.multiply(TIP*size(shown))),dir,shown,(float)((t-comes)/20),!in,in,false);
    }
    /** A knife in where it landed shivers a moment ({@code since} ticks after). */
    static Vec3d shiver(int k,float since){
        double sec=Math.max(0,since)/20.0,a=k*1.7;
        return DOWN.add(new Vec3d(Math.cos(a),0,Math.sin(a)).multiply(.06*Math.exp(-sec/.05)*Math.sin(sec*95))).normalize();
    }
    /**
     * Each knife of the rain: coming out of the sky in blocks of purple with a star, falling straight down with a short
     * streak over it, standing where it lands (in the ground, or in a body), broken up into pixels as it goes; where it
     * lands on the ground a ring and pixels splashing up, in a body a star, a ring, spikes thrown up and pixels, on its own
     * user a small ring it breaks on. What the camera cannot see is not drawn; what is far off is drawn coarser and throws
     * up only its ring, and none past LANDS_SEEN.
     */
    static void knives(Fx c,EffectMesh m,boolean light,RainClient.Rain r){
        long t0=System.nanoTime();int drawn=0;
        float age=c.age,cut=r.cut;var kn=r.knives;View view=view(c);var bodies=bodies(c,r);
        int[] w=RainRules.window(Math.min(age,cut),RainRules.LAST-RainRules.POUR);
        for(int k=w[0];k<w[1];k++){
            double comes=RainRules.comes(k);
            if(age<comes)break;
            if(!kn.known.get(k))kn.learn(r,k);
            var p=pose(r,k,age,cut,bodies,c.delta);
            if(p!=null){
                Vec3d tip=p.tip(),top=p.grip().add(0,p.flying()?kn.streak[k]:0,0);
                if(view==null||view.sees(tip,top,KNIFE_REACH)){
                    float near=near(tip,top,c.camera)*p.shown();
                    if(near>0){
                        drawn++;
                        double far=Math.sqrt(c.camera.squaredDistanceTo(p.grip()));boolean coarse=far>FAR;
                        float fine=1-Ease.smooth((float)far,(float)FAR-2,(float)FAR),blend=Ease.smooth((float)far,(float)(FAR-SWAP),(float)(FAR+SWAP));
                        if(blend<1)blade(m,p,kn.roll[k],near*(1-blend),light,0,c.camera);
                        if(blend>0)blade(m,p,kn.roll[k],near*blend,light,far>FARTHEST?2:1,c.camera);
                        Vec3d mid=p.grip().add(p.dir().multiply(TIP*.5));
                        if(light){
                            // a short streak over it as it falls, a star as it comes out of the sky, aglow where it stands
                            if(p.flying()){m.softTaper(top,tip,.03,.3,GLOW,.36f*near);if(fine>0)m.softTaper(top,p.grip(),.01,.07,WHITE,.5f*near*fine);}
                            if(p.lit()<.15f){float a=1-p.lit()/.15f;m.star(mid,.5*a+.12,LILAC,.8f*a*near);}
                            if(p.in()&&fine>0)m.softDisc(mid,.3,GLOW,.2f*near*fine);
                        }
                        // blocks of purple breaking over it as it comes out of the sky (the hack's own coming on)
                        if(p.lit()<.15f&&!coarse){
                            int slot=(int)Math.floor(age/20f*16);var f=GlitchFx.facing(c.camera,p.grip());
                            for(int j=0;j<4;j++){
                                if(GlitchFx.h(j,slot,r.seed+k,31)>.6)continue;
                                Vec3d at=p.grip().add(p.dir().multiply(TIP*GlitchFx.h(j,slot,r.seed+k,32))).add(f[0].multiply((GlitchFx.h(j,slot,r.seed+k,33)-.5)*.42)).add(f[1].multiply((GlitchFx.h(j,slot,r.seed+k,34)-.5)*.42));
                                double sq=.035+.055*GlitchFx.h(j,slot,r.seed+k,35);
                                if(light)GlitchFx.square(m,at,f,sq*1.9,GLOW,.3f*near);
                                else{GlitchFx.square(m,at,f,sq+.012,INK,.85f*near);GlitchFx.square(m,at,f,sq,GlitchFx.h(j,slot,r.seed+k,36)<.6?PURPLE:DEEP,.95f*near);}
                            }
                        }
                    }
                }
            }
            landed(c,m,light,r,k,age,cut,view);
        }
        count(c,drawn,System.nanoTime()-t0,light);
    }
    /** What knife {@code k} throws up where it lands: in a body, on its own user, or in the ground (a moment after it). */
    static void landed(Fx c,EffectMesh m,boolean light,RainClient.Rain r,int k,float age,float cut,View view){
        var kn=r.knives;double ground=kn.ground[k];if(Double.isNaN(ground))return;
        if(kn.on[k]>=0){
            float s=(age-kn.at[k])/20f;if(s<0||s>=.36f||kn.at[k]>cut)return;
            var w=c.mc.world;
            Vec3d feet=w!=null&&w.getEntityById(kn.on[k]) instanceof LivingEntity e?e.getLerpedPos(c.delta):new Vec3d(kn.stood[k*3],kn.stood[k*3+1],kn.stood[k*3+2]);
            Vec3d at=feet.add(kn.relX[k],kn.relY[k]+BODY_DEEP,kn.relZ[k]);
            if(view!=null&&!view.sees(at,LAND_REACH*1.5))return;
            float near=GlitchFx.near(at,c.camera);if(near<=0)return;
            if(kn.harmless.get(k)){
                // on its own user: broken on a small ring over their head, harmlessly
                if(s<.2f){float a=(1-s/.2f)*near;double rr=.12+.45*Ease.out(s,0,.15f);
                    if(light)FxLib.ringPlane(m,at.add(0,.15,0),X,Z,rr,.06,GLOW,.45f*a);else DanceBurst.band(m,at.add(0,.15,0),X,Z,rr,.05,LILAC,.7f*a);}
                GlitchFx.pixels(m,at.add(0,.1,0),UP,.9,2.4,.032,5,r.seed+k*7,s,light,c.camera);
                return;
            }
            // in them: a star and a glow, a ring round them, spikes thrown up, pixels
            if(light&&s<.1f){float a=1-s/.1f;m.star(at,.5*a+.1,LILAC,.85f*a*near);m.softDisc(at,.45,GLOW,.36f*a*near);}
            if(s<.18f){float a=(1-s/.18f)*near;double rr=.15+.6*Ease.out(s,0,.16f);
                if(light)FxLib.ringPlane(m,at,X,Z,rr,.08,GLOW,.55f*a);else DanceBurst.band(m,at,X,Z,rr,.07,PURPLE,.85f*a);}
            if(s<.16f)for(int j=0;j<3;j++){
                Vec3d dir=UP.add(DanceBurst.unit(k,j,r.seed,41).multiply(.7)).normalize();
                double len=(.45+.55*h(k*3+j,r.seed,42))*Ease.out(s,0,.05f);float a=(1-Ease.smooth(s,.04f,.16f))*near;
                Vec3d a0=at.add(dir.multiply(.05)),a1=at.add(dir.multiply(.05+len));
                if(light)m.needle(a0,a1,.1,.25,GLOW,.25f*a);else m.needle(a0,a1,.065,.25,j==0?INK:j==1?DEEP:PURPLE,.92f*a);
            }
            GlitchFx.pixels(m,at,UP,.7,3.8,.04,5,r.seed+k*7,s,light,c.camera);
            return;
        }
        Vec3d mid=r.middle;double sky=mid.y+kn.sky[k],land=ground-GROUND_DEEP,lands=RainRules.comes(k)+(sky-land)/RainRules.SPEED;
        if(lands>cut)return;
        float s=(float)((age-lands)/20);
        if(s>=0&&s<.3f){
            // in the ground: a ring running out and pixels splashing up (far off its ring only, fewer sided, and none past LANDS_SEEN)
            Vec3d at=new Vec3d(mid.x+kn.dx[k],ground+.05,mid.z+kn.dz[k]);double d2=c.camera.squaredDistanceTo(at);
            if(d2<LANDS_SEEN*LANDS_SEEN&&(view==null||view.sees(at,LAND_REACH))){
                boolean coarse=d2>FAR*FAR;float near=GlitchFx.near(at,c.camera),a=(1-s/.3f)*near;
                groundRing(m,at,.08+.62*Ease.out(s,0,.22f),.12,a,light,coarse?LAND_SIDES_FAR:LAND_SIDES);
                if(!coarse){
                    if(light&&s<.06f)m.star(at.add(0,.1,0),.34,LILAC,.55f*(1-s/.06f)*near);
                    GlitchFx.pixels(m,at.add(0,.06,0),UP,.85,2.8,.033,3,r.seed+k*7,s,light,c.camera);
                }
            }
        }
        // going: broken up into pixels where it stood (near enough to be seen)
        float gs=(float)((age-(lands+STUCK-GOING))/20);
        if(gs>=0&&gs<.35f){
            Vec3d stood=new Vec3d(mid.x+kn.dx[k],land+TIP*.6,mid.z+kn.dz[k]);
            if(c.camera.squaredDistanceTo(stood)<FAR*FAR&&(view==null||view.sees(stood,LAND_REACH)))GlitchFx.pixels(m,stood,UP,.9,2.2,.032,4,r.seed+k*11+5,gs,light,c.camera);
        }
    }

    // ───────────────────────────── the rain's passes ─────────────────────────────

    /** How much of the ring where it ends is there {@code age} ticks after the call: as the circle opens, gone after the last knife lands. */
    static float edgeOn(float age,float cut){
        float end=Math.min(RainRules.END,cut);
        return Ease.smooth(age,2,RainRules.OPEN+10)*(1-Ease.smooth(age,end,end+14));
    }
    /** Everything of the rain {@code c} (RainZone's cast): the circle in the sky, the ring where it ends, the knives (the blended bodies, or the light). */
    static void zone(Fx c,EffectMesh m,boolean light){
        var r=RainClient.of(c.cast);if(r==null)return;
        float age=c.age,cut=r.cut;
        sky(m,r.middle.add(0,SKY_UP,0),c.sec,skyOn(age,cut),r.seed,light);
        edge(m,r,edgeOn(age,cut),light);
        knives(c,m,light,r);
    }
    /** The rain's light on the world: the circle in the sky aglow, a little light where a knife meets a body; and the screen inside it. */
    static void zoneFrame(Fx c,FxFrame f){
        var r=RainClient.of(c.cast);if(r==null)return;
        float age=c.age,cut=r.cut,on=skyOn(age,cut);
        if(on>0)f.light(r.middle.add(0,SKY_UP-1.5,0),0x9a3cff,24,.3f*on*(.85f+.3f*breath(c.sec)));
        var kn=r.knives;int[] w=RainRules.window(Math.min(age,cut),RainRules.LAST-RainRules.POUR);int lit=0;
        for(int k=w[0];k<w[1]&&lit<6;k++){
            if(kn.on[k]<0||kn.harmless.get(k))continue;
            float s=(age-kn.at[k])/20f;if(s<0||s>=.18f)continue;
            Vec3d feet=c.mc.world!=null&&c.mc.world.getEntityById(kn.on[k]) instanceof LivingEntity e?e.getLerpedPos(c.delta):new Vec3d(kn.stood[k*3],kn.stood[k*3+1],kn.stood[k*3+2]);
            f.light(feet.add(kn.relX[k],kn.relY[k],kn.relZ[k]),0xb060ff,4,.8f*(1-s/.18f));lit++;
        }
        screen(c,f,r,age,cut);
    }

    // ───────────────────────────── the screen inside the rain ─────────────────────────────
    // (2.132.5, the user's choice: "은은하게 30초 내내": a violet air and thin light falling down it, gently, all the while
    // it rains; 2.132.1 to 2.132.4's tearing, beating and impact frames are gone)

    /** How strong the screen inside it is at most (FxFrame.rain: 1 is the most it has). */
    static final float GENTLE=1f;
    /**
     * How much one standing at {@code viewer} (their feet) is inside the rain {@code r} {@code age} ticks after its call
     * (cut short at {@code cut}): within it (ZONE of its middle, softly over its last block and a half) and under its
     * circle; from as the first knives come out of the sky until a little after the last have landed. It is the body the
     * screen sees from that is measured, not where its camera is (as Lee So-sang's H's night, 2.99.1).
     */
    static float inside(Vec3d viewer,Vec3d middle,float age,float cut){
        float end=Math.min(RainRules.END,cut);
        float on=Ease.smooth(age,RainRules.OPEN,RainRules.OPEN+12)*(1-Ease.smooth(age,end,end+16));
        if(on<=0)return 0;
        float r=(float)Math.hypot(viewer.x-middle.x,viewer.z-middle.z),up=(float)(viewer.y-middle.y);
        float in=1-Ease.smooth(r,(float)RainRules.ZONE-1.5f,(float)RainRules.ZONE+.5f);
        float under=1-Ease.smooth(up,(float)SKY_UP-1,(float)SKY_UP+1);
        float over=Ease.smooth(up,(float)-RainRules.DEEP-1,(float)-RainRules.DEEP+1);
        return on*in*under*over;
    }
    /** The screen inside the rain, gently (FxFrame.rain, the effect layer's composite): a violet air, its edges a little darker, thin light falling down it. */
    static void screen(Fx c,FxFrame f,RainClient.Rain r,float age,float cut){
        var body=c.mc.getCameraEntity();
        float in=inside(body!=null?body.getLerpedPos(c.delta):c.camera,r.middle,age,cut);
        if(in>0)f.rain(in*GENTLE);
    }

    /** For the checks: how many of {@code r}'s knives this client has standing in a body now, and how many of them the server told of. */
    static int[] standing(RainClient.Rain r,float age){
        var kn=r.knives;int[] w=RainRules.window(Math.min(age,r.cut),RainRules.LAST-RainRules.POUR);int in=0,told=0;
        for(int k=w[0];k<w[1];k++){if(kn.on[k]>=0&&age-kn.at[k]<STUCK){in++;if(r.told.containsKey(k))told++;}}
        return new int[]{in,told};
    }
    /** For the checks: how many of {@code r}'s knives are falling or standing at {@code age}. */
    static int shown(RainClient.Rain r,float age){
        int n=0;int[] w=RainRules.window(Math.min(age,r.cut),RainRules.LAST-RainRules.POUR);
        for(int k=w[0];k<w[1];k++){
            double comes=RainRules.comes(k);if(age<comes)break;
            if(!r.knives.known.get(k))r.knives.learn(r,k);
            if(pose(r,k,age,r.cut,java.util.List.of(),0)!=null)n++;
        }
        return n;
    }
}
