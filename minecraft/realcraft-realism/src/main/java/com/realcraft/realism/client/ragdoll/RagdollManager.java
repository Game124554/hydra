package com.realcraft.realism.client.ragdoll;

import com.realcraft.realism.RealCraft;
import com.realcraft.realism.config.RealConfig;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/** Starts a ragdoll for each creature that dies near the camera and steps them every client tick. */
@Mod.EventBusSubscriber(modid = RealCraft.MOD_ID, value = Dist.CLIENT)
public final class RagdollManager {
    private static final Int2ObjectOpenHashMap<Ragdoll> RAGDOLLS = new Int2ObjectOpenHashMap<>();
    private static ClientLevel trackedLevel;

    private RagdollManager() {
    }

    @Nullable
    public static Ragdoll get(Entity entity) {
        return RAGDOLLS.isEmpty() ? null : RAGDOLLS.get(entity.getId());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != trackedLevel) {
            RAGDOLLS.clear();
            trackedLevel = level;
        }
        if (level == null || mc.isPaused()) {
            return;
        }
        if (!RealConfig.RAGDOLLS.on() || RealConfig.MAX_RAGDOLLS.val() <= 0) {
            RAGDOLLS.clear();
            return;
        }

        RAGDOLLS.values().removeIf(ragdoll -> {
            Entity entity = level.getEntity(ragdoll.entityId);
            return !(entity instanceof LivingEntity living) || living.isRemoved() || !living.isDeadOrDying();
        });

        int max = RealConfig.MAX_RAGDOLLS.val();
        double range = RealConfig.RAGDOLL_DISTANCE.val();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        for (Entity entity : level.entitiesForRendering()) {
            if (RAGDOLLS.size() >= max) {
                break;
            }
            if (entity instanceof LivingEntity living && living.deathTime > 0 && living.isDeadOrDying()
                    && !RAGDOLLS.containsKey(entity.getId()) && canRagdoll(living)
                    && entity.distanceToSqr(camera) < range * range) {
                RAGDOLLS.put(entity.getId(), new Ragdoll(living));
            }
        }

        int iterations = RealConfig.RAGDOLL_ITERATIONS.val();
        for (Ragdoll ragdoll : RAGDOLLS.values()) {
            ragdoll.tick(level, iterations);
        }
    }

    private static boolean canRagdoll(LivingEntity entity) {
        return !(entity instanceof ArmorStand)
                && entity.getBbWidth() <= 4.0F
                && entity.getBbHeight() <= 4.0F
                && entity.getBbHeight() > 0.1F;
    }
}
