package dev.sangwoo.celestialfall.client.kageno;

import static dev.sangwoo.celestialfall.client.kageno.Anim.*;
import static dev.sangwoo.celestialfall.client.kageno.R6.pose;

/**
 * G body clip (seconds after the first skill frame, 144.733 s of the review footage). The
 * caster faces the camera and never moves: a 0.2 s raise into a two-handed overhead A-frame,
 * a static hold through the chant, a 0.2 s release to the sword arm straight up, then idle
 * once the blast has cleared.
 */
public final class AtomicPose {
    private AtomicPose() {}
    static final R6.Pose A_FRAME=pose().rArm(62,-78).lArm(62,78).blade(88,0);
    static final R6.Pose RAISED=pose().rArm(86,90).lArm(-66,-95).blade(89,0);
    public static final Anim CLIP=Anim.of(
        k(0,KagenoClips.IDLE),
        k(.067,KagenoClips.IDLE,Anim.Ease.HOLD),
        // Right arm starts forward, left arm 45° forward-out, the tip drops.
        k(.10,pose().rArm(-35,10).lArm(-45,-40).blade(-62,48)),
        // Right hand at chin height in front of the chest, blade diagonal with the tip low to the right.
        k(.134,pose().rArm(8,-15).lArm(-30,-40).blade(-35,75)),
        // Both hands come up in front of the face while the blade turns to vertical.
        k(.167,pose().rArm(32,-25).lArm(28,30).blade(8,88)),
        k(.20,pose().rArm(48,-40).lArm(48,40).blade(78,10)),
        k(.267,A_FRAME,Anim.Ease.OUT),
        k(5.047,A_FRAME,Anim.Ease.HOLD),
        // Arms open into a V, then the sword arm goes straight up and the left arm drops.
        k(5.117,pose().rArm(45,95).lArm(45,-95).blade(60,92)),
        k(5.217,pose().rArm(66,90).lArm(-45,-92).blade(80,60)),
        k(5.267,RAISED,Anim.Ease.OUT),
        k(21.9,RAISED,Anim.Ease.HOLD),
        k(22.6,KagenoClips.IDLE));
    /** Seconds the clip runs; afterwards the caster is back in the normal idle stance. */
    public static final float END=22.6f;
    public static R6.Frame frame(float sec) { return CLIP.frame(sec); }
}
