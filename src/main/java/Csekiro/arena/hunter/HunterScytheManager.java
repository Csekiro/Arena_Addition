package Csekiro.arena.hunter;

import Csekiro.arena.item.HunterScytheItem;
import Csekiro.arena.item.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class HunterScytheManager {
    public static final float LAST_STAND_HEALTH = 0.1F;

    private static final Comparator<BlackHeartBatch> RECOVERY_ORDER = Comparator
            .comparingLong(BlackHeartBatch::expireTick)
            .thenComparingLong(BlackHeartBatch::createdTick);
    private static final Comparator<BlackHeartBatch> TRIM_ORDER = Comparator
            .comparingLong(BlackHeartBatch::expireTick)
            .thenComparingLong(BlackHeartBatch::createdTick)
            .reversed();

    private static final Map<UUID, PlayerBlackHeartState> STATES = new HashMap<>();

    private HunterScytheManager() {
    }

    public static void initialize() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity player)) {
                return true;
            }

            PlayerBlackHeartState state = STATES.get(player.getUuid());
            if (!HunterScytheItem.isLastStandEnabled() || state == null || !state.lastStand || state.forcingDeath) {
                return true;
            }

            triggerDeath(player, state, source);
            return false;
        });

        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity player)) {
                return true;
            }

            PlayerBlackHeartState state = STATES.get(player.getUuid());
            if (!HunterScytheItem.isLastStandEnabled() || state == null || state.forcingDeath || !state.hasActiveBlackHearts()) {
                return true;
            }

            state.lastStand = true;
            player.setHealth(LAST_STAND_HEALTH);
            player.hurtTime = 0;
            player.maxHurtTime = 0;
            if (!HunterScytheItem.isHuntingMomentEnabled() && state.pendingLastStandReplacement) {
                replaceBlackHearts(player, state, state.pendingLastStandHealth);
            } else {
                clampBlackHeartsToMaxTotal(player, state);
            }
            state.pendingLastStandReplacement = false;
            state.pendingLastStandHealth = 0.0F;
            player.markHealthDirty();
            syncDisplay(player, state);
            return false;
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            handleKillRecovery(entity, source);

            if (entity instanceof ServerPlayerEntity player) {
                clear(player);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                tickPlayer(player);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(handler.player));
    }

    public static void recordAppliedDamage(ServerPlayerEntity player, float appliedDamage, float absorbedDamage, float preDamageHealth) {
        if (appliedDamage <= 0.0F) {
            return;
        }

        PlayerBlackHeartState state = getOrCreateState(player);
        state.pendingLastStandReplacement = false;
        state.pendingLastStandHealth = 0.0F;
        if (state.forcingDeath || !hasHunterScythe(player)) {
            return;
        }

        boolean hadActiveBlackHearts = state.hasActiveBlackHearts();
        float blackAmount = Math.max(0.0F, appliedDamage - Math.max(0.0F, absorbedDamage));
        if (blackAmount <= 0.0F) {
            return;
        }

        if (!HunterScytheItem.isHuntingMomentEnabled() && hadActiveBlackHearts) {
            if (state.lastStand || player.getHealth() <= LAST_STAND_HEALTH + 0.001F) {
                replaceBlackHearts(player, state, Math.max(0.0F, preDamageHealth));
                syncDisplay(player, state);
                return;
            }

            if (player.getHealth() <= 0.0F) {
                state.pendingLastStandReplacement = true;
                state.pendingLastStandHealth = Math.max(0.0F, preDamageHealth);
                return;
            }

            float recalculatedBlackHearts = Math.max(0.0F, player.getMaxHealth() - Math.max(0.0F, player.getHealth()));
            replaceBlackHearts(player, state, recalculatedBlackHearts);
            syncDisplay(player, state);
            return;
        }

        addBlackHeartBatch(player, state, blackAmount);
        syncDisplay(player, state);
    }

    public static void handleSuccessfulAttack(Entity target, DamageSource source, float attemptedDamage, float blockedDamage) {
        Entity attacker = source.getAttacker();
        if (!(attacker instanceof ServerPlayerEntity player) || target == player) {
            return;
        }

        PlayerBlackHeartState state = STATES.get(player.getUuid());
        if (state == null || !state.hasActiveBlackHearts()) {
            return;
        }

        if (HunterScytheItem.isRecoveryRequiresMainHand() && !player.getMainHandStack().isOf(ModItems.HUNTER_SCYTHE)) {
            return;
        }

        if (!HunterScytheItem.isNonMeleeRecoveryEnabled() && !isDirectMeleeAttack(player, source)) {
            return;
        }

        float recoveryAmount = computeRecoveryAmount(attemptedDamage, blockedDamage);
        if (recoveryAmount <= 0.0F) {
            return;
        }

        float recovered = consumeRecoverableBlackHearts(state, recoveryAmount);
        if (recovered <= 0.0F) {
            return;
        }

        player.heal(recovered);
        /*player.sendMessage(
                Text.literal("Recovered black hearts +" + formatRecoveryAmount(recovered))
                        .formatted(Formatting.DARK_GRAY, Formatting.ITALIC),
                false
        );*/

        state.lastStand = player.getHealth() <= LAST_STAND_HEALTH && state.hasActiveBlackHearts();
        clampBlackHeartsToMaxTotal(player, state);
        player.markHealthDirty();
        syncDisplay(player, state);
    }

    private static void handleKillRecovery(Entity victim, DamageSource source) {
        if (!HunterScytheItem.isFullRecoveryOnKillEnabled()) {
            return;
        }

        Entity attacker = source.getAttacker();
        if (!(attacker instanceof ServerPlayerEntity player) || victim == player) {
            return;
        }

        if (!(victim instanceof ServerPlayerEntity) && !HunterScytheItem.isFullRecoveryOnNonPlayerKillEnabled()) {
            return;
        }

        if (!isHunterScytheKill(player, source)) {
            return;
        }

        PlayerBlackHeartState state = STATES.get(player.getUuid());
        if (state == null || !state.hasActiveBlackHearts()) {
            return;
        }

        float recovered = consumeRecoverableBlackHearts(state, state.getTotalBlackHearts());
        if (recovered <= 0.0F) {
            return;
        }

        player.heal(recovered);
        state.lastStand = false;
        state.batches.clear();
        STATES.remove(player.getUuid());
        player.markHealthDirty();
        syncDisplay(player, new PlayerBlackHeartState());
    }

    public static boolean shouldBlockNaturalRegen(PlayerEntity player) {
        PlayerBlackHeartState state = STATES.get(player.getUuid());
        return state != null && state.hasActiveBlackHearts();
    }

    public static void clear(ServerPlayerEntity player) {
        PlayerBlackHeartState removed = STATES.remove(player.getUuid());
        if (removed == null) {
            syncDisplay(player, new PlayerBlackHeartState());
            return;
        }

        removed.clear();
        syncDisplay(player, removed);
    }

    private static void tickPlayer(ServerPlayerEntity player) {
        PlayerBlackHeartState state = STATES.get(player.getUuid());
        if (state == null) {
            syncDisplay(player, new PlayerBlackHeartState());
            return;
        }

        long now = ((ServerWorld) player.getEntityWorld()).getServer().getTicks();
        Iterator<BlackHeartBatch> iterator = state.batches.iterator();
        while (iterator.hasNext()) {
            BlackHeartBatch batch = iterator.next();
            if (batch.expireTick <= now || batch.amountRemaining <= 0.0F) {
                iterator.remove();
            }
        }

        if (state.lastStand && player.getHealth() > LAST_STAND_HEALTH + 0.001F) {
            state.lastStand = false;
        }

        clampBlackHeartsToMaxTotal(player, state);

        if (state.lastStand && !state.hasActiveBlackHearts()) {
            triggerWitherDeath(player, state);
            return;
        }

        if (!state.hasActiveBlackHearts()) {
            STATES.remove(player.getUuid());
        }

        syncDisplay(player, state);
    }

    private static void triggerWitherDeath(ServerPlayerEntity player, PlayerBlackHeartState state) {
        triggerDeath(player, state, player.getDamageSources().wither());
    }

    private static void triggerDeath(ServerPlayerEntity player, PlayerBlackHeartState state, DamageSource source) {
        if (state.forcingDeath) {
            return;
        }

        state.forcingDeath = true;
        state.lastStand = false;
        state.clear();
        syncDisplay(player, state);

        try {
            player.damage((ServerWorld) player.getEntityWorld(), source, Float.MAX_VALUE);
        } finally {
            state.forcingDeath = false;
        }
    }

    private static float computeRecoveryAmount(float attemptedDamage, float blockedDamage) {
        float effectiveBlockedDamage = Math.max(0.0F, blockedDamage);
        float effectiveDamageAmount = Math.max(0.0F, attemptedDamage - effectiveBlockedDamage);
        float recoveryAmount = HunterScytheItem.getBlackHeartRecoveryBase()
                + effectiveDamageAmount * HunterScytheItem.getBlackHeartRecoveryDamageMultiplier()
                + effectiveBlockedDamage * HunterScytheItem.getBlackHeartRecoveryShieldBlockedDamageMultiplier();
        return MathHelper.clamp(recoveryAmount, 0.0F, HunterScytheItem.getBlackHeartRecoveryCap());
    }

    private static boolean isDirectMeleeAttack(ServerPlayerEntity player, DamageSource source) {
        return source.getSource() == player;
    }

    private static boolean isHunterScytheKill(ServerPlayerEntity player, DamageSource source) {
        return player.getMainHandStack().isOf(ModItems.HUNTER_SCYTHE) && source.getSource() == player;
    }

    private static String formatRecoveryAmount(float amount) {
        if (Math.abs(amount - Math.round(amount)) < 0.001F) {
            return Integer.toString(Math.round(amount));
        }

        return String.format(Locale.ROOT, "%.1f", amount);
    }

    private static float consumeRecoverableBlackHearts(PlayerBlackHeartState state, float amount) {
        float remaining = amount;
        List<BlackHeartBatch> ordered = new ArrayList<>(state.batches);
        ordered.sort(RECOVERY_ORDER);

        for (BlackHeartBatch batch : ordered) {
            if (remaining <= 0.0F) {
                break;
            }

            float recovered = Math.min(batch.amountRemaining, remaining);
            batch.amountRemaining -= recovered;
            remaining -= recovered;
        }

        state.batches.removeIf(batch -> batch.amountRemaining <= 0.0F);
        return amount - remaining;
    }

    private static void clampBlackHeartsToMaxTotal(ServerPlayerEntity player, PlayerBlackHeartState state) {
        float effectiveRedHealth = state.lastStand ? 0.0F : player.getHealth();
        float allowedBlackHearts = Math.max(0.0F, player.getMaxHealth() - effectiveRedHealth);
        float overflow = state.getTotalBlackHearts() - allowedBlackHearts;

        if (overflow <= 0.0F) {
            return;
        }

        List<BlackHeartBatch> ordered = new ArrayList<>(state.batches);
        ordered.sort(TRIM_ORDER);

        for (BlackHeartBatch batch : ordered) {
            if (overflow <= 0.0F) {
                break;
            }

            float trimmed = Math.min(batch.amountRemaining, overflow);
            batch.amountRemaining -= trimmed;
            overflow -= trimmed;
        }

        state.batches.removeIf(batch -> batch.amountRemaining <= 0.0F);
    }

    private static void addBlackHeartBatch(ServerPlayerEntity player, PlayerBlackHeartState state, float blackAmount) {
        long now = ((ServerWorld) player.getEntityWorld()).getServer().getTicks();
        state.batches.add(new BlackHeartBatch(blackAmount, now, now + HunterScytheItem.getBlackHeartDurationTicks()));
        clampBlackHeartsToMaxTotal(player, state);
    }

    private static void replaceBlackHearts(ServerPlayerEntity player, PlayerBlackHeartState state, float blackAmount) {
        state.batches.clear();

        if (blackAmount <= 0.0F) {
            return;
        }

        addBlackHeartBatch(player, state, blackAmount);
    }

    private static void syncDisplay(PlayerEntity player, PlayerBlackHeartState state) {
        if (!(player instanceof HunterScytheTrackedPlayer trackedPlayer)) {
            return;
        }

        trackedPlayer.arena$setBlackHeartDisplay(state.getTotalBlackHearts());
        trackedPlayer.arena$setBlackHeartLastStand(state.lastStand && state.hasActiveBlackHearts());
    }

    private static PlayerBlackHeartState getOrCreateState(PlayerEntity player) {
        return STATES.computeIfAbsent(player.getUuid(), uuid -> new PlayerBlackHeartState());
    }

    private static boolean hasHunterScythe(PlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        DefaultedList<ItemStack> mainStacks = inventory.getMainStacks();

        for (ItemStack stack : mainStacks) {
            if (stack.isOf(ModItems.HUNTER_SCYTHE)) {
                return true;
            }
        }

        return player.getOffHandStack().isOf(ModItems.HUNTER_SCYTHE);
    }

    private static final class PlayerBlackHeartState {
        private final List<BlackHeartBatch> batches = new ArrayList<>();
        private boolean lastStand;
        private boolean forcingDeath;
        private boolean pendingLastStandReplacement;
        private float pendingLastStandHealth;

        private void clear() {
            batches.clear();
            lastStand = false;
            pendingLastStandReplacement = false;
            pendingLastStandHealth = 0.0F;
        }

        private boolean hasActiveBlackHearts() {
            return !batches.isEmpty() && getTotalBlackHearts() > 0.0F;
        }

        private float getTotalBlackHearts() {
            float total = 0.0F;

            for (BlackHeartBatch batch : batches) {
                total += batch.amountRemaining;
            }

            return MathHelper.clamp(total, 0.0F, Float.MAX_VALUE);
        }
    }

    private static final class BlackHeartBatch {
        private float amountRemaining;
        private final long createdTick;
        private final long expireTick;

        private BlackHeartBatch(float amount, long createdTick, long expireTick) {
            this.amountRemaining = amount;
            this.createdTick = createdTick;
            this.expireTick = expireTick;
        }

        private long createdTick() {
            return createdTick;
        }

        private long expireTick() {
            return expireTick;
        }
    }
}
