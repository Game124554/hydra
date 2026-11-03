package com.realcraft.realism.client;

import com.realcraft.realism.RealCraft;
import com.realcraft.realism.config.RealConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Realistic water without shaders: natural blue-green tint, underwater visibility that depends
 * on depth, daylight and murky biomes, sunlight glinting on open water and mist where waterfalls
 * land.
 */
@Mod.EventBusSubscriber(modid = RealCraft.MOD_ID, value = Dist.CLIENT)
public final class RealisticWater {
    private static final int SCAN_RADIUS = 16;
    private static final int MAX_SPOTS = 96;
    private static final List<Vec3> SURFACES = new ArrayList<>();
    private static final List<BlockPos> WATERFALLS = new ArrayList<>();
    private static int ticks;

    private RealisticWater() {
    }

    /** Blends a biome water colour towards a deep natural blue-green and darkens it slightly. */
    public static int naturalTint(int rgb) {
        int r = (int) (Mth.lerp(0.35F, rgb >> 16 & 255, 0x22) * 0.92F);
        int g = (int) (Mth.lerp(0.35F, rgb >> 8 & 255, 0x6A) * 0.92F);
        int b = (int) (Mth.lerp(0.35F, rgb & 255, 0x7F) * 0.92F);
        return (rgb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!RealConfig.UNDERWATER_VISIBILITY.on() || event.getType() != FogType.WATER) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        BlockPos pos = BlockPos.containing(event.getCamera().getPosition());
        float clarity = Mth.lerp(Mth.clamp(waterAbove(level, pos) / 40.0F, 0.0F, 1.0F), 1.0F, 0.35F);
        if (level.getBiome(pos).is(Biomes.SWAMP) || level.getBiome(pos).is(Biomes.MANGROVE_SWAMP)) {
            clarity *= 0.45F;
        }
        clarity *= 0.45F + 0.55F * level.getSkyDarken((float) event.getPartialTick());
        if (level.isRaining()) {
            clarity *= 0.85F;
        }
        event.setFarPlaneDistance(event.getFarPlaneDistance() * clarity);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        Camera camera = event.getCamera();
        ClientLevel level = Minecraft.getInstance().level;
        if (!RealConfig.UNDERWATER_VISIBILITY.on() || level == null || camera.getFluidInCamera() != FogType.WATER) {
            return;
        }
        float depth = Mth.clamp(waterAbove(level, BlockPos.containing(camera.getPosition())) / 48.0F, 0.0F, 1.0F);
        float darken = 1.0F - 0.7F * depth;
        event.setRed(event.getRed() * darken * 0.85F);
        event.setGreen(event.getGreen() * darken);
        event.setBlue(event.getBlue() * darken);
    }

    /** Number of water blocks above (and including) {@code pos}, up to 48. */
    private static int waterAbove(ClientLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        int depth = 0;
        while (depth < 48 && level.getFluidState(cursor).is(FluidTags.WATER)) {
            depth++;
            cursor.move(0, 1, 0);
        }
        return depth;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused() || (!RealConfig.WATER_GLINTS.on() && !RealConfig.WATERFALL_MIST.on())) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        if (ticks++ % 20 == 0) {
            scan(level, BlockPos.containing(camera.getPosition()));
        }
        RandomSource random = level.random;
        float density = RealConfig.PARTICLE_DENSITY.valF();

        if (RealConfig.WATER_GLINTS.on() && !SURFACES.isEmpty() && level.isDay() && !level.isRaining()
                && camera.getFluidInCamera() == FogType.NONE) {
            float attempts = 3.0F * density;
            for (int i = 0; i < attempts; i++) {
                Vec3 surface = SURFACES.get(random.nextInt(SURFACES.size()));
                if (random.nextFloat() < 0.6F) {
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                            surface.x + random.nextDouble(), surface.y + 0.02, surface.z + random.nextDouble(), 0.0, 0.0, 0.0);
                }
            }
        }

        if (RealConfig.WATERFALL_MIST.on()) {
            for (BlockPos base : WATERFALLS) {
                if (random.nextFloat() < 0.3F * density) {
                    level.addParticle(ParticleTypes.CLOUD,
                            base.getX() + random.nextDouble(), base.getY() + 0.9, base.getZ() + random.nextDouble(),
                            (random.nextDouble() - 0.5) * 0.04, 0.02 + random.nextDouble() * 0.03, (random.nextDouble() - 0.5) * 0.04);
                }
                if (random.nextFloat() < 0.5F * density) {
                    level.addParticle(ParticleTypes.SPLASH,
                            base.getX() + random.nextDouble(), base.getY() + 1.0, base.getZ() + random.nextDouble(),
                            (random.nextDouble() - 0.5) * 0.3, 0.2, (random.nextDouble() - 0.5) * 0.3);
                }
            }
        }
    }

    /** Finds open water surfaces (for glints) and waterfall impact points (for mist) around the camera. */
    private static void scan(ClientLevel level, BlockPos center) {
        SURFACES.clear();
        WATERFALLS.clear();
        RandomSource random = level.random;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
        int seenSurfaces = 0;
        for (int x = center.getX() - SCAN_RADIUS; x <= center.getX() + SCAN_RADIUS; x++) {
            for (int z = center.getZ() - SCAN_RADIUS; z <= center.getZ() + SCAN_RADIUS; z++) {
                boolean surfaceFound = false;
                for (int y = center.getY() + 10; y >= center.getY() - 10; y--) {
                    FluidState fluid = level.getFluidState(pos.set(x, y, z));
                    if (!fluid.is(FluidTags.WATER)) {
                        continue;
                    }
                    if (!surfaceFound) {
                        surfaceFound = true;
                        above.set(x, y + 1, z);
                        if (level.getBlockState(above).isAir() && level.canSeeSky(above)) {
                            // Reservoir sampling keeps an even spread when there is lots of water.
                            seenSurfaces++;
                            Vec3 surface = new Vec3(x, y + fluid.getHeight(level, pos), z);
                            if (SURFACES.size() < MAX_SPOTS) {
                                SURFACES.add(surface);
                            } else if (random.nextInt(seenSurfaces) < MAX_SPOTS) {
                                SURFACES.set(random.nextInt(MAX_SPOTS), surface);
                            }
                        }
                    }
                    if (WATERFALLS.size() < MAX_SPOTS / 3 && isFalling(fluid)) {
                        FluidState below = level.getFluidState(pos.set(x, y - 1, z));
                        if (below.is(FluidTags.WATER) && !isFalling(below)) {
                            WATERFALLS.add(new BlockPos(x, y - 1, z));
                        }
                        pos.set(x, y, z);
                    }
                }
            }
        }
    }

    private static boolean isFalling(FluidState fluid) {
        return fluid.hasProperty(FlowingFluid.FALLING) && fluid.getValue(FlowingFluid.FALLING);
    }
}
