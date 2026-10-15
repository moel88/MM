package dev.sangwoo.celestialfall.client.title;
import java.util.Objects;
/** Once per authorized map entry, not every tick, respawn or settings return. */
public final class MapEntryGate {
    private String map;
    private boolean pending;
    public boolean update(String authorizedMap,boolean ready){
        if(authorizedMap==null){reset();return false;}
        if(!Objects.equals(map,authorizedMap)){map=authorizedMap;pending=true;}
        if(!pending||!ready)return false;
        pending=false;return true;
    }
    public void reset(){map=null;pending=false;}
}
