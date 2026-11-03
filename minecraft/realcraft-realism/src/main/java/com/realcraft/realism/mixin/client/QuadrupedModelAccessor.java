package com.realcraft.realism.mixin.client;

import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(QuadrupedModel.class)
public interface QuadrupedModelAccessor {
    @Accessor("head")
    ModelPart realcraft$head();

    @Accessor("rightHindLeg")
    ModelPart realcraft$rightHindLeg();

    @Accessor("leftHindLeg")
    ModelPart realcraft$leftHindLeg();

    @Accessor("rightFrontLeg")
    ModelPart realcraft$rightFrontLeg();

    @Accessor("leftFrontLeg")
    ModelPart realcraft$leftFrontLeg();
}
