package com.realcraft.realism.world;

import com.realcraft.realism.RealCraft;
import com.realcraft.realism.config.RealConfig;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Smoothed terrain looks like slopes, so players step up full blocks instead of having to jump.
 * Applied on the server; Forge syncs the attribute to the client.
 */
@Mod.EventBusSubscriber(modid = RealCraft.MOD_ID)
public final class StepAssist {
    private static final UUID STEP_ID = UUID.fromString("2f8f2a7c-3b0e-4b4e-8d7c-71c6b9a1e0d4");
    private static final AttributeModifier STEP = new AttributeModifier(STEP_ID, "realcraft:step_assist", 0.5, AttributeModifier.Operation.ADDITION);

    private StepAssist() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide || event.player.tickCount % 20 != 0) {
            return;
        }
        AttributeInstance step = event.player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
        if (step == null) {
            return;
        }
        boolean wanted = RealConfig.STEP_ASSIST.on();
        boolean present = step.getModifier(STEP_ID) != null;
        if (wanted && !present) {
            step.addTransientModifier(STEP);
        } else if (!wanted && present) {
            step.removeModifier(STEP_ID);
        }
    }
}
