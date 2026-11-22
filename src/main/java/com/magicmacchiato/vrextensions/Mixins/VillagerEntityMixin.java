package com.magicmacchiato.vrextensions.Mixins;


import com.javic.slimpatch.entity.FemaleVillagerEntity;
import com.javic.slimpatch.entity.MaleVillagerEntity;
import com.magicmacchiato.vrextensions.Features.Mood;
import com.magicmacchiato.vrextensions.Features.MoodHolder;
import com.magicmacchiato.vrextensions.Features.MoodSync;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    @Unique
    private float vrHealthBeforeHurt;

    // Remember health before the hit; the difference afterwards is the damage that got through armor etc.
    @Inject(
            method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("HEAD")
    )
    private void vr$beforeHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        this.vrHealthBeforeHurt = ((LivingEntity) (Object) this).getHealth();
    }

    // Security drops only when the hit was accepted (not ignored/blocked by Villagers Reborn).
    @Inject(
            method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("RETURN")
    )
    private void vr$afterHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide) return;
        float taken = this.vrHealthBeforeHurt - self.getHealth();
        this.mood.applyDamage(taken, self.getMaxHealth(), self.getHealth());
        this.mood.logState(self.getUUID());
        MoodSync.sendToDialoguePlayer((net.minecraft.world.entity.npc.Villager) self);
    }
}
