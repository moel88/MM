package dev.sangwoo.celestialfall.client.menu;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import dev.sangwoo.celestialfall.CelestialFall;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
public final class MenuSettings {
    private static double interfaceVolume=1;
    public static double clamp(double v){return Double.isFinite(v)?Math.max(0,Math.min(1,v)):1;}
    public static void load(){try{var p=FabricLoader.getInstance().getConfigDir().resolve("botand-ui.json");if(Files.exists(p)){var o=JsonParser.parseString(Files.readString(p)).getAsJsonObject();if(o.has("interfaceVolume"))interfaceVolume=clamp(o.get("interfaceVolume").getAsDouble());}}catch(Exception e){CelestialFall.LOGGER.warn("Could not read menu preferences",e);}}
    public static double volume(){return interfaceVolume;}
    public static double[] current(){return new double[]{MinecraftClient.getInstance().options.getSoundVolume(SoundCategory.MUSIC),CelestialFall.CONFIG.effectVolume,interfaceVolume};}
    /** Merge only the requested sound field. Preserve the user's mobile renderer options and unknown fields. */
    public static void writeEffectVolume(Path file,double volume)throws java.io.IOException {
        JsonObject o=Files.exists(file)?JsonParser.parseString(Files.readString(file)).getAsJsonObject():new Gson().toJsonTree(CelestialFall.CONFIG).getAsJsonObject();
        o.addProperty("effectVolume",clamp(volume));atomicWrite(file,new GsonBuilder().setPrettyPrinting().create().toJson(o));
    }
    private static void atomicWrite(Path file,String text)throws java.io.IOException {Files.createDirectories(file.toAbsolutePath().getParent());var temp=Files.createTempFile(file.toAbsolutePath().getParent(),"botand-sound-",".tmp");try{Files.writeString(temp,text,StandardCharsets.UTF_8);try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}}
    public static boolean save(double[] values){try{var dir=FabricLoader.getInstance().getConfigDir();writeEffectVolume(dir.resolve("celestialfall.json"),values[1]);var prefs=new JsonObject();prefs.addProperty("interfaceVolume",clamp(values[2]));atomicWrite(dir.resolve("botand-ui.json"),prefs.toString());CelestialFall.CONFIG.effectVolume=clamp(values[1]);interfaceVolume=clamp(values[2]);var mc=MinecraftClient.getInstance();mc.options.getSoundVolumeOption(SoundCategory.MUSIC).setValue(clamp(values[0]));mc.options.write();return true;}catch(Exception e){CelestialFall.LOGGER.warn("Could not save menu sound settings",e);return false;}}
}
