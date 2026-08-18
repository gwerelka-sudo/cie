package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.RepairableComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class RepairableUtil {

    public static void clear(ItemStack stack) {
        stack.remove(DataComponentTypes.REPAIRABLE);
    }

    public static List<String> get(ItemStack stack) {
        RepairableComponent comp = stack.get(DataComponentTypes.REPAIRABLE);
        if (comp == null) return Collections.emptyList();
        return comp.items().stream()
                .map(entry -> entry.getKey().isPresent() ? entry.getKey().get().getValue().toString() : entry.toString())
                .collect(Collectors.toList());
    }

    private static List<RegistryEntry<Item>> currentEntries(ItemStack stack) {
        RepairableComponent comp = stack.get(DataComponentTypes.REPAIRABLE);
        if (comp == null) return new ArrayList<>();
        List<RegistryEntry<Item>> list = new ArrayList<>();
        comp.items().forEach(list::add);
        return list;
    }

    // Добавление предмета в список починки (через создание нового компонента)
    public static void add(ItemStack stack, RegistryEntry<Item> itemEntry) {
        List<RegistryEntry<Item>> entries = currentEntries(stack);
        boolean alreadyPresent = entries.stream()
                .anyMatch(e -> e.getKey().isPresent() && itemEntry.getKey().isPresent()
                        && e.getKey().get().equals(itemEntry.getKey().get()));
        if (!alreadyPresent) {
            entries.add(itemEntry);
        }
        stack.set(DataComponentTypes.REPAIRABLE, new RepairableComponent(RegistryEntryList.of(entries)));
    }

    public static void remove(ItemStack stack, RegistryEntry<Item> itemEntry) {
        List<RegistryEntry<Item>> entries = currentEntries(stack);
        entries.removeIf(e -> e.getKey().isPresent() && itemEntry.getKey().isPresent()
                && e.getKey().get().equals(itemEntry.getKey().get()));

        if (entries.isEmpty()) {
            stack.remove(DataComponentTypes.REPAIRABLE);
        } else {
            stack.set(DataComponentTypes.REPAIRABLE, new RepairableComponent(RegistryEntryList.of(entries)));
        }
    }
}