package dev.sangwoo.celestialfall;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModConfig {
    public double heavenDamage = 1000;
    public double heavenImpactRadius = 96;
    public int heavenCooldownSeconds = 45;
    public double atomicVisualRadius = 28;
    public double atomicDamagePerSecond = 50;
    public int atomicCooldownSeconds = 60;
    public boolean atomicCostume = true;
    public int cooldownSeconds = 40;
    public double damage = 200;
    public double impactRadius = 96;
    public int castRange = 128;
    public boolean terrainDamage = true;
    public double terrainRadiusLimit = 48;
    public int terrainDepth = 10;
    public int terrainAboveGround = 20;
    public boolean damagePlayers = false;
    public boolean hurtCaster = false;
    public int maxActiveCasts = 1;
    public int configVersion = 7;
    public boolean postProcessing = true;
    public boolean screenDistortion = true;
    public boolean animeImpactFrames = true;
    public boolean speedLines = true;
    public double bloomStrength = 1.35;
    public int visualQuality = 2;
    public boolean screenFlash = true;
    public boolean skyEffect = true;
    public boolean titleOverlay = true;
    public double effectVolume = 0.8;
    public boolean cinematicCamera = true;
    public double screenShakeStrength = 0.65;
    public boolean flyingBlocks = true;
    public boolean explosionSmoke = true;
    public boolean purpleGlow = true;
    public double purpleGlowStrength = 0.8;

    public void normalize() {
        heavenDamage = finiteClamp(heavenDamage,0,1000,1000);
        heavenImpactRadius = finiteClamp(heavenImpactRadius,2,160,96);
        heavenCooldownSeconds = Math.max(11,Math.min(300,heavenCooldownSeconds));
        atomicVisualRadius = finiteClamp(atomicVisualRadius,8,768,28);
        atomicDamagePerSecond = finiteClamp(atomicDamagePerSecond,0,1000,50);
        atomicCooldownSeconds = Math.max(10,Math.min(300,atomicCooldownSeconds));
        cooldownSeconds = Math.max(5, Math.min(300, cooldownSeconds));
        damage = finiteClamp(damage, 0, 1000, 200);
        impactRadius = finiteClamp(impactRadius, 2, 160, 96);
        castRange = Math.max(8, Math.min(256, castRange));
        maxActiveCasts = Math.max(1, Math.min(4, maxActiveCasts));
        terrainRadiusLimit = finiteClamp(terrainRadiusLimit,4,64,48);
        terrainDepth = Math.max(1,Math.min(16,terrainDepth));
        terrainAboveGround = Math.max(0,Math.min(32,terrainAboveGround));
        bloomStrength = finiteClamp(bloomStrength,0,2.5,1.35);
        visualQuality = Math.max(0, Math.min(2, visualQuality));
        effectVolume = finiteClamp(effectVolume, 0, 1, 0.8);
        screenShakeStrength = finiteClamp(screenShakeStrength, 0, 1, 0.65);
        purpleGlowStrength = finiteClamp(purpleGlowStrength, 0, 1, 0.8);
    }
    private static double finiteClamp(double x, double low, double high, double fallback) {
        return Double.isFinite(x) ? Math.max(low, Math.min(high, x)) : fallback;
    }
    public void applyAnimeUpgrade() {
        configVersion=Math.max(4,configVersion);terrainDamage=false;
    }
    public void applyHeavenUpgrade() {
        if(configVersion<5 && heavenDamage==200)heavenDamage=1000;
        configVersion=Math.max(5,configVersion);
    }
    /** Version 5 could not represent an arena-sized radius (minimum 128). */
    public void applyArenaUpgrade() {
        if(configVersion<6)atomicVisualRadius=28;
        configVersion=Math.max(6,configVersion);
    }
    public void applyTerrainUpgrade() {
        if(configVersion<7)terrainDamage=true;
        configVersion=Math.max(7,configVersion);
    }
    public static ModConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("celestialfall.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.exists(path)) {
                String original=Files.readString(path,StandardCharsets.UTF_8);
                var json=JsonParser.parseString(original).getAsJsonObject();
                ModConfig config = gson.fromJson(json, ModConfig.class);
                if (config == null) throw new IllegalArgumentException("Config must be an object");
                if(!json.has("configVersion"))config.configVersion=0;
                if(!json.has("configVersion") || config.configVersion<7) {
                    Path backup=path.resolveSibling("celestialfall-before-v280-"+System.currentTimeMillis()+".json");
                    Files.copy(path,backup);
                    config.applyAnimeUpgrade();
                    config.applyHeavenUpgrade();
                    config.applyArenaUpgrade();
                    config.applyTerrainUpgrade();
                    config.normalize();
                    Files.writeString(path,gson.toJson(config),StandardCharsets.UTF_8);
                    CelestialFall.LOGGER.info("Enabled reversible terrain; retained rendering and combat preferences. Previous config: {}",backup.getFileName());
                }
                config.normalize();
                if(!json.has("heavenDamage") || !json.has("heavenImpactRadius") || !json.has("heavenCooldownSeconds") || !json.has("atomicVisualRadius") || !json.has("atomicDamagePerSecond")
                    || !json.has("atomicCooldownSeconds") || !json.has("atomicCostume"))
                    Files.writeString(path,gson.toJson(config),StandardCharsets.UTF_8);
                return config;
            }
            ModConfig config = new ModConfig();
            Files.createDirectories(path.getParent());
            Files.writeString(path, gson.toJson(config), StandardCharsets.UTF_8);
            return config;
        } catch (Exception e) {
            CelestialFall.LOGGER.warn("Could not read celestialfall.json; using defaults without overwriting the file", e);
            return new ModConfig();
        }
    }
}
