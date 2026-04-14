package Csekiro.arena.event;

import Csekiro.arena.portal.PortalPairManager;
import Csekiro.arena.portal.PortalPlacementService;
import Csekiro.arena.portal.PortalTeleportService;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;

public final class PortalLifecycleEvents {
    private PortalLifecycleEvents() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(PortalTeleportService::tickServer);
        ServerPlayerEvents.JOIN.register(PortalPlacementService::revalidateOwnedPortals);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) {
                PortalPairManager.clearAllForPlayer(oldPlayer);
                PortalTeleportService.clearLock(newPlayer);
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity player) {
                PortalPairManager.clearAllForPlayer(player);
            }
        });
    }
}
