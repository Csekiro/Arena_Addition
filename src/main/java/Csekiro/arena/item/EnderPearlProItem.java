package Csekiro.arena.item;

import net.minecraft.block.Block;
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
        boolean keepOriginalWallSideLogic = shouldKeepOriginalWallSideLogic(world, hit);

        Vec3d base = computeBaseTeleportPos(world, user, hit);

        // 原始基准点能传就直接传
        if (canTeleportTo(world, user, base)) {
            return base;
        }

        // 点击上表面：若不合法，则向“玩家这边”回退一段距离
        if (side == Direction.UP) {
            Vec3d backed = computeTopSurfaceBackoffPos(user, hit, base);
            if (canTeleportTo(world, user, backed)) {
                return backed;
            }
        }

        // 点击侧面
        if (isHorizontal(side)) {
            Vec3d droppedBase = base.add(0.0D, -SIDE_INITIAL_DROP, 0.0D);

            if (canTeleportTo(world, user, droppedBase)) {
                return droppedBase;
            }

            // 围墙：保留原有点击侧面逻辑，继续尝试“吸附到后一格中心”
            if (keepOriginalWallSideLogic) {
                Vec3d droppedCentered = computeSideCenteredPos(hit, droppedBase);
                if (canTeleportTo(world, user, droppedCentered)) {
                    return droppedCentered;
                }
            }

            // 继续逐步向下试探
            for (int i = 1; i <= SIDE_DOWN_SEARCH_TIMES; i++) {
                Vec3d lowered = droppedBase.add(0.0D, -SIDE_DOWN_SEARCH_STEP * i, 0.0D);

                if (canTeleportTo(world, user, lowered)) {
                    return lowered;
                }

                // 围墙保持原有逻辑：每次下移后再尝试吸到后一格中心
                if (keepOriginalWallSideLogic) {
                    Vec3d loweredCentered = computeSideCenteredPos(hit, lowered);
                    if (canTeleportTo(world, user, loweredCentered)) {
                        return loweredCentered;
                    }
                }
            }
        }

        // 点击下表面时，继续往下试几次，避免窒息
        if (side == Direction.DOWN) {
            for (int i = 1; i <= DOWN_SEARCH_TIMES; i++) {
                Vec3d lowered = base.add(0.0D, -DOWN_SEARCH_STEP * i, 0.0D);
                if (canTeleportTo(world, user, lowered)) {
                    return lowered;
                }
            }
        }

        return null;
    }

    /**
     * 新的目标点选取逻辑：
     * 1. 点击上表面：该方块上表面中心
     * 2. 点击下表面：该方块底面中心
     * 3. 点击普通方块侧面：点击面外侧那一格的中心位置
     * 4. 点击围墙侧面：保持原有精确点击侧面逻辑
     */
    private static Vec3d computeBaseTeleportPos(World world, PlayerEntity user, BlockHitResult hit) {
        Direction side = hit.getSide();
        BlockPos clickedPos = hit.getBlockPos();

        if (side == Direction.UP) {
            return new Vec3d(
                    clickedPos.getX() + 0.5D,
                    clickedPos.getY() + 1.0D + EPSILON,
                    clickedPos.getZ() + 0.5D
            );
        }

        if (side == Direction.DOWN) {
            return new Vec3d(
                    clickedPos.getX() + 0.5D,
                    clickedPos.getY() - user.getHeight() - CEILING_EXTRA_DROP,
                    clickedPos.getZ() + 0.5D
            );
        }

        // 围墙侧面：保留原有逻辑
        if (shouldKeepOriginalWallSideLogic(world, hit)) {
            return computeOriginalSidePrecisePos(user, hit);
        }

        // 普通方块侧面：移至点击面外侧那一格的中心
        BlockPos targetBlock = clickedPos.offset(side);
        return new Vec3d(
                targetBlock.getX() + 0.5D,
                targetBlock.getY(),
                targetBlock.getZ() + 0.5D
        );
    }

    /**
     * 围墙侧面保持原有逻辑：按准星实际命中点，加上玩家半宽向外偏移。
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

    /**
     * 围墙侧面时，若当前候选点不合法，则尝试将 x/z 吸附到点击面外侧那一格的中心。
     * y 保持当前候选高度。
     */
    private static Vec3d computeSideCenteredPos(BlockHitResult hit, Vec3d base) {
        BlockPos centerBlock = hit.getBlockPos().offset(hit.getSide());

        return new Vec3d(
                centerBlock.getX() + 0.5D,
                base.y,
                centerBlock.getZ() + 0.5D
        );
    }

    private static boolean shouldKeepOriginalWallSideLogic(World world, BlockHitResult hit) {
        if (!isHorizontal(hit.getSide())) {
            return false;
        }

        Block block = world.getBlockState(hit.getBlockPos()).getBlock();
        return block instanceof WallBlock;
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