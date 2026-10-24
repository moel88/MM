package dev.sangwoo.celestialfall.client.loot;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.loot.*;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Ordinary buffered entity materials; no fullscreen shader or custom framebuffer dependency. */
public final class FieldLootRenderer extends EntityRenderer<FieldLootEntity> {
    private static final Identifier WHITE=new Identifier("minecraft","textures/block/white_concrete.png");
    public FieldLootRenderer(EntityRendererFactory.Context context){super(context);shadowRadius=.62f;}
    @Override public Identifier getTexture(FieldLootEntity entity){return WHITE;}
    @Override public void render(FieldLootEntity e,float yaw,float delta,MatrixStack m,VertexConsumerProvider buffers,int light){
        if(!MapClient.enabled())return;
        float elapsed=e.opened()?(float)(e.getWorld().getTime()-e.openedAt()+delta):-1;
        float t=Math.max(0,Math.min(1,elapsed/18)),open=t*t*(3-2*t),alpha=elapsed<42?1:Math.max(0,(LootRules.OPEN_TICKS-elapsed)/18f);
        if(alpha<=0)return;
        m.push();m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
        var out=buffers.getBuffer(alpha<1?RenderLayer.getEntityTranslucent(WHITE):RenderLayer.getEntityCutoutNoCull(WHITE));
        if(e.treasure()){
            LootModel.CHEST.draw(m,out,light,alpha);
            m.push();m.translate(0,.56,.44);m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(open*112));m.translate(0,-.56,-.44);LootModel.LID.draw(m,out,light,alpha);m.pop();
            if(open>.08)glow(m,buffers,elapsed,open*alpha);
        }else{
            LootModel.BOX.draw(m,out,light,alpha);
            for(int side:new int[]{-1,1}){
                m.push();m.translate(0,.743,side*.419);m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side==1?180:0));m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-open*115));LootModel.FRONT.draw(m,out,light,alpha);m.pop();
                m.push();m.translate(side*.565,.768,0);m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side==1?180:0));m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(open*112));LootModel.SIDE.draw(m,out,light,alpha);m.pop();
            }
        }
        m.pop();super.render(e,yaw,delta,m,buffers,light);
    }
    private void glow(MatrixStack m,VertexConsumerProvider buffers,float age,float intensity){
        var out=buffers.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));
        var core=new LootModel.Mesh();
        core.box(-.59,.52,-.38,1.18,.015,.76,0xffeaa5);core.draw(m,out,LightmapTextureManager.MAX_LIGHT_COORDINATE,intensity*.95f);
        var rays=new LootModel.Mesh();
        for(int i=0;i<10;i++){
            double a=i*Math.PI/5,spread=.6,height=.8+(i%3)*.28;
            double x=Math.cos(a),z=Math.sin(a);
            rays.face(i%2==0?0xffe7a1:0xffc34d,0,1,0,x*.18,.55,z*.13,x*.36,.55,z*.26,x*spread, .55+height,z*spread*.7,x*spread*.5,.55+height,z*spread*.35);
        }
        rays.draw(m,out,LightmapTextureManager.MAX_LIGHT_COORDINATE,intensity*.18f);
        var spark=new LootModel.Mesh();
        for(int i=0;i<13;i++){
            double u=(age*.022+i*.073)%1,x=Math.sin(i*4.3)*.58,z=Math.cos(i*3.7)*.4,y=.58+u*1.4,r=.015+Math.sin(u*Math.PI)*.018;
            spark.face(0xffefbc,0,0,-1,x-r,y,z,x,y+r*2,z,x+r,y,z,x,y-r*2,z);
            spark.face(0xfff8e1,1,0,0,x,y-r,z,x,y,z+r*2,x,y+r,z,x,y,z-r*2);
        }
        spark.draw(m,out,LightmapTextureManager.MAX_LIGHT_COORDINATE,intensity*.95f);
    }
}
