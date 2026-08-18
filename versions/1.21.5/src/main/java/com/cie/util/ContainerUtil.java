package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

import java.util.ArrayList;
import java.util.List;

/**
 * /cie edit container — читает/пишет minecraft:container (ContainerComponent)
 * предмета в руке.
 *
 * ФИКС (после багрепорта): реальный API ContainerComponent (проверено по
 * yarn-1.21.6 javadoc, а не угадано, как в первой версии этого файла):
 *   - НЕТ метода stacks() — только copyTo(DefaultedList<ItemStack>) и
 *     потоковые iterateNonEmpty()/stream()/streamNonEmpty().
 *   - fromStacks(List<ItemStack> stacks) "The stacks are copied into the
 *     component, which will contain copies of all stacks up to the last
 *     non-empty stack" — то есть ПОЗИЦИИ (включая внутренние пустые
 *     слоты-"дырки" между предметами) сохраняются, если передать список
 *     С пустыми ItemStack.EMPTY на своих местах, а не компактный список
 *     без них. Раньше сюда передавался список БЕЗ пустых слотов
 *     (компактный), из-за чего при переоткрытии редактора предметы
 *     "съезжали" в первые слоты подряд — это и был баг "кладу в разные
 *     слоты, а они все в 1 слот".
 *
 * Поэтому теперь везде работаем со списком РОВНО длины SLOT_COUNT, где
 * пустая позиция — буквально ItemStack.EMPTY на своём индексе.
 */
public final class ContainerUtil {

    private ContainerUtil() {
    }

    /** 27 слотов — сундук/бочка/шалкер/трапп-сундук и т.п. (3 ряда). Для мелких контейнеров (воронка/полка/книжная полка) см. отдельные константы в CIECommand при подключении конкретных типов. */
    public static final int SLOT_COUNT = 27;

    /** Список длиной slotCount, пустые слоты — ItemStack.EMPTY на СВОИХ позициях (позиции сохраняются 1:1). */
    public static List<ItemStack> getStacks(ItemStack stack, int slotCount) {
        DefaultedList<ItemStack> target = DefaultedList.ofSize(slotCount, ItemStack.EMPTY);
        ContainerComponent component = stack.get(DataComponentTypes.CONTAINER);
        if (component != null) {
            component.copyTo(target);
        }
        List<ItemStack> result = new ArrayList<>(slotCount);
        for (ItemStack s : target) {
            result.add(s.isEmpty() ? ItemStack.EMPTY : s.copy());
        }
        return result;
    }

    public static List<ItemStack> getStacks(ItemStack stack) {
        return getStacks(stack, SLOT_COUNT);
    }

    /** stacks — список позиционный (см. getStacks). Пустые слоты передавать как ItemStack.EMPTY на своих местах, а не пропускать. */
    public static void setStacks(ItemStack stack, List<ItemStack> stacks) {
        boolean anyNonEmpty = false;
        for (ItemStack s : stacks) {
            if (!s.isEmpty()) {
                anyNonEmpty = true;
                break;
            }
        }
        if (!anyNonEmpty) {
            stack.remove(DataComponentTypes.CONTAINER);
        } else {
            stack.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(stacks));
        }
    }

    public static void clear(ItemStack stack) {
        stack.remove(DataComponentTypes.CONTAINER);
    }

    /** 0-based слот. EMPTY, если слота нет/пуст. */
    public static ItemStack getSlot(ItemStack stack, int index) {
        if (index < 0 || index >= SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        return getStacks(stack, SLOT_COUNT).get(index).copy();
    }

    /** Кладёт предмет в первый ПО ПОЗИЦИИ свободный слот. false, если все SLOT_COUNT слотов заняты. */
    public static boolean addItem(ItemStack stack, ItemStack item) {
        List<ItemStack> stacks = getStacks(stack, SLOT_COUNT);
        for (int i = 0; i < stacks.size(); i++) {
            if (stacks.get(i).isEmpty()) {
                stacks.set(i, item.copy());
                setStacks(stack, stacks);
                return true;
            }
        }
        return false;
    }
}