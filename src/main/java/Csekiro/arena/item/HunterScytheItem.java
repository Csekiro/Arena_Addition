package Csekiro.arena.item;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import xyz.nucleoid.packettweaker.PacketContext;

public class HunterScytheItem extends Item implements PolymerItem {
    public static final float DEFAULT_ATTACK_DAMAGE = 7.0F;
    public static final float DEFAULT_ATTACK_SPEED = 0.8F;

    private static final boolean LAST_STAND_ENABLED = true;
    private static final boolean RECOVERY_REQUIRES_MAIN_HAND = true;
    private static final boolean NON_MELEE_CAN_TRIGGER_RECOVERY = false;
    private static final boolean FULL_RECOVERY_ON_KILL_ENABLED = true;
    private static final boolean FULL_RECOVERY_ON_NON_PLAYER_KILL_ENABLED = false;

    private static final float BLACK_HEART_RECOVERY_BASE = 2.0F;
    private static final float BLACK_HEART_RECOVERY_DAMAGE_MULTIPLIER = 0.5F;
    private static final float BLACK_HEART_RECOVERY_SHIELD_BLOCKED_DAMAGE_MULTIPLIER = 0.5F;
    private static final float BLACK_HEART_RECOVERY_CAP = 6.0F;
    private static final int BLACK_HEART_DURATION_TICKS = 100;

    public HunterScytheItem(Settings settings) {
        super(settings);
    }

    public static AttributeModifiersComponent createAttributeModifiers() {
        return AttributeModifiersComponent.builder()
                .add(
                        EntityAttributes.ATTACK_DAMAGE,
                        new EntityAttributeModifier(
                                Item.BASE_ATTACK_DAMAGE_MODIFIER_ID,
                                DEFAULT_ATTACK_DAMAGE - 6.0F,
                                EntityAttributeModifier.Operation.ADD_VALUE
                        ),
                        AttributeModifierSlot.MAINHAND
                )
                .add(
                        EntityAttributes.ATTACK_SPEED,
                        new EntityAttributeModifier(
                                Item.BASE_ATTACK_SPEED_MODIFIER_ID,
                                DEFAULT_ATTACK_SPEED - 4.0F,
                                EntityAttributeModifier.Operation.ADD_VALUE
                        ),
                        AttributeModifierSlot.MAINHAND
                )
                .build();
    }

    public static boolean isLastStandEnabled() {
        return LAST_STAND_ENABLED;
    }

    public static boolean isRecoveryRequiresMainHand() {
        return RECOVERY_REQUIRES_MAIN_HAND;
    }

    public static boolean isNonMeleeRecoveryEnabled() {
        return NON_MELEE_CAN_TRIGGER_RECOVERY;
    }

    public static boolean isFullRecoveryOnKillEnabled() {
        return FULL_RECOVERY_ON_KILL_ENABLED;
    }

    public static boolean isFullRecoveryOnNonPlayerKillEnabled() {
        return FULL_RECOVERY_ON_NON_PLAYER_KILL_ENABLED;
    }

    public static float getBlackHeartRecoveryBase() {
        return BLACK_HEART_RECOVERY_BASE;
    }

    public static float getBlackHeartRecoveryDamageMultiplier() {
        return BLACK_HEART_RECOVERY_DAMAGE_MULTIPLIER;
    }

    public static float getBlackHeartRecoveryShieldBlockedDamageMultiplier() {
        return BLACK_HEART_RECOVERY_SHIELD_BLOCKED_DAMAGE_MULTIPLIER;
    }

    public static float getBlackHeartRecoveryCap() {
        return BLACK_HEART_RECOVERY_CAP;
    }

    public static int getBlackHeartDurationTicks() {
        return BLACK_HEART_DURATION_TICKS;
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
        return Items.NETHERITE_HOE;
    }
}
