package com.magicmacchiato.vrextensions.Behaviors;

import com.google.common.collect.ImmutableMap;
import com.javic.slimpatch.entity.FamilyVillager;
import com.javic.slimpatch.entity.FemaleVillagerEntity;
import com.javic.slimpatch.entity.MaleVillagerEntity;
import com.javic.slimpatch.entity.VillagerPersonality;
import com.javic.slimpatch.entity.VillagerRelationshipStage;
import com.magicmacchiato.vrextensions.Dialogue.DialogueNode;
import com.magicmacchiato.vrextensions.Features.Mood;
import com.magicmacchiato.vrextensions.Features.MoodHolder;
import com.magicmacchiato.vrextensions.Features.MoodSync;
import com.magicmacchiato.vrextensions.Features.RelationshipHolder;
import com.magicmacchiato.vrextensions.Utils.DialogueManager;
import com.magicmacchiato.vrextensions.Utils.ReactionParticles;
import com.magicmacchiato.vrextensions.Network.OpenDialoguePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static com.magicmacchiato.vrextensions.VillagersRebornExtensionsMod.LOGGER;

/**
 * Villager-to-villager conversation, random for now: every {@link #INTERVAL_TICKS} ticks the villager rolls
 * {@link #CHANCE}; on success it picks any villager it can currently see (relationship data does not matter) and the
 * two of them talk through the dialogue graphs, at most {@link #MAX_TURNS} lines.
 * <p>
 * Each turn is one line (a dialogue node) from the speaker to the listener:
 * <ol>
 *   <li>the line's semantic is dotted with the listener's personality reaction (same as a player's choice in
 *       {@code ChooseDialoguePacket}) and the result moves the listener's mood, with the same happy / angry particles;</li>
 *   <li>the listener randomly picks the next line among the node's next_choice that its requirements allow (any
 *       speaker type, the graph is still written for player-villager talk); the roles swap;</li>
 *   <li>no allowed next line, or {@link #MAX_TURNS} lines said, ends the conversation.</li>
 * </ol>
 * Requirements are checked for the one who picks / opens a line: its mood, its hearts for the other one (stored
 * {@code VillagerRelationship}, 0 for strangers) and the FRIENDSHIP stage. Lines are only logged, there is no UI.
 * <p>
 * The target may also be a player. Then the villager walks up to them and opens the real dialogue screen on their
 * client ({@link OpenDialoguePacket}); from there the normal player-villager flow runs and this behavior is done.
 * <p>
 * After a conversation ends, whatever the reason, the villager waits {@link #COOLDOWN_TICKS} (3 minutes) before it
 * rolls for another. While walking to the target it gives up if it cannot get close (see {@link #approach}).
 * <p>
 * Uses only LOOK_TARGET / WALK_TARGET, not INTERACTION_TARGET, so vanilla's villager-to-villager trading gate stays
 * untouched. Starts only while the initiator is in the IDLE activity and the partner is not asleep.
 */
public class RandomConversation extends Behavior<Villager> {
    public static final int INTERVAL_TICKS = 100;
    /** Chance per roll. */
    public static final float CHANCE = 0.01F;
    /** Most lines said in one conversation. */
    public static final int MAX_TURNS = 10;
    /** Ticks between two lines. */
    public static final int TURN_TICKS = 30;
    private static final double TALK_DISTANCE_SQR = 3.0D * 3.0D;
    private static final double GIVE_UP_DISTANCE_SQR = 16.0D * 16.0D;
    private static final float WALK_SPEED = 0.5F;
    /** After a conversation ends (for any reason, including giving up) the villager does not look for another. */
    public static final int COOLDOWN_TICKS = 3 * 60 * 20;
    /** Give up if still not within talking distance this long after setting off. */
    private static final int APPROACH_TIMEOUT_TICKS = 15 * 20;
    /** Give up if, over this long, the distance to the target has not shrunk by {@link #MIN_PROGRESS_SQR}. */
    private static final int STUCK_TICKS = 5 * 20;
    private static final double MIN_PROGRESS_SQR = 1.0D;

    private long nextRoll;

    // State of the running conversation.
    private Villager partner;
    /** Set instead of {@link #partner} when the villager talks to a player (opens the dialogue GUI). */
    private ServerPlayer playerTarget;
    /** The line about to be said. */
    private DialogueNode line;
    /** Who says {@link #line}; the other one hears it. */
    private Villager speaker;
    private Villager listener;
    private int turns;
    private long nextTurn;
    private boolean finished;
    /** Player target only: the dialogue screen was actually opened (not abandoned on the way). */
    private boolean dialogueOpened;
    // Approach tracking, to give up when the two cannot reach each other (blocked path, unreachable, moving away).
    private long approachStart;
    private long lastProgress;
    private double bestDistanceSqr;

    public RandomConversation() {
        // Slack on top of the turns covers the initiator walking over first.
        super(ImmutableMap.of(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT),
                TURN_TICKS, (MAX_TURNS + 4) * TURN_TICKS);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager owner) {
        long now = level.getGameTime();
        if (now < this.nextRoll) return false;
        this.nextRoll = now + INTERVAL_TICKS;
        if (owner.isSleeping() || !owner.getBrain().isActive(Activity.IDLE)) return false;
        if (owner.getRandom().nextFloat() >= CHANCE) return false;

        LivingEntity target = pickTarget(owner);
        if (target == null) return false;
        if (target instanceof ServerPlayer player) {
            // The dialogue screen drives the conversation; the villager only has to walk up and open it.
            this.playerTarget = player;
            return true;
        }
        Villager other = (Villager) target;
        DialogueNode opener = DialogueManager.randomOpener(gate(owner, other));
        if (opener == null) return false;

        this.partner = other;
        this.speaker = owner;
        this.listener = other;
        this.line = opener;
        return true;
    }

    /** A random villager or player the owner can currently see, or null. */
    private static LivingEntity pickTarget(Villager owner) {
        NearestVisibleLivingEntities visible =
                owner.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElse(null);
        if (visible == null) return null;
        List<LivingEntity> candidates = new ArrayList<>();
        for (LivingEntity e : visible.findAll(x -> true)) {
            if (e instanceof Villager v) {
                if ((v.getType() == EntityType.VILLAGER || v instanceof FamilyVillager)
                        && v != owner && v.isAlive() && !v.isSleeping()) candidates.add(v);
            } else if (e instanceof ServerPlayer p && isAvailable(p, owner)) {
                candidates.add(p);
            }
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(owner.getRandom().nextInt(candidates.size()));
    }

    /** A player the villager may start talking to: alive, playing, not busy with a menu or another dialogue. */
    private static boolean isAvailable(ServerPlayer player, Villager owner) {
        return player.isAlive() && !player.isSpectator() && !player.isSleeping()
                && player.containerMenu == player.inventoryMenu
                && !com.javic.slimpatch.dialogue.DialogueManager.isInDialogue(owner)
                && !com.javic.slimpatch.dialogue.DialogueManager.getActiveDialogues().containsValue(player.getUUID());
    }

    /**
     * Requirements for a line that {@code chooser} opens or picks while talking with {@code other}: the chooser's
     * mood (read live, it changes during the conversation), its stored hearts for the other, the FRIENDSHIP stage.
     */
    private static Predicate<DialogueNode> gate(Villager chooser, Villager other) {
        float hearts = chooser instanceof RelationshipHolder holder
                ? holder.getVrRelationship().get(other.getUUID()) : 0f;
        Mood mood = chooser instanceof MoodHolder holder ? holder.getVrMood() : new Mood();
        return node -> node.requires() == null
                || node.requires().isMetBy(hearts, VillagerRelationshipStage.FRIENDSHIP, mood);
    }

    @Nullable
    private static VillagerPersonality personalityOf(Villager villager) {
        return villager instanceof MaleVillagerEntity male ? male.getPersonality()
                : villager instanceof FemaleVillagerEntity female ? female.getPersonality() : null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager owner, long gameTime) {
        if (this.finished || !owner.getBrain().isActive(Activity.IDLE)) return false;
        if (this.playerTarget != null) {
            return isAvailable(this.playerTarget, owner)
                    && owner.distanceToSqr(this.playerTarget) <= GIVE_UP_DISTANCE_SQR;
        }
        return this.partner != null && this.partner.isAlive() && !this.partner.isSleeping()
                && owner.distanceToSqr(this.partner) <= GIVE_UP_DISTANCE_SQR;
    }

    @Override
    protected void start(ServerLevel level, Villager owner, long gameTime) {
        this.turns = 0;
        this.finished = false;
        this.dialogueOpened = false;
        this.nextTurn = gameTime;
        this.approachStart = gameTime;
        this.lastProgress = gameTime;
        this.bestDistanceSqr = Double.MAX_VALUE;
        LivingEntity other = this.playerTarget != null ? this.playerTarget : this.partner;
        LOGGER.info("{} starts talking with {}", owner.getName().getString(), other.getName().getString());
    }

    @Override
    protected void tick(ServerLevel level, Villager owner, long gameTime) {
        if (this.playerTarget != null) {
            tickPlayerTarget(owner, gameTime);
            return;
        }
        if (this.partner == null) return;
        BehaviorUtils.lookAtEntity(owner, this.partner);
        BehaviorUtils.lookAtEntity(this.partner, owner);

        if (!approach(owner, this.partner, gameTime)) return; // wait until they stand close enough
        if (gameTime < this.nextTurn) return;
        this.nextTurn = gameTime + TURN_TICKS;
        speakLine(level);
    }

    /**
     * Walks the owner toward the target. True once within talking distance. Otherwise false, and the conversation is
     * abandoned ({@link #finished}) if it takes longer than {@link #APPROACH_TIMEOUT_TICKS} or the distance stops
     * shrinking for {@link #STUCK_TICKS} (path blocked, target unreachable or walking away).
     */
    private boolean approach(Villager owner, LivingEntity target, long gameTime) {
        double distanceSqr = owner.distanceToSqr(target);
        if (distanceSqr <= TALK_DISTANCE_SQR) return true;

        if (distanceSqr < this.bestDistanceSqr - MIN_PROGRESS_SQR) {
            this.bestDistanceSqr = distanceSqr;
            this.lastProgress = gameTime;
        }
        if (gameTime - this.approachStart > APPROACH_TIMEOUT_TICKS || gameTime - this.lastProgress > STUCK_TICKS) {
            LOGGER.info("{} gives up reaching {}", owner.getName().getString(), target.getName().getString());
            this.finished = true;
            return false;
        }
        BehaviorUtils.setWalkAndLookTargetMemories(owner, target, WALK_SPEED, 2);
        return false;
    }

    /** Walks up to the player, then opens the dialogue screen on their client. The dialogue flow takes over from there. */
    private void tickPlayerTarget(Villager owner, long gameTime) {
        BehaviorUtils.lookAtEntity(owner, this.playerTarget);
        if (!approach(owner, this.playerTarget, gameTime)) return;
        PacketDistributor.sendToPlayer(this.playerTarget, new OpenDialoguePacket(owner.getId()));
        this.dialogueOpened = true;
        this.finished = true;
    }

    /** Says the current line: the listener reacts, then picks the next line or the conversation ends. */
    private void speakLine(ServerLevel level) {
        LOGGER.info("[{} -> {}] {}", this.speaker.getName().getString(), this.listener.getName().getString(),
                this.line.text());
        this.turns++;

        VillagerPersonality personality = personalityOf(this.listener);
        if (this.line.semantic() != null && personality != null && this.listener instanceof MoodHolder holder) {
            float reaction = Mood.reaction(personality, this.line.semantic());
            holder.getVrMood().applyReaction(reaction);
            ReactionParticles.spawn(level, this.listener, reaction);
            holder.getVrMood().logState(this.listener.getUUID());
            MoodSync.sendToDialoguePlayer(this.listener);
        }

        if (this.turns >= MAX_TURNS) {
            this.finished = true;
            return;
        }
        // The mood has just changed, so the listener's requirements are evaluated afterwards.
        DialogueNode next = DialogueManager.randomNext(this.line, gate(this.listener, this.speaker));
        if (next == null) {
            this.finished = true;
            return;
        }
        this.line = next;
        Villager previousSpeaker = this.speaker;
        this.speaker = this.listener;
        this.listener = previousSpeaker;
    }

    @Override
    protected void stop(ServerLevel level, Villager owner, long gameTime) {
        this.nextRoll = gameTime + COOLDOWN_TICKS;
        if (this.partner != null) {
            LOGGER.info("{} stops talking with {} after {} line(s)", owner.getName().getString(),
                    this.partner.getName().getString(), this.turns);
        } else if (this.playerTarget != null) {
            LOGGER.info("{} stops walking to {} (dialogue opened: {})", owner.getName().getString(),
                    this.playerTarget.getName().getString(), this.dialogueOpened);
        }
        this.partner = null;
        this.playerTarget = null;
        this.speaker = null;
        this.listener = null;
        this.line = null;
    }
}
