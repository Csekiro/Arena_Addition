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
        PortalPose pose = createPose(pos, face, heightAxis, widthAxis);
        return canPlaceWall(world, pos) ? pose : null;
    }

    public static PortalPose buildFloorOrCeilingPose(ServerPlayerEntity player, BlockPos pos, Direction face) {
        Direction heightAxis = player.getHorizontalFacing();
        Direction widthAxis = PortalMath.cross(face, heightAxis);
        PortalPose pose = createPose(pos, face, heightAxis, widthAxis);
        return canPlaceFloorOrCeiling(player.getEntityWorld(), pos, heightAxis) ? pose : null;
    }

    public static boolean validatePlacement(ServerWorld world, PortalPose pose) {
        if (pose == null) {
            return false;
        }

        if (!hasSupport(world, pose)) {
            return false;
        }

        return world.getOtherEntities(null, pose.portalPlaneBox(), entity -> entity instanceof PortalEntity).isEmpty();
    }

    public static boolean hasSupport(ServerWorld world, PortalPose pose) {
        if (!isSupportBlock(world, pose.hostBasePos())) {
            return false;
        }

        return switch (pose.normal()) {
            case UP, DOWN -> isNoCollisionBlock(world, pose.hostBasePos().offset(pose.heightAxis()));
            default -> isNoCollisionBlock(world, pose.hostBasePos().up());
        };
    }

    public static boolean isHostStillValid(ServerWorld world, PortalPose pose) {
        return hasSupport(world, pose);
    }

    private static PortalPose createPose(BlockPos basePos, Direction normal, Direction heightAxis, Direction widthAxis) {
        Vec3d center = computeCenter(basePos, normal, heightAxis);
        return new PortalPose(center, normal, heightAxis, widthAxis, basePos.toImmutable(), normal);
    }

    public static Vec3d computeCenter(BlockPos basePos, Direction normal, Direction heightAxis) {
        return basePos.toCenterPos()
                .add(PortalMath.vec(heightAxis).multiply(0.5D))
                .add(PortalMath.vec(normal).multiply(0.5D + PortalPose.SURFACE_EPSILON));
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

    private static boolean isNoCollisionBlock(ServerWorld world, BlockPos pos) {
        return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }

    private static boolean canPlaceWall(ServerWorld world, BlockPos pos) {
        return isSupportBlock(world, pos) && isNoCollisionBlock(world, pos.up());
    }

    private static boolean canPlaceFloorOrCeiling(ServerWorld world, BlockPos pos, Direction forward) {
        return isSupportBlock(world, pos) && isNoCollisionBlock(world, pos.offset(forward));
    }
}
