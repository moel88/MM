package dev.sangwoo.celestialfall;
import dev.sangwoo.celestialfall.map.GardenPatch;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class GardenPatchTest {
    @Test void bundledPatchHasUniqueCoordinatesAndDoesNotObstructCombatOrAlterContainers()throws Exception{
        var p=GardenPatch.read(getClass().getResourceAsStream("/data/celestialfall/map/azure_garden.bgp.gz"));
        assertTrue(p.count>50_000&&p.count<500_000);var seen=new HashSet<Long>();
        for(int i=0;i<p.count;i++){
            long key=((long)(p.x(i)+176)<<32)|((long)(p.z(i)+192)<<16)|(p.y(i)+64);
            assertTrue(seen.add(key));assertNotEquals(p.before(i),p.after(i));
            assertFalse(p.palette[p.before(i)].contains("chest"));assertFalse(p.palette[p.before(i)].contains("sign"));
            if(p.x(i)>=4&&p.x(i)<=62&&p.z(i)>=-70&&p.z(i)<=-26&&p.y(i)>80)assertEquals("minecraft:air",p.palette[p.after(i)]);
            if(p.x(i)==33&&p.z(i)==-19&&p.y(i)>=81&&p.y(i)<=82)assertEquals("minecraft:air",p.palette[p.after(i)]);
        }
    }
    @Test void liquidsAreAppliedOnlyAfterAllRetainingWalls()throws Exception{
        var p=GardenPatch.read(getClass().getResourceAsStream("/data/celestialfall/map/azure_garden.bgp.gz"));boolean water=false;
        for(int i=0;i<p.count;i++){boolean fluid=p.palette[p.after(i)].startsWith("minecraft:water[");if(fluid)water=true;else assertFalse(water,"Solid geometry after water");}
        assertTrue(water);
    }
    @Test void corruptPatchFailsBeforeAnyMutation(){
        assertThrows(java.io.IOException.class,()->GardenPatch.read(null));
        assertThrows(java.io.IOException.class,()->GardenPatch.read(new java.io.ByteArrayInputStream(new byte[40])));
    }
    @Test void repairTargetsOnlyOldPavementInsertsAndMatchesNewDecoration()throws Exception{
        var floor=GardenPatch.read(getClass().getResourceAsStream("/data/celestialfall/map/azure_garden_floor_fix.bgp.gz"));
        var main=GardenPatch.read(getClass().getResourceAsStream("/data/celestialfall/map/azure_garden.bgp.gz"));
        var expected=new HashMap<String,String>();
        for(int i=0;i<main.count;i++)expected.put(main.x(i)+","+main.y(i)+","+main.z(i),main.palette[main.after(i)]);
        var venues=GardenPatch.read(getClass().getResourceAsStream("/data/celestialfall/map/azure_venues.bgp.gz"));
        var venueExpected=new HashMap<String,String>();
        for(int i=0;i<venues.count;i++)venueExpected.put(venues.x(i)+","+venues.y(i)+","+venues.z(i),venues.palette[venues.after(i)]);
        assertTrue(floor.count>100&&floor.count<5000);var seen=new HashSet<String>();
        for(int i=0;i<floor.count;i++){
            String key=floor.x(i)+","+floor.y(i)+","+floor.z(i);
            assertTrue(seen.add(key));assertEquals(80,floor.y(i));
            assertEquals("minecraft:quartz_bricks",floor.palette[floor.before(i)]);
            assertEquals("minecraft:polished_andesite",floor.palette[floor.after(i)]);
            assertEquals(venueExpected.getOrDefault(key,floor.palette[floor.after(i)]),expected.get(key));
        }
    }

    @Test void venuesOnlyTouchTheTwoParcelsAndBaselinesAreAligned()throws Exception{
        var p=GardenPatch.read(getClass().getResourceAsStream("/data/celestialfall/map/azure_venues.bgp.gz"));
        var old=GardenPatch.read(getClass().getResourceAsStream("/data/celestialfall/map/azure_venues_original.bgp.gz"));
        assertEquals(p.count,old.count);assertArrayEquals(p.palette,old.palette);assertTrue(p.count>2000);
        var seen=new HashSet<String>();
        for(int i=0;i<p.count;i++){
            int x=p.x(i),y=p.y(i),z=p.z(i);
            assertTrue((x>=-15&&x<=3&&z>=-128&&z<=-89)||(x>=-58&&x<=-41&&z>=33&&z<=58));
            assertTrue(y>=80&&y<=105);assertTrue(seen.add(x+","+y+","+z));
            assertEquals(x,old.x(i));assertEquals(y,old.y(i));assertEquals(z,old.z(i));assertEquals(p.after(i),old.after(i));
            assertFalse(p.palette[p.before(i)].contains("chest"));assertFalse(p.palette[p.before(i)].contains("sign"));
        }
    }
}
