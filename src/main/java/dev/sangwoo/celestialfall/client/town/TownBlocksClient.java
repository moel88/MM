package dev.sangwoo.celestialfall.client.town;

import dev.sangwoo.celestialfall.town.TownBlocks;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;

/** The town blocks' render layers (panels with holes are cut out, the curtained window is see-through), the map's wires and its far coast. */
public final class TownBlocksClient {
    private TownBlocksClient() {}
    public static void initialize(){
        WireRenderer.initialize();
        BackdropRenderer.initialize();
        RoofModel.register();
        PoleModel.register();
        for(var b:TownBlocks.CUTOUT)BlockRenderLayerMap.INSTANCE.putBlock(b,RenderLayer.getCutout());
        for(var b:TownBlocks.TRANSLUCENT)BlockRenderLayerMap.INSTANCE.putBlock(b,RenderLayer.getTranslucent());
        // the street furniture has thin parts and see-through details too
        for(var b:new net.minecraft.block.Block[]{TownBlocks.POLE_CROSSARM,TownBlocks.POLE_ARM,TownBlocks.STREET_LAMP,TownBlocks.STREET_LAMP_ARM,TownBlocks.POLE_TRANSFORMER,
            TownBlocks.AIR_CONDITIONER,TownBlocks.VENDING_MACHINE,TownBlocks.LETTERBOX})BlockRenderLayerMap.INSTANCE.putBlock(b,RenderLayer.getCutout());
    }
}
