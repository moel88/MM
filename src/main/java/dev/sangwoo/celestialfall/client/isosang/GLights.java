package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.isosang.GArenaBounds;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.Vec3d;

/**
 * Lee So-sang's G made pretty (2.106.0, user request: "마지막으로 G스킬 예쁘게 만들자"): her sword's moonlight and starlight
 * (월흔, the moon's trace: as R's SweepLights, T's LungeLights, the formation's FormationLights and FormationSky, H again's
 * FinaleLights and her strikes' SlashLights) in G's own gold, over what 2.87.0 to 2.92.0 drew:
 * <ul>
 * <li>her hand, in the black round it: a night of faint stars coming out one by one in the black, gold-white with a few
 * pale blue and lilac ones, some bright with four rays and four short ones, twinkling, gone as the black lifts
 * ({@link #night}); golden star dust drawn in round and round to her fingertips as the light gathers there
 * ({@link #gather}); little stars left along the stroke her two fingers draw, drifting down as they go
 * ({@link #strokeDust});</li>
 * <li>her face: a crescent moon of gold rising beside her head in the halo's light, its horns turned to her, a little star
 * in its arms ({@link #faceMoon});</li>
 * <li>the sky: glitter twinkling in the gold light of the storm's eye (isosang_cloud.fsh: the cutscene's canopy is drawn
 * over the effect layer, so its light is the clouds' own), and stars flaring here and there along the sword's burning
 * inscriptions (isosang_blade.fsh's and isosang_blade_observer.fsh's runeStars, {@link #runeStars});</li>
 * <li>the fall: star dust shed off the falling sword's edges, dragged down some way in its wake and left behind it as a
 * comet's tail ({@link #fallDust});</li>
 * <li>while it stands in the ground: a golden circle drawn round its foot on the ground, round from her side to its back,
 * three rings with twelve stars and the moon's eight phases between them, turning slowly, beating with the sword's heart,
 * brighter toward the blast and flaring at it ({@link #sigil}); on each beat a star of light running up each of its
 * edges ({@link #beatGlints});</li>
 * <li>after the blast: stardust drifting down over the whole of its ground, twinkling as it falls ({@link #starfall}).</li>
 * </ul>
 * None of what the user set or the videos show is changed (the hand, the face, the sky's sword and her figure in it, the
 * fall, the strike, the cracks, the blast, the cameras, the sound): this only adds. Nothing white-hot over the screen and
 * no new screen effect; white only in small cores. Every part is a function of the cast's clock alone (and of where the
 * server put the strike), the same for everyone; the far watcher's strokes widen with the distance as the far view's
 * ({@link GGrand#grown}).
 */
final class GLights {
    private GLights(){}

    static final int GOLD=FallingSword.GOLD,GOLD_WHITE=FallingSword.GOLD_WHITE,STAR=0xFFF4DC,HONEY=0xFFD98C,PALE=IsosangSlash.PALE,LILAC=IsosangSlash.LILAC;
    static final Vec3d UP=FallingSword.UP;

    /** The light pass: everything here is light. */
    static void additive(Fx c,EffectMesh m){
        if(!Float.isNaN(FallingSword.off(c.cast)))return;
        float t=c.sec;
        if(FallingSword.shown(c)){
            night(c,m,t);
            gather(c,m,t);
            strokeDust(c,m,t);
            faceMoon(c,m,t);
        }
        if(FallingSword.shown(c)||!GStage.hidden()){
            double grow=GGrand.grown(c);
            fallDust(c,m,t,grow);
            sigil(c,m,t,grow);
            beatGlints(c,m,t,grow);
            starfall(c,m,t,grow);
        }
    }

    /** A star's twinkle (0.1 to 1) at {@code t}: each its own pace and phase. */
    static float twinkle(int seed,float t){
        return .55f+.45f*(float)Math.sin(t*(2.6+4.4*Ease.hash(seed))+6.2832*Ease.hash(seed+1));
    }

    // ───────────────────────────── her hand: the night in the black ─────────────────────────────

    /** How many stars the night round her hand has, and how far off they are (blocks: far enough not to drift as the camera turns round her hand). */
    static final int NIGHT=1000;
    static final double NIGHT_R=15;
    /** When its stars come out (each in its turn, between the first two) and when they go (as the black lifts, GBlackout: 1.075 to 1.16 s). */
    static final float NIGHT0=.1f,NIGHT1=.4f,NIGHT_OUT0=1.04f,NIGHT_OUT1=1.12f;

    /** The middle of the night round her hand: her hand at half a second (the hand shots turn round it). */
    static Vec3d nightCentre(StandClient.Cast c){return FallingSword.hand(c.origin(),c.yaw(),.5f);}
    /** Star {@code i}'s way from the middle: spread evenly over the sphere (a golden spiral), a little jittered. */
    static Vec3d nightWay(int i){
        double y=1-2*(i+.5)/NIGHT,r=Math.sqrt(Math.max(0,1-y*y)),a=i*2.399963229728653;
        Vec3d d=new Vec3d(Math.cos(a)*r,y,Math.sin(a)*r);
        Vec3d j=new Vec3d(Ease.hash(i*13+1)-.5,Ease.hash(i*13+2)-.5,Ease.hash(i*13+3)-.5).multiply(.05);
        return d.add(j).normalize();
    }
    /** How much star {@code i} shows at {@code t}: out in its turn, gone as the black lifts. */
    static float nightShown(int i,float t){
        float born=NIGHT0+(NIGHT1-NIGHT0)*(float)Ease.hash(i*13+4);
        return Ease.smooth(t,born,born+.08f)*(1-Ease.smooth(t,NIGHT_OUT0,NIGHT_OUT1));
    }
    /** Star {@code i}'s kind: 2 bright (four rays and four short ones), 1 a little star, 0 a faint point. */
    static int nightKind(int i){double k=Ease.hash(i*13+5);return k<.05?2:k<.3?1:0;}

    /**
     * Stars coming out in the black round her hand (the world is not drawn there, GBlackout): mostly gold-white, a few pale
     * blue and lilac, most faint points, some little stars, one in twenty bright with eight rays, each twinkling at its
     * own pace. They stay where they are in the world, far off, so they turn with the shot as it swings round her hand.
     */
    static void night(Fx c,EffectMesh m,float t){
        if(t<NIGHT0||t>NIGHT_OUT1)return;
        Vec3d o=nightCentre(c.cast);
        for(int i=0;i<NIGHT;i++){
            float a=nightShown(i,t);if(a<=.01f)continue;
            Vec3d p=o.add(nightWay(i).multiply(NIGHT_R));
            double hue=Ease.hash(i*13+6),size=Ease.hash(i*13+7);
            int col=hue<.6?STAR:hue<.78?GOLD_WHITE:hue<.91?PALE:LILAC;
            float k=a*twinkle(i*13+8,t);
            switch(nightKind(i)){
                case 2->IsosangSlash.glint8(m,p,.2+.08*size,col,.9f*k);
                case 1->m.star(p,.09+.04*size,col,.8f*k);
                default->dot(m,p,.035+.025*size,col,.75f*k);
            }
        }
    }

    /** A faint point of light: a little diamond facing the camera. */
    static void dot(EffectMesh m,Vec3d p,double r,int rgb,float alpha){
        if(alpha<=.01f)return;
        Vec3d[] uv=IsosangSlash.cameraPlane(m,p);if(uv==null)return;
        Vec3d u=uv[0].multiply(r),v=uv[1].multiply(r);
        m.quad(p.add(u),p.add(v),p.subtract(u),p.subtract(v),rgb,alpha);
    }

    // ───────────────────────────── her hand: the star dust drawn in ─────────────────────────────

    static final int GATHER=16;
    /** When mote {@code i} of the star dust drawn in to her fingertips sets out, and when it gets there (seconds). */
    static float gatherBorn(int i){return .12f+.2f*(float)Ease.hash(i*17+1);}
    static float gatherArrive(int i){return gatherBorn(i)+.16f+.1f*(float)Ease.hash(i*17+2);}
    /**
     * Where mote {@code i} is at {@code t} from her fingertips (blocks), or null when it is not out: setting out a little
     * way off and coming in faster and faster, round and round, to nothing.
     */
    static Vec3d gatherOffset(int i,float t){
        float b=gatherBorn(i),e=gatherArrive(i);if(t<b||t>e)return null;
        double u=(t-b)/(e-b),in=Math.pow(u,1.5);
        Vec3d way=new Vec3d(Ease.hash(i*17+3)-.5,Ease.hash(i*17+4)-.5,Ease.hash(i*17+5)-.5);
        way=way.lengthSquared()<1e-4?new Vec3d(1,0,0):way.normalize();
        Vec3d axis=way.crossProduct(new Vec3d(.3,1,.2)).normalize();
        double turn=(Ease.hash(i*17+6)>.5?1:-1)*1.4*in;
        Vec3d spun=way.multiply(Math.cos(turn)).add(axis.crossProduct(way).multiply(Math.sin(turn)));
        return spun.multiply((.08+.1*Ease.hash(i*17+7))*(1-in));
    }

    /** Golden star dust drawn in to her fingertips as the light gathers there (0.12 to 0.58 s), each mote with a short tail. */
    static void gather(Fx c,EffectMesh m,float t){
        if(t<.12f||t>.62f)return;
        Vec3d tip=GGesture.tip(c.cast,t);
        for(int i=0;i<GATHER;i++){
            Vec3d off=gatherOffset(i,t);if(off==null)continue;
            float b=gatherBorn(i),e=gatherArrive(i);
            float a=Ease.smooth(t,b,b+.03f)*(1-Ease.smooth(t,e-.02f,e));
            Vec3d p=tip.add(off),back=gatherOffset(i,Math.max(b,t-.035f));
            if(back!=null)m.softTaper(tip.add(back),p,.001,.01,GOLD,.55f*a);
            m.star(p,.009+.005*Ease.hash(i*17+8),i%3==0?STAR:GOLD_WHITE,.9f*a);
        }
    }

    // ───────────────────────────── her hand: the stroke's stars ─────────────────────────────

    static final int STROKE=12;
    /** When her two fingers' stroke leaves its stars (seconds): one after another along it. */
    static final float STROKE0=.84f,STROKE1=.95f;
    static float strokeBorn(int i){return STROKE0+(STROKE1-STROKE0)*i/(STROKE-1f);}
    static float strokeLife(int i){return .15f+.07f*(float)Ease.hash(i*19+1);}
    /** How far star {@code i} has drifted from where her fingertips left it, {@code a} seconds on: a little down and out. */
    static Vec3d strokeDrift(int i,float a){return new Vec3d((Ease.hash(i*19+2)-.5)*.08*a,-.1*a,(Ease.hash(i*19+3)-.5)*.08*a);}

    /** Little stars left along the stroke her two fingers draw, drifting down and twinkling out (all gone by 1.2 s). */
    static void strokeDust(Fx c,EffectMesh m,float t){
        if(t<STROKE0||t>STROKE1+.25f)return;
        for(int i=0;i<STROKE;i++){
            float b=strokeBorn(i),age=t-b,life=strokeLife(i);if(age<0||age>life)continue;
            float a=Ease.smooth(age,0,.02f)*(1-Ease.smooth(age,.45f*life,life))*twinkle(i*19+4,t);
            Vec3d p=GGesture.tip(c.cast,b).add(strokeDrift(i,age));
            m.star(p,.008+.005*Ease.hash(i*19+5),i%2==0?STAR:GOLD_WHITE,.85f*a);
        }
    }

    // ───────────────────────────── her face: the moon beside her ─────────────────────────────

    /**
     * The crescent moon beside her head, in the halo's plane (GGrand.face: 1.1 blocks behind her head): how far across to
     * the frame's left of it and up (blocks), how big (blocks round) and how thick at its back (of that), its horns turned
     * to her.
     */
    static final double MOON_ACROSS=-1.5,MOON_UP=.12,MOON_R=.3,MOON_THICK=.5,MOON_RISE=.1;
    /** When it rises in (as the halo comes) and when it goes (as the shot pulls up to the sky, FallingSword.S_PULL). */
    static final float MOON0=1.1f,MOON1=1.3f,MOON_OUT0=1.5f,MOON_OUT1=1.6f;
    static float moonShown(float t){return Ease.smooth(t,MOON0,MOON1)*(1-Ease.smooth(t,MOON_OUT0,MOON_OUT1));}
    /** The moon's middle at {@code t}, from the halo's middle {@code back} with the camera's right and up there: rising a little as it comes. */
    static Vec3d moonAt(Vec3d back,Vec3d across,Vec3d up,float t){
        return back.add(across.multiply(MOON_ACROSS)).add(up.multiply(MOON_UP-MOON_RISE*(1-Ease.smooth(t,MOON0,MOON1+.1f))));
    }

    /**
     * A crescent moon of gold rising beside her head in the halo's light (the face shot, 1.1 to 1.6 s): its lit crescent,
     * its horns turned to her, a soft glow round it, and a little star twinkling in its arms.
     */
    static void faceMoon(Fx c,EffectMesh m,float t){
        float on=moonShown(t);if(on<=.01f)return;
        Vec3d head=FallingSword.head(c.cast.origin(),c.cast.yaw(),t);
        Vec3d view=head.subtract(c.camera);view=view.lengthSquared()<1e-6?c.f:view.normalize();
        Vec3d across=view.crossProduct(UP);across=across.lengthSquared()<1e-6?c.right:across.normalize();
        Vec3d up=across.crossProduct(view).normalize();
        Vec3d back=head.add(view.multiply(1.1));
        Vec3d o=moonAt(back,across,up,t);
        float breathe=.92f+.08f*(float)Math.sin(t*5.3f);
        m.softDisc(o,MOON_R*2.3,GOLD,.15f*on);
        m.softDisc(o.add(across.multiply(-MOON_R*.3)),MOON_R*1.15,HONEY,.12f*on);
        crescent(m,o,across,up,MOON_R*1.08,MOON_THICK*.9,0,GOLD,.35f*on);
        crescent(m,o,across,up,MOON_R,MOON_THICK,0,GOLD_WHITE,.9f*on*breathe);
        IsosangSlash.glint8(m,moonStar(o,across,up),.075,STAR,.9f*on*twinkle(5,t));
    }
    /** The little star in the moon's arms (between its horns, toward her). */
    static Vec3d moonStar(Vec3d o,Vec3d across,Vec3d up){return o.add(across.multiply(MOON_R*.42)).add(up.multiply(MOON_R*.18));}

    /**
     * A crescent of light at {@code o} in the plane of {@code right} and {@code up} (unit vectors): its outer edge a circle
     * {@code r} round, its inner edge bulging in to leave a lune {@code thick} of {@code r} wide at its back, its horns
     * opening toward {@code turn} (radians from {@code right} toward {@code up}).
     */
    static void crescent(EffectMesh m,Vec3d o,Vec3d right,Vec3d up,double r,double thick,double turn,int rgb,float alpha){
        if(alpha<=.01f||r<=.005)return;
        int n=18;double span=Math.toRadians(125);Vec3d po=null,pi=null;
        for(int i=0;i<=n;i++){
            double th=-span+2*span*i/n,w=thick*r*Math.cos(Math.PI/2*th/span),ang=turn+Math.PI+th;
            Vec3d d=right.multiply(Math.cos(ang)).add(up.multiply(Math.sin(ang))),out=o.add(d.multiply(r)),in=o.add(d.multiply(r-w));
            if(po!=null){m.triangle(po,out,in,rgb,alpha);m.triangle(po,in,pi,rgb,alpha);}
            po=out;pi=in;
        }
    }

    // ───────────────────────────── the sky: stars in the inscriptions ─────────────────────────────

    /**
     * The stars flaring along the sword's burning inscriptions at a point of its face ({@code x} across, {@code y} up from
     * its point, blocks) as its clock {@code clock} (GGrand.rune's, seconds) runs: here and there a bright spot, each
     * swelling and dying away in its turn. The same formula as isosang_blade.fsh's runeStars (and the observer's), in
     * doubles, for the tests.
     */
    static double runeStars(double x,double y,double clock){
        double gx=x*1.6,gy=y*1.6,s=0;
        int cx=(int)Math.floor(gx),cy=(int)Math.floor(gy);
        for(int j=-1;j<=1;j++)for(int i=-1;i<=1;i++){
            double kx=cx+i,ky=cy+j;
            double hx=fract(Math.sin(kx*127.1+ky*311.7)*43758.5453),hy=fract(Math.sin(kx*269.5+ky*183.3)*43758.5453),hz=fract(Math.sin(kx*419.2+ky*371.9)*43758.5453);
            if(hz<.6)continue;
            double dx=gx-(kx+.15+.7*hx),dy=gy-(ky+.15+.7*hy);
            double life=fract(clock*(.35+.3*hx)+hy),tw=Math.pow(Math.sin(Math.PI*life),6);
            s+=tw*Math.exp(-(dx*dx+dy*dy)*7);
        }
        return s;
    }
    static double fract(double v){return v-Math.floor(v);}

    // ───────────────────────────── the fall: the comet's tail ─────────────────────────────

    static final int DUST=60;
    /** When dust {@code i} is shed (seconds: while the sword falls, G_FALL to G_STRIKE) and how long it lasts. */
    static float dustBorn(int i){return IsosangRules.G_FALL+(IsosangRules.G_STRIKE+.02f-IsosangRules.G_FALL)*(float)Ease.hash(i*23+1);}
    static float dustLife(int i){return .35f+.3f*(float)Ease.hash(i*23+2);}
    /** How much of the sword's fall the dust follows down in its wake. */
    static final double WAKE=.72;
    /**
     * Where star dust {@code i} is at {@code t} (world), or null when it is not out: shed off one of the falling sword's
     * edges (the observers' wider sword's, {@code observer}: GWorld.bladeSurface) at its birth, then dragged down
     * {@link #WAKE} of the way the sword goes on falling (no further once it has struck), drifting out and up a little.
     */
    static Vec3d dustAt(StandClient.Cast c,int i,float t,boolean observer){
        float b=dustBorn(i),age=t-b;if(age<0||age>dustLife(i))return null;
        FallingSword.Blade bl=GScene.blade(c,b);
        int side=i%2==0?1:-1;
        double y=.6+15*Ease.hash(i*23+3);
        Vec3d at=observer?GWorld.bladeSurface(bl,side*3.8,y,(Ease.hash(i*23+4)-.5)*2.0)
            :bl.at(side*(FallingSword.half(y)+.12),y,(Ease.hash(i*23+4)-.5)*.6);
        double drop=Math.max(0,GScene.height(b))-Math.max(0,GScene.height(t));
        Vec3d axis=bl.axis().normalize(),across=bl.across().normalize();
        return at.subtract(axis.multiply(WAKE*drop)).add(across.multiply(side*(.5+.6*Ease.hash(i*23+5))*age)).add(0,.25*age,0);
    }

    /** Star dust shed off the falling sword's edges and left behind it as a comet's tail (2.78 to 3.8 s). */
    static void fallDust(Fx c,EffectMesh m,float t,double grow){
        if(t<IsosangRules.G_FALL||t>IsosangRules.G_STRIKE+.7f)return;
        boolean observer=!FallingSword.shown(c);
        for(int i=0;i<DUST;i++){
            Vec3d p=dustAt(c.cast,i,t,observer);if(p==null)continue;
            float age=t-dustBorn(i),life=dustLife(i);
            float a=Ease.smooth(age,0,.03f)*(1-Ease.smooth(age,.45f*life,life))*twinkle(i*23+6,t);
            double r=(.1+.08*Ease.hash(i*23+7))*grow;
            int col=i%5==0?PALE:i%3==0?STAR:GOLD_WHITE;
            if(i%4==0)IsosangSlash.glint8(m,p,r*1.4,col,.85f*a);
            else m.star(p,r,col,.85f*a);
        }
    }

    // ───────────────────────────── while it stands: the circle round its foot ─────────────────────────────

    /** The circle's rings (blocks round where the sword struck), and where its stars and its moons sit between them. */
    static final double SIGIL_R=6.2,SIGIL_INNER=5.4,SIGIL_CORE=3.6,SIGIL_STARS=5.8,SIGIL_MOONS=4.5,SIGIL_LIFT=.09;
    /** When it starts to be drawn, when it is drawn all round, and when it goes (after flaring at the blast, with the cracks: GGrand.ground). */
    static final float SIGIL0=IsosangRules.G_STRIKE+.04f,SIGIL_DRAWN=IsosangRules.G_STRIKE+.46f,SIGIL_OUT0=5.2f,SIGIL_OUT1=5.75f;
    /** Its twelve stars (every third one larger) and the moon's eight phases (how much of each is lit, the first four waxing). */
    static final int SIGIL_STAR_COUNT=12;
    static final double[] PHASES={.25,.5,.75,1,.75,.5,.25,0};

    /** How much of its round the circle has drawn (0 to 1): from the side facing her round both ways to its back. */
    static float sigilDrawn(float t){return Ease.out(t,SIGIL0,SIGIL_DRAWN);}
    /** How bright it is at {@code t}: lit as it is drawn, beating with the sword's heart, brighter toward the blast, flaring at it, gone after. */
    static float sigilBright(float t){
        float present=Ease.smooth(t,SIGIL0,SIGIL0+.08f)*(1-Ease.smooth(t,SIGIL_OUT0,SIGIL_OUT1));
        if(present<=0)return 0;
        return present*(.5f+.3f*Ease.smooth(t,3.3f,4.7f)+.5f*GGrand.heart(t)+.9f*FxLib.kick(t,4.86f,.25f));
    }
    /** The way round the ground facing her (radians from +x toward +z): the circle is drawn from there. */
    static double sigilFront(StandClient.Cast c){
        Vec3d d=c.origin().subtract(FallingSword.strike(c));
        return d.x*d.x+d.z*d.z<1e-6?Math.atan2(-FallingSword.forward(c).z,-FallingSword.forward(c).x):Math.atan2(d.z,d.x);
    }
    /** How far its stars have turned round (radians) and its moons the other way. */
    static double starSpin(float t){return .12*Math.max(0,t-IsosangRules.G_STRIKE);}
    static double moonSpin(float t){return -.08*Math.max(0,t-IsosangRules.G_STRIKE);}
    /** The angle round the ground of the circle's star {@code k} and of its moon {@code k} at {@code t} (radians). */
    static double starAngle(StandClient.Cast c,int k,float t){return sigilFront(c)+starSpin(t)+k*Math.PI*2/SIGIL_STAR_COUNT;}
    static double moonAngle(StandClient.Cast c,int k,float t){return sigilFront(c)+moonSpin(t)+(k+.5)*Math.PI*2/PHASES.length;}
    /** Where on the ground round where the sword struck the circle puts what is {@code radius} out at {@code angle}. */
    static Vec3d onGround(StandClient.Cast c,double radius,double angle){
        return FallingSword.strike(c).add(Math.cos(angle)*radius,SIGIL_LIFT,Math.sin(angle)*radius);
    }
    /** How much of what sits {@code angle} round the ground the drawing has reached at {@code t} (0 to 1): from her side round both ways. */
    static float reached(StandClient.Cast c,double angle,float t){
        double off=angle-sigilFront(c),d=Math.abs(Math.atan2(Math.sin(off),Math.cos(off)));
        return Ease.smooth((float)(sigilDrawn(t)*(Math.PI+.3)-d),0,.3f);
    }

    /**
     * A golden circle on the ground round the sword's foot while it stands (3.14 to 5.75 s): drawn round from her side to
     * its back in under half a second, three rings, twelve four-pointed stars between the outer two (turning one way) and
     * the moon's eight phases between the inner two (turning the other), beating with the sword's heart, brighter toward
     * the blast and flaring at it. The cracks tear across it.
     */
    static void sigil(Fx c,EffectMesh m,float t,double grow){
        float bright=sigilBright(t);if(bright<=.01f)return;
        Vec3d o=FallingSword.strike(c.cast).add(0,SIGIL_LIFT,0);
        double front=sigilFront(c.cast),span=sigilDrawn(t)*Math.PI;
        double w=Math.min(grow,2.5);
        arc(m,o,SIGIL_R,.55*w,front-span,front+span,GOLD,.45f*bright);
        arc(m,o,SIGIL_R,.13*w,front-span,front+span,GOLD_WHITE,.95f*bright);
        arc(m,o,SIGIL_INNER,.34*w,front-span,front+span,GOLD,.38f*bright);
        arc(m,o,SIGIL_INNER,.09*w,front-span,front+span,GOLD_WHITE,.85f*bright);
        arc(m,o,SIGIL_CORE,.3*w,front-span,front+span,GOLD,.34f*bright);
        arc(m,o,SIGIL_CORE,.08*w,front-span,front+span,GOLD_WHITE,.75f*bright);
        for(int k=0;k<SIGIL_STAR_COUNT;k++){
            double a=starAngle(c.cast,k,t);float on=reached(c.cast,a,t)*bright;if(on<=.01f)continue;
            double size=(k%3==0?.85:.58)*Math.min(grow,1.8);
            Vec3d p=onGround(c.cast,SIGIL_STARS,a);
            flatStar(m,p,size*1.6,a,GOLD,.38f*on);
            flatStar(m,p,size,a,GOLD_WHITE,1f*on);
        }
        for(int k=0;k<PHASES.length;k++){
            double a=moonAngle(c.cast,k,t);float on=reached(c.cast,a,t)*bright;if(on<=.01f)continue;
            Vec3d p=onGround(c.cast,SIGIL_MOONS,a),out=new Vec3d(Math.cos(a),0,Math.sin(a));
            double r=.56*Math.min(grow,1.8);
            if(PHASES[k]<=0)arc(m,p,r,.08*w,0,Math.PI*2,GOLD_WHITE,.6f*on);
            else flatMoon(m,p,out,r,PHASES[k],k<4,HONEY,.95f*on);
            arc(m,p,r*1.35,.24*w,0,Math.PI*2,GOLD,.24f*on);
        }
    }

    /** A soft ring lying on the ground round {@code o}, {@code width} wide, from angle {@code from} to {@code to} (radians from +x toward +z). */
    static void arc(EffectMesh m,Vec3d o,double radius,double width,double from,double to,int rgb,float alpha){
        if(alpha<=.003f||to-from<1e-4||radius<=0)return;
        double in=Math.max(0,radius-width*.5),out=radius+width*.5;
        int n=Math.max(3,(int)Math.ceil((to-from)/(Math.PI*2)*96));
        for(int i=0;i<n;i++){
            double a=from+(to-from)*i/n,b=from+(to-from)*(i+1)/n;
            Vec3d da=new Vec3d(Math.cos(a),0,Math.sin(a)),db=new Vec3d(Math.cos(b),0,Math.sin(b));
            m.vertex(o.add(da.multiply(in)),rgb,0);m.vertex(o.add(db.multiply(in)),rgb,0);
            m.vertex(o.add(db.multiply(radius)),rgb,alpha);m.vertex(o.add(da.multiply(radius)),rgb,alpha);
            m.vertex(o.add(da.multiply(radius)),rgb,alpha);m.vertex(o.add(db.multiply(radius)),rgb,alpha);
            m.vertex(o.add(db.multiply(out)),rgb,0);m.vertex(o.add(da.multiply(out)),rgb,0);
        }
    }

    /** A four-pointed star lying on the ground at {@code o}, {@code r} to its long points (along {@code turn} and square to it), with four short ones between. */
    static void flatStar(EffectMesh m,Vec3d o,double r,double turn,int rgb,float alpha){
        if(alpha<=.01f)return;
        for(int q=0;q<8;q++){
            double a=turn+q*Math.PI/4,len=q%2==0?r:r*.42;
            Vec3d d=new Vec3d(Math.cos(a),0,Math.sin(a)),side=new Vec3d(-d.z,0,d.x);
            m.needle(o,o.add(d.multiply(len)),side,len*(q%2==0?.36:.4),0,rgb,alpha);
        }
    }

    /**
     * A moon of light lying on the ground at {@code o}, {@code r} round, {@code lit} of it lit (0 new, 1 full): lit from its
     * right limb (waxing) or its left (waning), the terminator between, its up toward {@code out}.
     */
    static void flatMoon(EffectMesh m,Vec3d o,Vec3d out,double r,double lit,boolean waxing,int rgb,float alpha){
        if(alpha<=.01f||lit<=0)return;
        Vec3d side=new Vec3d(-out.z,0,out.x);
        int n=12;Vec3d pa=null,pb=null;
        for(int j=0;j<=n;j++){
            double v=-r+2*r*j/n,half=Math.sqrt(Math.max(0,r*r-v*v)),term=Math.cos(Math.PI*lit)*half;
            double x0=waxing?term:-half,x1=waxing?half:-term;
            Vec3d a=o.add(out.multiply(v)).add(side.multiply(x0)),b=o.add(out.multiply(v)).add(side.multiply(x1));
            if(pa!=null)m.quad(pa,pb,b,a,rgb,alpha);
            pa=a;pb=b;
        }
    }

    // ───────────────────────────── while it stands: the stars up its edges ─────────────────────────────

    /** How far up from its point the star of light running up the sword's edges on beat {@code k} is at {@code t} (blocks), or NaN when it is not. */
    static double glintHeight(int k,float t){
        float tau=t-GGrand.BEATS[k];if(tau<-.03f||tau>.42f)return Double.NaN;
        return Math.max(0,-GScene.height(t))+.4+15*Ease.smooth(tau,-.03f,.42f);
    }
    /** How much it shows: in on the beat, going as it climbs. */
    static float glintShown(int k,float t){float tau=t-GGrand.BEATS[k];return Ease.smooth(tau,-.03f,.01f)*(1-Ease.smooth(tau,.24f,.42f));}
    /** A point just off the sword's edge {@code side} (-1 or 1) at {@code y}, on the camera's side ({@code facing}): the observers' wider sword's for them. */
    static Vec3d edge(FallingSword.Blade bl,int side,double y,int facing,boolean observer){
        return observer?GWorld.bladeSurface(bl,side*3.8,y,1.3*facing):bl.at(side*(FallingSword.half(y)+.04),y,.45*facing);
    }

    /** On each beat of the sword's heart, a star of light running up each of its edges from the ground, a golden streak behind it. */
    static void beatGlints(Fx c,EffectMesh m,float t,double grow){
        float[] beats=GGrand.BEATS;
        if(t<beats[0]-.05f||t>beats[beats.length-1]+.45f)return;
        boolean observer=!FallingSword.shown(c);
        FallingSword.Blade bl=observer?GWorld.blade(c.cast,t):FallingSword.blade(c.cast,t);
        if(bl==null||bl.alpha()<.02f)return;
        int facing=bl.normal().dotProduct(c.camera.subtract(bl.point()))>=0?1:-1;
        for(int k=0;k<beats.length;k++){
            double y=glintHeight(k,t);if(Double.isNaN(y))continue;
            float a=glintShown(k,t)*bl.alpha();if(a<=.01f)continue;
            for(int side=-1;side<=1;side+=2){
                Vec3d p=edge(bl,side,y,facing,observer),q=edge(bl,side,Math.max(Math.max(0,-GScene.height(t))+.4,y-2.4),facing,observer);
                m.softTaper(q,p,.03*grow,.3*grow,GOLD,.5f*a);
                m.softTaper(q,p,.006*grow,.08*grow,GOLD_WHITE,.8f*a);
                IsosangSlash.glint8(m,p,.6*grow,STAR,.95f*a);
            }
        }
    }

    // ───────────────────────────── after the blast: the falling stars ─────────────────────────────

    static final int FALL=260;
    /** When the stardust starts to drift down after the blast (seconds), over how long it sets out, and how high it starts (blocks over the ground). */
    static final float FALL0=4.92f,FALL_SPREAD=1.15f;
    static final double FALL_LOW=7,FALL_HIGH=25;
    static float fallBorn(int i){return FALL0+FALL_SPREAD*(float)Ease.hash(i*29+1);}
    /** How long star {@code i} drifts down: ending before the cast is gone (FallingSword.LIFE). */
    static float fallLife(int i){return Math.min(1.3f+.9f*(float)Ease.hash(i*29+2),FallingSword.LIFE-.06f-fallBorn(i));}
    /**
     * Where star {@code i} of the dust drifting down after the blast is at {@code t} (world), or null when it is not out:
     * somewhere over the blast's ground (GArenaClient.area: the arena, or 62 × 47 blocks round where the sword struck),
     * falling slowly and swaying, never below the ground.
     */
    static Vec3d fallAt(StandClient.Cast c,int i,float t){return fallAt(GArenaClient.area(c),FallingSword.strike(c),i,t);}
    /** The same over the blast's ground {@code area} round where the sword struck, {@code s} (worked out once a frame). */
    static Vec3d fallAt(GArenaBounds area,Vec3d s,int i,float t){
        float b=fallBorn(i),age=t-b;if(age<0||age>fallLife(i))return null;
        double x=area.minX()+.6+(area.maxX()-area.minX()-1.2)*Ease.hash(i*29+3),z=area.minZ()+.6+(area.maxZ()-area.minZ()-1.2)*Ease.hash(i*29+4);
        double h=FALL_LOW+(FALL_HIGH-FALL_LOW)*Ease.hash(i*29+5),v=1.4+1.6*Ease.hash(i*29+6),ph=6.2832*Ease.hash(i*29+7);
        return new Vec3d(x+.35*Math.sin(age*2.1+ph),s.y+Math.max(.15,h-v*age),z+.35*Math.cos(age*1.7+ph));
    }

    /** Stardust drifting down over the blast's whole ground after it (4.92 to 7.35 s), twinkling as it falls, gone before it reaches the ground. */
    static void starfall(Fx c,EffectMesh m,float t,double grow){
        if(t<FALL0||t>FallingSword.LIFE)return;
        GArenaBounds area=GArenaClient.area(c.cast);Vec3d s=FallingSword.strike(c.cast);
        double ground=s.y;
        for(int i=0;i<FALL;i++){
            Vec3d p=fallAt(area,s,i,t);if(p==null)continue;
            float age=t-fallBorn(i),life=fallLife(i);
            float a=Ease.smooth(age,0,.2f)*(1-Ease.smooth(age,.6f*life,life))*Ease.smooth((float)(p.y-ground),.15f,.9f)*twinkle(i*29+8,t);
            double r=(.17+.13*Ease.hash(i*29+9))*grow,hue=Ease.hash(i*29+10);
            int col=hue<.5?GOLD_WHITE:hue<.78?STAR:hue<.9?PALE:LILAC;
            m.softDisc(p,r*.9,GOLD,.22f*a);
            if(i%3==0)IsosangSlash.glint8(m,p,r*1.5,col,.95f*a);
            else m.star(p,r,col,.92f*a);
        }
    }
}
