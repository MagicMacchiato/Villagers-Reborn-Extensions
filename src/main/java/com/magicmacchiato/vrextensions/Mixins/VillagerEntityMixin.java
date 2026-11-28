package com.magicmacchiato.vrextensions.Mixins;


import com.javic.slimpatch.entity.FemaleVillagerEntity;
import com.javic.slimpatch.entity.MaleVillagerEntity;
import com.magicmacchiato.vrextensions.Features.InterestHolder;
import com.magicmacchiato.vrextensions.Features.Mood;
import com.magicmacchiato.vrextensions.Features.MoodHolder;
import com.magicmacchiato.vrextensions.Features.MoodSync;
import com.magicmacchiato.vrextensions.Features.RelationshipHolder;
import com.magicmacchiato.vrextensions.Features.TalkCooldownHolder;
import com.magicmacchiato.vrextensions.Features.VillagerInterests;
import com.magicmacchiato.vrextensions.Features.VillagerRelationship;
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
public class VillagerEntityMixin implements MoodHolder, RelationshipHolder, InterestHolder, TalkCooldownHolder {
    @Unique
    private final Mood mood = new Mood();

    @Override
    public Mood getVrMood() {
        return this.mood;
    }

    @Unique
    private final VillagerRelationship relationship = new VillagerRelationship();

    @Override
    public VillagerRelationship getVrRelationship() {
        return this.relationship;
    }

    @Unique
    private final VillagerInterests interests = new VillagerInterests();

    @Override
    public VillagerInterests getVrInterests() {
        return this.interests;
    }

    @Unique
    private static final String NEXT_TALK_KEY = "vrextensions:next_talk";

    @Unique
    private static final String TALK_DAY_KEY = "vrextensions:talk_day";
    @Unique
    private static final String TALK_COUNT_KEY = "vrextensions:talk_count";

    @Unique
    private long nextTalk;
    @Unique
    private long talkDay;
    @Unique
    private int talkCount;

    @Override
    public int getVrTalks(long day) {
        return day == this.talkDay ? this.talkCount : 0;
    }

    @Override
    public void addVrTalk(long day) {
        this.talkCount = getVrTalks(day) + 1;
        this.talkDay = day;
    }

    @Override
    public long getVrNextTalk() {
        return this.nextTalk;
    }

    @Override
    public void setVrNextTalk(long gameTime) {
        this.nextTalk = gameTime;
    }

    // Save to tag
    @Inject(
            method = "addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("TAIL")
    )
    private void addAdditionalSaveData(CompoundTag tag, CallbackInfo callbackInfo) {
        this.mood.save(tag);
        this.relationship.save(tag);
        this.interests.save(tag);
        tag.putLong(NEXT_TALK_KEY, this.nextTalk);
        tag.putLong(TALK_DAY_KEY, this.talkDay);
        tag.putInt(TALK_COUNT_KEY, this.talkCount);
    }

    // Load to tag
    @Inject(
            method = "readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("TAIL")
    )
    private void readAdditionalSaveData(CompoundTag tag, CallbackInfo callbackInfo) {
        this.mood.load(tag);
        this.relationship.load(tag);
        this.interests.load(tag);
        this.nextTalk = tag.getLong(NEXT_TALK_KEY); // 0 (ready) if absent
        this.talkDay = tag.getLong(TALK_DAY_KEY);
        this.talkCount = tag.getInt(TALK_COUNT_KEY);
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
