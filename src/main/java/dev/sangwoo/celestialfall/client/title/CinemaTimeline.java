package dev.sangwoo.celestialfall.client.title;

import java.util.ArrayList;
import java.util.List;

/** A screen-owned clock. Resizing/rebuilding widgets never rewinds the performance. */
public final class CinemaTimeline {
    public static final double INTRO=7, LOOP=12, TURN_START=3.8;
    public record Cue(String sound,float volume){}
    private double elapsed;
    private boolean reduced;
    public double elapsed(){return elapsed;}
    public boolean musicReady(){return elapsed>=TURN_START;}
    public double poseTime(){return reduced?INTRO:elapsed<INTRO?elapsed:INTRO+(elapsed-INTRO)%LOOP;}
    public List<Cue> advance(double dt,boolean animated){
        List<Cue> cues=new ArrayList<>();
        if(!animated){reduced=true;elapsed=Math.max(INTRO,elapsed);return cues;}
        reduced=false;
        // Tab-out, resource reload and stalled frames cannot skip the intro or burst old sounds.
        double old=elapsed;elapsed+=Math.max(0,Math.min(.10,Double.isFinite(dt)?dt:0));
        double[] steps={.12,.67,1.22,1.77,2.32,2.87,3.42,3.97};
        float[] gains={.48f,.46f,.50f,.47f,.49f,.46f,.42f,.34f};
        for(int i=0;i<steps.length;i++)if(crossed(old,elapsed,steps[i]))cues.add(new Cue("cinema_step_"+(i+1),gains[i]));
        if(crossed(old,elapsed,4.1))cues.add(new Cue("cinema_cloth",.32f));
        if(crossed(old,elapsed,5.2))cues.add(new Cue("cinema_chime",.15f));
        for(long cycle=Math.max(0,(long)Math.floor((old-INTRO)/LOOP));cycle<=(long)Math.floor((elapsed-INTRO)/LOOP);cycle++){
            if(crossed(old,elapsed,INTRO+cycle*LOOP+3.7))cues.add(new Cue("cinema_sand",.26f));
            if(crossed(old,elapsed,INTRO+cycle*LOOP+8.3))cues.add(new Cue("cinema_chime",.15f));
        }
        return cues;
    }
    private static boolean crossed(double a,double b,double t){return a<t&&b>=t;}
}
