package Csekiro.arena.portal;

import Csekiro.arena.entity.PortalColor;
import Csekiro.arena.entity.PortalEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class PortalPairManager {
    private static final Map<MinecraftServer, PortalRuntimeState> STATES = Collections.synchronizedMap(new WeakHashMap<>());

    private PortalPairManager() {
    }

    public static PortalRuntimeState state(MinecraftServer server) {
        return STATES.computeIfAbsent(server, ignored -> new PortalRuntimeState());
    }

    public static void setPortal(MinecraftServer server, UUID ownerUuid, PortalColor color, @Nullable PortalEntity portal) {
        PortalRuntimeState.PortalPairRuntime runtime = state(server).pair(ownerUuid);
        if (color == PortalColor.BLUE) {
            runtime.bluePortalUuid = portal == null ? null : portal.getUuid();
        } else {
            runtime.orangePortalUuid = portal == null ? null : portal.getUuid();
        }

        runtime.pairRevision++;
        syncPairRevision(server, runtime);
        if (runtime.bluePortalUuid == null && runtime.orangePortalUuid == null) {
            state(server).pairs.remove(ownerUuid);
        }
    }

    public static void clearPortal(MinecraftServer server, UUID ownerUuid, PortalColor color) {
        setPortal(server, ownerUuid, color, null);
    }

    @Nullable
    public static PortalEntity getPortal(ServerWorld world, UUID ownerUuid, PortalColor color) {
        PortalRuntimeState.PortalPairRuntime runtime = state(world.getServer()).pairs.get(ownerUuid);
        if (runtime == null) {
            return null;
        }

        UUID portalUuid = color == PortalColor.BLUE ? runtime.bluePortalUuid : runtime.orangePortalUuid;
        if (portalUuid == null) {
            return null;
        }

        return world.getEntity(portalUuid) instanceof PortalEntity portal ? portal : null;
    }

    @Nullable
    public static PortalEntity getPair(PortalEntity portal) {
        if (portal.getOwnerUuid() == null || !(portal.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return null;
        }

        return getPortal(serverWorld, portal.getOwnerUuid(), portal.getColor().opposite());
    }

    public static int getPairRevision(MinecraftServer server, UUID ownerUuid) {
        PortalRuntimeState.PortalPairRuntime runtime = state(server).pairs.get(ownerUuid);
        return runtime == null ? 0 : runtime.pairRevision;
    }

    public static void clearAllForPlayer(ServerPlayerEntity player) {
        ServerWorld serverWorld = player.getEntityWorld();
        UUID ownerUuid = player.getUuid();
        PortalEntity blue = getPortal(serverWorld, ownerUuid, PortalColor.BLUE);
        PortalEntity orange = getPortal(serverWorld, ownerUuid, PortalColor.ORANGE);

        if (blue != null) {
            blue.discard();
        }

        if (orange != null) {
            orange.discard();
        }

        clearPortal(serverWorld.getServer(), ownerUuid, PortalColor.BLUE);
        clearPortal(serverWorld.getServer(), ownerUuid, PortalColor.ORANGE);
        clearTeleportLock(serverWorld.getServer(), ownerUuid);
    }

    public static Set<UUID> getTeleportLock(MinecraftServer server, UUID playerUuid) {
        return state(server).lockSet(playerUuid);
    }

    public static void clearTeleportLock(MinecraftServer server, UUID playerUuid) {
        state(server).teleportLocks.remove(playerUuid);
    }

    private static void syncPairRevision(MinecraftServer server, PortalRuntimeState.PortalPairRuntime runtime) {
        ServerWorld overworld = server.getOverworld();
        if (overworld == null) {
            return;
        }

        if (runtime.bluePortalUuid != null && overworld.getEntity(runtime.bluePortalUuid) instanceof PortalEntity blue) {
            blue.setPairRevision(runtime.pairRevision);
        }

        if (runtime.orangePortalUuid != null && overworld.getEntity(runtime.orangePortalUuid) instanceof PortalEntity orange) {
            orange.setPairRevision(runtime.pairRevision);
        }
    }
}
