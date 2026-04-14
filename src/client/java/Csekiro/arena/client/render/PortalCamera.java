package Csekiro.arena.client.render;

import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public class PortalCamera extends Camera {
    public void configure(ClientWorld world, Entity focusedEntity, Vec3d position, float yaw, float pitch, float tickProgress) {
        this.update(world, focusedEntity, false, false, tickProgress);
        this.setRotation(yaw, pitch);
        this.setPos(position);
    }
}
