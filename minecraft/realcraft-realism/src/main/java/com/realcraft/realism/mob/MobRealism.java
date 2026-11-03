package com.realcraft.realism.mob;

import com.realcraft.realism.RealCraft;
import com.realcraft.realism.config.RealConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;
import java.util.UUID;

/**
 * Creature realism: darkness and sneaking hide you from monsters, herds flee together,
 * projectiles hurt more in the head and slow on the legs, the badly wounded limp, hits spray
 * matching particles and big creatures shrug off knockback.
 */
@Mod.EventBusSubscriber(modid = RealCraft.MOD_ID)
public final class MobRealism {
    private static final UUID LIMP_ID = UUID.fromString("6d1a3f1e-8b8c-4c55-9a4f-3f1f0c7a7b21");
    private static final AttributeModifier LIMP = new AttributeModifier(LIMP_ID, "realcraft:limp", -0.3, AttributeModifier.Operation.MULTIPLY_TOTAL);

    private MobRealism() {
    }

    @SubscribeEvent
    public static void onVisibility(LivingEvent.LivingVisibilityEvent event) {
        if (!RealConfig.STEALTH.on() || !(event.getEntity() instanceof Player player) || !(event.getLookingEntity() instanceof Enemy)) {
            return;
        }
        int light = player.level().getMaxLocalRawBrightness(BlockPos.containing(player.getEyePosition()));
        double factor = 0.35 + 0.65 * (light / 15.0);
        if (player.isCrouching()) {
            factor *= 0.7;
        }
        event.modifyVisibility(factor);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || victim instanceof ArmorStand) {
            return;
        }
        DamageSource source = event.getSource();

        if (RealConfig.HERD_PANIC.on() && victim instanceof Animal && !(victim instanceof NeutralMob)
                && source.getEntity() instanceof LivingEntity attacker) {
            double radius = RealConfig.HERD_RADIUS.val();
            for (Animal other : level.getEntitiesOfClass(Animal.class, victim.getBoundingBox().inflate(radius),
                    a -> a != victim && a.getType() == victim.getType() && a.isAlive())) {
                other.setLastHurtByMob(attacker);
            }
        }

        if (RealConfig.LOCATIONAL_DAMAGE.on() && source.getDirectEntity() instanceof Projectile projectile && victim.getBbHeight() > 0.9F) {
            applyLocationalDamage(event, level, victim, projectile);
        }

        if (RealConfig.IMPACT_PARTICLES.on() && event.getAmount() > 0) {
            BlockState particle = impactParticle(victim);
            if (particle != null) {
                int count = Mth.clamp((int) (event.getAmount() * 3), 3, 30);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, particle),
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), count,
                        victim.getBbWidth() * 0.3, victim.getBbHeight() * 0.2, victim.getBbWidth() * 0.3, 0.1);
            }
        }
    }

    private static void applyLocationalDamage(LivingHurtEvent event, ServerLevel level, LivingEntity victim, Projectile projectile) {
        // The projectile has not moved into the target yet, so trace its path through the hitbox.
        Vec3 from = projectile.position();
        Vec3 to = from.add(projectile.getDeltaMovement());
        Optional<Vec3> clip = victim.getBoundingBox().inflate(0.3).clip(from, to);
        double hitY = clip.map(v -> v.y).orElse(projectile.getY());

        if (hitY >= victim.getEyeY() - 0.3) {
            float multiplier = RealConfig.HEADSHOT_MULTIPLIER.valF();
            if (!victim.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                multiplier = 1.0F + (multiplier - 1.0F) * 0.5F; // helmets soak part of it
            }
            event.setAmount(event.getAmount() * multiplier);
            level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getEyeY(), victim.getZ(), 10, 0.2, 0.2, 0.2, 0.2);
            if (projectile.getOwner() instanceof Player shooter) {
                level.playSound(null, shooter.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.8F, 1.3F);
            }
        } else if (hitY <= victim.getY() + victim.getBbHeight() * 0.3) {
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        }
    }

    private static BlockState impactParticle(LivingEntity victim) {
        if (victim instanceof AbstractSkeleton) {
            return Blocks.BONE_BLOCK.defaultBlockState();
        }
        if (victim instanceof MagmaCube) {
            return Blocks.MAGMA_BLOCK.defaultBlockState();
        }
        if (victim instanceof Slime) {
            return Blocks.SLIME_BLOCK.defaultBlockState();
        }
        if (victim instanceof IronGolem) {
            return Blocks.IRON_BLOCK.defaultBlockState();
        }
        if (victim instanceof SnowGolem) {
            return Blocks.SNOW_BLOCK.defaultBlockState();
        }
        if (victim instanceof AbstractGolem || victim instanceof Blaze) {
            return null;
        }
        return Blocks.REDSTONE_BLOCK.defaultBlockState();
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        if (!RealConfig.MASS_KNOCKBACK.on()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        double volume = Math.max(0.01, entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight());
        double factor = Mth.clamp(Math.pow(0.65 / volume, 0.35), 0.35, 1.6);
        event.setStrength((float) (event.getStrength() * factor));
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || (entity.tickCount + entity.getId()) % 20 != 0) {
            return;
        }
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean wounded = RealConfig.LIMPING.on() && entity.isAlive()
                && entity.getHealth() < entity.getMaxHealth() * RealConfig.LIMP_THRESHOLD.val();
        boolean limping = speed.getModifier(LIMP_ID) != null;
        if (wounded && !limping) {
            speed.addTransientModifier(LIMP);
        } else if (!wounded && limping) {
            speed.removeModifier(LIMP_ID);
        }
    }
}
