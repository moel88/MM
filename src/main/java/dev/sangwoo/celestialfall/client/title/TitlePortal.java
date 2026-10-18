package dev.sangwoo.celestialfall.client.title;

import dev.sangwoo.celestialfall.client.MapClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.multiplayer.*;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

/** Server-authorized map entry screen. Minecraft's launch title is never replaced. */
public final class TitlePortal {
    private static final MapEntryGate entry=new MapEntryGate();
    public static void initialize(){
        TitlePreferences.load();
        ClientPlayConnectionEvents.JOIN.register((handler,sender,mc)->entry.reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->entry.reset());
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            boolean allowed=MapClient.enabled();
            String map=allowed?mc.world.getRegistryKey().getValue().toString():null;
            boolean ready=allowed&&mc.player.isAlive()&&mc.currentScreen==null&&mc.getOverlay()==null;
            if(entry.update(map,ready)){
                KeyBinding.unpressAll();
                mc.setScreen(new BotandTitleScreen());
            }else if(!allowed&&mc.world!=null&&mc.currentScreen instanceof BotandTitleScreen){
                mc.setScreen(null);
            }
        });
    }
    /** Use vanilla's save/disconnect path before showing an out-of-world menu. */
    public static void leaveMap(MinecraftClient mc,boolean servers){
        boolean singleplayer=mc.isInSingleplayer();
        if(mc.world!=null)mc.world.disconnect();
        if(singleplayer)mc.disconnect(new MessageScreen(Text.translatable("menu.savingLevel")));
        else mc.disconnect();
        entry.reset();
        Screen parent=new TitleScreen(false);
        mc.setScreen(servers&&mc.isMultiplayerEnabled()
            ?(mc.options.skipMultiplayerWarning?new MultiplayerScreen(parent):new MultiplayerWarningScreen(parent))
            :parent);
    }
    private TitlePortal(){}
}
