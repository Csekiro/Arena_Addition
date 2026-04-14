package Csekiro.arena.portal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PortalRuntimeState {
    public final Map<UUID, PortalPairRuntime> pairs = new HashMap<>();
    public final Map<UUID, Set<UUID>> teleportLocks = new HashMap<>();

    public PortalPairRuntime pair(UUID owner) {
        return this.pairs.computeIfAbsent(owner, ignored -> new PortalPairRuntime());
    }

    public Set<UUID> lockSet(UUID playerUuid) {
        return this.teleportLocks.computeIfAbsent(playerUuid, ignored -> new HashSet<>());
    }

    public static final class PortalPairRuntime {
        public UUID bluePortalUuid;
        public UUID orangePortalUuid;
        public int pairRevision;
    }
}
