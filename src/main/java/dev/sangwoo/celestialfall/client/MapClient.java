package dev.sangwoo.celestialfall.client;
import dev.sangwoo.celestialfall.map.MapGate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/** Defaults off, including on other servers. Only this server's dimension-scoped message turns it on. */
public final class MapClient {
    private static Identifier dimension;
    private static boolean allowed;
    private static Object world;
    private MapClient() {}
    public static boolean enabled(){var mc=MinecraftClient.getInstance();return allowed&&mc.world!=null&&mc.player!=null&&mc.world.getRegistryKey().getValue().equals(dimension);}
    public static void initialize() {
        MapGate.setClientCheck(w->enabled()&&w==MinecraftClient.getInstance().world);
        MapControls.initialize();
        ClientPlayNetworking.registerGlobalReceiver(MapGate.STATE,(mc,handler,b,response)->{
            var d=b.readIdentifier();boolean on=b.readBoolean();
            mc.execute(()->{
                if(allowed!=on||!d.equals(dimension)||world!=mc.world){allowed=false;clearPresentation(mc);}
                dimension=d;allowed=on;world=mc.world;
            });
        });
        ClientTickEvents.START_CLIENT_TICK.register(mc->{
            if(world!=mc.world){clearPresentation(mc);world=mc.world;}
            MapControls.tick(mc,enabled()&&!mc.player.isSpectator());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(()->{allowed=false;dimension=null;world=null;clearPresentation(mc);}));
    }
    private static void clearPresentation(MinecraftClient mc) {
        dev.sangwoo.celestialfall.client.menu.MenuClient.reset();CelestialFallClient.clear(mc);CombatClient.clear();HitStunClient.clear();ProgressClient.clear();BotandCamera.reset(mc);MapControls.exit();
    }
}
