package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.*;
import static dev.sangwoo.celestialfall.isosang.IsosangRules.*;

/**
 * T's moonlight and starlight (2.100.0, user request: "이번에는 T스킬도 엄청 예쁘게 꾸며줘"), as her sword's (월흔, the
 * moon's trace) and the formation's (FormationLights, FormationSky):
 * <ul>
 * <li>as she coils: motes of light drawn in to her and a ring tightening round her ({@link #coil});</li>
 * <li>as she springs off: a ring of light on the pommel she leaves, a gleam running down that sword and a white crystal
 * flower blooming at its foot as at Z's (SwordStep.bloom; {@link #takeoff});</li>
 * <li>the line of ink to the foe with a few stars in it ({@link #aimStars});</li>
 * <li>her dash's light: stars riding its bright body until it cools, faint stars in its rim as it turns to ink
 * ({@link #dashStars}), and crystal petals shed along her way, drifting down, tumbling, catching the light now and then
 * ({@link #petals});</li>
 * <li>as she cuts the foe: a ring of light running out on the ground under it and a few stars rising off it
 * ({@link #pass}); as she lands before it, a small ring round her feet and moon dust kicked up ({@link #landing});</li>
 * <li>her flight on to the far sword: stardust left along its way ({@link #stardust});</li>
 * <li>the six swords: stardust shed along each one's flight to the foe, as comets ({@link #swordDust});</li>
 * <li>the three slashes: starry sparks springing off each one's edges as it is drawn, faint stars in its ink, never at
 * its ends nor at its middle where they cross before the foe (2.96.3 took the glints off their ends: gathered, they read
 * as one star), and moon dust sprayed off each as it is eaten ({@link #slashSparks}, {@link #slashStars},
 * {@link #slashDust}).</li>
 * </ul>
 * None of what the user tuned is changed: the dash's light and rim, the three slashes' size, place, randomness and timing,
 * the swords, the body, the camera; nothing white-hot over the screen.
 */
final class LungeLights {
    private LungeLights(){}

    /** The blended pass: the crystal petals and the flower at the foot of the sword she leaves. */
    static void alpha(Fx c,EffectMesh m){
        float t=c.sec,k=SwordLunge.kept(c.cast.id(),t);if(k<=.01f)return;
        petals(c,m,t,k,false);
        takeoffFlower(c,m,t,false);
    }
    /** The light pass (under the ink). */
    static void additive(Fx c,EffectMesh m){
        float t=c.sec,k=SwordLunge.kept(c.cast.id(),t);if(k<=.01f)return;
        Vec3d foe=SwordVolley.target(c.cast,c.delta);
        coil(c,m,t,k);
        takeoff(c,m,t,k);
        takeoffFlower(c,m,t,true);
        petals(c,m,t,k,true);
        if(foe==null)return;
        aimStars(c,m,t,k,foe);
        pass(c,m,t,k,foe);
        landing(c,m,t,k);
        stardust(c,m,t,k);
        slashDust(c,m,t,k,foe);
    }
    /** Over the ink (the dash's light and rim and the slashes are ink ribbons, drawn after the light pass): the stars on them. */
    static void overlay(Fx c,EffectMesh m){
        float t=c.sec,k=SwordLunge.kept(c.cast.id(),t);if(k<=.01f)return;
        Vec3d foe=SwordVolley.target(c.cast,c.delta);if(foe==null)return;
        dashStars(c,m,t,k,foe);
        swordDust(c,m,t,k,foe);
        slashStars(c,m,t,k,foe);
        slashSparks(c,m,t,k,foe);
    }

    // ───────────────────────────── as she coils and springs off ─────────────────────────────

    /** Motes of light drawn in to her chest as she coils (each over a tenth of a second, a short trail behind it), a ring tightening round her. */
    static void coil(Fx c,EffectMesh m,float t,float k){
        float go=LUNGE_GO/20f;if(t>go+.04f)return;
        Vec3d chest=c.cast.origin().add(0,SwordLunge.CHEST,0);
        for(int i=0;i<COIL_MOTES;i++){
            int seed=i*53+7;float born=.09f*(float)Ease.hash(seed),u=Ease.unit(t,born,born+COIL_LIFE);if(t<born||u>=1)continue;
            double az=Math.PI*2*Ease.hash(seed+1),el=(Ease.hash(seed+2)-.5)*1.4,r0=1.1+.8*Ease.hash(seed+3);
            Vec3d dir=new Vec3d(Math.cos(el)*Math.cos(az),Math.sin(el),Math.cos(el)*Math.sin(az));
            double r=.18+(r0-.18)*(1-Math.pow(u,1.5));
            Vec3d p=chest.add(dir.multiply(r)),q=chest.add(dir.multiply(Math.min(r0,r+.3*(1-u)+.05)));
            float a=k*Ease.smooth(u,0,.25f)*(1-Ease.smooth(u,.8f,1))*near(p,c.camera);if(a<=.01f)continue;
            int col=i%3==0?WHITE:i%3==1?LILAC:PALE;
            m.softTaper(q,p,0,.05,col,.45f*a);m.softDisc(p,.06,WHITE,.8f*a);
        }
        // the ring tightening round her chest, facing the camera
        float ra=k*Ease.smooth(t,.02f,.06f)*(1-Ease.smooth(t,go-.02f,go+.03f));
        if(ra>.01f){
            Vec3d[] uv=cameraPlane(m,chest);
            if(uv!=null)FxLib.ringPlane(m,chest,uv[0],uv[1],.35+1.0*(1-Ease.out(t,.02f,go)),.035,LILAC,.5f*ra);
        }
    }
    static final int COIL_MOTES=16;
    static final float COIL_LIFE=.1f;

    /** As she springs off the pommel (LUNGE_GO): a ring of light opening round it and a gleam running down the sword she leaves. */
    static void takeoff(Fx c,EffectMesh m,float t,float k){
        float go=LUNGE_GO/20f;if(t<go-.01f||t>go+.4f)return;
        Vec3d feet=c.cast.origin().add(0,.03,0);float u=Ease.unit(t,go,go+.32f),a=k*Ease.smooth(t,go-.01f,go+.01f)*(1-u);
        FxLib.ring(m,feet,.25+1.5*Ease.out(t,go,go+.28f),.08,AZURE,.6f*a);
        FxLib.ring(m,feet,.15+.9*Ease.out(t,go,go+.2f),.03,WHITE,.5f*a);
        m.softDisc(feet.add(0,.15,0),.9,BLUE,.22f*a);
        // the gleam down the sword under her, from its guard to its point in the ground
        var sw=leaving(c);if(sw==null)return;
        Vec3d top=sw[0],bottom=sw[1];
        float g=Ease.unit(t,go,go+GLEAM);if(g<=0||g>=1)return;
        Vec3d head=top.lerp(bottom,.12+.88*g),tail=top.lerp(bottom,Math.max(.12,.12+.88*g-.3));
        float ga=k*(float)Math.sin(Math.PI*g);
        m.softTaper(tail,head,.02,.09,PALE,.6f*ga);m.softDisc(head,.18,WHITE,.6f*ga);
    }
    static final float GLEAM=.22f;
    /** The sword she springs off (its pommel, its foot), or null when it is not known. */
    static Vec3d[] leaving(Fx c){
        var run=IsosangClient.lunge(c.cast.id());var form=IsosangClient.formationOf(c.cast.caster());if(run==null||form==null)return null;
        var all=SwordFormation.layout(form.id());if(run.from()<0||run.from()>=all.length)return null;
        Vec3d centre=SwordFormation.centre(form);float yaw=SwordFormation.yaw(form);
        return new Vec3d[]{SwordFormation.planted(centre,yaw,all[run.from()]),SwordFormation.foot(centre,yaw,all[run.from()]),new Vec3d(all[run.from()].seed(),0,0)};
    }
    /** The white crystal flower at the foot of the sword she leaves, blooming as she springs off and gone again (Z's, SwordStep.bloom). */
    static void takeoffFlower(Fx c,EffectMesh m,float t,boolean light){
        float go=LUNGE_GO/20f;if(t<go-.01f||t>go+SwordStep.DEPART_FADE1+.02f)return;
        var sw=leaving(c);if(sw==null)return;
        SwordStep.bloom(m,sw[1].add(0,.03,0),(int)sw[2].x+11,SwordStep.departs(t-go),light,t);
    }

    // ───────────────────────────── the line of ink and her dash ─────────────────────────────

    /** A few small stars in the line of ink drawn to the foe as she coils (as the ink of her slashes holds a night's stars). */
    static void aimStars(Fx c,EffectMesh m,float t,float k,Vec3d foe){
        if(t<SwordLunge.AIM0||t>=SwordLunge.AIM_GONE1)return;
        Vec3d wl=SwordLunge.way(c.cast,foe),from=c.cast.origin().add(0,SwordLunge.CHEST,0).subtract(wl.multiply(SwordLunge.AIM_BACK)).add(0,-.5,0);
        float drawn=Ease.out(t,SwordLunge.AIM0,SwordLunge.AIM1),gone=1-Ease.smooth(t,SwordLunge.AIM_GONE0,SwordLunge.AIM_GONE1);
        for(int i=0;i<AIM_STARS.length;i++){
            double f=AIM_STARS[i];if(drawn<f)continue;
            Vec3d p=from.lerp(foe,f);float a=k*gone*twinkle(t,i*29+3)*near(p,c.camera);if(a<=.01f)continue;
            glint8(m,p,.11,i%2==0?WHITE:PALE,.7f*a);
        }
    }
    static final double[] AIM_STARS={.2,.43,.62,.81};

    /**
     * Where the i-th star on her dash's light lies in it: its share of the way along the drop from its tail to the foe
     * (kept off the foe's end, where the burst is) and its share of the drop's half-width to its side.
     */
    static double[] dashStar(int i){
        int seed=i*71+13;
        return new double[]{DASH_FROM+(DASH_TO-DASH_FROM)*Ease.hash(seed),(2*Ease.hash(seed+1)-1)*.72};
    }
    static final int DASH_STARS=16;
    static final double DASH_FROM=.08,DASH_TO=.82;
    /** The rim's faint stars: their share of the way along the drop and which side (as dashStar). */
    static double[] rimStar(int i){
        int seed=i*83+29;
        return new double[]{.15+.65*Ease.hash(seed),Ease.hash(seed+1)<.5?-1:1};
    }
    static final int RIM_STARS=10;
    /**
     * The stars on her dash's light (over the ink): small eight-pointed stars riding its bright body, each from as its
     * head passes it, twinkling, gone as its light cools or is eaten; faint ones in its rim once it is ink, gone as it
     * is eaten from its ends toward the foe.
     */
    static void dashStars(Fx c,EffectMesh m,float t,float k,Vec3d foe){
        float l=SwordLunge.light();if(t<l-.01f||t>=l+SwordLunge.RIM_EAT1)return;
        var d=SwordLunge.drop(c.cast,foe,t);double full=Math.max(.5,foe.distanceTo(d.tail())),tip=d.tip();
        float cool=1-Ease.smooth(t,l+SwordLunge.FILL_INK0,l+SwordLunge.FILL_INK1+.03f),eat=Ease.smooth(t,l+SwordLunge.FILL_EAT0,l+SwordLunge.FILL_EAT1);
        if(cool>.01f)for(int i=0;i<DASH_STARS;i++){
            double[] st=dashStar(i);double s=st[0]*full;if(d.length()<s||eat>s/tip-.02)continue;
            Vec3d p=d.at(s,st[1]*d.width(s));int seed=i*71+13;
            float a=k*cool*twinkle(t,seed)*near(p,c.camera)*Ease.smooth((float)(d.length()-s),0,.3f);if(a<=.01f)continue;
            glint8(m,p,.1+.07*Ease.hash(seed+2),i%3==0?WHITE:i%3==1?PALE:LILAC,.85f*a);
        }
        float inked=Ease.smooth(t,l+SwordLunge.RIM_INK0,l+SwordLunge.RIM_INK1),rimEat=Ease.smooth(t,l+SwordLunge.RIM_EAT0,l+SwordLunge.RIM_EAT1);
        if(inked>.01f)for(int i=0;i<RIM_STARS;i++){
            double[] st=rimStar(i);double s=st[0]*full;if(rimEat>s/tip-.03)continue;
            Vec3d p=d.at(s,st[1]*Math.max(0,d.width(s)-SwordLunge.RIM*.5));int seed=i*83+29;
            float a=k*inked*twinkle(t,seed)*near(p,c.camera);if(a<=.01f)continue;
            glint8(m,p,.07+.04*Ease.hash(seed+2),i%2==0?PALE:WHITE,.5f*a);
        }
    }

    /**
     * A crystal petal shed along her dash (i of PETALS): where it is {@code age} seconds after it is shed (from where her
     * dash's light passes it, {@code from} her start to {@code to} where she lands, a little to either side and above),
     * drifting down and out slowly and swaying, its tumble (radians) in its 4th value.
     */
    static double[] petal(Vec3d from,Vec3d to,Vec3d side,int i,float age){
        int seed=i*97+41;double f=Ease.hash(seed);
        Vec3d p0=from.lerp(to,f).add(side.multiply((2*Ease.hash(seed+1)-1)*1.3)).add(0,(Ease.hash(seed+2)-.3)*1.1,0);
        double out=(2*Ease.hash(seed+3)-1)*.35,fall=PETAL_FALL*(.7+.6*Ease.hash(seed+4));
        double sway=.12*Math.sin(age*(3+2*Ease.hash(seed+5))+seed);
        Vec3d p=p0.add(side.multiply(out*age+sway)).add(0,-fall*age,0);
        return new double[]{p.x,p.y,p.z,age*(4+5*Ease.hash(seed+6))+seed};
    }
    static final int PETALS=22;
    static final double PETAL_FALL=.45;
    static final float PETAL_LIFE0=.9f,PETAL_LIFE1=1.2f;
    /** When petal i is shed (seconds into T): as her dash's light passes its place. */
    static float shed(int i){return SwordLunge.light()+SwordLunge.RACE*(float)Ease.hash(i*97+41);}
    /** The petals (the blended pass: pale crystal, tumbling; the light pass: their glints now and then). */
    static void petals(Fx c,EffectMesh m,float t,float k,boolean light){
        Vec3d foe=SwordVolley.target(c.cast,c.delta);if(foe==null)return;
        Vec3d from=c.cast.origin().add(0,SwordLunge.CHEST*.7,0),to=SwordLunge.past(c.cast).add(0,SwordLunge.CHEST*.7,0);
        Vec3d wl=SwordLunge.way(c.cast,foe),side=new Vec3d(-wl.z,0,wl.x);
        for(int i=0;i<PETALS;i++){
            int seed=i*97+41;float born=shed(i),life=PETAL_LIFE0+(PETAL_LIFE1-PETAL_LIFE0)*(float)Ease.hash(seed+7),age=t-born;
            if(age<0||age>=life)continue;
            double[] pt=petal(from,to,side,i,age);Vec3d p=new Vec3d(pt[0],pt[1],pt[2]);
            float a=k*Ease.smooth(age,0,.08f)*(1-Ease.smooth(age,life*.6f,life))*near(p,c.camera);if(a<=.01f)continue;
            double size=.1+.07*Ease.hash(seed+8),turn=pt[3];
            Vec3d axis=new Vec3d(Math.cos(turn),.55*Math.sin(turn*.7),Math.sin(turn)).normalize();
            Vec3d across=axis.crossProduct(new Vec3d(Math.sin(turn*.5),.8,Math.cos(turn*.5))).normalize();
            if(!light){
                // a pointed crystal leaf, world-fixed and turning (never facing the camera)
                Vec3d u=axis.multiply(size),v=across.multiply(size*.38);
                m.quad(p.subtract(u),p.subtract(v),p.add(u),p.add(v),PETAL,.62f*a);
                m.line(p.subtract(u),p.add(u),size*.12,PALE,.55f*a);
            }else{
                // catching the light now and then as it turns
                double ph=((age+Ease.hash(seed+9)*.8)%.8)/.8;float spark=(float)Math.max(0,1-Math.abs(ph-.5)/.12);
                m.softDisc(p,size*1.6,AZURE,.14f*a);
                if(spark>.05f)glint8(m,p,size*1.6,WHITE,.65f*a*spark*spark);
            }
        }
    }
    static final int PETAL=0xd8e6ff;

    // ───────────────────────────── on the foe, where she lands, as she flies on ─────────────────────────────

    /** As she cuts the foe (its first blow): a ring of light running out on the ground under it, and a few stars rising off it. */
    static void pass(Fx c,EffectMesh m,float t,float k,Vec3d foe){
        if(!SwordLunge.passes(c.cast.id()))return;
        float at=blowAt(LUNGE,0)/20f;if(t<at-.01f||t>at+.9f)return;
        Vec3d ground=foe.subtract(0,SwordLunge.FOE_MID-.04,0);
        float u=Ease.unit(t,at,at+.4f),a=k*Ease.smooth(t,at-.01f,at+.01f);
        FxLib.ring(m,ground,.4+2.2*Ease.out(t,at,at+.4f),.09,AZURE,.55f*a*(1-u));
        FxLib.ring(m,ground,.3+1.4*Ease.out(t,at,at+.3f),.035,PALE,.5f*a*(1-Ease.unit(t,at,at+.3f)));
        for(int i=0;i<PASS_STARS;i++){
            int seed=i*37+501;float born=at+.04f*(float)Ease.hash(seed),age=t-born,life=.55f+.3f*(float)Ease.hash(seed+1);
            if(age<0||age>=life)continue;
            double az=Math.PI*2*(i+Ease.hash(seed+2))/PASS_STARS,r=.35+.3*Ease.hash(seed+3);
            Vec3d p=foe.add(Math.cos(az)*r*(1+age),(-.3+.6*Ease.hash(seed+4))+1.3*Ease.out(age,0,life),Math.sin(az)*r*(1+age));
            float sa=k*(1-age/life)*Ease.smooth(age,0,.05f)*twinkle(t,seed)*near(p,c.camera);
            glint8(m,p,.09+.05*Ease.hash(seed+5),i%3==0?WHITE:i%3==1?PALE:LILAC,.8f*sa);
        }
    }
    static final int PASS_STARS=6;

    /** As she lands before the foe (LUNGE_LAND): a small ring of light round her feet and moon dust kicked up and settling. */
    static void landing(Fx c,EffectMesh m,float t,float k){
        float at=LUNGE_LAND/20f;if(t<at-.01f||t>at+.6f)return;
        Vec3d g=SwordLunge.past(c.cast).add(0,.04,0);
        float a=k*Ease.smooth(t,at-.01f,at+.01f),u=Ease.unit(t,at,at+.32f);
        FxLib.ring(m,g,.3+1.6*Ease.out(t,at,at+.3f),.07,AZURE,.55f*a*(1-u));
        FxLib.ring(m,g,.2+.9*Ease.out(t,at,at+.2f),.025,WHITE,.4f*a*(1-Ease.unit(t,at,at+.2f)));
        for(int i=0;i<LAND_DUST;i++){
            int seed=i*43+211;double az=Math.PI*2*(i+Ease.hash(seed))/LAND_DUST,out=.6+.9*Ease.hash(seed+1),up=.35+.45*Ease.hash(seed+2);
            float age=t-at;double s=Ease.out(age,0,.5f);
            Vec3d p=g.add(Math.cos(az)*(.25+out*s),up*Math.sin(Math.PI*Math.min(1,age/.55)),Math.sin(az)*(.25+out*s));
            float da=a*(1-Ease.smooth(age,.3f,.55f))*twinkle(t,seed)*near(p,c.camera);if(da<=.01f)continue;
            if(i%2==0)glint8(m,p,.08,i%4==0?WHITE:PALE,.7f*da);else m.softDisc(p,.05,LILAC,.8f*da);
        }
    }
    static final int LAND_DUST=8;

    /** As she flies on to the far sword (LUNGE_HOP, over Z_TRAVEL): stardust left along her way, each mote from as she passes it, drifting down and fading. */
    static void stardust(Fx c,EffectMesh m,float t,float k){
        float hop=LUNGE_HOP/20f,fl=SwordStep.flight();if(t<hop||t>hop+fl+DUST_LIFE)return;
        var run=IsosangClient.lunge(c.cast.id());var form=IsosangClient.formationOf(c.cast.caster());if(run==null||form==null)return;
        var all=SwordFormation.layout(form.id());if(run.to()<0||run.to()>=all.length)return;
        Vec3d a=run.past().add(0,SwordStep.CHEST,0),b=SwordFormation.planted(SwordFormation.centre(form),SwordFormation.yaw(form),all[run.to()]).add(0,SwordStep.CHEST,0);
        for(int i=0;i<FLIGHT_DUST;i++){
            int seed=i*59+307;double u=(i+.2+.6*Ease.hash(seed))/FLIGHT_DUST;
            float born=hop+fl*(float)u,age=t-born;if(age<0||age>=DUST_LIFE)continue;
            Vec3d p=SwordStep.path(a,b,(float)u).add((Ease.hash(seed+1)-.5)*.35,(Ease.hash(seed+2)-.5)*.3-.35*age,(Ease.hash(seed+3)-.5)*.35);
            float da=k*Ease.smooth(age,0,.04f)*(1-age/DUST_LIFE)*twinkle(t,seed)*near(p,c.camera);if(da<=.01f)continue;
            glint8(m,p,.07+.05*Ease.hash(seed+4),i%3==0?WHITE:i%3==1?PALE:LILAC,.75f*da);
        }
    }
    static final int FLIGHT_DUST=14;
    static final float DUST_LIFE=.55f;

    // ───────────────────────────── the three slashes ─────────────────────────────

    /**
     * Where the stars lie in slash j's ink (their shares of the way from its far tip to its near tip): off its ends and
     * off its middle, where it passes before the foe and crosses the others ([.1, .4] and [.6, .9]). While it is bright
     * stars on it are lost in its white (the first preview), so its brightness gets its sparks instead ({@link #slashSparks}).
     */
    static double[] slashStarsU(int j){
        double[] u=new double[SLASH_STARS];
        for(int i=0;i<u.length;i++){double h=Ease.hash(j*131+i*17+5),v=.1+.3*h;u[i]=i%2==0?v:1-v;}
        return u;
    }
    static final int SLASH_STARS=5;
    /** The stars in the slashes' ink (over it): faint, twinkling, from as it turns to ink until it is eaten there. */
    static void slashStars(Fx c,EffectMesh m,float t,float k,Vec3d foe){
        Vec3d to=SwordLunge.toward(c.cast,foe);
        for(int j=0;j<LUNGE_SLASHES;j++){
            float at=lungeSlash(j);if(t<at+SwordLunge.INK0||t>=at+SwordLunge.EAT1||!SwordLunge.cuts(c.cast.id(),j))continue;
            var b=SwordLunge.blade(c.cast.id(),foe,to,j);
            float inked=Ease.smooth(t,at+SwordLunge.INK0,at+SwordLunge.INK1),eaten=Ease.smooth(t,at+SwordLunge.EAT0,at+SwordLunge.EAT1);
            double[] us=slashStarsU(j);
            for(int i=0;i<us.length;i++){
                double u=us[i];if(eaten>u-.02)continue;
                Vec3d p=b.point(u);int seed=j*131+i*17+5;
                float a=.6f*inked*k*twinkle(t,seed)*near(p,c.camera);if(a<=.01f)continue;
                glint8(m,p,.07+.03*Ease.hash(seed+2),i%2==0?PALE:WHITE,.65f*a);
            }
        }
    }

    /**
     * Spark i of slash j: its share of the way from the slash's far tip (off its ends and off its middle, as its stars),
     * the edge it springs off (-1 or 1), and when (seconds after the cut: as the slash's head passes there, the inverse of
     * SwordLunge.drawn's ease).
     */
    static double[] slashSpark(int j,int i){
        int seed=j*257+i*23+41;double h=Ease.hash(seed),u=h<.5?.08+.6*h:.62+.6*(h-.5);
        return new double[]{u,Ease.hash(seed+1)<.5?-1:1,-.01+(SwordLunge.DRAW+.01)*(1-Math.cbrt(1-u))};
    }
    static final int SLASH_SPARKS=18;
    static final float SPARK_LIFE0=.35f,SPARK_LIFE1=.6f;
    /**
     * Where spark {@code s} (slashSpark) of slash {@code b} is {@code age} seconds after it springs off its edge: out across
     * the blade ({@code side}, in the plane facing her camera's side) and on the way the slash was drawn, a little up,
     * slowing quickly, then drifting down.
     */
    static Vec3d sparkAt(SwordLunge.Blade b,Vec3d side,double[] s,int seed,float age){
        Vec3d v=side.multiply(s[1]*(1.4+2.2*Ease.hash(seed+3))).add(b.way().multiply(.5+1.1*Ease.hash(seed+4))).add(0,.5+1.1*Ease.hash(seed+5),0);
        double go=(1-Math.exp(-5*age))/5;
        return b.point(s[0]).add(side.multiply(s[1]*b.width()*.5)).add(v.multiply(go)).add(0,-.7*age*age,0);
    }
    /** Across slash {@code b} in the plane facing her camera's side ({@code toward}, SwordLunge.toward): world-fixed. */
    static Vec3d across(SwordLunge.Blade b,Vec3d toward){
        Vec3d fw=toward.lengthSquared()<1e-8?new Vec3d(1,0,0):toward.normalize(),s=b.way().crossProduct(fw);
        return s.lengthSquared()<1e-8?new Vec3d(0,1,0):s.normalize();
    }
    /**
     * Starry sparks springing off each slash's edges as its head passes, white, pale and lilac, a short streak behind each
     * at first, twinkling, slowing and drifting down, gone in half a second (over the ink, where they show against the
     * night round it; never off its ends nor its middle, so they never gather into one star before the foe).
     */
    static void slashSparks(Fx c,EffectMesh m,float t,float k,Vec3d foe){
        Vec3d to=SwordLunge.toward(c.cast,foe);
        for(int j=0;j<LUNGE_SLASHES;j++){
            float at=lungeSlash(j);if(t<at-.01f||t>=at+SwordLunge.DRAW+SPARK_LIFE1||!SwordLunge.cuts(c.cast.id(),j))continue;
            var b=SwordLunge.blade(c.cast.id(),foe,to,j);Vec3d side=across(b,to);
            for(int i=0;i<SLASH_SPARKS;i++){
                double[] s=slashSpark(j,i);int seed=j*257+i*23+41;
                float born=at+(float)s[2],life=SPARK_LIFE0+(SPARK_LIFE1-SPARK_LIFE0)*(float)Ease.hash(seed+2),age=t-born;if(age<0||age>=life)continue;
                Vec3d p=sparkAt(b,side,s,seed,age);
                float a=k*Ease.smooth(age,0,.02f)*(1-Ease.smooth(age,life*.45f,life))*twinkle(t,seed)*near(p,c.camera);if(a<=.01f)continue;
                int col=i%3==0?WHITE:i%3==1?PALE:LILAC;
                if(age<.12f){
                    Vec3d back=sparkAt(b,side,s,seed,Math.max(0,age-.05f));
                    m.softTaper(back,p,0,.055,col,.55f*a*(1-age/.12f));
                }
                glint8(m,p,.1+.07*Ease.hash(seed+6),col,.9f*a);
            }
        }
    }

    // ───────────────────────────── the six swords ─────────────────────────────

    /** When mote j of sword i's stardust is shed (seconds into T): spread over its flight. */
    static float swordDustBorn(int i,int j){
        float thrown=volleyThrown(LUNGE,i),arrive=volleyArrives(LUNGE,i);
        return thrown+(arrive-thrown)*(float)((j+.15+.7*Ease.hash(i*173+j*31+9))/SWORD_DUST);
    }
    static final int SWORD_DUST=10;
    static final float SWORD_DUST_LIFE=.45f;
    /**
     * Stardust shed by each of T's six swords as it flies to the foe (as comets): each mote left where its blade was as it
     * is shed, twinkling, drifting down a little and fading (over the ink, so its dash's light does not hide it).
     */
    static void swordDust(Fx c,EffectMesh m,float t,float k,Vec3d foe){
        Vec3d from=c.cast.origin();float yaw=c.cast.yaw();
        for(int i=0;i<VOLLEY_SWORDS;i++){
            if(t<volleyThrown(LUNGE,i)||t>=volleyArrives(LUNGE,i)+SWORD_DUST_LIFE)continue;
            for(int j=0;j<SWORD_DUST;j++){
                int seed=i*173+j*31+9;float born=swordDustBorn(i,j),age=t-born;if(age<0||age>=SWORD_DUST_LIFE)continue;
                var at=SwordVolley.pose(LUNGE,from,yaw,foe,born,i);if(at==null||at.phase()!=1)continue;
                Vec3d p=at.tip().subtract(at.dir().multiply(SwordVolley.LEN*(.1+.75*Ease.hash(seed+1))))
                    .add((Ease.hash(seed+2)-.5)*.25,(Ease.hash(seed+3)-.5)*.25-.3*age,(Ease.hash(seed+4)-.5)*.25);
                float a=k*Ease.smooth(age,0,.03f)*(1-Ease.smooth(age,SWORD_DUST_LIFE*.4f,SWORD_DUST_LIFE))*twinkle(t,seed)*near(p,c.camera);if(a<=.01f)continue;
                glint8(m,p,.06+.05*Ease.hash(seed+5),j%3==0?WHITE:j%3==1?PALE:LILAC,.8f*a);
            }
        }
    }

    /** Moon dust sprayed off each slash as it is eaten (from both its tips toward its middle): motes from where it is being eaten, drifting out and up, fading. */
    static void slashDust(Fx c,EffectMesh m,float t,float k,Vec3d foe){
        Vec3d to=SwordLunge.toward(c.cast,foe);
        for(int j=0;j<LUNGE_SLASHES;j++){
            float at=lungeSlash(j);if(t<at+SwordLunge.EAT0-.01f||t>=at+SwordLunge.EAT1+DUST_LIFE||!SwordLunge.cuts(c.cast.id(),j))continue;
            var b=SwordLunge.blade(c.cast.id(),foe,to,j);Vec3d way=b.way();
            Vec3d side=way.crossProduct(new Vec3d(0,1,0));side=side.lengthSquared()<1e-6?new Vec3d(1,0,0):side.normalize();
            for(int i=0;i<SLASH_DUST;i++){
                int seed=j*211+i*19+77;float when=(float)((i+Ease.hash(seed))/SLASH_DUST);
                float born=at+SwordLunge.EAT0+(SwordLunge.EAT1-SwordLunge.EAT0)*when,age=t-born,life=dustLife(born);if(age<0||age>=life)continue;
                // where it was being eaten then (from both its tips toward its middle, as its band in the shader), a little off its line
                double u=eaten(Ease.smooth(born,at+SwordLunge.EAT0,at+SwordLunge.EAT1),i%2==0);
                Vec3d p=b.point(u).add(side.multiply((Ease.hash(seed+1)-.5)*.8*(.3+age))).add(0,.4*age+(Ease.hash(seed+2)-.5)*.2,0);
                float da=k*Ease.smooth(age,0,.04f)*(1-age/life)*twinkle(t,seed)*near(p,c.camera);if(da<=.01f)continue;
                if(i%2==0)glint8(m,p,.07+.03*Ease.hash(seed+3),i%4==0?WHITE:PALE,.7f*da);else m.softDisc(p,.04,LILAC,.8f*da);
            }
        }
    }
    static final int SLASH_DUST=12;
    /**
     * Where a slash is being eaten at {@code erase} (0 to 1; its share of the way from its far tip, on its far half or its
     * near half): as its band in the shader (kageno_flame style 7), eaten from both tips toward its middle, its front at
     * erase*1.08-.04 of the way from either tip to the middle.
     */
    static double eaten(float erase,boolean farHalf){double x=Math.max(0,Math.min(.5,(erase*1.08-.04)*.5));return farHalf?x:1-x;}
    /** How long a mote of the slashes' dust shed at {@code born} lasts: DUST_LIFE, shorter for the last ones so all of it is gone before T stops being drawn (SwordLunge.LIFE). */
    static float dustLife(float born){return Math.max(.05f,Math.min(DUST_LIFE,SwordLunge.LIFE-.05f-born));}

    // ───────────────────────────── light on the world ─────────────────────────────

    /** Small lights as she lands and under the foe as she cuts it. */
    static void frame(Fx c,FxFrame f){
        float t=c.sec,k=SwordLunge.kept(c.cast.id(),t);if(k<=.01f)return;
        float land=LUNGE_LAND/20f,kl=FxLib.kick(t,land,.2f);
        if(kl>.01f)f.light(SwordLunge.past(c.cast).add(0,.5,0),0xa8c8ff,5,.45f*kl*k);
    }
}
