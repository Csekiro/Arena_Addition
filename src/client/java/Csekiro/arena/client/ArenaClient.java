package Csekiro.arena.client;

import Csekiro.arena.client.render.PortalEntityRenderer;
import Csekiro.arena.client.render.PortalRenderManager;
import Csekiro.arena.entity.ModEntityTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class ArenaClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntityTypes.PORTAL, PortalEntityRenderer::new);
        PortalRenderManager.getInstance().initialize();
    }
}
