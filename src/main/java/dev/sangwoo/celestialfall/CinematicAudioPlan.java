package dev.sangwoo.celestialfall;

import java.util.List;

/** Sample-speed audio chunks are triggered by the same server-age clock as the visuals. */
public final class CinematicAudioPlan {
    public record Cue(String sound,int start,int end) {}
    public static final List<Cue> ATOMIC=List.of(new Cue("atomic_full",0,AtomicTimeline.END));
    // One uninterrupted sound: camera/contact transitions must never stop the chant.
    public static final List<Cue> HEAVEN=List.of(new Cue("heaven_full",1,HeavenTimeline.CAMERA_END));
    private CinematicAudioPlan() {}
    public static int segment(List<Cue> cues,int age) {
        for(int i=0;i<cues.size();i++)if(age>=cues.get(i).start && age<cues.get(i).end)return i;
        return -1;
    }
    /** Never replay a chant from the beginning for a late spectator or after a tick jump. */
    public static boolean startable(Cue cue,int age) { return age>=cue.start && age<=cue.start+2; }
}
