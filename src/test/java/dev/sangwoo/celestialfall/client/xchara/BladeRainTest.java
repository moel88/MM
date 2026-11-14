package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.xchara.RainRules;
import dev.sangwoo.celestialfall.xchara.ShotRules;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's H with their hack off, the rain of knives, as drawn (2.132.5, the user's: "20블록으로 줄이고 떨어지는 칼들도
 * 대폭 줄이고 떨어지는 속도도 줄여 진짜 비 처럼 만들어 그리고 적을 찌르는건 없애고 그냥 비만 떨어지게 비는 30초동안 계속
 * 떨어져"; the screen inside it "은은하게 30초 내내"): the call raises the knife straight up to the sky and brings it down,
 * nothing jumping; the knives fall straight down slowly out of the sky, far fewer at once than 2.132.4's, and stand a
 * moment where they land; cut short, none comes and what falls is gone; the circle in the sky opens before the first and
 * closes after the last; what the camera cannot see is not drawn; the screen inside it is gentle and for whoever stands
 * in it. (No world in the tests: the ground is flat at the rain's middle.)
 */
class BladeRainTest {
    static final Vec3d MIDDLE=new Vec3d(10,64,5);static final float NEVER=Float.MAX_VALUE;static final int SEED=4242;
    static RainClient.Rain rain(){
        UUID id=new UUID(1,2),owner=new UUID(3,4);
        var cast=new StandClient.Cast(id,owner,new Identifier("minecraft","overworld"),RainRules.SKILL,0,1000,1000,0,MIDDLE,0,List.of());
        return new RainClient.Rain(id,owner,MIDDLE,SEED,cast);
    }
    static RainFx.Pose pose(RainClient.Rain r,int k,float age,float cut){
        if(!r.knives.known.get(k))r.knives.learn(r,k);
        return RainFx.pose(r,k,age,cut,List.of(),0);
    }

    @Test void theCallRaisesTheKnifeStraightUpAndBringsItDown(){
        var clip=BladeRain.CALL;
        assertSame(clip,KnifeCombo.clipOf(RAIN));
        assertEquals(LENGTH[RAIN]/20f,clip.length(),1e-4);assertEquals(BladeRain.LIFE,KnifeCombo.life(RAIN),1e-6);
        var rest=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(KnifeCombo.AT_REST));
        for(float t:new float[]{0,clip.length()}){
            var b=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(clip.frame(t)));
            assertEquals(0,rest[0].distanceTo(b[0]),1e-4,"from the stance and back to it");assertEquals(0,rest[1].distanceTo(b[1]),1e-4);
        }
        // straight up at the sky as the rain is called, the hand over the head
        var up=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(clip.frame(BladeRain.CALL_AT)));
        assertTrue(up[1].y>.97,"the knife straight up: "+up[1]);assertTrue(up[0].y>1.95,"the hand over the head: "+up[0].y);
        assertTrue(up[0].add(up[1].multiply(ShotRules.TIP)).y>4,"its point well over them");
        // held up a moment, then down before the body
        assertTrue(KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(clip.frame(.5f)))[1].y>.95);
        assertTrue(KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(clip.frame(.88f)))[1].y<.4);
        // the head back to it
        assertTrue(R6.poseOf(clip.frame(BladeRain.CALL_AT)).headPitch+clip.frame(BladeRain.CALL_AT).pitch()<-25,"looking up at it");
    }
    @Test void nothingOfTheCallJumpsAndTheKnifeKeepsOffTheGround(){
        var clip=BladeRain.CALL;Vec3d lastFist=null,lastHand=null,lastTip=null;float lastPitch=0;
        for(float t=0;t<=clip.length();t+=1/60f){
            var fr=clip.frame(t);var p=R6.poseOf(fr);var b=KagenoClips.blade(Vec3d.ZERO,0,p);Vec3d hand=KagenoClips.freeHand(Vec3d.ZERO,0,p);
            Vec3d tip=b[0].add(b[1].multiply(ShotRules.TIP));
            assertTrue(tip.y>.1,"the knife's point clear of the ground at "+t+": "+tip.y);
            if(lastFist!=null){
                assertTrue(b[0].distanceTo(lastFist)<.3,"the knife's hand at "+t+": "+b[0].distanceTo(lastFist));
                assertTrue(hand.distanceTo(lastHand)<.3,"the free hand at "+t+": "+hand.distanceTo(lastHand));
                assertTrue(tip.distanceTo(lastTip)<.75,"the knife's point at "+t+": "+tip.distanceTo(lastTip));
                assertTrue(Math.abs(fr.pitch()-lastPitch)<6,"the body at "+t);
            }
            lastFist=b[0];lastHand=hand;lastTip=tip;lastPitch=fr.pitch();
        }
    }
    @Test void theKnifeIsLitAsItIsRaisedAndGoesOutAsItIsBroughtDown(){
        assertNull(BladeRain.lit(BladeRain.LIT_AT-.01f,Float.NaN));
        assertEquals(1,BladeRain.lit(BladeRain.CALL_AT,Float.NaN)[0]);
        assertEquals(0,BladeRain.lit(BladeRain.DOWN_AT+.05f,Float.NaN)[0],"going out as it comes down");
        assertNull(BladeRain.lit(BladeRain.DOWN_AT+.5f,Float.NaN));
        assertEquals(0,BladeRain.lit(.3f,.2f)[0],"stopped short: it goes out then");assertNull(BladeRain.lit(.04f,.02f));
    }
    @Test void theKnivesFallStraightDownSlowlyAndStandAMomentWhereTheyLand(){
        var r=rain();
        for(int k:new int[]{0,321,4000,RainRules.BLADES-1}){
            float comes=(float)RainRules.comes(k);
            assertNull(pose(r,k,comes-.01f,NEVER),"none before it comes");
            double sky=MIDDLE.y+RainRules.sky(k,SEED),land=MIDDLE.y-RainFx.GROUND_DEEP;
            float lands=(float)(comes+(sky-land)/RainRules.SPEED);
            Vec3d last=null;
            for(float a=comes+.05f;a<lands+RainFx.STUCK;a+=1/3f){
                var p=pose(r,k,a,NEVER);if(p==null){assertTrue(a>lands+RainFx.STUCK-RainFx.GOING,"gone only as it goes: "+a);continue;}
                Vec3d tip=p.tip();
                double[] s=RainRules.spot(k,SEED);
                assertEquals(MIDDLE.x+s[0],tip.x,1e-6);assertEquals(MIDDLE.z+s[1],tip.z,1e-6);
                if(a<lands-.01f){
                    assertTrue(p.flying());assertEquals(-1,p.dir().y,1e-9,"straight down");
                    assertEquals(Math.max(land,sky-(a-RainRules.comes(k))*RainRules.SPEED),tip.y,1e-5);
                }else{assertTrue(p.in(),"in the ground where it landed");assertEquals(land,tip.y,1e-6);}
                if(last!=null)assertTrue(last.y-tip.y<=RainRules.SPEED/3+1e-4,"slow: no further a frame than it falls: "+(last.y-tip.y)+" at "+(a-comes)+" of "+(lands-comes));
                last=tip;
            }
            assertNull(pose(r,k,lands+RainFx.STUCK+.01f,NEVER),"gone a moment after it landed");
        }
    }
    @Test void farFewerKnivesInTheAirAtOnceThanBefore(){
        var r=rain();
        int most=0;
        for(float age:new float[]{RainRules.OPEN+200,RainRules.OPEN+300.3f,RainRules.OPEN+450.7f}){
            int n=RainFx.shown(r,age);most=Math.max(most,n);
            assertTrue(n>200&&n<500,"some 350 at once in the thick of it (2.132.4: some 2,270): "+n+" at "+age);
        }
        assertTrue(RainFx.shown(r,RainRules.OPEN+3)<60,"coming on: "+RainFx.shown(r,RainRules.OPEN+3));
        assertEquals(0,RainFx.shown(r,RainRules.END+RainFx.STUCK+1),"all of them gone by its end");
    }
    @Test void cutShortNoneComesAndWhatFallsIsGone(){
        var r=rain();float cut=RainRules.OPEN+100;
        int before=RainFx.shown(r,cut-.01f);assertTrue(before>100);
        r.cut=cut;
        // what falls is gone over VANISH; what had landed stands its moment out
        int[] w=RainRules.window(cut,RainRules.LAST-RainRules.POUR);
        for(int k=w[0];k<w[1];k++){var p=pose(r,k,cut+RainFx.VANISH+.01f,cut);assertTrue(p==null||p.in(),"none falling after it: "+k);}
        assertEquals(0,RainFx.shown(r,cut+RainFx.STUCK+.01f),"all gone a moment after it");
        for(int k=0;k<RainRules.BLADES;k++){if(RainRules.comes(k)<cut)continue;assertNull(pose(r,k,(float)RainRules.comes(k)+1,cut),"none comes after it");break;}
    }
    @Test void theCircleInTheSkyOpensBeforeTheFirstKnifeAndClosesAfterTheLast(){
        assertEquals(0,RainFx.skyOn(0,NEVER),1e-6);
        assertEquals(1,RainFx.skyOn(RainRules.OPEN+2,NEVER),1e-6,"open as the first knives come");
        assertEquals(1,RainFx.skyOn(RainRules.OPEN+RainRules.POUR,NEVER),1e-6);
        assertEquals(0,RainFx.skyOn(RainRules.OPEN+RainRules.POUR+12,NEVER),1e-6,"closed after the last has come");
        assertEquals(0,RainFx.skyOn(112,100),1e-6,"cut short: closed after it");
        assertTrue(RainFx.SKY_UP>RainRules.SKY+RainRules.SKY_MORE,"over where they come out");
        float prev=0,most=0;
        for(float a=0;a<RainRules.END;a+=1/3f){float v=RainFx.skyOn(a,NEVER);most=Math.max(most,Math.abs(v-prev));prev=v;}
        assertTrue(most<=.1,"opening and closing over frames: "+most);
    }
    /** (2.132.4's) What the camera cannot see is not drawn; all it can is. */
    @Test void whatTheCameraCannotSeeIsNotDrawnAndAllItCanIs(){
        // a camera behind its user, standing in the middle of the rain: 16:9, 70 degrees up and down
        double ty=Math.tan(Math.toRadians(35)),tx=ty*16/9.0;
        Vec3d eye=MIDDLE.add(-4,2.6,0),ahead=MIDDLE.add(4,1,0).subtract(eye).normalize();
        Vec3d left=new Vec3d(0,1,0).crossProduct(ahead).normalize(),up=ahead.crossProduct(left).normalize();
        var view=new RainFx.View(eye,ahead,up,tx*RainFx.WIDER,ty*RainFx.WIDER);
        var rng=new java.util.Random(7);
        for(int i=0;i<20000;i++){
            Vec3d p=eye.add((rng.nextDouble()-.5)*80,(rng.nextDouble()-.5)*40,(rng.nextDouble()-.5)*80);
            Vec3d d=p.subtract(eye);double z=d.dotProduct(ahead),x=d.dotProduct(left),y=d.dotProduct(up);
            boolean onScreen=z>.05&&Math.abs(x)<=z*tx&&Math.abs(y)<=z*ty;
            if(onScreen)assertTrue(view.sees(p,0),"on the screen, so seen: "+p);
            if(z<-2||Math.abs(x)>z*tx*RainFx.WIDER+3||Math.abs(y)>z*ty*RainFx.WIDER+3)assertFalse(view.sees(p,1),"well off the screen, not: "+p);
        }
        Vec3d mid=eye.add(ahead.multiply(10));
        assertTrue(view.sees(mid.add(left.multiply(30)),mid.subtract(left.multiply(30)),.5));
        // through the rain: well under all of them are drawn, a good share are
        var r=rain();int all=0,seen=0;float t=RainRules.OPEN+300;
        int[] w=RainRules.window(t,RainRules.LAST-RainRules.POUR);
        for(int k=w[0];k<w[1];k++){
            var p=pose(r,k,t,NEVER);if(p==null)continue;all++;
            Vec3d tip=p.tip(),top=p.grip().add(0,p.flying()?RainFx.STREAK+RainFx.STREAK_MORE:0,0);
            if(view.sees(tip,top,RainFx.KNIFE_REACH))seen++;
        }
        assertTrue(all>200&&seen<all*.7,"drawn: "+seen+" of "+all);
        assertTrue(seen>all*.15,"those ahead of it are: "+seen+" of "+all);
    }
    /** (2.132.2's) Knives come down by cameras, and fade there, not cutting across the lens; drawn round the knife's own outline with fewer points. */
    @Test void aKnifeFadesByTheCameraNotAcrossTheLens(){
        Vec3d camera=new Vec3d(0,66,0);
        assertEquals(0,RainFx.near(new Vec3d(.5,60,0),new Vec3d(.5,70,0),camera),1e-6,"its line passing half a block from the lens: none of it");
        assertEquals(0,RainFx.near(new Vec3d(1.9,64,0),new Vec3d(1.9,67,0),camera),1e-6,"(2.132.5) two blocks off: none of it");
        assertEquals(1,RainFx.near(new Vec3d(5.6,60,0),new Vec3d(5.6,70,0),camera),1e-6,"(2.132.5) five and a half blocks off: all of it");
        float last=0;
        for(double x=0;x<=6;x+=.01){float v=RainFx.near(new Vec3d(x,60,0),new Vec3d(x,70,0),camera);assertTrue(v>=last-1e-6,"coming in as it is further off");last=v;}
        double fine=Math.abs(HackKnife.area2(HackKnife.OUTLINE));
        for(var o:new double[][][]{RainFx.OUTLINE,RainFx.OUTLINE_FAR}){
            double coarse=Math.abs(HackKnife.area2(o));
            assertTrue(Math.abs(coarse/fine-1)<(o==RainFx.OUTLINE?.02:.05),"fewer points, the same shape: "+o.length+" points, "+coarse/fine+" of its area");
        }
        double farthest=Math.abs(HackKnife.area2(RainFx.OUTLINE_FARTHEST));
        assertTrue(farthest/fine>.9&&farthest/fine<1.0001,"the farthest outline: "+farthest/fine+" of its area");
    }
    /** (2.132.4's) One far off is drawn with fewer points on only the one of its two planes more across the camera's sight. */
    @Test void aKnifeFarOffIsDrawnLighterOnThePlaneFacingTheCamera(){
        var r=rain();float t=(float)RainRules.comes(7)+5;
        var p=pose(r,7,t,NEVER);assertTrue(p!=null&&p.flying());
        float roll=30;Vec3d y=p.dir(),sp=BladeShot.spine(y,roll),across=y.crossProduct(sp).normalize();
        var buf=new net.minecraft.client.render.BufferBuilder(1<<16);
        int[][] count=new int[3][2];
        for(int lod=0;lod<3;lod++)for(int l=0;l<2;l++){
            buf.begin(net.minecraft.client.render.VertexFormat.DrawMode.QUADS,net.minecraft.client.render.VertexFormats.POSITION_COLOR);
            var m=new dev.sangwoo.celestialfall.client.EffectMesh(buf,new org.joml.Matrix4f(),p.grip().add(across.multiply(20)));
            RainFx.blade(m,p,roll,1,l==1,lod,p.grip().add(across.multiply(20)));
            var b=buf.end();count[lod][l]=b.getParameters().vertexCount();b.release();
        }
        assertEquals(2*4*(3*RainFx.OUTLINE.length-2),count[0][0],"near: both planes, the fan, the rod and two bands of rim");
        assertEquals(2*4*RainFx.OUTLINE.length,count[0][1],"near: the glow round its outline on both planes");
        assertEquals(4*(2*RainFx.OUTLINE_FAR.length-2),count[1][0],"far: one plane, one band of rim");
        assertEquals(8,count[1][1],"far: its glow one strip along it");
        assertTrue(count[1][0]+count[1][1]<(count[0][0]+count[0][1])/6,"far off it costs a sixth of near");
    }
    /** (2.132.5, the user's "은은하게 30초 내내") The screen inside the rain is for whoever stands in it (their body), all the while it rains, coming and going smoothly. */
    @Test void theScreenInsideTheRainIsForWhoeverStandsInItAllTheWhile(){
        float mid=RainRules.OPEN+300;
        assertEquals(1,RainFx.inside(MIDDLE,MIDDLE,mid,NEVER),1e-6,"in the middle of it");
        assertEquals(1,RainFx.inside(MIDDLE.add(RainRules.ZONE-1.6,0,0),MIDDLE,mid,NEVER),1e-6,"just inside its edge");
        assertEquals(0,RainFx.inside(MIDDLE.add(0,0,RainRules.ZONE+.6),MIDDLE,mid,NEVER),1e-6,"outside it: none");
        assertEquals(0,RainFx.inside(MIDDLE.add(0,RainFx.SKY_UP+1.1,0),MIDDLE,mid,NEVER),1e-6,"over its circle: none");
        assertEquals(1,RainFx.inside(MIDDLE,MIDDLE,RainRules.OPEN+20,NEVER),1e-6,"from as the knives come");
        assertEquals(1,RainFx.inside(MIDDLE,MIDDLE,RainRules.END,NEVER),1e-6,"until its end");
        assertEquals(0,RainFx.inside(MIDDLE,MIDDLE,RainRules.END+16.01f,NEVER),1e-6,"gone a little after");
        assertEquals(0,RainFx.inside(MIDDLE,MIDDLE,120,100),1e-6,"cut short: gone after it");
        float prev=0,most=0;
        for(float a=0;a<RainRules.END+20;a+=1/3f){float v=RainFx.inside(MIDDLE,MIDDLE,a,NEVER);most=Math.max(most,Math.abs(v-prev));prev=v;}
        assertTrue(most<=.05,"it comes and goes over frames, not at once: at most "+most+" a frame");
    }
    @Test void itsSoundsAreOnesTheGameHas(){
        assertEquals("xchara_slam_lit",BladeRain.CALL_SOUND,"the slam's knife lighting (a sound the game has)");
        // its burst is some 0.19 s into its file: on the call
        assertEquals(RainRules.CALL,BladeRain.CALL_SOUND_AT+4);
        assertEquals("xchara_shot_hit",RainClient.STRIKE_SOUND);assertTrue(RainClient.STRIKE_PART>0&&RainClient.STRIKE_PART<1);
    }
}
