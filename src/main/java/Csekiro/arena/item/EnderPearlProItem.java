package Csekiro.arena.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class EnderPearlProItem extends Item {
    private static final double MAX_DISTANCE = 200.0D;
    private static final int COOLDOWN_TICKS = 20; // 1 秒

    public EnderPearlProItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        HitResult hitResult = user.raycast(MAX_DISTANCE, 0.0F, false);

        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return ActionResult.FAIL;
        }

        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }

        BlockHitResult blockHitResult = (BlockHitResult) hitResult;
        Vec3d targetPos = findSafeTeleportPos(world, user, blockHitResult);

        if (targetPos == null) {
            return ActionResult.FAIL;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) user;
        player.requestTeleport(targetPos.x, targetPos.y, targetPos.z);
        player.getItemCooldownManager().set(player.getStackInHand(hand), COOLDOWN_TICKS);

        return ActionResult.SUCCESS;
    }

    private static Vec3d findSafeTeleportPos(World world, PlayerEntity user, BlockHitResult hit) {
        BlockPos[] candidates = new BlockPos[] {
                hit.getBlockPos().offset(hit.getSide()),      // 命中面的相邻方块
                hit.getBlockPos().up(),                       // 命中方块上方
                hit.getBlockPos().offset(hit.getSide()).up()  // 相邻方块上方
        };

        for (BlockPos pos : candidates) {
            Vec3d candidate = new Vec3d(
                    pos.getX() + 0.5D,
                    pos.getY(),
                    pos.getZ() + 0.5D
            );

            if (canTeleportTo(world, user, candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private static boolean canTeleportTo(World world, PlayerEntity user, Vec3d targetPos) {
        Vec3d delta = targetPos.subtract(user.getX(), user.getY(), user.getZ());
        Box movedBox = user.getBoundingBox().offset(delta.x, delta.y, delta.z);
        return world.isSpaceEmpty(user, movedBox);
    }
}