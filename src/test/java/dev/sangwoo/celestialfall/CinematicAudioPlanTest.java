package dev.sangwoo.celestialfall;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CinematicAudioPlanTest {
    @Test void audioHandoffsFollowContactAndFinalExplosionWithNoOverlap() {
        for(var plan:List.of(CinematicAudioPlan.ATOMIC,CinematicAudioPlan.HEAVEN)) {
            for(int i=0;i<plan.size();i++) {
                var cue=plan.get(i);
                if(i>0)assertEquals(plan.get(i-1).end(),cue.start());
                assertEquals(i,CinematicAudioPlan.segment(plan,cue.start()));
                assertEquals(i,CinematicAudioPlan.segment(plan,cue.end()-1));
                assertNotEquals(i,CinematicAudioPlan.segment(plan,cue.end()));
            }
            assertEquals(-1,CinematicAudioPlan.segment(plan,-1));
            assertEquals(-1,CinematicAudioPlan.segment(plan,plan.get(plan.size()-1).end()));
        }
        assertEquals(1,CinematicAudioPlan.HEAVEN.size());
        for(int age=1;age<HeavenTimeline.CAMERA_END;age++)assertEquals(0,CinematicAudioPlan.segment(CinematicAudioPlan.HEAVEN,age));
        assertEquals("heaven_full",CinematicAudioPlan.HEAVEN.get(0).sound());
        assertEquals(1,CinematicAudioPlan.ATOMIC.size());assertEquals("atomic_full",CinematicAudioPlan.ATOMIC.get(0).sound());
    }
    @Test void lateJoinOrTickJumpDoesNotReplayOldChantOrImpact() {
        for(var plan:List.of(CinematicAudioPlan.ATOMIC,CinematicAudioPlan.HEAVEN))for(var cue:plan) {
            assertFalse(CinematicAudioPlan.startable(cue,cue.start()-1));
            assertTrue(CinematicAudioPlan.startable(cue,cue.start()));
            assertTrue(CinematicAudioPlan.startable(cue,cue.start()+2));
            assertFalse(CinematicAudioPlan.startable(cue,cue.start()+3));
            assertFalse(CinematicAudioPlan.startable(cue,cue.end()));
        }
    }
    @Test void soundRemakeAlignsDamageAndRetainsOtherCastRules() {
        assertEquals(AtomicTimeline.END,AtomicTimeline.CAST_LOCK_END);
        assertEquals(100,HeavenTimeline.IMPACT);assertEquals(180,HeavenTimeline.EMBED_END);
        assertEquals(244,HeavenTimeline.CAMERA_END);assertEquals(340,HeavenTimeline.END);
        int hits=0;
        for(int a=0;a<=740;a++) {
            boolean expected=a>=128 && a<468 && (a-128)%20==0;
            assertEquals(expected,AtomicTimeline.damageDue(a));if(expected)hits++;
        }
        assertEquals(17,hits);
    }
    @Test void atomicSlashAndSoundUseOneClockWithoutCameraHandoffs() {
        assertEquals(100,AtomicTimeline.SLASH_START);assertEquals(110,AtomicTimeline.SLASH_END);
        for(int age=0;age<468;age++)assertEquals(0,CinematicAudioPlan.segment(CinematicAudioPlan.ATOMIC,age));
        assertEquals(0,CinematicAudioPlan.ATOMIC.get(0).start());assertEquals(468,CinematicAudioPlan.ATOMIC.get(0).end());
    }
    @Test void shippedAudioMatchesVerifiedNormalSpeedSamplesAndCueDurations() throws Exception {
        var stream=getClass().getResourceAsStream("/assets/celestialfall/audio_sync.json");assertNotNull(stream);
        var manifest=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        for(String kind:new String[]{"atomic","heaven"}) {
            var report=manifest.getAsJsonObject(kind);assertEquals("passed",report.get("result").getAsString());
            var plan=kind.equals("atomic")?CinematicAudioPlan.ATOMIC:CinematicAudioPlan.HEAVEN;
            int index=0;
            for(var node:report.getAsJsonArray("tracks")) {
                var track=node.getAsJsonObject();var cue=plan.get(index++);
                assertEquals(cue.sound(),track.get("sound").getAsString());
                double seconds=track.get("seconds").getAsDouble();
                assertTrue(seconds<=(cue.end()-cue.start())/20.0);
                assertEquals(1.0,track.get("playback_speed").getAsDouble());
                assertTrue(track.get("decoded_correlation").getAsDouble()>.97);
                assertTrue(track.get("decoded_peak").getAsDouble()<1);
                try(var audio=getClass().getResourceAsStream("/assets/celestialfall/sounds/"+cue.sound()+".ogg")) {
                    assertNotNull(audio);
                    assertEquals(track.get("sha256").getAsString(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(audio.readAllBytes())));
                }
            }
            assertEquals(plan.size(),index);
        }
    }
}
