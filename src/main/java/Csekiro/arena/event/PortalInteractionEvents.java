package Csekiro.arena.event;

import Csekiro.arena.item.PortalGunItem;
import Csekiro.arena.portal.PortalPlacementService;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public final class PortalInteractionEvents {
    private PortalInteractionEvents() {
    }

    public static void initialize() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            PortalGunItem portalGun = getPortalGun(player.getStackInHand(hand));
            if (portalGun == null) {
                return ActionResult.PASS;
            }

            if (world.isClient()) {
                return ActionResult.SUCCESS;
            }

            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.FAIL;
            }

            HitResult hitResult = player.raycast(player.getBlockInteractionRange(), 0.0F, false);
            if (!(hitResult instanceof BlockHitResult blockHitResult)) {
                return ActionResult.FAIL;
            }

            return PortalPlacementService.tryPlaceBlue(serverPlayer, portalGun, pos, direction, blockHitResult.getPos())
                    ? ActionResult.SUCCESS
                    : ActionResult.FAIL;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            PortalGunItem portalGun = getPortalGun(player.getStackInHand(hand));
            if (portalGun == null) {
                return ActionResult.PASS;
            }

            if (world.isClient()) {
                return ActionResult.SUCCESS;
            }

            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.FAIL;
            }

            return PortalPlacementService.tryPlaceOrange(serverPlayer, portalGun, hitResult.getBlockPos(), hitResult.getSide(), hitResult.getPos())
                    ? ActionResult.SUCCESS
                    : ActionResult.FAIL;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> getPortalGun(player.getStackInHand(hand)) == null ? ActionResult.PASS : ActionResult.FAIL);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> getPortalGun(player.getStackInHand(hand)) == null ? ActionResult.PASS : ActionResult.FAIL);
    }

    private static PortalGunItem getPortalGun(ItemStack stack) {
        return stack.getItem() instanceof PortalGunItem portalGun ? portalGun : null;
    }
}
