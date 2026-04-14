package Csekiro.arena.portal;

import Csekiro.arena.entity.PortalEntity;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class PortalPlacementRules {
    private PortalPlacementRules() {
    }

    public static PortalPose buildPose(ServerPlayerEntity player, BlockPos pos, Direction face, Vec3d hitPos, boolean allowFloorAndCeiling) {
        if (face.getAxis().isHorizontal()) {
            return buildWallPose(player, pos, face, hitPos);
        }

        if (!allowFloorAndCeiling) {
            return null;
        }

        return buildFloorOrCeilingPose(player, pos, face);
    }

    public static PortalPose buildWallPose(ServerPlayerEntity player, BlockPos pos, Direction face, Vec3d hitPos) {
        ServerWorld world = player.getEntityWorld();
        Direction heightAxis = Direction.UP;
        Direction widthAxis = chooseWallWidthAxis(player, face);
        BlockPos primaryBase = hitPos.y - pos.getY() > 0.5D ? pos.down() : pos;
        BlockPos fallbackBase = primaryBase.equals(pos) ? pos.down() : pos;

        PortalPose primaryPose = createPose(primaryBase, face, heightAxis, widthAxis);
        if (hasSupport(world, primaryPose)) {
            return primaryPose;
        }

        PortalPose fallbackPose = createPose(fallbackBase, face, heightAxis, widthAxis);
        return hasSupport(world, fallbackPose) ? fallbackPose : null;
    }

    public static PortalPose buildFloorOrCeilingPose(ServerPlayerEntity player, BlockPos pos, Direction face) {
        Direction heightAxis = player.getHorizontalFacing();
        Direction widthAxis = PortalMath.cross(face, heightAxis);
        PortalPose pose = createPose(pos, face, heightAxis, widthAxis);
        return hasSupport(player.getEntityWorld(), pose) ? pose : null;
    }

    public static boolean validatePlacement(ServerWorld world, PortalPose pose) {
        if (pose == null) {
            return false;
        }

        if (!hasSupport(world, pose)) {
            return false;
        }

        if (!world.isSpaceEmpty(pose.exitClearanceBox())) {
            return false;
        }

        return world.getOtherEntities(null, pose.portalPlaneBox(), entity -> entity instanceof PortalEntity).isEmpty();
    }

    public static boolean hasSupport(ServerWorld world, PortalPose pose) {
        return isSupportBlock(world, pose.hostBasePos()) && isSupportBlock(world, pose.upperSupportPos());
    }

    public static boolean isHostStillValid(ServerWorld world, PortalPose pose) {
        return hasSupport(world, pose);
    }

    private static PortalPose createPose(BlockPos basePos, Direction normal, Direction heightAxis, Direction widthAxis) {
        Vec3d center = basePos.toCenterPos()
                .add(PortalMath.vec(heightAxis).multiply(0.5D))
                .add(PortalMath.vec(normal).multiply(PortalPose.SURFACE_EPSILON));
        return new PortalPose(center, normal, heightAxis, widthAxis, basePos.toImmutable(), normal);
    }

    private static Direction chooseWallWidthAxis(ServerPlayerEntity player, Direction face) {
        Direction horizontal = player.getHorizontalFacing();
        if (face.getAxis() == Direction.Axis.Z) {
            return horizontal.getAxis() == Direction.Axis.X ? horizontal : Direction.EAST;
        }

        return horizontal.getAxis() == Direction.Axis.Z ? horizontal : Direction.SOUTH;
    }

    private static boolean isSupportBlock(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return !state.isAir() && !state.getCollisionShape(world, pos).isEmpty();
    }
}
