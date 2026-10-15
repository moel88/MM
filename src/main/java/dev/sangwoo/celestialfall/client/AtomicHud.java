package dev.sangwoo.celestialfall.client;
import dev.sangwoo.celestialfall.CelestialFall;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
/** Software fallback for the same interior exposure when post-processing is unavailable. */
public final class AtomicHud {
    private AtomicHud(){}
    public static void render(DrawContext context,float delta){
        if(CelestialFall.CONFIG.postProcessing&&AnimePostProcessor.available()||StandClient.monochrome())return;
        var mc=MinecraftClient.getInstance();if(mc.world==null||mc.player==null)return;
        var value=AtomicImmersion.at(mc.gameRenderer.getCamera().getPos(),delta);
        if(value.violet()>0)context.fill(0,0,mc.getWindow().getScaledWidth(),mc.getWindow().getScaledHeight(),((int)(value.violet()*230)<<24)|0x4600e6);
        if(CelestialFall.CONFIG.screenFlash&&value.flash()>0)context.fill(0,0,mc.getWindow().getScaledWidth(),mc.getWindow().getScaledHeight(),((int)(value.flash()*220)<<24)|0xffcfff);
    }
}
