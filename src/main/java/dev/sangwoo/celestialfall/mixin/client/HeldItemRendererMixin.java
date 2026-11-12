package dev.sangwoo.celestialfall.mixin.client;
import dev.sangwoo.celestialfall.client.CombatHands;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @org.spongepowered.asm.mixin.Shadow
    private void renderFirstPersonItem(net.minecraft.client.network.AbstractClientPlayerEntity player,float delta,float pitch,net.minecraft.util.Hand hand,float swing,net.minecraft.item.ItemStack stack,float equip,MatrixStack matrices,VertexConsumerProvider vertices,int light){throw new AssertionError();}

    @Inject(method="renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",at=@At("HEAD"),cancellable=true)
    private void botand$hands(float delta,MatrixStack matrices,VertexConsumerProvider.Immediate vertices,ClientPlayerEntity player,int light,CallbackInfo ci) {
        if(dev.sangwoo.celestialfall.client.stand.StandArrowClient.frame(player,delta)!=null){
            // the stand arrow driven into the chest (2.70.0): the forearms and the arrow from one's own eyes
            dev.sangwoo.celestialfall.client.stand.StandArrowFeature.renderFirstPerson(player,delta,matrices,vertices,light);
            vertices.draw();
            ci.cancel();return;
        }
        if(dev.sangwoo.celestialfall.client.meteor.MiningPose.holding(player)){
            // the meteor pickaxe: in both hands, or as the game holds it while the hold cannot show (a stand user too)
            if(!dev.sangwoo.celestialfall.client.meteor.MiningPose.active(player,delta))return;
            dev.sangwoo.celestialfall.client.meteor.MiningPose.renderFirstPerson(player,delta,matrices,vertices,light);
            vertices.draw();
            ci.cancel();return;
        }
        if(dev.sangwoo.celestialfall.client.StandClient.visible(player)){
            float swing=player.getHandSwingProgress(delta);
            var cast=dev.sangwoo.celestialfall.client.kageno.KagenoClips.animated(player.getUuid());
            matrices.push();
            var atomic=dev.sangwoo.celestialfall.client.AtomicClient.poseCast(player.getUuid());
            if(atomic!=null){
                // First person: the sword rises overhead with the A-frame and comes down as the arms spread before the blast.
                float sec=atomic.age(delta)/20f;
                float raise=dev.sangwoo.celestialfall.client.kageno.Ease.smooth(sec,.067f,.267f)*(1-dev.sangwoo.celestialfall.client.kageno.Ease.smooth(sec,5.30f,5.55f));
                matrices.translate(-raise*.12,raise*.42,-raise*.12);
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(raise*25));swing=0;
            }
            renderFirstPersonItem(player,delta,player.getPitch(delta),net.minecraft.util.Hand.MAIN_HAND,player.preferredHand==net.minecraft.util.Hand.OFF_HAND?0:swing,dev.sangwoo.celestialfall.client.StandClient.sword(),0,matrices,vertices,light);
            matrices.pop();
            if(!player.getOffHandStack().isEmpty())renderFirstPersonItem(player,delta,player.getPitch(delta),net.minecraft.util.Hand.OFF_HAND,player.preferredHand==net.minecraft.util.Hand.OFF_HAND?swing:0,player.getOffHandStack(),0,matrices,vertices,light);
            // Vanilla flushes this buffer at the end of the cancelled outer method.
            vertices.draw();
            ci.cancel();return;
        }
        // (2.105.0) Lee So-sang using what she holds (eating, drinking) with her sword away: as the game draws it
        float up=dev.sangwoo.celestialfall.client.StandClient.isosang(player)?dev.sangwoo.celestialfall.client.isosang.IsosangSheath.held(player.getUuid(),delta):0;
        if(dev.sangwoo.celestialfall.client.StandClient.isosang(player)&&!(player.isUsingItem()&&up<=.001f)){
            // Lee So-sang's sword (2.76.0), as the game holds an item (its swing, the off hand as it is); (2.105.0) only
            // while she uses it: it comes up into the picture as it comes into her hand and goes down out of it as it
            // goes (IsosangSheath.held: the game's own lowering and raising of a held item); nothing else in her hand
            float swing=player.getHandSwingProgress(delta);
            if(up>.001f){
                // (2.80.0) glowing while their next combo is empowered (E)
                dev.sangwoo.celestialfall.client.isosang.IsosangSwordRenderer.charge=dev.sangwoo.celestialfall.client.isosang.IsosangClient.glow(player.getUuid(),delta);
                dev.sangwoo.celestialfall.client.isosang.IsosangSwordRenderer.clock=player.age+delta;
                renderFirstPersonItem(player,delta,player.getPitch(delta),net.minecraft.util.Hand.MAIN_HAND,player.preferredHand==net.minecraft.util.Hand.OFF_HAND?0:swing,dev.sangwoo.celestialfall.client.StandClient.isosangSword(),1-up,matrices,vertices,light);
                vertices.draw();
            }
            dev.sangwoo.celestialfall.client.isosang.IsosangSwordRenderer.charge=0;
            if(!player.getOffHandStack().isEmpty())renderFirstPersonItem(player,delta,player.getPitch(delta),net.minecraft.util.Hand.OFF_HAND,player.preferredHand==net.minecraft.util.Hand.OFF_HAND?swing:0,player.getOffHandStack(),0,matrices,vertices,light);
            vertices.draw();
            ci.cancel();return;
        }
        if(dev.sangwoo.celestialfall.client.StandClient.xChara(player)&&!player.isUsingItem()){
            // X Chara's knife (2.111.0), as the game holds an item (its swing, the off hand as it is)
            float swing=player.getHandSwingProgress(delta);
            // (2.115.0) their hack (Q): the knife lit white within its purple rim, the panel in the other hand
            var hack=dev.sangwoo.celestialfall.client.xchara.HackMode.look(player.getUuid(),delta);
            dev.sangwoo.celestialfall.client.xchara.XCharaKnifeRenderer.hack=hack;
            renderFirstPersonItem(player,delta,player.getPitch(delta),net.minecraft.util.Hand.MAIN_HAND,player.preferredHand==net.minecraft.util.Hand.OFF_HAND?0:swing,dev.sangwoo.celestialfall.client.StandClient.xCharaKnife(),0,matrices,vertices,light);
            dev.sangwoo.celestialfall.client.xchara.XCharaKnifeRenderer.hack=null;
            var panel=dev.sangwoo.celestialfall.client.xchara.HackMode.panel(player);
            if(panel!=null){
                dev.sangwoo.celestialfall.client.xchara.HackPanel.frame=hack;
                dev.sangwoo.celestialfall.client.xchara.HackPanel.press=dev.sangwoo.celestialfall.client.xchara.HealClient.press(player.getUuid(),delta);
                renderFirstPersonItem(player,delta,player.getPitch(delta),net.minecraft.util.Hand.OFF_HAND,0,panel,0,matrices,vertices,light);
                dev.sangwoo.celestialfall.client.xchara.HackPanel.frame=null;dev.sangwoo.celestialfall.client.xchara.HackPanel.press=null;
            }
            else if(!player.getOffHandStack().isEmpty())renderFirstPersonItem(player,delta,player.getPitch(delta),net.minecraft.util.Hand.OFF_HAND,player.preferredHand==net.minecraft.util.Hand.OFF_HAND?swing:0,player.getOffHandStack(),0,matrices,vertices,light);
            vertices.draw();
            ci.cancel();return;
        }
        if(CombatHands.enabled(player)){CombatHands.render(player,delta,matrices,vertices,light);ci.cancel();}
    }
}
