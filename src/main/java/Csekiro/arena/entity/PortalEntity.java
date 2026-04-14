package Csekiro.arena.entity;

import Csekiro.arena.portal.PortalMath;
import Csekiro.arena.portal.PortalPlacementRules;
import Csekiro.arena.portal.PortalPlacementService;
import Csekiro.arena.portal.PortalPose;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class PortalEntity extends Entity {
    private static final TrackedData<String> OWNER_UUID = DataTracker.registerData(PortalEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> COLOR = DataTracker.registerData(PortalEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<BlockPos> HOST_BASE_POS = DataTracker.registerData(PortalEntity.class, TrackedDataHandlerRegistry.BLOCK_POS);
    private static final TrackedData<Direction> NORMAL = DataTracker.registerData(PortalEntity.class, TrackedDataHandlerRegistry.FACING);
    private static final TrackedData<Direction> HEIGHT_AXIS = DataTracker.registerData(PortalEntity.class, TrackedDataHandlerRegistry.FACING);
    private static final TrackedData<Direction> WIDTH_AXIS = DataTracker.registerData(PortalEntity.class, TrackedDataHandlerRegistry.FACING);
    private static final TrackedData<Boolean> HOST_BOUND = DataTracker.registerData(PortalEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> ALLOW_FLOOR_AND_CEILING = DataTracker.registerData(PortalEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> DESTROY_WHEN_HOST_INVALID = DataTracker.registerData(PortalEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> PAIR_REVISION = DataTracker.registerData(PortalEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public PortalEntity(EntityType<? extends PortalEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public void initialize(@Nullable UUID ownerUuid, PortalColor color, PortalPose pose, boolean hostBound, boolean allowFloorAndCeiling, boolean destroyWhenHostInvalid) {
        this.dataTracker.set(OWNER_UUID, ownerUuid == null ? "" : ownerUuid.toString());
        this.dataTracker.set(COLOR, color.id());
        this.dataTracker.set(HOST_BASE_POS, pose.hostBasePos());
        this.dataTracker.set(NORMAL, pose.normal());
        this.dataTracker.set(HEIGHT_AXIS, pose.heightAxis());
        this.dataTracker.set(WIDTH_AXIS, pose.widthAxis());
        this.dataTracker.set(HOST_BOUND, hostBound);
        this.dataTracker.set(ALLOW_FLOOR_AND_CEILING, allowFloorAndCeiling);
        this.dataTracker.set(DESTROY_WHEN_HOST_INVALID, destroyWhenHostInvalid);
        refreshPoseState();
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(OWNER_UUID, "");
        builder.add(COLOR, PortalColor.BLUE.id());
        builder.add(HOST_BASE_POS, BlockPos.ORIGIN);
        builder.add(NORMAL, Direction.NORTH);
        builder.add(HEIGHT_AXIS, Direction.UP);
        builder.add(WIDTH_AXIS, Direction.EAST);
        builder.add(HOST_BOUND, true);
        builder.add(ALLOW_FLOOR_AND_CEILING, true);
        builder.add(DESTROY_WHEN_HOST_INVALID, true);
        builder.add(PAIR_REVISION, 0);
    }

    @Override
    public void tick() {
        super.tick();
        refreshPoseState();

        if (!this.getEntityWorld().isClient() && this.shouldDestroyWhenHostInvalid() && this.getEntityWorld() instanceof ServerWorld serverWorld) {
            if (!PortalPlacementRules.isHostStillValid(serverWorld, getPortalPose())) {
                PortalPlacementService.removePortal(this);
            }
        }
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (data == HOST_BASE_POS || data == NORMAL || data == HEIGHT_AXIS || data == WIDTH_AXIS) {
            refreshPoseState();
        }
    }

    @Override
    protected void readCustomData(ReadView view) {
    }

    @Override
    protected void writeCustomData(WriteView view) {
    }

    @Override
    public boolean shouldSave() {
        return false;
    }

    @Override
    public boolean collidesWith(Entity other) {
        return false;
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isCollidable(@Nullable Entity entity) {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return false;
    }

    @Override
    public EntityDimensions getDimensions(EntityPose pose) {
        return EntityDimensions.fixed(1.1F, 2.1F);
    }

    @Nullable
    public UUID getOwnerUuid() {
        String ownerUuid = this.dataTracker.get(OWNER_UUID);
        return ownerUuid.isEmpty() ? null : UUID.fromString(ownerUuid);
    }

    public PortalColor getColor() {
        return PortalColor.byId(this.dataTracker.get(COLOR));
    }

    public boolean isHostBound() {
        return this.dataTracker.get(HOST_BOUND);
    }

    public boolean allowFloorAndCeilingSnapshot() {
        return this.dataTracker.get(ALLOW_FLOOR_AND_CEILING);
    }

    public boolean shouldDestroyWhenHostInvalid() {
        return this.dataTracker.get(DESTROY_WHEN_HOST_INVALID);
    }

    public int getPairRevision() {
        return this.dataTracker.get(PAIR_REVISION);
    }

    public void setPairRevision(int pairRevision) {
        this.dataTracker.set(PAIR_REVISION, pairRevision);
    }

    public PortalPose getPortalPose() {
        BlockPos basePos = this.dataTracker.get(HOST_BASE_POS);
        Direction normal = this.dataTracker.get(NORMAL);
        Direction heightAxis = this.dataTracker.get(HEIGHT_AXIS);
        Direction widthAxis = this.dataTracker.get(WIDTH_AXIS);
        return new PortalPose(
                basePos.toCenterPos()
                        .add(PortalMath.vec(heightAxis).multiply(0.5D))
                        .add(PortalMath.vec(normal).multiply(PortalPose.SURFACE_EPSILON)),
                normal,
                heightAxis,
                widthAxis,
                basePos,
                normal
        );
    }

    private void refreshPoseState() {
        PortalPose pose = getPortalPose();
        this.setPosition(pose.center());
        this.setBoundingBox(pose.portalPlaneBox());
    }
}
