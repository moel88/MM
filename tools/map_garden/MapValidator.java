import java.nio.file.*;
import java.util.*;
import java.io.*;
import net.minecraft.SharedConstants;
import net.minecraft.Bootstrap;
import net.minecraft.nbt.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.registry.*;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.*;
import net.minecraft.world.storage.RegionFile;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.resource.DataConfiguration;
import com.mojang.serialization.*;

/** Uses the actual Minecraft 1.20.1 readers/codecs; does not start a game server. */
public class MapValidator {
    static void check(boolean b,String s) { if(!b)throw new IllegalStateException(s); }
    static <T> T decode(Codec<T> codec,DynamicOps<NbtElement> ops,NbtElement value) {
        return codec.parse(ops,value).getOrThrow(false,msg->{throw new IllegalStateException(msg);});
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.createGameVersion();Bootstrap.initialize();
        Path world=Path.of(args[0]),out=Path.of(args[1]);
        var manifest=com.google.gson.JsonParser.parseString(Files.readString(out.getParent().resolve("manifest.json"))).getAsJsonObject();
        int expectedChunks=manifest.get("chunks").getAsInt(),expectedEntities=manifest.get("block_entities").getAsInt();
        var lookup=BuiltinRegistries.createWrapperLookup();var ops=RegistryOps.of(NbtOps.INSTANCE,lookup);
        var level=NbtIo.readCompressed(world.resolve("level.dat").toFile()).getCompound("Data");
        check(level.getInt("DataVersion")==3465,"Data version");
        int sx=level.getInt("SpawnX"),sy=level.getInt("SpawnY"),sz=level.getInt("SpawnZ");
        var info=LevelInfo.fromDynamic(new Dynamic<>(NbtOps.INSTANCE,level),DataConfiguration.SAFE_MODE);
        check(info.areCommandsAllowed(),"Commands enabled");
        var settings=level.getCompound("WorldGenSettings");
        var generator=decode(GeneratorOptions.CODEC.codec(),ops,settings);
        var dimensions=decode(DimensionOptionsRegistryHolder.CODEC.codec(),ops,settings);
        check(dimensions.getWorldKeys().size()==3,"Three dimensions");
        var access=net.minecraft.command.CommandRegistryAccess.of(lookup,net.minecraft.resource.featuretoggle.FeatureFlags.VANILLA_FEATURES);
        var commands=new net.minecraft.server.command.CommandManager(net.minecraft.server.command.CommandManager.RegistrationEnvironment.ALL,access).getDispatcher();
        var source=new net.minecraft.server.command.ServerCommandSource(net.minecraft.server.command.CommandOutput.DUMMY,Vec3d.ZERO,Vec2f.ZERO,null,4,"mapcheck",net.minecraft.text.Text.literal("mapcheck"),null,null);
        int functionLines=0;
        try(var functions=Files.list(world.resolve("datapacks/cliffcity/data/cliffcity/functions"))) {
            for(Path function:functions.toList())for(String line:Files.readAllLines(function)) {
                if(line.isBlank() || line.startsWith("#"))continue;
                var parsed=commands.parse(line,source);
                check(!parsed.getReader().canRead() && parsed.getExceptions().isEmpty(),"Function command parse: "+function+" / "+line);
                functionLines++;
            }
        }
        var codec=PalettedContainer.createPalettedContainerCodec(Block.STATE_IDS,BlockState.CODEC,PalettedContainer.PaletteProvider.BLOCK_STATE,Blocks.AIR.getDefaultState());
        int chunks=0,sections=0,entities=0,regions=0;Set<String> states=new HashSet<>();
        Set<String> seen=new HashSet<>();
        try(var paths=Files.list(world.resolve("region"))) {
            for(Path path:paths.filter(p->p.toString().endsWith(".mca")).sorted().toList()) {
                String[] parts=path.getFileName().toString().split("\\.");int rx=Integer.parseInt(parts[1]),rz=Integer.parseInt(parts[2]);regions++;
                try(var region=new RegionFile(path,path.getParent(),false)) {
                    for(int z=0;z<32;z++)for(int x=0;x<32;x++) {
                        var pos=new ChunkPos(rx*32+x,rz*32+z);
                        if(!region.hasChunk(pos))continue;
                        check(region.isChunkValid(pos),"Region allocation "+pos);
                        try(var stream=region.getChunkInputStream(pos)) {
                            var chunk=NbtIo.read(stream);chunks++;
                            check(chunk.getInt("xPos")==pos.x && chunk.getInt("zPos")==pos.z,"Chunk position");
                            check(seen.add(pos.x+","+pos.z),"Duplicate chunk");
                            check(chunk.getString("Status").equals("minecraft:full"),"Full status");
                            var list=chunk.getList("sections",NbtElement.COMPOUND_TYPE);
                            check(list.size()==24,"All vertical sections");
                            Map<Integer,PalettedContainer<BlockState>> decoded=new HashMap<>();
                            for(var entry:list) {
                                var sec=(NbtCompound)entry;sections++;
                                var container=decode(codec,NbtOps.INSTANCE,sec.getCompound("block_states"));
                                container.forEachValue(state->states.add(Registries.BLOCK.getId(state.getBlock()).toString()));
                                decoded.put((int)sec.getByte("Y"),container);
                                check(sec.getCompound("biomes").getList("palette",NbtElement.STRING_TYPE).getString(0).equals("minecraft:plains"),"Biome");
                            }
                            for(String key:List.of("WORLD_SURFACE","MOTION_BLOCKING","OCEAN_FLOOR")) {
                                long[] packed=chunk.getCompound("Heightmaps").getLongArray(key);
                                check(packed.length==37,"Heightmap packing");
                            }
                            for(var entry:chunk.getList("block_entities",NbtElement.COMPOUND_TYPE)) {
                                var be=(NbtCompound)entry;var bp=new BlockPos(be.getInt("x"),be.getInt("y"),be.getInt("z"));
                                var state=decoded.get(bp.getY()>>4).get(bp.getX()&15,bp.getY()&15,bp.getZ()&15);
                                check(BlockEntity.createFromNbt(bp,state,be)!=null,"Block entity decode: "+bp);entities++;
                            }
                            if(pos.x==(sx>>4) && pos.z==(sz>>4)) {
                                check(decoded.get(sy>>4).get(sx&15,sy&15,sz&15).isAir(),"Spawn feet clear");
                                check(decoded.get((sy+1)>>4).get(sx&15,(sy+1)&15,sz&15).isAir(),"Spawn head clear");
                                check(!decoded.get((sy-1)>>4).get(sx&15,(sy-1)&15,sz&15).isAir(),"Spawn ground");
                            }
                        }
                    }
                }
            }
        }
        check(chunks==expectedChunks,"Expected contiguous chunk coverage");check(entities==expectedEntities,"All signs and chests");
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("check","passed");result.put("reader","Minecraft Java 1.20.1 RegionFile / NbtIo / PalettedContainer / dimension codec / block entity loader");
        result.put("chunks",chunks);result.put("sections",sections);result.put("block_entities",entities);result.put("regions",regions);result.put("vanilla_block_types",states.size());
        result.put("worldgen_dimensions",dimensions.getWorldKeys().size());result.put("level_name",info.getLevelName());
        result.put("function_commands_parsed",functionLines);
        result.put("named_jar_access_repair","Standalone validation harness repairs remapped package-access flags in memory; Minecraft reader logic is unchanged.");
        result.put("gameplay_tested",false);
        String json=new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result);
        Files.writeString(out,json+"\n");System.out.println(json);
    }
}
