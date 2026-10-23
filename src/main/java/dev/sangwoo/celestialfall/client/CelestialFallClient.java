package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.SpellTimeline;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import java.util.*;

public final class CelestialFallClient implements ClientModInitializer {
    public static final Map<UUID, ClientSpell> SPELLS = new LinkedHashMap<>();
    private static final List<PositionedSoundInstance> SOUNDS = new ArrayList<>();
    public static Collection<ClientSpell> spells() { return SPELLS.values(); }
    @Override public void onInitializeClient() {
        dev.sangwoo.celestialfall.client.integration.PlayerAnimatorBridge.initialize();
        dev.sangwoo.celestialfall.client.integration.VeilBridge.initialize();
        dev.sangwoo.celestialfall.client.integration.AtomicOverwhelm.initialize();
        dev.sangwoo.celestialfall.client.integration.CinematicsBridge.initialize();
        dev.sangwoo.celestialfall.client.integration.IntegrationCommands.initialize();
        dev.sangwoo.celestialfall.client.title.TitlePortal.initialize();
        MapClient.initialize();
        dev.sangwoo.celestialfall.client.town.TownBlocksClient.initialize();
        TrainingDummyRenderer.initialize();
        CombatClient.initialize();
        StandClient.initialize();
        CooldownHud.initialize();
        dev.sangwoo.celestialfall.client.ui.PlayerPanel.initialize();
        ProgressClient.initialize();
        dev.sangwoo.celestialfall.client.commerce.CommerceClient.initialize();
        dev.sangwoo.celestialfall.client.loot.FieldLootClient.initialize();
        dev.sangwoo.celestialfall.client.meteor.MeteorClient.initialize();
        dev.sangwoo.celestialfall.client.menu.MenuClient.initialize();
        dev.sangwoo.celestialfall.client.gacha.GachaClient.initialize();
        RecoveryRenderer.initialize();
        CinematicDirector.initialize();
        AtomicClient.initialize();
        HeavenClient.initialize();
        ClientPlayNetworking.registerGlobalReceiver(CelestialFall.CAST_PACKET, (client, handler, buf, response) -> {
            UUID id = buf.readUuid(); Identifier dimension = buf.readIdentifier();
            Vec3d target = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            long start = buf.readLong(); float yaw = buf.readFloat(); UUID caster = buf.readUuid();
            client.execute(() -> {
                if (!MapClient.enabled() || client.world == null || !client.world.getRegistryKey().getValue().equals(dimension)) return;
                if (SPELLS.size() >= 8 && !SPELLS.containsKey(id)) SPELLS.remove(SPELLS.keySet().iterator().next());
                int age = (int) Math.max(0, client.world.getTime() - start);
                if (age < SpellTimeline.END && !SPELLS.containsKey(id)) {
                    ClientSpell spell=new ClientSpell(id, dimension, target, start, yaw, caster, age);
                    SPELLS.put(id,spell);
                    CinematicDirector.start(spell,age);
                    if(age>=SpellTimeline.IMPACT)ImpactEffects.explode(client,spell);
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(CelestialFall.CLEAR_PACKET,
            (client, handler, buf, response) -> client.execute(() -> clear(client)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(()->clear(client)));
        ClientTickEvents.END_CLIENT_TICK.register(CelestialFallClient::tick);
        WorldRenderEvents.LAST.register(CelestialRenderer::renderWorld);
        HudRenderCallback.EVENT.register(CelestialHud::render);
    }

    public static ClientSpell nearest() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return null;
        ClientSpell best = null; double distance = Double.MAX_VALUE;
        for (ClientSpell spell : spells()) {
            if (!spell.dimension.equals(mc.world.getRegistryKey().getValue())) continue;
            double d = mc.player.getPos().squaredDistanceTo(spell.target);
            if (d < distance) { best = spell; distance = d; }
        }
        return best;
    }

    private static void tick(MinecraftClient mc) {
        if (!MapClient.enabled()) { clear(mc); return; }
        CinematicDirector.tick();
        if (mc.isPaused()) return;
        SOUNDS.removeIf(sound -> !mc.getSoundManager().isPlaying(sound));
        Iterator<ClientSpell> iterator = SPELLS.values().iterator();
        while (iterator.hasNext()) {
            ClientSpell spell = iterator.next();
            int age = (int) spell.age(mc.world.getTime(), 0);
            if (!spell.dimension.equals(mc.world.getRegistryKey().getValue()) || age >= SpellTimeline.END
                || mc.player.getPos().squaredDistanceTo(spell.target) > 660 * 660) { iterator.remove(); continue; }
            if(age>=30 && age<SpellTimeline.IMPACT) ImpactEffects.prepare(mc,spell);
            if (spell.lastAge < 1 && age >= 1 && age < 20) play(mc, spell, CelestialFall.CHARGE, 1);
            for(int clash=0;clash<SpellTimeline.CLASH_COUNT;clash++)
                if(spell.lastAge<SpellTimeline.clashTick(clash) && age>=SpellTimeline.clashTick(clash))
                    play(mc,spell,CelestialFall.CLASH,1.0f-clash*.08f);
            if(spell.lastAge<SpellTimeline.REVEAL && age>=SpellTimeline.REVEAL)play(mc,spell,CelestialFall.REVEAL,1);
            if(spell.lastAge<SpellTimeline.SILENCE_START && age>=SpellTimeline.SILENCE_START) {
                for(PositionedSoundInstance sound:SOUNDS)mc.getSoundManager().stop(sound);
                SOUNDS.clear();
            }
            for(int wave=1;wave<SpellTimeline.WAVE_COUNT;wave++)
                if(spell.lastAge<SpellTimeline.waveTick(wave) && age>=SpellTimeline.waveTick(wave))
                    play(mc,spell,CelestialFall.AFTERSHOCK,1.02f-wave*.08f);
            if (spell.lastAge < SpellTimeline.DESCENT_START && age >= SpellTimeline.DESCENT_START)
                play(mc, spell, CelestialFall.FALL, 1);
            if (spell.lastAge < SpellTimeline.IMPACT && age >= SpellTimeline.IMPACT) {
                play(mc, spell, CelestialFall.IMPACT_SOUND, 1);
                ImpactEffects.explode(mc,spell);
                int count = CelestialFall.CONFIG.visualQuality == 0 ? 30 : CelestialFall.CONFIG.visualQuality == 1 ? 60 : 90;
                for (int i = 0; i < count; i++) {
                    double a = i * Math.PI * 2 / count;
                    mc.world.addParticle(ParticleTypes.END_ROD, spell.target.x, spell.target.y + 0.5, spell.target.z,
                        Math.cos(a) * 0.8, 0.1 + mc.world.random.nextDouble() * 0.3, Math.sin(a) * 0.8);
                }
            }
            if (age < SpellTimeline.IMPACT && age % 3 == 0) {
                double angle = age * 0.15;
                mc.world.addParticle(ParticleTypes.END_ROD, spell.target.x + Math.cos(angle) * 3,
                    spell.target.y + 0.15, spell.target.z + Math.sin(angle) * 3, 0, 0.05, 0);
            }
            spell.lastAge = age;
        }
        ImpactEffects.tick(mc);
    }
    private static void play(MinecraftClient mc, ClientSpell spell, SoundEvent event, float pitch) {
        // Custom fall/charge are long sounds. Starting only at phase boundaries prevents replay spam.
        if (CelestialFall.CONFIG.effectVolume <= 0) return;
        double d = mc.player.getPos().distanceTo(spell.target);
        float volume = (float) (CelestialFall.CONFIG.effectVolume * Math.max(0, 1 - d / 640));
        if (volume <= 0) return;
        // Listener-relative audio stays audible while the cutscene camera travels hundreds of blocks.
        PositionedSoundInstance sound = new PositionedSoundInstance(event.getId(),SoundCategory.PLAYERS,volume,
            pitch,Random.create(),false,0,SoundInstance.AttenuationType.NONE,0,0,0,true);
        SOUNDS.add(sound);
        mc.getSoundManager().play(sound);
    }
    public static void clear(MinecraftClient mc) {
        SPELLS.clear();
        StandClient.clear(mc);
        AtomicClient.clear(mc);
        HeavenClient.clear(mc);
        CinematicDirector.clear();
        AnimePostProcessor.release();
        ImpactEffects.clear();
        for (PositionedSoundInstance sound : SOUNDS) mc.getSoundManager().stop(sound);
        SOUNDS.clear();
    }
}
