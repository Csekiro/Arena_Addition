package Csekiro.arena.item;

import Csekiro.arena.Arena;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ToolMaterial;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public final class ModItems {
    private static final RegistryKey<net.minecraft.item.ItemGroup> TOOLS_GROUP = RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.ofVanilla("tools"));

    private ModItems() {
    }

    public static final Item ENDER_PEARL_PRO = register(
            "ender_pearl_pro",
            EnderPearlProItem::new,
            new Item.Settings().maxCount(16)
    );

    public static final Item HUNTER_SCYTHE = register(
            "hunter_scythe",
            HunterScytheItem::new,
            new Item.Settings()
                    .maxCount(1).attributeModifiers(
                            AttributeModifiersComponent.builder()
                                    // 注意 1.21 的写法：EntityAttributes.ATTACK_DAMAGE
                                    .add(EntityAttributes.ATTACK_DAMAGE,
                                            new EntityAttributeModifier(
                                                    Identifier.of("arena_", "base_attack_damage"),
                                                    6.0,
                                                    EntityAttributeModifier.Operation.ADD_VALUE),
                                            AttributeModifierSlot.MAINHAND)

                                    .add(EntityAttributes.ATTACK_SPEED,
                                            new EntityAttributeModifier(
                                                    Identifier.of("arena_", "base_attack_speed"),
                                                    -3.2,
                                                    EntityAttributeModifier.Operation.ADD_VALUE),
                                            AttributeModifierSlot.MAINHAND)
                                    .build()
                    )
    );

    private static Item register(String path, Function<Item.Settings, Item> factory, Item.Settings settings) {
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(Arena.MOD_ID, path));
        return Items.register(key, factory, settings);
    }

    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(TOOLS_GROUP)
                .register(entries -> {
                    entries.add(ENDER_PEARL_PRO);
                    entries.add(HUNTER_SCYTHE);
                });
    }
}
