package com.magicmacchiato.vrextensions.Behaviors;

import com.google.common.collect.ImmutableMap;
import com.javic.slimpatch.entity.FamilyVillager;
import com.javic.slimpatch.entity.FemaleVillagerEntity;
import com.javic.slimpatch.entity.MaleVillagerEntity;
import com.javic.slimpatch.entity.VillagerPersonality;
import com.magicmacchiato.vrextensions.Dialogue.Dialogue;
import com.magicmacchiato.vrextensions.Enums.RelationshipStatus;
import com.magicmacchiato.vrextensions.Features.DialogueHearts;
import com.magicmacchiato.vrextensions.Features.Mood;
import com.magicmacchiato.vrextensions.Features.MoodHolder;
import com.magicmacchiato.vrextensions.Features.MoodSync;
import com.magicmacchiato.vrextensions.Features.RelationshipHolder;
import com.magicmacchiato.vrextensions.Features.TalkCooldownHolder;
import com.magicmacchiato.vrextensions.Features.VillagerInterests;
import com.magicmacchiato.vrextensions.Utils.DialogueManager;
import com.magicmacchiato.vrextensions.Utils.Random;
import com.magicmacchiato.vrextensions.Utils.ReactionParticles;
import com.magicmacchiato.vrextensions.Utils.Requirements;
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
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static com.magicmacchiato.vrextensions.VillagersRebornExtensionsMod.LOGGER;

/**
 * Villager-to-villager conversation. Every {@link #INTERVAL_TICKS} ticks the villager looks at who it can currently
 * see. If it has hearts for any of them, it starts right away and picks the target by softmax over the hearts at
 * {@link #HEART_TEMPERATURE} (those with 0 hearts can still be picked). Otherwise it rolls {@link #CHANCE} and picks uniformly. The two of them talk
 * through the dialogue graphs, at most {@link #MAX_TURNS} lines.
 * <p>
 * Each turn is one line (a dialogue node) from the speaker to the listener:
 * <ol>
 *   <li>topics of the line that the listener is interested in raise its eagerness ({@link VillagerInterests});</li>
 *   <li>the line's semantic is dotted with the listener's personality reaction (same as a player's choice in
 *       {@code ChooseDialoguePacket}) and the result moves the listener's mood, with the same happy / angry particles;</li>
 *   <li>the total mood change updates the listener's hearts for the speaker ({@link DialogueHearts});</li>
 *   <li>the listener randomly picks the next line among the node's next_choice that its requirements allow;
 *       the roles swap;</li>
 *   <li>no allowed next line, or {@link #MAX_TURNS} lines said, ends the conversation.</li>
 * </ol>
 * Requirements are checked for the one who picks / opens a line: its mood, its hearts for the other one (stored
 * {@code VillagerRelationship}, 0 for strangers) and the resulting status. Lines are only logged, there is no UI.
 * <p>
 * The target may also be a player. Then the villager walks up to them and opens the real dialogue screen on their
 * client ({@link OpenDialoguePacket}); from there the normal player-villager flow runs and this behavior is done.
 * <p>
 * After a conversation ends, whatever the reason, both villagers wait {@link #COOLDOWN_TICKS} (3 minutes): they
 * neither start one nor get picked by others ({@link TalkCooldownHolder}; also true while the conversation runs).
 * This applies with or without hearts. Players have no cooldown.
 * <p>
 * A villager also takes part in at most {@link #DAILY_CAP} conversations per Minecraft day, whether it starts them or
 * is picked. A conversation counts once its first line is said, or the player's dialogue screen is opened; giving up
 * on the way does not count. Dialogues the player starts are neither counted nor limited. While walking to the target it gives up if it cannot get close (see {@link #approach}).
 * <p>
 * Uses only LOOK_TARGET / WALK_TARGET, not INTERACTION_TARGET, so vanilla's villager-to-villager trading gate stays
 * untouched. Starts only while the initiator is in the IDLE activity and the partner is not asleep.
 */
public class RandomConversation extends Behavior<Villager> {
    public static final int INTERVAL_TICKS = 100;
    /** Chance per check when the villager has no hearts for anyone it sees. */
    public static final float CHANCE = 0.01F;
    /** MOCK - softmax temperature over hearts: higher spreads the choice more evenly. */
    public static final float HEART_TEMPERATURE = 7.0F;
    /** Most conversations one villager takes part in per Minecraft day. */
    public static final int DAILY_CAP = 3;
    /** Most lines said in one conversation. */
    public static final int MAX_TURNS = 10;
    /** Ticks between two lines. */
    public static final int TURN_TICKS = 30;
    private static final double TALK_DISTANCE_SQR = 3.0D * 3.0D;
    private static final double GIVE_UP_DISTANCE_SQR = 16.0D * 16.0D;
    private static final float WALK_SPEED = 0.5F;
    /** After a conversation ends (for any reason, including giving up) neither villager takes part in another. */
    public static final int COOLDOWN_TICKS = 3 * 60 * 20;
    /** Give up if still not within talking distance this long after setting off. */
    private static final int APPROACH_TIMEOUT_TICKS = 15 * 20;
    /** Give up if, over this long, the distance to the target has not shrunk by {@link #MIN_PROGRESS_SQR}. */
    private static final int STUCK_TICKS = 5 * 20;
    private static final double MIN_PROGRESS_SQR = 1.0D;

    /** Longest a run can last; slack on top of the turns covers the initiator walking over first. */
    private static final int MAX_DURATION_TICKS = (MAX_TURNS + 4) * TURN_TICKS;

    private long nextRoll;

    // State of the running conversation.
    private Villager partner;
    /** Set instead of {@link #partner} when the villager talks to a player (opens the dialogue GUI). */
    private ServerPlayer playerTarget;
    /** The line about to be said. */
    private Dialogue line;
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
        super(ImmutableMap.of(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT),
                TURN_TICKS, MAX_DURATION_TICKS);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager owner) {
        long now = level.getGameTime();
        if (now < this.nextRoll) return false;
        this.nextRoll = now + INTERVAL_TICKS;
        if (!canTalk(owner) || owner.isSleeping() || !owner.getBrain().isActive(Activity.IDLE)) return false;

        LivingEntity target = pickTarget(owner);
        if (target == null) return false;
        if (target instanceof ServerPlayer player) {
            // The dialogue screen drives the conversation; the villager only has to walk up and open it.
            this.playerTarget = player;
            return true;
        }
        Villager other = (Villager) target;
        Dialogue opener = DialogueManager.randomOpener(gate(owner, other), gate(other, owner));
        if (opener == null) return false;

        this.partner = other;
        this.speaker = owner;
        this.listener = other;
        this.line = opener;
        return true;
    }

    /**
     * A villager or player the owner can currently see, or null if there is none or the no-hearts roll failed. See
     * the class doc for the selection rule.
     */
    private static LivingEntity pickTarget(Villager owner) {
        NearestVisibleLivingEntities visible =
                owner.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElse(null);
        if (visible == null) return null;
        List<LivingEntity> candidates = new ArrayList<>();
        for (LivingEntity e : visible.findAll(x -> true)) {
            if (e instanceof Villager v) {
                if ((v.getType() == EntityType.VILLAGER || v instanceof FamilyVillager)
                        && v != owner && v.isAlive() && !v.isSleeping()
                        && canTalk(v)) candidates.add(v);
            } else if (e instanceof ServerPlayer p && isAvailable(p, owner)) {
                candidates.add(p);
            }
        }
        if (candidates.isEmpty()) return null;

        double[] hearts = candidates.stream().mapToDouble(c -> heartsFor(owner, c)).toArray();
        if (Arrays.stream(hearts).anyMatch(h -> h > 0)) {
            return Random.pickWeighted(candidates, Random.softmax(hearts, HEART_TEMPERATURE), owner.getRandom());
        }
        return Random.chance(owner.getRandom(), CHANCE) ? Random.pick(candidates, owner.getRandom()) : null;
    }

    /** The owner's hearts for the target: its stored relationship for a villager, the base mod's for a player. */
    private static float heartsFor(Villager owner, LivingEntity target) {
        if (target instanceof ServerPlayer player) return Requirements.exactHearts(owner, player);
        return owner instanceof RelationshipHolder holder ? holder.getVrRelationship().get(target.getUUID()) : 0;
    }

    /** Off cooldown and under the daily cap. */
    private static boolean canTalk(Villager villager) {
        if (!(villager instanceof TalkCooldownHolder holder)) return true;
        Level level = villager.level();
        return level.getGameTime() >= holder.getVrNextTalk() && holder.getVrTalks(dayOf(level)) < DAILY_CAP;
    }

    private static long dayOf(Level level) {
        return level.getDayTime() / Level.TICKS_PER_DAY;
    }

    private static void countTalk(Villager villager) {
        if (villager instanceof TalkCooldownHolder holder) holder.addVrTalk(dayOf(villager.level()));
    }

    private static void setNextTalk(@Nullable Villager villager, long gameTime) {
        if (villager instanceof TalkCooldownHolder holder) holder.setVrNextTalk(gameTime);
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
     * mood (read live, it changes during the conversation), its stored hearts for the other and the status that
     * follows from them.
     */
    private static Predicate<Dialogue> gate(Villager chooser, Villager other) {
        int hearts = 0;
        RelationshipStatus status = RelationshipStatus.STRANGER;
        if (chooser instanceof RelationshipHolder holder) {
            hearts = (int) holder.getVrRelationship().get(other.getUUID());
            status = holder.getVrRelationship().statusOf(chooser, other.getUUID());
        }
        Mood mood = chooser instanceof MoodHolder holder ? holder.getVrMood() : new Mood();
        int h = hearts;
        RelationshipStatus st = status;
        return node -> node.requirement().isMetBy(h, st, mood);
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
        // Marks both as busy. The end time is an upper bound in case stop() never runs (initiator unloaded).
        long busyUntil = gameTime + MAX_DURATION_TICKS + COOLDOWN_TICKS;
        setNextTalk(owner, busyUntil);
        setNextTalk(this.partner, busyUntil);
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
        countTalk(owner);
        this.dialogueOpened = true;
        this.finished = true;
    }

    /** Says the current line: the listener reacts, then picks the next line or the conversation ends. */
    private void speakLine(ServerLevel level) {
        LOGGER.info("[{} -> {}] {}", this.speaker.getName().getString(), this.listener.getName().getString(),
                this.line.text());
        this.turns++;
        if (this.turns == 1) {
            countTalk(this.speaker);
            countTalk(this.listener);
        }

        float moodBefore = this.listener instanceof MoodHolder before ? before.getVrMood().total() : 0f;

        // Before the reaction, so its log and sync below include the new eagerness.
        VillagerInterests.hear(this.listener, this.line.conversationTopics());

        VillagerPersonality personality = personalityOf(this.listener);
        if (this.line.semantic() != null && personality != null && this.listener instanceof MoodHolder holder) {
            float reaction = Mood.reaction(personality, this.line.semantic());
            holder.getVrMood().applyReaction(reaction);
            ReactionParticles.spawn(level, this.listener, reaction);
            holder.getVrMood().logState(this.listener.getUUID());
            MoodSync.sendToDialoguePlayer(this.listener);
        }

        if (this.listener instanceof MoodHolder after) {
            DialogueHearts.applyBetweenVillagers(this.listener, this.speaker, after.getVrMood().total() - moodBefore);
        }

        if (this.turns >= MAX_TURNS) {
            this.finished = true;
            return;
        }
        // The mood has just changed, so the listener's requirements are evaluated afterwards.
        Dialogue next = DialogueManager.randomNext(this.line, gate(this.listener, this.speaker));
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
        setNextTalk(owner, gameTime + COOLDOWN_TICKS);
        setNextTalk(this.partner, gameTime + COOLDOWN_TICKS);
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
