package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.AutoRespawn;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Dead on the Botand map (2.107.0, user request: "일단 죽으면 리스폰 화면이 뜨지 않고 자동으로 부활되게 하는데 죽으면 그
 * 자리에서 5초 동안 화면만 움직일수 있게 하다가 자동 부활"): Minecraft's death screen is not opened (DeathMessageMixin, and
 * MinecraftClientMixin where the game would open it of itself while the player is dead), nor is a respawn asked for:
 * the server brings them back after five seconds (AutoRespawn). Meanwhile they stay where they fell and only their view
 * moves: the camera turns (the right button held, the shift lock, the first person) and zooms, the shift lock and the
 * first person can be switched (BotandCamera.viewing), and nothing else goes through (CombatClient.actionLocked:
 * no walking, jumping, attacks, skills, items, hotbar, inventory or menu). The view does not roll (2.107.1, user
 * request: "화면이 기울어 지는 건 고치고": GameRendererMixin), nor does it close in (2.107.2, user request: "화면은 왜
 * 확대되는데", then to take it away: Minecraft narrows a dead body's view, {@link #unzoom}). Their body goes as Minecraft's does, a second on in a puff of
 * smoke (2.107.1: "시체가 원래 처럼 소멸하는 건 똑같게"), while the world about them goes on (DeadBodyMixin). The chat and
 * the pause menu still open. This holds only with a server that brings its dead back (it takes AutoRespawn's channel):
 * with an older one the death screen is Minecraft's, as that server waits for its button.
 */
public final class DeathView {
    /** (2.107.1) The body the death message was about: it counts as dead from that message, before its health is told. */
    private static ClientPlayerEntity died;
    private DeathView(){}
    public static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            // come back to life (a new body): the one that died is forgotten
            if(died!=null&&mc.player!=died)died=null;
            // one shown the death screen before the server said it brings its dead back (joined dead, before its
            // channels came): it closes, to wait as anyone does
            if(mc.currentScreen instanceof DeathScreen&&active())mc.setScreen(null);
        });
    }
    /** (2.107.1) The death message came for this player's body (DeathMessageMixin): from now it is dead to the view. */
    public static void died(ClientPlayerEntity player){died=player;}
    /** Whether this server brings its dead back by itself on the map this player is on (then no death screen opens). */
    public static boolean handled(){return MapClient.enabled()&&ClientPlayNetworking.canSend(AutoRespawn.CHANNEL);}
    /** Dead and waiting to be brought back: only the view moves. */
    public static boolean active(){var mc=MinecraftClient.getInstance();return mc.player!=null&&(mc.player.isDead()||mc.player==died)&&handled();}
    /**
     * (2.107.2) What Minecraft's view of {@code focused} is divided by as it lies dead (GameRenderer.getFov, hidden behind
     * the death screen till now: the field of view some 7% narrower a second on, the picture some 10% closer, and back at
     * once as they come back), to multiply it back by: 1 unless it is this player's body, dead and waiting to come back.
     */
    public static double unzoom(net.minecraft.entity.Entity focused,float delta){
        return focused instanceof net.minecraft.entity.LivingEntity body&&body==MinecraftClient.getInstance().player&&body.isDead()&&active()?narrowing(body.deathTime,delta):1;
    }
    /** (2.107.2) Minecraft's narrowing of a dead body's view at its death time (GameRenderer.getFov): 1 at the death, about 1.077 from a second on. */
    public static double narrowing(int deathTime,float delta){float f=Math.min((float)deathTime+delta,20f);return (1f-500f/(f+500f))*2f+1f;}
}
