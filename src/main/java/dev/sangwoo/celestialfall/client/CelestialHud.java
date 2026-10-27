package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.SpellTimeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class CelestialHud {
    private CelestialHud() {}
    public static void render(DrawContext context, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        ClientSpell spell = CinematicDirector.active();
        if(spell==null)spell=CelestialFallClient.nearest();
        if (spell == null) return;
        float age = spell.age(mc.world.getTime(), delta);
        int width = mc.getWindow().getScaledWidth(), height = mc.getWindow().getScaledHeight();
        double d = mc.player.getPos().distanceTo(spell.target);
        float distanceFade = (float) Math.max(0, Math.min(1, (600 - d) / 240));
        if (CelestialFall.CONFIG.screenFlash) {
            float flash=0;
            for(int wave=0;wave<SpellTimeline.WAVE_COUNT;wave++)
                flash=Math.max(flash,SpellTimeline.pulse(age,SpellTimeline.waveTick(wave),wave==0?8:3)*(wave==0?1:.4f));
            int alpha=(int)(flash*flash*225*distanceFade);
            if(alpha>0)context.fill(0,0,width,height,(alpha<<24)|0xF2E7FF);
        }
        if(CelestialFall.CONFIG.purpleGlow && age>=SpellTimeline.IMPACT) {
            float t=age-SpellTimeline.IMPACT;
            float glow=(float)Math.exp(-t/65)*distanceFade*(float)CelestialFall.CONFIG.purpleGlowStrength;
            int strip=Math.max(1,height/100);
            for(int i=0;i<24;i++) {
                int a=(int)(glow*(1-i/24f)*32);
                if(a<=0)continue;
                context.fill(0,i*strip,width,(i+1)*strip,(a<<24)|0x9C35FF);
                context.fill(0,height-(i+1)*strip,width,height-i*strip,(a<<24)|0x9C35FF);
            }
        }
        float cinema=CinematicDirector.blend(delta);
        if(cinema>0 && !mc.options.hudHidden) {
            int bar=(int)(height*.115f*cinema);
            context.fill(0,0,width,bar,0xFF05030A);
            context.fill(0,height-bar,width,height,0xFF05030A);
            if(cinema>.75f)context.drawCenteredTextWithShadow(mc.textRenderer,
                Text.translatable("hud.celestialfall.skip",CinematicDirector.skip.getBoundKeyLocalizedText()),
                width/2,height-bar/2-4,0xFFC7B9DF);
        }
        if (!CelestialFall.CONFIG.titleOverlay || mc.options.hudHidden) return;
        float cut=SpellTimeline.smooth(SpellTimeline.progress(age,SpellTimeline.REVEAL,SpellTimeline.REVEAL+2))
            *(1-SpellTimeline.smooth(SpellTimeline.progress(age,SpellTimeline.REVEAL+13,SpellTimeline.DESCENT_START+1)));
        if(cut>.02f) {
            int a=(int)(cut*235),top=(int)(height*.48),bottom=(int)(height*.72);
            context.fill(0,top,width,bottom,(a<<24)|0x07020F);
            context.fill(0,top,width,top+2,((int)(cut*255)<<24)|0xE9C9FF);
            context.fill(0,bottom-2,width,bottom,((int)(cut*255)<<24)|0xAB60FF);
            var m=context.getMatrices();m.push();
            m.translate(width*.5f,height*.535f,0);
            m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(-4));
            String word="天外流星";
            float scale=Math.min(5.8f,width*.77f/mc.textRenderer.getWidth(word));
            scale*=1+.20f*(1-SpellTimeline.progress(age,SpellTimeline.REVEAL,SpellTimeline.REVEAL+7));
            m.scale(scale,scale,1);
            int x=-mc.textRenderer.getWidth(word)/2,ink=(int)(cut*255)<<24;
            context.drawText(mc.textRenderer,word,x+1,1,ink|0x8225DE,false);
            context.drawText(mc.textRenderer,word,x,0,ink|0xFFF3FF,true);
            m.pop();
        }
        float opacity = SpellTimeline.smooth(SpellTimeline.progress(age, 24, 40))
            * (1 - SpellTimeline.smooth(SpellTimeline.progress(age, 85, 105))) * distanceFade;
        if (opacity > 0.04) {
            int alpha = Math.max(5, (int) (opacity * 255));
            var matrices = context.getMatrices();
            matrices.push(); matrices.translate(width / 2f, height * 0.30f, 0); matrices.scale(2, 2, 1);
            String title = "天 外 流 星";
            context.drawText(mc.textRenderer, title, -mc.textRenderer.getWidth(title) / 2, 0, (alpha << 24) | 0xF3E6FF, true);
            matrices.pop();
            context.drawCenteredTextWithShadow(mc.textRenderer, Text.translatable("hud.celestialfall.subtitle"),
                width / 2, (int) (height * 0.30f + 28), (alpha << 24) | 0xBBA9E8);
            int span = (int) (width * 0.13);
            context.fill(width / 2 - span, (int) (height * 0.30f + 24), width / 2 + span, (int) (height * 0.30f + 25),
                ((int)(alpha * 0.55) << 24) | 0xAB89DE);
        }
    }
}
