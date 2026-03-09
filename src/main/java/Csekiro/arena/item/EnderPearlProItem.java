package Csekiro.arena.item;

import net.minecraft.block.Block;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UseRemainderComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class EnderPearlProItem extends Item {
    private static final double MAX_DISTANCE = 200.0D;
    private static final int COOLDOWN_TICKS = 20; // 1 秒

    private static final double EPSILON = 0.001D;
    private static final double CEILING_EXTRA_DROP = 0.15D; // 点击下表面时额外下移，防窒息

    private static final double DOWN_SEARCH_STEP = 0.10D;
    private static final int DOWN_SEARCH_TIMES = 8;

    // 点击侧面但当前位置不合法时，先整体下移一段距离
    private static final double SIDE_INITIAL_DROP = 0.60D;
    private static final double SIDE_DOWN_SEARCH_STEP = 0.10D;
    private static final int SIDE_DOWN_SEARCH_TIMES = 10;

    // 点击上表面但当前位置不合法时，向后退一段距离
    private static final double UP_BACKOFF_DISTANCE = 0.60D;

    public EnderPearlProItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        HitResult hitResult = user.raycast(MAX_DISTANCE, 0.0F, false);

        if (!(hitResult instanceof BlockHitResult blockHit) || hitResult.getType() != HitResult.Type.BLOCK) {
            return ActionResult.FAIL;
        }

        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }

        Vec3d targetPos = findTeleportPos(world, user, blockHit);
        if (targetPos == null) {
            return ActionResult.FAIL;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) user;
        ServerWorld serverWorld = (ServerWorld) world;
        ItemStack usedStack = player.getStackInHand(hand);

        Vec3d originPos = new Vec3d(player.getX(), player.getY(), player.getZ());

        // 原位置：音效 + 粒子
        playTeleportOriginEffects(serverWorld, player, originPos);

        // 先给原物品上冷却，避免后面手中物品被替换后冷却错位
        player.getItemCooldownManager().set(usedStack, COOLDOWN_TICKS);

        // 传送
        player.requestTeleport(targetPos.x, targetPos.y, targetPos.z);
        player.setVelocity(0.0D, 0.0D, 0.0D);

        // 目标位置：音效
        playTeleportDestinationSound(serverWorld, targetPos);

        // 消耗并处理 use_remainder
        ItemStack resultStack = consumeAndHandleUseRemainder(player, hand);

        // 直接写回手中物品，避免依赖返回值携带新栈
        player.setStackInHand(hand, resultStack);

        return ActionResult.SUCCESS_SERVER.noIncrementStat();
    }

    private static Vec3d findTeleportPos(World world, PlayerEntity user, BlockHitResult hit) {
        Direction side = hit.getSide();

        // 点击上表面：传到该方块上表面中心
        if (side == Direction.UP) {
            Vec3d base = computeTopCenterPos(hit);
            if (canTeleportTo(world, user, base)) {
                return base;
            }

            Vec3d backed = computeTopSurfaceBackoffPos(user, hit, base);
            if (canTeleportTo(world, user, backed)) {
                return backed;
            }

            return null;
        }

        // 点击下表面：传到该方块底面中心
        if (side == Direction.DOWN) {
            Vec3d base = computeBottomCenterPos(user, hit);
            if (canTeleportTo(world, user, base)) {
                return base;
            }

            for (int i = 1; i <= DOWN_SEARCH_TIMES; i++) {
                Vec3d lowered = base.add(0.0D, -DOWN_SEARCH_STEP * i, 0.0D);
                if (canTeleportTo(world, user, lowered)) {
                    return lowered;
                }
            }

            return null;
        }

        // 点击侧面
        if (isHorizontal(side)) {
            if (isFenceLikeBlock(world, hit)) {
                // 栅栏类：先完整跑“精确落点逻辑”，失败后再跑“中心逻辑”
                Vec3d preciseResult = trySidePreciseLogic(world, user, hit);
                if (preciseResult != null) {
                    return preciseResult;
                }

                Vec3d centeredResult = trySideCenteredLogic(world, user, hit);
                if (centeredResult != null) {
                    return centeredResult;
                }

                return null;
            } else {
                // 普通方块侧面：直接使用后一格中心逻辑
                Vec3d centeredResult = trySideCenteredLogic(world, user, hit);
                if (centeredResult != null) {
                    return centeredResult;
                }

                return null;
            }
        }

        return null;
    }

    /**
     * 栅栏类点击侧面时，先使用原有“精确侧面落点”逻辑：
     * 1. 精确基础点
     * 2. 整体下移
     * 3. 逐步下移
     */
    private static Vec3d trySidePreciseLogic(World world, PlayerEntity user, BlockHitResult hit) {
        Vec3d base = computeOriginalSidePrecisePos(user, hit);

        if (canTeleportTo(world, user, base)) {
            return base;
        }

        Vec3d droppedBase = base.add(0.0D, -SIDE_INITIAL_DROP, 0.0D);
        if (canTeleportTo(world, user, droppedBase)) {
            return droppedBase;
        }

        for (int i = 1; i <= SIDE_DOWN_SEARCH_TIMES; i++) {
            Vec3d lowered = droppedBase.add(0.0D, -SIDE_DOWN_SEARCH_STEP * i, 0.0D);
            if (canTeleportTo(world, user, lowered)) {
                return lowered;
            }
        }

        return null;
    }

    /**
     * 点击侧面时的“中心逻辑”：
     * 目标为点击面外侧那一格的中心，
     * 然后同样进行下移/逐步下移搜索。
     */
    private static Vec3d trySideCenteredLogic(World world, PlayerEntity user, BlockHitResult hit) {
        Vec3d base = computeSideCenteredBasePos(hit);

        if (canTeleportTo(world, user, base)) {
            return base;
        }

        Vec3d droppedBase = base.add(0.0D, -SIDE_INITIAL_DROP, 0.0D);
        if (canTeleportTo(world, user, droppedBase)) {
            return droppedBase;
        }

        for (int i = 1; i <= SIDE_DOWN_SEARCH_TIMES; i++) {
            Vec3d lowered = droppedBase.add(0.0D, -SIDE_DOWN_SEARCH_STEP * i, 0.0D);
            if (canTeleportTo(world, user, lowered)) {
                return lowered;
            }
        }

        return null;
    }

    /**
     * 点击上表面：该方块上表面中心
     */
    private static Vec3d computeTopCenterPos(BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        return new Vec3d(
                pos.getX() + 0.5D,
                pos.getY() + 1.0D + EPSILON,
                pos.getZ() + 0.5D
        );
    }

    /**
     * 点击下表面：该方块底面中心
     */
    private static Vec3d computeBottomCenterPos(PlayerEntity user, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        return new Vec3d(
                pos.getX() + 0.5D,
                pos.getY() - user.getHeight() - CEILING_EXTRA_DROP,
                pos.getZ() + 0.5D
        );
    }

    /**
     * 栅栏类侧面：保留原有“精确命中点 + 玩家半宽外推”逻辑
     */
    private static Vec3d computeOriginalSidePrecisePos(PlayerEntity user, BlockHitResult hit) {
        Vec3d hitPos = hit.getPos();
        Direction side = hit.getSide();

        double halfWidth = user.getWidth() / 2.0D;
        double outward = halfWidth + EPSILON;

        return new Vec3d(
                hitPos.x + side.getOffsetX() * outward,
                hitPos.y,
                hitPos.z + side.getOffsetZ() * outward
        );
    }

    /**
     * 普通侧面/栅栏类兜底：点击面外侧那一格的中心
     */
    private static Vec3d computeSideCenteredBasePos(BlockHitResult hit) {
        BlockPos targetBlock = hit.getBlockPos().offset(hit.getSide());
        return new Vec3d(
                targetBlock.getX() + 0.5D,
                targetBlock.getY(),
                targetBlock.getZ() + 0.5D
        );
    }

    /**
     * 点击上表面时，若基础落点不合法，则向“玩家这边”回退一段距离。
     */
    private static Vec3d computeTopSurfaceBackoffPos(PlayerEntity user, BlockHitResult hit, Vec3d base) {
        Vec3d hitPos = hit.getPos();

        Vec3d back = new Vec3d(
                user.getX() - hitPos.x,
                0.0D,
                user.getZ() - hitPos.z
        );

        if (back.lengthSquared() < 1.0E-6D) {
            Direction facing = user.getHorizontalFacing();
            back = new Vec3d(
                    -facing.getOffsetX(),
                    0.0D,
                    -facing.getOffsetZ()
            );
        }

        back = back.normalize();

        return base.add(
                back.x * UP_BACKOFF_DISTANCE,
                0.0D,
                back.z * UP_BACKOFF_DISTANCE
        );
    }

    private static boolean isFenceLikeBlock(World world, BlockHitResult hit) {
        Block block = world.getBlockState(hit.getBlockPos()).getBlock();
        return block instanceof FenceBlock || block instanceof WallBlock;
    }

    private static boolean isHorizontal(Direction side) {
        return side == Direction.NORTH
                || side == Direction.SOUTH
                || side == Direction.EAST
                || side == Direction.WEST;
    }

    private static boolean canTeleportTo(World world, PlayerEntity user, Vec3d targetPos) {
        Box movedBox = user.getBoundingBox().offset(
                targetPos.x - user.getX(),
                targetPos.y - user.getY(),
                targetPos.z - user.getZ()
        );
        return world.isSpaceEmpty(user, movedBox);
    }

    private static void playTeleportOriginEffects(ServerWorld world, PlayerEntity player, Vec3d originPos) {
        world.playSound(
                null,
                originPos.x,
                originPos.y + player.getHeight() * 0.5D,
                originPos.z,
                SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                SoundCategory.PLAYERS,
                1.0F,
                1.0F
        );

        world.spawnParticles(
                ParticleTypes.PORTAL,
                false,
                false,
                originPos.x,
                originPos.y + player.getHeight() * 0.5D,
                originPos.z,
                64,
                player.getWidth() * 0.35D,
                player.getHeight() * 0.45D,
                player.getWidth() * 0.35D,
                0.25D
        );
    }

    private static void playTeleportDestinationSound(ServerWorld world, Vec3d targetPos) {
        world.playSound(
                null,
                targetPos.x,
                targetPos.y,
                targetPos.z,
                SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                SoundCategory.PLAYERS,
                1.0F,
                1.0F
        );
    }

    private static ItemStack consumeAndHandleUseRemainder(PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        Item usedItem = stack.getItem();

        UseRemainderComponent useRemainder = stack.get(DataComponentTypes.USE_REMAINDER);
        ItemStack resultStack;

        if (useRemainder != null) {
            resultStack = ItemUsage.exchangeStack(
                    stack,
                    player,
                    useRemainder.convertInto().copy()
            );
        } else {
            stack.decrementUnlessCreative(1, player);
            resultStack = stack;
        }

        player.incrementStat(Stats.USED.getOrCreateStat(usedItem));
        return resultStack;
    }
}