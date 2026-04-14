package Csekiro.arena.portal;

import Csekiro.arena.entity.PortalEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.Set;
import java.util.Set;
import java.util.UUID;

public final class PortalTeleportService {
    private static final double SEARCH_RADIUS = 3.0D;
    private static final double EXIT_OVERLAP = 0.03D;

    private PortalTeleportService() {
    }

    public static void tickServer(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            tickPlayer(player);
        }
    }

    public static void tickPlayer(ServerPlayerEntity player) {
        if (player.isRemoved() || player.isSpectator() || player.getEntityWorld().getRegistryKey() != World.OVERWORLD) {
            clearLock(player);
            return;
        }

        updateLockState(player);
        PortalEntity entrance = findIntersectingPortal(player);
        if (entrance == null || !canTeleport(player, entrance)) {
            return;
        }

        PortalEntity exit = PortalPairManager.getPair(entrance);
        if (exit == null) {
            return;
        }

        Vec3d destination = transformPosition(player, entrance, exit);
        float yaw = transformYaw(player.getYaw(), entrance, exit);

        player.teleport(player.getEntityWorld(), destination.x, destination.y, destination.z, Set.of(), yaw, player.getPitch(), false);
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);
        player.fallDistance = 0.0F;

        Set<UUID> blocking = PortalPairManager.getTeleportLock(player.getEntityWorld().getServer(), player.getUuid());
        blocking.clear();
        blocking.add(entrance.getUuid());
        blocking.add(exit.getUuid());
    }

    public static void clearLock(ServerPlayerEntity player) {
        PortalPairManager.clearTeleportLock(player.getEntityWorld().getServer(), player.getUuid());
    }

    private static PortalEntity findIntersectingPortal(ServerPlayerEntity player) {
        Box searchBox = player.getBoundingBox().expand(SEARCH_RADIUS);
        return player.getEntityWorld()
                .getEntitiesByClass(PortalEntity.class, searchBox, portal -> portal.getBoundingBox().intersects(player.getBoundingBox()))
                .stream()
                .filter(portal -> portal.getPortalPose().containsProjectedPoint(player.getBoundingBox().getCenter()))
                .min(Comparator.comparingDouble(portal -> portal.squaredDistanceTo(player)))
                .orElse(null);
    }

    private static boolean canTeleport(ServerPlayerEntity player, PortalEntity entrance) {
        PortalEntity exit = PortalPairManager.getPair(entrance);
        if (exit == null) {
            return false;
        }

        Set<UUID> blocking = PortalPairManager.getTeleportLock(player.getEntityWorld().getServer(), player.getUuid());
        if (blocking.contains(entrance.getUuid()) || blocking.contains(exit.getUuid())) {
            return false;
        }

        PortalPose pose = entrance.getPortalPose();
        Vec3d center = player.getBoundingBox().getCenter();
        return pose.signedDistance(center) >= -0.35D && pose.containsProjectedPoint(center);
    }

    private static Vec3d transformPosition(ServerPlayerEntity player, PortalEntity in, PortalEntity out) {
        PortalPose inPose = in.getPortalPose();
        PortalPose outPose = out.getPortalPose();
        Vec3d center = player.getBoundingBox().getCenter();
        Vec3d offsetFromFeet = center.subtract(player.getEntityPos());
        Vec3d local = inPose.worldToLocalFull(center);
        double clampedU = MathHelper.clamp(local.x, -PortalPose.HALF_WIDTH + 0.15D, PortalPose.HALF_WIDTH - 0.15D);
        double clampedV = MathHelper.clamp(local.y, -PortalPose.HALF_HEIGHT + 0.35D, PortalPose.HALF_HEIGHT - 0.35D);
        double localN = Math.max(0.0D, local.z);
        Vec3d mappedCenter = outPose.localToWorld(clampedU, clampedV, localN + EXIT_OVERLAP);
        return mappedCenter.subtract(offsetFromFeet);
    }

    private static float transformYaw(float yaw, PortalEntity in, PortalEntity out) {
        Vec3d look = Vec3d.fromPolar(0.0F, yaw).multiply(1.0D, 0.0D, 1.0D).normalize();
        if (look.lengthSquared() < 1.0E-6D) {
            return yaw;
        }

        PortalPose inPose = in.getPortalPose();
        PortalPose outPose = out.getPortalPose();
        double widthComponent = look.dotProduct(PortalMath.vec(inPose.widthAxis()));
        double forwardComponent = look.dotProduct(PortalMath.vec(inPose.normal()).multiply(-1.0D));
        Vec3d transformed = PortalMath.vec(outPose.widthAxis()).multiply(widthComponent)
                .add(PortalMath.vec(outPose.normal()).multiply(forwardComponent));
        if (transformed.lengthSquared() < 1.0E-6D) {
            transformed = PortalMath.vec(outPose.normal());
        }

        return (float) (MathHelper.atan2(-transformed.x, transformed.z) * (180.0F / Math.PI));
    }

    private static void updateLockState(ServerPlayerEntity player) {
        Set<UUID> blocking = PortalPairManager.getTeleportLock(player.getEntityWorld().getServer(), player.getUuid());
        if (blocking.isEmpty()) {
            return;
        }

        ServerWorld world = player.getEntityWorld();
        for (UUID portalUuid : blocking) {
            if (world.getEntity(portalUuid) instanceof PortalEntity portal && portal.getBoundingBox().intersects(player.getBoundingBox())) {
                return;
            }
        }

        blocking.clear();
    }
}
