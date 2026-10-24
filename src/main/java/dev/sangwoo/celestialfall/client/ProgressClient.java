package dev.sangwoo.celestialfall.client;
import dev.sangwoo.celestialfall.progression.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
public final class ProgressClient {
    public static ProgressProfile profile=ProgressProfile.NEW;
    public static boolean ready;
    private ProgressClient() {}
    public static void clear(){profile=ProgressProfile.NEW;ready=false;}
    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(ProgressManager.STATE,(mc,handler,b,response)->{
            var snapshot=new ProgressProfile(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readString(32),b.readLong(),b.readVarInt());
            mc.execute(()->{if(MapClient.enabled()){profile=snapshot;ready=true;}});
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->{profile=ProgressProfile.NEW;ready=false;});
        // Draw inside InGameHud before DebugHud; Fabric's HUD tail would cover F3.
    }
}
