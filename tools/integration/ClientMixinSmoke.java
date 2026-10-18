package dev.sangwoo.botandverify;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import java.nio.file.*;
import java.util.*;
public final class ClientMixinSmoke implements PreLaunchEntrypoint {
    public void onPreLaunch() {
        var report=new LinkedHashMap<String,Object>();int code=0;
        try {
            var classes=List.of("dev.sangwoo.celestialfall.client.title.TitleMapCamera","dev.sangwoo.celestialfall.client.title.TitleCameraTour","dev.sangwoo.celestialfall.client.title.MapEntryGate","dev.sangwoo.celestialfall.client.title.BotandTitleScreen","dev.sangwoo.celestialfall.client.title.TitlePortal","dev.sangwoo.celestialfall.client.title.TitlePainter","dev.sangwoo.celestialfall.client.menu.DockSprites","dev.sangwoo.celestialfall.client.menu.BotandMenuScreen","dev.sangwoo.celestialfall.client.menu.MenuClient","dev.sangwoo.celestialfall.client.menu.MenuPainter","net.minecraft.client.option.GameOptions","net.minecraft.client.option.KeyBinding","dev.sangwoo.celestialfall.client.MapClient","dev.sangwoo.celestialfall.client.MapControls","net.minecraft.world.World","dev.sangwoo.celestialfall.client.RecoveryRenderer","dev.sangwoo.celestialfall.terrain.RecoveryAnchor","dev.sangwoo.celestialfall.terrain.RecoveryBlock","net.minecraft.client.render.entity.PlayerEntityRenderer","net.minecraft.client.render.item.HeldItemRenderer","net.minecraft.client.MinecraftClient","net.minecraft.client.render.GameRenderer","net.minecraft.client.render.Camera","net.minecraft.client.render.WorldRenderer","net.minecraft.client.gui.hud.InGameHud","net.minecraft.client.toast.ToastManager","dev.sangwoo.celestialfall.client.MotionPlayerModel","dev.sangwoo.celestialfall.client.CombatHands","net.minecraft.client.Mouse","net.minecraft.client.Keyboard","net.minecraft.client.input.KeyboardInput","net.minecraft.client.network.ClientPlayerEntity","dev.sangwoo.celestialfall.client.BotandCamera","dev.sangwoo.celestialfall.client.HitStunClient","dev.sangwoo.celestialfall.client.CinematicSoundSequence","dev.sangwoo.celestialfall.CinematicAudioPlan","net.minecraft.entity.mob.MobEntity","dev.sangwoo.celestialfall.client.BotandHud",
                "dev.sangwoo.celestialfall.client.BotandHudLayout",
                "dev.sangwoo.celestialfall.client.ProgressClient",
                "net.minecraft.entity.player.HungerManager",
                "net.minecraft.entity.attribute.ClampedEntityAttribute",
                "dev.sangwoo.celestialfall.client.AtomicHud","dev.sangwoo.celestialfall.client.AtomicFlashMesh","dev.sangwoo.celestialfall.client.AtomicActor","dev.sangwoo.celestialfall.client.AtomicRenderer","dev.sangwoo.celestialfall.client.AtomicColumnGeometry","dev.sangwoo.celestialfall.combat.AtomicMotion","dev.sangwoo.celestialfall.AtomicTimeline","dev.sangwoo.celestialfall.training.TrainingDummyEntity","dev.sangwoo.celestialfall.client.TrainingDummyModel","dev.sangwoo.celestialfall.client.TrainingDummyRenderer");
            for(String name:classes)Class.forName(name,false,getClass().getClassLoader());
            keyRouting(report);
            report.put("result","passed");report.put("fabric_client_class_transform",classes);report.put("graphics_or_gameplay_tested",false);
        }catch(Throwable t){t.printStackTrace();report.put("result","failed");report.put("error",t.toString());code=1;}
        try{Path p=Path.of("../build/botand-motion/client-mixins.json");Files.createDirectories(p.getParent());Files.writeString(p,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){e.printStackTrace();code=1;}
        System.out.println("BOTAND_CLIENT_TRANSFORM "+report);System.exit(code);
    }
    private static void keyRouting(Map<String,Object> report)throws Exception {
        var normal=new ArrayList<net.minecraft.client.option.KeyBinding>();var custom=new ArrayList<net.minecraft.client.option.KeyBinding>();
        for(int code:new int[]{70,340,341}) {
            normal.add(new net.minecraft.client.option.KeyBinding("key.verify.normal_"+code,code,"key.categories.gameplay"));
            custom.add(new net.minecraft.client.option.KeyBinding("key.botand.verify_"+code,code,"key.categories.gameplay"));
        }
        net.minecraft.client.option.KeyBinding.updateKeysByCode();
        for(int i=0;i<normal.size();i++) {
            var k=net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(custom.get(i));
            net.minecraft.client.option.KeyBinding.onKeyPressed(k);
            if(!normal.get(i).wasPressed()||custom.get(i).wasPressed())throw new AssertionError("Off-map F/Shift/Ctrl swallowed");
        }
        var active=dev.sangwoo.celestialfall.client.MapControls.class.getDeclaredField("active");active.setAccessible(true);active.setBoolean(null,true);
        net.minecraft.client.option.KeyBinding.updateKeysByCode();
        for(int i=0;i<normal.size();i++) {
            var k=net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(custom.get(i));
            net.minecraft.client.option.KeyBinding.onKeyPressed(k);
            if(normal.get(i).wasPressed()||!custom.get(i).wasPressed())throw new AssertionError("Map custom key missing");
        }
        var binding=normal.get(0);var original=net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(binding);
        var applied=net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(71);
        var override=dev.sangwoo.celestialfall.client.MapControls.class.getDeclaredMethod("override",net.minecraft.client.option.KeyBinding.class,net.minecraft.client.util.InputUtil.Key.class);override.setAccessible(true);override.invoke(null,binding,applied);
        dev.sangwoo.celestialfall.client.MapControls.beginSave();
        if(!net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(binding).equals(original))throw new AssertionError("Options save persists temporary key");
        dev.sangwoo.celestialfall.client.MapControls.endSave();
        if(!net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(binding).equals(applied))throw new AssertionError("Saving changed runtime controls");
        dev.sangwoo.celestialfall.client.MapControls.exit();
        if(!net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(binding).equals(original))throw new AssertionError("Map exit loses original key");
        report.put("inactive_custom_bindings_do_not_swallow_vanilla_f_shift_ctrl",true);
        report.put("active_custom_keys_route_and_temporary_overrides_do_not_persist",true);
    }
}
