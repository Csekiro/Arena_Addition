package Csekiro.arena.client.render;

import Csekiro.arena.entity.PortalEntity;
import Csekiro.arena.portal.PortalMath;
import Csekiro.arena.portal.PortalPose;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
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
    private final Map<UUID, Identifier> frameTextureCache = new HashMap<>();
    private final PortalCamera portalCamera = new PortalCamera();
    private final ExitSceneRenderer exitSceneRenderer = new ExitSceneRenderer();
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

    @Nullable
    public Identifier getFrameTexture(PortalEntity portal) {
        return this.frameTextureCache.get(portal.getUuid());
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
        beginFrame();
        Frustum frustum = new Frustum(new Matrix4f(positionMatrix), new Matrix4f(projectionMatrix));
        Vec3d cameraPos = camera.getPos();
        frustum.setPosition(cameraPos.x, cameraPos.y, cameraPos.z);
        List<PortalEntity> visiblePortals = collectVisiblePortals(client.world, cameraPos, frustum);
        if (visiblePortals.isEmpty()) {
            return;
        }

        int targetWidth = Math.max(1, Math.round(client.getWindow().getFramebufferWidth() * PortalRenderConfig.framebufferScale()));
        int targetHeight = Math.max(1, Math.round(client.getWindow().getFramebufferHeight() * PortalRenderConfig.framebufferScale()));
        this.capturing = true;
        try {
            for (PortalEntity portal : visiblePortals) {
                PortalEntity pair = findClientPair(portal);
                if (pair == null) {
                    continue;
                }

                PortalFrameBufferPool.PooledFramebuffer target = this.frameBufferPool.acquire(client, targetWidth, targetHeight);
                if (target == null) {
                    break;
                }

                Vec3d mappedCameraPos = mapCameraPosition(cameraPos, portal.getPortalPose(), pair.getPortalPose());
                float mappedYaw = mapCameraYaw(camera.getYaw(), portal.getPortalPose(), pair.getPortalPose());
                if (this.exitSceneRenderer.render(
                        client,
                        allocator,
                        tickCounter,
                        this.portalCamera,
                        target,
                        portal,
                        mappedCameraPos,
                        mappedYaw,
                        camera.getPitch(),
                        worldProjectionMatrix,
                        projectionMatrix,
                        fogBuffer,
                        fogColor,
                        renderSky
                )) {
                    this.frameTextureCache.put(portal.getUuid(), target.textureId());
                }
            }
        } finally {
            this.capturing = false;
        }
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

    private void beginFrame() {
        this.frameTextureCache.clear();
        this.frameBufferPool.beginFrame();
        PortalRenderState.reset();
    }

    private List<PortalEntity> collectVisiblePortals(ClientWorld world, Vec3d cameraPos, Frustum frustum) {
        List<PortalEntity> portals = new ArrayList<>(
                world.getEntitiesByClass(
                        PortalEntity.class,
                        world.getWorldBorder().asVoxelShape().getBoundingBox(),
                        portal -> cameraPos.squaredDistanceTo(portal.getEntityPos()) <= PortalRenderConfig.maxCaptureDistance() * PortalRenderConfig.maxCaptureDistance()
                                && hasClientPair(portal)
                )
        );
        portals.sort(Comparator.comparingDouble(portal -> {
            double distance = cameraPos.squaredDistanceTo(portal.getEntityPos());
            return frustum.isVisible(portal.getBoundingBox().expand(0.5D)) ? distance : distance + 4096.0D;
        }));
        return portals.size() > PortalRenderConfig.maxVisiblePortals() ? portals.subList(0, PortalRenderConfig.maxVisiblePortals()) : portals;
    }

    private Vec3d mapCameraPosition(Vec3d cameraPos, PortalPose inPose, PortalPose outPose) {
        Vec3d local = inPose.worldToLocalFull(cameraPos);
        return outPose.localToWorld(local.x, local.y, local.z);
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

}
