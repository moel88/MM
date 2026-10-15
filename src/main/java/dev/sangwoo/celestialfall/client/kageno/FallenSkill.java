package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.atomic.GroundCache;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static dev.sangwoo.celestialfall.client.kageno.Anim.*;
import static dev.sangwoo.celestialfall.client.kageno.R6.pose;

/**
 * Y "어둠 속으로 빠지다": the left knee comes up to the chest with the sword overhead, a three-frame
 * whip down into a wide stomp (hit 1, two ground chunks pop up), a 0.57 s crouch, then the flash
 * (hit 2): violet light pillars come down from the sky over a starburst, the screen whites out for
 * 0.3 s and a ring of eight huge tilted ground slabs stands around the caster, with wind arcs, mist,
 * orbs and motes. The slabs stay ≈3 s and sink straight down. No hop and no turning.
 * Seconds after the cast; 1 character height (H) = 1.8 blocks.
 */
final class FallenSkill implements Fx.Skill {
    static final double H=1.8;
    static final float LAND=.467f,CHUNKS=LAND+.08f,FLASH=1.034f,STAND=1.60f,SINK=FLASH+3.0f;

    // Windup: the torso turns 25-35° left and leans back while the left knee rises to the chest.
    static final R6.Pose K1=pose().body(25,-5,0).rArm(-30,-21).blade(-55,-10).lArm(-70,-106).lLeg(-50,0).rLeg(-85,180);
    static final R6.Pose K2=pose().body(35,-10,0).head(0,10,0).rArm(-6,-49).blade(-81,0).lArm(-65,-90).lLeg(-10,0).rLeg(-80,180);
    static final R6.Pose K3=pose().body(30,-15,0).head(-10,15,0).rArm(17,-62).blade(-50,-51).lArm(-65,-95).lLeg(0,0).rLeg(-75,180);
    static final R6.Pose K4=pose().body(25,-20,0).head(0,-5,0).rArm(53,-35).blade(6,-84).lArm(-62,-90).lLeg(5,0).rLeg(-70,180);
    /** Coiled peak: sword high behind the head, tip over the left shoulder, knee to the chest. */
    static final R6.Pose K5=pose().body(20,-20,-15).head(0,-10,0).rArm(75,143).blade(41,-138).lArm(-60,-66).lLeg(18,0).rLeg(-68,-143);
    static final R6.Pose K6=pose().head(0,15,0).rArm(40,67).blade(40,95).lArm(-30,-70).lLeg(-45,0);
    static final R6.Pose K7=pose().body(0,8,0).head(0,20,0).rArm(17,72).blade(5,85).lArm(-25,-75).lLeg(-70,-8).rLeg(-82,0);
    /** Stomp: wide stance, arms spread low, sword down-out to the right front, head bowed. */
    static final R6.Pose K8=pose().at(0,-.14,0).body(0,17,0).head(0,28,0).rArm(-15,55).blade(-45,51).lArm(-20,-65).rLeg(-66,45).lLeg(-60,-25);
    static final R6.Pose K9=K8.at(0,-.24,0).rLeg(-60,50).lLeg(-56,-32);
    static final Anim CLIP=Anim.of(
        k(0,KagenoClips.IDLE),
        k(.05,K1,Anim.Ease.OUT),
        k(.10,K2,Anim.Ease.LINEAR),
        k(.20,K3),
        k(.25,K4),
        k(.30,K5,Anim.Ease.OUT),
        k(.416,K5,Anim.Ease.HOLD),
        k(.433,K6,Anim.Ease.IN),
        k(.45,K7,Anim.Ease.LINEAR),
        k(LAND,K8,Anim.Ease.LINEAR),
        k(.86,K8,Anim.Ease.HOLD),
        k(.97,K9),
        k(STAND,K9,Anim.Ease.HOLD),
        k(1.65,KagenoClips.IDLE,Anim.Ease.LINEAR),
        k(1.7,KagenoClips.IDLE));
    @Override public Anim clip(int combo){return CLIP;}
    @Override public float lifetime(){return SINK+.45f;}

    // ───────────────────────────── crater pieces ─────────────────────────────

    /** A box of the ground block hinged at its inner edge, the outer edge raised by {@code tilt}. */
    record Piece(Vec3d hinge,double yaw,double length,double width,double thick,double tilt,double roll,float rise,float risen,float sink,BlockState state,double inner,double sector){}
    private static final Map<UUID,List<Piece>> PIECES=new HashMap<>();
    static List<Piece> pieces(Fx c){
        var list=PIECES.get(c.cast.id());if(list!=null)return list;
        PIECES.keySet().removeIf(id->!StandClient.CASTS.containsKey(id));
        list=new ArrayList<>();ClientWorld w=c.mc.world;int seed=(int)c.cast.id().getLeastSignificantBits();
        double face=Math.atan2(c.f.z,c.f.x);
        BlockState centre=material(w,c.origin.x,c.origin.z,c.origin.y,Blocks.STONE.getDefaultState());
        // Eight slabs at ±22.5°, ±67.5°, ±112.5° and ±157.5° from the facing, so two meet in front and
        // two behind. The side slabs stand up 20-28° (rims 1.4-2 character heights); the front and back
        // pairs lie low and long, so a camera in front of or behind the caster looks over them.
        for(int i=0;i<8;i++){
            double r1=Ease.hash(seed+i*7),r2=Ease.hash(seed+i*11+1),r3=Ease.hash(seed+i*13+2),r4=Ease.hash(seed+i*17+3),r5=Ease.hash(seed+i*19+4);
            double rel=22.5+45*i;boolean low=i==0||i==3||i==4||i==7;
            double a=face+Math.toRadians(rel+(r1-.5)*6);
            double inner=(.4+.4*r2)*H,length=(low?4.4+.5*r3:3.6+.8*r3)*H,width=(2.0+.5*r4)*H,thick=(.3+.1*r5)*H;
            double tilt=Math.toRadians(low?8+4*Ease.hash(seed+i*23+5):20+8*Ease.hash(seed+i*23+5)),roll=Math.toRadians((Ease.hash(seed+i*29+6)-.5)*12);
            Vec3d out=new Vec3d(Math.cos(a),0,Math.sin(a));
            double x=c.origin.x+out.x*inner,z=c.origin.z+out.z*inner;
            double ground=ground(w,x,z,c.origin.y);
            Vec3d mid=c.origin.add(out.multiply(inner+length*.5));
            list.add(new Piece(new Vec3d(x,ground-.15*H,z),a,length,width,thick,tilt,roll,FLASH,FLASH+.1f+.05f*(float)Ease.hash(seed+i*31+7),
                SINK+.1f*(float)Ease.hash(seed+i*37+8),material(w,mid.x,mid.z,c.origin.y,centre),inner,Math.toRadians(22.5)));
        }
        // Stomp chunks: a flat wedge right-front of the right foot and a smaller one by the left foot.
        Vec3d rOut=c.right.add(c.f.multiply(.3)).normalize(),lOut=c.left.add(c.f.multiply(.1)).normalize();
        Vec3d rh=c.origin.add(c.f.multiply(.25)).add(c.right.multiply(.3)),lh=c.origin.add(c.f.multiply(.1)).add(c.left.multiply(.35));
        list.add(new Piece(new Vec3d(rh.x,ground(w,rh.x,rh.z,c.origin.y)-.06,rh.z),Math.atan2(rOut.z,rOut.x),.75*H,.35*H,.12*H,Math.toRadians(20),Math.toRadians(-6),CHUNKS,CHUNKS+.067f,SINK,centre,0,0));
        list.add(new Piece(new Vec3d(lh.x,ground(w,lh.x,lh.z,c.origin.y)-.05,lh.z),Math.atan2(lOut.z,lOut.x),.5*H,.25*H,.1*H,Math.toRadians(27),Math.toRadians(5),CHUNKS,CHUNKS+.067f,SINK+.03f,centre,0,0));
        PIECES.put(c.cast.id(),list);return list;
    }
    private static double ground(ClientWorld w,double x,double z,double y){float h=GroundCache.surface(w,(int)Math.floor(x),(int)Math.floor(z),y);return Float.isNaN(h)?y:h;}
    private static BlockState material(ClientWorld w,double x,double z,double y,BlockState fallback){
        float h=GroundCache.surface(w,(int)Math.floor(x),(int)Math.floor(z),y);if(Float.isNaN(h))return fallback;
        var pos=BlockPos.ofFloored(x,h-.01,z);var state=w.getBlockState(pos);
        return state.isOpaqueFullCube(w,pos)?state:fallback;
    }
    @Override public void blocks(Fx c){
        if(c.sec<CHUNKS||c.sec>=SINK+.35f)return;
        var matrices=c.matrices;var vertices=c.mc.getBufferBuilders().getEntityVertexConsumers();boolean drew=false;
        for(var p:pieces(c)){
            float t=c.sec;if(t<p.rise()||t>=p.sink()+.2f)continue;
            double depth=p.length()*Math.sin(p.tilt())+p.thick()+.6;
            double drop=depth*(1-Ease.out(t,p.rise(),p.risen()))+depth*Ease.in(t,p.sink(),p.sink()+.18f);
            matrices.push();
            matrices.translate(p.hinge().x,p.hinge().y-drop,p.hinge().z);
            // Local frame: x along the ring, z outward, the outer edge raised by the tilt.
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float)(Math.PI/2-p.yaw())));
            matrices.multiply(RotationAxis.POSITIVE_X.rotation((float)-p.tilt()));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotation((float)p.roll()));
            // Block-sized cells. A crater slab keeps inside its own 45° sector (narrow near the caster,
            // a thin gap to its neighbours) so neighbouring slabs never cut through each other.
            int nl=Math.max(1,(int)Math.round(p.length()/(p.sector()>0?.5:1.05)));double pl=p.length()/nl;
            int light=WorldRenderer.getLightmapCoordinates(c.mc.world,BlockPos.ofFloored(p.hinge().x,p.hinge().y+1.2,p.hinge().z));
            for(int i=0;i<nl;i++){
                double row=p.width();
                if(p.sector()>0){double r=p.inner()+(i+.15)*pl*Math.cos(p.tilt());row=Math.min(row,2*r*Math.tan(p.sector())-.35);}
                if(row<.3)continue;
                int nw=Math.max(1,(int)Math.round(row/1.05));double pw=row/nw;
                for(int j=0;j<nw;j++){
                    matrices.push();
                    matrices.translate(-row*.5+j*pw,-p.thick(),i*pl);
                    matrices.scale((float)pw,(float)p.thick(),(float)pl);
                    c.mc.getBlockRenderManager().renderBlockAsEntity(p.state(),matrices,vertices,light,OverlayTexture.DEFAULT_UV);
                    matrices.pop();drew=true;
                }
            }
            matrices.pop();
        }
        if(drew)vertices.draw();
    }

    // ───────────────────────────── flash ─────────────────────────────

    /** White-out alpha keyed per 60 fps frame from the flash. */
    private static final float[] WF={0,1,2,3,5,9,11,13,16,17,18,23},WA={.2f,.5f,.72f,.86f,.97f,.98f,.9f,.8f,.75f,.35f,.2f,0};
    static float whiteout(float t){
        float f=t*60;if(f<0)return 0;if(f>=WF[WF.length-1])return 0;
        for(int i=1;i<WF.length;i++)if(f<WF[i])return WA[i-1]+(WA[i]-WA[i-1])*(f-WF[i-1])/(WF[i]-WF[i-1]);
        return 0;
    }
    @Override public float flash(Fx c){return whiteout(c.sec-FLASH);}
    @Override public int flashColor(Fx c){return 0xfbfbfa;}

    /** Camera kick: pitches down ≈8° with a roll over 0.25 s, then a 3.7 Hz yaw wobble; 0.6 s in all. */
    @Override public float[] kick(float sec){
        float t=sec-FLASH;if(t<0||t>=.62f)return null;
        if(t<.255f){float k=Ease.smooth(t,0,.255f);return new float[]{0,8*k,4.2f*k};}
        float u=t-.255f,fade=1-Ease.smooth(u,.2f,.35f);
        float pitch=4*(1-Ease.smooth(u,.18f,.35f))+4*(float)Math.exp(-u/.025f)-(float)Math.sin(2*Math.PI*7.5*u)*fade;
        float yaw=-1.9f*(float)Math.cos(2*Math.PI*3.7*u)*(1-Ease.smooth(u,.15f,.36f));
        float roll=4.2f*(float)Math.exp(-u/.04f)+.9f*(float)Math.sin(2*Math.PI*5*u)*(1-Ease.smooth(u,.15f,.35f));
        return new float[]{yaw,pitch,roll};
    }

    /** Light pillars: spread evenly around the crater (0.3-4.8 character heights out), slight stagger. */
    private static Vec3d pillar(Fx c,int i){
        int seed=(int)c.cast.id().getMostSignificantBits();
        double a=(i+.2+.6*Ease.hash(seed+i*41))*Math.PI*2/PILLARS,r=(.3+4.5*Math.sqrt(Ease.hash(seed+i*43+1)))*H;
        return c.origin.add(Math.cos(a)*r,0,Math.sin(a)*r);
    }
    private static final int PILLARS=14;
    /** Drawn over the white-out: violet brush pillars, then thin white beams with a soft glow. */
    @Override public void overFlash(Fx c,EffectMesh m,boolean additive){
        float t=c.sec-FLASH;if(t<0||t>=.4f)return;
        for(int i=0;i<PILLARS;i++){
            Vec3d base=pillar(c,i);double top=base.y+18;
            float delay=.02f*(float)Ease.hash(i*5+3);
            double bottom=top-(top-base.y)*Ease.out(t,delay,delay+.05f);
            if(bottom>=top-.05)continue;
            Vec3d a=new Vec3d(base.x,top,base.z),b=new Vec3d(base.x,bottom,base.z);
            if(!additive){
                float va=1-Ease.smooth(t,.11f,.16f);if(va<=0)continue;
                // Violet brush pillar ≈0.5-1 stud wide (wider with the glow): light edges, violet body,
                // a dark core and loose streaks ending at different heights.
                double w=(.28+.34*Ease.hash(i*7+1));
                m.needle(a,b,w*2.2,.55,0xc7acfb,va*.45f);
                m.needle(a,b,w*1.3,.55,Ease.hash(i*9)>.5?0x866ce9:0xaa8cf5,va*.85f);
                m.needle(a,b,w*.45,.55,0x6046d4,va*.9f);
                Vec3d side0=c.camera.subtract(base).crossProduct(Fx.UP);if(side0.lengthSquared()<1e-6)side0=c.right;side0=side0.normalize();
                for(int k=0;k<4;k++){
                    Vec3d side=side0.multiply((k%2==0?-1:1)*w*(.35+.45*Ease.hash(i*11+k)));
                    double end=bottom+(top-bottom)*.3*Ease.hash(i*13+k);
                    m.needle(a.add(side),new Vec3d(b.x,end,b.z).add(side),w*(.2+.25*Ease.hash(i*19+k)),.5,k<2?0x7a5ee2:0xb79cf6,va*.75f);
                }
            }else{
                float wa=Ease.smooth(t,.12f,.16f);if(wa<=0)continue;
                // Thin white beams with a soft glow; pieces fade out at different times, from the ground up.
                for(int k=0;k<4;k++){
                    double y0=base.y+(top-base.y)*k/4.0*.6,y1=k==3?top:base.y+(top-base.y)*(k+1)/4.0*.6;
                    float pa=wa*(1-Ease.smooth(t,.26f+.025f*k+.06f*(float)Ease.hash(i*17+k),.37f));if(pa<=0)continue;
                    Vec3d p0=new Vec3d(base.x,Math.max(y0,bottom),base.z),p1=new Vec3d(base.x,y1,base.z);
                    m.line(p0,p1,.3,0xd8e0f8,pa*.22f);m.line(p0,p1,.12,0xe6e9f4,pa*.4f);m.line(p0,p1,.05,0xf9fef8,pa);
                }
            }
        }
    }
    @Override public void additive(Fx c,EffectMesh m){
        hitSpark(c,m);
        float t=c.sec-FLASH;if(t<0)return;
        starburst(c,m,t);
        mist(c,m,t);
        orbs(c,m,t);
        motes(c,m,t);
        arcs(c,m,t,true);
    }
    @Override public void alpha(Fx c,EffectMesh m){
        float t=c.sec-FLASH;if(t<0)return;
        arcs(c,m,t,false);
        dust(c,m,t);
    }
    /** Flat star at chest height: 12 irregular spikes, white centre, pink fringe, violet tips. */
    static void starburst(Fx c,EffectMesh m,float t){
        if(t>=.09f)return;float a=1-Ease.smooth(t,.05f,.09f);double grow=.85+.15*Ease.out(t,0,.035f);
        Vec3d o=c.origin.add(0,.55*H,0);double R=4*H*grow,base=.22*R;int seed=(int)c.cast.id().getLeastSignificantBits()^0x5a5a;
        for(int k=0;k<12;k++){
            double th=k*Math.PI/6+(Ease.hash(seed+k)-.5)*.25,len=R*(.55+.45*Ease.hash(seed+k*3+1)),half=Math.toRadians(7+4*Ease.hash(seed+k*5+2));
            Vec3d l=o.add(Math.cos(th-half)*base,0,Math.sin(th-half)*base),r=o.add(Math.cos(th+half)*base,0,Math.sin(th+half)*base);
            Vec3d tip=o.add(Math.cos(th)*len,0,Math.sin(th)*len),ml=l.lerp(tip,.55),mr=r.lerp(tip,.55);
            // Spike: white root, pink shoulders, violet tip; the centre fan stays white.
            m.vertex(l,0xfbfdf8,a);m.vertex(ml,0xfbeff8,a*.9f);m.vertex(mr,0xfbeff8,a*.9f);m.vertex(r,0xfbfdf8,a);
            m.vertex(ml,0xfbeff8,a*.9f);m.vertex(tip,0xa98cf2,a*.45f);m.vertex(tip,0xa98cf2,a*.45f);m.vertex(mr,0xfbeff8,a*.9f);
            m.vertex(o,0xfbfdf8,a);m.vertex(l,0xfbfdf8,a);m.vertex(r,0xfbfdf8,a);m.vertex(r,0xfbfdf8,a);
        }
        m.softDisc(o,base*1.6,0xfbfdf8,a);
    }
    /** Luminous low fog over the crater floor as the white-out clears. */
    static void mist(Fx c,EffectMesh m,float t){
        if(t<.12f||t>=.55f)return;float a=.42f*(1-Ease.smooth(t,.3f,.55f));
        for(int i=0;i<14;i++){
            double ang=Ease.hash(i*3+71)*Math.PI*2,r=(.5+3.5*Ease.hash(i*5+72))*H;
            m.softDisc(c.origin.add(Math.cos(ang)*r,.3,Math.sin(ang)*r),1.6+1.0*Ease.hash(i*7+73),0xf2f4ff,a);
        }
    }
    /** Grey-blue dust hugging the slab bases. */
    static void dust(Fx c,EffectMesh m,float t){
        if(t<.42f||t>=1.12f)return;float a=.28f*Ease.window(t,.42f,1.12f,.15f,.4f);
        for(int i=0;i<10;i++){
            double ang=Ease.hash(i*3+91)*Math.PI*2,r=(1.3+2.6*Ease.hash(i*5+92))*H+.3*(t-.42f);
            m.softDisc(c.origin.add(Math.cos(ang)*r,.5+.2*(t-.42f),Math.sin(ang)*r),1.4+.8*Ease.hash(i*7+93),0x9aaed0,a);
        }
    }
    /** Large white orbs with a pink-violet halo, drifting; some shoot up at the end. */
    static void orbs(Fx c,EffectMesh m,float t){
        for(int i=0;i<10;i++){
            float born=.15f+.1f*(float)Ease.hash(i*3+11),life=.8f+.25f*(float)Ease.hash(i*5+12),u=(t-born)/life;
            if(u<0||u>=1)continue;
            double ang=Ease.hash(i*7+13)*Math.PI*2,r=(.2+3.8*Ease.hash(i*9+14))*H,y=1.5*H*Ease.hash(i*11+15);
            double dir=Ease.hash(i*13+16)*Math.PI*2,speed=(.6+.6*Ease.hash(i*17+17))*H,age=t-born;
            Vec3d p=c.origin.add(Math.cos(ang)*r+Math.cos(dir)*speed*age,y+.3*age,Math.sin(ang)*r+Math.sin(dir)*speed*age);
            if(i%3==0)p=p.add(0,7*Ease.in(u,.85f,1),0);
            float a=Ease.smooth(u,0,.08f)*(1-Ease.smooth(u,.8f,1));double s=(i%4==3?.12:.28)+.06*Ease.hash(i*19+18);
            m.softDisc(p,s*2.6,0xd59cf2,a*.6f);m.softDisc(p,s*1.2,0xfbe6ff,a);m.softDisc(p,s*.8,0xffffff,a);m.softDisc(p,s*.45,0xffffff,a);
        }
    }
    /** Small purple motes filling the crater volume, drifting and twinkling. */
    static void motes(Fx c,EffectMesh m,float t){
        float time=c.age*.05f;
        for(int i=0;i<44;i++){
            float born=.2f+.1f*(float)Ease.hash(i*3+31),end=1.35f+.2f*(float)Ease.hash(i*5+32);
            if(t<born||t>=end)continue;
            float a=Ease.smooth(t,born,born+.1f)*(1-Ease.smooth(t,end-.2f,end))*(.6f+.4f*(float)Math.sin(time*8+i*1.7));
            double ang=Ease.hash(i*7+33)*Math.PI*2,r=(.5+4*Ease.hash(i*9+34))*H,y=1.5*H*Ease.hash(i*11+35),drift=(t-born)*.15;
            Vec3d p=c.origin.add(Math.cos(ang)*r+Math.cos(i)*drift,y+drift,Math.sin(ang)*r+Math.sin(i)*drift);
            double s=.09+.11*Ease.hash(i*13+36);
            m.softDisc(p,s*2.6,0xd77ff0,a*.6f);m.softDisc(p,s*1.2,0xebb4f1,a);m.softDisc(p,s*.6,0xffdcff,a);
        }
    }
    /** Broad violet wind bands sweeping out around the crater (an expanding ring past the camera). */
    static void arcs(Fx c,EffectMesh m,float t,boolean core){
        if(t<.225f||t>=.485f)return;
        float u=Ease.unit(t,.225f,.485f),a=.6f*(1-u)*Ease.smooth(t,.225f,.26f);
        for(int k=0;k<3;k++){
            double y=(.25+.4*k)*H,start=Ease.hash(k*7+51)*Math.PI*2,span=Math.toRadians(200+40*Ease.hash(k*7+52)),r=(1+5*Ease.out(u,0,1))*H*(.9+.2*Ease.hash(k*7+53));
            double hb=.25+.15*Ease.hash(k*7+54);int n=28;
            for(int i=0;i<n;i++){
                double s0=i/(double)n,s1=(i+1)/(double)n,a0=start+span*s0,a1=start+span*s1;
                float e0=a*(float)Math.sin(Math.PI*s0),e1=a*(float)Math.sin(Math.PI*s1);
                Vec3d p0=c.origin.add(Math.cos(a0)*r,y,Math.sin(a0)*r),p1=c.origin.add(Math.cos(a1)*r,y,Math.sin(a1)*r);
                if(core){m.line(p0,p1,.08,0xefe8ff,(e0+e1)*.25f);continue;}
                Vec3d up=new Vec3d(0,hb,0);
                m.vertex(p0.subtract(up),0xbaa3f1,0);m.vertex(p1.subtract(up),0xbaa3f1,0);m.vertex(p1,0xbaa3f1,e1);m.vertex(p0,0xbaa3f1,e0);
                m.vertex(p0,0xbaa3f1,e0);m.vertex(p1,0xbaa3f1,e1);m.vertex(p1.add(up),0xbaa3f1,0);m.vertex(p0.add(up),0xbaa3f1,0);
            }
        }
    }
    /** Hit 1 on each target: a white star spark at the chest, then thin orbit arcs spinning out. */
    static void hitSpark(Fx c,EffectMesh m){
        float t=c.sec;if(t<LAND+.09f||t>=LAND+.5f)return;
        for(LivingEntity e:c.targets){
            Vec3d chest=e.getLerpedPos(c.delta).add(0,e.getHeight()*.6,0);
            if(t<LAND+.16f){float u=Ease.unit(t,LAND+.09f,LAND+.16f);m.star(chest,.45*(1+.3*u),0xffffff,1-Ease.smooth(u,.5f,1));}
            if(t>=LAND+.16f){
                float u=Ease.unit(t,LAND+.16f,LAND+.5f),a=1-Ease.smooth(u,.65f,1);
                for(int k=0;k<3;k++){
                    double tilt=Math.toRadians(20+25*k),spin=t*12+k*2.1;
                    Vec3d uu=new Vec3d(Math.cos(spin),0,Math.sin(spin)),vv=new Vec3d(-Math.sin(spin)*Math.cos(tilt),Math.sin(tilt),Math.cos(spin)*Math.cos(tilt));
                    m.ring(chest,uu,vv,.6*H*(1+.25*u),24,.55f,.035,0xd9f6ff,a*.85f,k);
                }
            }
        }
    }
}
