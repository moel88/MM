package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.isosang.GArenaBounds;
import dev.sangwoo.celestialfall.isosang.GExplosionRules;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlashTest.turn;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lee So-sang's G (2.87.0, the user's G from 2.87.0-preview.25 with what differed from the videos put right): her motion
 * runs smooth from the stance and back to it; it strikes as the cutscene ends and hurts by how far from where the sword
 * strikes; the shots of her hand come out the videos' way round (the arm in from one side, the fingers along it, turned
 * up only before her face); she is slighter and leans away in the sky shot; the cutscene's camera moves smoothly within
 * each shot.
 */
class FallingSwordTest {
    static final Anim CLIP=FallingSword.CLIP;
    static StandClient.Cast cast(){return GGesture.REFERENCE_CAST;}

    @Test void itRunsFromTheStanceBackToIt(){
        var stance=IsosangSlash.stance();
        var first=CLIP.frame(0);var last=CLIP.frame(CLIP.length());
        for(int j=0;j<6;j++){assertTrue(turn(first,stance,j)<.5,"G starts in the stance");assertTrue(turn(last,stance,j)<.5,"G ends in the stance");}
        assertEquals(IsosangRules.LENGTH[IsosangRules.ULTIMATE]/20f,CLIP.length(),1e-6);
        assertSame(CLIP,IsosangSlash.hit(IsosangRules.ULTIMATE),"its swing is drawn with its own clip");
        assertTrue(IsosangClient.life(IsosangRules.ULTIMATE)>=IsosangRules.G_CLEAR,"drawn until the dust has cleared");
    }
    @Test void noJointJumpsOrFlips(){
        double h=1/960.0;
        for(double t=h;t+2*h<=CLIP.length();t+=h){
            var p0=CLIP.frame((float)(t-h));var p1=CLIP.frame((float)t);var p2=CLIP.frame((float)(t+h));var p3=CLIP.frame((float)(t+2*h));
            for(int j=0;j<6;j++){
                double before=turn(p0,p1,j),now=turn(p1,p2,j),after=turn(p2,p3,j);
                assertFalse(now>4&&now>4*Math.max(before,after)+1e-9,"joint "+j+" jumps "+now+" deg at "+t);
            }
        }
        for(double t=0;t+1/60.0<=CLIP.length();t+=1/60.0)
            for(int j=0;j<6;j++)assertTrue(turn(CLIP.frame((float)t),CLIP.frame((float)(t+1/60.0)),j)<60,"joint "+j+" flips at "+t);
    }
    @Test void itFacesTheFront(){
        for(float t=0;t<=CLIP.length();t+=1/120f){
            float yaw=MathHelper.wrapDegrees(CLIP.frame(t).yaw());
            assertTrue(Math.abs(yaw)<=20,"G turns "+yaw+" deg away from the front at "+t);
        }
    }
    @Test void itStrikesAsTheCutsceneEndsAndHurtsByHowFar(){
        // (the user's preview) the server strikes at 97 ticks, the end of the hold; by distance from the strike, squared
        assertEquals(IsosangRules.G_HOLD,GExplosionRules.HIT_TICK);
        // (2.87.1, user request) 200 within 5 blocks, 100 within 15, 50 within 30, 20 further on
        assertEquals(200,GExplosionRules.damage(0));assertEquals(200,GExplosionRules.damage(5*5));
        assertEquals(100,GExplosionRules.damage(5.01*5.01));assertEquals(100,GExplosionRules.damage(15*15));
        assertEquals(50,GExplosionRules.damage(15.01*15.01));assertEquals(50,GExplosionRules.damage(30*30));
        assertEquals(20,GExplosionRules.damage(30.01*30.01));assertEquals(20,GExplosionRules.damage(60*60));
        assertEquals(0,GExplosionRules.damage(Double.NaN));assertEquals(0,GExplosionRules.damage(-1));
        // its ground out of the arena: 62 × 47 blocks round the strike, lying as the garden arena lies (east-west × north-south)
        Vec3d s=new Vec3d(-120.3,72,415.6);var area=GExplosionRules.area(s);
        assertEquals(62,area.maxX()-area.minX(),1e-9);assertEquals(47,area.maxZ()-area.minZ(),1e-9);
        assertEquals(GArenaBounds.GARDEN.maxX()-GArenaBounds.GARDEN.minX(),area.maxX()-area.minX(),1e-9);
        assertEquals(GArenaBounds.GARDEN.maxZ()-GArenaBounds.GARDEN.minZ(),area.maxZ()-area.minZ(),1e-9);
        assertEquals(s.x,(area.minX()+area.maxX())/2,1e-9);assertEquals(s.z,(area.minZ()+area.maxZ())/2,1e-9);
        assertEquals(200,area.damage(s.add(3,0,-3),s));assertEquals(100,area.damage(s.add(0,0,14),s));
        assertEquals(50,area.damage(s.add(-29,0,0),s));assertEquals(50,area.damage(s.add(0,0,-23),s));
        assertEquals(20,area.damage(s.add(30.9,0,0),s),"the ground's far east, past 30 blocks");
        assertEquals(20,area.damage(s.add(-30,0,20),s),"a corner, 36 blocks off");
        assertEquals(0,area.damage(s.add(31.1,0,0),s),"past its east edge");assertEquals(0,area.damage(s.add(0,0,23.6),s),"past its south edge");
        assertEquals(0,area.damage(s.add(0,-20.5,0),s),"too far under");assertEquals(0,area.damage(s.add(0,20.5,0),s),"too far over");
        assertEquals(200,area.damage(s.add(1,12,0),s),"how far is across the ground");
        assertEquals(Math.hypot(31,23.5),area.reach(s),1e-9);
        // a boss reads the same ground (BossThreats, GArenaServer.threat): in its corners, not past its edges
        var zone=area.zone();
        assertTrue(zone.covers(s,0));assertTrue(zone.covers(s.add(30.9,0,23.4),0));assertTrue(zone.covers(s.add(-30.9,19.9,-23.4),0));
        assertFalse(zone.covers(s.add(31.5,0,0),.3));assertFalse(zone.covers(s.add(0,0,-24),.3));assertFalse(zone.covers(s.add(0,-20.5,0),.3));
        // in the garden's arena: everyone in it, by how far, the same steps
        var arena=GArenaBounds.GARDEN;Vec3d a=new Vec3d(30,81,-50);
        assertEquals(200,arena.damage(a.add(3,0,0),a));assertEquals(100,arena.damage(a.add(10,0,0),a));
        assertEquals(50,arena.damage(a.add(20,0,0),a));assertEquals(20,arena.damage(a.add(30,0,-10),a));
        assertEquals(0,arena.damage(new Vec3d(100,81,-50),a),"outside the arena");
    }
    @Test void theFireLeapsUpAfterTheBlack(){
        // the screen black after the cut, lifting just before the fire (the videos'), all within the cast's life
        assertTrue(FallingSword.AFTER_BLACK>IsosangRules.G_BACK&&FallingSword.AFTER_LIT<=IsosangRules.G_ERUPT);
        assertTrue(FallingSword.FIRE<FallingSword.AFTER_LIT&&FallingSword.FIRE>FallingSword.AFTER_BLACK,"the fire leaps up as the black lifts");
        assertTrue(IsosangClient.life(IsosangRules.ULTIMATE)>IsosangRules.G_ERUPT+2);
    }

    /** Where {@code p} is in a shot's 21:9 frame ({x, y} from 0 to 1, left to right and top to bottom), the camera's roll turned in, or null behind it. */
    static double[] inFrame(FallingSword.Shot s,Vec3d p,boolean mirrored){
        Vec3d f=s.look().subtract(s.eye()).normalize(),r=f.crossProduct(new Vec3d(0,1,0)).normalize(),u=r.crossProduct(f);
        Vec3d d=p.subtract(s.eye());double z=d.dotProduct(f);if(z<=0)return null;
        double th=Math.tan(Math.toRadians(s.fov()/2)),aspect=FallingSword.ASPECT;
        double x=d.dotProduct(r)/z/th,y=d.dotProduct(u)/z/th;
        double a=Math.toRadians(s.roll()),x2=x*Math.cos(a)-y*Math.sin(a),y2=x*Math.sin(a)+y*Math.cos(a);
        double sx=.5+x2/aspect/2;if(mirrored)sx=1-sx;
        return new double[]{sx,.5-y2/2};
    }
    /** Her right wrist and the tip of her two fingers at {@code t} (the drawn hand's, GGesture), for the reference cast. */
    static Vec3d[] hand(float t){
        var frame=CLIP.frame(t);var pose=R6.poseOf(frame);float k=(float)Math.PI/180;
        float[] joint=frame.joints().joint[3];float blend=GGesture.seal(t);
        var upper=new Quaternionf().rotationZYX(joint[2]*k,joint[1]*k,joint[0]*k);
        var lower=new Quaternionf(upper).rotateX(frame.joints().elbow[1]*k);
        var chain=GGesture.solve(GGesture.LEFT_SHOULDER,GGesture.SEAL_WRIST,4,new Vector3f(8,7,-1));
        upper.slerp(chain.upper(),blend).normalize();lower.slerp(chain.lower(),blend).normalize();
        var fingers=new Quaternionf(lower).mul(GGesture.wristPose(lower,t));
        Vector3f elbow=new Vector3f(GGesture.LEFT_SHOULDER).add(upper.transform(new Vector3f(0,4,0)));
        Vector3f wrist=new Vector3f(elbow).add(lower.transform(new Vector3f(1,4,0)));
        Vector3f tip=new Vector3f(wrist).add(fingers.transform(new Vector3f(-.675f,4.175f,-.12f)));
        Vec3d[] out=new Vec3d[3];Vector3f[] ps={elbow,wrist,tip};var c=cast();
        Vec3d f=FallingSword.forward(c),right=FallingSword.right(f),left=right.negate();
        for(int i=0;i<3;i++){
            Vector3f m=new Vector3f(ps[i]);m.x=-m.x;double scale=.9375;
            Vec3d local=right.multiply(-m.x/16*scale).add(0,(1.501-m.y/16)*scale-KagenoClips.PIVOT,0).add(f.multiply(-m.z/16*scale));
            var q=new Quaternionf().rotationAxis(pose.yaw*k,0,1,0).rotateAxis(pose.pitch*k,(float)left.x,0,(float)left.z).rotateAxis(pose.roll*k,(float)f.x,0,(float)f.z);
            Vector3f v=q.transform(new Vector3f((float)local.x,(float)local.y,(float)local.z));
            out[i]=c.origin().add(f.multiply(pose.fw)).add(right.multiply(pose.rt)).add(v.x,pose.up+KagenoClips.PIVOT+v.y,v.z);
        }
        return out;
    }
    @Test void herHandIsShotTheVideosWayRound(){
        var c=cast();
        assertTrue(FallingSword.mirrored(.2f)&&FallingSword.mirrored(.9f)&&!FallingSword.mirrored(1.1f),"only the shots of her hand are turned back");
        // beside her hand: the fingers run on along the arm and point to the frame's left, the arm in from its right
        for(float t:new float[]{.18f,.25f}){
            var s=FallingSword.shot(c,t);Vec3d[] h=hand(t);
            double[] elbow=inFrame(s,h[0],true),wrist=inFrame(s,h[1],true),tip=inFrame(s,h[2],true);
            assertTrue(tip[0]<wrist[0]-.2&&wrist[0]<elbow[0],"the fingers to the left of the hand, the arm to its right at "+t+": "+tip[0]+" "+wrist[0]+" "+elbow[0]);
            assertTrue(tip[0]>.05&&tip[1]>.3&&tip[1]<.75,"the fingertips in the frame's left half, at its middle height: "+tip[0]+" "+tip[1]);
        }
        // under the arm: the arm in from the lower left, the fingertips up near the upper right
        for(float t:new float[]{.55f,.65f,.75f}){
            var s=FallingSword.shot(c,t);Vec3d[] h=hand(t);
            double[] elbow=inFrame(s,h[0],true),wrist=inFrame(s,h[1],true),tip=inFrame(s,h[2],true);
            assertTrue(elbow[0]<wrist[0]&&wrist[0]<tip[0]&&elbow[1]>wrist[1]&&wrist[1]>tip[1],"the arm up from the lower left at "+t);
            assertTrue(tip[0]>.6&&tip[1]<.3&&tip[1]>0,"the fingertips at the upper right: "+tip[0]+" "+tip[1]);
        }
        // the fingers turned up only as the hand comes before her face, and held upright at her lips
        assertEquals(0,GGesture.uprightWeight(.6f),1e-6);assertEquals(1,GGesture.uprightWeight(1.2f),1e-6);
    }
    @Test void inTheSkySheStandsUpFromTheFramesBottom(){
        // (2.87.2, user request) her legs out through the frame's bottom edge, her feet below the frame: nothing of her
        // floating in the sky; (2.87.3, user request) 30 % smaller, moved left, her face at about (0.30, 0.68), her legs out
        // at about 0.23 across, cut at the shin, her front turned further to the frame's right
        var c=cast();
        for(float t:new float[]{1.7f,2.2f,2.7f}){
            var shot=GScene.skyShot(c,t);Vec3d feet=c.origin();
            Matrix4f m=GScene.actor(c,t,feet);
            double[] face=inFrame(shot,apply(m,feet,feet.add(0,1.75,0)),false),soles=inFrame(shot,apply(m,feet,feet),false);
            assertEquals(.296,face[0],.015,"her face across at "+t);assertEquals(.675,face[1],.015,"her face down at "+t);
            assertTrue(soles==null||soles[1]>1.03,"her feet below the frame at "+t+": "+(soles==null?"behind":soles[1]));
            double[] above=null;double cutAt=Double.NaN;
            for(double h=.75;h>=0;h-=.01){
                double[] q=inFrame(shot,apply(m,feet,feet.add(0,h,0)),false);
                if(q!=null&&q[1]<1){above=q;continue;}
                cutAt=h;break;
            }
            assertNotNull(above);assertTrue(cutAt>.08&&cutAt<.4,"the frame's bottom edge cuts her legs above her ankles at "+t+": "+cutAt);
            assertEquals(.23,above[0],.03,"her legs leave the frame at about 0.23 across at "+t);
            assertTrue(above[0]<face[0]-.03,"her legs to the lower left of her face at "+t);
            // her front turned to the frame's right (draft B): her front, from her chest, goes to the frame's right
            Vec3d f=FallingSword.forward(c),chest=feet.add(0,1.1,0);
            double[] at=inFrame(shot,apply(m,feet,chest),false),ahead=inFrame(shot,apply(m,feet,chest.add(f.multiply(.5))),false);
            assertTrue(ahead[0]>at[0]+.02,"her front to the frame's right at "+t+": "+at[0]+" "+ahead[0]);
        }
    }
    static Vec3d apply(Matrix4f m,Vec3d origin,Vec3d p){
        Vector4f v=m.transform(new Vector4f((float)(p.x-origin.x),(float)(p.y-origin.y),(float)(p.z-origin.z),1));
        return origin.add(v.x,v.y,v.z);
    }
    @Test void theCameraMovesSmoothlyWithinEachShot(){
        // cuts only where the videos cut: into the face (1.008 s) and into the fall (2.783 s); elsewhere no jump
        var c=cast();double h=1/60.0;
        float[] cuts={1.008f,IsosangRules.G_FALL};
        for(double t=0;t+h<IsosangRules.G_BACK;t+=h){
            boolean cut=false;for(float k:cuts)if(t<k&&t+h>=k)cut=true;
            // the face pulled away into the sky in three frames (1.6 to 1.65 s, the videos' whip out of her face)
            // and the fall shot riding down with the sword until it strikes (the user's preview: 32 blocks in a third of a second)
            if(cut||t+h>1.6&&t<1.65||t+h>IsosangRules.G_FALL&&t<IsosangRules.G_STRIKE+.02)continue;
            var a=FallingSword.shot(c,(float)t);var b=FallingSword.shot(c,(float)(t+h));
            double move=a.eye().distanceTo(b.eye());
            // the whip round under the arm is meant to be fast (the videos': a few frames), and the strike jolts the camera
            double limit=t>3.05&&t<3.4?.8:t>.3&&t<.45?.5:.35;
            assertTrue(move<limit,"the camera jumps "+move+" blocks at "+t);
            assertTrue(Math.abs(a.fov()-b.fov())<6,"the lens jumps at "+t);
        }
    }
}
