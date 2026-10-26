package dev.sangwoo.celestialfall.client.boss;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.boss.Bosses;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

/**
 * The bosses this client knows of (2.69.0, Bosses' boss_list_v1): which players in its world are bosses, of which
 * kind, their names as the boss bar shows them, how far their anger reaches and whether it is up. Their bodies are
 * drawn as players are, in their own skin with their outfit (BossOutfit), with no name over them (the bar has it) and
 * no line in the player list.
 */
public final class BossClient {
    /** One the server told of. */
    public record Info(UUID id,String kind,String title,float range,boolean angry){}
    private static final Map<UUID,Info> KNOWN=new LinkedHashMap<>();
    private BossClient(){}
    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(Bosses.LIST,(client,handler,buf,sender)->{
            int n=buf.readVarInt();var list=new LinkedHashMap<UUID,Info>();
            for(int i=0;i<n;i++){var id=buf.readUuid();var kind=buf.readString(32);var title=buf.readString(64);float range=buf.readFloat();boolean angry=buf.readBoolean();list.put(id,new Info(id,kind,title,range,angry));}
            client.execute(()->{KNOWN.clear();KNOWN.putAll(list);});
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->KNOWN.clear());
        BossBar.initialize();
        BossOutfit.initialize();
    }
    public static boolean isBoss(UUID id){return id!=null&&KNOWN.containsKey(id);}
    public static boolean isBoss(Entity e){return e!=null&&KNOWN.containsKey(e.getUuid());}
    public static Info info(UUID id){return KNOWN.get(id);}
    public static Collection<Info> all(){return Collections.unmodifiableCollection(KNOWN.values());}
    /** The skin a boss's body is drawn in (null for anyone else). */
    public static Identifier skin(UUID id){var i=KNOWN.get(id);return i==null?null:CelestialFall.id("textures/entity/boss/"+i.kind()+".png");}
}
