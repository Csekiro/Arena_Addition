package Csekiro.arena.client.render;

import net.minecraft.util.math.MathHelper;

public final class PortalRenderConfig {
    private static final String FRAMEBUFFER_SCALE_PROPERTY = "arena.portalFramebufferScale";
    private static final float DEFAULT_FRAMEBUFFER_SCALE = 1.0F;
    private static final float MIN_FRAMEBUFFER_SCALE = 0.25F;
    private static final int FRAMEBUFFER_IDLE_FRAMES = 60;
    private static final int MAX_VISIBLE_PORTALS = 4;
    private static final double MAX_CAPTURE_DISTANCE = 96.0D;

    private PortalRenderConfig() {
    }

    public static float framebufferScale() {
        String raw = System.getProperty(FRAMEBUFFER_SCALE_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_FRAMEBUFFER_SCALE;
        }

        try {
            return MathHelper.clamp(Float.parseFloat(raw), MIN_FRAMEBUFFER_SCALE, 1.0F);
        } catch (NumberFormatException ignored) {
            return DEFAULT_FRAMEBUFFER_SCALE;
        }
    }

    public static int framebufferIdleFrames() {
        return FRAMEBUFFER_IDLE_FRAMES;
    }

    public static int maxVisiblePortals() {
        return MAX_VISIBLE_PORTALS;
    }

    public static double maxCaptureDistance() {
        return MAX_CAPTURE_DISTANCE;
    }
}
