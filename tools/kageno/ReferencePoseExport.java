import java.nio.file.*;
import java.net.*;
import java.util.*;
public final class ReferencePoseExport {
 public static void main(String[] args)throws Exception {
  try(var loader=new URLClassLoader(new URL[]{Path.of(args[0]).toUri().toURL()},null)) {
   var motion=loader.loadClass("dev.sangwoo.celestialfall.combat.AtomicMotion");
   var sample=motion.getMethod("sample",float.class);
   var posed=loader.loadClass("dev.sangwoo.celestialfall.AtomicTimeline").getMethod("posed",float.class);
   var c=loader.loadClass("dev.sangwoo.celestialfall.combat.MotionPose");
   var times=new ArrayList<Float>();
   for(int i=0;i<=864;i++)times.add(i/6f);
   times.addAll(List.of(-10f,144.1f,600f,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY));
   StringBuilder out=new StringBuilder("age_ticks,posed,head_x,head_y,head_z,body_x,body_y,body_z,right_arm_x,right_arm_y,right_arm_z,left_arm_x,left_arm_y,left_arm_z,right_leg_x,right_leg_y,right_leg_z,left_leg_x,left_leg_y,left_leg_z,right_elbow,left_elbow,right_knee,left_knee,bob,shift,lean\n");
   for(float t:times) {
    Object p=sample.invoke(null,t);out.append(t).append(',').append(posed.invoke(null,t));
    for(var joint:(float[][])c.getField("joint").get(p))for(float x:joint)out.append(',').append(x);
    for(String field:List.of("elbow","knee"))for(float x:(float[])c.getField(field).get(p))out.append(',').append(x);
    for(String field:List.of("bob","shift","lean"))out.append(',').append(c.getField(field).getFloat(p));
    out.append('\n');
   }
   Files.writeString(Path.of(args[1]),out);
  }
 }
}
