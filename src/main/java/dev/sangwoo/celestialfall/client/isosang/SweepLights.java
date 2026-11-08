package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.*;

/**
 * R's moonlight and starlight (2.102.0, user request: "이번에는 R스킬 예쁘고 멋있게 해줘"), as her sword's (월흔, the moon's
 * trace), the formation's (FormationLights, and FormationSky's sword in the stars, 검자리), T's (LungeLights) and H again's
 * (FinaleLights):
 * <ul>
 * <li>before the giant sword is called: motes of light drawn in to where its pommel will hang, and its own seven stars
 * lit there one by one in its shape, pommel to point, threads of light drawn between them as the sword in the
 * formation's night sky is, each flaring as the lance of light runs through it, then given up to the sword that forms in
 * them ({@link #gather}, {@link #constellation}); rings of light where the lance shoots out and where it stops
 * ({@link #lanceRings});</li>
 * <li>as it forms and cools from white: a shine running down its blade from its guard to its point and motes of light
 * shed off it ({@link #shine}, {@link #cooling});</li>
 * <li>as it hangs (cool: charged): lightning creeping over its edges, gathering until it goes and gone as it crosses the
 * front ({@link #lightning}); a crescent moon in its guard's gem, flaring as it goes and as it crosses the front
 * ({@link #gemMoon}); the line its point will take drawn in light on the ground, round from its back across the front to
 * the left, each part going as its point passes there and lays its ring of ink ({@link #guide});</li>
 * <li>as it sweeps: a crescent moon riding its point and stardust left along its point's way, the moon's trace
 * ({@link #tipMoon}, {@link #trace}); starry sparks springing off its edge ({@link #sparks}); crystal petals shed off
 * it, drifting down and catching the light ({@link #petals}); as it crosses the front, rings of light running out over
 * the ground from under its pommel ({@link #shockwave});</li>
 * <li>under whom it strikes: a ring of light on the ground and stars rising off them ({@link #struck}); where she comes
 * down in her crouch, a ring round her feet and moon dust ({@link #landing});</li>
 * <li>as it goes into ink: its last shine down its blade, motes of light rising off it and a small crescent moon rising
 * from its gem ({@link #shine}, {@link #parting}, {@link #risingMoon});</li>
 * <li>its crescent: moon dust sprayed off its edge as it is eaten ({@link #crescentDust}); the ground its ink darkened:
 * faint stars in the ink ahead, a night lying on the ground until it dries ({@link #groundStars}).</li>
 * </ul>
 * None of what the user's videos set is changed (the giant sword, its sweep and timing, its crescent, fan, streaks and
 * ink, her body, the camera, the sound) but its head's glint, made smaller for the moon riding there; nothing white-hot
 * over the screen, and no impact frame (2.102.1, user request: "화면 효과는 빼줘 R도 빼고 T도 빼", then "흔들림은 안 빼도
 * 돼": 2.102.0's blue impact frame taken out, R's shake kept). Every one is a function of R's clock and of where the server
 * put the sweep, the same for everyone.
 */
final class SweepLights {
    private SweepLights(){}

    static final double SCALE=GiantSweep.SCALE;
    static final float START=(float)IsosangRules.SWEEP_START,FRONT=(float)IsosangRules.SWEEP_FRONT,END=(float)IsosangRules.SWEEP_END;

    /** The blended pass: the crystal petals. */
    static void alpha(Fx c,EffectMesh m){
        petals(c,m,c.sec,false);
    }
    /** The light pass (under the ink): what is drawn before the sweep, on the sword and off it. */
    static void additive(Fx c,EffectMesh m){
        float t=c.sec;
        gather(c,m,t);
        constellation(c,m,t);
        lanceRings(c,m,t);
        shine(c,m,t,SHINE0,SHINE1);
        shine(c,m,t,LAST0,LAST1);
        cooling(c,m,t);
        gemMoon(c,m,t);
        petals(c,m,t,true);
        parting(c,m,t);
        risingMoon(c,m,t);
        crescentDust(c,m,t);
    }
    /** Over the ink (the crescent and the ground's ink are ink ribbons, drawn after the light pass): what must show over it. */
    static void overlay(Fx c,EffectMesh m){
        float t=c.sec;
        guide(c,m,t);
        lightning(c,m,t);
        trace(c,m,t);
        tipMoon(c,m,t);
        sparks(c,m,t);
        shockwave(c,m,t);
        struck(c,m,t);
        landing(c,m,t);
        groundStars(c,m,t);
    }

    // ───────────────────────────── where things are ─────────────────────────────

    /** The giant sword's grip and the way it points at R's second {@code t}, shown or not yet (where GiantSweep.giant puts it, without its bob). */
    static Vec3d[] sword(Vec3d pivot,float yaw,float t){
        Vec3d d=GiantSweep.dir(yaw,GiantSweep.angle(t));
        return new Vec3d[]{pivot.add(d.multiply(-SpiritSword.POMMEL*SCALE)),d};
    }
    /** A point of the giant sword in its own frame (SpiritSword's: across its flat, along it from its grip; blocks at full size) at {@code t}. */
    static Vec3d onSword(Vec3d pivot,float yaw,float t,double across,double along){
        Vec3d[] s=sword(pivot,yaw,t);return SpiritSword.world(s[0],SpiritSword.frame(s[1]),SCALE,v(across,along,0));
    }
    /** Its point on the ground under its pommel. */
    static Vec3d under(Fx c){Vec3d p=GiantSweep.pivot(c.cast);return new Vec3d(p.x,GiantSweep.floor(c.cast),p.z);}

    // ───────────────────────────── before it is called ─────────────────────────────

    /**
     * The sword's seven stars in its own frame (across, along: SpiritSword's): its pommel, the top of its grip, its guard's
     * points, its gem, its blade's middle and its point, as the sword in the formation's night sky (FormationSky's).
     */
    static final double[][] STARS={{0,SpiritSword.POMMEL},{0,SpiritSword.GRIP_HIGH},{-.92*SpiritSword.GUARD,SpiritSword.GUARD_POINT},{0,SpiritSword.GEM_AT},
        {.92*SpiritSword.GUARD,SpiritSword.GUARD_POINT},{0,(SpiritSword.BLADE_BASE+SpiritSword.TIP)*.5},{0,SpiritSword.TIP}};
    /** The threads between them (FormationSky.SWORD_LINES), each drawn from the star lit first. */
    static final int[][] LINES={{0,1},{1,3},{3,5},{5,6},{3,2},{3,4}};
    /** Each star's turn: from its pommel to its point, the guard's points with the gem's next. */
    static final int[] RANK={0,1,3,2,3,4,5};
    /** When the first star is lit, how far apart they are, how long a thread takes, and when they are given up to the sword (seconds into R). */
    static final float CON0=.03f,CON_STEP=.014f,CON_DRAW=.03f,CON_OUT0=GiantSweep.FORM0+.03f,CON_OUT1=GiantSweep.FORM1+.04f;
    static float lit(int k){return CON0+CON_STEP*RANK[k];}
    /** Star {@code k} of the sword's constellation (world): where the sword will be, its pommel on the pivot. */
    static Vec3d star(Vec3d pivot,float yaw,int k){return onSword(pivot,yaw,CON0,STARS[k][0],STARS[k][1]);}
    /** How strongly star {@code k} shows at {@code t}: lit in its turn, given up to the sword as it forms. */
    static float starShown(int k,float t){return Ease.smooth(t,lit(k),lit(k)+.025f)*(1-Ease.smooth(t,CON_OUT0,CON_OUT1));}
    /** How bright the lance's head makes star {@code k} as it runs through it (0 to 1). */
    static float flare(int k,float t){
        if(t<GiantSweep.LANCE0-.01f||t>GiantSweep.LANCE1+.08f)return 0;
        double len=SpiritSword.LENGTH*SCALE,head=len*Ease.out(t,GiantSweep.LANCE0,GiantSweep.LANCE1),s=(STARS[k][1]-SpiritSword.POMMEL)*SCALE;
        return (float)Math.max(0,1-Math.abs(head-s)/1.6)*(1-Ease.smooth(t,GiantSweep.LANCE1+.02f,GiantSweep.LANCE1+.08f));
    }
    /** The sword's stars and the threads between them, where it is about to be called. */
    static void constellation(Fx c,EffectMesh m,float t){
        if(t<CON0-.01f||t>=CON_OUT1)return;
        Vec3d pivot=GiantSweep.pivot(c.cast);float yaw=GiantSweep.yaw(c.cast);
        Vec3d[] p=new Vec3d[STARS.length];for(int k=0;k<p.length;k++)p[k]=star(pivot,yaw,k);
        float out=1-Ease.smooth(t,CON_OUT0,CON_OUT1);
        for(int[] l:LINES){
            int a=RANK[l[0]]<=RANK[l[1]]?l[0]:l[1],b=a==l[0]?l[1]:l[0];
            float from=Math.max(lit(a),lit(b)),drawn=Ease.out(t,from,from+CON_DRAW);if(drawn<=0)continue;
            float la=out*Ease.smooth(t,from,from+.012f)*near(p[a].lerp(p[b],.5),c.camera);if(la<=.01f)continue;
            Vec3d e=p[a].lerp(p[b],drawn);
            m.line(p[a],e,.035,PALE,.62f*la);m.softLine(p[a],e,.2,AZURE,.2f*la);
        }
        for(int k=0;k<p.length;k++){
            float a=starShown(k,t)*near(p[k],c.camera);if(a<=.01f)continue;
            float fl=flare(k,t),tw=.75f+.25f*twinkle(t,k*31+5);
            boolean end=k==0||k==6;
            glint8(m,p[k],(end?.28:.2)+.24*fl,end?WHITE:PALE,Math.min(1,(.85f+.5f*fl)*tw*a));
            m.softDisc(p[k],.26+.3*fl,AZURE,.2f*a);
        }
    }
    /** Motes of light drawn in to where the giant sword's pommel will hang, before its flash (each over a tenth of a second, a short trail behind it). */
    static void gather(Fx c,EffectMesh m,float t){
        if(t>GiantSweep.FLASH+.03f)return;
        Vec3d pivot=GiantSweep.pivot(c.cast);
        for(int i=0;i<GATHER;i++){
            int seed=i*53+1201;float born=.01f+.08f*(float)Ease.hash(seed),u=Ease.unit(t,born,born+GATHER_LIFE);if(t<born||u>=1)continue;
            double az=Math.PI*2*Ease.hash(seed+1),el=(Ease.hash(seed+2)-.5)*1.6,r0=1.4+1.1*Ease.hash(seed+3);
            Vec3d dir=new Vec3d(Math.cos(el)*Math.cos(az),Math.sin(el),Math.cos(el)*Math.sin(az));
            double r=.15+(r0-.15)*(1-Math.pow(u,1.5));
            Vec3d q=pivot.add(dir.multiply(r)),tail=pivot.add(dir.multiply(Math.min(r0,r+.35*(1-u)+.05)));
            float a=Ease.smooth(u,0,.25f)*(1-Ease.smooth(u,.8f,1))*near(q,c.camera);if(a<=.01f)continue;
            int col=i%3==0?WHITE:i%3==1?LILAC:PALE;
            m.softTaper(tail,q,0,.06,col,.45f*a);m.softDisc(q,.07,WHITE,.8f*a);
        }
    }
    static final int GATHER=14;
    static final float GATHER_LIFE=.1f;
    /** Rings of light square to the lance: round where it shoots out of the flash, and round where its head stops. */
    static void lanceRings(Fx c,EffectMesh m,float t){
        float l0=GiantSweep.LANCE0,l1=GiantSweep.LANCE1;if(t<l0-.01f||t>l1+.22f)return;
        Vec3d pivot=GiantSweep.pivot(c.cast),d=GiantSweep.dir(GiantSweep.yaw(c.cast),GiantSweep.angle(t));Vec3d[] f=SpiritSword.frame(d);
        float a=Ease.smooth(t,l0-.01f,l0+.01f)*(1-Ease.unit(t,l0,l0+.18f));
        if(a>.01f){
            FxLib.ringPlane(m,pivot,f[0],f[2],.3+1.5*Ease.out(t,l0,l0+.16f),.05,PALE,.5f*a);
            FxLib.ringPlane(m,pivot,f[0],f[2],.2+.9*Ease.out(t,l0,l0+.12f),.025,WHITE,.4f*a*(1-Ease.unit(t,l0,l0+.12f)));
        }
        float e=Ease.smooth(t,l1-.01f,l1+.01f)*(1-Ease.unit(t,l1,l1+.2f));
        if(e>.01f){
            Vec3d end=pivot.add(d.multiply(SpiritSword.LENGTH*SCALE));
            FxLib.ringPlane(m,end,f[0],f[2],.25+1.2*Ease.out(t,l1,l1+.18f),.045,AZURE,.5f*e);
            glint8(m,end,.35*(1-Ease.unit(t,l1,l1+.15f)),WHITE,.6f*e);
        }
    }

    // ───────────────────────────── as it forms and hangs ─────────────────────────────

    /** When its shine runs down it as it cools from white, and its last as it goes into ink (seconds into R). */
    static final float SHINE0=.24f,SHINE1=.42f,LAST0=.66f,LAST1=.8f;
    /** How far down its blade a shine running from {@code from} to {@code to} is at {@code t} (0 at its guard, 1 at its point), or -1 when none runs. */
    static double shineAt(float t,float from,float to){float u=Ease.unit(t,from,to);return u<=0||u>=1?-1:u;}
    /** A shine running down the giant sword's blade from its guard to its point: a band of light across its flat on the camera's side. */
    static void shine(Fx c,EffectMesh m,float t,float from,float to){
        double u=shineAt(t,from,to);if(u<0)return;
        var g=GiantSweep.giant(c.cast,t);if(g==null)return;
        double y=SpiritSword.BLADE_BASE+(SpiritSword.TIP-SpiritSword.BLADE_BASE)*u,w=SpiritSword.half(y);if(w<=0)return;
        Vec3d[] f=SpiritSword.frame(g.dir());Vec3d mid=SpiritSword.world(g.grip(),f,SCALE,v(0,y,0));
        double side=f[2].dotProduct(c.camera.subtract(mid))>=0?1:-1,z=side*SpiritSword.FLAT_Z*1.6;
        Vec3d a=SpiritSword.world(g.grip(),f,SCALE,v(-.95*w,y,z)),b=SpiritSword.world(g.grip(),f,SCALE,v(.95*w,y,z));
        float al=g.alpha()*(1-.8f*g.ink())*(float)Math.sin(Math.PI*u);if(al<=.01f)return;
        m.softLine(a,b,.5,PALE,.32f*al);m.line(a,b,.06,WHITE,.6f*al);
        glint8(m,SpiritSword.world(g.grip(),f,SCALE,v(0,y,z)),.3,WHITE,.45f*al);
    }
    /** Motes of light shed off the blade as it cools from white: from along it and its edges, drifting down and out, twinkling. */
    static void cooling(Fx c,EffectMesh m,float t){
        if(t<COOL0||t>COOL0+COOL_SPAN+COOL_LIFE)return;
        Vec3d pivot=GiantSweep.pivot(c.cast);float yaw=GiantSweep.yaw(c.cast);
        for(int i=0;i<COOL;i++){
            int seed=i*61+1409;float born=COOL0+COOL_SPAN*(float)Ease.hash(seed),life=COOL_LIFE*(.7f+.3f*(float)Ease.hash(seed+1)),age=t-born;
            if(age<0||age>=life)continue;
            double y=SpiritSword.BLADE_BASE+(SpiritSword.TIP-SpiritSword.BLADE_BASE)*Ease.hash(seed+2),x=(2*Ease.hash(seed+3)-1)*SpiritSword.half(y);
            Vec3d p=onSword(pivot,yaw,born,x,y).add((Ease.hash(seed+4)-.5)*.4*age,-.45*age,(Ease.hash(seed+5)-.5)*.4*age);
            float a=Ease.smooth(age,0,.04f)*(1-age/life)*twinkle(t,seed)*near(p,c.camera);if(a<=.01f)continue;
            glint8(m,p,.08+.05*Ease.hash(seed+6),i%3==0?WHITE:i%3==1?PALE:LILAC,.75f*a);
        }
    }
    static final int COOL=16;
    static final float COOL0=.2f,COOL_SPAN=.14f,COOL_LIFE=.7f;

    /**
     * How charged the giant sword is at {@code t} (0 to 1, its lightning): from as it hangs, gathering until it goes, gone as
     * it crosses the front.
     */
    static float charge(float t){
        return Ease.smooth(t,BOLT0,BOLT0+.06f)*(.55f+.45f*Ease.smooth(t,BOLT0+.04f,START))*(1-Ease.smooth(t,FRONT-.04f,FRONT+.07f));
    }
    static final float BOLT0=.3f;
    static final int BOLTS=4;
    /** Where bolt {@code s} runs over its {@code q}-th fifteenth of a second: from and to along the blade (SpiritSword's units), on the blade, a sixth to a third of it long. */
    static double[] boltSpan(int s,int q){
        int seed=s*131+q*17+2021;double lo=SpiritSword.BLADE_BASE+.1,hi=SpiritSword.TIP-.25,len=.45+.5*Ease.hash(seed+1);
        double y0=lo+(hi-lo-len)*Ease.hash(seed);return new double[]{y0,y0+len};
    }
    /** Which side of the blade and how far out one end of a bolt is (a share of the half width there, either side): just outside its edge. */
    static double boltOff(int seed){return (Ease.hash(seed)<.5?-1:1)*(1.05+.4*Ease.hash(seed+1));}
    /** Lightning creeping over the hanging sword's edges (white, lilac), each bolt new every fifteenth of a second, flickering. */
    static void lightning(Fx c,EffectMesh m,float t){
        float e=charge(t);if(e<=.01f)return;
        var g=GiantSweep.giant(c.cast,t);if(g==null)return;e*=g.alpha()*(1-g.ink());
        Vec3d[] f=SpiritSword.frame(g.dir());int q=(int)Math.floor(t*15),flick=(int)Math.floor(t*24);
        // a faint lilac charge along the blade, beating with the flicker
        Vec3d base=g.at(SpiritSword.BLADE_BASE),top=g.at(SpiritSword.TIP*.96);
        m.softLine(base,top,.9,LILAC,.09f*e*(.7f+.3f*(float)Ease.hash(flick*13+5)));
        for(int s=0;s<BOLTS;s++){
            int seed=s*131+q*17+2021;double[] span=boltSpan(s,q);
            Vec3d a=SpiritSword.world(g.grip(),f,SCALE,v(boltOff(seed+2)*SpiritSword.half(span[0]),span[0],0));
            Vec3d z=SpiritSword.world(g.grip(),f,SCALE,v(boltOff(seed+5)*SpiritSword.half(span[1]),span[1],0));
            float fl=e*(.6f+.4f*(float)Ease.hash(seed+8+flick*7))*near(a.lerp(z,.5),c.camera);if(fl<=.01f)continue;
            SwordFormation.bolt(m,a,z,seed,t,BOLT_JAG,BOLT_WIDTH,WHITE,LILAC,fl);
            if(Ease.hash(seed+9)<.55){
                double yb=(span[0]+span[1])*.5,sg=Ease.hash(seed+10)<.5?-1:1;
                Vec3d pb=SpiritSword.world(g.grip(),f,SCALE,v(sg*SpiritSword.half(yb),yb,0));
                Vec3d end=SpiritSword.world(g.grip(),f,SCALE,v(sg*(SpiritSword.half(yb)+.28+.2*Ease.hash(seed+11)),yb+(Ease.hash(seed+12)-.5)*.3,(Ease.hash(seed+13)-.5)*.3));
                SwordFormation.bolt(m,pb,end,seed+14,t,BOLT_JAG*.55,BOLT_WIDTH*.75,WHITE,LILAC,.75f*fl);
            }
        }
    }
    static final double BOLT_JAG=.3,BOLT_WIDTH=.075;
    /** A crescent moon of light in its guard's gem while it hangs and sweeps, flaring as it goes and as it crosses the front. */
    static void gemMoon(Fx c,EffectMesh m,float t){
        var g=GiantSweep.giant(c.cast,t);if(g==null)return;
        float a=Ease.smooth(t,GiantSweep.FORM1-.02f,GiantSweep.FORM1+.06f)*(1-Ease.smooth(t,GiantSweep.FADE0-.04f,GiantSweep.FADE0+.03f))*g.alpha();if(a<=.01f)return;
        float fl=Math.min(1,FxLib.kick(t,START,.1f)+FxLib.kick(t,FRONT,.12f));
        Vec3d p=g.at(SpiritSword.GEM_AT),to=c.camera.subtract(p);if(to.lengthSquared()<1e-6)return;
        Vec3d q=p.add(to.normalize().multiply(.18));
        crescentMoon(m,q,.27+.13*fl,Math.PI*.5+.3*Math.sin(t*2),MOON,(.7f+.3f*twinkle(t,91))*a*(.85f+.3f*fl));
        glint8(m,q,.15+.3*fl,WHITE,(.35f+.5f*fl)*a);
    }
    /** When the line its point will take is drawn on the ground, round from its back across the front (seconds into R). */
    static final float GUIDE0=.3f,GUIDE1=.42f;
    /** How far out it is drawn: where its ring of ink is laid (GiantSweep.RING). */
    static final double GUIDE_R=IsosangRules.SWEEP_REACH+.15;
    /** A point of the guide on the ground (local to the sweep's ground: its feet on the floor), at angle {@code deg} off its front (right +). */
    static Vec3d guidePoint(double deg){double a=Math.toRadians(deg);return v(IsosangRules.SWEEP_PIVOT_FW+GUIDE_R*Math.cos(a),.05,GUIDE_R*Math.sin(a));}
    /** How much of the guide is drawn at {@code t}, round from its back (0) to its end at the left (1). */
    static float guideDrawn(float t){return Ease.out(t,GUIDE0,GUIDE1);}
    /** How much of the guide shows at angle {@code deg} at {@code t}: drawn on, gone to ink as the point passes there. */
    static float guideShown(double deg,float t){
        double share=(IsosangRules.SWEEP_FROM-deg)/(IsosangRules.SWEEP_FROM-IsosangRules.SWEEP_TO);if(share>guideDrawn(t)+1e-6)return 0;
        float pass=GiantSweep.passes(deg);
        return Ease.smooth(t,GUIDE0,GUIDE0+.02f)*(1-Ease.smooth(t,pass-.01f,pass+.06f));
    }
    /** The line its point will take, drawn in light on the ground, little stars along it; each part going to ink as its point passes. */
    static void guide(Fx c,EffectMesh m,float t){
        if(t<GUIDE0-.01f||t>=END+.08f)return;
        Vec3d ground=GiantSweep.ground(c.cast);float yaw=GiantSweep.yaw(c.cast);double from=IsosangRules.SWEEP_FROM,to=IsosangRules.SWEEP_TO;
        float drawn=guideDrawn(t);double head=from+(to-from)*drawn;
        for(int i=0;i<GUIDE_PARTS;i++){
            double a=from+(to-from)*i/GUIDE_PARTS;if(a<head-1e-9)break;
            double b=Math.max(from+(to-from)*(i+1)/GUIDE_PARTS,head);
            float al=.5f*(guideShown(a,t)+guideShown(b,t));if(al<=.01f)continue;
            Vec3d pa=world(ground,yaw,guidePoint(a)),pb=world(ground,yaw,guidePoint(b));
            m.line(pa,pb,.07,PALE,.6f*al);m.softLine(pa,pb,.45,AZURE,.22f*al);
        }
        for(int k=0;k<=GUIDE_TICKS;k++){
            double deg=from+(to-from)*k/GUIDE_TICKS;float a=guideShown(deg,t);if(a<=.01f)continue;
            Vec3d p=world(ground,yaw,guidePoint(deg)).add(0,.08,0);
            glint8(m,p,.18,k%2==0?WHITE:PALE,.7f*a*twinkle(t,k*37+11)*near(p,c.camera));
        }
        // a glint where it is being drawn
        if(drawn<1){Vec3d p=world(ground,yaw,guidePoint(head)).add(0,.08,0);glint8(m,p,.24,WHITE,.7f*Ease.smooth(t,GUIDE0,GUIDE0+.02f)*near(p,c.camera));}
    }
    static final int GUIDE_PARTS=72,GUIDE_TICKS=8;

    // ───────────────────────────── as it sweeps ─────────────────────────────

    /** The angle of trace mote {@code k} (degrees off the front, right +): spread over the way its point goes. */
    static double traceDeg(int k){return IsosangRules.SWEEP_FROM+(IsosangRules.SWEEP_TO-IsosangRules.SWEEP_FROM)*(k+.2+.6*Ease.hash(k*79+601))/TRACE;}
    /** When trace mote {@code k} is left (seconds into R): as the point passes its angle. */
    static float traceBorn(int k){return GiantSweep.passes(traceDeg(k));}
    static float traceLife(int k){return TRACE_LIFE0+(TRACE_LIFE1-TRACE_LIFE0)*(float)Ease.hash(k*79+607);}
    /** Where trace mote {@code k} is {@code age} seconds after the point passed: where the point was, a little scattered, drifting down and out. */
    static Vec3d traceAt(Vec3d pivot,float yaw,int k,float age){
        int seed=k*79+601;double deg=traceDeg(k);
        Vec3d d=GiantSweep.dir(yaw,deg),out=new Vec3d(d.x,0,d.z).normalize();double r=IsosangRules.SWEEP_REACH*(.84+.16*Ease.hash(seed+1));
        return pivot.add(d.multiply(r)).add(out.multiply(.3*(Ease.hash(seed+2)-.5)+.18*age)).add(0,(Ease.hash(seed+3)-.5)*.45-.32*age,0);
    }
    static final int TRACE=64;
    static final float TRACE_LIFE0=.8f,TRACE_LIFE1=1.2f;
    /** The moon's trace: stardust left along its point's way as it sweeps, drifting down and fading. */
    static void trace(Fx c,EffectMesh m,float t){
        if(t<START||t>END+TRACE_LIFE1)return;
        Vec3d pivot=GiantSweep.pivot(c.cast);float yaw=GiantSweep.yaw(c.cast);
        for(int k=0;k<TRACE;k++){
            float born=traceBorn(k),life=traceLife(k),age=t-born;if(age<0||age>=life)continue;
            Vec3d p=traceAt(pivot,yaw,k,age);int seed=k*79+601;
            float a=Ease.smooth(age,0,.03f)*(1-Ease.smooth(age,.45f*life,life))*twinkle(t,seed)*near(p,c.camera);if(a<=.01f)continue;
            glint8(m,p,.08+.06*Ease.hash(seed+4),k%3==0?WHITE:k%3==1?PALE:LILAC,.8f*a);
        }
    }
    /** A crescent moon riding the giant sword's point as it sweeps (its horns back the way it came), as hit 4's falling moon rides its slash. */
    static void tipMoon(Fx c,EffectMesh m,float t){
        if(t<START||t>=END+.08f)return;
        var g=GiantSweep.giant(c.cast,t);if(g==null)return;
        float a=Ease.smooth(t,START,START+.02f)*(1-Ease.smooth(t,END+.01f,END+.08f))*g.alpha();if(a<=.01f)return;
        Vec3d p=g.at(SpiritSword.TIP*.97).add(0,.4,0),mv=GiantSweep.moving(GiantSweep.yaw(c.cast),GiantSweep.angle(t));
        Vec3d[] uv=cameraPlane(m,p);double turn=uv==null?0:Math.atan2(-mv.dotProduct(uv[1]),-mv.dotProduct(uv[0]));
        crescentMoon(m,p,.42,turn,MOON,.85f*a);m.softDisc(p,.55,AZURE,.25f*a);
    }
    /** When spark {@code i} springs off the blade (seconds into R): spread over the sweep. */
    static float sparkBorn(int i){return START+.01f+(END-START-.04f)*(i+(float)Ease.hash(i*67+803))/SPARKS;}
    static float sparkLife(int i){return SPARK_LIFE0+(SPARK_LIFE1-SPARK_LIFE0)*(float)Ease.hash(i*67+809);}
    /**
     * Where spark {@code i} is {@code age} seconds after it springs off the blade's edge: from out along the blade where it
     * was, flung out and on the way it sweeps and a little up, slowing quickly, then falling.
     */
    static Vec3d sparkAt(Vec3d pivot,float yaw,int i,float age){
        int seed=i*67+803;double deg=IsosangRules.sweepAngle(sparkBorn(i));
        Vec3d d=GiantSweep.dir(yaw,deg),out=new Vec3d(d.x,0,d.z).normalize(),mv=GiantSweep.moving(yaw,deg);
        Vec3d vel=out.multiply(1.2+1.8*Ease.hash(seed+2)).add(mv.multiply(1+1.6*Ease.hash(seed+3))).add(0,.5+.9*Ease.hash(seed+4),0);
        double r=IsosangRules.SWEEP_REACH*(.45+.5*Ease.hash(seed+1)),go=(1-Math.exp(-4.5*age))/4.5;
        return pivot.add(d.multiply(r)).add(vel.multiply(go)).add(0,-1.8*age*age,0);
    }
    static final int SPARKS=22;
    static final float SPARK_LIFE0=.35f,SPARK_LIFE1=.6f;
    /** Starry sparks springing off the blade's edge as it sweeps, a short streak behind each at first, twinkling, slowing and falling. */
    static void sparks(Fx c,EffectMesh m,float t){
        if(t<START||t>END+SPARK_LIFE1)return;
        Vec3d pivot=GiantSweep.pivot(c.cast);float yaw=GiantSweep.yaw(c.cast);
        for(int i=0;i<SPARKS;i++){
            float born=sparkBorn(i),life=sparkLife(i),age=t-born;if(age<0||age>=life)continue;
            int seed=i*67+803;Vec3d p=sparkAt(pivot,yaw,i,age);
            float a=Ease.smooth(age,0,.02f)*(1-Ease.smooth(age,life*.45f,life))*twinkle(t,seed)*near(p,c.camera);if(a<=.01f)continue;
            int col=i%3==0?WHITE:i%3==1?PALE:LILAC;
            if(age<.1f)m.softTaper(sparkAt(pivot,yaw,i,Math.max(0,age-.05f)),p,0,.06,col,.55f*a*(1-age/.1f));
            glint8(m,p,.1+.07*Ease.hash(seed+6),col,.9f*a);
        }
    }
    /** The angle petal {@code i} is shed at and when (as the blade passes there). */
    static double petalDeg(int i){return IsosangRules.SWEEP_FROM+(IsosangRules.SWEEP_TO-IsosangRules.SWEEP_FROM)*(i+.2+.6*Ease.hash(i*97+907))/PETALS;}
    static float petalBorn(int i){return GiantSweep.passes(petalDeg(i))+.02f;}
    static float petalLife(int i){return PETAL_LIFE0+(PETAL_LIFE1-PETAL_LIFE0)*(float)Ease.hash(i*97+914);}
    /** Where petal {@code i} is {@code age} seconds after it is shed off the blade, drifting down and out, swaying; its tumble (radians) in its 4th value. */
    static double[] petal(Vec3d pivot,float yaw,int i,float age){
        int seed=i*97+907;double deg=petalDeg(i);
        Vec3d d=GiantSweep.dir(yaw,deg),out=new Vec3d(d.x,0,d.z).normalize(),side=new Vec3d(-out.z,0,out.x);
        Vec3d p0=pivot.add(d.multiply(IsosangRules.SWEEP_REACH*(.35+.6*Ease.hash(seed+1)))).add(0,(Ease.hash(seed+2)-.5)*.5,0);
        double fall=PETAL_FALL*(.7+.6*Ease.hash(seed+3)),sway=.15*Math.sin(age*(2.5+2*Ease.hash(seed+4))+seed);
        Vec3d p=p0.add(out.multiply(.3*age)).add(side.multiply(sway)).add(0,-fall*age,0);
        return new double[]{p.x,p.y,p.z,age*(3.5+4*Ease.hash(seed+5))+seed};
    }
    static final int PETALS=18;
    static final double PETAL_FALL=.5;
    static final float PETAL_LIFE0=1.1f,PETAL_LIFE1=1.5f;
    static final int PETAL=0xd8e6ff;
    /** Crystal petals shed off the blade as it sweeps (the blended pass: pale pointed leaves, world-fixed and tumbling; the light pass: their glints now and then). */
    static void petals(Fx c,EffectMesh m,float t,boolean light){
        if(t<START||t>END+PETAL_LIFE1+.05f)return;
        Vec3d pivot=GiantSweep.pivot(c.cast);float yaw=GiantSweep.yaw(c.cast);
        for(int i=0;i<PETALS;i++){
            int seed=i*97+907;float born=petalBorn(i),life=petalLife(i),age=t-born;if(age<0||age>=life)continue;
            double[] pt=petal(pivot,yaw,i,age);Vec3d p=new Vec3d(pt[0],pt[1],pt[2]);
            float a=Ease.smooth(age,0,.08f)*(1-Ease.smooth(age,life*.6f,life))*near(p,c.camera);if(a<=.01f)continue;
            double size=.13+.08*Ease.hash(seed+8),turn=pt[3];
            Vec3d axis=new Vec3d(Math.cos(turn),.55*Math.sin(turn*.7),Math.sin(turn)).normalize();
            Vec3d across=axis.crossProduct(new Vec3d(Math.sin(turn*.5),.8,Math.cos(turn*.5))).normalize();
            if(!light){
                Vec3d u=axis.multiply(size),w=across.multiply(size*.38);
                m.quad(p.subtract(u),p.subtract(w),p.add(u),p.add(w),PETAL,.62f*a);
                m.line(p.subtract(u),p.add(u),size*.12,PALE,.55f*a);
            }else{
                double ph=((age+Ease.hash(seed+9)*.8)%.8)/.8;float spark=(float)Math.max(0,1-Math.abs(ph-.5)/.12);
                m.softDisc(p,size*1.6,AZURE,.14f*a);
                if(spark>.05f)glint8(m,p,size*1.6,WHITE,.65f*a*spark*spark);
            }
        }
    }
    /** How far the shockwave's rings have run out from under the pommel at {@code t} (blocks), or -1 when none: as it crosses the front. */
    static double shockRadius(float t){if(t<FRONT-.01f||t>FRONT+SHOCK_LIFE)return -1;return 1+SHOCK_R*Ease.out(t,FRONT,FRONT+SHOCK_LIFE);}
    static final double SHOCK_R=10.5;
    static final float SHOCK_LIFE=.45f;
    /** Rings of light running out over the ground from under its pommel as it crosses the front (over its ink). */
    static void shockwave(Fx c,EffectMesh m,float t){
        double r=shockRadius(t);if(r<0)return;
        Vec3d g=under(c).add(0,.06,0);float a=Ease.smooth(t,FRONT-.01f,FRONT+.01f),u=Ease.unit(t,FRONT,FRONT+SHOCK_LIFE);
        FxLib.ring(m,g,r,.14,AZURE,.5f*a*(1-u));
        float u2=Ease.unit(t,FRONT,FRONT+.32f);
        FxLib.ring(m,g,.8+7.5*Ease.out(t,FRONT,FRONT+.32f),.05,PALE,.55f*a*(1-u2));
        m.softDisc(g.add(0,.1,0),1.4,BLUE,.22f*a*(1-Ease.unit(t,FRONT,FRONT+.2f)));
    }

    // ───────────────────────────── on whom it strikes, where she lands ─────────────────────────────

    /** Under whom it strikes, as it reaches them: a ring of light running out on the ground and a few stars rising off them. */
    static void struck(Fx c,EffectMesh m,float t){
        int b=0;double floor=GiantSweep.floor(c.cast)+.05;
        for(var e:GiantSweep.struck(c)){
            float at=GiantSweep.struckAt(c,e);b++;if(t<at-.01f||t>at+.9f)continue;
            Vec3d o=e.getLerpedPos(c.delta),ground=new Vec3d(o.x,Math.max(floor,o.y+.04),o.z);
            float a=Ease.smooth(t,at-.01f,at+.01f);
            FxLib.ring(m,ground,.4+2.2*Ease.out(t,at,at+.4f),.09,AZURE,.55f*a*(1-Ease.unit(t,at,at+.4f)));
            FxLib.ring(m,ground,.3+1.4*Ease.out(t,at,at+.3f),.035,PALE,.5f*a*(1-Ease.unit(t,at,at+.3f)));
            Vec3d chest=o.add(0,e.getHeight()*.55,0);
            for(int i=0;i<STRUCK_STARS;i++){
                int seed=b*211+i*37+1601;float born=at+.04f*(float)Ease.hash(seed),age=t-born,life=.55f+.3f*(float)Ease.hash(seed+1);
                if(age<0||age>=life)continue;
                double az=Math.PI*2*(i+Ease.hash(seed+2))/STRUCK_STARS,r=.35+.3*Ease.hash(seed+3);
                Vec3d p=chest.add(Math.cos(az)*r*(1+age),(-.3+.6*Ease.hash(seed+4))+1.3*Ease.out(age,0,life),Math.sin(az)*r*(1+age));
                float sa=(1-age/life)*Ease.smooth(age,0,.05f)*twinkle(t,seed)*near(p,c.camera);
                glint8(m,p,.09+.05*Ease.hash(seed+5),i%3==0?WHITE:i%3==1?PALE:LILAC,.8f*sa);
            }
        }
    }
    static final int STRUCK_STARS=6;
    /** When she comes down in her crouch (seconds into R: GiantSweep.CLIP, down by R_LAND). */
    static final float LAND=.62f;
    /** Where she comes down: a small ring of light round her feet and moon dust kicked up and settling. */
    static void landing(Fx c,EffectMesh m,float t){
        if(t<LAND-.01f||t>LAND+.6f)return;
        Vec3d g=c.cast.origin().add(0,.04,0);
        float a=Ease.smooth(t,LAND-.01f,LAND+.01f),u=Ease.unit(t,LAND,LAND+.32f);
        FxLib.ring(m,g,.3+1.6*Ease.out(t,LAND,LAND+.3f),.07,AZURE,.55f*a*(1-u));
        FxLib.ring(m,g,.2+.9*Ease.out(t,LAND,LAND+.2f),.025,WHITE,.4f*a*(1-Ease.unit(t,LAND,LAND+.2f)));
        for(int i=0;i<LAND_DUST;i++){
            int seed=i*43+1711;double az=Math.PI*2*(i+Ease.hash(seed))/LAND_DUST,out=.6+.9*Ease.hash(seed+1),up=.35+.45*Ease.hash(seed+2);
            float age=t-LAND;double s=Ease.out(age,0,.5f);
            Vec3d p=g.add(Math.cos(az)*(.25+out*s),up*Math.sin(Math.PI*Math.min(1,age/.55)),Math.sin(az)*(.25+out*s));
            float da=a*(1-Ease.smooth(age,.3f,.55f))*twinkle(t,seed)*near(p,c.camera);if(da<=.01f)continue;
            if(i%2==0)glint8(m,p,.08,i%4==0?WHITE:PALE,.7f*da);else m.softDisc(p,.05,LILAC,.8f*da);
        }
    }
    static final int LAND_DUST=8;

    // ───────────────────────────── as it goes ─────────────────────────────

    /** When mote {@code i} rises off the blade as it goes into ink (seconds into R). */
    static float partBorn(int i){return GiantSweep.FADE0-.03f+.12f*(float)Ease.hash(i*59+1801);}
    static float partLife(int i){return PART_LIFE0+(PART_LIFE1-PART_LIFE0)*(float)Ease.hash(i*59+1807);}
    /** Where mote {@code i} is {@code age} seconds after it leaves the blade: from along it, rising and drifting out, slowing. */
    static Vec3d partAt(Vec3d pivot,float yaw,int i,float age){
        int seed=i*59+1801;double y=SpiritSword.BLADE_BASE+(SpiritSword.TIP-SpiritSword.BLADE_BASE)*Ease.hash(seed+2),x=(2*Ease.hash(seed+3)-1)*SpiritSword.half(y);
        Vec3d p=onSword(pivot,yaw,partBorn(i),x,y);double rise=(.8+.6*Ease.hash(seed+4))*(1-Math.exp(-2.2*age));
        return p.add((Ease.hash(seed+5)-.5)*.5*age,rise,(Ease.hash(seed+6)-.5)*.5*age);
    }
    static final int PARTS=20;
    static final float PART_LIFE0=.6f,PART_LIFE1=.9f;
    /** Motes of light rising off the blade as it goes into ink, twinkling and fading. */
    static void parting(Fx c,EffectMesh m,float t){
        if(t<GiantSweep.FADE0-.04f||t>GiantSweep.FADE0+.1f+PART_LIFE1)return;
        Vec3d pivot=GiantSweep.pivot(c.cast);float yaw=GiantSweep.yaw(c.cast);
        for(int i=0;i<PARTS;i++){
            float born=partBorn(i),life=partLife(i),age=t-born;if(age<0||age>=life)continue;
            int seed=i*59+1801;Vec3d p=partAt(pivot,yaw,i,age);
            float a=Ease.smooth(age,0,.05f)*(1-Ease.smooth(age,.4f*life,life))*twinkle(t,seed)*near(p,c.camera);if(a<=.01f)continue;
            if(i%3==2)m.softDisc(p,.06,LILAC,.8f*a);else glint8(m,p,.08+.05*Ease.hash(seed+7),i%3==0?WHITE:PALE,.75f*a);
        }
    }
    /** When the small crescent moon rises from its gem as it goes, and how long it rises (seconds). */
    static final float MOON_RISE=GiantSweep.FADE0,MOON_LIFE=1f;
    /** Where the rising moon is {@code age} seconds after it leaves the gem (world): up from where the gem was. */
    static Vec3d moonAt(Vec3d pivot,float yaw,float age){return onSword(pivot,yaw,MOON_RISE,0,SpiritSword.GEM_AT).add(0,.3+1.5*Ease.out(age,0,MOON_LIFE),0);}
    /** A small crescent moon rising from its gem as it goes into ink, its horns up, fading. */
    static void risingMoon(Fx c,EffectMesh m,float t){
        float age=t-MOON_RISE;if(age<0||age>=MOON_LIFE)return;
        Vec3d p=moonAt(GiantSweep.pivot(c.cast),GiantSweep.yaw(c.cast),age);
        float a=Ease.smooth(age,0,.08f)*(1-Ease.smooth(age,.45f,MOON_LIFE))*near(p,c.camera);if(a<=.01f)return;
        crescentMoon(m,p,.3,Math.PI*.5,MOON,.85f*a);glint8(m,p.add(0,.02,0),.14,WHITE,.5f*a*twinkle(t,301));m.softDisc(p,.4,AZURE,.2f*a);
    }
    /** Moon dust sprayed off the crescent's bright edge as it is eaten from its tail (as each hit's slashes, IsosangSlash.spray). */
    static void crescentDust(Fx c,EffectMesh m,float t){
        var s=GiantSweep.ARC;if(t<s.eat0()||t>=s.eat1()+DUST_LIFE1)return;
        Vec3d feet=GiantSweep.feet(c.cast);float yaw=GiantSweep.yaw(c.cast);Vec3d centre=world(feet,yaw,s.centre());
        for(int k=0;k<CRESCENT_DUST;k++){
            int seed=k*71+1907;float born=s.eat0()+(s.eat1()-s.eat0())*(float)Ease.hash(seed),age=t-born,life=DUST_LIFE0+(DUST_LIFE1-DUST_LIFE0)*(float)Ease.hash(seed+1);
            if(age<0||age>=life)continue;
            double x=Math.max(0,Math.min(1,Ease.smooth(born,s.eat0(),s.eat1())*1.05+.08*(Ease.hash(seed+2)-.5)));
            Vec3d at=world(feet,yaw,point(s,s.from()+(s.to()-s.from())*x,s.outer()-(s.outer()-s.inner())*.12*Ease.hash(seed+3)));
            Vec3d out=at.subtract(centre).multiply(1,0,1);out=out.lengthSquared()<1e-8?UP:out.normalize();
            Vec3d vel=out.multiply(.4+.9*Ease.hash(seed+4)).add(0,.4+.5*Ease.hash(seed+5),0),side=out.crossProduct(UP);
            side=side.lengthSquared()<1e-8?Vec3d.ZERO:side.normalize();
            Vec3d q=at.add(vel.multiply((1-Math.exp(-age*3.5))/3.5)).add(0,-.3*age*age,0).add(side.multiply(.07*Math.sin(age*9+seed)));
            float u=age/life,a=(1-u)*Ease.smooth(age,0,.04f)*twinkle(t,seed)*near(q,c.camera);if(a<=.01f)continue;
            int col=k%3==0?WHITE:k%3==1?PALE:LILAC;
            if(k%2==0)glint8(m,q,.08+.05*(1-u),col,.85f*a);else m.softDisc(q,.04+.03*(1-u),col,.8f*a);
        }
    }
    static final int CRESCENT_DUST=24;
    static final float DUST_LIFE0=.5f,DUST_LIFE1=.8f;

    /** The angle and the distance from under the pommel of ground star {@code k} (in the ink over the ground ahead, which darkens most). */
    static double[] groundStar(int k){
        int seed=k*83+2111;
        return new double[]{(2*Ease.hash(seed)-1)*GROUND_ARC,GROUND_NEAR+(GROUND_FAR-GROUND_NEAR)*Math.sqrt(Ease.hash(seed+1))};
    }
    static final int GROUND_STARS=30;
    static final double GROUND_ARC=78,GROUND_NEAR=2,GROUND_FAR=7.6;
    /** When ground star {@code k} comes out (as the blade passes over it) and how strongly it shows at {@code t}: until the ink it lies in dries (GiantSweep.ink's darkened ground). */
    static float groundShown(int k,float t){
        float born=GiantSweep.passes(groundStar(k)[0])+.1f,dk=t-START-.05f;
        return Ease.smooth(t,born,born+.08f)*(1-Ease.smooth(dk,GROUND_DRY0,GROUND_DRY1));
    }
    static final float GROUND_DRY0=1.1f,GROUND_DRY1=1.9f;
    /** Where ground star {@code k} lies (local to the sweep's ground). */
    static Vec3d groundPoint(int k){double[] g=groundStar(k);double a=Math.toRadians(g[0]);return v(IsosangRules.SWEEP_PIVOT_FW+g[1]*Math.cos(a),.05,g[1]*Math.sin(a));}
    /** Faint stars in the ink over the ground ahead, twinkling: a night lying on the ground, gone as the ink dries. */
    static void groundStars(Fx c,EffectMesh m,float t){
        if(t<START||t>START+.05f+GROUND_DRY1)return;
        Vec3d ground=GiantSweep.ground(c.cast);float yaw=GiantSweep.yaw(c.cast);
        for(int k=0;k<GROUND_STARS;k++){
            float a=groundShown(k,t);if(a<=.01f)continue;
            Vec3d p=world(ground,yaw,groundPoint(k));int seed=k*83+2111;
            a*=twinkle(t,seed)*near(p,c.camera);if(a<=.01f)continue;
            glint8(m,p,.07+.05*Ease.hash(seed+2),k%3==0?WHITE:k%3==1?PALE:LILAC,.6f*a);
        }
    }

    // ───────────────────────────── light on the world ─────────────────────────────

    /** Its light on the world (no impact frame: 2.102.1). */
    static void frame(Fx c,FxFrame f){
        float t=c.sec;Vec3d pivot=GiantSweep.pivot(c.cast);
        // the stars before it is called, the lightning as it hangs (flickering), the rings as it crosses the front
        float con=Ease.window(t,CON0,CON_OUT1,.04f,.06f);if(con>.01f)f.light(pivot,0x9fb8ff,6,.35f*con);
        float e=charge(t);
        if(e>.01f){var g=GiantSweep.giant(c.cast,t);if(g!=null)f.light(g.at((SpiritSword.BLADE_BASE+SpiritSword.TIP)*.5),0xc8b8ff,8,.45f*e*(.6f+.4f*(float)Ease.hash((int)Math.floor(t*24)*7+3)));}
        float s=FxLib.kick(t,FRONT,.25f);if(s>.01f)f.light(under(c).add(0,1,0),0x9fc8ff,14,.6f*s);
        float l=FxLib.kick(t,LAND,.2f);if(l>.01f)f.light(c.cast.origin().add(0,.5,0),0xa8c8ff,5,.4f*l);
    }
    /** The last any of it is drawn (seconds into R): within its swing's life (IsosangSlash.lifetime). */
    static float last(){
        float m=0;
        for(int k=0;k<TRACE;k++)m=Math.max(m,traceBorn(k)+traceLife(k));
        for(int i=0;i<SPARKS;i++)m=Math.max(m,sparkBorn(i)+sparkLife(i));
        for(int i=0;i<PETALS;i++)m=Math.max(m,petalBorn(i)+petalLife(i));
        for(int i=0;i<PARTS;i++)m=Math.max(m,partBorn(i)+partLife(i));
        m=Math.max(m,MOON_RISE+MOON_LIFE);m=Math.max(m,GiantSweep.ARC.eat1()+DUST_LIFE1);
        m=Math.max(m,START+.05f+GROUND_DRY1);m=Math.max(m,FRONT+SHOCK_LIFE);m=Math.max(m,LAND+.6f);
        return m;
    }
}
