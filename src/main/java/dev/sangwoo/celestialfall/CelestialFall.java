package dev.sangwoo.celestialfall;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import java.util.Collection;
import java.util.List;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CelestialFall implements ModInitializer {
    public static final String ID = "celestialfall";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);
    public static final Identifier CAST_PACKET = id("cast_v4");
    public static final Identifier HEAVEN_PACKET=id("heaven_v1"), HEAVEN_STOP_PACKET=id("heaven_stop");
    public static final Identifier ATOMIC_PACKET = id("atomic_v2");
    public static final Identifier ATOMIC_STOP_PACKET = id("atomic_stop");
    public static final Identifier CLEAR_PACKET = id("clear");
    public static final SoundEvent CHARGE = sound("charge");
    public static final SoundEvent AFTERSHOCK = sound("aftershock");
    public static final SoundEvent REVEAL = sound("reveal");
    public static final SoundEvent CLASH = sound("clash");
    public static final SoundEvent FALL = sound("fall");
    public static final SoundEvent IMPACT_SOUND = sound("impact");
    public static final Item HEAVEN_SWORD=new HeavenSwordItem();
    public static final SoundEvent HEAVEN_CHARGE=sound("heaven_charge"), HEAVEN_IMPACT=sound("heaven_impact"), HEAVEN_RELEASE=sound("heaven_release");
    public static final SoundEvent HEAVEN_FULL=sound("heaven_full");
    public static final Item ATOMIC_SWORD = new AtomicSwordItem();
    public static final SoundEvent KAGENO_THEME=sound("kageno_theme");
    public static final SoundEvent ATOMIC_FULL=sound("atomic_full");
    public static final SoundEvent ATOMIC_CLIP = sound("atomic_clip");
    public static final SoundEvent ATOMIC_CHARGE = sound("atomic_charge");
    public static final SoundEvent ATOMIC_IMPACT = sound("atomic_impact");
    public static final Item ASTRAL_SIGIL = new AstralSigilItem(new Item.Settings().maxCount(1).fireproof());
    public static ModConfig CONFIG;
    public static Identifier id(String path) { return new Identifier(ID, path); }
    private static SoundEvent sound(String name) { return SoundEvent.of(id(name)); }

    @Override public void onInitialize() {
        CONFIG = ModConfig.load();
        dev.sangwoo.celestialfall.map.MapGate.initialize();
        ((dev.sangwoo.celestialfall.mixin.HealthAttributeAccess)net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).botand$setMaxValue(100_000);
        dev.sangwoo.celestialfall.training.TrainingDummies.initialize();
        dev.sangwoo.celestialfall.combat.CombatManager.initialize();
        dev.sangwoo.celestialfall.progression.ProgressManager.initialize();
        dev.sangwoo.celestialfall.stand.StandManager.initialize();
        dev.sangwoo.celestialfall.menu.MenuServer.initialize();
        dev.sangwoo.celestialfall.commerce.CommerceRegistry.initialize();
        dev.sangwoo.celestialfall.loot.LootRegistry.initialize();
        dev.sangwoo.celestialfall.terrain.TerrainRestoration.initialize();
        Registry.register(Registries.ITEM,id("heaven_sword"),HEAVEN_SWORD);
        Registry.register(Registries.SOUND_EVENT,HEAVEN_CHARGE.getId(),HEAVEN_CHARGE);
        Registry.register(Registries.SOUND_EVENT,HEAVEN_IMPACT.getId(),HEAVEN_IMPACT);
        Registry.register(Registries.SOUND_EVENT,HEAVEN_RELEASE.getId(),HEAVEN_RELEASE);
        Registry.register(Registries.ITEM,id("atomic_sword"),ATOMIC_SWORD);
        Registry.register(Registries.SOUND_EVENT,ATOMIC_FULL.getId(),ATOMIC_FULL);
        Registry.register(Registries.SOUND_EVENT,KAGENO_THEME.getId(),KAGENO_THEME);
        Registry.register(Registries.SOUND_EVENT,ATOMIC_CLIP.getId(),ATOMIC_CLIP);
        Registry.register(Registries.SOUND_EVENT,ATOMIC_CHARGE.getId(),ATOMIC_CHARGE);
        Registry.register(Registries.SOUND_EVENT,ATOMIC_IMPACT.getId(),ATOMIC_IMPACT);
        Registry.register(Registries.ITEM, id("astral_sigil"), ASTRAL_SIGIL);
        Registry.register(Registries.SOUND_EVENT, CHARGE.getId(), CHARGE);
        Registry.register(Registries.SOUND_EVENT, FALL.getId(), FALL);
        Registry.register(Registries.SOUND_EVENT, CLASH.getId(), CLASH);
        Registry.register(Registries.SOUND_EVENT, AFTERSHOCK.getId(), AFTERSHOCK);
        Registry.register(Registries.SOUND_EVENT, REVEAL.getId(), REVEAL);
        Registry.register(Registries.SOUND_EVENT, IMPACT_SOUND.getId(), IMPACT_SOUND);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> { entries.add(ASTRAL_SIGIL); entries.add(ATOMIC_SWORD); entries.add(HEAVEN_SWORD); });
        ServerTickEvents.END_SERVER_TICK.register(SpellManager::tick);
        ServerTickEvents.END_SERVER_TICK.register(AtomicManager::tick);
        ServerTickEvents.END_SERVER_TICK.register(HeavenManager::tick);
        ServerTickEvents.END_SERVER_TICK.register(NoCooldownState::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { SpellManager.clear(); AtomicManager.clear(); HeavenManager.clear(); });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            dispatcher.register(CommandManager.literal("celestialfall").requires(source -> source.hasPermissionLevel(2))
                .then(dev.sangwoo.celestialfall.training.TrainingDummies.commands())
                .then(CommandManager.literal("give").executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    player.giveItemStack(ASTRAL_SIGIL.getDefaultStack());
                    return 1;
                }))
                .then(CommandManager.literal("cast").executes(context ->
                    SpellManager.cast(context.getSource().getPlayerOrThrow()) ? 1 : 0))
                .then(CommandManager.literal("heaven").executes(context -> HeavenManager.cast(context.getSource().getPlayerOrThrow())?1:0))
                .then(CommandManager.literal("giveheaven").executes(context -> { context.getSource().getPlayerOrThrow().giveItemStack(HEAVEN_SWORD.getDefaultStack());return 1; }))
                .then(CommandManager.literal("atomic").executes(context -> AtomicManager.cast(context.getSource().getPlayerOrThrow())?1:0))
                .then(CommandManager.literal("giveatomic").executes(context -> {
                    var player=context.getSource().getPlayerOrThrow();player.giveItemStack(ATOMIC_SWORD.getDefaultStack());
                    dev.sangwoo.celestialfall.progression.ProgressManager.set(player,dev.sangwoo.celestialfall.progression.ProgressManager.profile(player).withStand("kageno"));return 1;
                }))
                .then(CommandManager.literal("resetcooldown")
                    .executes(context -> resetCooldowns(context.getSource(),List.of(context.getSource().getPlayerOrThrow())))
                    .then(CommandManager.argument("targets",EntityArgumentType.players())
                        .executes(context -> resetCooldowns(context.getSource(),EntityArgumentType.getPlayers(context,"targets")))))
                .then(CommandManager.literal("nocooldown")
                    .executes(context -> {
                        boolean on=NoCooldownState.enabled(context.getSource().getPlayerOrThrow());
                        context.getSource().sendFeedback(() -> Text.translatable("message.celestialfall.nocooldown_status",
                            Text.translatable(on?"options.on":"options.off")),false);return on?1:0;
                    })
                    .then(noCooldownMode("on",true)).then(noCooldownMode("off",false)))
                .then(CommandManager.literal("clear").executes(context -> {
                    SpellManager.clear(context.getSource().getServer());
                    AtomicManager.clear(context.getSource().getServer());
                    HeavenManager.clear(context.getSource().getServer());
                    dev.sangwoo.celestialfall.stand.KagenoManager.clear(context.getSource().getServer());
                    context.getSource().sendFeedback(() -> Text.translatable("message.celestialfall.cleared"), false);
                    return 1;
                }))
                .then(CommandManager.literal("reload").executes(context -> {
                    CONFIG = ModConfig.load();
        dev.sangwoo.celestialfall.map.MapGate.initialize();
                    context.getSource().sendFeedback(() -> Text.translatable("message.celestialfall.reloaded"), false);
                    return 1;
                })));
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            var botand=CommandManager.literal("botand").then(dev.sangwoo.celestialfall.map.MapGate.commands());
            // Preserve admin permissions on every old command while allowing self-service progression.
            for(var node:dispatcher.getRoot().getChild("celestialfall").getChildren()) {
                var alias=CommandManager.literal(node.getName()).requires(source->source.hasPermissionLevel(2));
                if(node.getCommand()!=null)alias.executes(node.getCommand());
                for(var child:node.getChildren())alias.then(child);
                botand.then(alias);
            }
            dev.sangwoo.celestialfall.progression.ProgressCommands.add(botand);
            dev.sangwoo.celestialfall.commerce.CommerceCommands.add(botand);
            dispatcher.register(botand);
        });
        LOGGER.info("Botand {} initialized",net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("celestialfall").map(m->m.getMetadata().getVersion().getFriendlyString()).orElse("?"));
    }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> noCooldownMode(String name,boolean enabled) {
        return CommandManager.literal(name)
            .executes(context -> setNoCooldown(context.getSource(),List.of(context.getSource().getPlayerOrThrow()),enabled))
            .then(CommandManager.argument("targets",EntityArgumentType.players())
                .executes(context -> setNoCooldown(context.getSource(),EntityArgumentType.getPlayers(context,"targets"),enabled)));
    }
    private static int setNoCooldown(ServerCommandSource source,Collection<ServerPlayerEntity> players,boolean enabled) {
        var state=NoCooldownState.get(source.getServer());
        for(var player:players) { state.set(player.getUuid(),enabled);if(enabled)NoCooldownState.reset(player); }
        source.sendFeedback(() -> Text.translatable(enabled?"message.celestialfall.nocooldown_on":"message.celestialfall.nocooldown_off",players.size()),false);
        return players.size();
    }
    private static int resetCooldowns(ServerCommandSource source,Collection<ServerPlayerEntity> players) {
        for(var player:players) {
            SpellManager.resetCooldown(player);
            AtomicManager.resetCooldown(player);
            HeavenManager.resetCooldown(player);
        }
        source.sendFeedback(() -> Text.translatable("message.celestialfall.cooldowns_reset",players.size()),false);
        return players.size();
    }
}
