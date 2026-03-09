package Csekiro.arena.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UseRemainderComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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

        // 先给原物品上冷却，避免后面 use_remainder 把手里的物品换成别的
        player.getItemCooldownManager().set(usedStack, COOLDOWN_TICKS);

        // 传送
        player.requestTeleport(targetPos.x, targetPos.y, targetPos.z);
        player.setVelocity(0.0D, 0.0D, 0.0D);

        // 目标位置：音效
        playTeleportDestinationSound(serverWorld, targetPos);

        // 原版消耗逻辑：支持 use_remainder，并增加 used 统计
        consumeWithVanillaUseRemainder(player, hand);

        return ActionResult.SUCCESS;
    }

    private static Vec3d findTeleportPos(World world, PlayerEntity user, BlockHitResult hit) {
        Vec3d base = computePreciseTeleportPos(user, hit);

        if (canTeleportTo(world, user, base)) {
            return base;
        }

        // 点击方块下表面时，继续往下试几次，避免窒息
        if (hit.getSide() == Direction.DOWN) {
            for (int i = 1; i <= DOWN_SEARCH_TIMES; i++) {
                Vec3d lowered = base.add(0.0D, -DOWN_SEARCH_STEP * i, 0.0D);
                if (canTeleportTo(world, user, lowered)) {
                    return lowered;
                }
            }
        }

        return null;
    }

    private static Vec3d computePreciseTeleportPos(PlayerEntity user, BlockHitResult hit) {
        Vec3d hitPos = hit.getPos(); // 十字准星实际命中的具体坐标
        Direction side = hit.getSide();

        double playerHeight = user.getHeight();
        double halfWidth = user.getWidth() / 2.0D;
        double outward = halfWidth + EPSILON;

        return switch (side) {
            case UP -> new Vec3d(
                    hitPos.x,
                    hitPos.y + EPSILON,
                    hitPos.z
            );

            case DOWN -> new Vec3d(
                    hitPos.x,
                    hitPos.y - playerHeight - CEILING_EXTRA_DROP,
                    hitPos.z
            );

            case NORTH, SOUTH, EAST, WEST -> new Vec3d(
                    hitPos.x + side.getOffsetX() * outward,
                    hitPos.y,
                    hitPos.z + side.getOffsetZ() * outward
            );
        };
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

    private static ItemStack consumeWithVanillaUseRemainder(PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        Item usedItem = stack.getItem();
        int oldCount = stack.getCount();

        UseRemainderComponent useRemainder = stack.get(DataComponentTypes.USE_REMAINDER);
        ItemStack resultStack;

        if (useRemainder != null) {
            resultStack = useRemainder.convert(
                    stack,
                    oldCount,
                    player.isCreative(),
                    remainderStack -> insertOrDrop(player, remainderStack)
            );
        } else {
            stack.decrementUnlessCreative(1, player);
            resultStack = stack;
        }

        player.setStackInHand(hand, resultStack);
        player.incrementStat(Stats.USED.getOrCreateStat(usedItem));

        return resultStack;
    }

    private static void insertOrDrop(PlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
    }
}