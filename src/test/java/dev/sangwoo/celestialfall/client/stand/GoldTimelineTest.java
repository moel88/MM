package dev.sangwoo.celestialfall.client.stand;

import static org.junit.jupiter.api.Assertions.*;

import dev.sangwoo.celestialfall.stand.GoldExperienceLook;
import dev.sangwoo.celestialfall.stand.GoldExperienceStance;
import org.junit.jupiter.api.Test;

/** Gold Experience coming out of its user and going back (2.146.0): every value a smooth function of the seconds since Q. */
class GoldTimelineTest {
    private static float sum(GoldExperienceLook l){float s=0;for(float w:l.weights)s+=w;return s;}
    /** It starts inside its user, curled up and unseen; opens into the picture's pose; and ends floating at rest beside them. */
    @Test void itComesOutCurledOpensIntoThePoseAndSettles(){
        var l=new GoldExperienceLook();
        GoldTimeline.summon(0,l);
        assertEquals(0,l.out,1e-5);assertEquals(0,l.alpha,1e-5);assertEquals(0,l.glow,1e-5);assertEquals(1,l.weights[GoldExperienceStance.SUMMON],1e-5);
        GoldTimeline.summon(GoldTimeline.EMERGE,l);
        assertEquals(1,l.out,1e-4,"out of the body");assertEquals(1,l.alpha,1e-4,"solid");
        GoldTimeline.summon((GoldTimeline.POSED+GoldTimeline.HOLD)/2,l);
        assertEquals(1,l.weights[GoldExperienceStance.HERO],1e-4,"the pose held");assertEquals(1,l.scale,.01);
        GoldTimeline.summon(GoldTimeline.SETTLED,l);
        assertEquals(1,l.weights[GoldExperienceStance.IDLE],1e-4,"at rest");assertEquals(1,l.glow,1e-4,"its aura steady");
        assertEquals(0,l.weights[GoldExperienceStance.STAND],0,"never the stance for looking at the model");
    }
    /** Its aura flares past full as it opens into the pose and comes back to full. */
    @Test void itsAuraFlaresAsItOpens(){
        var l=new GoldExperienceLook();float most=0;
        for(float t=0;t<2;t+=1/60f){GoldTimeline.summon(t,l);most=Math.max(most,l.glow);assertTrue(l.glow<=1.91f);}
        assertTrue(most>1.5f,"flare "+most);
    }
    /** Nothing jumps between two frames at 60 fps, coming out or going back; its stances always make up one whole. */
    @Test void nothingJumpsInAFrame(){
        var l=new GoldExperienceLook();var last=new GoldExperienceLook();var from=new GoldExperienceLook();
        for(boolean out:new boolean[]{true,false}){
            GoldTimeline.summon(3,from);
            for(int f=0;f<=180;f++){
                float t=f/60f;GoldTimeline.at(out,t,out?null:from,l);
                assertEquals(1,sum(l),1e-3,"stances at "+t);
                if(f>0){
                    assertTrue(Math.abs(l.out-last.out)<.2f,"out jumps at "+t);assertTrue(Math.abs(l.alpha-last.alpha)<.25f,"alpha jumps at "+t);
                    assertTrue(Math.abs(l.scale-last.scale)<.06f,"size jumps at "+t);assertTrue(Math.abs(l.glow-last.glow)<.3f,"glow jumps at "+t);
                    for(int i=0;i<l.weights.length;i++)assertTrue(Math.abs(l.weights[i]-last.weights[i])<.2f,"stance "+i+" jumps at "+t);
                }
                last.set(l);
            }
        }
    }
    /** Sent back it curls up, is drawn into its user and is gone in GONE; from the look it had. */
    @Test void sentBackItCurlsUpAndIsGone(){
        var from=new GoldExperienceLook();GoldTimeline.summon(3,from);
        var l=new GoldExperienceLook();
        GoldTimeline.dismiss(0,from,l);
        assertEquals(1,l.out,1e-4);assertEquals(1,l.alpha,1e-4);assertEquals(1,l.weights[GoldExperienceStance.IDLE],1e-4);
        GoldTimeline.dismiss(GoldTimeline.GONE,from,l);
        assertEquals(0,l.out,1e-4);assertEquals(0,l.alpha,1e-4);assertEquals(0,l.glow,1e-4);assertEquals(1,l.weights[GoldExperienceStance.SUMMON],1e-4);
        assertTrue(GoldTimeline.gone(GoldTimeline.GONE));assertFalse(GoldTimeline.gone(GoldTimeline.GONE-.01f));
    }
    /** Q again while it goes back: it takes over from the look it had got to, with no jump on the first frame. */
    @Test void broughtOutAgainItTakesOverFromWhereItWas(){
        var rest=new GoldExperienceLook();GoldTimeline.summon(3,rest);
        var mid=new GoldExperienceLook();GoldTimeline.dismiss(.15f,rest,mid);
        var l=new GoldExperienceLook();GoldTimeline.at(true,0,mid,l);
        assertEquals(mid.out,l.out,1e-4);assertEquals(mid.alpha,l.alpha,1e-4);assertEquals(mid.scale,l.scale,1e-4);
        GoldTimeline.at(true,GoldTimeline.CATCH,mid,l);var plain=new GoldExperienceLook();GoldTimeline.summon(GoldTimeline.CATCH,plain);
        assertEquals(plain.out,l.out,1e-4,"taken over by CATCH");
    }
    /** The stances weighed together move no bone further in a frame than turning between them would. */
    @Test void theStancesBlendWithoutAJump(){
        var poses=new GoldExperiencePoses();var l=new GoldExperienceLook();
        float[] p=new float[6],last=new float[6];
        for(String bone:new String[]{"right_arm","left_arm","right_leg","left_shin","torso","head","right_forearm"}){
            for(int f=0;f<=150;f++){
                GoldTimeline.summon(f/60f,l);poses.blend(bone,l.weights,0,p);
                if(f>0)for(int i=0;i<3;i++)assertTrue(Math.abs(p[i]-last[i])<30,bone+" turns "+(p[i]-last[i])+" degrees in a frame at "+f/60f);
                System.arraycopy(p,0,last,0,6);
            }
        }
    }
}
