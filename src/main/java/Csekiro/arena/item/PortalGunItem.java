package Csekiro.arena.item;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import xyz.nucleoid.packettweaker.PacketContext;

public class PortalGunItem extends Item implements PolymerItem {
    private final boolean allowFloorAndCeiling;
    private final boolean destroyWhenHostInvalid;

    public PortalGunItem(Settings settings, boolean allowFloorAndCeiling, boolean destroyWhenHostInvalid) {
        super(settings);
        this.allowFloorAndCeiling = allowFloorAndCeiling;
        this.destroyWhenHostInvalid = destroyWhenHostInvalid;
    }

    public boolean allowFloorAndCeiling() {
        return this.allowFloorAndCeiling;
    }

    public boolean destroyWhenHostInvalid() {
        return this.destroyWhenHostInvalid;
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
        return Items.BLAZE_ROD;
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        return ActionResult.FAIL;
    }
}
