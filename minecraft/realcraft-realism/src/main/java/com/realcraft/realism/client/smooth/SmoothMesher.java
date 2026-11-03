package com.realcraft.realism.client.smooth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.realcraft.realism.config.RealConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Arrays;
import java.util.List;

/**
 * Surface-nets mesher used in place of the cube model for smoothable blocks.
 *
 * <p>Every block is a density sample at its centre (1 = smoothable, 0 = anything else). Each cell
 * between eight block centres that straddles the surface gets one vertex: the average of the
 * points where its edges cross the surface. Every exposed face of a smoothable block is replaced
 * by a quad joining the vertices of the four cells around that face. Vertices only depend on the
 * blocks around them, so neighbouring faces and chunk sections meet without cracks, and flat
 * ground stays exactly flat.</p>
 *
 * <p>Runs on chunk-builder threads; all scratch state is thread-local.</p>
 */
public final class SmoothMesher {
    private static final ThreadLocal<Context> CONTEXT = ThreadLocal.withInitial(Context::new);
    private static final Direction[] DIRECTIONS = Direction.values();
    /** Corner index pairs (bit 0 = x, bit 1 = y, bit 2 = z) for the 12 edges of a cell. */
    private static final int[][] EDGES = {
            {0, 1}, {2, 3}, {4, 5}, {6, 7},
            {0, 2}, {1, 3}, {4, 6}, {5, 7},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };
    /** Corner order (u, v) of a face quad, counter-clockwise seen from outside. */
    private static final int[][] POSITIVE_ORDER = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
    private static final int[][] NEGATIVE_ORDER = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};

    private SmoothMesher() {
    }

    public static void renderBlock(BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack poseStack,
                                   VertexConsumer consumer, RandomSource random, ModelData modelData, RenderType renderType) {
        Context ctx = CONTEXT.get();
        ctx.begin(level, pos);
        float smoothness = RealConfig.SMOOTHNESS.valF();
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        long seed = state.getSeed(pos);
        Matrix4f pose = poseStack.last().pose();

        for (Direction face : DIRECTIONS) {
            if (ctx.solid(pos.getX() + face.getStepX(), pos.getY() + face.getStepY(), pos.getZ() + face.getStepZ())) {
                continue;
            }
            emitFace(ctx, face, state, pos, level, pose, consumer, random, seed, modelData, renderType, model, smoothness);
        }
    }

    private static void emitFace(Context ctx, Direction face, BlockState state, BlockPos pos, BlockAndTintGetter level,
                                 Matrix4f pose, VertexConsumer consumer, RandomSource random, long seed, ModelData modelData,
                                 RenderType renderType, BakedModel model, float smoothness) {
        int a = face.getAxis().ordinal(); // X=0, Y=1, Z=2
        int u = (a + 1) % 3;
        int v = (a + 2) % 3;
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        int[][] order = positive ? POSITIVE_ORDER : NEGATIVE_ORDER;
        int[] p = {pos.getX(), pos.getY(), pos.getZ()};
        int[] m = new int[3];
        float[][] verts = ctx.verts;
        int[] light = ctx.light;
        float[] ao = ctx.ao;

        for (int k = 0; k < 4; k++) {
            m[a] = positive ? p[a] : p[a] - 1;
            m[u] = p[u] - 1 + order[k][0];
            m[v] = p[v] - 1 + order[k][1];
            int cell = ctx.cell(m[0], m[1], m[2], smoothness);
            verts[k][0] = ctx.cellX + m[0] - p[0];
            verts[k][1] = ctx.cellY + m[1] - p[1];
            verts[k][2] = ctx.cellZ + m[2] - p[2];
            light[k] = ctx.cellLight;
            ao[k] = ctx.cellAo;
            if (cell < 0) {
                return;
            }
        }

        // Face normal from the quad diagonals.
        float d1x = verts[2][0] - verts[0][0], d1y = verts[2][1] - verts[0][1], d1z = verts[2][2] - verts[0][2];
        float d2x = verts[3][0] - verts[1][0], d2y = verts[3][1] - verts[1][1], d2z = verts[3][2] - verts[1][2];
        float nx = d1y * d2z - d1z * d2y;
        float ny = d1z * d2x - d1x * d2z;
        float nz = d1x * d2y - d1y * d2x;
        float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0E-6F) {
            return; // collapsed face
        }
        nx /= length;
        ny /= length;
        nz /= length;

        Direction textureFace = Direction.getNearest(nx, ny, nz);
        random.setSeed(seed);
        List<BakedQuad> quads = model.getQuads(state, textureFace, random, modelData, renderType);
        if (quads.isEmpty()) {
            random.setSeed(seed);
            quads = model.getQuads(state, face, random, modelData, renderType);
        }
        if (quads.isEmpty()) {
            random.setSeed(seed);
            quads = model.getQuads(state, null, random, modelData, renderType);
        }
        if (quads.isEmpty()) {
            return;
        }

        float shade = nx * nx * level.getShade(nx > 0 ? Direction.EAST : Direction.WEST, true)
                + ny * ny * level.getShade(ny > 0 ? Direction.UP : Direction.DOWN, true)
                + nz * nz * level.getShade(nz > 0 ? Direction.SOUTH : Direction.NORTH, true);

        projectUvs(ctx, textureFace);

        for (BakedQuad quad : quads) {
            float r = 1.0F, g = 1.0F, b = 1.0F;
            if (quad.isTinted()) {
                int color = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, quad.getTintIndex());
                r = (color >> 16 & 255) / 255.0F;
                g = (color >> 8 & 255) / 255.0F;
                b = (color & 255) / 255.0F;
            }
            float quadShade = quad.isShade() ? shade : 1.0F;
            TextureAtlasSprite sprite = quad.getSprite();
            for (int k = 0; k < 4; k++) {
                Vector4f pos4 = ctx.tmp.set(verts[k][0], verts[k][1], verts[k][2], 1.0F);
                pose.transform(pos4);
                float brightness = quadShade * ao[k];
                consumer.vertex(pos4.x(), pos4.y(), pos4.z(),
                        r * brightness, g * brightness, b * brightness, 1.0F,
                        sprite.getU(ctx.uvs[k][0] * 16.0), sprite.getV(ctx.uvs[k][1] * 16.0),
                        OverlayTexture.NO_OVERLAY, light[k], nx, ny, nz);
            }
        }
    }

    /** Planar texture projection onto the plane of {@code face}, shifted so the quad stays inside one tile. */
    private static void projectUvs(Context ctx, Direction face) {
        float[][] verts = ctx.verts;
        float[][] uvs = ctx.uvs;
        for (int k = 0; k < 4; k++) {
            float x = verts[k][0], y = verts[k][1], z = verts[k][2];
            float tu, tv;
            switch (face) {
                case DOWN -> { tu = x; tv = 1 - z; }
                case UP -> { tu = x; tv = z; }
                case NORTH -> { tu = 1 - x; tv = 1 - y; }
                case SOUTH -> { tu = x; tv = 1 - y; }
                case WEST -> { tu = z; tv = 1 - y; }
                default -> { tu = 1 - z; tv = 1 - y; }
            }
            uvs[k][0] = tu;
            uvs[k][1] = tv;
        }
        fitAxis(uvs, 0);
        fitAxis(uvs, 1);
    }

    private static void fitAxis(float[][] uvs, int axis) {
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (float[] uv : uvs) {
            min = Math.min(min, uv[axis]);
            max = Math.max(max, uv[axis]);
        }
        float span = max - min;
        float scale = span > 1.0F ? 1.0F / span : 1.0F;
        float shift = 0.0F;
        if (span <= 1.0F) {
            if (min < 0.0F) {
                shift = -min;
            } else if (max > 1.0F) {
                shift = 1.0F - max;
            }
        }
        for (float[] uv : uvs) {
            float value = span > 1.0F ? (uv[axis] - min) * scale : uv[axis] + shift;
            uv[axis] = Mth.clamp(value, 0.0F, 1.0F);
        }
    }

    /** Per-thread caches for the chunk section currently being built. */
    private static final class Context {
        private static final int SIZE = 20; // section plus a two-block margin on each side
        private static final int VOLUME = SIZE * SIZE * SIZE;

        final float[][] verts = new float[4][3];
        final float[][] uvs = new float[4][2];
        final int[] light = new int[4];
        final float[] ao = new float[4];
        final Vector4f tmp = new Vector4f();
        private final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        private final int[] solidStamp = new int[VOLUME];
        private final boolean[] solidValue = new boolean[VOLUME];
        private final int[] lightStamp = new int[VOLUME];
        private final int[] lightValue = new int[VOLUME];
        private final int[] cellStamp = new int[VOLUME];
        private final float[] cellPos = new float[VOLUME * 3];
        private final int[] cellLightValue = new int[VOLUME];
        private final float[] cellAoValue = new float[VOLUME];

        private BlockAndTintGetter level;
        private int originX, originY, originZ;
        private int stamp = 1;

        // Results of the last cell() call.
        float cellX, cellY, cellZ;
        int cellLight;
        float cellAo;

        void begin(BlockAndTintGetter level, BlockPos pos) {
            int ox = (pos.getX() & ~15) - 2;
            int oy = (pos.getY() & ~15) - 2;
            int oz = (pos.getZ() & ~15) - 2;
            if (level != this.level || ox != originX || oy != originY || oz != originZ) {
                this.level = level;
                originX = ox;
                originY = oy;
                originZ = oz;
                if (++stamp == 0) {
                    Arrays.fill(solidStamp, 0);
                    Arrays.fill(lightStamp, 0);
                    Arrays.fill(cellStamp, 0);
                    stamp = 1;
                }
            }
        }

        private int index(int x, int y, int z) {
            int lx = x - originX, ly = y - originY, lz = z - originZ;
            if (lx < 0 || ly < 0 || lz < 0 || lx >= SIZE || ly >= SIZE || lz >= SIZE) {
                return -1;
            }
            return (lx * SIZE + ly) * SIZE + lz;
        }

        boolean solid(int x, int y, int z) {
            int i = index(x, y, z);
            if (i < 0) {
                return SmoothTerrain.isSmoothable(level.getBlockState(mutable.set(x, y, z)));
            }
            if (solidStamp[i] != stamp) {
                solidStamp[i] = stamp;
                solidValue[i] = SmoothTerrain.isSmoothable(level.getBlockState(mutable.set(x, y, z)));
            }
            return solidValue[i];
        }

        private int lightAt(int x, int y, int z) {
            int i = index(x, y, z);
            if (i < 0) {
                return LevelRenderer.getLightColor(level, mutable.set(x, y, z));
            }
            if (lightStamp[i] != stamp) {
                lightStamp[i] = stamp;
                lightValue[i] = LevelRenderer.getLightColor(level, mutable.set(x, y, z));
            }
            return lightValue[i];
        }

        /**
         * Computes (or fetches) the vertex of the cell whose lowest sample is block (mx, my, mz).
         * Writes the position (relative to that block), light and occlusion into the cell* fields.
         * Returns -1 if the cell does not straddle the surface.
         */
        int cell(int mx, int my, int mz, float smoothness) {
            int i = index(mx, my, mz);
            if (i >= 0 && cellStamp[i] == stamp) {
                cellX = cellPos[i * 3];
                cellY = cellPos[i * 3 + 1];
                cellZ = cellPos[i * 3 + 2];
                cellLight = cellLightValue[i];
                cellAo = cellAoValue[i];
                return i;
            }

            boolean[] corner = new boolean[8];
            int solidCount = 0;
            int sky = 0, block = 0;
            boolean anyOpen = false;
            for (int c = 0; c < 8; c++) {
                int x = mx + (c & 1), y = my + (c >> 1 & 1), z = mz + (c >> 2 & 1);
                corner[c] = solid(x, y, z);
                if (corner[c]) {
                    solidCount++;
                } else {
                    int packed = lightAt(x, y, z);
                    sky = Math.max(sky, LightTexture.sky(packed));
                    block = Math.max(block, LightTexture.block(packed));
                    anyOpen = true;
                }
            }

            float sx = 0, sy = 0, sz = 0;
            int crossings = 0;
            for (int[] edge : EDGES) {
                int c0 = edge[0], c1 = edge[1];
                if (corner[c0] != corner[c1]) {
                    sx += ((c0 & 1) + (c1 & 1)) * 0.5F;
                    sy += ((c0 >> 1 & 1) + (c1 >> 1 & 1)) * 0.5F;
                    sz += ((c0 >> 2 & 1) + (c1 >> 2 & 1)) * 0.5F;
                    crossings++;
                }
            }
            if (crossings == 0) {
                cellX = cellY = cellZ = 1.0F;
                cellLight = anyOpen ? LightTexture.pack(block, sky) : LightTexture.FULL_BRIGHT;
                cellAo = 1.0F;
                return -1;
            }
            // Blend between the cube corner (m + 1) and the surface-nets vertex (m + 0.5 + average).
            cellX = 1.0F + smoothness * (sx / crossings - 0.5F);
            cellY = 1.0F + smoothness * (sy / crossings - 0.5F);
            cellZ = 1.0F + smoothness * (sz / crossings - 0.5F);
            cellLight = anyOpen ? LightTexture.pack(block, sky) : lightAt(mx + 1, my + 1, mz + 1);
            cellAo = Mth.clamp(1.0F - (solidCount - 4) * 0.1F, 0.55F, 1.0F);

            if (i >= 0) {
                cellStamp[i] = stamp;
                cellPos[i * 3] = cellX;
                cellPos[i * 3 + 1] = cellY;
                cellPos[i * 3 + 2] = cellZ;
                cellLightValue[i] = cellLight;
                cellAoValue[i] = cellAo;
            }
            return Math.max(i, 0);
        }
    }
}
