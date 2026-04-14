package Csekiro.arena.client.render;

import Csekiro.arena.entity.PortalEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PortalRenderState {
    private static boolean renderingExitView;
    private static Set<UUID> maskedPortalIds = Set.of();

    private PortalRenderState() {
    }

    public static void reset() {
        renderingExitView = false;
        maskedPortalIds = Set.of();
    }

    public static boolean isRenderingExitView() {
        return renderingExitView;
    }

    public static void beginExitView(Set<UUID> maskedPortals) {
        renderingExitView = true;
        maskedPortalIds = maskedPortals.isEmpty() ? Set.of() : Set.copyOf(new HashSet<>(maskedPortals));
    }

    public static void endExitView() {
        reset();
    }

    public static boolean shouldRenderBlackFace(PortalEntity portal) {
        return renderingExitView && maskedPortalIds.contains(portal.getUuid());
    }
}
