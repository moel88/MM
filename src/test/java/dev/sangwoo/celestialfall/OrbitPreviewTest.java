package dev.sangwoo.celestialfall;
import dev.sangwoo.celestialfall.combat.OrbitCameraState;
import com.google.gson.Gson;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Export the production camera's positions; the renderer does not reimplement zoom/orbit smoothing. */
class OrbitPreviewTest {
    @Test void exportProductionCameraTrack() throws Exception {
        var s=new OrbitCameraState();s.reset(0);List<Object> frames=new ArrayList<>();
        double x=0,z=0;float body=0;
        for(int f=0;f<960;f++) {
            double t=f/30.0;String chapter,clip="idle";boolean moving=false;int meshFrame=0;
            if(t<5){chapter="ORBIT";s.rotate(f<120?1:0,0);}
            else if(t<10){chapter="RUN";s.run=true;moving=true;clip="run";body=OrbitCameraState.turn(body,s.yaw,24);x-=Math.sin(Math.toRadians(s.yaw))*.14;z+=Math.cos(Math.toRadians(s.yaw))*.14;}
            else if(t<15){chapter="SHIFT LOCK";s.shiftLock=true;s.run=false;moving=true;clip="strafe";s.rotate(.12,0);body=s.yaw;x+=Math.cos(Math.toRadians(s.yaw))*.09;z+=Math.sin(Math.toRadians(s.yaw))*.09;}
            else if(t<20){chapter="ZOOM IN";if(f%8==0&&t<17)s.scroll(1);body=s.yaw;}
            else if(t<24){chapter="ZOOM OUT";if(f%4==0)s.scroll(-1);body=s.yaw;}
            else if(t<28){chapter="WALL CLEARANCE";s.targetDistance=5.5;body=s.yaw;}
            else if(t<30){chapter=t<28.5?"GUARD":t<29.5?"GUARD BREAK":"RECOVER";clip=t<28.5?"guard":t<29.5?"break":"idle";if(t>=28.5&&t<29.5){x-=Math.sin(Math.toRadians(s.yaw))*.009;z+=Math.cos(Math.toRadians(s.yaw))*.009;}body=s.yaw;}
            else{chapter="4 HIT COMBO";clip="combo";body=s.yaw;}
            s.step(1.0/30);double available=s.distance;
            if(t>=24&&t<28)available=Math.min(s.distance,t<26?Math.max(1.6,5.5-(t-24)*2.6):5.5);
            double length=s.clip(available,1.0/30);
            var offset=s.offset(length,s.shoulder*(s.distance<.001?0:length/s.distance));
            if(moving)meshFrame=(int)(t*24)%72;
            if(clip.equals("guard"))meshFrame=20;
            if(clip.equals("break"))meshFrame=(int)((t-28.5)*24)%72;
            if(clip.equals("combo"))meshFrame=Math.min(71,(int)((t-30)*24));
            Map<String,Object> row=new LinkedHashMap<>();row.put("time",t);row.put("chapter",chapter);row.put("clip",clip);row.put("meshFrame",meshFrame);
            row.put("player",new double[]{x,0,z});row.put("bodyYaw",body);row.put("eye",new double[]{x+(s.firstPerson()?0:offset.x()),1.62+(s.firstPerson()?0:offset.y()),z+(s.firstPerson()?0:offset.z())});
            row.put("yaw",s.yaw);row.put("pitch",s.pitch);row.put("distance",length);row.put("target",s.targetDistance);row.put("shiftLock",s.shiftLock);row.put("firstPerson",s.firstPerson());row.put("run",s.run);
            assertTrue(Double.isFinite(offset.x())&&Double.isFinite(offset.y())&&Double.isFinite(offset.z()));assertTrue(length<=14);
            frames.add(row);
        }
        Path out=Path.of("build/botand-motion");Files.createDirectories(out);Files.writeString(out.resolve("camera-track.json"),new Gson().toJson(frames));
    }
}
