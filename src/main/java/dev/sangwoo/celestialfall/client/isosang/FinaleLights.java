package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.*;
import static dev.sangwoo.celestialfall.isosang.IsosangRules.*;

/**
 * H again's moonlight and starlight (2.101.2, user request: "잉크가 더 많이 튀게 바꿔주고 이제 엄청 멋있고 예쁘게 바꿔줘"),
 * as her sword's (월흔, the moon's trace), the formation's (FormationLights, FormationSky) and T's (LungeLights):
 * <ul>
 * <li>as it flashes in: stars riding the ring out round her feet and motes of light rising round her
 * ({@link #opening});</li>
 * <li>her sword pulled up under her: a white crystal flower blooming where it stood (Z's, SwordStep.bloom), a gleam
 * running up it twice as it rises, and as she comes down onto it a small ring under her feet, a flash and sparks thrown
 * off behind her ({@link #mount}, {@link #flowers});</li>
 * <li>round her lap: stardust shed along her way ({@link #lapDust}), crystal petals shed off her sword, drifting down
 * and tumbling, catching the light now and then ({@link #petals}), and a star riding its point as a comet's head
 * ({@link #comet});</li>
 * <li>up there: moon dust sprayed off her swing's arc ({@link #swingDust}), stars riding the white ring out
 * ({@link #ringStars}), motes drawn in to her two fingers ({@link #seal}); the fan behind her a starry halo: a star at
 * every pommel, its two inner rings laced together as a constellation, a shimmer running out along every blade from the
 * top down each side ({@link #fan}); as each shoots off, a small star where it shot from ({@link #launch}), and
 * stardust shed along each sword's flight as a comet ({@link #flightDust});</li>
 * <li>as each strikes: every third a pillar of light ({@link #pillars}), starry sparks springing up out of its crystal
 * ({@link #sparks}), and a gleam running down it as it settles and now and then while it stands ({@link #gleams});</li>
 * <li>as the band bursts: a net of starlight drawn from the band's middle out between the hilts ({@link #net});</li>
 * <li>where she comes down: a white crystal flower and moon dust kicked up ({@link #landing});</li>
 * <li>as each goes: a ring at its foot and a little crescent moon rising off its hilt ({@link #vanish}).</li>
 * </ul>
 * In her cutscene a crescent moon stands in the dusk behind her (SwordFinale.ground). The light stays blue, lilac and
 * pale, white only in small hearts: nothing washes the frame white (2.101.1's previews), nothing is added on her face.
 */
final class FinaleLights {
    private FinaleLights(){}

    /** The blended pass: the crystal petals off her sword, the flowers where her sword stood and where she comes down. */
    static void alpha(Fx c,EffectMesh m){
        float t=c.sec;var plans=SwordFinale.plans(c.cast);if(plans==null)return;var fr=SwordFinale.frame(c.cast);
        petals(c,m,fr,t,false);
        flowers(c,m,fr,plans,t,false);
    }
    /** The light pass (under the ink). */
    static void additive(Fx c,EffectMesh m){
        float t=c.sec;var plans=SwordFinale.plans(c.cast);if(plans==null)return;var fr=SwordFinale.frame(c.cast);
        opening(c,m,fr,t);
        mount(c,m,fr,plans,t);
        flowers(c,m,fr,plans,t,true);
        petals(c,m,fr,t,true);
        lapDust(c,m,fr,t);
        comet(c,m,fr,t);
        swingDust(c,m,fr,t);
        ringStars(c,m,fr,t);
        seal(c,m,fr,t);
        fan(c,m,fr,plans,t);
        launch(c,m,fr,plans,t);
        speedLines(c,m,plans,t);
        pillars(c,m,plans,t);
        gleams(c,m,plans,t);
        landing(c,m,fr,t);
        vanish(c,m,plans,t);
    }
    /** Over the ink (its strokes and splashes drawn after the light pass): the stardust of the flying swords, the sparks, the net. */
    static void overlay(Fx c,EffectMesh m){
        float t=c.sec;var plans=SwordFinale.plans(c.cast);if(plans==null)return;var fr=SwordFinale.frame(c.cast);
        flightDust(c,m,fr,plans,t);
        sparks(c,m,plans,t);
        net(c,m,fr,plans,t);
    }
    /** The light they cast: as she comes down onto her sword, each pillar, the band moonlit while its swords stand. */
    static void frame(Fx c,FxFrame f){
        float t=c.sec;var plans=SwordFinale.plans(c.cast);if(plans==null)return;var fr=SwordFinale.frame(c.cast);
        float k=FxLib.kick(t,FINALE_RIDE,.15f);if(k>.01f)f.light(SwordFinale.body(fr,FINALE_RIDE),0x9fc8ff,7,.7f*k);
        for(var p:plans){
            if(!pillar(p)||!SwordFinale.comes(c.cast,p.plant()))continue;
            float s=FxLib.kick(t,p.plant(),.2f);if(s>.05f)f.light(p.foot().add(0,3,0),0xa8d8ff,10,.7f*s);
        }
        float stand=Ease.window(t,FINALE_BURST,FINALE_GONE1,.2f,.4f);
        if(stand>.01f&&SwordFinale.comes(c.cast,FINALE_BURST))f.light(fr.at((SwordFinale.BAND_NEAR+SwordFinale.BAND_FAR)/2,2.5,0),0x9ab8ff,14,.35f*stand);
    }

    static SwordFinale.Plan steed(SwordFinale.Plan[] plans){for(var p:plans)if(p.steed())return p;return null;}
    /** Where a spark thrown along {@code dir} is {@code s} seconds on (it falls). */
    static Vec3d spark(Vec3d o,Vec3d dir,double out,double up,double s){return o.add(dir.multiply(out*s)).add(0,up*s-6*s*s,0);}

    // ───────────────────────────── as it flashes in, as her sword comes up under her ─────────────────────────────

    /** As it flashes in (FINALE_FLASH): stars riding its ring out round her feet on the pommel, motes of light rising round her. */
    static void opening(Fx c,EffectMesh m,SwordFinale.Frame fr,float t){
        float at=FINALE_FLASH;if(t<at||t>at+.8f)return;
        Vec3d feet=fr.spot().add(0,fr.pommel()+.05,0);
        for(int i=0;i<OPEN_STARS;i++){
            int seed=i*61+1201;double az=Math.PI*2*(i+.4*Ease.hash(seed))/OPEN_STARS;
            float age=t-at-.015f*(float)Ease.hash(seed+1);if(age<0)continue;
            // (on SwordFinale's ring: .3 out to 3.5 blocks over 0.35 s)
            double r=.3+3.2*Ease.out(age,0,.35f)*(.9+.2*Ease.hash(seed+2));
            Vec3d p=feet.add(Math.cos(az)*r,.08+.5*Ease.hash(seed+3)*Ease.out(age,0,.6f),Math.sin(az)*r);
            float a=Ease.smooth(age,0,.02f)*(1-Ease.smooth(age,.25f,.55f))*twinkle(t,seed)*near(p,c.camera);
            glint8(m,p,.1+.06*Ease.hash(seed+4),i%3==0?WHITE:i%3==1?PALE:LILAC,.8f*a);
        }
        for(int i=0;i<RISE_MOTES;i++){
            int seed=i*47+1301;float born=at+.12f*(float)Ease.hash(seed),age=t-born,life=.5f+.25f*(float)Ease.hash(seed+1);
            if(age<0||age>=life)continue;
            double az=Math.PI*2*Ease.hash(seed+2)+1.6*age,r=.35+.55*Ease.hash(seed+3);
            Vec3d p=feet.add(Math.cos(az)*r,.1+2.2*Ease.out(age,0,life),Math.sin(az)*r);
            float a=Ease.smooth(age,0,.05f)*(1-age/life)*twinkle(t,seed)*near(p,c.camera);if(a<=.01f)continue;
            if(i%2==0)glint8(m,p,.07,i%4==0?WHITE:PALE,.7f*a);else m.softDisc(p,.05,LILAC,.75f*a);
        }
    }
    static final int OPEN_STARS=12,RISE_MOTES=14;

    /**
     * Her sword pulled up under her: a gleam running up it twice from its pommel to its point as it rises; as she comes
     * down onto it (FINALE_RIDE), a small ring of light under her feet going with her, a flash, and sparks thrown off
     * behind her and out to either side, falling.
     */
    static void mount(Fx c,EffectMesh m,SwordFinale.Frame fr,SwordFinale.Plan[] plans,float t){
        var p=steed(plans);if(p==null)return;
        if(t>=p.start()&&t<FINALE_RIDE+.1f){
            var b=SwordFinale.blade(fr,p,t);
            if(b!=null)for(int g=0;g<2;g++){
                float g0=p.start()+.04f+.11f*g,u=Ease.unit(t,g0,g0+MOUNT_GLEAM);if(u<=0||u>=1)continue;
                Vec3d head=b.pommel().lerp(b.point(),u),tail=b.pommel().lerp(b.point(),Math.max(0,u-.32));
                float a=(float)Math.sin(Math.PI*u)*SwordFinale.clearOf(b,c.camera);
                m.softTaper(tail,head,.02,.12,PALE,.55f*a);m.softDisc(head,.2,WHITE,.4f*a);
            }
        }
        float at=FINALE_RIDE;if(t<at-.01f||t>=at+.6f)return;
        Vec3d feet=SwordFinale.body(fr,at).add(0,-.02,0),h=SwordFinale.heading(fr,at),side=new Vec3d(-h.z,0,h.x);
        float a=Ease.smooth(t,at-.01f,at+.01f),u=Ease.unit(t,at,at+.22f);
        // a small ring under her feet, going with her (2.101.2's first previews: a ring left where she came down onto
        // it hung in the sky behind her as she rode off, a great grey oval to the low camera close under her)
        if(u<1){Vec3d now=SwordFinale.body(fr,t).add(0,-.02,0);FxLib.ring(m,now,.2+.9*Ease.out(t,at,at+.16f),.06,AZURE,.55f*a*(1-u));}
        float k=FxLib.kick(t,at,.1f);
        if(k>.01f){Vec3d o=SwordFinale.toward(feet,c.camera,.2);m.softDisc(o,.9,BLUE,.3f*k);glint8(m,o,.4*k,WHITE,.7f*k*near(o,c.camera));}
        float s=t-at;
        if(s>=0&&s<.45f)for(int j=0;j<MOUNT_SPARKS;j++){
            int seed=j*29+1501;double az=Math.PI*(.55+.9*Ease.hash(seed))*(j%2==0?1:-1);
            Vec3d dir=h.multiply(Math.cos(az)).add(side.multiply(Math.sin(az)));
            double out=2+3*Ease.hash(seed+1),up=1.5+2.5*Ease.hash(seed+2);
            Vec3d q=spark(feet,dir,out,up,s),q0=spark(feet,dir,out,up,Math.max(0,s-.03));
            m.needle(q0,q,.04,.4,j%3==0?WHITE:j%3==1?PALE:AZURE,.8f*a*(1-Ease.smooth(s,.22f,.45f))*near(q,c.camera));
        }
    }
    static final float MOUNT_GLEAM=.18f;
    static final int MOUNT_SPARKS=12;

    /** The white crystal flowers (Z's, SwordStep.bloom): where the sword she stood on stood as it is pulled up, and where she comes down onto the ground. */
    static void flowers(Fx c,EffectMesh m,SwordFinale.Frame fr,SwordFinale.Plan[] plans,float t,boolean light){
        var p=steed(plans);
        if(p!=null&&t>=p.start()-.01f&&t<p.start()+SwordStep.DEPART_FADE1+.02f){
            Vec3d g=p.planted().add(p.down().multiply(SwordFormation.S_LEN));
            SwordStep.bloom(m,new Vec3d(g.x,fr.spot().y+.03,g.z),p.seed()+11,SwordStep.departs(t-p.start()),light,t);
        }
        // (opening as she comes down onto it: Z's flower at the sword she lands on)
        float at=FINALE_LAND-SwordStep.ARRIVE_OPEN0;
        if(t>=at&&t<at+SwordStep.ARRIVE_FADE1+.02f&&SwordFinale.comes(c.cast,FINALE_LAND))
            SwordStep.bloom(m,fr.spot().add(0,.03,0),c.cast.id().hashCode()*7+5,SwordStep.arrives(t-at),light,t);
    }

    // ───────────────────────────── round her lap ─────────────────────────────

    /** When petal {@code i} is shed off her sword (seconds: along her ride round the lap). */
    static float petalBorn(int i){return FINALE_RIDE+.04f+(FINALE_LAP1-FINALE_RIDE-.08f)*(i+(float)Ease.hash(i*97+1441))/PETALS;}
    /** The crystal petals shed off her sword as she rides round (the blended pass: pale crystal leaves, tumbling; the light pass: their glints now and then). */
    static void petals(Fx c,EffectMesh m,SwordFinale.Frame fr,float t,boolean light){
        if(t<FINALE_RIDE||t>FINALE_LAP1+PETAL_LIFE1)return;
        for(int i=0;i<PETALS;i++){
            int seed=i*97+1441;float born=petalBorn(i),life=PETAL_LIFE0+(PETAL_LIFE1-PETAL_LIFE0)*(float)Ease.hash(seed+9),age=t-born;
            if(age<0||age>=life)continue;
            Vec3d p0=SwordFinale.rideMid(fr,born).add((Ease.hash(seed+1)-.5)*1.1,(Ease.hash(seed+2)-.5)*.4,(Ease.hash(seed+3)-.5)*1.1);
            Vec3d drift=new Vec3d(Ease.hash(seed+4)-.5,0,Ease.hash(seed+5)-.5).multiply(.7);
            double sway=.14*Math.sin(age*(3+2*Ease.hash(seed+6))+seed),fall=PETAL_FALL*(.7+.6*Ease.hash(seed+7));
            Vec3d p=p0.add(drift.multiply(age)).add(sway,-fall*age,.6*sway);
            float a=Ease.smooth(age,0,.08f)*(1-Ease.smooth(age,life*.6f,life))*near(p,c.camera);if(a<=.01f)continue;
            double size=.11+.08*Ease.hash(seed+8),turn=age*(4+5*Ease.hash(seed+10))+seed;
            Vec3d axis=new Vec3d(Math.cos(turn),.55*Math.sin(turn*.7),Math.sin(turn)).normalize();
            Vec3d across=axis.crossProduct(new Vec3d(Math.sin(turn*.5),.8,Math.cos(turn*.5)));
            if(across.lengthSquared()<1e-6)continue;across=across.normalize();
            if(!light){
                // a pointed crystal leaf, fixed in the world and turning (never facing the camera)
                Vec3d u=axis.multiply(size),v=across.multiply(size*.38);
                m.quad(p.subtract(u),p.subtract(v),p.add(u),p.add(v),PETAL,.62f*a);
                m.line(p.subtract(u),p.add(u),size*.12,PALE,.55f*a);
            }else{
                double ph=((age+Ease.hash(seed+11)*.8)%.8)/.8;float spark=(float)Math.max(0,1-Math.abs(ph-.5)/.12);
                m.softDisc(p,size*1.6,AZURE,.14f*a);
                if(spark>.05f)glint8(m,p,size*1.6,WHITE,.65f*a*spark*spark);
            }
        }
    }
    static final int PETALS=20,PETAL=0xd8e6ff;
    static final double PETAL_FALL=.5;
    static final float PETAL_LIFE0=1f,PETAL_LIFE1=1.4f;

    /** Stardust shed along her way round the lap (each mote from as she passes it), drifting down and fading. */
    static void lapDust(Fx c,EffectMesh m,SwordFinale.Frame fr,float t){
        if(t<FINALE_RIDE||t>FINALE_LAP1+LAP_DUST_LIFE)return;
        for(int i=0;i<LAP_DUST;i++){
            int seed=i*59+1601;float born=FINALE_RIDE+(FINALE_LAP1-FINALE_RIDE)*(i+(float)Ease.hash(seed))/LAP_DUST;
            float life=LAP_DUST_LIFE*(.7f+.3f*(float)Ease.hash(seed+1)),age=t-born;if(age<0||age>=life)continue;
            Vec3d p=SwordFinale.body(fr,born).add((Ease.hash(seed+2)-.5)*.6,(Ease.hash(seed+3)-.5)*.4-.4*age,(Ease.hash(seed+4)-.5)*.6);
            float a=Ease.smooth(age,0,.04f)*(1-age/life)*twinkle(t,seed)*near(p,c.camera);if(a<=.01f)continue;
            glint8(m,p,.07+.06*Ease.hash(seed+5),i%4==0?WHITE:i%4==1?PALE:i%4==2?LILAC:CYAN,.8f*a);
        }
    }
    static final int LAP_DUST=44;
    static final float LAP_DUST_LIFE=.8f;

    /** A star riding her sword's point as she rides it round (a comet's head), a short tail of light behind it. */
    static void comet(Fx c,EffectMesh m,SwordFinale.Frame fr,float t){
        float a=Ease.window(t,FINALE_RIDE,FINALE_LAP1+.05f,.06f,.12f);if(a<=.01f)return;
        Vec3d tip=cometAt(fr,t);a*=near(tip,c.camera);if(a<=.01f)return;
        float pulse=.85f+.15f*(float)Math.sin(t*23);
        m.softDisc(tip,.5,AZURE,.28f*a);glint8(m,tip,.22*pulse,WHITE,.8f*a);
        Vec3d back=cometAt(fr,Math.max(FINALE_RIDE,t-.06f));
        if(back.squaredDistanceTo(tip)>.01)m.softTaper(back,tip,0,.12,PALE,.35f*a);
    }
    static Vec3d cometAt(SwordFinale.Frame fr,float t){return SwordFinale.rideMid(fr,t).add(SwordFinale.rideDir(fr,t).multiply(SwordFormation.S_LEN/2+.05));}

    // ───────────────────────────── up there ─────────────────────────────

    /** Moon dust sprayed off her swing's arc as it fades (from where her point went), drifting out and settling, twinkling. */
    static void swingDust(Fx c,EffectMesh m,SwordFinale.Frame fr,float t){
        if(t<FINALE_SWING0||t>FINALE_SWING1+.9f)return;
        for(int i=0;i<SWING_DUST;i++){
            int seed=i*71+1701;double f=(i+.5+.5*(Ease.hash(seed)-.5))/SWING_DUST;
            float at=FINALE_SWING0+(FINALE_SWING1-FINALE_SWING0)*(float)f,born=at+.06f+.08f*(float)Ease.hash(seed+1);
            float life=.5f+.3f*(float)Ease.hash(seed+2),age=t-born;if(age<0||age>=life)continue;
            Vec3d[] b=SwordFinale.sword(fr,at);Vec3d tip=b[0].add(b[1].multiply(TIP+.3));
            Vec3d p=tip.add(b[1].multiply(.4*Ease.out(age,0,life))).add((Ease.hash(seed+3)-.5)*.5*age,.25*Math.sin(Math.PI*Math.min(1,age/life))-.3*age*age,(Ease.hash(seed+4)-.5)*.5*age);
            float a=Ease.smooth(age,0,.05f)*(1-Ease.smooth(age,life*.5f,life))*twinkle(t,seed)*near(p,c.camera);if(a<=.01f)continue;
            if(i%2==0)glint8(m,p,.08+.04*Ease.hash(seed+5),i%4==0?WHITE:PALE,.75f*a);else m.softDisc(p,.05,LILAC,.8f*a);
        }
    }
    static final int SWING_DUST=16;

    /** Stars riding the white ring out round her up there (FINALE_RING; SwordFinale.ringBurst's ring), turning a little as they go. */
    static void ringStars(Fx c,EffectMesh m,SwordFinale.Frame fr,float t){
        float at=FINALE_RING;if(t<at||t>=at+.5f)return;
        Vec3d o=SwordFinale.body(fr,t).add(0,1.15,0);Vec3d[] uv=cameraPlane(m,o);if(uv==null)return;
        double r=(.6+2.6*Ease.out(t,at,at+.24f))*1.02+.6*Ease.unit(t,at,at+.5f);
        float a=Ease.smooth(t,at,at+.02f)*(1-Ease.smooth(t,at+.15f,at+.5f));
        for(int i=0;i<RING_STARS;i++){
            int seed=i*43+1801;double ang=Math.PI*2*(i+.3*Ease.hash(seed))/RING_STARS+.5*(t-at);
            Vec3d p=o.add(uv[0].multiply(Math.cos(ang)*r)).add(uv[1].multiply(Math.sin(ang)*r));
            glint8(m,p,.1+.05*Ease.hash(seed+1),i%3==0?WHITE:i%3==1?PALE:LILAC,.85f*a*twinkle(t,seed)*near(p,c.camera));
        }
    }
    static final int RING_STARS=12;

    /** Motes of light drawn in to her two raised fingers while she holds them before her face (small: nothing on her face). */
    static void seal(Fx c,EffectMesh m,SwordFinale.Frame fr,float t){
        float k=Ease.window(t,FINALE_SEAL0,FINALE_SEAL1+.12f,.08f,.12f);if(k<=.01f)return;
        var f=GGesture.FINALE_TIP;Vec3d tip=SwordFinale.onBody(fr,t,-f.x/16,(24-f.y)/16,-f.z/16);
        for(int i=0;i<SEAL_MOTES;i++){
            int seed=i*53+1901;float born=FINALE_SEAL0+(FINALE_SEAL1-FINALE_SEAL0-.25f)*(i+(float)Ease.hash(seed))/SEAL_MOTES,u=Ease.unit(t,born,born+.25f);
            if(t<born||u>=1)continue;
            double az=Math.PI*2*Ease.hash(seed+1),el=(Ease.hash(seed+2)-.5)*1.6,r0=.7+.6*Ease.hash(seed+3);
            Vec3d dir=new Vec3d(Math.cos(el)*Math.cos(az),Math.sin(el),Math.cos(el)*Math.sin(az));
            Vec3d p=tip.add(dir.multiply(.05+(r0-.05)*(1-Math.pow(u,1.4))));
            float a=k*Ease.smooth(u,0,.15f)*(1-Ease.smooth(u,.85f,1))*near(p,c.camera);if(a<=.01f)continue;
            m.softDisc(p,.035,i%3==0?WHITE:i%3==1?PALE:LILAC,.85f*a);
        }
    }
    static final int SEAL_MOTES=14;

    /** When a sword is spread in the fan, and its pommel and point there. */
    static float fanned(SwordFinale.Plan p){return p.gather()+SwordFinale.GATHER;}
    static Vec3d fanPommel(SwordFinale.Plan p,float t){return SwordFinale.fanAt(p,t).subtract(p.fanDir().multiply(SwordFormation.S_LEN/2));}
    static Vec3d fanPoint(SwordFinale.Plan p,float t){return SwordFinale.fanAt(p,t).add(p.fanDir().multiply(SwordFormation.S_LEN/2));}
    /** The swords of the fan's two inner rings round her head and shoulders, by their angle round it (laced together: a halo). */
    static int[] halo(SwordFinale.Plan[] plans){
        List<Integer> l=new ArrayList<>();
        for(var p:plans)if(p.own()&&!p.steed()&&SwordFinale.ring(SwordFinale.slot(p.k()))<2)l.add(p.k());
        l.sort(Comparator.comparingDouble(k->SwordFinale.slotAngle(SwordFinale.slot(k))));
        return l.stream().mapToInt(Integer::intValue).toArray();
    }
    /**
     * The fan a starry halo behind her: a star at every pommel twinkling, the two inner rings' pommels laced together by
     * threads of light drawn on as they come into the fan, and a shimmer running out along every blade from its pommel to
     * its point, the top first and on down each side ({@link #FAN_SHIMMER}, {@link #FAN_SPREAD}); all gone as they fly.
     */
    static void fan(Fx c,EffectMesh m,SwordFinale.Frame fr,SwordFinale.Plan[] plans,float t){
        if(t<SwordFinale.GATHER0||t>FINALE_LAUNCH+.3f)return;
        int[] lace=halo(plans);
        for(int i=0;i+1<lace.length;i++){
            var a=plans[lace[i]];var b=plans[lace[i+1]];
            float on=Math.max(fanned(a),fanned(b))+.06f,off=SwordFinale.FOLD0;
            float drawn=Ease.out(t,on,on+.14f),gone=1-Ease.smooth(t,off-.06f,off+.01f);if(drawn<=0||gone<=0)continue;
            Vec3d p=fanPommel(a,t),q=fanPommel(b,t),e=p.lerp(q,drawn);
            float al=gone*Ease.smooth(t,on,on+.04f)*near(p.lerp(q,.5),c.camera);
            m.line(p,e,.02,i%2==0?PALE:LILAC,.5f*al);m.softLine(p,e,.12,AZURE,.12f*al);
        }
        for(var p:plans){
            if(p.steed()||!p.own())continue;
            float on=fanned(p);if(t<on||t>=SwordFinale.FOLD0+.02f)continue;
            // (2.101.3: gone as the fan folds to wait aimed through her)
            float a=Ease.smooth(t,on,on+.08f)*(1-Ease.smooth(t,SwordFinale.FOLD0-.04f,SwordFinale.FOLD0+.02f));
            Vec3d pommel=fanPommel(p,t),point=fanPoint(p,t);
            glint8(m,pommel,.09,(p.seed()&1)==0?WHITE:PALE,.7f*a*twinkle(t,p.seed())*near(pommel,c.camera));
            float s0=shimmer(p),u=Ease.unit(t,s0,s0+SHIMMER);
            if(u>0&&u<1){
                Vec3d head=pommel.lerp(point,u),tail=pommel.lerp(point,Math.max(0,u-.35));float g=a*(float)Math.sin(Math.PI*u)*near(head,c.camera);
                m.softTaper(tail,head,.01,.08,PALE,.5f*g);m.softDisc(head,.14,WHITE,.35f*g);
            }
        }
    }
    static final float FAN_SHIMMER=1.78f,FAN_SPREAD=.16f,SHIMMER=.2f;
    /** When the shimmer starts running out along sword {@code p} in the fan: the top first and on down each side, never before it is spread there, done before the fan folds. */
    static float shimmer(SwordFinale.Plan p){
        double phi=Math.abs(Math.toDegrees(SwordFinale.slotAngle(SwordFinale.slot(p.k()))));
        return Math.min(Math.max(FAN_SHIMMER+FAN_SPREAD*(float)(phi/84),fanned(p)+.04f),SwordFinale.FOLD0-SHIMMER);
    }

    /**
     * As each sword shoots off (2.101.3: one after another, each just before it strikes): a small star where its point shot
     * from and a soft blue light, swelling a little as it goes (2.101.2's stars flung out of the fan's hub at FINALE_LAUNCH went
     * with them all at once; 2.101.2's first previews had a ring there too, a thin grey oval round her from behind). Since
     * 2.101.10 they wait behind her camera, so this is seen by those watching her from elsewhere; her own camera sees each
     * as it comes in past it (SwordFinale.seen, its streak and the fading copies after it, the speed lines).
     */
    static void launch(Fx c,EffectMesh m,SwordFinale.Frame fr,SwordFinale.Plan[] plans,float t){
        for(var p:plans){
            float s=t-p.launch();if(s<-.02f||s>=RELEASE||!SwordFinale.comes(c.cast,p.plant()))continue;
            Vec3d o=SwordFinale.tipFrom(fr,p),d=p.aimDir();float n=near(o,c.camera);
            float k=Ease.smooth(s,-.02f,0)*(1-Ease.smooth(s,.03f,RELEASE))*n;
            if(k>.01f){m.softDisc(o,.8,AZURE,.32f*k);glint8(m,o,.22*(1+3*s),(p.seed()&1)==0?WHITE:PALE,.8f*k);}
            // (2.101.9, user request: "연사를 하고 있다는 이펙트를 추가해봐 속도감이 느껴지게"; 2.101.10: "연사하는 이펙트를 더
            // 강화해봐 연사한다는 느낌이 들게") the shot: a sharp flash, two rings of air thrown out round it square to the way
            // it goes (a quick bright one and a wider late one), a cone of light shot on ahead of it, sparks
            float f=FxLib.kick(t,p.launch(),.06f)*n;
            if(f>.01f){m.softDisc(o,.75,BLUE,.45f*f);glint8(m,o,.34*f,WHITE,.95f*f);}
            Vec3d a=SwordFormation.perp(d,0),b=d.crossProduct(a).normalize();
            for(int r=0;r<2;r++){
                float dur=SHOT_RING*(r==0?1:1.6f),u=Ease.unit(s,0,dur);if(u<=0||u>=1)continue;
                double e=Ease.out(s,0,dur);
                FxLib.ringPlane(m,o.add(d.multiply(r==0?.2+1.4*e:.05+.6*e)),a,b,r==0?.15+.9*e:.25+1.4*e,(r==0?.035:.05)*(1-u)+.008,r==0?PALE:AZURE,(r==0?.75f:.45f)*(1-u)*n);
            }
            for(int i=0;i<SHOT_CONE;i++){
                int seed=p.seed()*41+i*13+911;double life=.07;if(s<0||s>=life)continue;
                Vec3d w=d.add(SwordFormation.perp(d,Math.PI*2*Ease.hash(seed)).multiply(.06)).normalize();
                Vec3d head=o.add(w.multiply(60*s+.3)),tail=o.add(w.multiply(Math.max(0,60*s-2.2)));
                float al=(1-(float)(s/life))*near(head,c.camera);if(al>.01f)m.needle(tail,head,.03,.5,WHITE,.8f*al);
            }
            for(int i=0;i<SHOT_SPARKS;i++){
                int seed=p.seed()*29+i*17+2301;double life=.07+.06*Ease.hash(seed);if(s<0||s>=life)continue;
                Vec3d w=d.add(SwordFormation.perp(d,Math.PI*2*Ease.hash(seed+1)).multiply(.3*Ease.hash(seed+2))).normalize();
                double speed=30+30*Ease.hash(seed+3);Vec3d head=o.add(w.multiply(speed*s)),tail=o.add(w.multiply(speed*Math.max(0,s-.03)));
                float al=(1-(float)(s/life))*near(head,c.camera);if(al<=.01f)continue;
                m.needle(tail,head,.04,.4,i%2==0?WHITE:PALE,.85f*al);
            }
        }
    }
    static final float RELEASE=.24f,SHOT_RING=.12f;
    static final int SHOT_SPARKS=9,SHOT_CONE=3;

    /**
     * Lines of speed as they are fired (2.101.9, user request: "연사를 하고 있다는 이펙트를 추가해봐 속도감이 느껴지게"): thin
     * streaks of light racing on from where they wait the way the swords there point (2.101.10: from behind her camera,
     * straight ahead, in past it), one after another as long as they are fired ({@link #SPEED_LINES} over the rain, each
     * from beside one of the swords there, a little off its line, {@link #SPEED_LEN} long, shooting on at {@link #SPEED}
     * blocks a second for {@link #SPEED_LIFE}), white at their heads and blue behind; to her own cutscene's camera none of
     * them behind its screen (SwordFinale.clipped).
     */
    static void speedLines(Fx c,EffectMesh m,SwordFinale.Plan[] plans,float t){
        var fr=SwordFinale.frame(c.cast);boolean hers=SwordFinale.herCamera(c);
        float t0=SwordFinale.FIRE0-.03f,t1=t0;for(var p:plans)if(!p.steed())t1=Math.max(t1,p.launch());
        if(t<t0||t>t1+SPEED_LIFE*1.3f||!SwordFinale.comes(c.cast,t0))return;
        for(int i=0;i<SPEED_LINES;i++){
            int seed=i*61+3301;float born=t0+(t1-t0)*(i+(float)Ease.hash(seed))/SPEED_LINES,life=SPEED_LIFE*(.7f+.6f*(float)Ease.hash(seed+1)),age=t-born;
            if(age<0||age>=life)continue;
            var p=plans[(int)(Ease.hash(seed+2)*plans.length)%plans.length];if(p.steed())p=plans[(p.k()+1)%plans.length];
            Vec3d d=p.aimDir(),o=p.readyMid().add(SwordFormation.perp(d,Math.PI*2*Ease.hash(seed+3)).multiply(.3+.9*Ease.hash(seed+4))).add(d.multiply(-.6+1.2*Ease.hash(seed+5)));
            double speed=SPEED*(.8+.4*Ease.hash(seed+6)),len=SPEED_LEN*(.6+.8*Ease.hash(seed+7));
            Vec3d head=o.add(d.multiply(speed*age)),tail=head.subtract(d.multiply(len));
            if(hers){Vec3d[] cl=SwordFinale.clipped(fr,tail,head,t);if(cl==null)continue;tail=cl[0];head=cl[1];}
            float a=Ease.smooth(age,0,.015f)*(1-Ease.smooth(age,life*.45f,life))*near(head,c.camera);if(a<=.01f)continue;
            m.softTaper(tail,head,0,.06,BLUE,.4f*a);m.line(tail.lerp(head,.45),head,.014,i%3==0?WHITE:PALE,.85f*a);
        }
    }
    static final int SPEED_LINES=360;
    static final double SPEED=120,SPEED_LEN=4.5;
    static final float SPEED_LIFE=.1f;

    /** Stardust shed along each sword's flight into the band, as a comet's (each mote from where the sword passed), drifting down, twinkling. */
    static void flightDust(Fx c,EffectMesh m,SwordFinale.Frame fr,SwordFinale.Plan[] plans,float t){
        for(var p:plans){
            if(t<p.launch()||t>p.plant()+FLIGHT_DUST_LIFE)continue;
            for(int i=0;i<FLIGHT_DUST;i++){
                int seed=p.seed()*13+i*31+2001;
                float born=p.launch()+(p.plant()-p.launch())*(i+.5f+.5f*((float)Ease.hash(seed)-.5f))/FLIGHT_DUST,age=t-born,life=FLIGHT_DUST_LIFE*(.7f+.3f*(float)Ease.hash(seed+1));
                if(age<0||age>=life||!SwordFinale.comes(c.cast,born))continue;
                Vec3d q=SwordFinale.mid(fr,p,born);if(q==null)continue;
                double sc=SwordFinale.scale(p,born);
                Vec3d pt=q.add((Ease.hash(seed+2)-.5)*.5*sc,(Ease.hash(seed+3)-.5)*.4*sc-.45*age,(Ease.hash(seed+4)-.5)*.5*sc);
                float a=Ease.smooth(age,0,.04f)*(1-age/life)*twinkle(t,seed)*near(pt,c.camera);if(a<=.01f)continue;
                glint8(m,pt,.08+.07*Ease.hash(seed+5),i%3==0?WHITE:i%3==1?PALE:LILAC,.75f*a);
            }
        }
    }
    static final int FLIGHT_DUST=8;
    static final float FLIGHT_DUST_LIFE=.5f;

    // ───────────────────────────── as each strikes, as the band bursts ─────────────────────────────

    /** Whether sword {@code p} shoots a pillar of light up as it strikes: none (2.101.9, the user's video: "이거처럼": its swords strike in bursts of blue crystal, no pillars; 2.101.2 to 2.101.8 had one every third or so). */
    static boolean pillar(SwordFinale.Plan p){return false;}
    /** The pillar of light up out of its foot as it strikes (a moment, PILLAR blocks high, blue round a pale heart). */
    static void pillars(Fx c,EffectMesh m,SwordFinale.Plan[] plans,float t){
        for(var p:plans){
            if(!pillar(p)||!SwordFinale.comes(c.cast,p.plant()))continue;
            float s=Ease.smooth(t,p.plant(),p.plant()+.02f)*(1-Ease.smooth(t,p.plant()+.12f,p.plant()+.5f));if(s<=.01f)continue;
            Vec3d g=p.foot(),up=g.add(0,PILLAR,0);
            m.line(g,up,.06,PALE,.55f*s);m.softLine(g,up,.8,CYAN,.22f*s);m.softLine(g,g.add(0,PILLAR*.55,0),1.8,BLUE,.12f*s);
        }
    }
    static final double PILLAR=16;

    /** Starry sparks springing up out of each one's crystal as it strikes, streaks at first, then little stars falling and fading. */
    static void sparks(Fx c,EffectMesh m,SwordFinale.Plan[] plans,float t){
        for(var p:plans){
            double s=t-p.plant();if(s<0||s>=.7||!SwordFinale.comes(c.cast,p.plant()))continue;
            Vec3d g=p.foot().add(0,.1,0);
            for(int j=0;j<PLANT_SPARKS;j++){
                int seed=p.seed()*23+j*13+2101;double az=Math.PI*2*Ease.hash(seed),out=1.5+3*Ease.hash(seed+1),up=3.5+4.5*Ease.hash(seed+2);
                double life=.45+.25*Ease.hash(seed+3);if(s>=life)continue;
                Vec3d dir=new Vec3d(Math.cos(az),0,Math.sin(az));
                Vec3d q=spark(g,dir,out,up,s),q0=spark(g,dir,out,up,Math.max(0,s-.035));
                float a=(1-Ease.smooth((float)s,(float)(life*.5),(float)life))*near(q,c.camera);if(a<=.01f)continue;
                if(s<.15)m.needle(q0,q,.04,.4,j%3==0?WHITE:j%3==1?PALE:CYAN,.8f*a);
                else glint8(m,q,.07+.04*Ease.hash(seed+4),j%2==0?WHITE:LILAC,.75f*a*twinkle(t,seed));
            }
        }
    }
    static final int PLANT_SPARKS=10;

    /**
     * A gleam of moonlight running down each sword standing in the band from its hilt to its foot: once as it settles
     * (a moment after it strikes), then now and then until it goes, each its own beat (a gleam starts only once the last
     * has run).
     */
    static void gleams(Fx c,EffectMesh m,SwordFinale.Plan[] plans,float t){
        for(var p:plans){
            if(!SwordFinale.comes(c.cast,p.plant()))continue;
            double u=gleam(p,t);if(u>=1)continue;
            Vec3d hilt=SwordFinale.stuck(p)[0],foot=p.foot();
            Vec3d head=hilt.lerp(foot,u),tail=hilt.lerp(foot,Math.max(0,u-.3));
            float g=(float)Math.sin(Math.PI*u)*(1-Ease.smooth(t,p.vanish()-.1f,p.vanish()))*near(head,c.camera);
            m.softTaper(tail,head,.03,.22,PALE,.45f*g);m.softDisc(head,.35,WHITE,.28f*g);
        }
    }
    static final double GLEAM=.3;
    /** How far down sword {@code p} its gleam has run {@code t} seconds in (0 to 1), or 9 when none is running. */
    static double gleam(SwordFinale.Plan p,float t){
        if(t<p.plant()+.12f||t>=p.vanish())return 9;
        double since=t-p.plant()-.12,u=since/GLEAM;
        if(u<1)return u;
        double period=.9+.6*Ease.hash(p.seed()+90),off=GLEAM+(period-GLEAM)*Ease.hash(p.seed()+91);
        return since<off?9:((since-off)%period)/GLEAM;
    }

    /** Each of her own swords' two nearest of them by their hilts (each pair once: those of light are gone by then). */
    static int[][] links(SwordFinale.Plan[] plans){
        List<int[]> out=new ArrayList<>();
        for(int i=0;i<plans.length;i++){
            if(!plans[i].own())continue;
            Vec3d a=SwordFinale.stuck(plans[i])[0];int b0=-1,b1=-1;double d0=1e9,d1=1e9;
            for(int j=0;j<plans.length;j++){
                if(j==i||!plans[j].own())continue;double d=a.squaredDistanceTo(SwordFinale.stuck(plans[j])[0]);
                if(d<d0){d1=d0;b1=b0;d0=d;b0=j;}else if(d<d1){d1=d;b1=j;}
            }
            for(int j:new int[]{b0,b1}){
                if(j<0)continue;int lo=Math.min(i,j),hi=Math.max(i,j);boolean seen=false;
                for(int[] l:out)if(l[0]==lo&&l[1]==hi){seen=true;break;}
                if(!seen)out.add(new int[]{lo,hi});
            }
        }
        return out.toArray(new int[0][]);
    }
    /**
     * As the band bursts (FINALE_BURST): a net of starlight between the swords' hilts, each thread drawn on as the burst's
     * ring passes it (from the band's middle out, NET_SPEED), a star at every hilt; each thread going with its swords.
     */
    static void net(Fx c,EffectMesh m,SwordFinale.Frame fr,SwordFinale.Plan[] plans,float t){
        float at=FINALE_BURST;if(t<at||t>=FINALE_GONE1+SwordFinale.FADE||!SwordFinale.comes(c.cast,at))return;
        Vec3d mid=fr.at((SwordFinale.BAND_NEAR+SwordFinale.BAND_FAR)/2,0,0);
        for(int[] l:links(plans)){
            var a=plans[l[0]];var b=plans[l[1]];
            Vec3d p=SwordFinale.stuck(a)[0],q=SwordFinale.stuck(b)[0];
            float on=at+(float)(Math.min(p.distanceTo(mid),q.distanceTo(mid))/NET_SPEED),drawn=Ease.out(t,on,on+.12f);if(drawn<=0)continue;
            float al=Ease.smooth(t,on,on+.03f)*going(a,t)*going(b,t)*near(p.lerp(q,.5),c.camera);if(al<=.01f)continue;
            Vec3d e=p.lerp(q,drawn);
            m.line(p,e,.03,(l[0]+l[1])%2==0?PALE:LILAC,.5f*al);m.softLine(p,e,.18,AZURE,.13f*al);
        }
        for(var p:plans){
            if(!p.own())continue;
            Vec3d hilt=SwordFinale.stuck(p)[0];float on=at+(float)(hilt.distanceTo(mid)/NET_SPEED);
            float a=Ease.smooth(t,on,on+.05f)*going(p,t)*twinkle(t,p.seed()+7)*near(hilt,c.camera);
            glint8(m,hilt,.13,(p.seed()&1)==0?WHITE:PALE,.75f*a);
        }
    }
    /** How fast the net is drawn out from the band's middle (blocks a second: after the burst's ring). */
    static final double NET_SPEED=40;
    /** How much of what hangs on sword {@code p} is left as it goes. */
    static float going(SwordFinale.Plan p,float t){return 1-Ease.smooth(t,p.vanish()-.05f,p.vanish()+.1f);}

    // ───────────────────────────── where she comes down, as they go ─────────────────────────────

    /** Moon dust kicked up round her feet as she comes down onto the ground, settling. */
    static void landing(Fx c,EffectMesh m,SwordFinale.Frame fr,float t){
        float at=FINALE_LAND;if(t<at||t>at+.7f||!SwordFinale.comes(c.cast,at))return;
        Vec3d g=fr.spot().add(0,.05,0);float age=t-at,a=Ease.smooth(age,0,.02f);double s=Ease.out(age,0,.55f);
        for(int i=0;i<LAND_DUST;i++){
            int seed=i*43+2201;double az=Math.PI*2*(i+Ease.hash(seed))/LAND_DUST,out=.7+1.1*Ease.hash(seed+1),up=.4+.6*Ease.hash(seed+2);
            Vec3d p=g.add(Math.cos(az)*(.3+out*s),up*Math.sin(Math.PI*Math.min(1,age/.6)),Math.sin(az)*(.3+out*s));
            float da=a*(1-Ease.smooth(age,.35f,.65f))*twinkle(t,seed)*near(p,c.camera);if(da<=.01f)continue;
            if(i%2==0)glint8(m,p,.09,i%4==0?WHITE:PALE,.75f*da);else m.softDisc(p,.055,LILAC,.8f*da);
        }
    }
    static final int LAND_DUST=12;

    /** As each of her own goes in light: a ring of light out round its foot, and a little crescent moon rising off its hilt, a star by it. */
    static void vanish(Fx c,EffectMesh m,SwordFinale.Plan[] plans,float t){
        for(var p:plans){
            float s=t-p.vanish();if(!p.own()||s<0||s>=.8f||!SwordFinale.comes(c.cast,p.plant()))continue;
            float u=Ease.unit(s,0,.4f);
            if(u<1)FxLib.ring(m,p.foot().add(0,.05,0),.3+1.6*Ease.out(s,0,.35f),.06,AZURE,.5f*(1-u));
            float a=Ease.smooth(s,0,.06f)*(1-Ease.smooth(s,.35f,.8f));if(a<=.01f)continue;
            Vec3d q=SwordFinale.stuck(p)[0].add(0,1.8*Ease.out(s,0,.8f),0);float n=near(q,c.camera);
            crescentMoon(m,q,.26,Math.PI*.55+(Ease.hash(p.seed()+95)-.5)*.6,MOON,.6f*a*n);
            glint8(m,q.add(0,.05,0),.1,WHITE,.5f*a*n*twinkle(t,p.seed()+3));
        }
    }
}
