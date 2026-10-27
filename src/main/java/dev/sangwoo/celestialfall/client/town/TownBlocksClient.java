package dev.sangwoo.celestialfall.client.town;

import dev.sangwoo.celestialfall.town.TownBlocks;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;

/**
 * The town blocks' render layers (panels with holes are cut out, leaves on cards are cut out with their far ones
 * blurred, windows and drinks machines are see-through), their models built in code, the map's wires and its far sea.
 */
public final class TownBlocksClient {
    private TownBlocksClient() {}
    public static void initialize(){
        WireRenderer.initialize();
        BackdropRenderer.initialize();
        RoofModel.register();
        PoleModel.register();
        FoliageModel.register();
        WindowModel.register();
        FurnitureModel.register();
        for(var b:TownBlocks.CUTOUT)BlockRenderLayerMap.INSTANCE.putBlock(b,RenderLayer.getCutout());
        for(var b:TownBlocks.CUTOUT_MIPPED)BlockRenderLayerMap.INSTANCE.putBlock(b,RenderLayer.getCutoutMipped());
        for(var b:TownBlocks.TRANSLUCENT)BlockRenderLayerMap.INSTANCE.putBlock(b,RenderLayer.getTranslucent());
        // the street furniture has thin parts and see-through details too; the drinks machines' glass is see-through
        for(var b:new net.minecraft.block.Block[]{TownBlocks.POLE_CROSSARM,TownBlocks.POLE_ARM,TownBlocks.STREET_LAMP,TownBlocks.STREET_LAMP_ARM,TownBlocks.POLE_TRANSFORMER,
            TownBlocks.AIR_CONDITIONER,TownBlocks.LETTERBOX,TownBlocks.TRAFFIC_MIRROR})BlockRenderLayerMap.INSTANCE.putBlock(b,RenderLayer.getCutout());
        for(var b:new net.minecraft.block.Block[]{TownBlocks.VENDING_MACHINE,TownBlocks.VENDING_MACHINE_WHITE})BlockRenderLayerMap.INSTANCE.putBlock(b,RenderLayer.getTranslucent());
    }
}
