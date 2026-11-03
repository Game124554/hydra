package com.realcraft.realism.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every RealCraft setting. COMMON values drive gameplay and world generation (and are what a
 * server uses); CLIENT values drive visuals. All options are listed in {@link #OPTIONS} in the
 * order the settings screen shows them.
 */
public final class RealConfig {
    public static final List<Opt<?>> OPTIONS = new ArrayList<>();

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    // ---- Physics (common) ----
    public static final Opt.Bool PHYSICS;
    public static final Opt.Int ROCK_SPAN;
    public static final Opt.Int SOIL_SPAN;
    public static final Opt.Int PHYSICS_CHECKS_PER_TICK;
    public static final Opt.Int PHYSICS_FALLS_PER_TICK;
    public static final Opt.Int PHYSICS_QUEUE_LIMIT;
    public static final Opt.Bool ROCK_CRUMBLES;
    public static final Opt.Dbl FALLING_BLOCK_DAMAGE;
    public static final Opt.Bool EXPLOSION_DEBRIS;
    public static final Opt.Dbl DEBRIS_CHANCE;
    public static final Opt.Int MAX_DEBRIS;
    public static final Opt.Dbl DEBRIS_SPEED;

    // ---- Glass (common) ----
    public static final Opt.Bool GLASS;
    public static final Opt.Bool GLASS_PROJECTILES;
    public static final Opt.Dbl GLASS_PROJECTILE_SPEED;
    public static final Opt.Bool GLASS_IMPACTS;
    public static final Opt.Dbl GLASS_IMPACT_SPEED;
    public static final Opt.Dbl GLASS_FALL_DISTANCE;
    public static final Opt.Int GLASS_SHOCKWAVE_RADIUS;
    public static final Opt.Dbl GLASS_CUT_DAMAGE;

    // ---- Water & lava (common) ----
    public static final Opt.Dbl WATER_CURRENT;
    public static final Opt.Bool ARMOR_SINKING;
    public static final Opt.Dbl ARMOR_SINK_STRENGTH;
    public static final Opt.Bool LAVA_HEAT;
    public static final Opt.Dbl LAVA_HEAT_RADIUS;
    public static final Opt.Bool STEAM;

    // ---- World (common) ----
    public static final Opt.Bool REMOVE_BEDROCK;
    public static final Opt.Bool REMOVE_NETHER_BEDROCK;
    public static final ForgeConfigSpec.ConfigValue<String> OVERWORLD_BEDROCK_REPLACEMENT;
    public static final ForgeConfigSpec.ConfigValue<String> NETHER_BEDROCK_REPLACEMENT;
    public static final Opt.Bool HELL_LAYER;
    public static final Opt.Int HELL_TOP_Y;
    public static final Opt.Int HELL_LAVA_LEVEL;
    public static final Opt.Dbl HELL_FIRE_CHANCE;
    public static final Opt.Dbl HELL_GLOWSTONE_CHANCE;
    public static final Opt.Bool HELL_CONVERT_ORES;
    public static final Opt.Bool STEP_ASSIST;

    // ---- Mobs (common) ----
    public static final Opt.Bool STEALTH;
    public static final Opt.Bool HERD_PANIC;
    public static final Opt.Dbl HERD_RADIUS;
    public static final Opt.Bool LOCATIONAL_DAMAGE;
    public static final Opt.Dbl HEADSHOT_MULTIPLIER;
    public static final Opt.Bool LIMPING;
    public static final Opt.Dbl LIMP_THRESHOLD;
    public static final Opt.Bool IMPACT_PARTICLES;
    public static final Opt.Bool MASS_KNOCKBACK;
    public static final Opt.Int CORPSE_TICKS;

    // ---- Client ----
    public static final ForgeConfigSpec.EnumValue<PerformancePreset> PRESET;
    public static final Opt.Bool SMOOTH_TERRAIN;
    public static final Opt.Dbl SMOOTHNESS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SMOOTH_EXTRA_BLOCKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SMOOTH_EXCLUDED_BLOCKS;
    public static final Opt.Bool CAMERA_SHAKE;
    public static final Opt.Dbl SHAKE_INTENSITY;
    public static final Opt.Bool SHAKE_EXPLOSIONS;
    public static final Opt.Bool SHAKE_DAMAGE;
    public static final Opt.Bool SHAKE_LANDING;
    public static final Opt.Bool SHAKE_ENVIRONMENT;
    public static final Opt.Bool RAGDOLLS;
    public static final Opt.Int MAX_RAGDOLLS;
    public static final Opt.Int RAGDOLL_DISTANCE;
    public static final Opt.Int RAGDOLL_ITERATIONS;
    public static final Opt.Bool ITEM_PHYSICS;
    public static final Opt.Int ITEM_RENDER_COUNT;
    public static final Opt.Bool HELL_FOG;
    public static final Opt.Dbl HELL_FOG_DENSITY;
    public static final Opt.Bool AMBIENT_PARTICLES;
    public static final Opt.Dbl PARTICLE_DENSITY;
    public static final Opt.Bool HELL_SOUNDS;
    public static final Opt.Bool BIG_SPLASHES;

    static {
        Defs c = new Defs(false);

        c.category("physics", "Structural physics: unsupported natural blocks collapse, explosions throw debris.");
        PHYSICS = c.bool("structuralPhysics", true, "Structural Physics",
                "Natural blocks (tags realcraft:physics_rigid / physics_loose) collapse when they lose support.");
        ROCK_SPAN = c.range("rockOverhangSpan", 4, 0, 12, "Rock Overhang Span",
                "How many blocks sideways rock can reach to find support before it caves in.");
        SOIL_SPAN = c.range("soilOverhangSpan", 1, 0, 6, "Soil Overhang Span",
                "How many blocks sideways dirt, grass and similar soils can reach for support.");
        PHYSICS_CHECKS_PER_TICK = c.range("maxSupportChecksPerTick", 64, 4, 1024, "Max Support Checks / Tick",
                "Performance limit: support checks processed per tick in each dimension.");
        PHYSICS_FALLS_PER_TICK = c.range("maxCollapsesPerTick", 12, 1, 128, "Max Collapsing Blocks / Tick",
                "Performance limit: blocks that may start falling per tick in each dimension.");
        PHYSICS_QUEUE_LIMIT = c.range("maxQueuedChecks", 2048, 64, 16384, "Max Queued Checks",
                "Performance limit: pending support checks are dropped beyond this many (stops endless cave-ins).");
        ROCK_CRUMBLES = c.bool("rockCrumbles", true, "Rock Crumbles When Falling",
                "Collapsing stone turns into cobblestone, grass into dirt, and so on.");
        FALLING_BLOCK_DAMAGE = c.range("fallingBlockDamage", 2.0, 0.0, 20.0, "Cave-in Damage",
                "Damage per block fallen dealt by collapsing blocks (max 40).");
        EXPLOSION_DEBRIS = c.bool("explosionDebris", true, "Explosion Debris",
                "Explosions hurl some of the destroyed blocks through the air instead of deleting them.");
        DEBRIS_CHANCE = c.range("debrisChance", 0.3, 0.0, 1.0, "Debris Chance",
                "Chance for each destroyed block to become flying debris.");
        MAX_DEBRIS = c.range("maxDebrisPerExplosion", 24, 0, 256, "Max Debris / Explosion",
                "Performance limit: flying debris blocks per explosion.");
        DEBRIS_SPEED = c.range("debrisSpeed", 0.9, 0.1, 3.0, "Debris Speed", "Launch speed of explosion debris.");

        c.category("glass", "Breakable glass.");
        GLASS = c.bool("breakableGlass", true, "Breakable Glass",
                "Glass and glass panes (tag realcraft:shatterable) shatter from hits, impacts and blasts.");
        GLASS_PROJECTILES = c.bool("projectilesShatterGlass", true, "Arrows Shatter Glass",
                "Arrows and tridents fly through glass, shattering it.");
        GLASS_PROJECTILE_SPEED = c.range("projectileShatterSpeed", 0.6, 0.0, 5.0, "Arrow Shatter Speed",
                "Minimum projectile speed (blocks/tick) needed to break glass.");
        GLASS_IMPACTS = c.bool("impactsShatterGlass", true, "Body Impacts Shatter Glass",
                "Sprinting into glass or landing on it from a height breaks it.");
        GLASS_IMPACT_SPEED = c.range("impactShatterSpeed", 0.22, 0.05, 2.0, "Impact Speed Threshold",
                "Horizontal speed (blocks/tick) needed to crash through glass. Sprinting is about 0.28.");
        GLASS_FALL_DISTANCE = c.range("fallShatterDistance", 3.0, 0.0, 30.0, "Fall Shatter Height",
                "Fall distance in blocks needed to break glass you land on.");
        GLASS_SHOCKWAVE_RADIUS = c.range("explosionShatterRadius", 6, 0, 16, "Blast Shatter Radius",
                "Glass within this many blocks of an explosion shatters from the shockwave.");
        GLASS_CUT_DAMAGE = c.range("glassCutDamage", 2.0, 0.0, 20.0, "Glass Cut Damage",
                "Damage taken when crashing through glass with your body.");

        c.category("fluids", "Water and lava.");
        WATER_CURRENT = c.range("waterCurrentStrength", 0.012, 0.0, 0.1, "Water Current Strength",
                "Extra push from flowing water (vanilla is about 0.014).");
        ARMOR_SINKING = c.bool("armorSinking", true, "Heavy Armor Sinks",
                "Wearing heavy armor drags you down in water.");
        ARMOR_SINK_STRENGTH = c.range("armorSinkStrength", 0.0012, 0.0, 0.01, "Armor Sink Strength",
                "Downward pull per armor point while swimming.");
        LAVA_HEAT = c.bool("lavaHeat", true, "Lava Radiant Heat",
                "Standing right next to lava sets you on fire.");
        LAVA_HEAT_RADIUS = c.range("lavaHeatRadius", 1.0, 0.25, 4.0, "Lava Heat Radius",
                "How close (in blocks) you must be to lava to catch fire.");
        STEAM = c.bool("steamClouds", true, "Steam Clouds",
                "Water meeting lava bursts into clouds of steam.");

        c.category("world", "World generation. Only affects newly generated chunks.");
        REMOVE_BEDROCK = c.bool("removeBedrock", true, "Remove Overworld Bedrock",
                "Replaces the overworld bedrock floor in new chunks.");
        REMOVE_NETHER_BEDROCK = c.bool("removeNetherBedrock", true, "Remove Nether Bedrock",
                "Replaces the nether bedrock floor and roof in new chunks.");
        OVERWORLD_BEDROCK_REPLACEMENT = c.builder
                .comment("Block that replaces overworld bedrock. Use minecraft:air to remove it completely (the void will be exposed).")
                .define("overworldBedrockReplacement", "minecraft:blackstone", RealConfig::isBlockId);
        NETHER_BEDROCK_REPLACEMENT = c.builder
                .comment("Block that replaces nether bedrock. Use minecraft:air to remove it completely.")
                .define("netherBedrockReplacement", "minecraft:netherrack", RealConfig::isBlockId);
        HELL_LAYER = c.bool("hellUnderground", true, "Hell Underground",
                "The deepest overworld layers turn into a hellscape of netherrack, magma, fire and lava lakes.");
        HELL_TOP_Y = c.range("hellTopY", -40, -64, 64, "Hell Layer Top (Y)",
                "Height where the hell layer begins (it blends over a few blocks).");
        HELL_LAVA_LEVEL = c.range("hellLavaLevel", -58, -64, 64, "Hell Lava Lake Level (Y)",
                "Open caves in the hell layer are flooded with lava up to this height.");
        HELL_FIRE_CHANCE = c.range("hellFireChance", 0.04, 0.0, 0.5, "Hell Fire Density",
                "Chance for exposed hell floors to be burning.");
        HELL_GLOWSTONE_CHANCE = c.range("hellGlowstoneChance", 0.012, 0.0, 0.2, "Hell Glowstone Density",
                "Chance for hell ceilings to grow glowstone.");
        HELL_CONVERT_ORES = c.bool("hellConvertOres", true, "Nether Ores In Hell Layer",
                "Gold and coal ores in the hell layer become nether gold and nether quartz; rarer ores stay.");
        STEP_ASSIST = c.bool("smoothTerrainStepAssist", true, "Step Assist",
                "Players step up full blocks so smoothed slopes can be walked without jumping.");

        c.category("mobs", "Mob realism.");
        STEALTH = c.bool("darknessStealth", true, "Darkness Stealth",
                "Hostile mobs have a much harder time spotting you in the dark or while sneaking.");
        HERD_PANIC = c.bool("herdPanic", true, "Herd Panic",
                "Hurting a passive animal makes the rest of its herd flee as well.");
        HERD_RADIUS = c.range("herdRadius", 10.0, 1.0, 32.0, "Herd Radius", "How far a herd panic spreads.");
        LOCATIONAL_DAMAGE = c.bool("locationalDamage", true, "Headshots & Leg Shots",
                "Projectiles to the head deal extra damage; leg hits slow the target.");
        HEADSHOT_MULTIPLIER = c.range("headshotMultiplier", 1.75, 1.0, 5.0, "Headshot Multiplier",
                "Damage multiplier for projectile headshots.");
        LIMPING = c.bool("woundedLimping", true, "Wounded Mobs Limp",
                "Badly hurt creatures (and players) move slower.");
        LIMP_THRESHOLD = c.range("limpHealthFraction", 0.3, 0.0, 1.0, "Limp Health Threshold",
                "Fraction of max health below which creatures start limping.");
        IMPACT_PARTICLES = c.bool("impactParticles", true, "Hit Particles",
                "Hits spray particles that match the creature (red for flesh, bone for skeletons, slime for slimes).");
        MASS_KNOCKBACK = c.bool("massKnockback", true, "Weight-Based Knockback",
                "Big creatures are knocked back less, small ones more.");
        CORPSE_TICKS = c.range("corpseTicks", 80, 20, 600, "Corpse Duration",
                "How long (in ticks) dead creatures stay in the world, so ragdolls have time to settle. Vanilla is 20.");
        COMMON_SPEC = c.build();

        Defs v = new Defs(true);
        v.category("performance", "Performance preset. Picking one in the settings screen overwrites the limits below.");
        PRESET = v.builder.comment("Last applied preset (informational).").defineEnum("preset", PerformancePreset.HIGH);

        v.category("terrain", "Smooth terrain (NoCubes-style).");
        SMOOTH_TERRAIN = v.bool("smoothTerrain", true, "Smooth Terrain",
                "Renders natural terrain (tag realcraft:smoothable) as smooth surfaces instead of cubes.");
        SMOOTHNESS = v.range("smoothness", 1.0, 0.0, 1.0, "Smoothness",
                "0 = cubes, 1 = fully smoothed.");
        SMOOTH_EXTRA_BLOCKS = v.builder.comment("Extra block ids to smooth, e.g. [\"minecraft:oak_leaves\"].")
                .defineListAllowEmpty("extraSmoothBlocks", Collections.emptyList(), RealConfig::isBlockId);
        SMOOTH_EXCLUDED_BLOCKS = v.builder.comment("Block ids never to smooth, even if they are in the tag.")
                .defineListAllowEmpty("excludedSmoothBlocks", Collections.emptyList(), RealConfig::isBlockId);

        v.category("camera", "Camera shake.");
        CAMERA_SHAKE = v.bool("cameraShake", true, "Camera Shake", "Shake the camera on impacts and blasts.");
        SHAKE_INTENSITY = v.range("shakeIntensity", 1.0, 0.0, 3.0, "Shake Intensity", "Overall strength of camera shake.");
        SHAKE_EXPLOSIONS = v.bool("shakeOnExplosions", true, "Shake: Explosions", "Explosions shake the camera by distance.");
        SHAKE_DAMAGE = v.bool("shakeOnDamage", true, "Shake: Taking Damage", "Taking damage jolts the camera.");
        SHAKE_LANDING = v.bool("shakeOnLanding", true, "Shake: Hard Landings", "Landing from a height jolts the camera.");
        SHAKE_ENVIRONMENT = v.bool("shakeOnEnvironment", true, "Shake: Thunder & Cave-ins",
                "Thunder, roars, sonic booms and nearby cave-ins rumble the camera.");

        v.category("ragdolls", "Ragdoll physics for dying creatures.");
        RAGDOLLS = v.bool("ragdolls", true, "Ragdolls", "Dying creatures topple and tumble with physics instead of the stiff death flip.");
        MAX_RAGDOLLS = v.range("maxRagdolls", 12, 0, 64, "Max Ragdolls", "Performance limit: simultaneous ragdolls.");
        RAGDOLL_DISTANCE = v.range("ragdollDistance", 48, 8, 128, "Ragdoll Distance", "Ragdolls are only simulated within this many blocks.");
        RAGDOLL_ITERATIONS = v.range("ragdollSolverIterations", 4, 1, 12, "Ragdoll Quality", "Physics solver iterations per tick.");

        v.category("items", "Dropped item physics.");
        ITEM_PHYSICS = v.bool("itemPhysics", true, "Item Physics", "Dropped items lie flat on the ground and tumble through the air.");
        ITEM_RENDER_COUNT = v.range("maxRenderedItemsPerStack", 4, 1, 8, "Items Drawn Per Stack",
                "Performance limit: models drawn for one dropped stack.");

        v.category("atmosphere", "Hell atmosphere, particles and water effects.");
        HELL_FOG = v.bool("hellFog", true, "Hell Fog", "Thick red fog in the Nether and in the hell layer.");
        HELL_FOG_DENSITY = v.range("hellFogDensity", 1.0, 0.0, 3.0, "Hell Fog Density", "How thick the hell fog is.");
        AMBIENT_PARTICLES = v.bool("ambientParticles", true, "Ash & Embers", "Ash and embers drift around you in hell.");
        PARTICLE_DENSITY = v.range("particleDensity", 1.0, 0.0, 3.0, "Particle Density", "Performance limit: amount of ambient and effect particles.");
        HELL_SOUNDS = v.bool("hellSounds", true, "Hell Ambience", "Rumbles and moans echo through the hell layer.");
        BIG_SPLASHES = v.bool("bigSplashes", true, "Big Splashes", "Creatures falling into water throw up big splashes.");
        CLIENT_SPEC = v.build();
    }

    private RealConfig() {
    }

    // ------------------------------------------------------------------------------------------
    // Derived values, recomputed whenever a config file is (re)loaded.

    private static volatile int generation;
    private static volatile WorldCache worldCache;
    private static volatile SmoothCache smoothCache;

    // Kept apart because a dedicated server never loads the client config.
    private record WorldCache(int generation, BlockState overworldBedrock, BlockState netherBedrock) {
    }

    private record SmoothCache(int generation, Set<Block> extra, Set<Block> excluded) {
    }

    public static void onLoad(ModConfigEvent.Loading event) {
        generation++;
    }

    public static void onReload(ModConfigEvent.Reloading event) {
        generation++;
    }

    /** Call after changing values from code (the settings screen) so derived caches refresh. */
    public static void markChanged() {
        generation++;
    }

    private static WorldCache worldCache() {
        WorldCache c = worldCache;
        if (c == null || c.generation != generation) {
            c = new WorldCache(generation,
                    parseState(OVERWORLD_BEDROCK_REPLACEMENT.get(), Blocks.BLACKSTONE),
                    parseState(NETHER_BEDROCK_REPLACEMENT.get(), Blocks.NETHERRACK));
            worldCache = c;
        }
        return c;
    }

    private static SmoothCache smoothCache() {
        SmoothCache c = smoothCache;
        if (c == null || c.generation != generation) {
            c = new SmoothCache(generation, parseBlocks(SMOOTH_EXTRA_BLOCKS.get()), parseBlocks(SMOOTH_EXCLUDED_BLOCKS.get()));
            smoothCache = c;
        }
        return c;
    }

    public static BlockState overworldBedrockReplacement() {
        return worldCache().overworldBedrock;
    }

    public static BlockState netherBedrockReplacement() {
        return worldCache().netherBedrock;
    }

    public static Set<Block> smoothExtraBlocks() {
        return smoothCache().extra;
    }

    public static Set<Block> smoothExcludedBlocks() {
        return smoothCache().excluded;
    }

    private static boolean isBlockId(Object o) {
        return o instanceof String s && ResourceLocation.tryParse(s) != null;
    }

    private static BlockState parseState(String id, Block fallback) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl != null && ForgeRegistries.BLOCKS.containsKey(rl)) {
            Block block = ForgeRegistries.BLOCKS.getValue(rl);
            if (block != null) {
                return block.defaultBlockState();
            }
        }
        return fallback.defaultBlockState();
    }

    private static Set<Block> parseBlocks(List<? extends String> ids) {
        Set<Block> blocks = new HashSet<>();
        for (String id : ids) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null && ForgeRegistries.BLOCKS.containsKey(rl)) {
                blocks.add(ForgeRegistries.BLOCKS.getValue(rl));
            }
        }
        return Collections.unmodifiableSet(blocks);
    }

    // ------------------------------------------------------------------------------------------

    /** Small helper that defines a value in the Forge spec and registers its screen metadata. */
    private static final class Defs {
        final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        final boolean client;
        String category;

        Defs(boolean client) {
            this.client = client;
        }

        void category(String name, String comment) {
            if (category != null) {
                builder.pop();
            }
            category = name;
            builder.comment(comment).push(name);
        }

        ForgeConfigSpec build() {
            if (category != null) {
                builder.pop();
            }
            return builder.build();
        }

        private String lang(String key) {
            return "realcraft.option." + key;
        }

        Opt.Bool bool(String key, boolean def, String label, String comment) {
            ForgeConfigSpec.BooleanValue value = builder.comment(comment).translation(lang(key)).define(key, def);
            return add(new Opt.Bool(category, key, label, comment, client, value));
        }

        Opt.Int range(String key, int def, int min, int max, String label, String comment) {
            ForgeConfigSpec.IntValue value = builder.comment(comment).translation(lang(key)).defineInRange(key, def, min, max);
            return add(new Opt.Int(category, key, label, comment, client, value, min, max));
        }

        Opt.Dbl range(String key, double def, double min, double max, String label, String comment) {
            ForgeConfigSpec.DoubleValue value = builder.comment(comment).translation(lang(key)).defineInRange(key, def, min, max);
            return add(new Opt.Dbl(category, key, label, comment, client, value, min, max));
        }

        private <O extends Opt<?>> O add(O opt) {
            OPTIONS.add(opt);
            return opt;
        }
    }
}
