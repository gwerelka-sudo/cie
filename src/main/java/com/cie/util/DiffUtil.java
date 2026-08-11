package com.cie.util;

import net.minecraft.component.ComponentMap;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Утилита для /cie diff — сравнивает наборы компонентов двух предметов
 * (обычно главная рука и оффхенд), чтобы понять, например, почему они
 * не стакаются, хотя выглядят одинаково.
 *
 * ВАЖНО: ItemStack.getComponents() возвращает ComponentMap, у которого
 * (в отличие от ранее предполагавшегося прямого Iterable<TypedDataComponent<?>>)
 * нет публичного итератора пар тип+значение — вместо этого перебираем
 * ComponentMap.getTypes() (Set<ComponentType<?>>). В декомпиле ItemStack
 * getTypes() вызывался на приватном поле components (MergedComponentMap),
 * а не напрямую на возвращаемом типе getComponents() — если getTypes() не
 * найдётся на интерфейсе ComponentMap, пришлите ошибку компиляции и
 * сигнатуру интерфейса ComponentMap, поправим на реальный метод перебора.
 */
public final class DiffUtil {

    private DiffUtil() {
    }

    public enum Kind {
        /** Компонент есть только у первого предмета. */
        ONLY_LEFT,
        /** Компонент есть только у второго предмета. */
        ONLY_RIGHT,
        /** Компонент есть у обоих, но значения различаются. */
        DIFFERENT
    }

    public record Entry(Kind kind, String componentId, String leftValue, String rightValue) {
    }

    /** Возвращает список расхождений между left и right. Пустой список = идентичные наборы компонентов. */
    public static List<Entry> diff(ItemStack left, ItemStack right) {
        List<Entry> result = new ArrayList<>();

        ComponentMap leftComponents = left.getComponents();
        ComponentMap rightComponents = right.getComponents();
        Set<ComponentType<?>> leftTypes = leftComponents.getTypes();
        Set<ComponentType<?>> rightTypes = rightComponents.getTypes();

        for (ComponentType<?> type : leftTypes) {
            String id = String.valueOf(type);
            Object leftValue = leftComponents.get(type);
            if (!rightTypes.contains(type)) {
                result.add(new Entry(Kind.ONLY_LEFT, id, String.valueOf(leftValue), null));
                continue;
            }
            Object rightValue = rightComponents.get(type);
            if (!java.util.Objects.equals(leftValue, rightValue)) {
                result.add(new Entry(Kind.DIFFERENT, id, String.valueOf(leftValue), String.valueOf(rightValue)));
            }
        }

        for (ComponentType<?> type : rightTypes) {
            if (!leftTypes.contains(type)) {
                result.add(new Entry(Kind.ONLY_RIGHT, String.valueOf(type), null, String.valueOf(rightComponents.get(type))));
            }
        }

        return result;
    }
}