package Csekiro.arena.client.render;

import Csekiro.arena.entity.PortalEntity;
import Csekiro.arena.portal.PortalMath;
import Csekiro.arena.portal.PortalPose;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public class PortalEntityRenderer extends EntityRenderer<PortalEntity, PortalEntityRenderer.PortalEntityRenderState> {
    public PortalEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public PortalEntityRenderState createRenderState() {
        return new PortalEntityRenderState();
    }

    @Override
    public void updateRenderState(PortalEntity entity, PortalEntityRenderState state, float tickProgress) {
        super.updateRenderState(entity, state, tickProgress);
        PortalRenderManager renderManager = PortalRenderManager.getInstance();
        state.pose = entity.getPortalPose();
        Identifier capturedTexture = renderManager.getFrameTexture(entity);
        boolean renderBlackFace = PortalRenderState.shouldRenderBlackFace(entity);
        state.innerTexture = renderBlackFace
                ? renderManager.getBlackTexture()
                : (capturedTexture != null ? capturedTexture : renderManager.getWhiteTexture());
        state.frameColor = 0xFF000000 | entity.getColor().rgb();
        state.innerColor = renderBlackFace
                ? 0xFF000000
                : (capturedTexture != null ? 0xFFFFFFFF : (0x88000000 | entity.getColor().rgb()));
    }

    @Override
    public void render(PortalEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
        super.render(state, matrices, queue, cameraState);
        if (state.pose == null) {
            return;
        }

        queue.submitCustom(matrices, RenderLayer.getEntityTranslucent(state.innerTexture), (entry, consumer) ->
                drawPanel(state.pose, entry, consumer, -0.5D, 0.5D, -1.0D, 1.0D, 0.002D, state.innerColor, state.light));

        queue.submitCustom(matrices, RenderLayer.getLines(), (entry, consumer) ->
                drawFrame(state.pose, entry, consumer, state.frameColor, LightmapTextureManager.MAX_LIGHT_COORDINATE));
    }

    private static void drawPanel(PortalPose pose, MatrixStack.Entry entry, VertexConsumer consumer, double minU, double maxU, double minV, double maxV, double depth, int color, int light) {
        Vec3d width = PortalMath.vec(pose.widthAxis());
        Vec3d height = PortalMath.vec(pose.heightAxis());
        Vec3d normal = PortalMath.vec(pose.normal());

        Vec3d bottomLeft = width.multiply(minU).add(height.multiply(minV)).add(normal.multiply(depth));
        Vec3d topLeft = width.multiply(minU).add(height.multiply(maxV)).add(normal.multiply(depth));
        Vec3d topRight = width.multiply(maxU).add(height.multiply(maxV)).add(normal.multiply(depth));
        Vec3d bottomRight = width.multiply(maxU).add(height.multiply(minV)).add(normal.multiply(depth));

        putVertex(consumer, entry, bottomLeft, 0.0F, 1.0F, color, light, normal);
        putVertex(consumer, entry, topLeft, 0.0F, 0.0F, color, light, normal);
        putVertex(consumer, entry, topRight, 1.0F, 0.0F, color, light, normal);
        putVertex(consumer, entry, bottomRight, 1.0F, 1.0F, color, light, normal);
    }

    private static void drawFrame(PortalPose pose, MatrixStack.Entry entry, VertexConsumer consumer, int color, int light) {
        Vec3d bottomLeft = corner(pose, -0.5D, -1.0D, 0.004D);
        Vec3d topLeft = corner(pose, -0.5D, 1.0D, 0.004D);
        Vec3d topRight = corner(pose, 0.5D, 1.0D, 0.004D);
        Vec3d bottomRight = corner(pose, 0.5D, -1.0D, 0.004D);

        putLineVertex(consumer, entry, bottomLeft, topLeft.subtract(bottomLeft), color, light);
        putLineVertex(consumer, entry, topLeft, bottomLeft.subtract(topLeft), color, light);
        putLineVertex(consumer, entry, topLeft, topRight.subtract(topLeft), color, light);
        putLineVertex(consumer, entry, topRight, topLeft.subtract(topRight), color, light);
        putLineVertex(consumer, entry, topRight, bottomRight.subtract(topRight), color, light);
        putLineVertex(consumer, entry, bottomRight, topRight.subtract(bottomRight), color, light);
        putLineVertex(consumer, entry, bottomRight, bottomLeft.subtract(bottomRight), color, light);
        putLineVertex(consumer, entry, bottomLeft, bottomRight.subtract(bottomLeft), color, light);
    }

    private static Vec3d corner(PortalPose pose, double u, double v, double depth) {
        return PortalMath.vec(pose.widthAxis()).multiply(u)
                .add(PortalMath.vec(pose.heightAxis()).multiply(v))
                .add(PortalMath.vec(pose.normal()).multiply(depth));
    }

    private static void putVertex(VertexConsumer consumer, MatrixStack.Entry entry, Vec3d position, float u, float v, int color, int light, Vec3d normal) {
        consumer.vertex(entry, (float) position.x, (float) position.y, (float) position.z)
                .color(color)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static void putLineVertex(VertexConsumer consumer, MatrixStack.Entry entry, Vec3d position, Vec3d normal, int color, int light) {
        consumer.vertex(entry, (float) position.x, (float) position.y, (float) position.z)
                .color(color)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    public static class PortalEntityRenderState extends EntityRenderState {
        public PortalPose pose;
        public Identifier innerTexture;
        public int frameColor;
        public int innerColor;
    }
}
