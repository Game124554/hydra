package com.realcraft.realism.config;

/**
 * One-click bundles of the performance limits. Applying a preset only touches limit-type
 * settings; feature toggles stay as the player set them (except smooth terrain on LOW).
 */
public enum PerformancePreset {
    LOW(false, 3, 24, 2, 1, 0.3, 24, 4, 6, 3, 10),
    MEDIUM(true, 8, 40, 3, 2, 0.6, 48, 8, 12, 5, 16),
    HIGH(true, 12, 48, 4, 4, 1.0, 64, 12, 24, 6, 24),
    ULTRA(true, 32, 96, 8, 8, 1.6, 256, 32, 64, 10, 40);

    private final boolean smoothTerrain;
    private final int maxRagdolls;
    private final int ragdollDistance;
    private final int ragdollIterations;
    private final int itemRenderCount;
    private final double particleDensity;
    private final int checksPerTick;
    private final int fallsPerTick;
    private final int maxDebris;
    private final int shockwaveRadius;
    private final int cloudDistance;

    PerformancePreset(boolean smoothTerrain, int maxRagdolls, int ragdollDistance, int ragdollIterations, int itemRenderCount,
                      double particleDensity, int checksPerTick, int fallsPerTick, int maxDebris, int shockwaveRadius,
                      int cloudDistance) {
        this.smoothTerrain = smoothTerrain;
        this.maxRagdolls = maxRagdolls;
        this.ragdollDistance = ragdollDistance;
        this.ragdollIterations = ragdollIterations;
        this.itemRenderCount = itemRenderCount;
        this.particleDensity = particleDensity;
        this.checksPerTick = checksPerTick;
        this.fallsPerTick = fallsPerTick;
        this.maxDebris = maxDebris;
        this.shockwaveRadius = shockwaveRadius;
        this.cloudDistance = cloudDistance;
    }

    public String translationKey() {
        return "realcraft.preset." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public String label() {
        return name().charAt(0) + name().substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    public void apply() {
        RealConfig.SMOOTH_TERRAIN.set(smoothTerrain);
        RealConfig.MAX_RAGDOLLS.set(maxRagdolls);
        RealConfig.RAGDOLL_DISTANCE.set(ragdollDistance);
        RealConfig.RAGDOLL_ITERATIONS.set(ragdollIterations);
        RealConfig.ITEM_RENDER_COUNT.set(itemRenderCount);
        RealConfig.PARTICLE_DENSITY.set(particleDensity);
        RealConfig.PHYSICS_CHECKS_PER_TICK.set(checksPerTick);
        RealConfig.PHYSICS_FALLS_PER_TICK.set(fallsPerTick);
        RealConfig.MAX_DEBRIS.set(maxDebris);
        RealConfig.GLASS_SHOCKWAVE_RADIUS.set(shockwaveRadius);
        RealConfig.CLOUD_DISTANCE.set(cloudDistance);
        RealConfig.PRESET.set(this);
        RealConfig.markChanged();
    }
}
