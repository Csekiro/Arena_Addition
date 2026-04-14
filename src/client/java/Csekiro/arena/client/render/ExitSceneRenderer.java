package Csekiro.arena.client.render;

import Csekiro.arena.entity.PortalEntity;
import Csekiro.arena.mixin.client.MinecraftClientAccessor;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.Set;
import java.util.UUID;

public final class ExitSceneRenderer {
    public boolean render(
            MinecraftClient client,
            ObjectAllocator allocator,
            RenderTickCounter tickCounter,
            PortalCamera portalCamera,
            PortalFrameBufferPool.PooledFramebuffer target,
            PortalEntity sourcePortal,
            Vec3d exitCameraPos,
            float exitCameraYaw,
            float exitCameraPitch,
            Matrix4f worldProjectionMatrix,
            Matrix4f projectionMatrix,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean renderSky
    ) {
        Entity focusedEntity = client.gameRenderer.getCamera().getFocusedEntity();
        if (client.world == null || focusedEntity == null) {
            return false;
        }

        float tickProgress = tickCounter.getTickProgress(true);
        portalCamera.configure(client.world, focusedEntity, exitCameraPos, exitCameraYaw, exitCameraPitch, tickProgress);
        Matrix4f exitPositionMatrix = new Matrix4f().rotation(portalCamera.getRotation().conjugate(new Quaternionf()));
        Frustum exitFrustum = new Frustum(new Matrix4f(exitPositionMatrix), new Matrix4f(projectionMatrix));
        exitFrustum.setPosition(exitCameraPos.x, exitCameraPos.y, exitCameraPos.z);
        Set<UUID> maskedPortals = PortalMaskComposer.collectMaskedPortals(client.world, exitFrustum, exitCameraPos);

        Framebuffer originalFramebuffer = client.getFramebuffer();
        ((MinecraftClientAccessor) client).arena$setFramebuffer(target.framebuffer());
        PortalRenderState.beginExitView(maskedPortals);
        try {
            client.worldRenderer.render(
                    allocator,
                    tickCounter,
                    false,
                    portalCamera,
                    exitPositionMatrix,
                    new Matrix4f(worldProjectionMatrix),
                    new Matrix4f(projectionMatrix),
                    fogBuffer,
                    fogColor,
                    renderSky
            );
            target.copyToSampledTexture();
            return true;
        } finally {
            PortalRenderState.endExitView();
            ((MinecraftClientAccessor) client).arena$setFramebuffer(originalFramebuffer);
        }
    }
}
