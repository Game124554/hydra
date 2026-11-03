package com.realcraft.realism.registry;

import com.realcraft.realism.RealCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    /** Blocks drawn as smooth terrain. */
    public static final TagKey<Block> SMOOTHABLE = block("smoothable");
    /** Rock-like blocks that can span gaps a few blocks wide before collapsing. */
    public static final TagKey<Block> PHYSICS_RIGID = block("physics_rigid");
    /** Soil-like blocks that need support directly below (or right next to them). */
    public static final TagKey<Block> PHYSICS_LOOSE = block("physics_loose");
    /** Blocks that shatter from impacts, projectiles and shockwaves. */
    public static final TagKey<Block> SHATTERABLE = block("shatterable");
    /** Blocks that never turn into explosion debris. */
    public static final TagKey<Block> NO_DEBRIS = block("no_debris");

    private ModTags() {
    }

    private static TagKey<Block> block(String name) {
        return TagKey.create(Registries.BLOCK, RealCraft.id(name));
    }
}
