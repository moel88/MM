package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.*;

/**
 * The basic attack's moonlight and starlight made prettier (2.103.0, user request: "이번에는 평타와 e스킬 강화 평타 예쁘게
 * 바꿔줘"), over its own (2.77.5: IsosangSlash.stars, heads, spray, the moon in each burst, hit 4's stardust) and as R's
 * (SweepLights), T's (LungeLights) and H's (FormationLights); small and few, as the basic attack must stay (2.77.4: its
 * slashes the heroes, she and whom they strike always seen; no screen effect, 2.102.1):
 * <ul>
 * <li>each cut's moon trace: stardust left along its cutting edge as its head passes, drifting out and down, twinkling
 * out ({@link #trace});</li>
 * <li>a small crescent moon where each cut's point stops (斬月), its horns back the way it came, rising a little as it
 * fades ({@link #endMoons});</li>
 * <li>crystal petals shed as each cut breaks up, tumbling down and catching the light ({@link #petals});</li>
 * <li>hit 2 (the ringed moon): as its second cut closes the ring, a halo of stars running round her the way both cuts
 * went ({@link #halo});</li>
 * <li>hit 4 (落月, the falling moon): the moon riding the thrown slash sets as the slash goes to ink, sinking under
 * where it stopped ({@link #waveMoon}; it rode the slash alone before, IsosangSlash.waveStars);</li>
 * <li>a few small stars rising beside whom each blow strikes, never over them ({@link #struck});</li>
 * <li>the empowered combo's second and third (E): lilac lightning crawling along the blade, the trace's stardust thicker
 * and more lilac ({@link #lightning}).</li>
 * </ul>
 * Not hit 3's X: its second and third cuts cross some 3 blocks ahead at the chest as its third blow lands, where a star
 * would sit on whom it strikes. Every one is a function of the swing's clock and of where the hit stands, the same for
 * everyone.
 */
final class SlashLights {
    private SlashLights(){}

    /** The blended pass: the petals' bodies. */
    static void alpha(Fx c,EffectMesh m){petals(c,m,false);}
    /** The light pass (under the ink): the petals' glints. */
    static void additive(Fx c,EffectMesh m){petals(c,m,true);}
    /** Over the ink ribbons (the slashes' ink would hide them): the stardust, the moons, the halo, the stars by the struck, the lightning. */
    static void overlay(Fx c,EffectMesh m){
        trace(c,m);
        endMoons(c,m);
        halo(c,m);
        waveMoon(c,m);
        struck(c,m);
        lightning(c,m);
    }

    /** Whether swing {@code c} is the empowered combo's second or third (its first and fourth are IsosangEmpower's). */
    static boolean charged(Fx c){int combo=c.cast.combo();return (combo==2||combo==3)&&IsosangClient.empowered(c.cast.id());}
    /** When slash {@code s}'s head reaches fraction {@code f} of its sweep (seconds into the hit; its pace's own curve, IsosangSlash.head). */
    static float passes(Arc s,double f){
        if(f<=0)return s.t0();if(f>=1)return s.t1();
        float lo=s.t0(),hi=s.t1();
        for(int i=0;i<24;i++){float mid=(lo+hi)*.5f;if(head(s,mid)<f)lo=mid;else hi=mid;}
        return (lo+hi)*.5f;
    }

    // ───────────────────────────── the moon's trace ─────────────────────────────

    /** How many motes of stardust slash {@code s} leaves: one every 24° of its sweep, 8 to 12; half again in the empowered combo. */
    static int traceCount(Arc s,boolean charged){int n=Math.max(8,Math.min(12,(int)Math.round(Math.abs(s.to()-s.from())/24)));return charged?n*3/2:n;}
    /** Mote {@code k}'s place along slash {@code s} (0 its tail, 1 its head): spread over its sweep. */
    static double traceFrac(Arc s,int k,int n){return .1+.86*(k+.2+.6*Ease.hash(k*83+s.seed()*331+5))/n;}
    /** When mote {@code k} is left: as the head passes its place. */
    static float traceBorn(Arc s,int k,int n){return passes(s,traceFrac(s,k,n));}
    static float traceLife(Arc s,int k){return TRACE_LIFE0+(TRACE_LIFE1-TRACE_LIFE0)*(float)Ease.hash(k*83+s.seed()*331+11);}
    static final float TRACE_LIFE0=.5f,TRACE_LIFE1=.8f;
    /** Whether mote {@code k} is left at all: not off the slash's thin tips (where its band is all but a line). */
    static boolean traced(Arc s,int k,int n){return profile(s.style(),traceFrac(s,k,n))>=.18;}
    /** Where mote {@code k} is (the hit's frame) {@code age} seconds after the head passed: just out from the cutting edge where it passed, scattered a little, drifting out and down. */
    static Vec3d traceLocal(Arc s,int k,int n,float age){
        int seed=k*83+s.seed()*331;double deg=s.from()+(s.to()-s.from())*traceFrac(s,k,n);
        Vec3d p=point(s,deg,s.outer()*(1+.06*Ease.hash(seed+1)));
        Vec3d out=p.subtract(s.centre());out=new Vec3d(out.x,0,out.z);out=out.lengthSquared()<1e-8?Vec3d.ZERO:out.normalize();
        return p.add(out.multiply(.1*(Ease.hash(seed+2)-.3)+.12*age)).add(0,(Ease.hash(seed+3)-.5)*.2-.26*age,0);
    }
    /** The moon's trace: stardust left along each cut's edge as its head passes, twinkling and fading. */
    static void trace(Fx c,EffectMesh m){
        float t=c.sec;int combo=c.cast.combo();boolean ch=charged(c);Vec3d feet=anchor(c);float yaw=c.cast.yaw();
        for(var s:arcs(combo)){
            if(t<s.t0()||t>s.t1()+TRACE_LIFE1)continue;
            int n=traceCount(s,ch);
            for(int k=0;k<n;k++){
                if(!traced(s,k,n))continue;
                float born=traceBorn(s,k,n),life=traceLife(s,k),age=t-born;if(age<0||age>=life)continue;
                int seed=k*83+s.seed()*331;Vec3d q=traceLocal(s,k,n,age);if(q.y<.05)continue;
                Vec3d p=world(feet,yaw,q);
                float a=Ease.smooth(age,0,.03f)*(1-Ease.smooth(age,.45f*life,life))*twinkle(t,seed)*near(p,c.camera);if(a<=.01f)continue;
                int col=ch?(k%3==2?PALE:LILAC):(k%3==0?WHITE:k%3==1?PALE:LILAC);
                glint8(m,p,.055+.04*Ease.hash(seed+4),col,.8f*a);
            }
        }
    }

    // ───────────────────────────── where each cut stops ─────────────────────────────

    /** How long each cut's moon shows after its point stops (seconds), and how big it is (blocks). */
    static final float MOON_LIFE=.5f;
    static final double MOON_R=.2;
    /** Where slash {@code s}'s moon is (the hit's frame) {@code age} seconds after its point stopped: just out from where it stopped, rising a little, kept off the ground. */
    static Vec3d moonLocal(Arc s,float age){
        Vec3d e=point(s,s.to(),s.outer()),out=e.subtract(s.centre()).normalize();
        Vec3d p=e.add(out.multiply(.18)).add(0,.3*Ease.out(age,0,MOON_LIFE),0);
        return new Vec3d(p.x,Math.max(.3,p.y),p.z);
    }
    /** The way slash {@code s}'s point moves as it stops (the hit's frame, a unit vector). */
    static Vec3d endWay(Arc s){
        double sg=Math.signum(s.to()-s.from());return point(s,s.to(),s.outer()).subtract(point(s,s.to()-3*sg,s.outer())).normalize();
    }
    /** How strongly slash {@code s}'s moon shows at {@code t}: as its point stops, fading within half a second. */
    static float moonShown(Arc s,float t){float age=t-s.t1();return age<-.01f||age>=MOON_LIFE?0:Ease.smooth(age,-.01f,.02f)*(1-Ease.smooth(age,.22f,MOON_LIFE));}
    /** The small crescent moon where each of hits 1 to 3's cuts stops (not hit 4's low cut: its thrown slash carries the moon). */
    static void endMoons(Fx c,EffectMesh m){
        int combo=c.cast.combo();if(combo<1||combo>3)return;
        float t=c.sec;Vec3d feet=anchor(c);float yaw=c.cast.yaw();boolean ch=charged(c);
        for(var s:arcs(combo)){
            float sh=moonShown(s,t);if(sh<=.01f)continue;float age=t-s.t1();
            Vec3d p=world(feet,yaw,moonLocal(s,age)),way=dir(yaw,endWay(s));
            float a=sh*near(p,c.camera);if(a<=.01f)continue;
            Vec3d[] uv=cameraPlane(m,p);double turn=uv==null?0:Math.atan2(-way.dotProduct(uv[1]),-way.dotProduct(uv[0]));
            crescentMoon(m,p,MOON_R,turn,MOON,.85f*a);
            float pop=1-Ease.unit(age,0,.12f);
            glint8(m,p,.08+.18*pop,WHITE,(.35f+.45f*pop)*a);
            m.softDisc(p,.32,ch?LILAC:AZURE,.18f*a);
        }
    }

    // ───────────────────────────── crystal petals ─────────────────────────────

    /** How many petals slash {@code s} sheds as it breaks up: three, four off hit 1's, five off hit 3's last and biggest. */
    static int petalCount(Arc s){return s==H3_ARCS[3]?5:s==H1_ARCS[0]?4:3;}
    static float petalBorn(Arc s,int i){return s.eat0()+(s.eat1()-s.eat0())*(float)Ease.hash(i*97+s.seed()*211+3);}
    static float petalLife(Arc s,int i){return PETAL_LIFE0+(PETAL_LIFE1-PETAL_LIFE0)*(float)Ease.hash(i*97+s.seed()*211+4);}
    static final float PETAL_LIFE0=.8f,PETAL_LIFE1=1.2f;
    /**
     * Where petal {@code i} of slash {@code s} is (the hit's frame: x, y, z) and how far it has turned, {@code age}
     * seconds after it is shed: from where the cut is being eaten (its tail on), tumbling down slowly, swaying, drifting
     * out; resting on the ground once it gets there.
     */
    static double[] petal(Arc s,int i,float age){
        int seed=i*97+s.seed()*211;float born=petalBorn(s,i);
        double x=Math.max(0,Math.min(1,Ease.smooth(born,s.eat0(),s.eat1())*1.05+.06*(Ease.hash(seed+5)-.5)));
        Vec3d at=point(s,s.from()+(s.to()-s.from())*x,s.outer()-(s.outer()-s.inner())*.25*Ease.hash(seed+6));
        Vec3d out=at.subtract(s.centre());out=new Vec3d(out.x,0,out.z);out=out.lengthSquared()<1e-8?Vec3d.ZERO:out.normalize();
        Vec3d side=out.crossProduct(UP);double fall=.32+.2*Ease.hash(seed+7);
        Vec3d p=at.add(out.multiply(.18*age)).add(side.multiply(.12*Math.sin(age*4.5+seed))).add(0,-fall*age,0);
        return new double[]{p.x,Math.max(.03,p.y),p.z,age*(2.2+1.8*Ease.hash(seed+8))+seed};
    }
    /** The petals shed off hits 1 to 3's cuts: their see-through bodies ({@code light} false), or a soft glow and now and then a glint as one catches the light. */
    static void petals(Fx c,EffectMesh m,boolean light){
        int combo=c.cast.combo();if(combo<1||combo>3)return;
        float t=c.sec;Vec3d feet=anchor(c);float yaw=c.cast.yaw();
        for(var s:arcs(combo)){
            if(t<s.eat0()||t>s.eat1()+PETAL_LIFE1)continue;
            for(int i=0;i<petalCount(s);i++){
                int seed=i*97+s.seed()*211;float born=petalBorn(s,i),life=petalLife(s,i),age=t-born;if(age<0||age>=life)continue;
                double[] pt=petal(s,i,age);Vec3d p=world(feet,yaw,new Vec3d(pt[0],pt[1],pt[2]));
                float a=Ease.smooth(age,0,.08f)*(1-Ease.smooth(age,life*.6f,life))*near(p,c.camera);if(a<=.01f)continue;
                petalAt(m,p,.08+.05*Ease.hash(seed+9),pt[3],age,seed,a,light);
            }
        }
    }
    /** A crystal petal at {@code p} ({@code size} long), turned {@code turn}: a lens fixed in the world (not facing the camera), white along its ridge; or its light. */
    static void petalAt(EffectMesh m,Vec3d p,double size,double turn,float age,int seed,float a,boolean light){
        Vec3d axis=new Vec3d(Math.cos(turn),.55*Math.sin(turn*.7),Math.sin(turn)).normalize();
        Vec3d across=axis.crossProduct(new Vec3d(Math.sin(turn*.5),.8,Math.cos(turn*.5))).normalize();
        if(!light){
            Vec3d u=axis.multiply(size),w=across.multiply(size*.38);
            m.quad(p.subtract(u),p.subtract(w),p.add(u),p.add(w),SweepLights.PETAL,.62f*a);
            m.line(p.subtract(u),p.add(u),size*.12,PALE,.55f*a);
        }else{
            double ph=((age+Ease.hash(seed+10)*.8)%.8)/.8;float spark=(float)Math.max(0,1-Math.abs(ph-.5)/.12);
            m.softDisc(p,size*1.6,AZURE,.14f*a);
            if(spark>.05f)glint8(m,p,size*1.6,WHITE,.65f*a*spark*spark);
        }
    }

    // ───────────────────────────── hit 2: the halo ─────────────────────────────

    /** The halo round her (the hit's frame): between the two cuts' heights, as far out as their middles. */
    static final Vec3d HALO_C=v(.15,.5,0);
    static final double HALO_R=2.35;
    static final int HALO=12;
    /** How long the halo takes to run round, and how long each star lasts (seconds). */
    static final float HALO_RUN=.12f,HALO_LIFE=.45f;
    /** Halo star {@code k}'s angle (degrees off the front, right +): from where the front cut stopped round to the left, anticlockwise seen from above as both cuts went. */
    static double haloDeg(int k){return H2_ARCS[1].to()-360.0*(k+.5)/HALO;}
    /** When halo star {@code k} lights (seconds into hit 2): as the front cut closes the ring, running round. */
    static float haloLit(int k){return H2_ARCS[1].t1()+HALO_RUN*(k+.5f)/HALO;}
    static float haloLife(int k){return HALO_LIFE+.15f*(float)Ease.hash(k*53+71);}
    /** Where halo star {@code k} is (the hit's frame): on the ring, waving a little up and down. */
    static Vec3d haloLocal(int k){double a=Math.toRadians(haloDeg(k));return HALO_C.add(HALO_R*Math.cos(a),.12*Math.sin(3*a),HALO_R*Math.sin(a));}
    static float haloShown(int k,float t){float age=t-haloLit(k),life=haloLife(k);return age<0||age>=life?0:Ease.smooth(age,0,.03f)*(1-Ease.smooth(age,.15f,life));}
    /** Hit 2's halo: stars lighting one after another round her as its two cuts make their ring, twinkling out. */
    static void halo(Fx c,EffectMesh m){
        if(c.cast.combo()!=2)return;
        float t=c.sec;Vec3d feet=anchor(c);float yaw=c.cast.yaw();
        for(int k=0;k<HALO;k++){
            float sh=haloShown(k,t);if(sh<=.01f)continue;
            Vec3d p=world(feet,yaw,haloLocal(k));float a=sh*twinkle(t,k*53+73)*near(p,c.camera);if(a<=.01f)continue;
            glint8(m,p,.12+.05*Ease.hash(k*53+79),k%3==0?WHITE:k%3==1?PALE:LILAC,.85f*a);
        }
    }

    // ───────────────────────────── hit 4: the falling moon ─────────────────────────────

    /** When the moon sets (seconds into hit 4): as the thrown slash goes to ink, for half a second. */
    static final float SET0=WAVE_INK0,SET1=WAVE_INK0+.5f;
    /** How far it sinks as it sets (blocks). */
    static final double SET_DROP=.95;
    /**
     * Where the falling moon is at hit 4's second {@code t} (world; null before it is thrown): riding the thrown slash's
     * leading edge as it flies and while it hangs, then setting, sinking faster and faster under where it stopped.
     */
    static Vec3d waveMoonAt(Vec3d feet,float yaw,float t){
        var sw=wave(feet,yaw,Math.min(t,SET0));if(sw==null)return null;
        Vec3d front=sw.outer()[sw.n()/2].add(0,.3,0);
        float u=Ease.unit(t,SET0,SET1);return front.add(0,-SET_DROP*u*u,0);
    }
    /** How strongly the falling moon shows at {@code t}: in with the throw, out as it sets. */
    static float waveMoonShown(float t){
        float at=blow(4,0);if(t<at||t>=SET1)return 0;
        return Ease.smooth(t,at,at+.03f)*(1-Ease.smooth(Ease.unit(t,SET0,SET1),.55f,1));
    }
    /** Hit 4's moon: riding the thrown slash, then setting as it goes to ink, its horns turning up to the sky. */
    static void waveMoon(Fx c,EffectMesh m){
        if(c.cast.combo()!=4)return;
        float t=c.sec,sh=waveMoonShown(t);if(sh<=.01f)return;
        Vec3d p=waveMoonAt(anchor(c),c.cast.yaw(),t);if(p==null)return;
        float u=Ease.unit(t,SET0,SET1),a=sh*near(p,c.camera);if(a<=.01f)return;
        crescentMoon(m,p,.3-.06*u,Math.PI*(.62-.12*u),MOON,.85f*a);
        glint8(m,p,.2,WHITE,.6f*a*(1-u));m.softDisc(p,.45,AZURE,.25f*a);
    }

    // ───────────────────────────── whom they strike ─────────────────────────────

    static final int STRUCK_STARS=3;
    /** A few small stars rising beside each one struck, at each blow (to their sides as seen, never over them), twinkling out. */
    static void struck(Fx c,EffectMesh m){
        int combo=c.cast.combo();if(combo<1||combo>4)return;float t=c.sec;
        for(var w:strikes(c,combo)){
            float at=w.when();if(t<at||t>at+.75f)continue;
            Vec3d p=w.at();float vis=seen(p,c.camera);if(vis<=.01f)continue;
            Vec3d side=c.camera.subtract(p).crossProduct(UP);side=side.lengthSquared()<1e-6?c.right:side.normalize();
            for(int i=0;i<STRUCK_STARS;i++){
                int seed=combo*131+w.blow()*37+w.nth()*17+i*7;float born=at+.03f+.04f*(float)Ease.hash(seed),age=t-born,life=.45f+.2f*(float)Ease.hash(seed+1);
                if(age<0||age>=life)continue;
                Vec3d q=p.add(side.multiply((i%2==0?1:-1)*(.42+.25*Ease.hash(seed+2)+.25*age))).add(0,-.15+.3*Ease.hash(seed+3)+Ease.out(age,0,life),0);
                float a=(1-age/life)*Ease.smooth(age,0,.05f)*twinkle(t,seed)*vis*near(q,c.camera);if(a<=.01f)continue;
                glint8(m,q,.08+.04*Ease.hash(seed+4),i%3==0?WHITE:i%3==1?PALE:LILAC,.8f*a);
            }
        }
    }

    // ───────────────────────────── the empowered blade ─────────────────────────────

    /** How strongly the empowered blade crackles at hit {@code combo}'s second {@code t}: from the start until its last cut is done. */
    static float charge(int combo,float t){
        float end=combo==2?H2_ARCS[1].t1()+.1f:H3_ARCS[3].t1()+.12f;
        return Ease.smooth(t,0,.12f)*(1-Ease.smooth(t,end-.04f,end+.08f));
    }
    static final int BOLTS=2;
    /** Which side of the blade one end of a bolt is on and how far out (blocks): just off its edges. */
    static double boltOff(int seed){return (Ease.hash(seed)<.5?-1:1)*(.07+.05*Ease.hash(seed+1));}
    /** The empowered second and third: lilac lightning crawling along the blade, each bolt new every fifteenth of a second, flickering. */
    static void lightning(Fx c,EffectMesh m){
        if(!charged(c))return;
        float t=c.sec,e=charge(c.cast.combo(),t);if(e<=.01f)return;
        var hb=bladeNow(c);Vec3d guard=hb[0].add(hb[1].multiply(GUARD)),tip=hb[0].add(hb[1].multiply(TIP));
        Vec3d side=hb[1].crossProduct(UP);side=side.lengthSquared()<1e-4?c.right:side.normalize();Vec3d up=side.crossProduct(hb[1]).normalize();
        int q=(int)Math.floor(t*15),flick=(int)Math.floor(t*24);
        m.softLine(guard,tip,.32,LILAC,.08f*e*(.7f+.3f*(float)Ease.hash(flick*13+5)));
        for(int s=0;s<BOLTS;s++){
            int seed=s*131+q*17+3031;double y0=.12+.5*Ease.hash(seed),y1=Math.min(.97,y0+.3+.3*Ease.hash(seed+1));
            Vec3d a=guard.lerp(tip,y0).add(side.multiply(boltOff(seed+2))).add(up.multiply((Ease.hash(seed+3)-.5)*.06));
            Vec3d z=guard.lerp(tip,y1).add(side.multiply(boltOff(seed+5))).add(up.multiply((Ease.hash(seed+6)-.5)*.06));
            float fl=e*(.6f+.4f*(float)Ease.hash(seed+8+flick*7))*near(a.lerp(z,.5),c.camera);if(fl<=.01f)continue;
            SwordFormation.bolt(m,a,z,seed,t,.1,.04,WHITE,LILAC,.85f*fl);
        }
    }

    /** The latest time (seconds into a hit) any of it is drawn. */
    static float last(){
        float m=SET1;
        for(int combo=1;combo<=4;combo++)for(var s:arcs(combo)){
            m=Math.max(m,s.t1()+TRACE_LIFE1);m=Math.max(m,s.t1()+MOON_LIFE);m=Math.max(m,s.eat1()+PETAL_LIFE1);
        }
        for(int k=0;k<HALO;k++)m=Math.max(m,haloLit(k)+haloLife(k));
        return m;
    }
}
