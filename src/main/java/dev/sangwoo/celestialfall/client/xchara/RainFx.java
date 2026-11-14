package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxSolids;
import dev.sangwoo.celestialfall.xchara.RainRules;
import dev.sangwoo.celestialfall.xchara.ShotRules;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;

/**
 * Everything the effects' layer adds to X Chara's rain of knives (H with their hack off), made much stronger (2.132.1,
 * user requests: "이펙트를 전부 대폭 강화해줘": every part of 2.132.0's bigger, brighter and much more of it, and more
 * added to every part; "칼이 더 많이 떨어지게 바꾸고 적 방향으로 떨어지게 하지 말고 비 처럼 일자로 떨어지게"; "비가 떨어지는
 * 범위를 넓게 바꾸고 검이 떨어지는 비 내부에는 엄청난 화면효과를 넣어줘"). What it is drawn over (the body, the knife in
 * the hand) is BladeRain's; the knives of the rain are drawn here, flat. X Chara's purple is all of it; white is in
 * thin cores, flashes, stars and the knives themselves; the world's ground answers it, drawn only (no block changes).
 * <ul>
 * <li>The run ({@link #run}): a ring over the ground and streaks thrown back where they spring off, a ring and pixels
 * where each foot comes down, a wake of purple streaks left hanging behind them, a purple wind round the body and a
 * glint trailing the knife's point; braking to a stop with no one met, a skid and a ring.</li>
 * <li>The stab ({@link #stabbed}): purple gathering onto the knife as it comes round to the hip ({@link #charge}); as
 * it goes in, a lance of light along the knife out through their back, the knife's own burst on them half again as
 * big, an eruption out of their back along the knife (the sword dance's, X Chara's own: DanceBurst), rings racing out
 * over the ground, the ground darkened under them, pixels thrown.</li>
 * <li>Borne on through the rain ({@link #held}): a circle of purple on the ground under them (rings, dashes and ticks
 * turning, a star in it, squares of the hack rising out of it), purple spiralling up round them, lightning crackling
 * along the knife and into them, both hands aglow, all of it beating with the volleys.</li>
 * <li>The rain ({@link #sky}, {@link #knives}; 2.132.4: over the ground 30 blocks round): the ground's circle again in
 * the sky over them, wide as the rain, turning the other way; out of it the knives (BladeRain.spot, BladeRain.pose)
 * fall straight down, white with a crisp purple rim and a glow, a long streak over each as it falls, coming out of the
 * sky in blocks of purple and a star; in the ground a ring and pixels splashing up, on them a star, a ring, spikes
 * thrown up and pixels; broken up into pixels as each goes (what the camera cannot see is not drawn, what is far off
 * is drawn coarser). Every few volleys the knife's own burst on them ({@link #bursts}).</li>
 * <li>The screen inside the rain ({@link #screen}): for whoever stands in it, violet, its edges dark, torn in slices,
 * streaked with light falling down it, scanned and beating with the volleys (FxFrame.rain, the layer's composite);
 * its colours split; an impact frame with each burst and a full one with the last volley. The one place in X Chara's
 * skills with a screen effect, asked for.</li>
 * <li>The last volley ({@link #last}): the knife's own burst twice as big, everything bursting out of their whole body
 * at once (the sword dance's end), a pillar of light up out of them, three rings racing out over the ground, the
 * floor heaved up round them and rippling out ({@link #solids}), pixels everywhere.</li>
 * <li>Drawn out ({@link #withdrawn}): a spurt of purple out of them back along the knife, a ring where it comes out.</li>
 * </ul>
 * Every part of it is a function of the skills' own clocks, what the server told and a seed, the same on every client.
 */
final class RainFx {
    private RainFx(){}
    static final int INK=GlitchFx.INK,DEEP=GlitchFx.DEEP,PURPLE=GlitchFx.PURPLE,GLOW=GlitchFx.GLOW,LILAC=GlitchFx.LILAC,WHITE=GlitchFx.WHITE,NAVY=0x1c1470,NEON=0xc45cff;
    static final Vec3d UP=new Vec3d(0,1,0),X=new Vec3d(1,0,0),Z=new Vec3d(0,0,1);
    static final float STAB_AT=BladeRain.STAB_AT,LAST_AT=BladeRain.LAST_AT,OUT_AT=BladeRain.OUT_AT;
    private static double h(int j,int seed,int salt){return GlitchFx.h(j,0,seed,salt);}

    // ───────────────────────────── shapes ─────────────────────────────

    /** A ring lying on the ground at {@code g}: its band laid over what is behind it (the blended pass), or its light (a thin white line in a purple glow). */
    static void groundRing(EffectMesh m,Vec3d g,double r,double wide,float a,boolean light){groundRing(m,g,r,wide,a,light,0);}
    /** The same with {@code n} sides (0: as many as its size has it). */
    static void groundRing(EffectMesh m,Vec3d g,double r,double wide,float a,boolean light,int n){
        if(a<=0||r<=0)return;
        if(light){if(n>0)FxLib.ringPlane(m,g,X,Z,r,wide*1.5,GLOW,.42f*a,n);else FxLib.ringPlane(m,g,X,Z,r,wide*1.5,GLOW,.42f*a);}
        else{if(n>0)DanceBurst.band(m,g,X,Z,r,wide,PURPLE,.85f*a,n);else DanceBurst.band(m,g,X,Z,r,wide,PURPLE,.85f*a);}
    }
    /** A ring facing the camera round {@code p} (its light, or its band). */
    static void facingRing(EffectMesh m,Vec3d p,Vec3d camera,double r,double wide,int rgb,float a,boolean light){
        if(a<=0||r<=0)return;var f=GlitchFx.facing(camera,p);
        if(light)FxLib.ringPlane(m,p,f[0],f[1],r,wide,rgb,a);else DanceBurst.band(m,p,f[0],f[1],r,wide,rgb,a);
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
    /** A crackling bolt from {@code a} to {@code b}: {@code n} legs, each joint thrown off the line by up to {@code off} (most in its middle). */
    static void bolt(EffectMesh m,Vec3d a,Vec3d b,int n,double off,int seed,double width,int rgb,float alpha){
        Vec3d d=b.subtract(a);if(d.lengthSquared()<1e-6||alpha<=0)return;
        Vec3d[] uv=DanceBurst.across(d.normalize());Vec3d last=a;
        for(int i=1;i<=n;i++){
            double u=(double)i/n,w=Math.sin(Math.PI*u);Vec3d p=a.add(d.multiply(u));
            if(i<n)p=p.add(uv[0].multiply((h(i,seed,1)-.5)*2*off*w)).add(uv[1].multiply((h(i,seed,2)-.5)*2*off*w));
            m.line(last,p,width,rgb,alpha);last=p;
        }
    }

    // ───────────────────────────── the run ─────────────────────────────

    /** When the run's feet come down (seconds into it: its strides, BladeRain.RUN) and where, from where the body is drawn: {right, ahead}. */
    static final float[] FALLS={.17f,.295f,.42f,.545f};
    static final double[][] FEET={{-.18,.56},{.17,.58},{-.18,.56},{.17,.58}};
    /** The wake the run leaves hanging behind it: this many streaks, each this long a life (seconds). */
    static final int WAKE=48;static final float WAKE_LIFE=.3f;
    static void run(Fx c,EffectMesh m,boolean light){
        var way=KnifeClient.rainWay(c.cast.id());if(way==null)return;
        float age=c.age,sec=c.sec,stop=BladeRain.until(c.cast);int seed=BladeRain.seed(c.cast);
        Vec3d f=Vec3d.fromPolar(0,c.cast.yaw()),right=new Vec3d(-f.z,0,f.x);
        float went=Math.min(age,Math.min(stop,RainRules.RUN_TO+2));
        // springing off: two rings over the ground where they stood, streaks thrown back along it, pixels kicked up
        float s=(age-RainRules.RUN_FROM)/20f;
        if(s>=0&&s<.5f){
            Vec3d g=way.from().add(0,.06,0);float fade=1-s/.5f;
            groundRing(m,g,.4+2.8*Ease.out(s,0,.4f),.34,fade,light);
            groundRing(m,g,.3+1.7*Ease.out(s,0,.3f),.18,fade*.8f,light);
            for(int j=0;j<12;j++){
                double side=(j-5.5)/5.5*.55,len=(1.4+1.4*h(j,seed,301))*Ease.out(s,0,.18f);
                Vec3d a=g.add(right.multiply(side)).add(0,.04,0),b=a.subtract(f.multiply(len)).add(right.multiply(side*.8));
                if(light)m.softTaper(a,b,.18,.02,j%3==0?LILAC:GLOW,.5f*fade);else if(j%2==0)flat(m,a,b,.06,DEEP,.7f*fade);
            }
            if(light&&s<.08f)m.softDisc(g.add(0,.3,0),1.2,GLOW,.4f*(1-s/.08f));
            GlitchFx.pixels(m,way.from().add(0,.3,0),f.negate().add(0,.8,0).normalize(),.6,4.5,.05,18,seed+302,s,light,c.camera);
        }
        // each foot coming down: a small ring and pixels where it lands
        for(int i=0;i<FALLS.length;i++){
            float at=FALLS[i]*20;if(at>=stop)break;
            float fs=(age-at)/20f;if(fs<0||fs>=.4f)continue;
            Vec3d body=BladeRain.ran(way.from(),way.to(),at),foot=body.add(f.multiply(FEET[i][1])).add(right.multiply(FEET[i][0])).add(0,.05,0);
            float fade=1-fs/.4f;
            groundRing(m,foot,.15+1.2*Ease.out(fs,0,.3f),.16,fade,light);
            if(light&&fs<.07f)m.star(foot.add(0,.12,0),.5,LILAC,.65f*(1-fs/.07f));
            GlitchFx.pixels(m,foot.add(0,.1,0),f.negate().add(0,.9,0).normalize(),.7,3.2,.04,8,seed+311+i*13,fs,light,c.camera);
        }
        // the wake: streaks of purple left hanging where they ran
        for(int j=0;j<WAKE;j++){
            float born=RainRules.RUN_FROM+2+(RainRules.RUN_TO-RainRules.RUN_FROM-2)*(j+(float)h(j,seed,321))/WAKE;
            if(born>went)continue;
            float ws=(age-born)/20f;if(ws<0||ws>=WAKE_LIFE)continue;
            Vec3d at=BladeRain.ran(way.from(),way.to(),born);
            double side=(h(j,seed,322)<.5?-1:1)*(.3+.55*h(j,seed,323)),up=.12+1.8*h(j,seed,324),len=(1.1+1.7*h(j,seed,325))*(1-.4*ws/WAKE_LIFE);
            Vec3d head=at.add(right.multiply(side)).add(0,up,0),tail=head.subtract(f.multiply(len));
            float fade=Ease.smooth(ws,0,.03f)*(1-ws/WAKE_LIFE)*GlitchFx.near(head,c.camera);
            if(light){m.softTaper(tail,head,.01,.14,j%3==0?LILAC:GLOW,.6f*fade);if(j%3==0)m.softTaper(tail,head,.004,.04,WHITE,.6f*fade);}
            else if(j%4==0)m.needle(tail,head,.06,.85,DEEP,.8f*fade);
        }
        // while running: a purple wind round the body, low and at the chest, and a glint trailing the knife's point
        if(light&&age>RainRules.RUN_FROM+1&&age<Math.min(stop,RainRules.RUN_TO+2)){
            Vec3d head=BladeRain.ran(way.from(),way.to(),age),tail=BladeRain.ran(way.from(),way.to(),Math.max(RainRules.RUN_FROM,age-3));
            if(head.squaredDistanceTo(tail)>.04){
                m.softTaper(tail.add(0,1,0),head.add(0,1,0),.05,1.2,GLOW,.2f);
                m.softTaper(tail.add(0,.12,0),head.add(0,.12,0),.05,.9,PURPLE,.18f);
            }
            Vec3d last=null;
            for(int i=0;i<=6;i++){
                float t=Math.max(0,sec-.1f+.1f*i/6);
                var hb=KagenoClips.blade(BladeRain.ran(way.from(),way.to(),t*20),c.cast.yaw(),R6.poseOf(BladeRain.RUN.frame(t)));
                Vec3d tip=hb[0].add(hb[1].multiply(KnifeCombo.TIP));
                if(last!=null){m.softLine(last,tip,.06+.2*i/6,GLOW,.4f*i/6);m.softLine(last,tip,.02,LILAC,.4f*i/6);}
                last=tip;
            }
        }
        // braking to a stop, no one met: a skid over the ground, a ring, pixels
        if(stop==Float.MAX_VALUE&&age>=RainRules.RUN_TO){
            float bs=(age-RainRules.RUN_TO)/20f;
            if(bs<.6f){
                Vec3d foot=way.to().add(f.multiply(.46)).add(right.multiply(-.2)).add(0,.05,0);float fade=1-bs/.6f;
                groundRing(m,foot,.3+1.9*Ease.out(bs,0,.4f),.24,fade,light);
                if(light)m.softTaper(foot.subtract(f.multiply(1.7)),foot,.02,.34,GLOW,.42f*fade);
                else flat(m,foot.subtract(f.multiply(1.7)),foot,.18,DEEP,.6f*fade);
                GlitchFx.pixels(m,foot.add(0,.15,0),f.add(0,.6,0).normalize(),.6,3.5,.045,14,seed+331,bs,light,c.camera);
            }
        }
    }

    // ───────────────────────────── the stab ─────────────────────────────

    /** How much bigger the knife's own burst is as it goes in, every few volleys, and with the last. */
    static final double STAB_BURST=1.6,BURST_SIZE=1.0,LAST_BURST=2.0;
    /** The knife's burst on the one stabbed comes with every this many volleys, and an eruption out of them along that knife's way with every other one of them (at this size). */
    static final int BURST_EVERY=4;static final double ERUPTION=.6;
    /** How far the lance of light along the knife goes on past its point as it goes in (blocks). */
    static final double LANCE=2.8;
    /** Where the knife and its hand are this frame in the body as it is drawn: {its hand, its way (unit)}. */
    static Vec3d[] knife(Fx c,float sec){
        Vec3d body=c.pos.add(BladeRain.shift(c.cast,c.delta));
        var hb=KagenoClips.blade(body,c.yaw,R6.poseOf(BladeRain.STAB.frame(sec)));
        return new Vec3d[]{hb[0],hb[1].normalize()};
    }
    /**
     * The way the knife goes through the one struck ({@code st}, where they stood as it landed): the sword dance's
     * unseen blade, but the knife's own line, from its hand {@code hand} along {@code dir}: in where it meets their body,
     * through the point on it nearest their middle, out where it leaves.
     */
    static DanceBurst.Pierce through(KnifeCombo.Struck st,Vec3d hand,Vec3d dir){
        Vec3d mid=st.feet().add(0,st.height()*.5,0);double t=mid.subtract(hand).dotProduct(dir);
        Vec3d in=hand.add(dir.multiply(t)).subtract(st.feet());
        in=new Vec3d(MathHelper.clamp(in.x,-st.half()*.85,st.half()*.85),MathHelper.clamp(in.y,.15,st.height()-.15),MathHelper.clamp(in.z,-st.half()*.85,st.half()*.85));
        return new DanceBurst.Pierce(dir,in.subtract(dir.multiply(DanceBurst.leave(in,dir.negate(),st.half(),st.height()))),in.add(dir.multiply(DanceBurst.leave(in,dir,st.half(),st.height()))),in);
    }
    /** The same along a way {@code dir} through the point {@code at} of a body standing at {@code feet} ({@code half} wide either way, {@code height} high). */
    static DanceBurst.Pierce through(Vec3d feet,double half,double height,Vec3d at,Vec3d dir){
        Vec3d in=at.subtract(feet);
        in=new Vec3d(MathHelper.clamp(in.x,-half*.85,half*.85),MathHelper.clamp(in.y,.15,height-.15),MathHelper.clamp(in.z,-half*.85,half*.85));
        return new DanceBurst.Pierce(dir,in.subtract(dir.multiply(DanceBurst.leave(in,dir.negate(),half,height))),in.add(dir.multiply(DanceBurst.leave(in,dir,half,height))),in);
    }
    /** An eruption out of a body along {@code p}'s way, {@code s} seconds after it, in the blended pass (its purple, then its black) or the light. */
    static void erupt(EffectMesh m,Vec3d feet,DanceBurst.Pierce p,int k,int seed,float s,boolean big,boolean light,Vec3d camera){
        float t=s*20;if(t<0||t>=DanceBurst.LIFE)return;
        if(light)DanceBurst.light(m,feet,p,k,seed,t,big,camera);
        else{DanceBurst.body(m,feet,p,k,seed,t,big,camera);DanceBurst.black(m,feet,p,k,seed,t,big,camera);}
    }
    /** Before it goes in, as the knife comes round to the hip: purple gathering onto its blade (motes drawn in onto it, its glow coming up). */
    static void charge(Fx c,EffectMesh m,boolean light,Vec3d hand,Vec3d dir,float sec,int seed){
        float u=Ease.smooth(sec,.1f,STAB_AT);
        Vec3d heel=hand.add(dir.multiply(KnifeCombo.HEEL)),tip=hand.add(dir.multiply(KnifeCombo.TIP));
        if(light){m.softLine(heel,tip,.45*u,GLOW,.38f*u);m.softLine(heel,tip,.06,LILAC,.45f*u);}
        for(int j=0;j<18;j++){
            float born=.1f+.17f*(float)h(j,seed,401),life=.15f,ms=sec-born;if(ms<0||ms>=life)continue;
            Vec3d to=hand.add(dir.multiply(KnifeCombo.HEEL+(KnifeCombo.TIP-KnifeCombo.HEEL)*h(j,seed,402)));
            Vec3d off=DanceBurst.unit(j,0,seed,403).multiply(.8+.9*h(j,seed,404));
            float g=ms/life;double k=1-g*g;Vec3d at=to.add(off.multiply(k));
            float near=GlitchFx.near(at,c.camera);if(near<=0)continue;
            if(light)m.softTaper(at.add(off.multiply(.3*k+.05)),at,.01,.08,j%3==0?WHITE:GLOW,.75f*near);
            else GlitchFx.square(m,at,GlitchFx.facing(c.camera,at),.035,j%2==0?PURPLE:DEEP,.9f*near);
        }
    }
    /** As it goes in ({@code s} seconds after): the lance, the knife's own burst, the eruption out of their back along it, rings and dark over the ground, pixels. */
    static void stabbed(Fx c,EffectMesh m,boolean light,float s,int seed){
        Vec3d[] k=knife(c,STAB_AT);Vec3d hand=k[0],dir=k[1];
        // a lance of light along the knife and on out past its point
        if(s<.34f){
            float fade=Ease.smooth(s,0,.02f)*(1-Ease.smooth(s,.07f,.34f));
            Vec3d[] now=knife(c,c.sec);
            Vec3d a=now[0].add(now[1].multiply(.5)),b=now[0].add(now[1].multiply(KnifeCombo.TIP+LANCE*Ease.out(s,0,.08f)));
            if(light){m.softTaper(a,b,.85,.14,GLOW,.6f*fade);m.softTaper(a,b,.12,.03,WHITE,.85f*fade);}
            else{m.needle(a,b,.46,.35,INK,.8f*fade);m.needle(a,b,.36,.35,DEEP,.95f*fade);m.needle(a,b,.22,.35,PURPLE,.95f*fade);}
        }
        for(var st:KnifeCombo.struck(c,0)){
            // the knife's own burst, half again as big
            if(s<SlashFx.SWIRL_LIFE)SlashFx.burst(m.scaled(SlashFx.burstAt(st.mid(),c.camera),STAB_BURST),st.mid(),dir,HACK_LUNGE,KnifeCombo.seed(c,0)+st.id(),s,light,c.camera);
            // out of their back along the knife
            var p=through(st,hand,dir);
            erupt(m,st.feet(),p,7,seed,s,true,light,c.camera);
            // rings racing out over the ground under them
            Vec3d g=st.feet().add(0,.06,0);
            for(int r=0;r<2;r++){
                float t=s-.05f*r;if(t<0||t>=.55f)continue;
                groundRing(m,g,(r==0?6.2:4.0)*Ease.out(t,0,.5f)+.4,r==0?.42:.26,1-t/.55f,light);
            }
            if(light&&s<.12f)m.softDisc(g.add(0,.2,0),2.4,GLOW,.35f*(1-s/.12f));
            GlitchFx.pixels(m,st.feet().add(p.out()),dir.add(0,.45,0).normalize(),.55,7.5,.06,34,seed+501+st.id(),s,light,c.camera);
        }
    }

    // ───────────────────────────── borne on through the rain ─────────────────────────────

    /** The circle under the one stabbed (blocks out), how quick its parts turn (degrees a second: its dashes, its ticks the other way, its star). */
    static final double CIRCLE=2.6,DASH_TURN=40,TICK_TURN=-26,STAR_TURN=12;
    /** How much of it all is there {@code sec} seconds into the stab (stopped short at {@code stopS}): from just after the knife goes in, gone after the last volley (or as it stops). */
    static float bearing(float sec,float stopS){
        float end=Math.min(LAST_AT,stopS);
        return Ease.smooth(sec,STAB_AT+.02f,STAB_AT+.16f)*(1-Ease.smooth(sec,end,end+.45f));
    }
    /** How big the circle is: coming out from small as the knife goes in, thrown wide as the last volley lands. */
    static double circleScale(float sec,float stopS){
        float end=Math.min(LAST_AT,stopS);
        return (.45+.55*Ease.out(sec,STAB_AT,STAB_AT+.22f))*(1+.7*Ease.out(sec,end,end+.45f));
    }
    /** The volleys' beat {@code sec} seconds in: up at each burst (every BURST_EVERY volleys), dying away after it. */
    static float beat(float sec,float stopS){
        float most=0;
        for(int v=0;v<RainRules.VOLLEYS;v+=BURST_EVERY){
            float at=RainRules.volleyAt(v)/20f;if(at>sec||at>stopS)continue;
            most=Math.max(most,(float)Math.exp(-(sec-at)/.1));
        }
        return most;
    }
    /** The circle lying on the ground at {@code g}: two rings and a thin one out past them, dashes turning between them and ticks round the outside the other way, a star of two triangles in it turning slowly, squares of the hack lying in it. */
    static void circle(EffectMesh m,Vec3d g,float sec,float on,double scale,float beat,int seed,boolean light){
        double R=CIRCLE*scale;float a=on*(.75f+.45f*beat);if(a<=0)return;
        if(light){
            FxLib.ringPlane(m,g,X,Z,R,.26,GLOW,.5f*a);FxLib.ringPlane(m,g,X,Z,R*.72,.16,GLOW,.42f*a);FxLib.ringPlane(m,g,X,Z,R*1.1,.08,GLOW,.3f*a);
        }else{
            patch(m,g,R*.95,NAVY,.42f*on,seed);
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
        // squares of the hack lying in it, flickering (a new set eight times a second)
        int slot=(int)Math.floor(sec*8);
        for(int j=0;j<10;j++){
            if(h(j+slot*17,seed,611)>.6)continue;
            double ang=Math.PI*2*h(j+slot*13,seed,612),r=R*(.25+.7*h(j+slot*7,seed,613)),half=(.06+.1*h(j+slot*5,seed,614))*scale;
            Vec3d p=g.add(Math.cos(ang)*r,.015,Math.sin(ang)*r);
            Vec3d u=X.multiply(half),v=Z.multiply(half);
            if(light)m.quad(p.subtract(u).subtract(v),p.add(u).subtract(v),p.add(u).add(v),p.subtract(u).add(v),GLOW,.3f*a);
            else m.quad(p.subtract(u).subtract(v),p.add(u).subtract(v),p.add(u).add(v),p.subtract(u).add(v),j%3==0?LILAC:PURPLE,.9f*a);
        }
    }
    /** Squares of the hack rising out of the circle: this many, a new one from each every so often (seconds), each rising this long. */
    static final int RISERS=30;static final float RISE_EVERY=1.2f,RISE_LIFE=.75f;
    static void rising(Fx c,EffectMesh m,Vec3d g,float sec,float on,double scale,int seed,boolean light){
        if(on<=0)return;
        for(int j=0;j<RISERS;j++){
            float first=STAB_AT+.06f+j*RISE_EVERY/RISERS;if(sec<first)continue;
            int k=(int)Math.floor((sec-first)/RISE_EVERY);float t=sec-first-k*RISE_EVERY;if(t>=RISE_LIFE)continue;
            double ang=Math.PI*2*h(j*7+k,seed,621),r=CIRCLE*scale*(.3+.75*h(j*7+k,seed,622)),up=(1.3+1.9*h(j*7+k,seed,623))*Ease.out(t,0,RISE_LIFE);
            Vec3d p=g.add(Math.cos(ang)*r,up,Math.sin(ang)*r);float near=GlitchFx.near(p,c.camera);
            float shown=on*near*(t>RISE_LIFE*.75f&&((int)Math.floor(t*40+j))%2==1?0:1)*(1-Ease.smooth(t,RISE_LIFE*.8f,RISE_LIFE));
            if(shown<=0)continue;
            var f=GlitchFx.facing(c.camera,p);double half=.04+.07*h(j*7+k,seed,624);
            if(light)GlitchFx.square(m,p,f,half*2,GLOW,.32f*shown);
            else{GlitchFx.square(m,p,f,half+.012,INK,.9f*shown);GlitchFx.square(m,p,f,half,j%3==0?WHITE:j%3==1?PURPLE:DEEP,shown);}
        }
    }
    /** Purple spiralling up round them: three strands, turning (turns a second), climbing past their head. */
    static final double SPIN=1.3;
    static void spiral(EffectMesh m,Vec3d feet,double half,double height,float sec,float on,Vec3d camera){
        if(on<=0)return;
        for(int s=0;s<3;s++){
            Vec3d last=null;
            for(int i=0;i<=26;i++){
                double u=i/26.0,y=u*height*1.4,r=(half+.5)*(1-.38*u)+.08*Math.sin(sec*7+s*2);
                double ang=sec*Math.PI*2*SPIN+s*Math.PI*2/3+u*Math.PI*2.5;
                Vec3d p=feet.add(Math.cos(ang)*r,y,Math.sin(ang)*r);
                if(last!=null){
                    float flow=(float)(.45+.55*Math.max(0,Math.sin(u*13-sec*18+s*2))),a=on*flow*(float)(1-u*.55)*GlitchFx.near(p,camera);
                    m.softLine(last,p,.18,GLOW,.32f*a);m.softLine(last,p,.035,LILAC,.5f*a);
                }
                last=p;
            }
        }
    }
    /** Borne on through the rain ({@code sec} seconds in, its user's knife at {@code hand} along {@code dir}): the circle and its squares, the spiral, lightning on the knife and into them, the hands aglow, a haze about them. */
    static void held(Fx c,EffectMesh m,boolean light,KnifeCombo.Struck st,float sec,float stopS,int seed){
        float on=bearing(sec,stopS);if(on<=0)return;
        float beat=beat(sec,stopS);double scale=circleScale(sec,stopS);
        Vec3d feet=st.feet(),g=feet.add(0,.05,0),mid=st.mid();
        circle(m,g,sec,on,scale,beat,seed,light);
        rising(c,m,g,sec,on,scale,seed,light);
        if(!light)return;
        spiral(m,feet,st.half(),st.height(),sec,on,c.camera);
        // lightning crackling along the knife (out of their body up to the hands) and off it into them
        float lit=on*(1-Ease.smooth(sec,OUT_AT-.05f,OUT_AT+.05f));
        if(lit>0){
            Vec3d[] k=knife(c,sec);Vec3d hand=k[0],dir=k[1];
            int slot=(int)Math.floor(sec*15);
            Vec3d a=hand.add(dir.multiply(.45)),b=hand.add(dir.multiply(KnifeCombo.TIP));
            for(int i=0;i<2;i++){int sd=seed+slot*31+i*7;bolt(m,a,b,10,.14,sd,.14,GLOW,.42f*lit);bolt(m,a,b,10,.14,sd,.03,WHITE,.8f*lit);}
            for(int i=0;i<2;i++){
                int sd=seed+slot*53+i*11;
                Vec3d from=hand.add(dir.multiply(KnifeCombo.HEEL+(KnifeCombo.TIP-KnifeCombo.HEEL)*(.25+.5*h(slot*3+i,seed,701))));
                Vec3d to=mid.add(DanceBurst.unit(slot*3+i,1,seed,702).multiply(.4));
                bolt(m,from,to,6,.12,sd,.12,GLOW,.36f*lit);bolt(m,from,to,6,.12,sd,.025,WHITE,.7f*lit);
            }
            // both hands aglow on the rod
            m.softDisc(hand,.5,GLOW,.3f*lit*(.8f+.4f*beat));m.softDisc(hand,.2,LILAC,.35f*lit);
        }
        // a haze about them, beating with the volleys
        m.softDisc(mid,1.6,GLOW,.13f*on*(.7f+.6f*beat)*GlitchFx.near(mid,c.camera));
    }

    // ───────────────────────────── the knives ─────────────────────────────
    // (2.132.1: straight down out of a circle in the sky like rain, on the one stabbed and the ground round them; 2.132.2:
    // over the ground 15 blocks round, the knives a little bigger, drawn lighter (a coarser outline, each point worked
    // out once, faded by the camera); 2.132.3 had them fly in at the one stabbed from all round. 2.132.4, "아니다 걍 주변
    // 30블록으로 검들이 떨어지게 바꾸자 비처럼 적을 향하게 만들지말고": the rain again, over the ground 30 blocks round,
    // as thick on it as 2.132.2's (four times as many: some two thousand at once). Drawn here, flat (the knife's own
    // model is 604 boxes); what the camera cannot see is not drawn ({@link View}), what is far off is drawn coarser
    // ({@link #FAR}: fewer points, one band of rim, one streak) and throws up less where it lands, and what each knife is
    // (where it comes down, the ground there, how high it comes from) is worked out once ({@link Rain}))

    /** One stab's knives as each was first worked out (2.132.4: thousands, every frame twice): where it comes down from the middle of the one stabbed (level), whether on them, the ground there (NaN: none, it is not drawn), how high it comes from, how it is turned, how long its streak is. */
    static final class Rain{
        final double[] dx=new double[RainRules.BLADES],dz=new double[RainRules.BLADES],ground=new double[RainRules.BLADES],high=new double[RainRules.BLADES],streak=new double[RainRules.BLADES];
        final float[] roll=new float[RainRules.BLADES];
        final boolean[] body=new boolean[RainRules.BLADES],known=new boolean[RainRules.BLADES];
    }
    private static final java.util.Map<java.util.UUID,Rain> RAINS=new java.util.LinkedHashMap<>();
    static Rain rain(StandClient.Cast c){
        var r=RAINS.get(c.id());
        if(r==null){r=new Rain();RAINS.put(c.id(),r);while(RAINS.size()>8)RAINS.remove(RAINS.keySet().iterator().next());}
        return r;
    }
    /** How long the streak over a falling knife is (blocks: 2.132.4, falling slower, shorter than 2.132.2's 2.8 to 4). */
    static final double STREAK=2.1,STREAK_MORE=.9;
    /** Knife {@code k} of {@code r} worked out as it comes down at {@code s} round one whose feet are at {@code feet}, {@code near} the height to look for the ground about. */
    static void learn(Rain r,int k,int seed,BladeRain.Spot s,Vec3d feet,double near){
        r.dx[k]=s.x()-feet.x;r.dz[k]=s.z()-feet.z;r.body[k]=s.body();
        r.ground[k]=s.body()?near:groundAt(s.x(),s.z(),near);
        r.high[k]=BladeRain.sky(k,seed);r.roll[k]=BladeRain.roll(k,seed);r.streak[k]=STREAK+STREAK_MORE*h(k,seed,51);
        r.known[k]=true;
    }
    /** The ground under where knife {@code k} of stab {@code c} comes down at {@code s} round one whose feet are at {@code feet} (worked out once, the world under it as it first came; NaN: none). */
    static double ground(StandClient.Cast c,int k,BladeRain.Spot s,Vec3d feet){
        var r=rain(c);if(!r.known[k])learn(r,k,BladeRain.seed(c),s,feet,feet.y);
        return r.ground[k];
    }
    /** How far over and under the feet of the one stabbed the ground is looked for (blocks: 2.132.4, the ground 30 blocks round them is not all as high as where they stand). */
    static final int GROUND_OVER=6,GROUND_UNDER=8;
    /**
     * The top of the ground under (x, z) near the height {@code y}: the first solid top from GROUND_OVER over it down to
     * GROUND_UNDER under it; NaN if there is none, or if it is solid from there up (in a wall or a hill higher than that:
     * no knife comes down there).
     */
    static double groundAt(double x,double z,double y){
        var w=net.minecraft.client.MinecraftClient.getInstance().world;if(w==null)return y;
        int bx=MathHelper.floor(x),bz=MathHelper.floor(z),top=MathHelper.floor(y)+GROUND_OVER;
        var pos=new BlockPos.Mutable();
        for(int by=top;by>=top-GROUND_OVER-GROUND_UNDER;by--){
            pos.set(bx,by,bz);var shape=w.getBlockState(pos).getCollisionShape(w,pos);
            if(!shape.isEmpty())return by==top?Double.NaN:by+shape.getMax(net.minecraft.util.math.Direction.Axis.Y);
        }
        return Double.NaN;
    }

    /**
     * What the camera sees this frame (2.132.4: some two thousand knives at once round it, most of them out of its
     * sight): its eye, the ways it looks (ahead, up, left: unit), and how far the sides of its sight go out ahead (the
     * tangents of half of it across and up, taken a little wider than the projection's: the camera shakes).
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

    /** The rim round a knife of the rain, and its glow's reach past it, this many times the knife in the hand's (it is half that size and seen far off). */
    static final double RIM_MORE=2.2,GLOW_MORE=1.6;
    /**
     * The outline a knife of the rain is drawn round: the knife's own (HackKnife), with ten points along its edge, not
     * twenty-six (2.132.2: some five hundred of them at once, each seen small; the same shape to the eye); one far off
     * ({@link #FAR}) with three (2.132.4: some two thousand at once).
     */
    static final double[][] OUTLINE=HackKnife.outline(10),MITER=HackKnife.miters(OUTLINE),OUTLINE_FAR=HackKnife.outline(3),MITER_FAR=HackKnife.miters(OUTLINE_FAR),
        OUTLINE_FARTHEST=HackKnife.outline(2),MITER_FARTHEST=HackKnife.miters(OUTLINE_FARTHEST);
    /**
     * How far from the camera a knife is drawn coarser (blocks: there some 7 degrees long, its outline's corners lost:
     * fewer points, one band of rim, only the one of its two planes more across the camera's sight, its glow laid along
     * it), and coarser still (eight points); how far a ring where one lands has fewer sides, and how far one landing is
     * drawn at all.
     */
    static final double FAR=14,FARTHEST=24,LANDS_SEEN=22;
    /** Over how far either side of FAR a knife is drawn both ways, the near way going as the far way comes (blocks: not at once as it falls past it). */
    static final double SWAP=1;
    /** How many sides the ring is where a knife lands on the ground (small, and over a thousand at once), and far off. */
    static final int LAND_SIDES=12,LAND_SIDES_FAR=8;
    /** How far round a knife's line what is drawn of it may reach (blocks: its width, its rim and glow), and round where one lands (its ring and what it throws up). */
    static final double KNIFE_REACH=.75,LAND_REACH=1.2;
    /**
     * How much of a knife of the rain shows by the camera (2.132.2: so many, and some come by it): none within NEAR0 of
     * its line (its point to the top of its streak), all of it from NEAR1 on.
     */
    static final double NEAR0=1.5,NEAR1=4.5;
    static float near(Vec3d tip,Vec3d top,Vec3d camera){
        Vec3d d=top.subtract(tip);double l=d.lengthSquared(),u=l<1e-9?0:Math.max(0,Math.min(1,camera.subtract(tip).dotProduct(d)/l));
        double far=camera.distanceTo(tip.add(d.multiply(u)));
        return Ease.smooth((float)far,(float)NEAR0,(float)NEAR1);
    }
    /** The points of up to three rings round a knife's outline in the world, worked out into these and not into new vectors (2.132.4: some three hundred thousand vertices a frame; the game draws on one thread). */
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
     * {@code camera} (seen far off, the other is near edge on), and its glow is laid along it ({@link #farGlow}), not
     * round its outline.
     */
    static void blade(EffectMesh m,BladeRain.Pose p,float roll,float a,boolean light,int lod,Vec3d camera){
        if(a<=0)return;
        double unit=ShotRules.SCALE*BladeRain.SIZE*BladeRain.size(p.shown())/16;
        Vec3d y=p.dir(),sp=BladeShot.spine(y,roll),across=y.crossProduct(sp).normalize(),grip=p.grip();
        boolean coarse=lod>0;
        if(light&&coarse){farGlow(m,p,unit,a);return;}
        double gx=grip.x,gy=grip.y,gz=grip.z;
        double[][] o=lod==0?OUTLINE:lod==1?OUTLINE_FAR:OUTLINE_FARTHEST,mi=lod==0?MITER:lod==1?MITER_FAR:MITER_FARTHEST;int n=o.length;
        // (far off, the plane more across the camera's sight: the first faces along `across`, the second along `sp`)
        int first=0,last=1;
        if(coarse){double vx=camera.x-gx,vy=camera.y-gy,vz=camera.z-gz;if(Math.abs(vx*across.x+vy*across.y+vz*across.z)>=Math.abs(vx*sp.x+vy*sp.y+vz*sp.z))last=0;else first=1;}
        // (2.132.2: each point of the outline worked out once a plane, not once for each quad it is in; 2.132.4: into
        // the scratch rings, nothing new made for it)
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
    /** How wide the glow laid along a knife far off is at its heel and at its point (blocks: as wide as its glow round its outline reaches, the knife and both sides). */
    static final double FAR_GLOW_HEEL=.72,FAR_GLOW_TIP=.38;
    /** A knife far off: its glow laid along it from its rod's end to its point, facing the camera (one strip, not a ring round its outline on each plane). */
    static void farGlow(EffectMesh m,BladeRain.Pose p,double unit,float a){
        Vec3d end=p.grip().subtract(p.dir().multiply(HackKnife.Y_HEEL*unit)),tip=p.tip();
        m.softTaper(end,tip,FAR_GLOW_HEEL,FAR_GLOW_TIP,GLOW,.42f*a);
    }
    /** The circle in the sky the knives come out of: this high over the feet of the one stabbed (blocks), its parts turning the other way to the one on the ground. */
    static final double SKY_UP=BladeRain.SKY+.55;
    /** How much of it is there {@code sec} seconds into the stab: opening before the first knife comes, closing after the last. */
    static float skyOn(float sec,float stopS){
        float end=Math.min(LAST_AT,stopS);
        return Ease.smooth(sec,STAB_AT+.05f,STAB_AT+.3f)*(1-Ease.smooth(sec,end+.05f,end+.4f));
    }
    /** The circle in the sky ({@code c} its middle): the ground's circle again, wide as the rain, turning the other way, a dark gap in the sky in it and its light beating as the knives pour out. */
    static void sky(EffectMesh m,Vec3d c,float sec,float on,float beat,int seed,boolean light){
        if(on<=0)return;
        double scale=(BladeRain.ZONE+.7)/CIRCLE*(.6+.4*Ease.out(sec,STAB_AT+.05f,STAB_AT+.35f));
        circle(m,c,-sec,on,scale,beat,seed+31,light);
        if(light){FxLib.ringPlane(m,c,X,Z,(BladeRain.ZONE+.7)*1.25,.3,GLOW,.3f*on);m.softDisc(c.add(0,-.3,0),3.6,GLOW,.16f*on*(.8f+.4f*beat));}
    }
    /** Which knives fall {@code age} ticks in: from the first of the volley landing a little before what is still shown of them, to past the last of the volley coming to be (indices, the second past the last). */
    static int[] window(float age){
        int v0=Math.max(0,(int)Math.floor(age-RainRules.FIRST-BladeRain.STUCK-12.5f)),v1=Math.min(RainRules.VOLLEYS-1,(int)Math.ceil(age-RainRules.FIRST+BladeRain.BEFORE+1));
        return v1<v0?new int[]{0,0}:new int[]{v0*RainRules.PER,(v1+1)*RainRules.PER};
    }
    /** The knives drawn and how long they took (both passes) the last whole frame, for the capture tools. */
    private static Fx counting;private static int drawing,drawnLast;private static long nanos,microsLast;
    private static void count(Fx c,int drawn,long ns,boolean light){
        if(counting!=c){if(counting!=null){drawnLast=drawing;microsLast=nanos/1000;}counting=c;drawing=0;nanos=0;}
        if(!light)drawing=drawn;nanos+=ns;
    }
    static int drawnLast(){return drawnLast;}
    static long microsLast(){return microsLast;}
    /**
     * Each knife of the rain: coming out of the sky in blocks of purple with a star, falling straight down with a long
     * streak over it, standing where it lands (in the ground, or in the one stabbed), broken up into pixels as it goes;
     * where it lands on the ground a ring and pixels splashing up, on them a star, a ring, spikes thrown up and pixels.
     * Those the camera cannot see are not drawn; those far off are drawn coarser and throw up only their ring, and none
     * past LANDS_SEEN.
     */
    static void knives(Fx c,EffectMesh m,boolean light,net.minecraft.entity.LivingEntity e,float stop,int seed){
        long t0=System.nanoTime();int drawn=0;
        float age=c.age;var rain=rain(c.cast);View view=view(c);
        Vec3d feet=e.getLerpedPos(c.delta),user=BladeRain.userFrom(c.cast,feet);double half=e.getWidth()*.5,height=e.getHeight();
        int[] w=window(age);
        for(int k=w[0];k<w[1];k++){
            float lands=(float)RainRules.landsAt(k);
            if(age<lands-BladeRain.BEFORE-.5f)break;
            if(age>lands+BladeRain.STUCK+12)continue;
            if(!rain.known[k])learn(rain,k,seed,BladeRain.spot(k,seed,feet,half,user),feet,feet.y);
            double ground=rain.ground[k];if(Double.isNaN(ground))continue;
            var sp=new BladeRain.Spot(feet.x+rain.dx[k],feet.z+rain.dz[k],rain.body[k]);double land=BladeRain.landY(sp,feet,height,ground);
            var p=BladeRain.pose(k,age,stop,sp,land,rain.high[k]);
            if(p!=null){
                Vec3d tip=p.tip(),top=p.grip().add(0,p.flying()?rain.streak[k]:0,0);
                if(view==null||view.sees(tip,top,KNIFE_REACH)){
                    float near=near(tip,top,c.camera)*p.shown();
                    if(near>0){
                        drawn++;
                        double far=Math.sqrt(c.camera.squaredDistanceTo(p.grip()));boolean coarse=far>FAR;
                        // (what only the near ones have goes out over the two blocks before FAR, not at once; and the
                        // knife is drawn both ways over the two blocks about FAR, one going as the other comes)
                        float fine=1-Ease.smooth((float)far,(float)FAR-2,(float)FAR),blend=Ease.smooth((float)far,(float)(FAR-SWAP),(float)(FAR+SWAP));
                        if(blend<1)blade(m,p,rain.roll[k],near*(1-blend),light,0,c.camera);
                        if(blend>0)blade(m,p,rain.roll[k],near*blend,light,far>FARTHEST?2:1,c.camera);
                        Vec3d mid=p.grip().add(p.dir().multiply(BladeRain.TIP*.5));
                        if(light){
                            // a long streak over it as it falls, a star as it comes out of the sky, aglow where it stands
                            if(p.flying()){m.softTaper(top,tip,.03,.34,GLOW,.42f*near);if(fine>0)m.softTaper(top,p.grip(),.01,.08,WHITE,.6f*near*fine);}
                            if(p.lit()<.1f){float a=1-p.lit()/.1f;m.star(mid,.6*a+.12,LILAC,.85f*a*near);}
                            if(p.in()&&fine>0)m.softDisc(mid,.3,GLOW,.2f*near*fine);
                        }
                        // blocks of purple breaking over it as it comes out of the sky (the hack's own coming on)
                        if(p.lit()<.12f&&!coarse){
                            int slot=(int)Math.floor(age/20f*16);var f=GlitchFx.facing(c.camera,p.grip());
                            for(int j=0;j<4;j++){
                                if(GlitchFx.h(j,slot,seed+k,31)>.6)continue;
                                Vec3d at=p.grip().add(p.dir().multiply(BladeRain.TIP*GlitchFx.h(j,slot,seed+k,32))).add(f[0].multiply((GlitchFx.h(j,slot,seed+k,33)-.5)*.42)).add(f[1].multiply((GlitchFx.h(j,slot,seed+k,34)-.5)*.42));
                                double sq=.035+.055*GlitchFx.h(j,slot,seed+k,35);
                                if(light)GlitchFx.square(m,at,f,sq*1.9,GLOW,.3f*near);
                                else{GlitchFx.square(m,at,f,sq+.012,INK,.85f*near);GlitchFx.square(m,at,f,sq,GlitchFx.h(j,slot,seed+k,36)<.6?PURPLE:DEEP,.95f*near);}
                            }
                        }
                    }
                }
            }
            if(lands>stop)continue;
            float s=(age-lands)/20f;
            if(sp.body()){
                // on them: a star and a glow, a ring round them, spikes thrown up, pixels
                Vec3d at=new Vec3d(sp.x(),feet.y+height,sp.z());
                if(s>=0&&s<.36f&&(view==null||view.sees(at,LAND_REACH*1.5))){
                    float near=GlitchFx.near(at,c.camera);
                    if(light&&s<.1f&&near>0){float a=1-s/.1f;m.star(at,.5*a+.1,LILAC,.85f*a*near);m.softDisc(at,.45,GLOW,.36f*a*near);}
                    if(s<.18f&&near>0){float a=(1-s/.18f)*near;double r=.15+.6*Ease.out(s,0,.16f);
                        if(light)FxLib.ringPlane(m,at,X,Z,r,.08,GLOW,.55f*a);else DanceBurst.band(m,at,X,Z,r,.07,PURPLE,.85f*a);}
                    if(s<.16f&&near>0)for(int j=0;j<3;j++){
                        Vec3d dir=UP.add(DanceBurst.unit(k,j,seed,41).multiply(.7)).normalize();
                        double len=(.45+.55*h(k*3+j,seed,42))*Ease.out(s,0,.05f);float a=(1-Ease.smooth(s,.04f,.16f))*near;
                        Vec3d a0=at.add(dir.multiply(.05)),a1=at.add(dir.multiply(.05+len));
                        if(light)m.needle(a0,a1,.1,.25,GLOW,.25f*a);else m.needle(a0,a1,.065,.25,j==0?INK:j==1?DEEP:PURPLE,.92f*a);
                    }
                    GlitchFx.pixels(m,at,UP,.7,3.8,.04,5,seed+k*7,s,light,c.camera);
                }
            }else if(s>=0&&s<.3f){
                // on the ground: a ring running out and pixels splashing up (far off its ring only, fewer sided, and none past LANDS_SEEN)
                Vec3d at=new Vec3d(sp.x(),ground+.05,sp.z());double d2=c.camera.squaredDistanceTo(at);
                if(d2<LANDS_SEEN*LANDS_SEEN&&(view==null||view.sees(at,LAND_REACH))){
                    boolean coarse=d2>FAR*FAR;float near=GlitchFx.near(at,c.camera),a=(1-s/.3f)*near;
                    groundRing(m,at,.08+.62*Ease.out(s,0,.22f),.12,a,light,coarse?LAND_SIDES_FAR:LAND_SIDES);
                    if(!coarse){
                        if(light&&s<.06f)m.star(at.add(0,.1,0),.36,LILAC,.6f*(1-s/.06f)*near);
                        GlitchFx.pixels(m,at.add(0,.06,0),UP,.85,2.8,.033,3,seed+k*7,s,light,c.camera);
                    }
                }
            }
            // going: broken up into pixels where it stood (near enough to be seen)
            float gs=(age-(lands+BladeRain.STUCK-BladeRain.GOING))/20f;
            if(gs>=0&&gs<.35f){
                Vec3d stood=new Vec3d(sp.x(),land+BladeRain.TIP*.6,sp.z());
                if(c.camera.squaredDistanceTo(stood)<FAR*FAR&&(view==null||view.sees(stood,LAND_REACH)))GlitchFx.pixels(m,stood,UP,.9,2.2,.032,4,seed+k*11+5,gs,light,c.camera);
            }
        }
        count(c,drawn,System.nanoTime()-t0,light);
    }
    /** Every few volleys the knife's own burst on them (near its full size). */
    static void bursts(Fx c,EffectMesh m,boolean light,net.minecraft.entity.LivingEntity e,float stop,int seed){
        float age=c.age;Vec3d mid=e.getLerpedPos(c.delta).add(0,e.getHeight()*.55,0);
        for(int v=0;v<RainRules.VOLLEYS-1;v+=BURST_EVERY){
            float at=RainRules.volleyAt(v),s=(age-at)/20f;if(s<0||at>stop||s>=SlashFx.SWIRL_LIFE)continue;
            SlashFx.burst(m.scaled(SlashFx.burstAt(mid,c.camera),BURST_SIZE),mid,BladeRain.DOWN,HACK_SLASH,seed+v,s,light,c.camera);
        }
    }

    // ───────────────────────────── the last volley, and drawn out ─────────────────────────────

    /** How long the pillar of light up out of them lasts (seconds) and how high it goes (blocks). */
    static final float PILLAR_LIFE=.42f;static final double PILLAR=11;
    /** The last volley ({@code s} seconds after it): the knife's own burst twice as big, everything bursting out of their whole body, a pillar of light, rings over the ground, pixels. */
    static void last(Fx c,EffectMesh m,boolean light,KnifeCombo.Struck st,float s,int seed){
        Vec3d feet=st.feet(),mid=st.mid(),g=feet.add(0,.06,0);
        Vec3d way=c.f;
        if(s<SlashFx.SWIRL_LIFE)SlashFx.burst(m.scaled(SlashFx.burstAt(mid,c.camera),LAST_BURST),mid,way,HACK_LUNGE,seed+RainRules.VOLLEYS-1,s,light,c.camera);
        float t=s*20;
        if(t<DanceBurst.FINALE_LIFE){if(light)DanceBurst.finale(m,mid,seed,t,1,c.camera);else{DanceBurst.finale(m,mid,seed,t,0,c.camera);DanceBurst.finale(m,mid,seed,t,2,c.camera);}}
        for(int r=0;r<3;r++){
            float tr=s-.05f*r;if(tr<0||tr>=.7f)continue;
            groundRing(m,g,(r==0?8.5:r==1?13:5.5)*Ease.out(tr,0,.7f)+.4,r==2?.32:.52,1-tr/.7f,light);
        }
        if(s<PILLAR_LIFE){
            float a=Ease.smooth(s,0,.03f)*(1-Ease.smooth(s,.08f,PILLAR_LIFE))*DanceBurst.near(mid,c.camera);
            Vec3d top=feet.add(0,PILLAR*Ease.out(s,0,.1f),0);
            if(light){m.softTaper(feet,top,1.8,.35,GLOW,.5f*a);m.softTaper(feet,top,.34,.06,WHITE,.8f*a);}
            else{m.needle(feet,top,.82,.15,DEEP,.8f*a);m.needle(feet,top,.55,.15,PURPLE,.85f*a);}
        }
        if(light&&s<.14f)m.softDisc(g.add(0,.3,0),4.5,GLOW,.32f*(1-s/.14f));
        if(!light&&s<1.6f)patch(m,g,3.1,NAVY,.5f*Ease.smooth(s,0,.04f)*(1-Ease.smooth(s,.6f,1.6f)),seed+7);
        GlitchFx.pixels(m,mid,UP,1,7,.07,48,seed+997,s,light,c.camera);
    }
    /** Drawn out ({@code s} seconds after): a spurt of purple out of them back along the knife and a ring where it comes out. */
    static void withdrawn(Fx c,EffectMesh m,boolean light,KnifeCombo.Struck st,float s,int seed){
        Vec3d[] k=knife(c,OUT_AT);
        var p=through(st,k[0],k[1].negate());
        erupt(m.scaled(st.feet().add(p.out()),.65),st.feet(),p,9,seed+3,s,false,light,c.camera);
        Vec3d out=st.feet().add(p.out());
        if(s<.22f)facingRing(m,out,c.camera,.15+.8*Ease.out(s,0,.2f),light?.1:.07,light?GLOW:PURPLE,(light?.55f:.85f)*(1-s/.22f),light);
        GlitchFx.pixels(m,out,k[1].negate().add(0,.3,0).normalize(),.55,5,.05,24,seed+801,s,light,c.camera);
    }

    // ───────────────────────────── the passes ─────────────────────────────

    /** Everything of the run, the stab and the rain but the body and the knives' models, in one pass (the blended bodies, or the light). */
    static void draw(Fx c,EffectMesh m,boolean light){
        if(c.cast.combo()==RUSH){run(c,m,light);return;}
        if(c.cast.combo()!=RAIN)return;
        float sec=c.sec,stop=BladeRain.until(c.cast),stopS=stop/20f;int seed=BladeRain.seed(c.cast);
        if(sec>=.1f&&sec<STAB_AT+.03f&&sec<stopS){Vec3d[] k=knife(c,sec);charge(c,m,light,k[0],k[1],sec,seed);}
        float s0=sec-STAB_AT;
        if(s0>=0&&STAB_AT*20<=stop&&KnifeClient.cameTo(c.cast,0))stabbed(c,m,light,s0,seed);
        var e=BladeRain.stabbed(c.cast);if(e==null)return;
        var all=KnifeCombo.struck(c,0);if(all.isEmpty())return;
        var st=all.get(0);
        held(c,m,light,st,sec,stopS,seed);
        sky(m,st.feet().add(0,SKY_UP,0),sec,skyOn(sec,stopS),beat(sec,stopS),seed,light);
        knives(c,m,light,e,stop,seed);
        bursts(c,m,light,e,stop,seed);
        float sl=(c.age-RainRules.LAST)/20f;
        if(sl>=0&&RainRules.LAST<=stop)last(c,m,light,st,sl,seed);
        float sw=sec-OUT_AT;
        if(sw>=0&&sw<.5f&&RainRules.WITHDRAW<=stop)withdrawn(c,m,light,st,sw,seed);
    }
    // ───────────────────────────── the screen inside the rain ─────────────────────────────
    // (2.132.1, user request: "비가 떨어지는 범위를 넓게 바꾸고 검이 떨어지는 비 내부에는 엄청난 화면효과를 넣어줘": the one
    // place in X Chara's skills with a screen effect, asked for)

    /** The impact frames inside the rain: X Chara's purple (deep, its shapes white; then pale with dark shapes). */
    static final FxFrame.ImpactLook LOOK=new FxFrame.ImpactLook(0x16002e,0xfff4ff,0xf8eeff,0x12001f,0x6c18c8);
    /**
     * How much one standing at {@code viewer} (their feet) is inside the rain {@code age} ticks into the stab (stopped
     * short at {@code stop}), the one stabbed standing at {@code feet}: within the ground the knives come down on (ZONE
     * of them, 2.132.4: 30 blocks; softly over its last block and a half) and under the circle in the sky; from as the
     * first knife comes out of the sky until a little after the last lands. It is the body the screen sees from that is
     * measured, not where its camera is (as Lee So-sang's H's night, 2.99.1).
     */
    static float inside(Vec3d viewer,Vec3d feet,float age,float stop){
        float first=RainRules.FIRST-BladeRain.BEFORE,end=Math.min(RainRules.LAST,stop);
        float on=Ease.smooth(age,first,first+5)*(1-Ease.smooth(age,end+2,end+10));
        if(on<=0)return 0;
        float r=(float)Math.hypot(viewer.x-feet.x,viewer.z-feet.z),up=(float)(viewer.y-feet.y);
        float in=1-Ease.smooth(r,(float)BladeRain.ZONE-1.5f,(float)BladeRain.ZONE+.5f);
        float under=1-Ease.smooth(up,(float)SKY_UP-1,(float)SKY_UP+1);
        return on*in*under;
    }
    /** An impact frame's strength {@code s} seconds after it starts, held {@code hold} (coming in over a frame, going over a few). */
    static float flashed(float s,float hold){return s<0?0:Ease.smooth(s,0,.02f)*(1-Ease.smooth(s,hold,hold+.06f));}
    /**
     * The screen inside the rain: violet, its edges dark, torn in slices, streaked with light falling down it, scanned
     * (FxFrame.rain, the effect layer's composite); colours split from its middle, harder with each burst; a short impact
     * frame with every burst (every BURST_EVERY volleys, white shapes and dark by turns), and as the last volley lands a
     * full one and a white-violet flash.
     */
    static void screen(Fx c,FxFrame f,Vec3d feet,float age,float stop){
        var body=c.mc.getCameraEntity();
        float in=inside(body!=null?body.getLerpedPos(c.delta):c.camera,feet,age,stop);if(in<=0)return;
        float sec=c.sec,stopS=stop/20f,b=beat(sec,stopS);
        float sl=(age-RainRules.LAST)/20f;boolean last=RainRules.LAST<=stop&&sl>=0;
        f.rain(in,b,last?Ease.smooth(sl,0,.03f)*(1-Ease.smooth(sl,.03f,.3f)):0);
        f.chroma(in*(.006f+.014f*b+(last?.02f*(1-Ease.smooth(sl,0,.3f)):0)));
        for(int v=0;v<RainRules.VOLLEYS-1;v+=BURST_EVERY){
            float at=RainRules.volleyAt(v)/20f;if(at*20>stop)continue;
            float k=flashed(sec-at,.03f);if(k>0)f.impact(in*.55f*k,(v/BURST_EVERY)%2,LOOK);
        }
        if(last){float k=flashed(sl,.11f);if(k>0)f.impact(in*k,sl<.055f?0:1,LOOK);}
    }

    /** Its light on the world; and little afterglow where things go fast (the run, the knives in flight: their light would leave copies of them). */
    static void frame(Fx c,FxFrame f){
        if(c.cast.combo()==RUSH){
            var way=KnifeClient.rainWay(c.cast.id());if(way==null)return;
            float age=c.age,stop=BladeRain.until(c.cast);
            if(age<Math.min(stop,RainRules.RUN_TO+2)){f.light(BladeRain.ran(way.from(),way.to(),age).add(0,1,0),0x9a3cff,7,.6f);f.afterglow(.06f);}
            float s=(age-RainRules.RUN_FROM)/20f;if(s>=0&&s<.3f)f.light(way.from().add(0,.6,0),0xb060ff,9,1f*(1-s/.3f));
            return;
        }
        if(c.cast.combo()!=RAIN)return;
        float age=c.age,sec=c.sec,stop=BladeRain.until(c.cast),stopS=stop/20f;
        if(age<stop&&age>2&&age<RainRules.STAB+2)f.afterglow(.08f);
        var e=BladeRain.stabbed(c.cast);if(e==null)return;
        Vec3d mid=e.getLerpedPos(c.delta).add(0,e.getHeight()*.55,0);
        screen(c,f,e.getLerpedPos(c.delta),age,stop);
        float s0=sec-STAB_AT;if(s0>=0&&s0<.35f&&STAB_AT*20<=stop)f.light(mid,0xb060ff,20,1.8f*(1-s0/.35f));
        float on=bearing(sec,stopS),beat=beat(sec,stopS);
        if(on>0){
            f.light(mid,0x9a3cff,12,(.7f+.45f*beat)*on);
            f.light(e.getLerpedPos(c.delta).add(0,.2,0),0x8a2be2,6,.5f*on);
            Vec3d[] k=knife(c,sec);f.light(k[0],0x9a3cff,4,.45f*on);
        }
        float sl=(age-RainRules.LAST)/20f;if(sl>=0&&sl<.55f&&RainRules.LAST<=stop)f.light(mid,0xb060ff,30,2.6f*(1-sl/.55f));
        float sw=sec-OUT_AT;if(sw>=0&&sw<.3f&&RainRules.WITHDRAW<=stop)f.light(mid,0x9a3cff,10,1f*(1-sw/.3f));
        float first=RainRules.FIRST-BladeRain.BEFORE,end=Math.min(RainRules.LAST,stop);
        if(age>=first&&age<end+BladeRain.STUCK)f.afterglow(.06f);
    }
    /** How hard it shakes the camera: a knock as they spring off, as the knife goes in, each volley as it lands, the last hard, and as it is torn out. */
    static final float SHAKE_SPRING=.3f,SHAKE_STAB=1.25f,SHAKE_VOLLEY=.22f,SHAKE_LAST=2.3f,SHAKE_OUT=.4f;
    static float shake(StandClient.Cast cast,float sec){
        if(cast.combo()==RUSH)return SHAKE_SPRING*KnifeCombo.bump(sec,RainRules.RUN_FROM/20f,.08f);
        if(cast.combo()!=RAIN||!KnifeClient.cameTo(cast,0))return 0;
        int[] told=KnifeClient.struck(cast.id(),0);boolean hit=told!=null&&told.length>0;
        float sum=SHAKE_STAB*(hit?1:.33f)*KnifeCombo.bump(sec,STAB_AT,.08f);
        if(!hit)return sum;
        float stop=BladeRain.until(cast)/20f;
        int near=Math.round(sec*20)-RainRules.FIRST;
        for(int v=Math.max(0,near-6);v<=Math.min(RainRules.VOLLEYS-1,near+1);v++){
            float at=RainRules.volleyAt(v)/20f;if(at>stop)continue;
            boolean last=v==RainRules.VOLLEYS-1;
            sum+=(last?SHAKE_LAST:v%BURST_EVERY==0?SHAKE_VOLLEY*2:SHAKE_VOLLEY)*KnifeCombo.bump(sec,at,last?.1f:.04f);
        }
        if(OUT_AT<=stop)sum+=SHAKE_OUT*KnifeCombo.bump(sec,OUT_AT,.06f);
        return sum;
    }

    // ───────────────────────────── the ground ─────────────────────────────

    /** The floor round them as the last volley lands: a crater's rim thrown up (blocks out from them), a ripple out to here, all of it gone after this long (seconds). */
    static final double CRATER=2.7,RIPPLE=9.5;static final float GROUND_LIFE=2.2f;static final int CHUNKS=30;
    /** How high the floor block {@code r} blocks from them is heaved {@code s} seconds after the last volley (blocks) and how far it is tipped away (degrees): nothing under them, most two blocks out, sinking back; a ripple beyond. */
    static double[] heave(double r,float s,double h,double h2){
        if(s<=0||s>=GROUND_LIFE)return new double[]{0,0};
        if(r<CRATER){
            double stand=MathHelper.clamp((r-.9)/.7,0,1);if(stand<=0)return new double[]{0,0};
            double rise=Ease.smooth(s,0,.06f),down=1-Ease.smooth(s,1.0f,1.8f);
            double crest=Math.sin(Math.PI*MathHelper.clamp((r-.6)/(CRATER-.6),0,1));crest*=crest;
            return new double[]{(.1+.7*crest+.22*h)*stand*rise*down,(10+22*h2)*(.4+.6*crest)*rise*down};
        }
        if(r>RIPPLE)return new double[]{0,0};
        double edge=1-Ease.smooth((float)r,(float)RIPPLE-2.5f,(float)RIPPLE);
        return new double[]{FxSolids.wave(r,s,.62,13,1.1)*(.7+.6*h)*edge,0};
    }
    /** One piece of the floor thrown by the last volley, {@code s} seconds after it: {where it is, its size, how far it has spun (degrees)}, or null once it is gone. */
    static double[] chunk(Vec3d at,int j,int seed,float s){
        float ss=s-.01f*(j%6);if(ss<0||ss>=2.2f)return null;
        double a=Math.PI*2*(j+h(j,seed,71))/CHUNKS*3,r=.9+2.0*h(j,seed,72);
        double size=(.16+.38*h(j,seed,73)*h(j,seed,74))*(1-Ease.smooth(ss,1.7f,2.2f));
        Vec3d out=new Vec3d(Math.cos(a),0,Math.sin(a)),from=at.add(out.multiply(r)).add(0,.2,0);
        Vec3d v=out.multiply(2+6*h(j,seed,75)).add(0,6+8*h(j,seed,76),0);
        Vec3d p=FxSolids.flight(from,v,at.y+size*.5,ss);
        return new double[]{p.x,p.y,p.z,size,(300+500*h(j,seed,77))*Math.min(ss,1.3)};
    }
    /** The floor itself as the last volley lands: its blocks round them heaved up and tipped, a ripple running out, pieces of it thrown (drawn only: no block is changed). */
    static void solids(Fx c,FxSolids so){
        if(c.cast.combo()!=RAIN)return;
        float stop=BladeRain.until(c.cast);if(RainRules.LAST>stop||BladeRain.stabbed(c.cast)==null)return;
        float s=(c.age-RainRules.LAST)/20f;if(s<0||s>=GROUND_LIFE)return;
        var all=KnifeCombo.struck(c,0);if(all.isEmpty())return;
        Vec3d at=all.get(0).feet();int seed=BladeRain.seed(c.cast)+5;
        int y=(int)Math.floor(at.y-.5),reach=(int)Math.ceil(RIPPLE+1),bx=MathHelper.floor(at.x),bz=MathHelper.floor(at.z);
        for(int ix=bx-reach;ix<=bx+reach;ix++)for(int iz=bz-reach;iz<=bz+reach;iz++){
            double dx=ix+.5-at.x,dz=iz+.5-at.z,r=Math.hypot(dx,dz);if(r>RIPPLE||r<1e-3)continue;
            var hv=heave(r,s,Ease.hash(ix*7349+iz*911),Ease.hash(ix*131+iz*5923+17));
            if(hv[0]<=.02)continue;
            var pos=new BlockPos(ix,y,iz);var state=so.surface(pos);if(state==null)continue;
            so.lifted(pos,state,hv[0],(float)hv[1],new Vec3d(dz/r,0,-dx/r).multiply(-1));
        }
        var ground=so.ground(at.add(0,-.5,0));if(ground==null)return;
        for(int j=0;j<CHUNKS;j++){
            var q=chunk(at,j,seed,s);if(q==null||q[3]<=.02)continue;
            so.chunk(ground,new Vec3d(q[0],q[1],q[2]),q[3],(float)q[4],(float)(q[4]*.7),(float)(q[4]*.4));
        }
    }
}
