package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;

import java.util.ArrayList;
import java.util.List;

/**
 * Util для /cie edit container — компонент minecraft:container хранит
 * содержимое предметов-контейнеров (сундук, бочка, шалкер, воронка,
 * раздатчик и т.д. в виде ПРЕДМЕТА).
 *
 * У ContainerComponent НЕТ фиксированной "вместимости" сама по себе —
 * это просто список, обрезанный по последнему непустому слоту
 * (ContainerComponent.fromStacks). Реальная вместимость (27 у сундука,
 * 5 у воронки и т.д.) — знание о конкретном блоке, которого в самом
 * компоненте нет, поэтому здесь она определяется по id предмета.
 */
public final class ContainerEditUtil {

    private ContainerEditUtil() {
    }

    /** Вместимость контейнера по id предмета, или -1, если это не контейнер (в нашем понимании). */
    public static int getCapacity(ItemStack stack) {
        Identifier id = Registries.ITEM.getId(stack.getItem());
        String path = id.getPath();

        if (path.endsWith("shulker_box")) return 27;
        if (path.equals("chest") || path.equals("trapped_chest") || path.equals("barrel")) return 27;
        if (path.equals("hopper")) return 5;
        if (path.equals("dispenser") || path.equals("dropper") || path.equals("crafter")) return 9;
        if (path.equals("brewing_stand")) return 5;
        if (path.equals("furnace") || path.equals("blast_furnace") || path.equals("smoker")) return 3;
        return -1;
    }

    public static boolean isContainer(ItemStack stack) {
        return getCapacity(stack) > 0;
    }

    /** Содержимое, дополненное пустыми стеками до capacity (для удобной работы по индексу). */
    public static List<ItemStack> getContents(ItemStack stack, int capacity) {
        ContainerComponent comp = stack.get(DataComponentTypes.CONTAINER);
        DefaultedList<ItemStack> list = DefaultedList.ofSize(capacity, ItemStack.EMPTY);
        if (comp != null) {
            comp.copyTo(list);
        }
        List<ItemStack> result = new ArrayList<>(capacity);
        result.addAll(list);
        return result;
    }

    public static void setContents(ItemStack stack, List<ItemStack> contents) {
        ContainerComponent comp = ContainerComponent.fromStacks(contents);
        boolean allEmpty = true;
        for (ItemStack s : contents) {
            if (!s.isEmpty()) {
                allEmpty = false;
                break;
            }
        }
        if (allEmpty) {
            stack.remove(DataComponentTypes.CONTAINER);
        } else {
            stack.set(DataComponentTypes.CONTAINER, comp);
        }
    }

    public static void clear(ItemStack stack) {
        stack.remove(DataComponentTypes.CONTAINER);
    }

    /** Кладёт предмет в первый свободный слот (в пределах capacity). Возвращает false, если контейнер полон. */
    public static boolean addToFirstFreeSlot(ItemStack containerStack, int capacity, ItemStack toAdd) {
        List<ItemStack> contents = getContents(containerStack, capacity);
        for (int i = 0; i < capacity; i++) {
            if (contents.get(i).isEmpty()) {
                contents.set(i, toAdd.copy());
                setContents(containerStack, contents);
                return true;
            }
        }
        return false;
    }
}
