package Csekiro.arena.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.util.Identifier;

import java.util.Arrays;

public final class PortalFrameBufferPool {
    private static final int POOL_SIZE = 4;
    private final PooledFramebuffer[] pool = new PooledFramebuffer[POOL_SIZE];
    private final boolean[] inUse = new boolean[POOL_SIZE];

    public void beginFrame() {
        Arrays.fill(this.inUse, false);
    }

    public PooledFramebuffer acquire(MinecraftClient client, int width, int height) {
        for (int i = 0; i < this.pool.length; i++) {
            if (this.inUse[i]) {
                continue;
            }

            this.inUse[i] = true;
            if (this.pool[i] == null) {
                this.pool[i] = new PooledFramebuffer(client, i, width, height);
            } else {
                this.pool[i].resize(width, height);
            }

            return this.pool[i];
        }

        return null;
    }

    public static final class PooledFramebuffer {
        private final SimpleFramebuffer framebuffer;
        private final PortalFramebufferTexture texture;
        private final Identifier textureId;
        private int width;
        private int height;

        private PooledFramebuffer(MinecraftClient client, int index, int width, int height) {
            this.framebuffer = new SimpleFramebuffer("Portal View " + index, width, height, true);
            this.texture = new PortalFramebufferTexture();
            this.textureId = Identifier.of("arena", "dynamic/portal_pool/" + index);
            client.getTextureManager().registerTexture(this.textureId, this.texture);
            this.width = width;
            this.height = height;
        }

        public void resize(int width, int height) {
            if (this.width == width && this.height == height) {
                return;
            }

            this.framebuffer.resize(width, height);
            this.width = width;
            this.height = height;
        }

        public void copyToSampledTexture() {
            this.texture.copyFrom(this.framebuffer);
        }

        public SimpleFramebuffer framebuffer() {
            return this.framebuffer;
        }

        public Identifier textureId() {
            return this.textureId;
        }
    }
}
