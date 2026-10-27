package dev.sangwoo.celestialfall;

import net.minecraft.client.font.*;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class TitleFontTest {
    @Test void realMinecraftLoaderDecodesTheBitmapAndEveryDeclaredGlyph()throws Exception {
        var root=Path.of("src/main/resources/assets/celestialfall");
        var o=JsonParser.parseString(Files.readString(root.resolve("font/title.json"))).getAsJsonObject();
        var providers=o.getAsJsonArray("providers");
        for(var provider:providers)assertTrue(FontLoader.CODEC.parse(JsonOps.INSTANCE,provider).result().isPresent());
        var loader=FontLoader.CODEC.parse(JsonOps.INSTANCE,providers.get(0)).result().orElseThrow();
        ResourceManager resources=new ResourceManager(){
            public Optional<Resource> getResource(Identifier id){return Optional.of(new Resource(null,()->Files.newInputStream(root.resolve(id.getPath()))));}
            public Set<String> getAllNamespaces(){return Set.of("celestialfall");}
            public List<Resource> getAllResources(Identifier id){return getResource(id).stream().toList();}
            public Map<Identifier,Resource> findResources(String s,Predicate<Identifier> p){return Map.of();}
            public Map<Identifier,List<Resource>> findAllResources(String s,Predicate<Identifier> p){return Map.of();}
            public Stream<ResourcePack> streamResourcePacks(){return Stream.empty();}
        };
        var loadable=loader.build().left().orElseThrow();
        try(var font=loadable.load(resources)){
            var metrics=JsonParser.parseString(Files.readString(Path.of("tools/title-font-metrics.json"))).getAsJsonObject().getAsJsonObject("glyphs");
            for(var item:metrics.entrySet()){
                var glyph=font.getGlyph(item.getKey().codePointAt(0));assertNotNull(glyph,item.getKey());
                assertEquals(item.getValue().getAsJsonObject().get("advance").getAsInt(),glyph.getAdvance(),.001,item.getKey());
            }
        }
    }
}
