package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.meteor.MiningPose;
import dev.sangwoo.celestialfall.combat.MotionPose;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.client.stand.ArrowStabPose.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The stand arrow's stab (2.70.0; about ten seconds since 2.71.0): smooth at 60 fps, the fists where they go, the arrow
 * in the hands and then in the chest (never through the head or the arms), the knees and the fall onto the ground.
 */
class ArrowStabPoseTest {
    static final float RAD=(float)Math.PI/180,FRAME=1/60f;
    static final Fate[] FATES={Fate.AWAKEN,Fate.DIE};

    /** Each joint's turn from one frame to the next (degrees, the angle between the two rotations). */
    static float turn(float[] a,float[] b){
        var qa=new Quaternionf().rotationZYX(a[2]*RAD,a[1]*RAD,a[0]*RAD);var qb=new Quaternionf().rotationZYX(b[2]*RAD,b[1]*RAD,b[0]*RAD);
        float d=Math.abs(qa.dot(qb));return 2*(float)Math.acos(Math.min(1,d))/RAD;
    }
    static float worst(MotionPose a,MotionPose b){
        float w=0;
        for(int j=0;j<6;j++)w=Math.max(w,turn(a.joint[j],b.joint[j]));
        for(int i=0;i<2;i++){w=Math.max(w,Math.abs(a.elbow[i]-b.elbow[i]));w=Math.max(w,Math.abs(a.knee[i]-b.knee[i]));}
        return w;
    }

    static String which(MotionPose a,MotionPose b){
        var o=new StringBuilder();String[] names={"head","body","rarm","larm","rleg","lleg"};
        for(int j=0;j<6;j++)o.append(names[j]).append(String.format(" %.1f ",turn(a.joint[j],b.joint[j])));
        o.append(String.format("elbows %.1f %.1f knees %.1f %.1f",a.elbow[0]-b.elbow[0],a.elbow[1]-b.elbow[1],a.knee[0]-b.knee[0],a.knee[1]-b.knee[1]));
        return o.toString();
    }
    @Test void noJointJumpsAFrameAndTheBodyNeverTeleports(){
        for(var fate:FATES)for(float known:new float[]{REVEAL,REVEAL+.15f,REVEAL+.4f}){
            Frame prev=frame(0,fate,known);float worst=0,at=0,move=0;
            for(float t=FRAME;t<(fate==Fate.DIE?DEATH+1:FREE+.5f);t+=FRAME){
                var f=frame(t,fate,known);
                float w=worst(prev.pose(),f.pose());if(w>worst){worst=w;at=t;}
                if(w>30)System.out.printf("  %.3f s: %s%n",t,which(prev.pose(),f.pose()));
                move=Math.max(move,Math.abs(f.pose().bob-prev.pose().bob)+Math.abs(f.pose().lean-prev.pose().lean));
                prev=f;
            }
            System.out.printf("%s known %.2f: worst joint turn %.1f° at %.3f s, hips %.2f px/frame%n",fate,known,worst,at,move);
            assertTrue(worst<40,fate+" turns "+worst+"° in a frame at "+at);
            assertTrue(move<1.6f,fate+" hips move "+move+" px in a frame");
        }
    }
    /** Every key's fists are within the arms' reach: the solved fists close where the keys (and what shakes them) put them. */
    @Test void theFistsReachWhereTheyGo(){
        float worst=0,at=0;
        for(float t=0;t<=REVEAL;t+=.01f){
            var f=frame(t,Fate.UNKNOWN,REVEAL);float[] k=keys(t),s=shake(t,Fate.UNKNOWN,REVEAL);
            float r=f.rightFist().distance(k[9]+s[4],k[10]+s[5],k[11]+s[6]),l=f.leftFist().distance(k[12]+s[7],k[13]+s[8],k[14]+s[9]);
            if(Math.max(r,l)>worst){worst=Math.max(r,l);at=t;}
        }
        for(float t:TIMES){var f=frame(t,Fate.UNKNOWN,REVEAL);System.out.printf("t %.2f right fist %s left fist %s%n",t,f.rightFist(),f.leftFist());}
        System.out.printf("the fists fall short of where they go by %.2f px at most (%.2f s)%n",worst,at);
        assertTrue(worst<.35f,"a fist falls "+worst+" px short at "+at);
    }
    @Test void theArrowIsInTheHandsThenInTheChest(){
        // in the fists until it goes in: its grip (between them once both hold it) where the fists close
        for(float t=0;t<HIT;t+=.01f){
            var f=frame(t,Fate.UNKNOWN,REVEAL);var a=f.arrow();assertNotNull(a);
            Vector3f grip=a.at(GRIP_U);
            float toRight=grip.distance(f.rightFist());
            assertTrue(toRight<CLASP/2+.6f,"at "+t+" the grip is "+toRight+" px from the right fist");
            // into the chest only where it goes in: once the tip is in, its line crosses the chest's front there
            Vector3f tip=a.tip();
            boolean inBody=Math.abs(tip.x)<4&&tip.y>0&&tip.y<12&&tip.z>-2;
            if(inBody)assertTrue(a.entry().distance(ENTRY)<.7f,"at "+t+" it goes in at "+a.entry());
        }
        var hit=frame(HIT,Fate.UNKNOWN,REVEAL).arrow();
        System.out.printf("goes in: tip %s way %s entry %s nock %s%n",hit.tip(),hit.way(),hit.entry(),hit.nock());
        assertEquals(DEPTH,hit.tip().distance(hit.entry()),.05f,"the tip is in the chest");
        assertTrue(hit.tip().z<1.6f,"not out of the back");
        // then it stays where it went in, and is drawn in and gone before its choice shows
        for(float t=HIT;t<REVEAL+.5f;t+=.01f){
            var a=frame(t,Fate.UNKNOWN,REVEAL).arrow();
            if(t>=SINK_TO){assertNull(a,"gone at "+t);continue;}
            assertNotNull(a);assertEquals(0,a.tip().distance(hit.tip()),1e-4f);
        }
    }
    /**
     * The arrow's shaft and head stay out of the forearms: held, from a fist's edge out to the tip; standing out of the
     * chest once the hands have left it (the gasp on), all of it.
     */
    @Test void theArrowStaysOutOfTheForearms(){
        float least=99,at=0;
        for(float t=.05f;t<SINK_TO;t+=.01f){
            if(t>=HIT&&t<GASP)continue;
            var f=frame(t,Fate.UNKNOWN,REVEAL);var a=f.arrow();if(a==null)continue;
            var p=f.pose();
            for(int side=0;side<2;side++){
                float lift=side==0?f.liftR():f.liftL();
                Vector3f shoulder=new Vector3f(side==0?-5:5,2-lift,0);
                float[] arm=p.joint[side==0?MotionPose.RIGHT_ARM:MotionPose.LEFT_ARM];
                float[] r=new float[]{arm[0],arm[1],arm[2],p.elbow[side]};
                // the forearm's axis: from the elbow to the fist
                var q=new Quaternionf().rotationZYX(arm[2]*RAD,arm[1]*RAD,arm[0]*RAD);
                Vector3f elbow=q.transform(new Vector3f(side==0?-1:1,4,0)).add(shoulder);
                Vector3f fist=MiningPose.fist(shoulder,r,side==0?-1:1);
                // the arrow beyond the fists (2.2 px past the grip) out to its tip, or all of it once let go
                for(float u=t<HIT?GRIP_U+4.4f:NOCK_U;u<=TIP_U;u+=.5f){
                    Vector3f pt=a.at(u);
                    float d=segment(pt,elbow,fist);
                    if(d<least){least=d;at=t;}
                }
            }
        }
        System.out.printf("the arrow comes within %.2f px of a forearm's axis (at %.2f s)%n",least,at);
        assertTrue(least>1.4f,"the arrow runs through a forearm at "+at);
    }
    static float segment(Vector3f p,Vector3f a,Vector3f b){
        Vector3f ab=new Vector3f(b).sub(a);float u=Math.max(0,Math.min(1,new Vector3f(p).sub(a).dot(ab)/ab.lengthSquared()));
        return new Vector3f(ab).mul(u).add(a).distance(p);
    }
    /** The arrow never goes through the head (the head's box as the pose turns it), held or standing out of the chest. */
    @Test void theArrowStaysOutOfTheHead(){
        for(float t=0;t<SINK_TO;t+=.01f){
            var f=frame(t,Fate.UNKNOWN,REVEAL);var a=f.arrow();var p=f.pose();if(a==null)continue;
            float[] h=p.joint[MotionPose.HEAD];
            var q=new Quaternionf().rotationZYX(h[2]*RAD,h[1]*RAD,h[0]*RAD).invert();
            // the head turns about the neck (model space): the arrow's points in the head's own frame
            Vector3f neck=toModel(p,new Vector3f());
            for(float u=NOCK_U;u<=TIP_U;u+=.5f){
                Vector3f m=toModel(p,a.at(u)).sub(neck);q.transform(m);
                boolean in=Math.abs(m.x)<3.9f&&m.y>-7.9f&&m.y<-.1f&&Math.abs(m.z)<3.9f;
                assertFalse(in,"at "+t+" the arrow ("+u+") is in the head");
            }
        }
    }
    /** How far a point (model px) is from the posed head's box (0 inside). */
    static float fromHead(MotionPose p,Vector3f model){
        float[] h=p.joint[MotionPose.HEAD];
        Vector3f m=new Vector3f(model).sub(toModel(p,new Vector3f()));
        new Quaternionf().rotationZYX(h[2]*RAD,h[1]*RAD,h[0]*RAD).invert().transform(m);
        float dx=Math.max(0,Math.abs(m.x)-4),dy=Math.max(0,Math.max(-8-m.y,m.y)),dz=Math.max(0,Math.abs(m.z)-4);
        return (float)Math.sqrt(dx*dx+dy*dy+dz*dz);
    }
    /** Points along each arm's axis (model px): the upper arm from its pivot to the elbow, then the forearm to the hand's end. */
    static java.util.List<Vector3f> armAxis(Frame f,int s){
        var p=f.pose();var out=new java.util.ArrayList<Vector3f>();
        float lift=s==0?f.liftR():f.liftL();float side=s==0?-1:1;
        float[] arm=p.joint[MotionPose.RIGHT_ARM+s];float[] b=p.joint[MotionPose.BODY];
        var torso=new Quaternionf().rotationZYX(b[2]*RAD,b[1]*RAD,b[0]*RAD);
        var q=new Quaternionf(torso).mul(new Quaternionf().rotationZYX(arm[2]*RAD,arm[1]*RAD,arm[0]*RAD));
        Vector3f shoulder=toModel(p,new Vector3f(side*5,2-lift,0));
        for(float y=1.5f;y<=4;y+=.5f)out.add(q.transform(new Vector3f(side,y,0)).add(shoulder));
        var lower=new Quaternionf(q).rotateX(p.elbow[s]*RAD);Vector3f elbow=q.transform(new Vector3f(0,4,0)).add(shoulder);
        for(float y=.5f;y<=6;y+=.5f)out.add(lower.transform(new Vector3f(side,y,0)).add(elbow));
        return out;
    }
    /** The arms (4 px thick: 2 px either side of their axes) and the arrow (a pixel thick) kept off the head, while it is held and stands out of the chest. */
    @Test void theArmsAndTheArrowStayOffTheHead(){
        float arms=99,armsAt=0,arrow=99,arrowAt=0;
        for(var fate:FATES)for(float t=0;t<(fate==Fate.DIE?REVEAL+.75f:FREE+.5f);t+=.01f){
            var f=frame(t,fate,REVEAL);
            for(int s=0;s<2;s++)for(var pt:armAxis(f,s)){float d=fromHead(f.pose(),pt)-2;if(d<arms){arms=d;armsAt=t;}}
            var a=f.arrow();
            if(a!=null&&a.sink()<.97f)for(float u=NOCK_U;u<=TIP_U;u+=.5f){float d=fromHead(f.pose(),toModel(f.pose(),a.at(u)))-.5f;if(d<arrow){arrow=d;arrowAt=t;}}
        }
        for(float t:TIMES){
            var f=frame(t,Fate.UNKNOWN,REVEAL);float am=99,ar=99;
            for(int s=0;s<2;s++)for(var pt:armAxis(f,s))am=Math.min(am,fromHead(f.pose(),pt)-2);
            var a=f.arrow();if(a!=null)for(float u=NOCK_U;u<=TIP_U;u+=.5f)ar=Math.min(ar,fromHead(f.pose(),toModel(f.pose(),a.at(u)))-.5f);
            System.out.printf("  key %.2f: arms %.2f px off the head, arrow %.2f%n",t,am,ar);
        }
        System.out.printf("the arms come %.2f px off the head at least (%.2f s), the arrow %.2f (%.2f s)%n",arms,armsAt,arrow,arrowAt);
        // (an arm's edge may graze the head's corner by under a pixel, as the rig's arms do; never through it)
        assertTrue(arms>-.75f,"an arm goes "+(-arms)+" px into the head at "+armsAt);
        assertTrue(arrow>.2f,"the arrow comes within "+(arrow+.5f)+" px of the head at "+arrowAt);
    }
    /** One it rejects ends face down on the ground: the body's lowest corners on it, none far under it. */
    @Test void theRejectedLieOnTheGround(){
        var f=frame(DEATH+1,Fate.DIE,REVEAL);var p=f.pose();
        float torso=lowest(p,f,"torso"),head=lowest(p,f,"head"),arms=Math.max(lowest(p,f,"right"),lowest(p,f,"left"));
        System.out.printf("lying: torso %.2f head %.2f arms %.2f (ground 24), legs %.2f%n",torso,head,arms,lowest(p,f,"legs"));
        assertEquals(24,torso,.6f);assertTrue(head<25.1f&&head>23);assertTrue(arms<25.1f);
        assertEquals(1,f.fallen(),1e-3f);
    }
    /** The lowest point (model px, +y down) of a part of the posed body. */
    static float lowest(MotionPose p,Frame f,String part){
        float low=-99;
        switch(part){
            case "torso"->{for(float[] c:corners(-4,4,0,12,-2,2))low=Math.max(low,toModel(p,new Vector3f(c[0],c[1],c[2])).y);}
            case "head"->{
                float[] h=p.joint[MotionPose.HEAD];var q=new Quaternionf().rotationZYX(h[2]*RAD,h[1]*RAD,h[0]*RAD);Vector3f neck=toModel(p,new Vector3f());
                for(float[] c:corners(-4,4,-8,0,-4,4))low=Math.max(low,q.transform(new Vector3f(c[0],c[1],c[2])).add(neck).y);
            }
            case "right","left"->{
                int s=part.equals("right")?0:1;float lift=s==0?f.liftR():f.liftL();
                float[] arm=p.joint[MotionPose.RIGHT_ARM+s];float[] b=p.joint[MotionPose.BODY];
                var torso=new Quaternionf().rotationZYX(b[2]*RAD,b[1]*RAD,b[0]*RAD);
                var q=new Quaternionf(torso).mul(new Quaternionf().rotationZYX(arm[2]*RAD,arm[1]*RAD,arm[0]*RAD));
                Vector3f shoulder=toModel(p,new Vector3f(s==0?-5:5,2-lift,0));
                float x0=s==0?-3:-1;
                for(float[] c:corners(x0,x0+4,-2,4,-2,2))low=Math.max(low,q.transform(new Vector3f(c[0],c[1],c[2])).add(shoulder).y);
                var lower=new Quaternionf(q).rotateX(p.elbow[s]*RAD);Vector3f elbow=q.transform(new Vector3f(0,4,0)).add(shoulder);
                for(float[] c:corners(x0,x0+4,0,6,-2,2))low=Math.max(low,lower.transform(new Vector3f(c[0],c[1],c[2])).add(elbow).y);
            }
            case "legs"->{
                for(int s=0;s<2;s++){
                    float[] l=p.joint[MotionPose.RIGHT_LEG+s];var q=new Quaternionf().rotationZYX(l[2]*RAD,l[1]*RAD,l[0]*RAD);
                    Vector3f hip=new Vector3f((s==0?-1.9f:1.9f)+p.shift,12+p.bob,p.lean);
                    var lower=new Quaternionf(q).rotateX(p.knee[s]*RAD);Vector3f knee=q.transform(new Vector3f(0,6,0)).add(hip);
                    for(float[] c:corners(-2,2,0,6,-2,2)){low=Math.max(low,q.transform(new Vector3f(c[0],c[1],c[2])).add(hip).y);low=Math.max(low,lower.transform(new Vector3f(c[0],c[1],c[2])).add(knee).y);}
                }
            }
            default->throw new IllegalArgumentException(part);
        }
        return low;
    }
    static float[][] corners(float x0,float x1,float y0,float y1,float z0,float z1){
        return new float[][]{{x0,y0,z0},{x1,y0,z0},{x0,y1,z0},{x1,y1,z0},{x0,y0,z1},{x1,y0,z1},{x0,y1,z1},{x1,y1,z1}};
    }
    /**
     * Standing, the feet stay on the ground; going down on the knees and getting up, the legs never go into it nor float
     * far off it; on the knees, the knees and shins are on it (the hips at the kneel).
     */
    @Test void theFeetAndKneesStayOnTheGround(){
        float low=99,high=0;
        for(var fate:FATES)for(float t=0;t<(fate==Fate.DIE?REVEAL+LANDS_AT-.3f:FREE+.5f);t+=FRAME){
            var f=frame(t,fate,REVEAL);float legs=lowest(f.pose(),f,"legs");
            low=Math.min(low,legs);high=Math.max(high,legs);
            // (bent knees tilt the boxy shins: a heel's corner may go a little under, as the rig's crouches always have)
            assertTrue(legs>23.1f&&legs<24.9f,fate+" at "+t+": the legs' lowest point at "+legs);
        }
        System.out.printf("the legs' lowest point stays between %.2f and %.2f (the ground at 24)%n",low,high);
        for(float t:new float[]{KNEES+.05f,ABSORB,STILL,REVEAL}){
            var k=frame(t,Fate.UNKNOWN,REVEAL);
            assertEquals(24,lowest(k.pose(),k,"legs"),.6f,"kneeling at "+t);assertEquals(4,k.pose().bob,1e-3f,"the hips down at "+t);
        }
        var one=frame(KNEE+.02f,Fate.UNKNOWN,REVEAL);
        assertTrue(one.pose().knee[0]>60&&one.pose().knee[1]>40,"down on the right knee, the left foot planted, its knee bent");
        var up=frame(FREE,Fate.AWAKEN,REVEAL);
        assertEquals(0,up.pose().bob,.3f,"standing again");
    }
    /** About ten seconds (user request, 2.71.0), its stages in order, everything still for a moment before its choice. */
    @Test void itTakesItsTimeAndGoesStillBeforeItsChoice(){
        for(int i=1;i<TIMES.length;i++)assertTrue(TIMES[i]>TIMES[i-1],"the keys in order at "+i);
        assertTrue(REVEAL>=8.5f&&DEATH>=10&&FREE>=10.5f,"about ten seconds");
        assertTrue(LOAD<HIT&&HIT-LOAD<.3f,"driven in fast from the load");
        assertTrue(SINK_FROM>KNEES&&SINK_TO<STILL,"drawn in on the knees, gone before the stillness");
        for(float b:BEATS)assertTrue(b>HIT&&b<STILL,"the heart beats between the blow and the stillness");
        for(int i=1;i<BEATS.length;i++)assertTrue(BEATS[i]-BEATS[i-1]<(i>1?BEATS[i-1]-BEATS[i-2]+1e-4f:1),"faster and faster");
        float most=0;
        for(float t=STILL+.08f;t<REVEAL;t+=FRAME)most=Math.max(most,worst(frame(t,Fate.UNKNOWN,REVEAL).pose(),frame(t+FRAME,Fate.UNKNOWN,REVEAL).pose()));
        System.out.printf("still before its choice: %.2f° a frame at most%n",most);
        assertTrue(most<1.2f,"still before its choice ("+most+"° a frame)");
    }
    /** The choice heard late starts from the pose then: nothing jumps however late the word comes. */
    @Test void aChoiceHeardLateStartsWhereTheBodyIs(){
        for(var fate:FATES)for(float known:new float[]{REVEAL+.05f,REVEAL+.3f,REVEAL+.9f}){
            var before=frame(known-FRAME/2,Fate.UNKNOWN,REVEAL).pose();var after=frame(known+FRAME/2,fate,known).pose();
            assertTrue(worst(before,after)<15,fate+" heard at "+known+": "+worst(before,after)+"°");
        }
    }
    /**
     * The arrow glints as it is looked at, dulls as it is lowered, shines brightest raised to the sky and again aimed, flares
     * as it goes in and beats with the heart, brighter as it is drawn in; it is out once its choice shows.
     */
    @Test void theArrowShines(){
        float sky=glow(RAISE,Fate.UNKNOWN,REVEAL),looked=glow(STUDY,Fate.UNKNOWN,REVEAL),doubt=glow(DWELL,Fate.UNKNOWN,REVEAL);
        assertTrue(sky>.7f&&looked>.4f&&sky>looked,"glints looked at, brightest raised to the sky");
        assertTrue(doubt<.15f,"dull as it is lowered");
        assertTrue(glow(TREMBLE,Fate.UNKNOWN,REVEAL)>.4f,"shines held high");
        assertTrue(glow(HIT+.02f,Fate.UNKNOWN,REVEAL)>.8f,"flares as it goes in");
        assertTrue(glow(BEATS[4]+.02f,Fate.UNKNOWN,REVEAL)>glow(BEATS[4]-.1f,Fate.UNKNOWN,REVEAL),"beats with the heart");
        assertTrue(glow(SINK_TO,Fate.UNKNOWN,REVEAL)>glow(KNEES,Fate.UNKNOWN,REVEAL),"brighter as it is drawn in");
        assertEquals(0,glow(REVEAL+.5f,Fate.AWAKEN,REVEAL),1e-4f);
        float prev=glow(0,Fate.AWAKEN,REVEAL);
        for(float t=FRAME;t<REVEAL+1;t+=FRAME){float g=glow(t,Fate.AWAKEN,REVEAL);assertTrue(Math.abs(g-prev)<.75f,"glow jumps at "+t);prev=g;}
    }
}
