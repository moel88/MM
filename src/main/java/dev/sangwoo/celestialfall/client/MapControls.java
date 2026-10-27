package dev.sangwoo.celestialfall.client;
import java.util.*;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Temporary control overrides. GameOptions.write always persists the user's normal-world bindings. */
public final class MapControls {
    private record Change(InputUtil.Key original,InputUtil.Key applied) {}
    private static final Map<KeyBinding,Change> changed=new IdentityHashMap<>();
    private static final Map<KeyBinding,InputUtil.Key> saving=new IdentityHashMap<>();
    private static boolean active;
    private MapControls() {}
    private static InputUtil.Key key(int code){return InputUtil.Type.KEYSYM.createFromCode(code);}
    private static InputUtil.Key bound(KeyBinding b){return KeyBindingHelper.getBoundKeyOf(b);}
    public static void initialize(){ClientLifecycleEvents.CLIENT_STARTED.register(MapControls::migrateLegacy);}
    private static void migrateLegacy(MinecraftClient mc) {
        KeyBinding.updateKeysByCode();
        Path marker=FabricLoader.getInstance().getConfigDir().resolve("botand-controls-v290-migrated.txt");
        if(Files.exists(marker))return;
        var o=mc.options;
        // Old releases permanently installed exactly this four-key layout. Do not touch other/custom layouts.
        if(o.useKey.matchesKey(GLFW.GLFW_KEY_R,0)&&bound(o.sneakKey).equals(InputUtil.UNKNOWN_KEY)
            &&bound(o.sprintKey).equals(InputUtil.UNKNOWN_KEY)&&o.swapHandsKey.matchesKey(GLFW.GLFW_KEY_G,0)) {
            o.useKey.setBoundKey(InputUtil.Type.MOUSE.createFromCode(GLFW.GLFW_MOUSE_BUTTON_RIGHT));
            o.sneakKey.setBoundKey(key(GLFW.GLFW_KEY_LEFT_SHIFT));o.sprintKey.setBoundKey(key(GLFW.GLFW_KEY_LEFT_CONTROL));o.swapHandsKey.setBoundKey(key(GLFW.GLFW_KEY_F));
            KeyBinding.updateKeysByCode();o.write();
        }
        try{Files.createDirectories(marker.getParent());Files.writeString(marker,"Legacy control migration checked. Per-map overrides are now temporary.\n");}
        catch(Exception e){dev.sangwoo.celestialfall.CelestialFall.LOGGER.warn("Could not record control migration",e);}
    }
    private static void override(KeyBinding b,InputUtil.Key value){changed.put(b,new Change(bound(b),value));b.setBoundKey(value);b.setPressed(false);}
    public static void tick(MinecraftClient mc,boolean enabled) {
        if(!enabled){exit();return;}if(active)return;active=true;
        var o=mc.options;
        if(o.useKey.matchesMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT))override(o.useKey,key(GLFW.GLFW_KEY_R));
        if(o.sneakKey.matchesKey(GLFW.GLFW_KEY_LEFT_SHIFT,0))override(o.sneakKey,InputUtil.UNKNOWN_KEY);
        if(o.sprintKey.matchesKey(GLFW.GLFW_KEY_LEFT_CONTROL,0))override(o.sprintKey,InputUtil.UNKNOWN_KEY);
        if(o.swapHandsKey.matchesKey(GLFW.GLFW_KEY_F,0))override(o.swapHandsKey,key(GLFW.GLFW_KEY_G));
        KeyBinding.updateKeysByCode();
    }
    public static void exit() {
        if(!active)return;
        endSave();changed.forEach((b,c)->{if(bound(b).equals(c.applied))b.setBoundKey(c.original);});
        changed.clear();active=false;KeyBinding.unpressAll();KeyBinding.updateKeysByCode();
    }
    public static void beginSave() {
        if(!active||!saving.isEmpty())return;
        changed.forEach((b,c)->{if(bound(b).equals(c.applied)){saving.put(b,bound(b));b.setBoundKey(c.original);}});
    }
    public static void endSave(){saving.forEach(KeyBinding::setBoundKey);saving.clear();}
    private static boolean botand(KeyBinding b){String id=b.getTranslationKey();return id.startsWith("key.botand.")||id.startsWith("key.celestialfall.");}
    /** Inactive custom bindings must not swallow vanilla F/Shift/Ctrl events. Configured keys stay intact. */
    public static void routeKeys(Map<InputUtil.Key,KeyBinding> routes,Collection<KeyBinding> bindings) {
        if(active){for(var b:bindings)if(botand(b)&&!b.isUnbound())routes.put(bound(b),b);return;}
        var conflicts=new HashSet<InputUtil.Key>();
        routes.entrySet().removeIf(e->{if(botand(e.getValue())){conflicts.add(e.getKey());return true;}return false;});
        for(var b:bindings)if(!botand(b)&&!b.isUnbound()&&conflicts.contains(bound(b)))routes.put(bound(b),b);
    }
}
