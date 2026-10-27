package dev.sangwoo.celestialfall;

import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import static org.junit.jupiter.api.Assertions.*;

class ModConfigTest {
    @Test void heavenDefaultsExtendExistingConfigWithoutChangingAtomic() {
        ModConfig c=new Gson().fromJson("{\"configVersion\":4,\"atomicDamagePerSecond\":17,\"effectVolume\":0.2}",ModConfig.class);
        c.normalize();assertEquals(1000,c.heavenDamage);assertEquals(96,c.heavenImpactRadius);assertEquals(45,c.heavenCooldownSeconds);
        assertEquals(17,c.atomicDamagePerSecond);assertEquals(.2,c.effectVolume);
        c.heavenDamage=Double.NaN;c.heavenImpactRadius=Double.POSITIVE_INFINITY;c.heavenCooldownSeconds=0;c.normalize();
        assertEquals(1000,c.heavenDamage);assertEquals(96,c.heavenImpactRadius);assertEquals(11,c.heavenCooldownSeconds);
        c.heavenDamage=0;c.heavenImpactRadius=10000;c.normalize();assertEquals(0,c.heavenDamage);assertEquals(160,c.heavenImpactRadius);
    }
    @Test void heavenUpgradeRaisesOnlyTheOldDefaultAndKeepsExplicitOverrides() {
        ModConfig old=new Gson().fromJson("{\"configVersion\":4,\"heavenDamage\":200,\"atomicDamagePerSecond\":50}",ModConfig.class);
        old.applyHeavenUpgrade();old.normalize();assertEquals(1000,old.heavenDamage);assertEquals(5,old.configVersion);assertEquals(50,old.atomicDamagePerSecond);
        for(double custom:new double[]{0,300,750}) {
            ModConfig c=new ModConfig();c.configVersion=4;c.heavenDamage=custom;c.applyHeavenUpgrade();c.normalize();assertEquals(custom,c.heavenDamage);
        }
    }
    @Test void nonfiniteNumbersAndResourceLimitsAreSanitized() {
        ModConfig c=new ModConfig();
        c.damage=Double.NaN;c.impactRadius=Double.POSITIVE_INFINITY;c.effectVolume=Double.NEGATIVE_INFINITY;
        c.maxActiveCasts=Integer.MAX_VALUE;c.cooldownSeconds=-3;c.castRange=-1;c.visualQuality=50;c.normalize();
        assertEquals(200,c.damage);assertEquals(96,c.impactRadius);assertEquals(.8,c.effectVolume);
        assertEquals(4,c.maxActiveCasts);assertEquals(5,c.cooldownSeconds);assertEquals(8,c.castRange);assertEquals(2,c.visualQuality);
    }
    @Test void destructiveOldConfigMigratesWithoutLosingUserPreferences() {
        ModConfig c=new Gson().fromJson("{\"configVersion\":2,\"visualQuality\":0,\"effectVolume\":0.3,\"terrainDamage\":true,\"damage\":7,\"cinematicCamera\":false}",ModConfig.class);
        c.applyAnimeUpgrade();c.normalize();
        assertEquals(4,c.configVersion);assertFalse(c.terrainDamage);
        assertEquals(0,c.visualQuality);assertEquals(.3,c.effectVolume);assertEquals(7,c.damage);assertFalse(c.cinematicCamera);
        assertTrue(c.postProcessing && c.animeImpactFrames && c.screenDistortion && c.speedLines);
    }
    @Test void temporaryTerrainUpgradeIsOptOutAndKeepsRenderingPreferences() {
        ModConfig c=new Gson().fromJson("{\"configVersion\":3,\"terrainDamage\":true,\"terrainRadius\":999999,\"craterDepth\":9999}",ModConfig.class);
        c.applyTerrainUpgrade();c.normalize();assertTrue(c.terrainDamage);assertEquals(7,c.configVersion);
        c.terrainDamage=false;c.applyTerrainUpgrade();assertFalse(c.terrainDamage);
        c.terrainRadiusLimit=Double.NaN;c.terrainDepth=1000;c.terrainAboveGround=-1;c.normalize();
        assertEquals(48,c.terrainRadiusLimit);assertEquals(16,c.terrainDepth);assertEquals(0,c.terrainAboveGround);
    }
    @Test void intensitySettingsRejectNonfiniteValuesAndSupportOff() {
        ModConfig c=new ModConfig();
        c.screenShakeStrength=Double.NaN;c.purpleGlowStrength=Double.POSITIVE_INFINITY;c.bloomStrength=Double.NaN;c.normalize();
        assertEquals(.65,c.screenShakeStrength);assertEquals(.8,c.purpleGlowStrength);assertEquals(1.35,c.bloomStrength);
        c.screenShakeStrength=-5;c.purpleGlowStrength=5;c.bloomStrength=99;c.normalize();
        assertEquals(0,c.screenShakeStrength);assertEquals(1,c.purpleGlowStrength);assertEquals(2.5,c.bloomStrength);
        c.bloomStrength=-1;c.normalize();assertEquals(0,c.bloomStrength);
    }
    @Test void oldAtomicBurstMigratesTo50PerSecondWithVisualRadiusShared() {
        ModConfig c=new Gson().fromJson("{\"configVersion\":3,\"damage\":0,\"atomicDamage\":300,\"atomicDamageRadius\":128}",ModConfig.class);
        c.applyAnimeUpgrade();c.normalize();
        assertEquals(4,c.configVersion);assertEquals(28,c.atomicVisualRadius);assertEquals(50,c.atomicDamagePerSecond);
        assertEquals(0,c.damage);assertFalse(new Gson().toJson(c).contains("atomicDamageRadius"));
        c.atomicVisualRadius=Double.NaN;c.atomicDamagePerSecond=Double.POSITIVE_INFINITY;c.atomicCooldownSeconds=-1;
        c.normalize();assertEquals(28,c.atomicVisualRadius);assertEquals(50,c.atomicDamagePerSecond);assertEquals(10,c.atomicCooldownSeconds);
        c.atomicVisualRadius=100000;c.atomicDamagePerSecond=-5;c.normalize();
        assertEquals(768,c.atomicVisualRadius);assertEquals(0,c.atomicDamagePerSecond);assertFalse(c.terrainDamage);
    }
    @Test void arenaMigrationBacksOffOldGiantRadiusWithoutChangingUserRenderingChoices() {
        ModConfig c=new Gson().fromJson("{\"configVersion\":5,\"atomicVisualRadius\":384,\"postProcessing\":false,\"skyEffect\":false,\"screenDistortion\":false,\"effectVolume\":0.3}",ModConfig.class);
        c.applyArenaUpgrade();c.normalize();assertEquals(28,c.atomicVisualRadius);assertEquals(6,c.configVersion);
        assertFalse(c.postProcessing);assertFalse(c.skyEffect);assertFalse(c.screenDistortion);assertEquals(.3,c.effectVolume);
        c.atomicVisualRadius=18;c.applyArenaUpgrade();assertEquals(18,c.atomicVisualRadius);
        c.atomicVisualRadius=2;c.normalize();assertEquals(8,c.atomicVisualRadius);
    }
    /** 2.73.0 (user request: "카게노 아토믹 쿨타임은 10분으로"): ten minutes, also for a config saved with the old default. */
    @Test void atomicCooldownIsTenMinutesAndTheOldDefaultMigrates() {
        assertEquals(600,new ModConfig().atomicCooldownSeconds,"ten minutes for a new config");
        ModConfig old=new Gson().fromJson("{\"configVersion\":7,\"atomicCooldownSeconds\":60,\"effectVolume\":0.3}",ModConfig.class);
        old.applyAtomicCooldownUpgrade();old.normalize();
        assertEquals(600,old.atomicCooldownSeconds,"the old default becomes ten minutes");assertEquals(8,old.configVersion);assertEquals(.3,old.effectVolume);
        ModConfig custom=new Gson().fromJson("{\"configVersion\":7,\"atomicCooldownSeconds\":90}",ModConfig.class);
        custom.applyAtomicCooldownUpgrade();custom.normalize();assertEquals(90,custom.atomicCooldownSeconds,"one an operator chose stays");
        ModConfig now=new Gson().fromJson("{\"configVersion\":8,\"atomicCooldownSeconds\":60}",ModConfig.class);
        now.applyAtomicCooldownUpgrade();assertEquals(60,now.atomicCooldownSeconds,"set to 60 after the upgrade, it stays 60");
        ModConfig c=new ModConfig();c.atomicCooldownSeconds=99999;c.normalize();assertEquals(3600,c.atomicCooldownSeconds,"an hour at most");
    }
    @Test void zeroDamageAndZeroVolumeRemainValidOptions() {
        ModConfig c=new ModConfig();c.damage=0;c.effectVolume=0;c.normalize();
        assertEquals(0,c.damage);assertEquals(0,c.effectVolume);assertTrue(c.terrainDamage);assertFalse(c.damagePlayers);
    }
}
