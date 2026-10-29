package com.example.mobrandomizer;

import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class MobRandomizer implements ModInitializer {
    public static final String MOD_ID = "mobrandomizer";
    public static final String TAG_LEGENDARY = "mr_legendary";
    public static final String TAG_ROLLED = "mr_rolled";
    public static final String OBJECTIVE = "mob_kills";

    /** 1 de cada 200 */
    public static final int LEGENDARY_ODDS = 200;
    /** 20% de dar un corazón extra */
    public static final float HEART_CHANCE = 0.20f;
    /** Corazón = 2 puntos de vida */
    public static final double HEART_VALUE = 2.0;

    private static final Identifier LEGENDARY_HEALTH_ID = Identifier.of(MOD_ID, "legendary_health");
    private static final Identifier HEARTS_ID = Identifier.of(MOD_ID, "bonus_hearts");

    public static final AttachmentType<Integer> BONUS_HEARTS = AttachmentRegistry.create(
            Identifier.of(MOD_ID, "bonus_hearts_count"),
            b -> b.persistent(Codec.INT).copyOnDeath());

    @Override
    public void onInitialize() {
        // --- Scoreboard lateral de kills ---
        ServerLifecycleEvents.SERVER_STARTED.register(this::setupScoreboard);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            Scoreboard sb = server.getScoreboard();
            ScoreboardObjective obj = sb.getNullableObjective(OBJECTIVE);
            if (obj != null) sb.getOrCreateScore(p, obj); // aparece con 0 desde el principio
            applyBonusHearts(p);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((old, np, alive) -> applyBonusHearts(np));

        // --- Mobs legendarios ---
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof MobEntity mob)) return;
            if (entity instanceof EnderDragonEntity || entity instanceof WitherEntity) return;
            if (mob.getCommandTags().contains(TAG_ROLLED)) return;
            mob.addCommandTag(TAG_ROLLED);
            if (world.getRandom().nextInt(LEGENDARY_ODDS) != 0) return;

            mob.addCommandTag(TAG_LEGENDARY);
            mob.setGlowing(true); // contorno visible a través de paredes
            mob.setCustomName(Text.literal("★ ").append(mob.getType().getName()).formatted(Formatting.GOLD));
            mob.setCustomNameVisible(true);
            EntityAttributeInstance hp = mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            if (hp != null) {
                hp.removeModifier(LEGENDARY_HEALTH_ID);
                // +400% sobre la base = vida x5
                hp.addPersistentModifier(new EntityAttributeModifier(LEGENDARY_HEALTH_ID, 4.0,
                        EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                mob.setHealth(mob.getMaxHealth());
            }
        });

        // --- Kills + corazón extra ---
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof PlayerEntity) return;
            if (!(source.getAttacker() instanceof ServerPlayerEntity player)) return;

            MinecraftServer server = player.getServer();
            if (server != null) {
                Scoreboard sb = server.getScoreboard();
                ScoreboardObjective obj = sb.getNullableObjective(OBJECTIVE);
                if (obj != null) sb.getOrCreateScore(player, obj).incrementScore();
            }

            if (entity.getCommandTags().contains(TAG_LEGENDARY)
                    && player.getRandom().nextFloat() < HEART_CHANCE) {
                int n = player.getAttachedOrElse(BONUS_HEARTS, 0) + 1;
                player.setAttached(BONUS_HEARTS, n);
                applyBonusHearts(player);
                player.sendMessage(Text.literal("¡Has ganado un contenedor de corazón extra! (+" + n + ")")
                        .formatted(Formatting.RED), false);
            }
        });
    }

    private void setupScoreboard(MinecraftServer server) {
        Scoreboard sb = server.getScoreboard();
        ScoreboardObjective obj = sb.getNullableObjective(OBJECTIVE);
        if (obj == null) {
            obj = sb.addObjective(OBJECTIVE, ScoreboardCriterion.DUMMY, Text.literal("Mob Kills"),
                    ScoreboardCriterion.RenderType.INTEGER, true, null);
        }
        sb.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, obj);
    }

    public static void applyBonusHearts(ServerPlayerEntity p) {
        EntityAttributeInstance hp = p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (hp == null) return;
        hp.removeModifier(HEARTS_ID);
        int n = p.getAttachedOrElse(BONUS_HEARTS, 0);
        if (n > 0) {
            hp.addPersistentModifier(new EntityAttributeModifier(HEARTS_ID, n * HEART_VALUE,
                    EntityAttributeModifier.Operation.ADD_VALUE));
        }
    }
}
