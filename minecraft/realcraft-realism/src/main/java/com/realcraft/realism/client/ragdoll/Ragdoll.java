package com.realcraft.realism.client.ragdoll;

import com.mojang.blaze3d.vertex.PoseStack;
import com.realcraft.realism.mixin.client.QuadrupedModelAccessor;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * A client-side ragdoll. The body is the creature's hitbox simulated as eight Verlet points held
 * rigid by distance constraints, so it topples, tumbles, bounces and slides against the real
 * block collision shapes. Arms, legs and the head swing towards gravity like pendulums.
 */
public final class Ragdoll {
    private static final double GRAVITY = 0.08;
    private static final double AIR_DRAG = 0.98;
    private static final double BOUNCE = 0.15;
    private static final double GROUND_FRICTION = 0.55;
    private static final int[][] PAIRS = new int[28][];

    static {
        int k = 0;
        for (int i = 0; i < 8; i++) {
            for (int j = i + 1; j < 8; j++) {
                PAIRS[k++] = new int[]{i, j};
            }
        }
    }

    final int entityId;
    private final float halfHeight;
    private final double[] x = new double[8];
    private final double[] y = new double[8];
    private final double[] z = new double[8];
    private final double[] ox = new double[8];
    private final double[] oy = new double[8];
    private final double[] oz = new double[8];
    private final double[] rest = new double[PAIRS.length];
    private final Vector3d center = new Vector3d();
    private final Vector3d prevCenter = new Vector3d();
    private final Quaternionf orientation = new Quaternionf();
    private final Quaternionf prevOrientation = new Quaternionf();
    private final Limb[] limbs = new Limb[5]; // head, right arm/front leg, left arm/front leg, right leg/hind leg, left leg/hind leg
    private final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
    private int restingTicks;
    private boolean asleep;

    Ragdoll(LivingEntity entity) {
        this.entityId = entity.getId();
        float halfWidth = entity.getBbWidth() / 2.0F;
        this.halfHeight = entity.getBbHeight() / 2.0F;
        RandomSource random = RandomSource.create(entity.getId() * 31L + entity.tickCount);

        // Same starting rotation vanilla applies in LivingEntityRenderer#setupRotations.
        Quaternionf start = new Quaternionf().rotationY((float) Math.toRadians(180.0 - entity.yBodyRot));
        double cx = entity.getX();
        double cy = entity.getY() + halfHeight;
        double cz = entity.getZ();

        Vec3 velocity = new Vec3(entity.getX() - entity.xo, entity.getY() - entity.yo, entity.getZ() - entity.zo);
        if (entity.getDeltaMovement().lengthSqr() > velocity.lengthSqr()) {
            velocity = entity.getDeltaMovement();
        }
        if (velocity.length() > 1.5) {
            velocity = velocity.normalize().scale(1.5);
        }

        // Topple in the direction the body was knocked, otherwise backwards (tall bodies) or sideways (wide ones).
        Vec3 horizontal = new Vec3(velocity.x, 0, velocity.z);
        Vec3 topple;
        if (horizontal.lengthSqr() > 4.0E-4) {
            topple = horizontal.normalize();
        } else {
            float yaw = (float) Math.toRadians(entity.yBodyRot);
            Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
            topple = entity.getBbHeight() > entity.getBbWidth() * 1.5F
                    ? forward.scale(-1)
                    : new Vec3(forward.z, 0, -forward.x).scale(random.nextBoolean() ? 1 : -1);
        }
        double tip = 0.04 + random.nextDouble() * 0.04;

        Vector3f local = new Vector3f();
        for (int i = 0; i < 8; i++) {
            float sx = (i & 1) == 1 ? 1 : -1;
            float sy = (i >> 1 & 1) == 1 ? 1 : -1;
            float sz = (i >> 2 & 1) == 1 ? 1 : -1;
            local.set(sx * halfWidth, sy * halfHeight, sz * halfWidth);
            start.transform(local);
            x[i] = cx + local.x;
            y[i] = cy + local.y;
            z[i] = cz + local.z;
            double push = sy > 0 ? tip : -tip * 0.2;
            ox[i] = x[i] - velocity.x - topple.x * push;
            oy[i] = y[i] - velocity.y;
            oz[i] = z[i] - velocity.z - topple.z * push;
        }
        for (int k = 0; k < PAIRS.length; k++) {
            rest[k] = distance(PAIRS[k][0], PAIRS[k][1]);
        }
        center.set(cx, cy, cz);
        prevCenter.set(center);
        orientation.set(start);
        prevOrientation.set(start);

        limbs[0] = new Limb(0.5F, 0.8F, random);
        for (int i = 1; i < limbs.length; i++) {
            limbs[i] = new Limb(i <= 2 ? 1.0F : 0.7F, i <= 2 ? 1.5F : 1.1F, random);
        }
    }

    void tick(Level level, int iterations) {
        prevCenter.set(center);
        prevOrientation.set(orientation);
        for (Limb limb : limbs) {
            limb.prevX = limb.x;
            limb.prevZ = limb.z;
        }
        if (asleep) {
            return;
        }

        for (int i = 0; i < 8; i++) {
            double vx = (x[i] - ox[i]) * AIR_DRAG;
            double vy = (y[i] - oy[i]) * AIR_DRAG;
            double vz = (z[i] - oz[i]) * AIR_DRAG;
            ox[i] = x[i];
            oy[i] = y[i];
            oz[i] = z[i];
            x[i] += vx;
            y[i] += vy - GRAVITY;
            z[i] += vz;
        }
        for (int it = 0; it < iterations; it++) {
            solveConstraints();
            for (int i = 0; i < 8; i++) {
                collide(level, i);
            }
        }
        updateFrame();
        updateLimbs();

        double maxMove = 0;
        for (int i = 0; i < 8; i++) {
            maxMove = Math.max(maxMove, Math.abs(x[i] - ox[i]) + Math.abs(y[i] - oy[i]) + Math.abs(z[i] - oz[i]));
        }
        restingTicks = maxMove < 0.003 ? restingTicks + 1 : 0;
        if (restingTicks > 30 || center.y < level.getMinBuildHeight() - 64) {
            asleep = true;
        }
    }

    private void solveConstraints() {
        for (int k = 0; k < PAIRS.length; k++) {
            int i = PAIRS[k][0];
            int j = PAIRS[k][1];
            double dx = x[j] - x[i], dy = y[j] - y[i], dz = z[j] - z[i];
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1.0E-9) {
                continue;
            }
            double diff = (length - rest[k]) / length * 0.5;
            dx *= diff;
            dy *= diff;
            dz *= diff;
            x[i] += dx;
            y[i] += dy;
            z[i] += dz;
            x[j] -= dx;
            y[j] -= dy;
            z[j] -= dz;
        }
    }

    private void collide(Level level, int i) {
        mutable.set(Mth.floor(x[i]), Mth.floor(y[i]), Mth.floor(z[i]));
        BlockState state = level.getBlockState(mutable);
        if (state.isAir()) {
            return;
        }
        VoxelShape shape = state.getCollisionShape(level, mutable);
        if (shape.isEmpty()) {
            return;
        }
        double bx = mutable.getX(), by = mutable.getY(), bz = mutable.getZ();
        double lx = x[i] - bx, ly = y[i] - by, lz = z[i] - bz;
        for (AABB box : shape.toAabbs()) {
            if (lx <= box.minX || lx >= box.maxX || ly <= box.minY || ly >= box.maxY || lz <= box.minZ || lz >= box.maxZ) {
                continue;
            }
            double up = box.maxY - ly;
            double down = ly - box.minY;
            double east = box.maxX - lx;
            double west = lx - box.minX;
            double south = box.maxZ - lz;
            double north = lz - box.minZ;
            boolean fromAbove = oy[i] - by >= box.maxY - 1.0E-3;
            double side = Math.min(Math.min(east, west), Math.min(south, north));
            if (fromAbove || up <= Math.min(side, down)) {
                double vy = y[i] - oy[i];
                y[i] = by + box.maxY + 1.0E-3;
                oy[i] = y[i] + vy * BOUNCE;
                ox[i] = x[i] - (x[i] - ox[i]) * GROUND_FRICTION;
                oz[i] = z[i] - (z[i] - oz[i]) * GROUND_FRICTION;
            } else if (down <= side) {
                y[i] = by + box.minY - 1.0E-3;
                oy[i] = y[i];
            } else if (side == east) {
                x[i] = bx + box.maxX + 1.0E-3;
            } else if (side == west) {
                x[i] = bx + box.minX - 1.0E-3;
            } else if (side == south) {
                z[i] = bz + box.maxZ + 1.0E-3;
            } else {
                z[i] = bz + box.minZ - 1.0E-3;
            }
            return;
        }
    }

    private void updateFrame() {
        double cx = 0, cy = 0, cz = 0;
        for (int i = 0; i < 8; i++) {
            cx += x[i];
            cy += y[i];
            cz += z[i];
        }
        cx /= 8;
        cy /= 8;
        cz /= 8;
        center.set(cx, cy, cz);

        // Box axes from the (possibly slightly deformed) corners, then re-orthonormalised.
        Vector3f ax = new Vector3f(), ay = new Vector3f();
        for (int i = 0; i < 8; i++) {
            float px = (float) (x[i] - cx), py = (float) (y[i] - cy), pz = (float) (z[i] - cz);
            float sx = (i & 1) == 1 ? 1 : -1;
            float sy = (i >> 1 & 1) == 1 ? 1 : -1;
            ax.add(px * sx, py * sx, pz * sx);
            ay.add(px * sy, py * sy, pz * sy);
        }
        if (ax.lengthSquared() < 1.0E-8F || ay.lengthSquared() < 1.0E-8F) {
            return;
        }
        ax.normalize();
        ay.sub(new Vector3f(ax).mul(ax.dot(ay)));
        if (ay.lengthSquared() < 1.0E-8F) {
            return;
        }
        ay.normalize();
        Vector3f az = ax.cross(ay, new Vector3f());
        new Matrix3f(ax.x, ax.y, ax.z, ay.x, ay.y, ay.z, az.x, az.y, az.z).getNormalizedRotation(orientation);
    }

    private void updateLimbs() {
        // Gravity seen from the model: the body frame, then the renderer's scale(-1, -1, 1) flip.
        Vector3f g = orientation.transformInverse(new Vector3f(0, -1, 0));
        float gx = -g.x, gy = -g.y, gz = g.z;
        float targetX = (float) Math.asin(Mth.clamp(gz, -1.0F, 1.0F));
        float targetZ = (float) Mth.atan2(-gx, gy);
        for (Limb limb : limbs) {
            limb.step(targetX, targetZ);
        }
    }

    public void applyBodyPose(LivingEntity entity, PoseStack poseStack, float partialTicks) {
        double ex = Mth.lerp(partialTicks, entity.xOld, entity.getX());
        double ey = Mth.lerp(partialTicks, entity.yOld, entity.getY());
        double ez = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
        poseStack.translate(
                Mth.lerp(partialTicks, prevCenter.x, center.x) - ex,
                Mth.lerp(partialTicks, prevCenter.y, center.y) - ey,
                Mth.lerp(partialTicks, prevCenter.z, center.z) - ez);
        poseStack.mulPose(prevOrientation.slerp(orientation, partialTicks, new Quaternionf()));
        poseStack.translate(0.0F, -halfHeight, 0.0F);
    }

    public void applyLimbs(EntityModel<?> model, float partialTicks) {
        if (model instanceof HumanoidModel<?> humanoid) {
            limbs[0].apply(humanoid.head, partialTicks);
            humanoid.hat.copyFrom(humanoid.head);
            limbs[1].apply(humanoid.rightArm, partialTicks);
            limbs[2].apply(humanoid.leftArm, partialTicks);
            limbs[3].apply(humanoid.rightLeg, partialTicks);
            limbs[4].apply(humanoid.leftLeg, partialTicks);
            if (model instanceof PlayerModel<?> player) {
                player.rightSleeve.copyFrom(player.rightArm);
                player.leftSleeve.copyFrom(player.leftArm);
                player.rightPants.copyFrom(player.rightLeg);
                player.leftPants.copyFrom(player.leftLeg);
                player.jacket.copyFrom(player.body);
            }
        } else if (model instanceof QuadrupedModel<?> quadruped) {
            QuadrupedModelAccessor parts = (QuadrupedModelAccessor) quadruped;
            limbs[0].apply(parts.realcraft$head(), partialTicks);
            limbs[1].apply(parts.realcraft$rightFrontLeg(), partialTicks);
            limbs[2].apply(parts.realcraft$leftFrontLeg(), partialTicks);
            limbs[3].apply(parts.realcraft$rightHindLeg(), partialTicks);
            limbs[4].apply(parts.realcraft$leftHindLeg(), partialTicks);
        }
    }

    private double distance(int i, int j) {
        double dx = x[j] - x[i], dy = y[j] - y[i], dz = z[j] - z[i];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** A damped pendulum that swings a model part towards the direction of gravity. */
    private static final class Limb {
        final float gain;
        final float limit;
        float x, z, prevX, prevZ, vx, vz;

        Limb(float gain, float limit, RandomSource random) {
            this.gain = gain;
            this.limit = limit;
            this.vx = (random.nextFloat() - 0.5F) * 0.3F;
            this.vz = (random.nextFloat() - 0.5F) * 0.3F;
        }

        void step(float targetX, float targetZ) {
            vx = (vx + (targetX * gain - x) * 0.3F) * 0.75F;
            vz = (vz + (targetZ * gain - z) * 0.3F) * 0.75F;
            x = Mth.clamp(x + vx, -limit, limit);
            z = Mth.clamp(z + vz, -limit, limit);
        }

        void apply(ModelPart part, float partialTicks) {
            part.xRot = Mth.lerp(partialTicks, prevX, x);
            part.yRot = 0.0F;
            part.zRot = Mth.lerp(partialTicks, prevZ, z);
        }
    }
}
