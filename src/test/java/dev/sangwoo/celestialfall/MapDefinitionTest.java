package dev.sangwoo.celestialfall;

import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.loot.LootPositions;
import dev.sangwoo.celestialfall.map.MapDefinition;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MapDefinitionTest {
    static final String SEASIDE="""
        {"format":1,"id":"botand:seaside","name":"보탠드 해안 마을","spawn":[0,65,29,0],
         "merchants":{"seller":{"pos":[14.5,65,25.5],"yaw":90},"buyer":{"pos":[20.5,65.0625,25.5],"yaw":90}},
         "loot":[[-150,65,62],[40,65,80],[0,105,-222]],
         "places":{"station":[23,65,26],"bridge":[0,76,131]}}""";
    static MapDefinition parse(String json){return MapDefinition.parse(JsonParser.parseString(json));}

    @AfterEach void reset(){LootPositions.use(null);}

    @Test void readsWhereEverythingStands(){
        var d=parse(SEASIDE);
        assertEquals("botand:seaside",d.id());assertEquals("보탠드 해안 마을",d.name());
        assertEquals(new BlockPos(0,65,29),d.spawn());
        assertEquals(14.5,d.seller().x);assertEquals(65.0625,d.buyer().y);assertEquals(90f,d.sellerYaw());
        assertEquals(List.of(new BlockPos(-150,65,62),new BlockPos(40,65,80),new BlockPos(0,105,-222)),d.loot());
        assertEquals(new BlockPos(0,76,131),d.places().get("bridge"));
        assertEquals(List.of("station","bridge"),List.copyOf(d.places().keySet()));
        assertTrue(d.wires().isEmpty());
    }
    @Test void readsTheWiresBetweenThePoles(){
        var d=parse(SEASIDE.replace("\"places\"","\"wires\":[[100.5,80.95,-36.5,100.5,80.95,-17.5,0.61],[-66,82,-65,-66,82,-54,0.4]],\"places\""));
        assertEquals(List.of(new MapDefinition.Wire(100.5f,80.95f,-36.5f,100.5f,80.95f,-17.5f,0.61f),new MapDefinition.Wire(-66,82,-65,-66,82,-54,0.4f)),d.wires());
        assertEquals(new BlockPos(23,65,26),d.places().get("station"));
    }
    @Test void refusesWiresItCannotDraw(){
        String[] bad={"[[0,80,0,0,80,10]]","[[0,80,0,0,80,100,0.5]]","[[0,80,0,0,80,10,-1]]","[[0,80,0,0,80,10,9]]","[[0,80,0,\"x\",80,10,0.5]]","[5]"};
        for(String w:bad)assertThrows(RuntimeException.class,()->parse(SEASIDE.replace("\"places\"","\"wires\":"+w+",\"places\"")),w);
        var many=new StringBuilder();for(int i=0;i<=MapDefinition.MAX_WIRES;i++)many.append(i>0?",":"").append("[0,80,").append(i).append(",1,80,").append(i).append(",0.1]");
        assertThrows(IllegalArgumentException.class,()->parse(SEASIDE.replace("\"places\"","\"wires\":["+many+"],\"places\"")));
    }
    @Test void refusesWhatItCannotTrust(){
        assertThrows(IllegalArgumentException.class,()->parse("[]"));
        assertThrows(IllegalArgumentException.class,()->parse(SEASIDE.replace("\"format\":1","\"format\":2")));
        assertThrows(IllegalArgumentException.class,()->parse(SEASIDE.replace("\"merchants\"","\"traders\"")));
        assertThrows(IllegalArgumentException.class,()->parse(SEASIDE.replace("[40,65,80]","[-150,65,62]")));
        assertThrows(IllegalArgumentException.class,()->parse(SEASIDE.replace("[14.5,65,25.5]","[14.5,65]")));
        var many=new StringBuilder();for(int i=0;i<=MapDefinition.MAX_LOOT;i++)many.append(i>0?",":"").append("[").append(i).append(",65,0]");
        assertThrows(IllegalArgumentException.class,()->parse(SEASIDE.replace("[[-150,65,62],[40,65,80],[0,105,-222]]","["+many+"]")));
    }
    @Test void aDescribedMapsSpotsReplaceTheCitysUntilTheServerStops(){
        assertSame(LootPositions.ALL,LootPositions.current());
        assertTrue(LootPositions.valid(10));assertFalse(LootPositions.valid(11));
        LootPositions.use(parse(SEASIDE).loot());
        assertEquals(3,LootPositions.current().size());
        assertTrue(LootPositions.valid(2));assertFalse(LootPositions.valid(3));assertFalse(LootPositions.valid(-1));
        LootPositions.use(null);
        assertSame(LootPositions.ALL,LootPositions.current());
        LootPositions.use(List.of());
        assertSame(LootPositions.ALL,LootPositions.current());
    }
}
