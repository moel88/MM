package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.*;

/**
 * E's and the empowered combo's moonlight and starlight made prettier (2.103.0, user request: "이번에는 평타와 e스킬 강화
 * 평타 예쁘게 바꿔줘"), as the basic attack's (SlashLights) and R's (SweepLights: the giant sword is the same sword of
 * light, SpiritSword), over what the user's videos set (IsosangEmpower):
 * <ul>
 * <li>E itself: light drawn into the sword hand out of the air round it, and out of the hand a crescent moon rising over
 * her head as it fades ({@link #gather}, {@link #eMoon}; the turning moon at the hand it replaces was a moment long);</li>
 * <li>the three swords: a little crystal flower blooming where one stops without striking anyone, facing back the way it
 * flew ({@link #bloom}), and starry sparks crackling off the blade with the lightning as she crouches ({@link #crouchSparks});</li>
 * <li>the giant sword: its seven stars lit in its shape, pommel to point, as it shows, given up to it as it forms
 * ({@link #constellation}); one crescent moon riding its point as it is thrust, rising over where it stops and climbing
 * on as the ink swallows it ({@link #giantMoon}; it showed only once it stopped before. One moon all the way: a second,
 * from its gem, made two at once); stardust left along its way ({@link #giantTrace}); while it hangs, lightning creeping
 * over its edges ({@link #hangLightning}), its guard's gem glinting ({@link #gemGlint}) and crystal petals falling off it
 * ({@link #petals}); as the ink swallows it, motes of light rising off it ({@link #parting}); a ring of light where she
 * lands from her leap back ({@link #landing}) and under whom it strikes, stars rising beside them ({@link #struck}).</li>
 * </ul>
 * Small, the slashes and the swords the heroes; no screen effect (2.102.1). Every one is a function of the swing's clock
 * and of where the server put the throws, the same for everyone.
 */
final class EmpowerLights {
    private EmpowerLights(){}

    static final int THROWS=IsosangRules.THROWS,THRUST=IsosangRules.THRUST;

    /** The blended pass: the flowers' and the petals' bodies. */
    static void alpha(Fx c,EffectMesh m){
        int combo=c.cast.combo();
        if(combo==THROWS)bloom(c,m,false);
        else if(combo==THRUST)petals(c,m,false);
    }
    /** The light pass (under the ink): the flowers' light, the giant sword's stars as it shows, the petals' glints. */
    static void additive(Fx c,EffectMesh m){
        int combo=c.cast.combo();
        if(combo==THROWS)bloom(c,m,true);
        else if(combo==THRUST){constellation(c,m);petals(c,m,true);}
    }
    /** Over the ink (the strokes round the giant sword would hide them): the rest. */
    static void overlay(Fx c,EffectMesh m){
        int combo=c.cast.combo();float t=c.sec;
        if(combo==0){gather(c,m,t);eMoon(c,m,t);}
        else if(combo==THROWS)crouchSparks(c,m,t);
        else if(combo==THRUST&&IsosangEmpower.flies(c.cast)){
            giantTrace(c,m,t);giantMoon(c,m,t);hangLightning(c,m,t);gemGlint(c,m,t);parting(c,m,t);struck(c,m,t);landing(c,m,t);
        }
    }

    // ───────────────────────────── E itself ─────────────────────────────

    static final int GATHER=10;
    /** When gathering mote {@code k} sets off (seconds into E) and how long it takes to reach the hand. */
    static float gatherFrom(int k){return .06f*(float)Ease.hash(k*71+4103);}
    static final float GATHER_TIME=.1f;
    /** Where gathering mote {@code k} sets off from: about a block round the hand. */
    static Vec3d gatherStart(Vec3d hand,int k){
        int seed=k*71+4103;return hand.add(new Vec3d(Ease.hash(seed+1)-.5,Ease.hash(seed+2)-.3,Ease.hash(seed+3)-.5).normalize().multiply(1+.6*Ease.hash(seed+4)));
    }
    /** Light drawn into the sword hand out of the air round it as E is used: thin streaks, faster as they come in. */
    static void gather(Fx c,EffectMesh m,float t){
        if(t>=.2f)return;
        Vec3d hand=IsosangEmpower.hand(c);
        for(int k=0;k<GATHER;k++){
            float t0=gatherFrom(k),age=t-t0;if(age<0||age>=GATHER_TIME)continue;
            float u=Ease.unit(t,t0,t0+GATHER_TIME);u=u*u;
            Vec3d from=gatherStart(hand,k),q=from.lerp(hand,u),tail=from.lerp(hand,Math.max(0,u-.35f));
            float a=Ease.smooth(age,0,.02f)*near(q,c.camera);if(a<=.01f)continue;
            m.line(tail,q,.025,k%2==0?WHITE:PALE,.65f*a);m.softLine(tail,q,.09,k%3==0?LILAC:AZURE,.22f*a);
        }
    }
    /** How long E's moon rises (seconds). */
    static final float E_MOON_LIFE=.9f;
    /** Where E's moon is at E's second {@code t}: out of the hand, rising over her head, slowing. */
    static Vec3d eMoonAt(Vec3d hand,float t){return hand.add(0,.15+1.6*Ease.out(t,.04f,E_MOON_LIFE),0);}
    static float eMoonShown(float t){return t>=E_MOON_LIFE?0:Ease.smooth(t,0,.03f)*(1-Ease.smooth(t,.45f,E_MOON_LIFE));}
    /** E's crescent moon: out of the sword hand in E's flash, rising over her head, its horns up, fading. */
    static void eMoon(Fx c,EffectMesh m,float t){
        float sh=eMoonShown(t);if(sh<=.01f)return;
        Vec3d p=eMoonAt(IsosangEmpower.hand(c),t);float a=sh*near(p,c.camera);if(a<=.01f)return;
        crescentMoon(m,p,.36-.08*Ease.unit(t,0,E_MOON_LIFE),Math.PI*.5,MOON,.9f*a);
        glint8(m,p,.14,WHITE,.5f*a*twinkle(t,401));m.softDisc(p,.45,AZURE,.22f*a);
    }

    // ───────────────────────────── the three swords ─────────────────────────────

    /** How long a stopped sword's flower lasts (seconds) and how big it is (blocks). */
    static final float BLOOM_LIFE=.55f;
    static final double BLOOM_R=.3;
    /** How far open the flower is ({@code open} 0 a bud, 1 open) and how strongly it shows, {@code age} seconds after its sword stopped. */
    static float bloomOpen(float age){return Ease.out(age,0,.12f);}
    static float bloomShown(float age){return age<0||age>=BLOOM_LIFE?0:Ease.smooth(age,0,.03f)*(1-Ease.smooth(age,.3f,BLOOM_LIFE));}
    /** A little crystal flower where each sword stops striking no one (where it struck someone, its burst is there), facing back the way it flew. */
    static void bloom(Fx c,EffectMesh m,boolean light){
        float t=c.sec;
        for(int i=0;i<IsosangRules.SWORD_FROM.length;i++){
            var w=IsosangEmpower.way(c,i);if(w==null||IsosangEmpower.struckBy(c,i)!=null)continue;
            float age=t-w.reach(w.end()),sh=bloomShown(age);if(sh<=.01f)continue;
            Vec3d o=w.point(w.end()).subtract(w.dir().multiply(.06));
            float a=sh*near(o,c.camera)*seen(o,c.camera);if(a<=.01f)continue;
            flower(m,o,w.dir().negate(),BLOOM_R,bloomOpen(age),a,light,i*37+11,t);
        }
    }
    static final int FLOWER_BASE=0x3a62e0,FLOWER_MID=0xc8dcff;
    /**
     * A crystal flower at {@code o} facing {@code n}: six white petals, blue at their foot, opening out of a bud
     * ({@code open} 0) to lie nearly flat ({@code open} 1), turning slowly; their bodies ({@code light} false) or their light
     * (white rims, a blue glow, a glint at its heart).
     */
    static void flower(EffectMesh m,Vec3d o,Vec3d n,double size,float open,float a,boolean light,int seed,float t){
        Vec3d u=n.crossProduct(UP);if(u.lengthSquared()<1e-4)u=n.crossProduct(new Vec3d(1,0,0));u=u.normalize();Vec3d w=n.crossProduct(u).normalize();
        double turn=Ease.hash(seed)*Math.PI*2+t*.3,el=Math.toRadians(72-54*open);
        for(int j=0;j<6;j++){
            double az=turn+Math.PI*2*j/6;
            Vec3d out=u.multiply(Math.cos(az)).add(w.multiply(Math.sin(az))),side=u.multiply(-Math.sin(az)).add(w.multiply(Math.cos(az)));
            Vec3d dir=out.multiply(Math.cos(el)).add(n.multiply(Math.sin(el)));
            double len=size*(.85+.15*Ease.hash(seed+j)),wid=size*.3*(.5+.5*open);
            Vec3d mid=o.add(dir.multiply(len*.42)),tip=o.add(dir.multiply(len)),l=mid.add(side.multiply(wid)),r=mid.subtract(side.multiply(wid));
            if(!light){
                m.vertex(o,FLOWER_BASE,.9f*a);m.vertex(l,FLOWER_MID,.88f*a);m.vertex(tip,WHITE,.92f*a);m.vertex(tip,WHITE,.92f*a);
                m.vertex(o,FLOWER_BASE,.9f*a);m.vertex(tip,WHITE,.92f*a);m.vertex(r,FLOWER_MID,.88f*a);m.vertex(r,FLOWER_MID,.88f*a);
            }else{
                double lw=.022*size/.3;
                m.line(l,tip,lw,WHITE,.7f*a);m.line(r,tip,lw,WHITE,.7f*a);m.line(o,l,lw*.7,PALE,.4f*a);m.line(o,r,lw*.7,PALE,.4f*a);
            }
        }
        if(light){m.softDisc(o,size*1.5,BLUE,.22f*a);glint8(m,o.add(n.multiply(.05)),size*.35*(.6+.4*twinkle(t,seed)),WHITE,.55f*a);}
    }
    static final int CROUCH_SPARKS=10;
    /** When crouch spark {@code k} springs off the blade (seconds into the empowered first: as the lightning runs along it, IsosangEmpower.lightning). */
    static float sparkBorn(int k){return .4f+.24f*(k+(float)Ease.hash(k*59+4211))/CROUCH_SPARKS;}
    static float sparkLife(int k){return .12f+.06f*(float)Ease.hash(k*59+4212);}
    /** Starry sparks crackling off the blade with its lightning as she crouches after the third throw, each from where it was on the blade, flung out a little. */
    static void crouchSparks(Fx c,EffectMesh m,float t){
        if(t<.38f||t>=.86f)return;
        for(int k=0;k<CROUCH_SPARKS;k++){
            int seed=k*59+4211;float born=sparkBorn(k),life=sparkLife(k),age=t-born;if(age<0||age>=life)continue;
            var hb=blade(c,THROWS,born);Vec3d at=hb[0].add(hb[1].multiply(GUARD+(TIP-GUARD)*(.15+.8*Ease.hash(seed+2))));
            Vec3d d=new Vec3d(Ease.hash(seed+3)-.5,Ease.hash(seed+4)-.2,Ease.hash(seed+5)-.5).normalize();
            Vec3d q=at.add(d.multiply(.08+.3*Ease.out(age,0,life)));
            float a=(1-age/life)*Ease.smooth(age,0,.02f)*near(q,c.camera);if(a<=.01f)continue;
            glint8(m,q,.07+.03*Ease.hash(seed+6),k%2==0?WHITE:LILAC,.8f*a);
        }
    }

    // ───────────────────────────── the giant sword ─────────────────────────────

    /** A point of the giant sword {@code g} in its own frame (SpiritSword's: across its flat, along it from its grip; blocks at its full size). */
    static Vec3d on(IsosangEmpower.Giant g,double across,double along){return SpiritSword.world(g.grip(),SpiritSword.frame(g.dir()),g.scale(),v(across,along,0));}
    /** When its first star is lit, how far apart they are, and when they are given up to it (seconds into the hit: it shows over 0.01 to 0.09 s). */
    static final float CON0=.012f,CON_STEP=.006f,CON_OUT0=.07f,CON_OUT1=.12f;
    static float conLit(int k){return CON0+CON_STEP*SweepLights.RANK[k];}
    static float conShown(int k,float t){return Ease.smooth(t,conLit(k),conLit(k)+.015f)*(1-Ease.smooth(t,CON_OUT0,CON_OUT1));}
    /** The giant sword's seven stars (SweepLights.STARS: pommel, grip, guard's points, gem, blade, point) and the threads between them, lit pommel to point as it shows. */
    static void constellation(Fx c,EffectMesh m){
        float t=c.sec;if(t>=CON_OUT1)return;
        var g=IsosangEmpower.giant(c.cast,t);if(g==null)return;
        Vec3d[] p=new Vec3d[SweepLights.STARS.length];for(int k=0;k<p.length;k++)p[k]=on(g,SweepLights.STARS[k][0],SweepLights.STARS[k][1]);
        float out=1-Ease.smooth(t,CON_OUT0,CON_OUT1);
        for(int[] l:SweepLights.LINES){
            float from=Math.max(conLit(l[0]),conLit(l[1])),drawn=Ease.out(t,from,from+.015f);if(drawn<=0)continue;
            int a=conLit(l[0])<=conLit(l[1])?l[0]:l[1],b=a==l[0]?l[1]:l[0];
            float la=out*near(p[a].lerp(p[b],.5),c.camera);if(la<=.01f)continue;
            Vec3d e=p[a].lerp(p[b],drawn);m.line(p[a],e,.025,PALE,.6f*la);m.softLine(p[a],e,.12,AZURE,.2f*la);
        }
        for(int k=0;k<p.length;k++){
            float a=conShown(k,t)*near(p[k],c.camera);if(a<=.01f)continue;
            boolean end=k==0||k==6;glint8(m,p[k],end?.18:.13,end?WHITE:PALE,.9f*a);
        }
    }
    /** When it is thrust, and when it stops (seconds into the hit). */
    static float thrust(){return blow(THRUST,0);}
    static final int G_TRACE=24;
    /** When trace mote {@code k} is left (seconds into the hit): as its point passes, spread over its flight. */
    static float gTraceBorn(StandClient.Cast cast,int k){float at=thrust(),arr=IsosangEmpower.arrive(cast);return at+(arr-at)*(k+.2f+.6f*(float)Ease.hash(k*79+4401))/G_TRACE;}
    static float gTraceLife(int k){return .7f+.3f*(float)Ease.hash(k*79+4407);}
    /** Where trace mote {@code k} is {@code age} seconds after the point passed: where the point was, a little scattered, drifting down. */
    static Vec3d gTraceAt(StandClient.Cast cast,int k,float age){
        var g=IsosangEmpower.giant(cast,gTraceBorn(cast,k));if(g==null)return null;
        int seed=k*79+4401;
        return g.point().add((Ease.hash(seed+1)-.5)*.4,(Ease.hash(seed+2)-.5)*.35-.28*age,(Ease.hash(seed+3)-.5)*.4);
    }
    /** Stardust left along the giant sword's way as its point passes, twinkling and drifting down. */
    static void giantTrace(Fx c,EffectMesh m,float t){
        float at=thrust();if(t<at||t>IsosangEmpower.arrive(c.cast)+1.05f)return;
        for(int k=0;k<G_TRACE;k++){
            float born=gTraceBorn(c.cast,k),life=gTraceLife(k),age=t-born;if(age<0||age>=life)continue;
            Vec3d p=gTraceAt(c.cast,k,age);if(p==null)continue;int seed=k*79+4401;
            float a=Ease.smooth(age,0,.03f)*(1-Ease.smooth(age,.45f*life,life))*twinkle(t,seed)*near(p,c.camera);if(a<=.01f)continue;
            glint8(m,p,.07+.05*Ease.hash(seed+4),k%3==0?WHITE:k%3==1?PALE:LILAC,.8f*a);
        }
    }
    /**
     * How long its moon takes to rise over where it stopped (seconds after it stops), how far it climbs on as the ink
     * swallows the sword, over how long, and when it is gone (seconds after the ink comes for the sword, GIANT_HANG after
     * it stops).
     */
    static final float G_MOON_RISE=.15f,G_MOON_CLIMB=1.1f,G_MOON_CLIMBING=.9f,G_MOON_OUT0=.35f,G_MOON_OUT1=.9f;
    /** When the ink comes for the giant sword (the hit's second): GIANT_HANG after it stops. */
    static float inked(StandClient.Cast cast){return IsosangEmpower.arrive(cast)+IsosangEmpower.GIANT_HANG;}
    /**
     * Where the giant sword's moon is at the hit's second {@code t} (null before it is thrust): riding its point as it flies,
     * rising over where it stopped, then climbing on, slowing, as the sword goes into ink.
     */
    static Vec3d giantMoonAt(StandClient.Cast cast,float t){
        float at=thrust(),arr=IsosangEmpower.arrive(cast);if(t<at)return null;
        var g=IsosangEmpower.giant(cast,Math.min(t,arr));if(g==null)return null;
        Vec3d ride=g.point().add(0,.35,0);if(t<=arr)return ride;
        float in=inked(cast);
        return ride.lerp(IsosangEmpower.end(cast).add(0,.9,0),Ease.smooth(t,arr,arr+G_MOON_RISE)).add(0,G_MOON_CLIMB*Ease.out(t,in,in+G_MOON_CLIMBING),0);
    }
    static float giantMoonShown(StandClient.Cast cast,float t){
        float at=thrust(),in=inked(cast);if(t<at||t>=in+G_MOON_OUT1)return 0;
        return Ease.smooth(t,at,at+.03f)*(1-Ease.smooth(t,in+G_MOON_OUT0,in+G_MOON_OUT1));
    }
    /**
     * The crescent moon riding the giant sword's point as it is thrust (its horns back the way it flies), rising over where
     * it stops, its horns turning up, and climbing on as the sword goes, a soft halo round it and a twinkle as it climbs.
     */
    static void giantMoon(Fx c,EffectMesh m,float t){
        float sh=giantMoonShown(c.cast,t);if(sh<=.01f)return;
        Vec3d p=giantMoonAt(c.cast,t);if(p==null)return;float a=sh*near(p,c.camera);if(a<=.01f)return;
        Vec3d[] uv=cameraPlane(m,p);Vec3d mv=IsosangEmpower.aim(c.cast);float arr=IsosangEmpower.arrive(c.cast),in=inked(c.cast);
        double back=uv==null?0:Math.atan2(-mv.dotProduct(uv[1]),-mv.dotProduct(uv[0])),up=Math.PI*.6;
        double d=Math.IEEEremainder(up-back,Math.PI*2),turn=back+d*Ease.smooth(t,arr,arr+G_MOON_RISE);
        double r=.42-.12*Ease.smooth(t,in,in+G_MOON_CLIMBING);
        crescentMoon(m,p,r,turn,MOON,.85f*a);m.softDisc(p,r+.08,AZURE,.22f*a);
        float tw=Ease.smooth(t,in,in+.1f);if(tw>.01f)glint8(m,p,.1+.04*twinkle(t,331),WHITE,.45f*a*tw*twinkle(t,337));
    }
    /** How strongly the hanging sword crackles at {@code t}: once it has stopped, gone as the ink comes for it. */
    static float hangCharge(StandClient.Cast cast,float t){
        float arr=IsosangEmpower.arrive(cast);
        return Ease.smooth(t,arr+.02f,arr+.08f)*(1-Ease.smooth(t,arr+IsosangEmpower.GIANT_HANG-.06f,arr+IsosangEmpower.GIANT_HANG+.04f));
    }
    static final int HANG_BOLTS=3;
    /** Lightning creeping over the hanging sword's edges (white, lilac; as R's giant sword as it hangs, SweepLights.lightning), each bolt new every fifteenth of a second, flickering. */
    static void hangLightning(Fx c,EffectMesh m,float t){
        float e=hangCharge(c.cast,t);if(e<=.01f)return;
        var g=IsosangEmpower.giant(c.cast,t);if(g==null)return;e*=g.alpha()*(1-IsosangEmpower.ink(c.cast,t));if(e<=.01f)return;
        int q=(int)Math.floor(t*15),flick=(int)Math.floor(t*24);
        m.softLine(g.at(SpiritSword.BLADE_BASE),g.at(SpiritSword.TIP*.96),.4,LILAC,.08f*e*(.7f+.3f*(float)Ease.hash(flick*13+9)));
        for(int s=0;s<HANG_BOLTS;s++){
            int seed=s*131+q*17+4501;double[] span=SweepLights.boltSpan(s,q);
            Vec3d a=on(g,SweepLights.boltOff(seed+2)*SpiritSword.half(span[0]),span[0]),z=on(g,SweepLights.boltOff(seed+5)*SpiritSword.half(span[1]),span[1]);
            float fl=e*(.6f+.4f*(float)Ease.hash(seed+8+flick*7))*near(a.lerp(z,.5),c.camera);if(fl<=.01f)continue;
            SwordFormation.bolt(m,a,z,seed,t,.12,.035,WHITE,LILAC,fl);
        }
    }
    /** Its guard's gem glinting while it hangs, flaring as it stops (no second moon: the one over where it stopped is enough). */
    static void gemGlint(Fx c,EffectMesh m,float t){
        var g=IsosangEmpower.giant(c.cast,t);if(g==null)return;float arr=IsosangEmpower.arrive(c.cast);
        float a=Ease.smooth(t,arr-.02f,arr+.06f)*(1-Ease.smooth(t,arr+IsosangEmpower.GIANT_HANG-.04f,arr+IsosangEmpower.GIANT_HANG+.03f))*g.alpha();if(a<=.01f)return;
        Vec3d p=g.at(SpiritSword.GEM_AT),to=c.camera.subtract(p);if(to.lengthSquared()<1e-6)return;
        Vec3d q=p.add(to.normalize().multiply(.1));float fl=FxLib.kick(t,arr,.1f);
        glint8(m,q,.1+.16*fl,WHITE,(.4f+.45f*fl)*a*(.75f+.25f*twinkle(t,93)));m.softDisc(q,.16,AZURE,.3f*a);
    }
    static final int G_PETALS=10;
    static float gPetalBorn(StandClient.Cast cast,int i){return IsosangEmpower.arrive(cast)+.04f+(IsosangEmpower.GIANT_HANG+.06f)*(i+(float)Ease.hash(i*97+4601))/G_PETALS;}
    static float gPetalLife(int i){return .9f+.4f*(float)Ease.hash(i*97+4602);}
    /** Where petal {@code i} is (x, y, z) and how far it has turned, {@code age} seconds after it falls off the blade: from along the blade where it hung, tumbling down slowly, swaying. */
    static double[] gPetal(StandClient.Cast cast,int i,float age){
        var g=IsosangEmpower.giant(cast,gPetalBorn(cast,i));if(g==null)return null;
        int seed=i*97+4601;double y=SpiritSword.BLADE_BASE+(SpiritSword.TIP*.95-SpiritSword.BLADE_BASE)*Ease.hash(seed+2),x=(2*Ease.hash(seed+3)-1)*SpiritSword.half(y);
        Vec3d p=on(g,x,y),side=g.dir().crossProduct(UP);side=side.lengthSquared()<1e-6?Vec3d.ZERO:side.normalize();
        p=p.add(side.multiply(.12*Math.sin(age*4.5+seed)+(Ease.hash(seed+4)-.5)*.3*age)).add(0,-(.3+.2*Ease.hash(seed+5))*age,0);
        return new double[]{p.x,p.y,p.z,age*(2.2+1.8*Ease.hash(seed+6))+seed};
    }
    /** Crystal petals falling off the giant sword while it hangs and as it fades. */
    static void petals(Fx c,EffectMesh m,boolean light){
        float t=c.sec,arr=IsosangEmpower.arrive(c.cast);if(!IsosangEmpower.flies(c.cast)||t<arr||t>arr+IsosangEmpower.GIANT_HANG+1.5f)return;
        for(int i=0;i<G_PETALS;i++){
            int seed=i*97+4601;float born=gPetalBorn(c.cast,i),life=gPetalLife(i),age=t-born;if(age<0||age>=life)continue;
            double[] pt=gPetal(c.cast,i,age);if(pt==null)continue;Vec3d p=new Vec3d(pt[0],pt[1],pt[2]);
            float a=Ease.smooth(age,0,.08f)*(1-Ease.smooth(age,life*.6f,life))*near(p,c.camera);if(a<=.01f)continue;
            SlashLights.petalAt(m,p,.09+.05*Ease.hash(seed+7),pt[3],age,seed,a,light);
        }
    }
    static final int PARTS=14;
    static float partBorn(StandClient.Cast cast,int i){return IsosangEmpower.arrive(cast)+IsosangEmpower.GIANT_HANG-.04f+.12f*(float)Ease.hash(i*59+4701);}
    static float partLife(int i){return .6f+.3f*(float)Ease.hash(i*59+4702);}
    /** Where mote {@code i} is {@code age} seconds after it leaves the blade: from along the blade as the ink came, rising and drifting out, slowing. */
    static Vec3d partAt(StandClient.Cast cast,int i,float age){
        var g=IsosangEmpower.giant(cast,partBorn(cast,i));if(g==null)return null;
        int seed=i*59+4701;double y=SpiritSword.BLADE_BASE+(SpiritSword.TIP-SpiritSword.BLADE_BASE)*Ease.hash(seed+2),x=(2*Ease.hash(seed+3)-1)*SpiritSword.half(y);
        return on(g,x,y).add((Ease.hash(seed+5)-.5)*.4*age,(.6+.5*Ease.hash(seed+4))*(1-Math.exp(-2.2*age)),(Ease.hash(seed+6)-.5)*.4*age);
    }
    /** Motes of light rising off the giant sword as the ink swallows it, twinkling and fading. */
    static void parting(Fx c,EffectMesh m,float t){
        float arr=IsosangEmpower.arrive(c.cast),t0=arr+IsosangEmpower.GIANT_HANG-.05f;if(t<t0||t>t0+1.1f)return;
        for(int i=0;i<PARTS;i++){
            float born=partBorn(c.cast,i),life=partLife(i),age=t-born;if(age<0||age>=life)continue;
            Vec3d p=partAt(c.cast,i,age);if(p==null)continue;int seed=i*59+4701;
            float a=Ease.smooth(age,0,.05f)*(1-Ease.smooth(age,.4f*life,life))*twinkle(t,seed)*near(p,c.camera);if(a<=.01f)continue;
            if(i%3==2)m.softDisc(p,.05,LILAC,.8f*a);else glint8(m,p,.07+.04*Ease.hash(seed+7),i%3==0?WHITE:PALE,.75f*a);
        }
    }
    /** Under each one the giant sword strikes, as it reaches them: a ring of light running out on the ground where they stood, and a few stars rising beside them. */
    static void struck(Fx c,EffectMesh m,float t){
        double floor=IsosangEmpower.throwFrom(c.cast).y+.05;int b=0;
        for(var e:IsosangEmpower.thrustStruck(c)){
            float at=IsosangEmpower.struckAt(c,e);b++;if(t<at-.01f||t>at+.8f)continue;
            Vec3d chest=IsosangEmpower.chestOf(c,e,at);double h=e.getHeight()*.58;
            Vec3d ground=new Vec3d(chest.x,Math.max(floor,chest.y-h+.04),chest.z);
            float a=Ease.smooth(t,at-.01f,at+.01f);
            FxLib.ring(m,ground,.35+1.7*Ease.out(t,at,at+.35f),.08,AZURE,.5f*a*(1-Ease.unit(t,at,at+.35f)));
            FxLib.ring(m,ground,.25+1.1*Ease.out(t,at,at+.25f),.03,PALE,.45f*a*(1-Ease.unit(t,at,at+.25f)));
            float vis=seen(chest,c.camera);if(vis<=.01f)continue;
            Vec3d side=c.camera.subtract(chest).crossProduct(UP);side=side.lengthSquared()<1e-6?c.right:side.normalize();
            for(int i=0;i<4;i++){
                int seed=b*211+i*37+4801;float born=at+.03f+.04f*(float)Ease.hash(seed),age=t-born,life=.5f+.25f*(float)Ease.hash(seed+1);
                if(age<0||age>=life)continue;
                Vec3d q=chest.add(side.multiply((i%2==0?1:-1)*(.45+.25*Ease.hash(seed+2)+.25*age))).add(0,-.15+.3*Ease.hash(seed+3)+1.1*Ease.out(age,0,life),0);
                float sa=(1-age/life)*Ease.smooth(age,0,.05f)*twinkle(t,seed)*vis*near(q,c.camera);if(sa<=.01f)continue;
                glint8(m,q,.08+.04*Ease.hash(seed+4),i%3==0?WHITE:i%3==1?PALE:LILAC,.8f*sa);
            }
        }
    }
    /** When she lands from her leap back (seconds into the hit: IsosangRules.backstep, over by BACKSTEP_TO). */
    static final float LAND=IsosangRules.BACKSTEP_TO/20f;
    static final int LAND_DUST=6;
    /** Where she lands from her leap back: a small ring of light round her feet and moon dust kicked up and settling. */
    static void landing(Fx c,EffectMesh m,float t){
        if(t<LAND-.01f||t>LAND+.6f)return;
        Vec3d g=IsosangEmpower.leapt(c.cast,LAND).add(0,.04,0);
        float a=Ease.smooth(t,LAND-.01f,LAND+.01f),u=Ease.unit(t,LAND,LAND+.3f);
        FxLib.ring(m,g,.3+1.3*Ease.out(t,LAND,LAND+.28f),.06,AZURE,.5f*a*(1-u));
        FxLib.ring(m,g,.2+.8*Ease.out(t,LAND,LAND+.2f),.022,WHITE,.35f*a*(1-Ease.unit(t,LAND,LAND+.2f)));
        for(int i=0;i<LAND_DUST;i++){
            int seed=i*43+4901;double az=Math.PI*2*(i+Ease.hash(seed))/LAND_DUST,out=.5+.8*Ease.hash(seed+1),up=.3+.4*Ease.hash(seed+2);
            float age=t-LAND;double s=Ease.out(age,0,.5f);
            Vec3d p=g.add(Math.cos(az)*(.25+out*s),up*Math.sin(Math.PI*Math.min(1,age/.55)),Math.sin(az)*(.25+out*s));
            float da=a*(1-Ease.smooth(age,.3f,.55f))*twinkle(t,seed)*near(p,c.camera);if(da<=.01f)continue;
            if(i%2==0)glint8(m,p,.07,i%4==0?WHITE:PALE,.7f*da);else m.softDisc(p,.045,LILAC,.8f*da);
        }
    }
}
