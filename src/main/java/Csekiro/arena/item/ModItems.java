package Csekiro.arena.item;

import Csekiro.arena.Arena;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
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
                    .maxCount(1)
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
