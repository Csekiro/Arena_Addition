package Csekiro.arena.client.render;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.texture.AbstractTexture;

public final class PortalFramebufferTexture extends AbstractTexture {
    private int width;
    private int height;

    public void copyFrom(SimpleFramebuffer framebuffer) {
        RenderSystem.assertOnRenderThread();
        if (framebuffer.getColorAttachment() == null || framebuffer.getColorAttachmentView() == null) {
            return;
        }

        ensureSize(framebuffer.textureWidth, framebuffer.textureHeight);
        RenderSystem.getDevice()
                .createCommandEncoder()
                .copyTextureToTexture(framebuffer.getColorAttachment(), this.glTexture, 0, 0, 0, 0, 0, this.width, this.height);
    }

    private void ensureSize(int width, int height) {
        if (this.glTexture != null && this.width == width && this.height == height) {
            return;
        }

        super.close();
        GpuDevice device = RenderSystem.getDevice();
        this.glTexture = device.createTexture(() -> "portal-sampled-view", 15, TextureFormat.RGBA8, width, height, 1, 1);
        this.glTexture.setAddressMode(AddressMode.CLAMP_TO_EDGE);
        this.glTexture.setTextureFilter(FilterMode.NEAREST, false);
        this.glTextureView = device.createTextureView(this.glTexture);
        this.width = width;
        this.height = height;
    }
}
