package dev.sangwoo.celestialfall.mixin;
import dev.sangwoo.celestialfall.combat.CombatManager;
import net.minecraft.server.network.*;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server-thread gates cover vanilla attacks, item use and inventory actions, not just custom packets. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class GuardBreakNetworkMixin {
    @Shadow public ServerPlayerEntity player;
    @Shadow protected abstract void updateSequence(int sequence);
    @Unique private static final String MAIN="Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V";
    @Inject(method="onPlayerMove",at=@At(value="INVOKE",target=MAIN,shift=At.Shift.AFTER),cancellable=true)
    private void botand$hitstunMovement(PlayerMoveC2SPacket packet,CallbackInfo ci) {
        if(dev.sangwoo.celestialfall.stand.KagenoManager.frozen(player)){
            dev.sangwoo.celestialfall.stand.KagenoManager.freeze(player);
            if(packet.changesPosition()&&(Math.abs(packet.getX(player.getX())-player.getX())>1e-6||Math.abs(packet.getY(player.getY())-player.getY())>1e-6||Math.abs(packet.getZ(player.getZ())-player.getZ())>1e-6))player.networkHandler.requestTeleport(player.getX(),player.getY(),player.getZ(),player.getYaw(),player.getPitch());
            ci.cancel();return;
        }
        if(!dev.sangwoo.celestialfall.combat.HitStun.stunned(player))return;
        double x=packet.getX(player.getX()),y=packet.getY(player.getY()),z=packet.getZ(player.getZ());
        float yaw=packet.getYaw(player.getYaw()),pitch=packet.getPitch(player.getPitch());
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yaw)||!Float.isFinite(pitch))return;
        if(Math.abs(x-player.getX())>1e-6||Math.abs(z-player.getZ())>1e-6||y>player.getY()+1e-6) {
            dev.sangwoo.celestialfall.combat.HitStun.freeze(player);
            player.networkHandler.requestTeleport(player.getX(),player.getY(),player.getZ(),yaw,net.minecraft.util.math.MathHelper.clamp(pitch,-90,90));ci.cancel();
        }
    }
    @Inject(method={"onPlayerInteractEntity","onHandSwing","onPickFromInventory","onClickSlot","onCraftRequest","onButtonClick","onCreativeInventoryAction","onUpdatePlayerAbilities"},
        at=@At(value="INVOKE",target=MAIN,shift=At.Shift.AFTER),cancellable=true)
    private void botand$lockedAction(CallbackInfo ci) {
        if(CombatManager.actionLocked(player)){player.currentScreenHandler.syncState();player.sendAbilitiesUpdate();ci.cancel();}
    }
    @Inject(method="onPlayerAction",at=@At(value="INVOKE",target=MAIN,shift=At.Shift.AFTER),cancellable=true)
    private void botand$action(PlayerActionC2SPacket packet,CallbackInfo ci) {
        if(CombatManager.actionLocked(player)){
            updateSequence(packet.getSequence());player.currentScreenHandler.syncState();
            player.networkHandler.sendPacket(new BlockUpdateS2CPacket(player.getWorld(),packet.getPos()));ci.cancel();
        }
    }
    @Inject(method="onPlayerInteractBlock",at=@At(value="INVOKE",target=MAIN,shift=At.Shift.AFTER),cancellable=true)
    private void botand$block(PlayerInteractBlockC2SPacket packet,CallbackInfo ci) {
        if(CombatManager.actionLocked(player)){
            updateSequence(packet.getSequence());var hit=packet.getBlockHitResult();
            player.networkHandler.sendPacket(new BlockUpdateS2CPacket(player.getWorld(),hit.getBlockPos()));
            player.networkHandler.sendPacket(new BlockUpdateS2CPacket(player.getWorld(),hit.getBlockPos().offset(hit.getSide())));
            player.currentScreenHandler.syncState();ci.cancel();
        }
    }
    @Inject(method="onPlayerInteractItem",at=@At(value="INVOKE",target=MAIN,shift=At.Shift.AFTER),cancellable=true)
    private void botand$item(PlayerInteractItemC2SPacket packet,CallbackInfo ci) {
        if(CombatManager.actionLocked(player)){updateSequence(packet.getSequence());player.currentScreenHandler.syncState();ci.cancel();}
    }
    @Inject(method="onUpdateSelectedSlot",at=@At(value="INVOKE",target=MAIN,shift=At.Shift.AFTER),cancellable=true)
    private void botand$slot(UpdateSelectedSlotC2SPacket packet,CallbackInfo ci) {
        if(CombatManager.actionLocked(player)){player.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(player.getInventory().selectedSlot));ci.cancel();}
    }
    @Inject(method="onClientCommand",at=@At(value="INVOKE",target=MAIN,shift=At.Shift.AFTER),cancellable=true)
    private void botand$command(ClientCommandC2SPacket packet,CallbackInfo ci) {
        if(dev.sangwoo.celestialfall.map.MapGate.enabled(player.getWorld())&&packet.getMode()==ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY&&!player.hasVehicle()&&!player.getAbilities().flying){player.setSneaking(false);ci.cancel();return;}
        if(packet.getMode()==ClientCommandC2SPacket.Mode.STOP_SPRINTING)CombatManager.runRequest(player,false);
        if(CombatManager.actionLocked(player)){
            // A client that starts running as the action ends (its clock can be a tick ahead) runs when the lock lets go.
            if(packet.getMode()==ClientCommandC2SPacket.Mode.START_SPRINTING)CombatManager.runRequest(player,true);
            player.setSprinting(false);ci.cancel();
        }
    }
}
