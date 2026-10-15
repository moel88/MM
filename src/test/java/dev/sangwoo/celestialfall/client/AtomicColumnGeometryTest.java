package dev.sangwoo.celestialfall.client;
import dev.sangwoo.celestialfall.AtomicTimeline;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AtomicColumnGeometryTest {
    @Test void actualVolumeHasFullHeightAtWhiteOnsetAndKeepsItToLastFrame() {
        for(float age:new float[]{128,200,302,400,440,450,460,467.9f}) {
            double[] bounds={0,0};int[] count={0};
            double radius=AtomicTimeline.blastRadius(age,28);
            AtomicColumnGeometry.body((a,b,c,d,color,alpha)->{
                count[0]++;assertEquals(1,alpha);
                assertTrue((color&255)>((color>>16)&255));assertTrue(((color>>8)&255)<125,"Body washed toward white");
                for(Vec3d p:new Vec3d[]{a,b,c,d}) {
                    assertTrue(Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z));
                    bounds[0]=Math.max(bounds[0],p.horizontalLength());bounds[1]=Math.max(bounds[1],p.y);
                }
            },age,28,false);
            assertEquals(radius,bounds[0],1e-6);assertEquals(570,bounds[1],1e-6);assertTrue(count[0]>100);
        }
    }
    @Test void geometryIsBoundedAndFullyRemovedOutsideCast() {
        for(boolean low:new boolean[]{true,false})for(float age:new float[]{0,127.99f,128,350,450,468,900}) {
            int[] count={0};
            AtomicColumnGeometry.Quads sink=(a,b,c,d,color,alpha)->{
                count[0]++;assertTrue(alpha>=0&&alpha<=1);
                for(Vec3d p:new Vec3d[]{a,b,c,d}) {
                    assertTrue(Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z));
                    assertTrue(p.y>=0&&p.y<=570.01);assertTrue(p.horizontalLength()<=45);
                }
            };
            AtomicColumnGeometry.body(sink,age,28,low);AtomicColumnGeometry.halo(sink,age,28,low);AtomicColumnGeometry.energy(sink,age,28,low);
            assertTrue(count[0]<=(low?1400:4500),"Mesh budget exceeded: "+count[0]);
            assertEquals(age>=128&&age<468,count[0]>0);
        }
    }
    private static Map<String,List<Double>> packetCenters(float age,boolean low) {
        Map<String,List<Double>> result=new HashMap<>();
        AtomicColumnGeometry.streams((a,b,c,d,color,alpha)->{
            Vec3d center=a.add(b).add(c).add(d).multiply(.25);
            if(center.y<45||center.y>525)return; // Skip clipped entering/exiting packets.
            String key=Math.round(center.x*10000)+":"+Math.round(center.z*10000)+":"+color+":"+Math.round(alpha*10000);
            result.computeIfAbsent(key,k->new ArrayList<>()).add(center.y);
        },age,28,low);
        return result;
    }
    @Test void renderedStreamPacketsActuallyTravelUpFastAtFractionalTicks() {
        for(boolean low:new boolean[]{true,false}) {
            var before=packetCenters(400,low);var after=packetCenters(400.25f,low);int matches=0,total=0;
            for(var e:before.entrySet())for(double y:e.getValue()) {
                total++;
                // 145--235 blocks/s over 0.0125 seconds, measured from actual mesh centers.
                if(after.getOrDefault(e.getKey(),List.of()).stream().anyMatch(next->next-y>=1.80&&next-y<=2.95))matches++;
            }
            assertTrue(total>100);assertTrue(matches>total*.95,"Rising geometry froze or moved sideways");
        }
    }
}
