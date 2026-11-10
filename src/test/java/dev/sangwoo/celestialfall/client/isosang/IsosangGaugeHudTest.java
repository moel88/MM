package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.isosang.IsosangRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lee So-sang's H gauge (2.113.0, the user's video read and confirmed): the sword's outline as the video has it, its fill
 * in eight steps from the pommel to the point, its flames and its lights all continuous in time.
 */
class IsosangGaugeHudTest {
    @Test void itsOutlineIsTheVideosSword(){
        // its two points on its middle line, the pommel's to the left
        assertEquals(0,IsosangGaugeHud.half(0),1e-9);
        assertEquals(0,IsosangGaugeHud.half(1),1e-9);
        // the grip and the blade as tall (the video: 55 of 870 px either way), the guard's big spike the tallest (2.6 times)
        float grip=IsosangGaugeHud.half(.12f),blade=IsosangGaugeHud.half(.6f),spike=0;
        for(float u=.2f;u<.3f;u+=.0005f)spike=Math.max(spike,IsosangGaugeHud.half(u));
        assertEquals(grip,blade,1e-6);
        assertEquals(55/870f/2,blade,.002f);
        assertEquals(2.6f,spike/blade,.15f);
        for(float u=0;u<=1;u+=.001f)assertTrue(IsosangGaugeHud.half(u)<=spike+1e-6f);
        // the guard's spikes stand between the grip and the blade, the collar's little step before them
        assertTrue(IsosangGaugeHud.half(.194f)>grip,"the collar's step");
        assertTrue(IsosangGaugeHud.half(.2106f)<grip,"the dip before the guard");
        assertTrue(IsosangGaugeHud.half(.2589f)>2.5f*grip&&IsosangGaugeHud.half(.2863f)>1.5f*grip,"its big and small spikes");
        // the blade's point the last tenth of it (the video: 90 of 870 px), the pommel's point its first 6%
        assertTrue(IsosangGaugeHud.half(.89f)==blade&&IsosangGaugeHud.half(.95f)<blade);
        assertTrue(IsosangGaugeHud.half(.03f)<grip&&IsosangGaugeHud.half(.07f)==grip);
    }
    @Test void itsOutlineGoesRoundItOnceClockwiseOnScreen(){
        float[] p=IsosangGaugeHud.outline(100,50);
        int n=p.length/2;
        // area by the shoelace sum, y down: clockwise on screen is positive (GachaArt's outward offsets need it)
        double a=0;for(int i=0;i<n;i++){int j=(i+1)%n;a+=p[i*2]*p[j*2+1]-p[j*2]*p[i*2+1];}
        assertTrue(a>0,"clockwise on screen: "+a);
        // its length and its spikes' reach in GUI pixels
        float lo=Float.MAX_VALUE,hi=-Float.MAX_VALUE,top=Float.MAX_VALUE,bottom=-Float.MAX_VALUE;
        for(int i=0;i<n;i++){lo=Math.min(lo,p[i*2]);hi=Math.max(hi,p[i*2]);top=Math.min(top,p[i*2+1]);bottom=Math.max(bottom,p[i*2+1]);}
        assertEquals(IsosangGaugeHud.LEN,hi-lo,1e-3);
        assertEquals(50-top,bottom-50,1e-3,"its lower half its upper's mirror");
        // no two of its edges cross
        for(int i=0;i<n;i++)for(int j=i+2;j<n;j++){
            if(i==0&&j==n-1)continue;
            assertFalse(cross(p,i,(i+1)%n,j,(j+1)%n),"edges "+i+" and "+j+" cross");
        }
    }
    @Test void itStaysClearOfTheHotbarAndRoomIsMadeOverIt(){
        // the hotbar is 22 tall: its lowest spike over it, and the boss bar's band (29.6 over the bottom) stepped up past its top
        float low=IsosangGaugeHud.LINE-IsosangGaugeHud.half(.2589f)*IsosangGaugeHud.LEN;
        assertTrue(low>22,"over the hotbar: "+low);
        float high=IsosangGaugeHud.LINE+IsosangGaugeHud.half(.2589f)*IsosangGaugeHud.LEN;
        assertTrue(29.6f-5+IsosangGaugeHud.ROOM>high,"the boss bar steps up over it");
        assertTrue(IsosangGaugeHud.LEN<=182,"no wider than the hotbar");
    }
    @Test void itsStripsAreCutAtItsCornersAndRunInOrder(){
        float[] c=IsosangGaugeHud.cuts(0,1);
        assertEquals(0,c[0],1e-9);assertEquals(1,c[c.length-1],1e-9);
        for(int i=1;i<c.length;i++)assertTrue(c[i]>c[i-1]);
        for(var corner:IsosangGaugeHud.TOP){boolean found=false;for(float u:c)if(Math.abs(u-corner[0])<1e-5f)found=true;assertTrue(found,"corner "+corner[0]);}
        // a part of it: from where it is filled to
        float[] part=IsosangGaugeHud.cuts(0,.375f);
        assertEquals(.375f,part[part.length-1],1e-6);
        for(float u:part)assertTrue(u<=.375f+1e-6f);
    }
    @Test void itFillsInEightEqualStepsFromThePommel(){
        // each foe struck fills an eighth of its length (the user's 12.5%)
        for(int k=0;k<=IsosangRules.GAUGE_FULL;k++)assertEquals(k/8f,k/(float)IsosangRules.GAUGE_FULL,1e-6);
        // as it fills, the edge it has filled to is lighter than the blade behind it
        int behind=IsosangGaugeHud.filling(.4f,0,.75f),edge=IsosangGaugeHud.filling(.745f,0,.75f);
        assertTrue(light(edge)>light(behind)+20,"lighter where it has filled to");
        // the grip lighter than the blade, the guard deep blue between
        assertTrue(light(IsosangGaugeHud.filling(.1f,0,1))>light(IsosangGaugeHud.filling(.5f,0,1)));
        assertTrue(blue(IsosangGaugeHud.filling(.25f,0,1))>red(IsosangGaugeHud.filling(.25f,0,1))*4);
        // its edges across lighter than its middle
        assertTrue(light(IsosangGaugeHud.filling(.5f,1,1))>light(IsosangGaugeHud.filling(.5f,0,1)));
    }
    @Test void fullItsGrainFlowsAlongTheBladeAndItsGlowBeats(){
        // the grain moves along the blade: the darkest place on it moves on with time
        float a=darkest(0),b=darkest(.3f);
        assertTrue(b>a,"it flows toward the point: "+a+" then "+b);
        // its beat comes back every 0.7 s and stays between 0 and 1, no jump within it
        for(float t=0;t<3;t+=1/60f){
            float x=IsosangGaugeHud.beat(t,IsosangGaugeHud.BEAT);assertTrue(x>=0&&x<=1);
            if(t>1/60f)assertTrue(Math.abs(x-IsosangGaugeHud.beat(t-1/60f,IsosangGaugeHud.BEAT))<.16f,"t "+t);
        }
        assertEquals(IsosangGaugeHud.beat(.1f,.7f),IsosangGaugeHud.beat(.8f,.7f),1e-4);
    }
    @Test void onHItsLightComesAndDrainsTowardThePointEveryPulse(){
        float f=.8f;
        // all lit as a pulse begins, the light gone from by the guard partway through, all lit again as it ends
        for(float u=.3f;u<f;u+=.05f)assertEquals(1,IsosangGaugeHud.lit(u,f,0),1e-4,"u "+u);
        assertTrue(IsosangGaugeHud.lit(.32f,f,.6f)<.2f,"by the guard, the light gone");
        assertTrue(IsosangGaugeHud.lit(.75f,f,.3f)>.8f,"toward the point, still lit");
        assertEquals(1,IsosangGaugeHud.lit(.5f,f,.999f),.02f);
        // its front (where the light gives way) runs on toward the point a little each frame, never back; no jump across
        // the pulse's turn
        float step=1/(60*IsosangGaugeHud.PULSE),last=-1;
        for(float p=.25f;p<.8f;p+=step){
            float front=-1;for(float u=.3f;u<=1;u+=.001f)if(IsosangGaugeHud.lit(u,f,p)>.5f){front=u;break;}
            if(last>=0){assertTrue(front>=last-1e-3f,"never back: "+p);assertTrue(front-last<.06f,"a little each frame: "+p);}
            last=front;
        }
        for(float u=.3f;u<=f;u+=.1f)assertEquals(IsosangGaugeHud.lit(u,f,0),IsosangGaugeHud.lit(u,f,.9999f),.01f,"across the turn, u "+u);
        for(float u=.3f;u<=f;u+=.1f)for(float p=0;p<1;p+=step)
            assertTrue(Math.abs(IsosangGaugeHud.lit(u,f,Math.min(.9999f,p+step))-IsosangGaugeHud.lit(u,f,p))<.5f,"u "+u+" p "+p);
        // white by the guard, sky blue where it has drained to, blue along between
        int guard=IsosangGaugeHud.onH(.2f,0,f,.5f),middle=IsosangGaugeHud.onH(.4f,0,f,.6f),end=IsosangGaugeHud.onH(.79f,0,f,.6f);
        assertTrue(light(guard)>light(middle),"white by the guard");
        assertTrue(green(end)>green(middle)+40,"sky blue where it has drained to");
    }
    @Test void itsFlamesRiseOffItSmoothlyAndAreGoneInTheirLife(){
        int seen=0,top=0;
        for(int k=0;k<IsosangGaugeHud.FLAMES;k++){
            IsosangGaugeHud.Flame before=null;
            for(float t=0;t<4;t+=1/60f){
                var fl=IsosangGaugeHud.flame(k,t,.6f);
                if(fl==null){before=null;continue;}
                seen++;if(fl.side()<0)top++;
                assertTrue(fl.u()>=.04f&&fl.u()<=.6f+1e-6f,"on what is lit of it: "+fl.u());
                assertTrue(fl.age()>=0&&fl.age()<1);
                if(before!=null&&fl.age()>before.age()){
                    assertEquals(before.u(),fl.u(),1e-6,"a flame stays where it rose from");
                    assertEquals(1/60f/IsosangGaugeHud.FLAME_LIFE,fl.age()-before.age(),1e-3);
                }
                before=fl;
            }
        }
        assertTrue(seen>0);
        assertTrue(top>seen/2,"most rise off its top");
        // a flame's outline: pointing up off its top, down off its bottom
        float[] up=IsosangGaugeHud.flameShape(0,0,4,6,-1),down=IsosangGaugeHud.flameShape(0,0,4,6,1);
        assertEquals(-6,up[1],1e-6);assertEquals(6,down[1],1e-6);
    }
    @Test void itsFlashesComeAndGoWithinTheirTime(){
        assertEquals(0,IsosangGaugeHud.flash(-.01f,.03f,.2f),1e-9);
        assertEquals(1,IsosangGaugeHud.flash(.03f,.03f,.2f),1e-6);
        assertEquals(0,IsosangGaugeHud.flash(.2f,.03f,.2f),1e-9);
        for(float t=0;t<.3f;t+=1/240f)assertTrue(IsosangGaugeHud.flash(t,.12f,.42f)>=0&&IsosangGaugeHud.flash(t,.12f,.42f)<=1);
    }

    private static float darkest(float t){
        float best=-1,lo=Float.MAX_VALUE;
        for(float u=.3f;u<1;u+=.002f){float l=light(IsosangGaugeHud.full(u,0,t));if(l<lo){lo=l;best=u;}}
        return best;
    }
    private static int red(int c){return (c>>16)&255;}
    private static int green(int c){return (c>>8)&255;}
    private static int blue(int c){return c&255;}
    private static float light(int c){return red(c)+green(c)+blue(c);}
    private static boolean cross(float[] p,int a,int b,int c,int d){
        double d1=orient(p,c,d,a),d2=orient(p,c,d,b),d3=orient(p,a,b,c),d4=orient(p,a,b,d);
        return d1*d2<0&&d3*d4<0;
    }
    private static double orient(float[] p,int a,int b,int c){return (p[b*2]-p[a*2])*(double)(p[c*2+1]-p[a*2+1])-(p[b*2+1]-p[a*2+1])*(double)(p[c*2]-p[a*2]);}
}
