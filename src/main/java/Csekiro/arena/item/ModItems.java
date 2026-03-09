package Csekiro.arena.item;

import Csekiro.arena.Arena;
import Csekiro.arena.item.EnderPearlProItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

// 可选：想放进创造栏就保留这两个 import 和 initialize() 里的代码
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;

import java.util.function.Function;

public final class ModItems {
    private ModItems() {
    }

    public static final Item ENDER_PEARL_PRO = register(
            "ender_pearl_pro",
            EnderPearlProItem::new,
            new Item.Settings().maxCount(16)
    );

    private static Item register(String path, Function<Item.Settings, Item> factory, Item.Settings settings) {
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(Arena.MOD_ID, path));
        return Items.register(key, factory, settings);
    }

    public static void initialize() {
        // 可选：加入创造模式物品栏
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
                .register(entries -> entries.add(ENDER_PEARL_PRO));
    }
}