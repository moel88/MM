package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.CelestialFall;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import com.mojang.blaze3d.systems.RenderSystem;

public final class BotandHud {
    private static final Identifier ART=CelestialFall.id("textures/botand_hud.png"),ICONS=new Identifier("textures/gui/icons.png");
    private BotandHud() {}
    public static boolean enabled(){var mc=MinecraftClient.getInstance();return MapClient.enabled()&&ProgressClient.ready&&mc.player!=null&&mc.world!=null;}
    public static void render(DrawContext c,float delta) {
        var mc=MinecraftClient.getInstance();if(!enabled()||mc.options.hudHidden||CinematicDirector.isActive()||mc.player.isSpectator())return;
        var state=CombatClient.snapshot(mc.player.getUuid());boolean broken=state.broken(mc.world.getTime()+delta);
        float guard=BotandHudLayout.guard(state.pressure(),broken);
        // (incapacitated, 2.60.0: its word over the guard bar, as BREAK over a broken guard)
        var layout=BotandHudLayout.frame(mc.player.getHealth(),mc.player.getMaxHealth(),guard,ProgressClient.profile,broken,state.disabled(mc.world.getTime()+delta));
        // Keep all artwork, bars and labels behind vanilla debug text in depth as well as order.
        var matrix=c.getMatrices();matrix.push();matrix.translate(8,8,-20);
        float scale=(float)(BotandHudLayout.width(c.getScaledWindowWidth())/BotandHudLayout.WIDTH);matrix.scale(scale,scale,1);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);
        c.drawTexture(ART,0,0,2048,683,0f,0f,2172,724,2172,724);c.draw();
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableDepthTest();RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);RenderSystem.setShaderColor(1,1,1,1);
        var buffer=Tessellator.getInstance().getBuffer();buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
        var pose=matrix.peek().getPositionMatrix();
        for(var quad:layout.quads())for(int i=0;i<4;i++) {
            int color=i<2?quad.top():quad.bottom();
            buffer.vertex(pose,(float)quad.xy()[i*2],(float)quad.xy()[i*2+1],1).color(color>>16&255,color>>8&255,color&255,color>>>24).next();
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());RenderSystem.enableDepthTest();RenderSystem.enableCull();
        for(var label:layout.labels()) {
            matrix.push();matrix.translate(label.x(),label.y(),3);matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float)label.angle()));
            float fontScale=(float)Math.min(label.scale(),label.maxWidth()/Math.max(1,mc.textRenderer.getWidth(label.text())));
            matrix.scale(fontScale,fontScale,1);int x=-mc.textRenderer.getWidth(label.text())/2;
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)if(dx!=0||dy!=0)c.drawText(mc.textRenderer,label.text(),x+dx,dy,label.outline(),false);
            c.drawText(mc.textRenderer,label.text(),x,0,label.color(),false);matrix.pop();
        }
        c.draw();matrix.pop();
        // the money and XP line on its plate under the art (2.73.0: smaller with it, in the UI's font)
        var growth=ProgressClient.profile;matrix.push();matrix.translate(0,0,-20);
        double infoTop=BotandHudLayout.infoTop(c.getScaledWindowWidth()),plate=BotandHudLayout.INFO_HEIGHT;float size=(float)BotandHudLayout.INFO_SIZE;
        String info=String.format(java.util.Locale.ROOT,"%,d원",growth.money())+"  ·  "+(growth.level()==50?"Lv.50 MAX":"XP "+growth.xp()+" / "+dev.sangwoo.celestialfall.progression.ProgressProfile.requiredXp(growth.level()));
        float pad=size*.55f,textWidth=dev.sangwoo.celestialfall.client.gacha.GachaFont.width(info,size,dev.sangwoo.celestialfall.client.gacha.GachaFont.Weight.MEDIUM);
        c.fill(13,(int)infoTop,(int)Math.ceil(13+2*pad+textWidth),(int)Math.round(infoTop+plate),0xcc153654);
        dev.sangwoo.celestialfall.client.gacha.GachaFont.text(info,13+pad,(float)(infoTop+plate/2),size,dev.sangwoo.celestialfall.client.gacha.GachaFont.Weight.MEDIUM).color(0xffdaf5ff).draw(c);
        matrix.pop();
        RenderSystem.setShaderColor(1,1,1,1);RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();
    }
    /** Preserve armor and breathing information without drawing hearts or food icons. */
    public static void survivalIndicators(DrawContext c) {
        var mc=MinecraftClient.getInstance();if(mc.player==null)return;var p=mc.player;
        // (2.69.0: a step up over the boss bar while it shows, boss/BossBar)
        int y=c.getScaledWindowHeight()-39-Math.round(10*dev.sangwoo.celestialfall.client.boss.BossBar.shown()),left=c.getScaledWindowWidth()/2-91,right=c.getScaledWindowWidth()/2+91;
        int armor=p.getArmor();if(armor>0)for(int i=0;i<10;i++)c.drawTexture(ICONS,left+i*8,y,i*2+1<armor?34:i*2+1==armor?25:16,9,9,9);
        if(p.isSubmergedIn(net.minecraft.registry.tag.FluidTags.WATER)||p.getAir()<p.getMaxAir()) {
            int air=Math.max(0,p.getAir()),max=Math.max(1,p.getMaxAir());
            int full=(int)Math.ceil((air-2)*10./max),all=(int)Math.ceil(air*10./max);
            for(int i=0;i<all;i++)c.drawTexture(ICONS,right-i*8-9,y,i<full?16:25,18,9,9);
        }
    }
}
