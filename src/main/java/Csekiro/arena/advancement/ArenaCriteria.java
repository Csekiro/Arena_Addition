package Csekiro.arena.advancement;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ArenaCriteria {
    public static final UsedCriterion USED = Registry.register(Registries.CRITERION, Identifier.of("arena", "used"), new UsedCriterion());

    private ArenaCriteria() {
    }

    public static void register() {
        // 仅用于触发类加载
    }
}