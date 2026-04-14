package Csekiro.arena.mixin.client;

import Csekiro.arena.client.render.PortalRenderManager;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.Pool;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    @Final
    private Pool pool;

    @Redirect(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"
            )
    )
    private void arena$renderWorldWithPortalCapture(
            WorldRenderer worldRenderer,
            net.minecraft.client.util.ObjectAllocator allocator,
            RenderTickCounter renderTickCounter,
            boolean renderBlockOutline,
            Camera camera,
            Matrix4f positionMatrix,
            Matrix4f worldProjectionMatrix,
            Matrix4f projectionMatrix,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean renderSky
    ) {
        PortalRenderManager.getInstance().capturePortalViews(
                this.client,
                this.pool,
                renderTickCounter,
                camera,
                positionMatrix,
                worldProjectionMatrix,
                projectionMatrix,
                fogBuffer,
                fogColor,
                renderSky
        );
        worldRenderer.render(
                allocator,
                renderTickCounter,
                renderBlockOutline,
                camera,
                positionMatrix,
                worldProjectionMatrix,
                projectionMatrix,
                fogBuffer,
                fogColor,
                renderSky
        );
    }
}
