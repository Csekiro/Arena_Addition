package Csekiro.arena.client.render;

import Csekiro.arena.entity.PortalEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class PortalMaskComposer {
    private PortalMaskComposer() {
    }

    public static Set<UUID> collectMaskedPortals(ClientWorld world, Frustum frustum, Vec3d cameraPos) {
        return world.getEntitiesByClass(
                        PortalEntity.class,
                        world.getWorldBorder().asVoxelShape().getBoundingBox(),
                        portal -> cameraPos.squaredDistanceTo(portal.getEntityPos()) <= PortalRenderConfig.maxCaptureDistance() * PortalRenderConfig.maxCaptureDistance()
                                && frustum.isVisible(portal.getBoundingBox().expand(0.5D))
                )
                .stream()
                .map(PortalEntity::getUuid)
                .collect(Collectors.toSet());
    }
}
