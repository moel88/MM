package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.EffectMesh;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import static dev.sangwoo.celestialfall.client.kageno.Anim.*;
import static dev.sangwoo.celestialfall.client.kageno.R6.pose;

/**
 * P "포즈": a 2 s sword flourish with lavender trails, then a held "arms open, sword lowered" stance.
 * Phase 1 (from 2.17 s): electric magenta/violet streaks flicker over the body, a red four-point
 * glint on the right eye, dark-red "ゴゴ" glyphs drifting up, a pink spiky star on the ground and a
 * violet light pool. Phase 2 (from 8.57 s): the star flashes white, and ground crescents unfold
 * behind the caster and small rings pulse out at the reference beats. Released at 18.68 s or when
 * P is pressed again. In place, no hits. Seconds after the cast; 1 stud = 0.36 blocks.
 */
final class PoseSkill implements Fx.Skill {
    static final double STUD=.36;
    static final float AURA=2.167f,STAR=2.30f,EYE=2.247f,POOL=2.35f,GLYPH=2.58f,PHASE2=8.567f,RELEASE=18.683f,END=19.45f;
    /** Phase-2 beats from the review: white star re-flashes, pulse rings and ground crescents (crescents: when they reach the sides). */
    static final float[] WHITE={8.567f,9.717f,10.6f,11.067f,11.633f,12.333f,12.683f,14.217f,14.883f,15.517f,16.733f};
    static final float[] PULSES={9.6f,9.817f,10.55f,10.867f,11.517f,11.717f,12.133f,12.45f,13.067f,13.233f,13.533f,13.7f,14.5f,15.15f,15.317f,16.067f,16.833f,17.617f};
    static final float[] CRESCENTS={8.767f,9.317f,9.55f,10.2f,10.367f,10.583f,10.833f,11.3f,11.917f,12.583f,12.9f,13.5f,14.433f,15.1f,15.4f,15.75f,16.317f,17.017f,17.6f,18.217f,18.85f};

    static final R6.Pose OVERHEAD=pose().rArm(76,-28).lArm(76,28).blade(88,0);
    static final R6.Pose FINAL=pose().rArm(-27,60).lArm(-47,-50).blade(-20,75).rLeg(-90,1).lLeg(-90,-1);
    static final Anim CLIP;
    static {
        List<Key> keys=new ArrayList<>(List.of(
            k(0,KagenoClips.IDLE),
            k(.12,pose().rArm(-20,70).blade(10,80),Anim.Ease.LINEAR),
            k(.35,OVERHEAD,Anim.Ease.OUT),
            k(.58,OVERHEAD,Anim.Ease.HOLD),
            k(.70,OVERHEAD.blade(10,-100),Anim.Ease.IN),
            k(.78,OVERHEAD.blade(-35,-105),Anim.Ease.OUT),
            k(1.12,OVERHEAD.blade(0,-95)),
            k(1.22,pose().rArm(60,70).blade(40,160).lArm(-30,-90),Anim.Ease.LINEAR),
            k(1.72,pose().rArm(58,72).blade(38,158).lArm(-28,-90)),
            k(1.82,pose().rArm(-5,85).blade(88,0).lArm(-30,-90),Anim.Ease.OUT),
            k(1.85,pose().rArm(-15,85).blade(30,90).lArm(-35,-80),Anim.Ease.LINEAR),
            k(1.88,pose().rArm(-25,75).blade(-40,90).lArm(-42,-60),Anim.Ease.LINEAR),
            k(2.0,FINAL)));
        // Breathing sway: the arms and blade rise and fall ≈3° with a 3 s period.
        for(float t=2.75f;t<RELEASE;t+=.75f){double s=Math.sin((t-2.0)*Math.PI*2/3.0)*3;keys.add(k(t,FINAL.rArm(-27+s,60).lArm(-47+s,-50).blade(-20+s,75)));}
        keys.add(k(RELEASE,FINAL,Anim.Ease.HOLD));
        keys.add(k(RELEASE+.033f,KagenoClips.IDLE,Anim.Ease.LINEAR));
        keys.add(k(RELEASE+.3f,KagenoClips.IDLE));
        CLIP=Anim.of(keys.toArray(Key[]::new));
    }
    @Override public Anim clip(int combo){return CLIP;}
    @Override public float lifetime(){return END;}

    /** Sword tip in the world for the clip pose at {@code sec}. */
    static Vec3d tip(Fx c,float sec){var hb=KagenoClips.blade(c.origin,c.cast.yaw(),CLIP.pose(sec));return hb[0].add(hb[1].multiply(1.35));}
    /** Point along a limb (0 = shoulder/hip, 1 = hand/foot) for the held pose. */
    static Vec3d limb(Fx c,R6.Dir d,boolean right,boolean leg,double u){
        Vec3d base=c.at0(0,leg?.75:1.375,(right?1:-1)*(leg?.125:.3125));
        return base.add(c.vec(d.fw(),d.up(),d.rt()).multiply((leg?.75:.625)*u));
    }
    static float phase2(float t){return t>=PHASE2&&t<RELEASE?1:0;}
    /** The star is white after each re-flash, dropping back to pink 0.2-0.6 s before the next one. */
    static boolean white(float t){
        if(t<PHASE2||t>=RELEASE)return false;
        for(int i=WHITE.length-1;i>=0;i--)if(t>=WHITE[i]){float next=i+1<WHITE.length?WHITE[i+1]:RELEASE,gap=next-WHITE[i];return t<WHITE[i]+Math.max(.3f,gap-(.2f+.4f*(float)Ease.hash(i*7+3)));}
        return false;
    }

    @Override public void additive(Fx c,EffectMesh m){
        float t=c.sec;
        trails(c,m,t);
        streaks(c,m,t);
        glint(c,m,t);
        // A small white-hot glow over the star; its colours are drawn in the alpha pass.
        if(t>=STAR&&t<RELEASE+.27f)m.softDisc(c.origin.add(0,.08,0),(white(t)?.8:.5),white(t)?0xffffff:0xfb9afc,.35f*(1-Ease.smooth(t,RELEASE,RELEASE+.27f)));
    }
    /** Ground light, star, rings and crescents: alpha-blended so their violet/pink holds on a bright floor. */
    @Override public void alpha(Fx c,EffectMesh m){
        float t=c.sec;
        pool(c,m,t);
        star(c,m,t);
        rings(c,m,t);
        crescents(c,m,t);
    }
    /** Lavender ribbons left in the air by the sword tip during the flourish (each piece ≈0.7 s). */
    static void trails(Fx c,EffectMesh m,float t){
        if(t<.1f||t>=2.8f)return;
        float from=Math.max(.1f,t-.7f),to=Math.min(t,2.0f);Vec3d prev=null;
        for(float s=from;s<=to;s+=1/60f){
            Vec3d p=tip(c,s);
            if(prev!=null&&p.squaredDistanceTo(prev)>.0004){
                float age=(t-s)/.7f,a=1-age;double w=.11*(1-.5*age);
                m.line(prev,p,w*1.9,0xae80e6,a*.45f);m.line(prev,p,w,0xc9bbe5,a*.8f);m.line(prev,p,w*.35,0xfeecff,a);
            }
            prev=p;
        }
    }
    /** Large violet light pool on the ground (≈20 studs), ramping on in 0.1 s. */
    static void pool(Fx c,EffectMesh m,float t){
        if(t<POOL||t>=RELEASE+.2f)return;
        float a=Ease.smooth(t,POOL,POOL+.1f)*(1-Ease.smooth(t,RELEASE+.1f,RELEASE+.2f))*.78f;
        Vec3d o=c.origin.add(0,.02,0);double r=20*STUD;
        for(int i=0;i<32;i++){
            double a0=i*Math.PI/16,a1=(i+1)*Math.PI/16;
            Vec3d p0=o.add(Math.cos(a0)*r,0,Math.sin(a0)*r),p1=o.add(Math.cos(a1)*r,0,Math.sin(a1)*r),q0=o.add(Math.cos(a0)*r*.35,0,Math.sin(a0)*r*.35),q1=o.add(Math.cos(a1)*r*.35,0,Math.sin(a1)*r*.35);
            m.vertex(o,0x6a4bd0,a);m.vertex(q0,0x6a4bd0,a*.75f);m.vertex(q1,0x6a4bd0,a*.75f);m.vertex(o,0x6a4bd0,a);
            m.vertex(q0,0x6a4bd0,a*.75f);m.vertex(p0,0x6a4bd0,0);m.vertex(p1,0x6a4bd0,0);m.vertex(q1,0x6a4bd0,a*.75f);
        }
    }
    /** Spiky impact star at the feet: pink in phase 1, flashing white-hot and larger in phase 2. */
    static void star(Fx c,EffectMesh m,float t){
        if(t<STAR||t>=RELEASE+.27f)return;
        boolean w=white(t);float grow=t<STAR+.1f?(float)(1+2*Ease.out(t,STAR,STAR+.1f))/3f:1,shrink=1-Ease.smooth(t,RELEASE,RELEASE+.27f);
        double base=(w?4.4:3.4)*STUD*grow*shrink;int frame=(int)(t*20);
        Vec3d o=c.origin.add(0,.03,0);int fill=w?0xfbf5f9:0xfb9afc,rim=w?0xfdb6fb:0xf07efd;
        int n=18;
        for(int k=0;k<n;k++){
            // Spikes re-randomise every frame or two (flickering shape); a few long single spikes in phase 2.
            double th=(k+.5*Ease.hash(frame*31+k))*Math.PI*2/n,len=base*(.65+.45*Ease.hash(frame*17+k*3))*(w&&Ease.hash(frame*7+k)>.85?1.35:1),half=Math.PI/n*.85;
            Vec3d l=o.add(Math.cos(th-half)*base*.32,0,Math.sin(th-half)*base*.32),r=o.add(Math.cos(th+half)*base*.32,0,Math.sin(th+half)*base*.32),tipP=o.add(Math.cos(th)*len,0,Math.sin(th)*len);
            m.vertex(o,fill,.95f);m.vertex(l,fill,.9f);m.vertex(tipP,rim,.6f);m.vertex(r,fill,.9f);
        }
        m.softDisc(o.add(0,.05,0),base*1.25,rim,.25f);
        // Solid hot centre.
        annulus(m,o.add(0,.005,0),0,base*.42,0,Math.PI*2,24,fill,.95f);
    }
    /** Phase 2 small pulse: a magenta filled disc that hollows into a pink annulus and a thin white ring (0.38 s). */
    static void rings(Fx c,EffectMesh m,float t){
        Vec3d o=c.origin.add(0,.04,0);
        for(float p:PULSES){
            float u=t-p;if(u<0||u>=.38f||p>=RELEASE)continue;
            double outer=(2+6.45*(1-Math.pow(1-Math.min(1,u/.36),3)))*STUD;
            double inner=u<.11f?0:outer-Math.max(.12,(1.6-1.3*Ease.unit(u,.11f,.33f)))*STUD;
            int col=u<.02f?0x5a2a9a:u<.12f?0xe330fb:u<.18f?0xf07cfe:u<.25f?0xfeb3fb:u<.3f?0xfec8fd:0xfdfdfd;
            float a=u<.33f?.9f:.9f*(1-Ease.unit(u,.33f,.38f));
            annulus(m,o,inner,outer,0,Math.PI*2,48,col,a);
            if(u>=.28f)annulus(m,o,outer,outer+.08,0,Math.PI*2,48,0xca8fff,a*.6f);
        }
    }
    /**
     * Phase 2 ground crescent: a thick ≈200° band behind the caster that unfolds from directly
     * behind to both sides in ≈0.14 s while it expands; the outer edge eases out to 17.4 studs, the
     * inner edge chases it at ≈16 studs/s; magenta → pink → white core → pink → magenta (0.8 s).
     */
    static void crescents(Fx c,EffectMesh m,float t){
        Vec3d o=c.origin.add(0,.05,0);double back=Math.atan2(-c.f.z,-c.f.x);
        for(float side:CRESCENTS){
            float born=side-.14f,u=t-born;if(u<0||u>=.8f||born>=RELEASE+.1f)continue;
            double outer=17.4*STUD*(1-Math.pow(1-Math.min(1,u/.55),2.6)),inner=Math.max(0,(u-.14)*16)*STUD+7.05*STUD*Ease.smooth(u,.06f,.14f);
            inner=Math.min(inner,outer-.03);if(outer<=.05)continue;
            double span=Math.toRadians(200)*Ease.out(u,0,.14f)/2;
            int core=u<.14f?0xe036db:u<.17f?0xf07cfe:u<.38f?0xfdfdfd:u<.45f?0xfb82fd:0xfd06cf;
            float a=1-Ease.smooth(u,.62f,.8f);
            double width=outer-inner;
            annulus(m,o,inner,inner+width*.3,back-span,back+span,40,0xb02583,a*.8f);
            annulus(m,o,inner+width*.3,outer-width*.12,back-span,back+span,40,core,a);
            annulus(m,o,outer-width*.12,outer,back-span,back+span,40,0xffa8f6,a);
        }
    }
    /** Flat ring sector on the ground. */
    static void annulus(EffectMesh m,Vec3d o,double r0,double r1,double a0,double a1,int n,int rgb,float alpha){
        if(r1<=r0||alpha<=0)return;
        for(int i=0;i<n;i++){
            double b0=a0+(a1-a0)*i/n,b1=a0+(a1-a0)*(i+1)/n;
            m.quad(o.add(Math.cos(b0)*r0,0,Math.sin(b0)*r0),o.add(Math.cos(b1)*r0,0,Math.sin(b1)*r0),o.add(Math.cos(b1)*r1,0,Math.sin(b1)*r1),o.add(Math.cos(b0)*r1,0,Math.sin(b0)*r1),rgb,alpha);
        }
    }
    /** Electric aura: 10-20 short magenta / violet-blue capsules flickering on the body (2-4 frames each). */
    static void streaks(Fx c,EffectMesh m,float t){
        if(t<AURA||t>=RELEASE+.03f)return;float in=Ease.smooth(t,AURA,AURA+.035f);
        var pose=CLIP.pose(t);int seed=(int)c.cast.id().getLeastSignificantBits();
        int last=(int)Math.floor(t*240);
        for(int k=last-16;k<=last;k++){
            float born=k/240f,life=.033f+.035f*(float)Ease.hash(seed+k*3),u=(t-born)/life;if(u<0||u>=1)continue;
            int part=(int)(Ease.hash(seed+k*5)*7);double h=Ease.hash(seed+k*7);
            Vec3d p=switch(part){
                case 0->limb(c,pose.rArm,true,false,h);
                case 1->limb(c,pose.lArm,false,false,h);
                case 2->limb(c,pose.rLeg,true,true,h);
                case 3->limb(c,pose.lLeg,false,true,h);
                case 4->c.at0(0,1.5+.5*h,(Ease.hash(seed+k*11)-.5)*.5);
                default->c.at0((Ease.hash(seed+k*13)-.5)*.3,.75+.75*h,(Ease.hash(seed+k*11)-.5)*.55);
            };
            p=p.add((Ease.hash(seed+k*17)-.5)*.3,(Ease.hash(seed+k*19)-.5)*.3,(Ease.hash(seed+k*23)-.5)*.3);
            Vec3d d=new Vec3d(Ease.hash(seed+k*29)-.5,Ease.hash(seed+k*31)-.5,Ease.hash(seed+k*37)-.5);if(d.lengthSquared()<1e-4)d=Fx.UP;d=d.normalize();
            double len=(.3+1.2*Ease.hash(seed+k*41))*STUD*.5,w=(.1+.1*Ease.hash(seed+k*43))*STUD;
            boolean magenta=Ease.hash(seed+k*47)>.35;float a=in*(float)Math.sin(Math.PI*u);
            m.line(p.subtract(d.multiply(len)),p.add(d.multiply(len)),w*2.2,magenta?0xeb6dea:0x7142e1,a*.5f);
            m.line(p.subtract(d.multiply(len*.8)),p.add(d.multiply(len*.8)),w*.7,magenta?0xff8bff:0x9a7cf0,a);
        }
        // Larger glowing blobs on the hands, hips and feet.
        float pulse=.6f+.4f*(float)Math.sin(t*17);
        for(Vec3d p:new Vec3d[]{limb(c,pose.rArm,true,false,1),limb(c,pose.lArm,false,false,1),c.at0(0,.8,.2),c.at0(0,.8,-.2),c.at0(0,.05,.13),c.at0(0,.05,-.13)})
            m.softDisc(p,.16,0xeb6dea,in*pulse*.55f);
    }
    /** Red four-point lens flare on the right eye, flickering ±30 % in size every frame. */
    static void glint(Fx c,EffectMesh m,float t){
        if(t<EYE||t>=RELEASE+.12f)return;
        float s=Ease.out(t,EYE,EYE+.067f)*(1-Ease.smooth(t,RELEASE+.02f,RELEASE+.12f));
        int frame=(int)(t*60);double flick=1+.3*(Ease.hash(frame*13+5)*2-1);
        Vec3d e=c.at0(.27,1.72,.12);Vec3d[] ax=HighestSkill.screen(c);
        double hx=(.75+.55*Ease.hash(frame*7))*STUD*flick*s,hy=(.7+.5*Ease.hash(frame*11))*STUD*flick*s;
        m.softDisc(e,.09*s,0xff0000,.8f);
        for(int k=0;k<4;k++){
            Vec3d d=k<2?ax[0].multiply(k==0?1:-1):ax[1].multiply(k==2?1:-1);double len=k<2?hx:hy;
            m.needle(e,e.add(d.multiply(len)),.07*s,.02,0xf02a3a,.55f);m.needle(e,e.add(d.multiply(len*.8)),.025*s,.02,0xff0000,1);
        }
    }
    /** "ゴゴ" glyph pairs (dark blood red, then black) drifting up around the upper body. */
    @Override public void blocks(Fx c){
        float t=c.sec;if(t<GLYPH||t>=RELEASE+1.1f)return;
        var tr=c.mc.textRenderer;var vertices=c.mc.getBufferBuilders().getEntityVertexConsumers();var cam=c.mc.gameRenderer.getCamera();
        int seed=(int)c.cast.id().getMostSignificantBits();boolean drew=false;
        int last=(int)Math.floor((Math.min(t,RELEASE)-GLYPH)*3);
        for(int k=Math.max(0,last-4);k<=last;k++){
            float born=GLYPH+k/3f+.12f*(float)Ease.hash(seed+k*3),u=t-born;if(u<0||u>=1.05f||born>=RELEASE)continue;
            double ang=Ease.hash(seed+k*5)*Math.PI*2,r=(1+1.5*Ease.hash(seed+k*7))*STUD;
            Vec3d p=c.at0(Math.sin(ang)*r*.4,1.3+(Ease.hash(seed+k*11)-.3)*1.5*STUD+.36*u,Math.cos(ang)*r);
            float pop=Ease.out(u,0,.1f);int color=u<.75f?0x6f1212:0x040105;
            for(int g=0;g<2;g++){
                double size=(g==0?.9:.65)*STUD*pop;Vec3d q=g==0?p:p.add(0,-.2,0).add(c.right.multiply(.18));
                var mat=c.matrices;mat.push();mat.translate(q.x,q.y,q.z);mat.multiply(cam.getRotation());
                float sc=(float)(size/8);mat.scale(-sc,-sc,sc);
                var text=Text.literal("ゴ").asOrderedText();float w=tr.getWidth(text);
                tr.drawWithOutline(text,-w/2,-4,0xff000000|color,0xff000000,mat.peek().getPositionMatrix(),vertices,LightmapTextureManager.MAX_LIGHT_COORDINATE);
                mat.pop();drew=true;
            }
        }
        if(drew)vertices.draw();
    }
}
