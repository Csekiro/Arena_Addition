package Csekiro.arena.portal;

import Csekiro.arena.entity.ModEntityTypes;
import Csekiro.arena.entity.PortalColor;
import Csekiro.arena.entity.PortalEntity;
import Csekiro.arena.item.PortalGunItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

public final class PortalPlacementService {
    private PortalPlacementService() {
    }

    public static boolean tryPlaceBlue(ServerPlayerEntity player, PortalGunItem item, BlockPos pos, Direction face, Vec3d hitPos) {
        return tryPlace(player, item, PortalColor.BLUE, pos, face, hitPos);
    }

    public static boolean tryPlaceOrange(ServerPlayerEntity player, PortalGunItem item, BlockPos pos, Direction face, Vec3d hitPos) {
        return tryPlace(player, item, PortalColor.ORANGE, pos, face, hitPos);
    }

    public static void revalidateOwnedPortals(ServerPlayerEntity player) {
        ServerWorld serverWorld = player.getEntityWorld();
        revalidatePortal(PortalPairManager.getPortal(serverWorld, player.getUuid(), PortalColor.BLUE));
        revalidatePortal(PortalPairManager.getPortal(serverWorld, player.getUuid(), PortalColor.ORANGE));
    }

    public static void removePortal(PortalEntity portal) {
        if (!(portal.getEntityWorld() instanceof ServerWorld serverWorld) || portal.getOwnerUuid() == null) {
            portal.discard();
            return;
        }

        PortalPairManager.clearPortal(serverWorld.getServer(), portal.getOwnerUuid(), portal.getColor());
        portal.discard();
    }

    private static boolean tryPlace(ServerPlayerEntity player, PortalGunItem item, PortalColor color, BlockPos pos, Direction face, Vec3d hitPos) {
        ServerWorld serverWorld = player.getEntityWorld();
        if (serverWorld.getRegistryKey() != World.OVERWORLD) {
            return false;
        }

        PortalPose pose = PortalPlacementRules.buildPose(player, pos, face, hitPos, item.allowFloorAndCeiling());
        if (!PortalPlacementRules.validatePlacement(serverWorld, pose)) {
            return false;
        }

        UUID ownerUuid = player.getUuid();
        PortalEntity oldPortal = PortalPairManager.getPortal(serverWorld, ownerUuid, color);
        PortalEntity portal = new PortalEntity(ModEntityTypes.PORTAL, serverWorld);
        portal.initialize(ownerUuid, color, pose, true, item.allowFloorAndCeiling(), item.destroyWhenHostInvalid());
        if (!serverWorld.spawnEntity(portal)) {
            return false;
        }

        PortalPairManager.setPortal(serverWorld.getServer(), ownerUuid, color, portal);
        if (oldPortal != null && oldPortal != portal) {
            oldPortal.discard();
        }

        return true;
    }

    private static void revalidatePortal(PortalEntity portal) {
        if (portal == null || !(portal.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        if (portal.shouldDestroyWhenHostInvalid() && !PortalPlacementRules.isHostStillValid(serverWorld, portal.getPortalPose())) {
            removePortal(portal);
        }
    }
}
