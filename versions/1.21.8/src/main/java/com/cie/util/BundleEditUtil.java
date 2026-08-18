package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Util для /cie edit bundle — компонент minecraft:bundle_contents.
 * В отличие от ContainerComponent, у баула нет позиционных слотов —
 * это просто список предметов (occupancy считается сама по BundleContentsComponent).
 * Для UI мы всё равно раскладываем его по 54 "виртуальным" слотам
 * (фиксированный размер сетки редактора), а при сохранении просто
 * собираем непустые стеки обратно в список по порядку.
 */
public final class BundleEditUtil {

    private BundleEditUtil() {
    }

    public static final int SLOTS = 54;

    public static List<ItemStack> getContents(ItemStack stack) {
        List<ItemStack> result = new ArrayList<>(SLOTS);
        BundleContentsComponent comp = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
        if (comp != null) {
            comp.stream().forEach(result::add);
        }
        while (result.size() < SLOTS) {
            result.add(ItemStack.EMPTY);
        }
        return result;
    }

    public static void setContents(ItemStack stack, List<ItemStack> contents) {
        List<ItemStack> nonEmpty = new ArrayList<>();
        for (ItemStack s : contents) {
            if (!s.isEmpty()) {
                nonEmpty.add(s.copy());
            }
        }
        if (nonEmpty.isEmpty()) {
            stack.remove(DataComponentTypes.BUNDLE_CONTENTS);
        } else {
            stack.set(DataComponentTypes.BUNDLE_CONTENTS, new BundleContentsComponent(nonEmpty));
        }
    }

    public static void clear(ItemStack stack) {
        stack.remove(DataComponentTypes.BUNDLE_CONTENTS);
    }

    public static boolean addToFirstFreeSlot(ItemStack bundleStack, ItemStack toAdd) {
        List<ItemStack> contents = getContents(bundleStack);
        for (int i = 0; i < SLOTS; i++) {
            if (contents.get(i).isEmpty()) {
                contents.set(i, toAdd.copy());
                setContents(bundleStack, contents);
                return true;
            }
        }
        return false;
    }
}
