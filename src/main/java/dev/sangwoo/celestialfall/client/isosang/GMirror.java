package dev.sangwoo.celestialfall.client.isosang;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Lee So-sang's G's shots of her hand turned back left for right (2.87.0). Its hand is her right (the user's preview), so
 * those shots are taken from the other side of her (GGesture.handShot) and come out the videos' mirror image: the arm in
 * from the lower right, the fingers to the left. The finished picture of the world is flipped across before the GUI
 * draws over it, so the frame is the videos' again (the arm in from the lower left, the fingers up and out to the upper
 * right). Nothing else is drawn in those shots (their black, GBlackout), and nothing changes for anyone else.
 */
final class GMirror {
    private GMirror(){}
    private static SimpleFramebuffer copy;

    /** Flips the main framebuffer's picture left for right if the local camera is in one of G's shots of her hand. */
    static void apply(float delta){
        if(!GStage.supported())return;
        var c=FallingSword.cinematic();if(c==null)return;
        if(!FallingSword.mirrored(c.age(delta)/20f))return;
        Framebuffer main=MinecraftClient.getInstance().getFramebuffer();
        int w=main.textureWidth,h=main.textureHeight;
        if(copy==null)copy=new SimpleFramebuffer(w,h,false,MinecraftClient.IS_SYSTEM_MAC);
        else if(copy.textureWidth!=w||copy.textureHeight!=h)copy.resize(w,h,MinecraftClient.IS_SYSTEM_MAC);
        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        // the picture as it is into the copy, then back across (its left edge to the right)
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.fbo);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,copy.fbo);
        GL30.glBlitFramebuffer(0,0,w,h,0,0,w,h,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,copy.fbo);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,main.fbo);
        GL30.glBlitFramebuffer(0,0,w,h,w,0,0,h,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);
        RenderSystem.assertOnRenderThread();
    }
}
