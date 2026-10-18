package dev.sangwoo.celestialfall.map;

import dev.sangwoo.celestialfall.CelestialFall;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.*;
import java.util.*;

/** Explicit, incremental renovation of the recognized one-third city, retaining player data.
 * Existing custom blocks, containers and entity positions always take precedence. */
public final class MapDecoration {
    private static final Map<ServerWorld,Job> JOBS=new IdentityHashMap<>();
    private static GardenPatch patch;
    private static BlockState[] states;
    private static final class Job {
        final ServerWorld world;final ServerCommandSource source;
        int cursor,changed,unchanged,conflicts,occupied,nextNotice=10;
        long chunk=Long.MIN_VALUE;
        Job(ServerCommandSource s){world=s.getWorld();source=s;}
        void message(String value){source.sendFeedback(()->Text.literal(value),false);}
    }
    private MapDecoration(){}
    public static void initialize(){
        ServerTickEvents.END_SERVER_TICK.register(MapDecoration::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s->JOBS.keySet().removeIf(w->w.getServer()==s));
    }
    public static boolean running(ServerWorld w){return JOBS.containsKey(w);}
    public static int start(ServerCommandSource source){
        var world=source.getWorld();
        if(!MapGate.enabled(world)||!MapGate.recognizeCity(world.getServer())||!world.getRegistryKey().equals(net.minecraft.world.World.OVERWORLD)){
            source.sendError(Text.literal("기존 3분의 1 보탠드 도시 맵에서만 장식을 적용할 수 있어요."));return 0;
        }
        if(running(world)){source.sendError(Text.literal("장식 작업이 진행 중이에요. /botand map decorate status"));return 0;}
        try{
            if(patch==null){
                var loaded=GardenPatch.read(MapDecoration.class.getResourceAsStream("/data/celestialfall/map/azure_garden.bgp.gz"));
                var decoded=new BlockState[loaded.palette.length];
                for(int i=0;i<decoded.length;i++)decoded[i]=BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(),loaded.palette[i],false).blockState();
                patch=loaded;states=decoded;
            }
        }catch(Exception e){CelestialFall.LOGGER.error("Cannot load garden renovation",e);source.sendError(Text.literal("장식 데이터를 읽지 못했어요. 월드는 변경하지 않았어요."));return 0;}
        // Check several original or already-renovated anchors as well as the datapack fingerprint.
        int matches=0;
        for(int[] a:new int[][]{{33,80,-48},{4,80,-70},{62,80,-26},{-35,80,40},{2,80,40},{-120,70,-105}}){
            var state=world.getBlockState(new BlockPos(a[0],a[1],a[2]));if(!state.isAir()&&!state.isOf(Blocks.WATER))matches++;
        }
        if(matches<5){source.sendError(Text.literal("원본 도시 지형을 확인할 수 없어 적용하지 않았어요."));return 0;}
        var job=new Job(source);JOBS.put(world,job);job.message("Azure Garden 장식 시작! 여러 틱에 나누어 적용해요. 직접 바꾼 블록과 상자는 유지해요.");return 1;
    }
    public static int status(ServerCommandSource s){
        var j=JOBS.get(s.getWorld());if(j==null){s.sendFeedback(()->Text.literal("진행 중인 장식 작업이 없어요."),false);return 0;}
        j.message("장식 진행: "+j.cursor*100L/patch.count+"% / 적용 "+j.changed+" / 기존 변경 보존 "+j.conflicts);return 1;
    }
    public static int cancel(ServerCommandSource s){
        var j=JOBS.remove(s.getWorld());s.sendFeedback(()->Text.literal(j==null?"진행 중인 작업이 없어요.":"장식 작업을 멈췄어요. 적용된 부분은 유지하며 같은 명령으로 이어서 적용할 수 있어요."),false);return j==null?0:1;
    }
    private static void tick(MinecraftServer server){
        for(var it=JOBS.entrySet().iterator();it.hasNext();){
            var job=it.next().getValue();if(job.world.getServer()!=server)continue;
            if(!MapGate.enabled(job.world)){job.message("전용 맵 설정이 해제되어 장식 작업을 멈췄어요.");it.remove();continue;}
            try{
                long deadline=System.nanoTime()+6_000_000L;int budget=1024;
                var living=job.world.getEntitiesByType(TypeFilter.instanceOf(LivingEntity.class),LivingEntity::isAlive);
                var pos=new BlockPos.Mutable();
                while(job.cursor<patch.count&&budget-->0){
                    int i=job.cursor;pos.set(patch.x(i),patch.y(i),patch.z(i));long chunk=ChunkPos.toLong(pos.getX()>>4,pos.getZ()>>4);
                    // At most one new chunk per tick. Existing region chunks are loaded on the server thread.
                    if(chunk!=job.chunk){if(budget<1023)break;job.world.getChunk(pos.getX()>>4,pos.getZ()>>4);job.chunk=chunk;}
                    var current=job.world.getBlockState(pos);var after=states[patch.after(i)];var before=states[patch.before(i)];
                    job.cursor++;
                    if(current==after){job.unchanged++;}
                    else if(current!=before||job.world.getBlockEntity(pos)!=null){job.conflicts++;}
                    else if(!after.getCollisionShape(job.world,pos).isEmpty()&&living.stream().anyMatch(e->e.getBoundingBox().intersects(new Box(pos)))){job.occupied++;}
                    else if(job.world.setBlockState(pos,after,Block.NOTIFY_LISTENERS|Block.FORCE_STATE|Block.SKIP_DROPS)){job.changed++;}
                    if(System.nanoTime()>=deadline)break;
                }
                int percent=(int)(job.cursor*100L/patch.count);
                if(percent>=job.nextNotice&&job.cursor<patch.count){job.message("Azure Garden 장식 "+percent+"%");job.nextNotice=(percent/10+1)*10;}
                if(job.cursor==patch.count){
                    job.message("Azure Garden 완료! 적용 "+job.changed+" / 이미 적용 "+job.unchanged+" / 직접 변경 보존 "+job.conflicts+" / 캐릭터 위치 보존 "+job.occupied);
                    it.remove();
                }
            }catch(Exception e){CelestialFall.LOGGER.error("Garden renovation stopped; partial changes can be resumed",e);job.message("장식 작업을 중단했어요. 적용된 부분은 유지되며 명령을 다시 실행할 수 있어요.");it.remove();}
        }
    }
}
