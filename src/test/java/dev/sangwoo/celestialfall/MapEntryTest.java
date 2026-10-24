package dev.sangwoo.celestialfall;
import dev.sangwoo.celestialfall.client.title.MapEntryGate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MapEntryTest {
    @Test void startupAndOrdinaryMapsNeverShowTheScreen(){
        var gate=new MapEntryGate();
        for(int tick=0;tick<50;tick++)assertFalse(gate.update(null,tick%2==0));
    }
    @Test void authorizedEntryWaitsForLoadingOverlaysAndOtherScreens(){
        var gate=new MapEntryGate();
        for(int tick=0;tick<30;tick++)assertFalse(gate.update("minecraft:overworld",false));
        assertTrue(gate.update("minecraft:overworld",true));
        assertFalse(gate.update("minecraft:overworld",true));
    }
    @Test void playAndCreditsReturnAndRespawnDoNotReopenWelcome(){
        var gate=new MapEntryGate();assertTrue(gate.update("minecraft:overworld",true));
        for(int tick=0;tick<100;tick++)assertFalse(gate.update("minecraft:overworld",tick%3==0));
    }
    @Test void reconnectingToTheSameMapShowsWelcomeAgain(){
        var gate=new MapEntryGate();assertTrue(gate.update("minecraft:overworld",true));
        gate.reset();assertFalse(gate.update("minecraft:overworld",false));
        assertTrue(gate.update("minecraft:overworld",true));
    }
    @Test void DisablingAMapClearsPendingEntryAndReenablingStartsANewEntry(){
        var gate=new MapEntryGate();assertFalse(gate.update("minecraft:overworld",false));
        assertFalse(gate.update(null,true));assertFalse(gate.update(null,true));
        assertTrue(gate.update("minecraft:overworld",true));
        assertFalse(gate.update(null,false));assertTrue(gate.update("minecraft:overworld",true));
    }
    @Test void onlyAuthorizedDimensionsCanStartANewEntry(){
        var gate=new MapEntryGate();assertTrue(gate.update("minecraft:overworld",true));
        assertFalse(gate.update(null,true)); // ordinary Nether is unauthorized
        assertTrue(gate.update("minecraft:overworld",true));
        assertTrue(gate.update("botand:arena",true));
        assertFalse(gate.update("botand:arena",true));
    }
}
