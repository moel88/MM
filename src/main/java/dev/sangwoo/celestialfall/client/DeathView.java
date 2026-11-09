package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.AutoRespawn;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;

/**
 * Dead on the Botand map (2.107.0, user request: "일단 죽으면 리스폰 화면이 뜨지 않고 자동으로 부활되게 하는데 죽으면 그
 * 자리에서 5초 동안 화면만 움직일수 있게 하다가 자동 부활"): Minecraft's death screen is not opened (DeathMessageMixin, and
 * MinecraftClientMixin where the game would open it of itself while the player is dead), nor is a respawn asked for:
 * the server brings them back after five seconds (AutoRespawn). Meanwhile they stay where they fell and only their view
 * moves: the camera turns (the right button held, the shift lock, the first person) and zooms, the shift lock and the
 * first person can be switched (BotandCamera.viewing), and nothing else goes through (CombatClient.actionLocked:
 * no walking, jumping, attacks, skills, items, hotbar, inventory or menu). The body is not turned with the view. The
 * chat and the pause menu still open. This holds only with a server that brings its dead back (it takes AutoRespawn's
 * channel): with an older one the death screen is Minecraft's, as that server waits for its button.
 */
public final class DeathView {
    private DeathView(){}
    public static void initialize(){
        // one shown the death screen before the server said it brings its dead back (joined dead, before its channels
        // came): it closes, to wait as anyone does
        ClientTickEvents.END_CLIENT_TICK.register(mc->{if(mc.currentScreen instanceof DeathScreen&&active())mc.setScreen(null);});
    }
    /** Whether this server brings its dead back by itself on the map this player is on (then no death screen opens). */
    public static boolean handled(){return MapClient.enabled()&&ClientPlayNetworking.canSend(AutoRespawn.CHANNEL);}
    /** Dead and waiting to be brought back: only the view moves. */
    public static boolean active(){var mc=MinecraftClient.getInstance();return mc.player!=null&&mc.player.isDead()&&handled();}
}
