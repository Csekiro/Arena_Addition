package Csekiro.arena.item;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.block.Block;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.UseRemainderComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
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
import xyz.nucleoid.packettweaker.PacketContext;

public class EnderPearlProItem extends Item implements PolymerItem {
    private static final double MAX_DISTANCE = 200.0D;
    private static final int COOLDOWN_TICKS = 20;

    private static final double EPSILON = 0.001D;
    private static final double CEILING_EXTRA_DROP = 0.15D;

    private static final double DOWN_SEARCH_STEP = 0.10D;
    private static final int DOWN_SEARCH_TIMES = 8;

    private static final double SIDE_INITIAL_DROP = 0.60D;
    private static final double SIDE_DOWN_SEARCH_STEP = 0.10D;
    private static final int SIDE_DOWN_SEARCH_TIMES = 10;

    private static final double UP_BACKOFF_DISTANCE = 0.60D;

    public EnderPearlProItem(Settings settings) {
        super(settings);
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
        return Items.BONE;
    }

    @Override
    public void modifyBasePolymerItemStack(ItemStack out, ItemStack stack, PacketContext context) {
        NbtCompound customData = new NbtCompound();
        customData.putBoolean("advanced_tp", true);
        out.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(customData));
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        // 客户端先接受这次交互，别在客户端先把它判死
        if (world.isClient()) {
            return ActionResult.CONSUME;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) user;
        ServerWorld serverWorld = (ServerWorld) world;

        HitResult hitResult = user.raycast(MAX_DISTANCE, 0.0F, false);

        // 没打到方块：不执行功能，但立刻强制同步手上的槽位，消掉幽灵物品
        if (!(hitResult instanceof BlockHitResult blockHit) || hitResult.getType() != HitResult.Type.BLOCK) {
            forceInventorySync(player);
            return ActionResult.FAIL;
        }

        Vec3d targetPos = findTeleportPos(world, user, blockHit);

        // 没找到合法落点：同样强制同步
        if (targetPos == null) {
            forceInventorySync(player);
            return ActionResult.FAIL;
        }

        ItemStack usedStack = player.getStackInHand(hand);
        Vec3d originPos = new Vec3d(player.getX(), player.getY(), player.getZ());

        // 原位置：音效 + 粒子
        playTeleportOriginEffects(serverWorld, player, originPos);

        // 冷却
        player.getItemCooldownManager().set(usedStack, COOLDOWN_TICKS);

        // 传送
        player.requestTeleport(targetPos.x, targetPos.y, targetPos.z);
        player.setVelocity(0.0D, 0.0D, 0.0D);

        // 目标位置：音效
        playTeleportDestinationSound(serverWorld, targetPos);

        // 消耗并处理 use_remainder
        ItemStack resultStack = consumeAndHandleUseRemainder(player, hand);
        player.setStackInHand(hand, resultStack);

        // 成功后也主动同步一次，省得 Polymer 映射栈和真实服务端栈短暂错位
        forceInventorySync(player);

        return ActionResult.SUCCESS_SERVER.noIncrementStat();
    }

    private static void forceInventorySync(ServerPlayerEntity player) {
        // playerScreenHandler 是玩家自身背包/热栏的处理器
        player.playerScreenHandler.syncState();
    }

    private static Vec3d findTeleportPos(World world, PlayerEntity user, BlockHitResult hit) {
        Direction side = hit.getSide();

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

        if (isHorizontal(side)) {
            if (isFenceLikeBlock(world, hit)) {
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
                Vec3d centeredResult = trySideCenteredLogic(world, user, hit);
                if (centeredResult != null) {
                    return centeredResult;
                }

                return null;
            }
        }

        return null;
    }

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

    private static Vec3d computeTopCenterPos(BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        return new Vec3d(pos.getX() + 0.5D, pos.getY() + 1.0D + EPSILON, pos.getZ() + 0.5D);
    }

    private static Vec3d computeBottomCenterPos(PlayerEntity user, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        return new Vec3d(pos.getX() + 0.5D, pos.getY() - user.getHeight() - CEILING_EXTRA_DROP, pos.getZ() + 0.5D);
    }

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

    private static Vec3d computeSideCenteredBasePos(BlockHitResult hit) {
        BlockPos targetBlock = hit.getBlockPos().offset(hit.getSide());
        return new Vec3d(targetBlock.getX() + 0.5D, targetBlock.getY(), targetBlock.getZ() + 0.5D);
    }

    private static Vec3d computeTopSurfaceBackoffPos(PlayerEntity user, BlockHitResult hit, Vec3d base) {
        Vec3d hitPos = hit.getPos();

        Vec3d back = new Vec3d(user.getX() - hitPos.x, 0.0D, user.getZ() - hitPos.z);

        if (back.lengthSquared() < 1.0E-6D) {
            Direction facing = user.getHorizontalFacing();
            back = new Vec3d(-facing.getOffsetX(), 0.0D, -facing.getOffsetZ());
        }

        back = back.normalize();
        return base.add(back.x * UP_BACKOFF_DISTANCE, 0.0D, back.z * UP_BACKOFF_DISTANCE);
    }

    private static boolean isFenceLikeBlock(World world, BlockHitResult hit) {
        Block block = world.getBlockState(hit.getBlockPos()).getBlock();
        return block instanceof FenceBlock || block instanceof WallBlock;
    }

    private static boolean isHorizontal(Direction side) {
        return side == Direction.NORTH || side == Direction.SOUTH || side == Direction.EAST || side == Direction.WEST;
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