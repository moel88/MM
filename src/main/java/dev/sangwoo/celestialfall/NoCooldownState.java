package dev.sangwoo.celestialfall;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;

/** Per-player switches saved with the world, independent of active casts and config reloads. */
public final class NoCooldownState extends PersistentState {
    private final Set<UUID> players=new HashSet<>();
    public static NoCooldownState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(NoCooldownState::read,NoCooldownState::new,"celestialfall_nocooldown");
    }
    public static boolean enabled(ServerPlayerEntity player) { return dev.sangwoo.celestialfall.map.MapGate.enabled(player.getWorld())&&get(player.getServer()).contains(player.getUuid()); }
    public boolean contains(UUID id) { return players.contains(id); }
    public void set(UUID id,boolean enabled) {
        boolean changed=enabled?players.add(id):players.remove(id);
        if(changed)markDirty();
    }
    public static NoCooldownState read(NbtCompound nbt) {
        var state=new NoCooldownState();var ids=nbt.getList("Players",NbtElement.STRING_TYPE);
        for(int i=0;i<ids.size();i++)try { state.players.add(UUID.fromString(ids.getString(i))); }
            catch(IllegalArgumentException ignored) { /* Ignore one damaged entry, keep other players. */ }
        return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt) {
        var ids=new NbtList();players.stream().map(UUID::toString).sorted().forEach(id->ids.add(NbtString.of(id)));
        nbt.put("Players",ids);return nbt;
    }
    public static void reset(ServerPlayerEntity player) {
        dev.sangwoo.celestialfall.stand.KagenoManager.resetCooldown(player);
        SpellManager.resetCooldown(player);AtomicManager.resetCooldown(player);HeavenManager.resetCooldown(player);
        // (2.77.0) Lee So-sang's attack's rest
        dev.sangwoo.celestialfall.isosang.IsosangAttack.forget(player);
    }
    public static void tick(MinecraftServer server) {
        var state=get(server);if(state.players.isEmpty())return;
        for(var player:server.getPlayerManager().getPlayerList())if(dev.sangwoo.celestialfall.map.MapGate.enabled(player.getWorld())&&state.contains(player.getUuid()))reset(player);
    }
}
