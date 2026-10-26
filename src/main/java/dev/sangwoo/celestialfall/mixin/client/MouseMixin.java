package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.*;
import net.minecraft.client.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin implements MouseCapture {
    @Shadow @Final private MinecraftClient client;
    @Shadow private boolean cursorLocked,hasResolutionChanged,leftButtonClicked,rightButtonClicked,middleButtonClicked;
    @Shadow private double x,y,cursorDeltaX,cursorDeltaY;
    @Unique private double botand$savedX,botand$savedY;
    @Unique private boolean botand$hasSaved;
    @Override public void botand$capture(boolean capture) {
        if(cursorLocked==capture)return;
        long window=client.getWindow().getHandle();
        if(capture) {
            botand$savedX=x;botand$savedY=y;botand$hasSaved=true;
            x=client.getWindow().getWidth()/2.0;y=client.getWindow().getHeight()/2.0;
        } else if(botand$hasSaved) {
            x=Math.max(0,Math.min(client.getWindow().getWidth(),botand$savedX));
            y=Math.max(0,Math.min(client.getWindow().getHeight(),botand$savedY));
        }
        cursorLocked=capture;cursorDeltaX=cursorDeltaY=0;hasResolutionChanged=true;
        InputUtil.setCursorParameters(window,capture?GLFW.GLFW_CURSOR_DISABLED:GLFW.GLFW_CURSOR_NORMAL,x,y);
    }
    @Inject(method="lockCursor",at=@At("HEAD"),cancellable=true)
    private void botand$lock(CallbackInfo ci) {
        if(BotandCamera.enabled()&&client.currentScreen==null){BotandCamera.refreshCapture();ci.cancel();}
    }
    @Inject(method="onMouseButton",at=@At("HEAD"),cancellable=true)
    private void botand$buttons(long window,int button,int action,int modifiers,CallbackInfo ci) {
        if(dev.sangwoo.celestialfall.client.menu.MenuClient.mouse(window,button,action)){ci.cancel();return;}
        if(window!=client.getWindow().getHandle()||!BotandCamera.gameplay())return;
        boolean down=action==GLFW.GLFW_PRESS;
        if(button==GLFW.GLFW_MOUSE_BUTTON_RIGHT){rightButtonClicked=down;BotandCamera.rightButton(down);ci.cancel();return;}
        if(button==GLFW.GLFW_MOUSE_BUTTON_LEFT)leftButtonClicked=down;
        if(button==GLFW.GLFW_MOUSE_BUTTON_MIDDLE)middleButtonClicked=down;
        var key=InputUtil.Type.MOUSE.createFromCode(button);KeyBinding.setKeyPressed(key,down);
        if(down)KeyBinding.onKeyPressed(key);
        ci.cancel();
    }
    @Inject(method="onMouseScroll",at=@At("HEAD"),cancellable=true)
    private void botand$wheel(long window,double horizontal,double vertical,CallbackInfo ci) {
        // stopped in Star Platinum's stopped time (2.67.0): the camera stays as it was
        if(window==client.getWindow().getHandle()&&client.currentScreen==null&&dev.sangwoo.celestialfall.client.stand.StandTimeStop.stillView()){ci.cancel();return;}
        // over the player list with the cursor free, in play or with the chat open (2.73.0): the wheel scrolls the list
        if(window==client.getWindow().getHandle()&&(client.currentScreen==null||client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)
            &&dev.sangwoo.celestialfall.client.ui.PlayerPanel.wheel(vertical)){ci.cancel();return;}
        if(window==client.getWindow().getHandle()&&BotandCamera.gameplay()) {
            BotandCamera.scroll(vertical*client.options.getMouseWheelSensitivity().getValue());ci.cancel();
        }
    }
    @Redirect(method="updateMouse",at=@At(value="INVOKE",target="Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void botand$look(ClientPlayerEntity player,double x,double y) {
        // stopped in Star Platinum's stopped time (2.67.0): no looking about until time moves again
        if(dev.sangwoo.celestialfall.client.stand.StandTimeStop.stillView())return;
        if(BotandCamera.enabled())BotandCamera.look(x,y);else player.changeLookDirection(x,y);
    }
}
