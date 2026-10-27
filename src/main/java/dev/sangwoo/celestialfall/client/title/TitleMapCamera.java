package dev.sangwoo.celestialfall.client.title;

import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.CinematicDirector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Util;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;

/** Owns only the rendered view while the map's welcome screen is open. */
public final class TitleMapCamera {
    private static Screen owner;
    private static ClientWorld world;
    private static Vec3d anchor;
    private static TitleCameraTour tour;
    private static long cut=-1;
    public static boolean active(){
        var mc=MinecraftClient.getInstance();
        return mc.currentScreen instanceof BotandTitleScreen&&MapClient.enabled()&&mc.player!=null&&mc.player.isAlive();
    }
    public static void clear(){owner=null;world=null;anchor=null;tour=null;cut=-1;}
    public static CinematicDirector.Pose pose(){
        if(!active()){clear();return null;}
        var mc=MinecraftClient.getInstance();
        if(mc.currentScreen instanceof BotandTitleScreen title&&title.liveCinema())return null;
        if(owner!=mc.currentScreen||world!=mc.world||anchor==null||anchor.squaredDistanceTo(mc.player.getPos())>64*64){
            owner=mc.currentScreen;world=mc.world;anchor=mc.player.getPos();cut=-1;
            final ClientWorld scene=world;
            tour=new TitleCameraTour(point(anchor),mc.options.getViewDistance().getValue(),new TitleCameraTour.Terrain(){
                public boolean loaded(int x,int z){return scene.isChunkLoaded(x>>4,z>>4);}
                public int surface(int x,int z){return scene.getTopY(Heightmap.Type.WORLD_SURFACE,x,z);}
                public int ceiling(){return scene.getTopY();}
                public boolean clear(TitleCameraTour.Point position,TitleCameraTour.Point focus){
                    Vec3d at=vec(position),look=vec(focus);
                    for(double dx:new double[]{-.4,.4})for(double dy:new double[]{-.4,.4})for(double dz:new double[]{-.4,.4}){
                        var block=BlockPos.ofFloored(at.add(dx,dy,dz));
                        if(!scene.getBlockState(block).getCollisionShape(scene,block).isEmpty()||!scene.getFluidState(block).isEmpty())return false;
                    }
                    return scene.raycast(new RaycastContext(at,look,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,mc.player)).getType()==HitResult.Type.MISS;
                }
            });
        }
        var shot=tour.sample(Util.getMeasuringTimeMs());
        if(cut!=tour.interval()){cut=tour.interval();mc.worldRenderer.scheduleTerrainUpdate();}
        // Until terrain arrives retain the normal view, then hold the first valid shot for 10 seconds.
        if(shot==null)return null;
        if(!world.isChunkLoaded(BlockPos.ofFloored(vec(shot.position())))){clear();return null;}
        return new CinematicDirector.Pose(vec(shot.position()),shot.yaw(),shot.pitch(),0);
    }
    private static TitleCameraTour.Point point(Vec3d p){return new TitleCameraTour.Point(p.x,p.y,p.z);}
    private static Vec3d vec(TitleCameraTour.Point p){return new Vec3d(p.x(),p.y(),p.z());}
    private TitleMapCamera(){}
}
