package Csekiro.arena.portal;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public record PortalPose(
        Vec3d center,
        Direction normal,
        Direction heightAxis,
        Direction widthAxis,
        BlockPos hostBasePos,
        Direction hostFace
) {
    public static final double WIDTH = 1.0D;
    public static final double HEIGHT = 2.0D;
    public static final double THICKNESS = 0.1D;
    public static final double HALF_WIDTH = WIDTH * 0.5D;
    public static final double HALF_HEIGHT = HEIGHT * 0.5D;
    public static final double HALF_THICKNESS = THICKNESS * 0.5D;
    public static final double SURFACE_EPSILON = 0.02D;
    public static final double EXIT_DEPTH = 0.85D;

    public Box portalPlaneBox() {
        return axisBox(HALF_WIDTH, HALF_HEIGHT, HALF_THICKNESS);
    }

    public Box exitClearanceBox() {
        Vec3d outward = PortalMath.vec(this.normal).multiply(EXIT_DEPTH * 0.5D + HALF_THICKNESS);
        return axisBox(0.45D, 0.9D, EXIT_DEPTH * 0.5D).offset(outward);
    }

    public Vec3d localToWorld(double u, double v, double n) {
        return this.center
                .add(PortalMath.vec(this.widthAxis).multiply(u))
                .add(PortalMath.vec(this.heightAxis).multiply(v))
                .add(PortalMath.vec(this.normal).multiply(n));
    }

    public Vec3d worldToLocalFull(Vec3d point) {
        Vec3d delta = point.subtract(this.center);
        return new Vec3d(
                delta.dotProduct(PortalMath.vec(this.widthAxis)),
                delta.dotProduct(PortalMath.vec(this.heightAxis)),
                delta.dotProduct(PortalMath.vec(this.normal))
        );
    }

    public BlockPos upperSupportPos() {
        return this.hostBasePos.offset(this.heightAxis);
    }

    public double signedDistance(Vec3d point) {
        return worldToLocalFull(point).z;
    }

    public boolean containsProjectedPoint(Vec3d point) {
        Vec3d local = worldToLocalFull(point);
        return Math.abs(local.x) <= HALF_WIDTH + 0.15D && Math.abs(local.y) <= HALF_HEIGHT + 0.15D;
    }

    private Box axisBox(double halfWidth, double halfHeight, double halfDepth) {
        Vec3d width = PortalMath.vec(this.widthAxis).multiply(halfWidth);
        Vec3d height = PortalMath.vec(this.heightAxis).multiply(halfHeight);
        Vec3d depth = PortalMath.vec(this.normal).multiply(halfDepth);
        Vec3d a = this.center.add(width).add(height).add(depth);
        Vec3d b = this.center.add(width).add(height).subtract(depth);
        Vec3d c = this.center.add(width).subtract(height).add(depth);
        Vec3d d = this.center.subtract(width).add(height).add(depth);
        return new Box(
                Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x)),
                Math.min(Math.min(a.y, b.y), Math.min(c.y, d.y)),
                Math.min(Math.min(a.z, b.z), Math.min(c.z, d.z)),
                Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x)),
                Math.max(Math.max(a.y, b.y), Math.max(c.y, d.y)),
                Math.max(Math.max(a.z, b.z), Math.max(c.z, d.z))
        );
    }
}
