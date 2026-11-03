package com.realcraft.realism.zombie;

import com.realcraft.realism.RealCraft;
import com.realcraft.realism.config.RealConfig;
import com.realcraft.realism.network.RealNetwork;
import com.realcraft.realism.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Zombie bites can infect players. The infection slowly worsens (hunger, nausea, weakness,
 * slowness); a golden apple cures it. If it runs its course, or the player dies infected, the
 * player turns into a zombie: they wear a zombie's skin, zombies treat them as one of their own,
 * iron golems hunt them and sunlight burns them unless they wear a helmet. Like a zombie
 * villager, they are cured by being weakened (splash potion of Weakness) and then eating a
 * golden apple; the cure takes a minute to work.
 */
@Mod.EventBusSubscriber(modid = RealCraft.MOD_ID)
public final class ZombieInfection {
    private static final int CURE_TICKS = 1200;

    private ZombieInfection() {
    }

    private static MobEffect infection() {
        return ModEffects.INFECTION.get();
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!RealConfig.ZOMBIE_INFECTION.on() || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getSource().getDirectEntity() instanceof Zombie zombie) || zombie instanceof ZombifiedPiglin) {
            return;
        }
        if (player.isCreative() || player.isSpectator() || ZombifiedState.isZombified(player) || player.hasEffect(infection())) {
            return;
        }
        if (player.getRandom().nextDouble() >= RealConfig.INFECTION_CHANCE.val()) {
            return;
        }
        player.addEffect(new MobEffectInstance(infection(), RealConfig.INFECTION_MINUTES.val() * 1200, 0, false, true, true));
        player.displayClientMessage(Component.translatableWithFallback("realcraft.zombie.bitten",
                "You've been bitten! Eat a golden apple before the infection takes over.").withStyle(ChatFormatting.DARK_GREEN), true);
    }

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        // Fired while the effect list is being iterated, so only flags and messages here.
        MobEffectInstance instance = event.getEffectInstance();
        if (instance != null && instance.getEffect() == infection() && event.getEntity() instanceof ServerPlayer player) {
            zombify(player);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.hasEffect(infection())) {
            ZombifiedState.setZombified(player, true);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        ZombifiedState.copy(event.getOriginal(), event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RealNetwork.syncZombified(player);
            if (ZombifiedState.isZombified(player)) {
                player.displayClientMessage(Component.translatableWithFallback("realcraft.zombie.risen",
                        "You rose again... as a zombie.").withStyle(ChatFormatting.DARK_GREEN), false);
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RealNetwork.syncZombified(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RealNetwork.syncZombified(player);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Player target && event.getEntity() instanceof ServerPlayer watcher
                && ZombifiedState.isZombified(target)) {
            RealNetwork.sendZombified(watcher, target);
        }
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (RealConfig.ZOMBIES_IGNORE_ZOMBIFIED.on() && event.getEntity() instanceof Zombie
                && event.getNewTarget() instanceof Player player && ZombifiedState.isZombified(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFinishUsing(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack item = event.getItem();
        boolean goldenApple = item.is(Items.GOLDEN_APPLE) || item.is(Items.ENCHANTED_GOLDEN_APPLE);
        if (goldenApple && player.hasEffect(infection())) {
            player.removeEffect(infection());
            player.displayClientMessage(Component.translatableWithFallback("realcraft.zombie.infection_cured",
                    "The fever breaks. The infection is gone.").withStyle(ChatFormatting.GREEN), true);
        }
        if (!ZombifiedState.isZombified(player)) {
            return;
        }
        if (goldenApple && player.hasEffect(MobEffects.WEAKNESS) && ZombifiedState.curingTicks(player) == 0) {
            ZombifiedState.setCuringTicks(player, CURE_TICKS);
            player.level().playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatableWithFallback("realcraft.zombie.curing",
                    "The cure is taking hold...").withStyle(ChatFormatting.GREEN), true);
        }
        if (item.is(Items.ROTTEN_FLESH)) {
            // Zombies digest rotten flesh just fine.
            player.removeEffect(MobEffects.HUNGER);
            player.getFoodData().eat(4, 0.6F);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        MobEffectInstance infected = player.getEffect(infection());
        if (infected != null) {
            applySymptoms(player, infected);
        }
        if (!ZombifiedState.isZombified(player)) {
            return;
        }

        int curing = ZombifiedState.curingTicks(player);
        if (curing > 0) {
            curing -= 20;
            if (curing <= 0) {
                cure(player);
                return;
            }
            ZombifiedState.setCuringTicks(player, curing);
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false));
        }

        if (RealConfig.ZOMBIE_SUNBURN.on() && burnsInSunlight(player)) {
            player.setSecondsOnFire(8);
        }
        Level level = player.level();
        if (RealConfig.ZOMBIES_IGNORE_ZOMBIFIED.on()) {
            for (Zombie zombie : level.getEntitiesOfClass(Zombie.class, player.getBoundingBox().inflate(32.0), z -> z.getTarget() == player)) {
                zombie.setTarget(null);
            }
        }
        if (RealConfig.GOLEMS_HUNT_ZOMBIFIED.on() && !player.isCreative() && !player.isSpectator()) {
            for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, player.getBoundingBox().inflate(16.0),
                    g -> g.getTarget() == null && g.hasLineOfSight(player))) {
                golem.setTarget(player);
            }
        }
    }

    private static void applySymptoms(ServerPlayer player, MobEffectInstance infected) {
        int total = Math.max(1, RealConfig.INFECTION_MINUTES.val() * 1200);
        float progress = 1.0F - Math.min(1.0F, infected.getDuration() / (float) total);
        if (progress > 0.35F && player.getRandom().nextFloat() < 0.06F) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0, false, false));
            player.displayClientMessage(Component.translatableWithFallback("realcraft.zombie.fever",
                    "You feel feverish... the bite is getting worse.").withStyle(ChatFormatting.DARK_GREEN), true);
        }
        if (progress > 0.6F) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, false, false));
        }
        if (progress > 0.8F) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false));
        }
    }

    private static boolean burnsInSunlight(ServerPlayer player) {
        Level level = player.level();
        if (!level.isDay() || player.isCreative() || player.isSpectator() || player.fireImmune()
                || player.hasEffect(MobEffects.FIRE_RESISTANCE) || !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            return false;
        }
        BlockPos eyes = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        return player.getLightLevelDependentMagicValue() > 0.5F && level.canSeeSky(eyes) && !player.isInWaterRainOrBubble();
    }

    /** Turns a player into a zombie. Must not touch the player's effects (see onEffectExpired). */
    public static void zombify(ServerPlayer player) {
        if (ZombifiedState.isZombified(player)) {
            return;
        }
        ZombifiedState.setZombified(player, true);
        ZombifiedState.setCuringTicks(player, 0);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.PLAYERS, 1.0F, 0.8F);
        player.displayClientMessage(Component.translatableWithFallback("realcraft.zombie.turned",
                "You have turned into a zombie! To cure it: splash yourself with Weakness, then eat a golden apple.")
                .withStyle(ChatFormatting.DARK_GREEN), false);
        RealNetwork.syncZombified(player);
    }

    private static void cure(ServerPlayer player) {
        ZombifiedState.setZombified(player, false);
        ZombifiedState.setCuringTicks(player, 0);
        player.removeEffect(MobEffects.CONFUSION);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.PLAYERS, 1.0F, 1.4F);
        player.displayClientMessage(Component.translatableWithFallback("realcraft.zombie.cured",
                "You are human again.").withStyle(ChatFormatting.GREEN), false);
        RealNetwork.syncZombified(player);
    }
}
