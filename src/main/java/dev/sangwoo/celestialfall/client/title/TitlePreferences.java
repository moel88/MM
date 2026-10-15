package dev.sangwoo.celestialfall.client.title;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import dev.sangwoo.celestialfall.CelestialFall;
import net.fabricmc.loader.api.FabricLoader;

/** Separate from all in-world graphics/cutscene preferences. */
public final class TitlePreferences {
    private static boolean motion=true;
    private static Path path(){return FabricLoader.getInstance().getConfigDir().resolve("botand-title.json");}
    public static boolean motion(){return motion;}
    public static void load(){try{if(Files.exists(path())){var o=JsonParser.parseString(Files.readString(path())).getAsJsonObject();if(o.has("motion"))motion=o.get("motion").getAsBoolean();}}catch(Exception e){CelestialFall.LOGGER.warn("Could not read title preferences",e);}}
    public static void write(Path file,boolean value)throws java.io.IOException{
        var o=Files.exists(file)?JsonParser.parseString(Files.readString(file)).getAsJsonObject():new JsonObject();o.addProperty("motion",value);
        Files.createDirectories(file.toAbsolutePath().getParent());var temp=Files.createTempFile(file.toAbsolutePath().getParent(),"botand-title-",".tmp");
        try{Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(o),StandardCharsets.UTF_8);try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}
    }
    public static boolean toggle(){try{write(path(),!motion);motion=!motion;return true;}catch(Exception e){CelestialFall.LOGGER.warn("Could not save title preferences",e);return false;}}
}
