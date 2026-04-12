package Csekiro.arena.item;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import xyz.nucleoid.packettweaker.PacketContext;

public class HunterScytheItem extends Item implements PolymerItem {
    //红心耗尽但有黑心是否锁血
    private static final boolean LAST_STAND_ENABLED = true;
    //是否主手持才触发回血
    private static final boolean RECOVERY_REQUIRES_MAIN_HAND = true;
    //非近战攻击触发回血
    private static final boolean NON_MELEE_CAN_TRIGGER_RECOVERY = false;

    //回血基值
    private static final float BLACK_HEART_RECOVERY_BASE = 2.0F;
    //回血倍率
    private static final float BLACK_HEART_RECOVERY_DAMAGE_MULTIPLIER = 0.5F;
    //盾牌挡下攻击,回血倍率
    private static final float BLACK_HEART_RECOVERY_SHIELD_BLOCKED_DAMAGE_MULTIPLIER = 0.5F;

    //回血上限
    private static final float BLACK_HEART_RECOVERY_CAP = 6.0F;
    //黑心存续时间
    private static final int BLACK_HEART_DURATION_TICKS = 100;

    public HunterScytheItem(Settings settings) {
        super(settings);
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
