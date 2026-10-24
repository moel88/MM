package dev.sangwoo.celestialfall.mixin.client;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

/**
 * Leaves out the client mixins written against parts of vanilla rendering that a performance or shader mod replaces
 * outright: Sodium (and its forks) rewrite the world renderer's terrain setup, so injecting into it would stop the
 * game from starting. Everything else applies as always.
 */
public final class ClientMixinPlugin implements IMixinConfigPlugin {
    private static final String TERRAIN="dev.sangwoo.celestialfall.mixin.client.TerrainCameraMixin";
    @Override public void onLoad(String mixinPackage){}
    @Override public String getRefMapperConfig(){return null;}
    @Override public boolean shouldApplyMixin(String target,String mixin){
        if(mixin.equals(TERRAIN))return !sodium();
        return true;
    }
    static boolean sodium(){
        var loader=FabricLoader.getInstance();
        return loader.isModLoaded("sodium")||loader.isModLoaded("embeddium")||loader.isModLoaded("rubidium");
    }
    @Override public void acceptTargets(Set<String> mine,Set<String> others){}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String target,ClassNode targetClass,String mixin,IMixinInfo info){}
    @Override public void postApply(String target,ClassNode targetClass,String mixin,IMixinInfo info){}
}
