package dev.sangwoo.celestialfall.client.town;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/** A block state's model built in code when the game bakes its models (no model or block state file), from the sprites it asks for by name. */
final class CodeModel implements UnbakedModel {
    interface Build{BakedModel bake(Function<String,Sprite> sprites);}
    private final Build build;
    CodeModel(Build build){this.build=build;}

    /** Hands every state of a block to the game as a model of its own. */
    static void resolve(ModelLoadingPlugin.Context plugin,Block b,Function<BlockState,Build> make){
        plugin.registerBlockStateResolver(b,ctx->{for(var s:b.getStateManager().getStates())ctx.setModel(s,new CodeModel(make.apply(s)));});
    }

    @Override public Collection<Identifier> getModelDependencies(){return List.of();}
    @Override public void setParents(Function<Identifier,UnbakedModel> loader){}
    @Override public BakedModel bake(Baker baker,Function<SpriteIdentifier,Sprite> textures,ModelBakeSettings settings,Identifier id){
        return build.bake(name->Mesh.sprite(textures,name));
    }
}
