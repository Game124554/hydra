package com.realcraft.realism.client;

import com.realcraft.realism.RealCraft;
import com.realcraft.realism.config.RealConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Makes the Nether and the overworld hell layer feel like hell: thick blood-red fog, drifting
 * ash, rising embers and distant rumbles.
 */
@Mod.EventBusSubscriber(modid = RealCraft.MOD_ID, value = Dist.CLIENT)
public final class HellAtmosphere {
    private static final SoundEvent[] AMBIENCE = {
            SoundEvent.createVariableRangeEvent(new ResourceLocation("minecraft", "ambient.nether_wastes.additions")),
            SoundEvent.createVariableRangeEvent(new ResourceLocation("minecraft", "ambient.basalt_deltas.additions")),
            SoundEvent.createVariableRangeEvent(new ResourceLocation("minecraft", "ambient.soul_sand_valley.additions")),
            SoundEvent.createVariableRangeEvent(new ResourceLocation("minecraft", "ambient.crimson_forest.mood"))
    };

    /** 0 = normal world, 1 = fully in hell. Eased towards the target every tick. */
    private static float hell;
    private static float prevHell;

    private HellAtmosphere() {
    }

    private static float target(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            return 0.0F;
        }
        if (level.dimension() == Level.NETHER) {
            return 1.0F;
        }
        if (level.dimension() == Level.OVERWORLD && RealConfig.HELL_LAYER.on()) {
            double y = mc.gameRenderer.getMainCamera().getPosition().y;
            return Mth.clamp((float) (RealConfig.HELL_TOP_Y.val() + 4 - y) / 12.0F, 0.0F, 1.0F);
        }
        return 0.0F;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        prevHell = hell;
        hell += (target(mc) - hell) * 0.05F;
        if (mc.level == null || mc.player == null || mc.isPaused() || hell < 0.05F) {
            return;
        }
        RandomSource random = mc.level.random;
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();

        if (RealConfig.AMBIENT_PARTICLES.on()) {
            float budget = hell * 4.0F * RealConfig.PARTICLE_DENSITY.valF();
            int count = (int) budget + (random.nextFloat() < budget - (int) budget ? 1 : 0);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int i = 0; i < count; i++) {
                double x = camera.x + (random.nextDouble() - 0.5) * 24.0;
                double y = camera.y + (random.nextDouble() - 0.4) * 12.0;
                double z = camera.z + (random.nextDouble() - 0.5) * 24.0;
                if (!mc.level.getBlockState(pos.set(x, y, z)).isAir()) {
                    continue;
                }
                int kind = random.nextInt(10);
                if (kind < 6) {
                    mc.level.addParticle(ParticleTypes.ASH, x, y, z, 0.0, 0.0, 0.0);
                } else if (kind < 9) {
                    mc.level.addParticle(ParticleTypes.SMALL_FLAME, x, y, z,
                            (random.nextDouble() - 0.5) * 0.01, 0.02 + random.nextDouble() * 0.03, (random.nextDouble() - 0.5) * 0.01);
                } else {
                    mc.level.addParticle(ParticleTypes.LAVA, x, y, z, 0.0, 0.0, 0.0);
                }
            }
        }

        // The Nether has its own ambience; the hell layer borrows it.
        if (RealConfig.HELL_SOUNDS.on() && mc.level.dimension() == Level.OVERWORLD && hell > 0.5F && random.nextInt(240) == 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forAmbientAddition(AMBIENCE[random.nextInt(AMBIENCE.length)]));
        }
    }

    private static float hellAmount(double partialTick) {
        return Mth.lerp((float) partialTick, prevHell, hell);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        float amount = hellAmount(event.getPartialTick());
        if (!RealConfig.HELL_FOG.on() || amount < 0.01F || event.getType() != FogType.NONE) {
            return;
        }
        float density = RealConfig.HELL_FOG_DENSITY.valF();
        if (density <= 0.0F) {
            return;
        }
        float hellFar = 72.0F / density;
        float far = Mth.lerp(amount, event.getFarPlaneDistance(), Math.min(event.getFarPlaneDistance(), hellFar));
        float near = Math.min(event.getNearPlaneDistance(), far * 0.05F);
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(near);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        Camera camera = event.getCamera();
        float amount = hellAmount(event.getPartialTick());
        if (!RealConfig.HELL_FOG.on() || amount < 0.01F || camera.getFluidInCamera() != FogType.NONE) {
            return;
        }
        float blend = amount * 0.85F;
        event.setRed(Mth.lerp(blend, event.getRed(), 0.45F));
        event.setGreen(Mth.lerp(blend, event.getGreen(), 0.07F));
        event.setBlue(Mth.lerp(blend, event.getBlue(), 0.03F));
    }
}
