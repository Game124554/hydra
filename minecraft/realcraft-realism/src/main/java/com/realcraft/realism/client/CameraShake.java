package com.realcraft.realism.client;

import com.realcraft.realism.RealCraft;
import com.realcraft.realism.config.RealConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Trauma-based camera shake. Events add "trauma" (0..1) which decays over time; the shake is
 * trauma squared, so small bumps are subtle and big blasts are violent. Explosions and other
 * loud events are detected from the sounds the client plays, so it also works on servers that
 * don't run the mod.
 */
@Mod.EventBusSubscriber(modid = RealCraft.MOD_ID, value = Dist.CLIENT)
public final class CameraShake {
    private static float trauma;
    private static int ticks;
    private static float lastHealth = -1.0F;
    private static float lastFallDistance;
    private static boolean wasOnGround = true;

    private CameraShake() {
    }

    public static void addTrauma(float amount) {
        if (RealConfig.CAMERA_SHAKE.on()) {
            trauma = Mth.clamp(trauma + amount, 0.0F, 1.0F);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            trauma = 0.0F;
            lastHealth = -1.0F;
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        ticks++;
        trauma = Math.max(0.0F, trauma - 0.035F);
        if (!RealConfig.CAMERA_SHAKE.on()) {
            trauma = 0.0F;
            return;
        }

        float health = player.getHealth();
        if (RealConfig.SHAKE_DAMAGE.on() && lastHealth >= 0.0F && health < lastHealth && player.isAlive()) {
            addTrauma(Math.min(0.7F, (lastHealth - health) / 10.0F + 0.15F));
        }
        lastHealth = health;

        boolean onGround = player.onGround();
        if (RealConfig.SHAKE_LANDING.on() && onGround && !wasOnGround && lastFallDistance > 3.0F && !player.isInWater()) {
            addTrauma(Math.min(0.8F, (lastFallDistance - 3.0F) * 0.06F + 0.15F));
        }
        lastFallDistance = player.fallDistance;
        wasOnGround = onGround;

        if (RealConfig.SHAKE_ENVIRONMENT.on() && ticks % 5 == 0) {
            int falling = mc.level.getEntitiesOfClass(FallingBlockEntity.class, player.getBoundingBox().inflate(16.0)).size();
            if (falling > 0) {
                addTrauma(Math.min(0.25F, falling * 0.02F));
            }
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (trauma <= 0.0F || !RealConfig.CAMERA_SHAKE.on()) {
            return;
        }
        float shake = trauma * trauma * RealConfig.SHAKE_INTENSITY.valF();
        float t = ticks + (float) event.getPartialTick();
        event.setYaw(event.getYaw() + 5.0F * shake * noise(t, 0));
        event.setPitch(event.getPitch() + 5.0F * shake * noise(t, 1));
        event.setRoll(event.getRoll() + 7.0F * shake * noise(t, 2));
    }

    @SubscribeEvent
    public static void onSound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || !RealConfig.CAMERA_SHAKE.on()) {
            return;
        }
        String id = sound.getLocation().getPath();
        float strength;
        float range;
        boolean explosion = false;
        switch (id) {
            case "entity.generic.explode" -> { strength = 1.0F; range = 48.0F; explosion = true; }
            case "entity.dragon_fireball.explode" -> { strength = 0.5F; range = 32.0F; explosion = true; }
            case "entity.firework_rocket.large_blast", "entity.firework_rocket.large_blast_far" -> { strength = 0.15F; range = 24.0F; explosion = true; }
            case "entity.lightning_bolt.thunder" -> { strength = 0.3F; range = 512.0F; }
            case "entity.lightning_bolt.impact" -> { strength = 0.6F; range = 32.0F; }
            case "entity.warden.sonic_boom" -> { strength = 0.6F; range = 24.0F; }
            case "entity.wither.spawn", "entity.ender_dragon.death" -> { strength = 0.8F; range = 1024.0F; }
            case "entity.ravager.roar", "entity.ender_dragon.growl", "entity.wither.break_block" -> { strength = 0.35F; range = 24.0F; }
            case "block.anvil.land" -> { strength = 0.15F; range = 8.0F; }
            default -> {
                return;
            }
        }
        if (explosion ? !RealConfig.SHAKE_EXPLOSIONS.on() : !RealConfig.SHAKE_ENVIRONMENT.on()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float falloff = 1.0F;
        if (!sound.isRelative()) {
            Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
            double distance = camera.distanceTo(new Vec3(sound.getX(), sound.getY(), sound.getZ()));
            falloff = 1.0F - (float) (distance / range);
        }
        if (falloff > 0.0F) {
            addTrauma(strength * falloff);
        }
    }

    /** Smooth pseudo-noise in [-1, 1] from a few incommensurate sine waves. */
    private static float noise(float t, int channel) {
        float phase = channel * 1.7F;
        return (Mth.sin(t * 1.9F + phase) + 0.6F * Mth.sin(t * 3.7F + phase * 2.3F) + 0.3F * Mth.sin(t * 7.3F + phase * 0.7F)) / 1.9F;
    }
}
