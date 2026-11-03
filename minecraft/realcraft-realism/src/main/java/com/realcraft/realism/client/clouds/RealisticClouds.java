package com.realcraft.realism.client.clouds;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.realcraft.realism.config.RealConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import javax.annotation.Nullable;

/**
 * Volumetric-looking cumulus clouds drawn with ordinary geometry (no shader pack needed).
 *
 * <p>The sky is split into 16-block cells. Layered noise decides which cells are cloudy and how
 * thick they are; each cloudy cell gets a flat-bottomed body plus a few puffs on top. Faces are
 * shaded like a lit volume (bright tops, grey sides darkening towards a flat dark base) and fade
 * out towards the edge of the draw radius. The mesh lives in a GPU buffer that is only rebuilt
 * when the camera crosses cells or the weather changes; wind drift is a per-frame offset. Colour
 * follows the vanilla cloud colour, so sunsets, night and storms tint the clouds naturally.</p>
 */
public final class RealisticClouds {
    private static final int CELL = 16;
    private static final SimplexNoise NOISE = new SimplexNoise(RandomSource.create(0x5EEDC10DL));
    private static final float ALPHA = 0.88F;

    @Nullable
    private static VertexBuffer buffer;
    private static int originCellX;
    private static int originCellZ;
    private static float builtCoverage = -1.0F;
    private static int builtThickness;
    private static int builtRadius;

    private RealisticClouds() {
    }

    /** Called instead of vanilla cloud rendering. Returns false to let vanilla draw. */
    public static boolean render(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Matrix4f projection,
                                 double camX, double camY, double camZ) {
        if (!RealConfig.REALISTIC_CLOUDS.on() || Float.isNaN(level.effects().getCloudHeight())) {
            return false;
        }
        double wind = (ticks + partialTick) * 0.05 * RealConfig.CLOUD_SPEED.val();
        int camCellX = Mth.floor((camX - wind) / CELL);
        int camCellZ = Mth.floor(camZ / CELL);

        float rain = level.getRainLevel(partialTick);
        float thunder = level.getThunderLevel(partialTick);
        float clear = RealConfig.CLOUD_COVERAGE.valF();
        float coverage = Mth.clamp(Mth.lerp(rain, clear, Math.max(clear, 0.8F)) + thunder * 0.1F, 0.0F, 1.0F);
        int thickness = RealConfig.CLOUD_THICKNESS.val();
        int radius = RealConfig.CLOUD_DISTANCE.val();

        if (buffer == null || Math.abs(camCellX - originCellX) > 1 || Math.abs(camCellZ - originCellZ) > 1
                || Math.abs(coverage - builtCoverage) > 0.03F || thickness != builtThickness || radius != builtRadius) {
            rebuild(camCellX, camCellZ, coverage, thickness, radius);
        }
        if (buffer == null) {
            return true;
        }

        Vec3 color = level.getCloudColor(partialTick);
        float stormDark = 1.0F - thunder * 0.25F;
        double base = RealConfig.CLOUD_HEIGHT.val() - rain * 16.0;

        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor((float) color.x * stormDark, (float) color.y * stormDark, (float) color.z * stormDark, 1.0F);

        poseStack.pushPose();
        poseStack.translate(originCellX * (double) CELL + wind - camX, base - camY, originCellZ * (double) CELL - camZ);
        buffer.bind();
        // Depth-only pass first so overlapping translucent faces don't stack up (same trick as vanilla).
        for (int pass = 0; pass < 2; pass++) {
            boolean color0 = pass == 1;
            RenderSystem.colorMask(color0, color0, color0, color0);
            buffer.drawWithShader(poseStack.last().pose(), projection, RenderSystem.getShader());
        }
        VertexBuffer.unbind();
        poseStack.popPose();

        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        return true;
    }

    private static void rebuild(int cellX, int cellZ, float coverage, int thickness, int radius) {
        originCellX = cellX;
        originCellZ = cellZ;
        builtCoverage = coverage;
        builtThickness = thickness;
        builtRadius = radius;

        int size = radius * 2 + 1;
        float[] bottom = new float[size * size];
        float[] top = new float[size * size];
        float[] fill = new float[size * size];
        float threshold = 1.0F - coverage;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int gx = cellX + i - radius;
                int gz = cellZ + j - radius;
                double density = density(gx, gz);
                int k = i * size + j;
                if (coverage <= 0.0F || density <= threshold) {
                    fill[k] = 0.0F;
                    continue;
                }
                float q = (float) ((density - threshold) / Math.max(0.05, 1.0 - threshold));
                fill[k] = Mth.clamp(q, 0.0F, 1.0F);
                bottom[k] = (random(gx, gz, 0) - 0.5F) * 3.0F;
                top[k] = bottom[k] + Math.max(3.0F, thickness * (0.2F + 0.35F * fill[k]));
            }
        }

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float fadeEnd = radius * CELL;
        float fadeStart = fadeEnd * 0.55F;
        Mesh mesh = new Mesh(builder, fadeStart, fadeEnd);
        mesh.quad(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0F, 0.0F); // keeps the buffer non-empty on a clear sky

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int k = i * size + j;
                if (fill[k] <= 0.0F) {
                    continue;
                }
                int gx = cellX + i - radius;
                int gz = cellZ + j - radius;
                float x0 = (i - radius) * CELL;
                float z0 = (j - radius) * CELL;
                // Body: hide side faces fully covered by an equally tall neighbour.
                mesh.box(x0, bottom[k], z0, x0 + CELL, top[k], z0 + CELL,
                        !covers(fill, top, size, i, j - 1, top[k]),
                        !covers(fill, top, size, i, j + 1, top[k]),
                        !covers(fill, top, size, i - 1, j, top[k]),
                        !covers(fill, top, size, i + 1, j, top[k]));

                // Puffs piled on top give the cauliflower cumulus silhouette.
                int puffs = 1 + (random(gx, gz, 1) > 0.5F ? 1 : 0) + (fill[k] > 0.55F ? 1 : 0);
                for (int p = 0; p < puffs; p++) {
                    float puff = CELL * (0.45F + 0.3F * random(gx, gz, 10 + p));
                    float px = x0 + random(gx, gz, 20 + p) * (CELL - puff);
                    float pz = z0 + random(gx, gz, 30 + p) * (CELL - puff);
                    float height = thickness * fill[k] * (0.25F + 0.45F * random(gx, gz, 40 + p));
                    if (height < 2.0F) {
                        continue;
                    }
                    mesh.box(px, top[k] - 1.0F, pz, px + puff, top[k] + height, pz + puff, true, true, true, true);
                }
            }
        }

        BufferBuilder.RenderedBuffer rendered = builder.end();
        if (buffer != null) {
            buffer.close();
        }
        buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(rendered);
        VertexBuffer.unbind();
    }

    private static boolean covers(float[] fill, float[] top, int size, int i, int j, float height) {
        if (i < 0 || j < 0 || i >= size || j >= size) {
            return false;
        }
        int k = i * size + j;
        return fill[k] > 0.0F && top[k] >= height;
    }

    /** Layered noise in [0, 1] for a cloud cell. */
    private static double density(int gx, int gz) {
        double x = gx * 0.11;
        double z = gz * 0.11;
        double value = 0.55 * NOISE.getValue(x * 0.35, z * 0.35)
                + 0.30 * NOISE.getValue(x * 0.9 + 17.3, z * 0.9 - 4.1)
                + 0.15 * NOISE.getValue(x * 2.1 - 9.7, z * 2.1 + 3.3);
        return 0.5 + 0.5 * value;
    }

    /** Stable pseudo-random value in [0, 1) for a cell and a salt. */
    private static float random(int gx, int gz, int salt) {
        long h = gx * 0x9E3779B97F4A7C15L ^ gz * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }

    /** Emits shaded, distance-faded quads. */
    private record Mesh(BufferBuilder builder, float fadeStart, float fadeEnd) {
        private static final float TOP = 1.0F;
        private static final float BOTTOM = 0.6F;
        private static final float SIDE_NS = 0.86F;
        private static final float SIDE_EW = 0.78F;
        private static final float SIDE_BASE = 0.75F; // sides darken towards the base

        void box(float x0, float y0, float z0, float x1, float y1, float z1,
                 boolean north, boolean south, boolean west, boolean east) {
            // top and bottom
            quad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, TOP, TOP);
            quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, BOTTOM, BOTTOM);
            if (north) {
                quad(x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, SIDE_NS * SIDE_BASE, SIDE_NS);
            }
            if (south) {
                quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, SIDE_NS * SIDE_BASE, SIDE_NS);
            }
            if (west) {
                quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, SIDE_EW * SIDE_BASE, SIDE_EW);
            }
            if (east) {
                quad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, SIDE_EW * SIDE_BASE, SIDE_EW);
            }
        }

        /** Four corners; corners on the lower edge of the quad get {@code lowShade}, the rest {@code highShade}. */
        void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                  float dx, float dy, float dz, float lowShade, float highShade) {
            float minY = Math.min(Math.min(ay, by), Math.min(cy, dy));
            vertex(ax, ay, az, ay <= minY ? lowShade : highShade);
            vertex(bx, by, bz, by <= minY ? lowShade : highShade);
            vertex(cx, cy, cz, cy <= minY ? lowShade : highShade);
            vertex(dx, dy, dz, dy <= minY ? lowShade : highShade);
        }

        private void vertex(float x, float y, float z, float shade) {
            float distance = Mth.sqrt(x * x + z * z);
            float alpha = ALPHA * Mth.clamp((fadeEnd - distance) / Math.max(1.0F, fadeEnd - fadeStart), 0.0F, 1.0F);
            builder.vertex(x, y, z).color(shade, shade, shade, alpha).endVertex();
        }
    }
}
