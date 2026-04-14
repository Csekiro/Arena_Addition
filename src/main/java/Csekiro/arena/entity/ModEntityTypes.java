package Csekiro.arena.entity;

import Csekiro.arena.Arena;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModEntityTypes {
    public static final EntityType<PortalEntity> PORTAL = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(Arena.MOD_ID, "portal"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, PortalEntity::new)
                    .dimensions(EntityDimensions.fixed(1.1F, 2.1F))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(1)
                    .disableSaving()
                    .disableSummon()
                    .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Arena.MOD_ID, "portal")))
    );

    private ModEntityTypes() {
    }

    public static void initialize() {
    }
}
