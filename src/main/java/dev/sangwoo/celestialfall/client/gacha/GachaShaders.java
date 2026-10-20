package dev.sangwoo.celestialfall.client.gacha;

import dev.sangwoo.celestialfall.CelestialFall;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormats;

/**
 * The gacha screens' own GUI programs (2.40.0). Minecraft's position_tex_color throws away everything under 10% alpha,
 * so glows were cut off in rings; and a program carries its blend in its JSON, applied when it is bound, so light that
 * adds needs a program of its own. Each comes twice: blending normally and adding its light. Minecraft's own stand in if
 * they fail to load. The pause menu and the player list draw with them too (2.45.0).
 */
public final class GachaShaders {
    private GachaShaders(){}
    private static ShaderProgram tex,texAdd,color,colorAdd;
    static void initialize(){
        CoreShaderRegistrationCallback.EVENT.register(c->{
            c.register(CelestialFall.id("gacha_ui_tex"),VertexFormats.POSITION_TEXTURE_COLOR,s->tex=s);
            c.register(CelestialFall.id("gacha_ui_tex_add"),VertexFormats.POSITION_TEXTURE_COLOR,s->texAdd=s);
            c.register(CelestialFall.id("gacha_ui_color"),VertexFormats.POSITION_COLOR,s->color=s);
            c.register(CelestialFall.id("gacha_ui_color_add"),VertexFormats.POSITION_COLOR,s->colorAdd=s);
        });
    }
    public static ShaderProgram tex(boolean additive){var s=additive?texAdd:tex;return s!=null?s:GameRenderer.getPositionTexColorProgram();}
    public static ShaderProgram color(boolean additive){var s=additive?colorAdd:color;return s!=null?s:GameRenderer.getPositionColorProgram();}
}
