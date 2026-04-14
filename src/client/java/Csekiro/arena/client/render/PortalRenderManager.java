package Csekiro.arena.client.render;

import Csekiro.arena.mixin.client.MinecraftClientAccessor;
import Csekiro.arena.entity.PortalEntity;
import Csekiro.arena.portal.PortalMath;
import Csekiro.arena.portal.PortalPose;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PortalRenderManager {
    private static final PortalRenderManager INSTANCE = new PortalRenderManager();
    private static final Identifier BLACK_TEXTURE = Identifier.of("arena", "dynamic/portal_black");
    private static final Identifier WHITE_TEXTURE = Identifier.of("arena", "dynamic/portal_white");

    private final PortalFrameBufferPool frameBufferPool = new PortalFrameBufferPool();
    private final Map<UUID, PortalTextureEntry> portalTextures = new HashMap<>();
    private final PortalCamera portalCamera = new PortalCamera();
    private boolean initialized;
    private boolean capturing;

    private PortalRenderManager() {
    }

    public static PortalRenderManager getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (this.initialized) {
            return;
        }

        this.initialized = true;
    }

    public boolean isCapturing() {
        return this.capturing;
    }

    public Identifier getPortalTexture(PortalEntity portal) {
        if (!hasClientPair(portal)) {
            return BLACK_TEXTURE;
        }

        PortalTextureEntry entry = this.portalTextures.get(portal.getUuid());
        return entry == null ? BLACK_TEXTURE : entry.textureId;
    }

    public Identifier getWhiteTexture() {
        return WHITE_TEXTURE;
    }

    public Identifier getBlackTexture() {
        return BLACK_TEXTURE;
    }

    public boolean hasClientPair(PortalEntity portal) {
        return findClientPair(portal) != null;
    }

    public void capturePortalViews(
            MinecraftClient client,
            ObjectAllocator allocator,
            RenderTickCounter tickCounter,
            Camera camera,
            Matrix4f positionMatrix,
            Matrix4f worldProjectionMatrix,
            Matrix4f projectionMatrix,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean renderSky
    ) {
        if (this.capturing || client.world == null || client.player == null || !camera.isReady()) {
            return;
        }

        ensureSolidTextures(client);
        List<PortalEntity> visiblePortals = collectVisiblePortals(client.world, camera.getPos());
        if (visiblePortals.isEmpty()) {
            return;
        }

        Matrix4f mainProjection = new Matrix4f(worldProjectionMatrix);
        Framebuffer originalFramebuffer = client.getFramebuffer();
        for (PortalEntity portal : visiblePortals) {
            PortalEntity pair = findClientPair(portal);
            if (pair == null) {
                continue;
            }

            PortalTextureEntry entry = this.portalTextures.computeIfAbsent(portal.getUuid(), uuid -> createTextureEntry(client, uuid));
            renderPortalView(client, allocator, tickCounter, camera, portal, pair, positionMatrix, mainProjection, projectionMatrix, fogBuffer, fogColor, renderSky, originalFramebuffer, entry);
        }

        ((MinecraftClientAccessor) client).arena$setFramebuffer(originalFramebuffer);
    }

    @Nullable
    public PortalEntity findClientPair(PortalEntity portal) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || portal.getOwnerUuid() == null) {
            return null;
        }

        return client.world.getEntitiesByClass(
                        PortalEntity.class,
                        portal.getBoundingBox().expand(256.0D),
                        other -> other != portal
                                && portal.getColor().opposite() == other.getColor()
                                && portal.getOwnerUuid().equals(other.getOwnerUuid())
                )
                .stream()
                .findFirst()
                .orElse(null);
    }

    private List<PortalEntity> collectVisiblePortals(ClientWorld world, Vec3d cameraPos) {
        List<PortalEntity> portals = new ArrayList<>(
                world.getEntitiesByClass(
                        PortalEntity.class,
                        world.getWorldBorder().asVoxelShape().getBoundingBox(),
                        portal -> portal.getPortalPose().signedDistance(cameraPos) >= -0.25D && cameraPos.squaredDistanceTo(portal.getEntityPos()) <= 96.0D * 96.0D
                )
        );
        portals.sort(Comparator.comparingDouble(portal -> cameraPos.squaredDistanceTo(portal.getEntityPos())));
        return portals.size() > 4 ? portals.subList(0, 4) : portals;
    }

    private void renderPortalView(
            MinecraftClient client,
            ObjectAllocator allocator,
            RenderTickCounter tickCounter,
            Camera camera,
            PortalEntity portal,
            PortalEntity pair,
            Matrix4f positionMatrix,
            Matrix4f mainProjection,
            Matrix4f projectionMatrix,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean renderSky,
            Framebuffer originalFramebuffer,
            PortalTextureEntry entry
    ) {
        Entity focusedEntity = camera.getFocusedEntity();
        if (focusedEntity == null) {
            return;
        }

        SimpleFramebuffer framebuffer = this.frameBufferPool.get(client.getWindow());
        ((MinecraftClientAccessor) client).arena$setFramebuffer(framebuffer);
        this.capturing = true;
        try {
            float tickProgress = tickCounter.getTickProgress(true);
            Vec3d mappedCameraPos = mapCameraPosition(camera.getPos(), portal.getPortalPose(), pair.getPortalPose());
            float mappedYaw = mapCameraYaw(camera.getYaw(), portal.getPortalPose(), pair.getPortalPose());
            this.portalCamera.configure(client.world, focusedEntity, mappedCameraPos, mappedYaw, camera.getPitch(), tickProgress);

            Matrix4f alternatePositionMatrix = new Matrix4f().rotation(this.portalCamera.getRotation().conjugate(new Quaternionf()));
            client.worldRenderer.render(allocator, tickCounter, false, this.portalCamera, alternatePositionMatrix, mainProjection, projectionMatrix, fogBuffer, fogColor, renderSky);
            ScreenshotRecorder.takeScreenshot(framebuffer, 1, image -> updateTexture(entry, image));
        } finally {
            this.capturing = false;
            ((MinecraftClientAccessor) client).arena$setFramebuffer(originalFramebuffer);
        }
    }

    private Vec3d mapCameraPosition(Vec3d cameraPos, PortalPose inPose, PortalPose outPose) {
        Vec3d local = inPose.worldToLocalFull(cameraPos);
        return outPose.localToWorld(local.x, local.y, Math.max(0.0D, local.z));
    }

    private float mapCameraYaw(float yaw, PortalPose inPose, PortalPose outPose) {
        Vec3d look = Vec3d.fromPolar(0.0F, yaw).multiply(1.0D, 0.0D, 1.0D).normalize();
        if (look.lengthSquared() < 1.0E-6D) {
            return yaw;
        }

        double widthComponent = look.dotProduct(PortalMath.vec(inPose.widthAxis()));
        double forwardComponent = look.dotProduct(PortalMath.vec(inPose.normal()).multiply(-1.0D));
        Vec3d transformed = PortalMath.vec(outPose.widthAxis()).multiply(widthComponent)
                .add(PortalMath.vec(outPose.normal()).multiply(forwardComponent));
        if (transformed.lengthSquared() < 1.0E-6D) {
            transformed = PortalMath.vec(outPose.normal());
        }

        return (float) (MathHelper.atan2(-transformed.x, transformed.z) * (180.0F / Math.PI));
    }

    private PortalTextureEntry createTextureEntry(MinecraftClient client, UUID uuid) {
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "portal-" + uuid, 16, 16, false);
        fillImage(texture.getImage(), 0xFF000000);
        texture.upload();
        Identifier textureId = Identifier.of("arena", "dynamic/portal/" + uuid);
        client.getTextureManager().registerTexture(textureId, texture);
        return new PortalTextureEntry(textureId, texture);
    }

    private void ensureSolidTextures(MinecraftClient client) {
        if (client.getTextureManager().getTexture(BLACK_TEXTURE) instanceof NativeImageBackedTexture) {
            return;
        }

        NativeImageBackedTexture black = new NativeImageBackedTexture(() -> "portal-black", 2, 2, false);
        fillImage(black.getImage(), 0xFF000000);
        black.upload();
        client.getTextureManager().registerTexture(BLACK_TEXTURE, black);

        NativeImageBackedTexture white = new NativeImageBackedTexture(() -> "portal-white", 2, 2, false);
        fillImage(white.getImage(), 0xFFFFFFFF);
        white.upload();
        client.getTextureManager().registerTexture(WHITE_TEXTURE, white);
    }

    private void updateTexture(PortalTextureEntry entry, NativeImage image) {
        entry.texture.setImage(image);
        entry.texture.upload();
    }

    private void fillImage(@Nullable NativeImage image, int color) {
        if (image == null) {
            return;
        }

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setColor(x, y, color);
            }
        }
    }

    private record PortalTextureEntry(Identifier textureId, NativeImageBackedTexture texture) {
    }
}
