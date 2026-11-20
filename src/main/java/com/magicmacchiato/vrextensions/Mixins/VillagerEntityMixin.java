package com.magicmacchiato.vrextensions.Mixins;


import com.javic.slimpatch.entity.FemaleVillagerEntity;
import com.javic.slimpatch.entity.MaleVillagerEntity;
import com.magicmacchiato.vrextensions.Features.Mood;
import com.magicmacchiato.vrextensions.Features.MoodHolder;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({FemaleVillagerEntity.class, MaleVillagerEntity.class})
public class VillagerEntityMixin implements MoodHolder {
    @Unique
    private final Mood mood = new Mood();

    @Override
    public Mood getVrMood() {
        return this.mood;
    }


    // Save to tag
    @Inject(
            method = "addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("TAIL")
    )
    private void addAdditionalSaveData(CompoundTag tag, CallbackInfo callbackInfo) {
        this.mood.save(tag);
    }

    // Load to tag
    @Inject(
            method = "readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("TAIL")
    )
    private void readAdditionalSaveData(CompoundTag tag, CallbackInfo callbackInfo) {
        this.mood.load(tag);
    }
}
