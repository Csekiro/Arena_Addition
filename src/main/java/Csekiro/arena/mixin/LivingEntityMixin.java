package Csekiro.arena.mixin;

import Csekiro.arena.hunter.HunterScytheManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Unique
    private float arena$blockedDamageForHunterScytheRecovery;

    @Inject(method = "damage", at = @At("HEAD"))
    private void arena$resetBlockedDamageForHunterScytheRecovery(
            ServerWorld world,
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> cir
    ) {
        this.arena$blockedDamageForHunterScytheRecovery = 0.0F;
    }

    @Inject(method = "getDamageBlockedAmount", at = @At("RETURN"))
    private void arena$captureBlockedDamageForHunterScytheRecovery(
            ServerWorld world,
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Float> cir
    ) {
        this.arena$blockedDamageForHunterScytheRecovery = Math.max(0.0F, cir.getReturnValueF());
    }

    @Inject(method = "damage", at = @At("RETURN"))
    private void arena$recoverBlackHeartsOnHit(
            ServerWorld world,
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> cir
    ) {
        float blockedDamage = this.arena$blockedDamageForHunterScytheRecovery;
        this.arena$blockedDamageForHunterScytheRecovery = 0.0F;

        if (!cir.getReturnValueZ() && blockedDamage <= 0.0F) {
            return;
        }

        HunterScytheManager.handleSuccessfulAttack((LivingEntity) (Object) this, source, amount, blockedDamage);
    }
}
