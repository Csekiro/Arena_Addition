package Csekiro.arena.advancement;

import net.minecraft.advancement.criterion.Criteria;

public final class ArenaCriteria {
    public static final UsedCriterion USED = Criteria.register("arena:used", new UsedCriterion());

    private ArenaCriteria() {
    }

    public static void register() {
        // 仅用于触发类加载
    }
}