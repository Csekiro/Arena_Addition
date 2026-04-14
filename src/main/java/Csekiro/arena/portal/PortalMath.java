package Csekiro.arena.portal;

import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class PortalMath {
    private PortalMath() {
    }

    public static Vec3d vec(Direction direction) {
        return new Vec3d(direction.getOffsetX(), direction.getOffsetY(), direction.getOffsetZ());
    }

    public static Direction directionFromInts(int x, int y, int z) {
        for (Direction direction : Direction.values()) {
            if (direction.getOffsetX() == x && direction.getOffsetY() == y && direction.getOffsetZ() == z) {
                return direction;
            }
        }

        throw new IllegalArgumentException("Unsupported axis vector: " + x + ", " + y + ", " + z);
    }

    public static Direction cross(Direction first, Direction second) {
        Vec3d a = vec(first);
        Vec3d b = vec(second);
        return directionFromInts(
                (int) Math.round(a.y * b.z - a.z * b.y),
                (int) Math.round(a.z * b.x - a.x * b.z),
                (int) Math.round(a.x * b.y - a.y * b.x)
        );
    }
}
