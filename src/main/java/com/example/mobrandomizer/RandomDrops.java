package com.example.mobrandomizer;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class RandomDrops {
    private static final Set<Item> BLACKLIST = Set.of(
            Items.AIR, Items.COMMAND_BLOCK, Items.CHAIN_COMMAND_BLOCK, Items.REPEATING_COMMAND_BLOCK,
            Items.COMMAND_BLOCK_MINECART, Items.STRUCTURE_BLOCK, Items.STRUCTURE_VOID, Items.JIGSAW,
            Items.BARRIER, Items.LIGHT, Items.DEBUG_STICK, Items.KNOWLEDGE_BOOK);

    private static List<Item> pool;

    private static List<Item> pool(ServerWorld world) {
        if (pool == null) {
            List<Item> list = new ArrayList<>();
            for (Item item : Registries.ITEM) {
                if (BLACKLIST.contains(item)) continue;
                if (!item.isEnabled(world.getEnabledFeatures())) continue;
                list.add(item);
            }
            pool = list;
        }
        return pool;
    }

    public static void dropFor(LivingEntity victim, ServerWorld world, DamageSource source) {
        Random random = world.getRandom();
        boolean legendary = victim.getCommandTags().contains(MobRandomizer.TAG_LEGENDARY);

        int looting = 0;
        Entity attacker = source.getAttacker();
        if (attacker instanceof LivingEntity living) {
            Optional<RegistryEntry.Reference<Enchantment>> ench = world.getRegistryManager()
                    .get(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.LOOTING);
            if (ench.isPresent()) {
                looting = EnchantmentHelper.getLevel(ench.get(), living.getMainHandStack());
            }
        }
        if (legendary) looting += 2;

        int count = 1 + random.nextInt(3);           // 1 a 3
        if (looting > 0) count += random.nextInt(looting + 1); // Looting: hasta +nivel
        if (legendary) count += 1;                    // loot algo mejor

        List<Item> items = pool(world);
        for (int i = 0; i < count; i++) {
            ItemStack stack = new ItemStack(items.get(random.nextInt(items.size())));
            maybeEnchant(stack, world, random, legendary);
            victim.dropStack(stack);
        }
    }

    private static void maybeEnchant(ItemStack stack, ServerWorld world, Random random, boolean legendary) {
        boolean book = stack.isOf(Items.ENCHANTED_BOOK);
        if (!book && !(stack.isEnchantable() && random.nextFloat() < (legendary ? 0.6f : 0.35f))) return;

        Registry<Enchantment> registry = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        Optional<RegistryEntry.Reference<Enchantment>> pick = registry.getRandom(random);
        if (pick.isEmpty()) return;
        Enchantment e = pick.get().value();
        int level = 1 + random.nextInt(Math.max(1, e.getMaxLevel()));

        ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        builder.add(pick.get(), level);
        stack.set(book ? DataComponentTypes.STORED_ENCHANTMENTS : DataComponentTypes.ENCHANTMENTS, builder.build());
    }

    private RandomDrops() {}
}
