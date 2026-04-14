package Csekiro.arena.mixin;

import Csekiro.arena.hunter.HunterScytheManager;
import Csekiro.arena.hunter.HunterScytheTrackedPlayer;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin implements HunterScytheTrackedPlayer {
    @Unique
    private static final TrackedData<Float> ARENA_BLACK_HEART_DISPLAY = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.FLOAT);
    @Unique
    private static final TrackedData<Boolean> ARENA_BLACK_HEART_LAST_STAND = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    @Unique
    private float arena$preApplyAbsorption;
    @Unique
    private float arena$preApplyHealth;

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void arena$initHunterScytheTrackedData(DataTracker.Builder builder, CallbackInfo ci) {
        builder.add(ARENA_BLACK_HEART_DISPLAY, 0.0F);
        builder.add(ARENA_BLACK_HEART_LAST_STAND, false);
    }

    @Inject(method = "applyDamage", at = @At("HEAD"))
    private void arena$capturePreApplyState(ServerWorld world, net.minecraft.entity.damage.DamageSource source, float amount, CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        arena$preApplyAbsorption = player.getAbsorptionAmount();
        arena$preApplyHealth = player.getHealth();

        if ((Object) this instanceof ServerPlayerEntity serverPlayer) {
            HunterScytheManager.prepareAppliedDamage(serverPlayer, source, amount, arena$preApplyAbsorption, arena$preApplyHealth);
        }
    }

    @Inject(method = "applyDamage", at = @At("TAIL"))
    private void arena$recordBlackHearts(ServerWorld world, net.minecraft.entity.damage.DamageSource source, float amount, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayerEntity player)) {
            return;
        }

        float absorbedDamage = Math.max(0.0F, arena$preApplyAbsorption - player.getAbsorptionAmount());
        HunterScytheManager.recordAppliedDamage(player, source, amount, absorbedDamage, arena$preApplyHealth);
    }

    @Inject(method = "canFoodHeal", at = @At("HEAD"), cancellable = true)
    private void arena$blockNaturalRegen(CallbackInfoReturnable<Boolean> cir) {
        if (HunterScytheManager.shouldBlockNaturalRegen((PlayerEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Override
    public float arena$getBlackHeartDisplay() {
        return ((PlayerEntity) (Object) this).getDataTracker().get(ARENA_BLACK_HEART_DISPLAY);
    }

    @Override
    public void arena$setBlackHeartDisplay(float amount) {
        ((PlayerEntity) (Object) this).getDataTracker().set(ARENA_BLACK_HEART_DISPLAY, amount);
    }

    @Override
    public boolean arena$isBlackHeartLastStand() {
        return ((PlayerEntity) (Object) this).getDataTracker().get(ARENA_BLACK_HEART_LAST_STAND);
    }

    @Override
    public void arena$setBlackHeartLastStand(boolean lastStand) {
        ((PlayerEntity) (Object) this).getDataTracker().set(ARENA_BLACK_HEART_LAST_STAND, lastStand);
    }
}
