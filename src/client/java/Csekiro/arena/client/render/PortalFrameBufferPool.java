package Csekiro.arena.client.render;

import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.util.Window;

public final class PortalFrameBufferPool {
    private SimpleFramebuffer framebuffer;
    private int width = -1;
    private int height = -1;

    public SimpleFramebuffer get(Window window) {
        int targetWidth = Math.max(320, Math.min(960, window.getFramebufferWidth() / 2));
        int targetHeight = Math.max(180, Math.min(540, window.getFramebufferHeight() / 2));
        if (this.framebuffer == null) {
            this.framebuffer = new SimpleFramebuffer("Portal View", targetWidth, targetHeight, true);
            this.width = targetWidth;
            this.height = targetHeight;
        } else if (this.width != targetWidth || this.height != targetHeight) {
            this.framebuffer.resize(targetWidth, targetHeight);
            this.width = targetWidth;
            this.height = targetHeight;
        }

        return this.framebuffer;
    }
}
