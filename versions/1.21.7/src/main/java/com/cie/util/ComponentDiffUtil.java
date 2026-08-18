package com.cie.util;

import net.minecraft.component.ComponentMap;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Даёт список компонентов, которые реально ОТЛИЧАЮТСЯ от дефолтного набора
 * компонентов итема — то есть только те, что явно выставлены/сняты игроком,
 * а не вообще все компоненты смёрженного ComponentMap (там всегда есть и
 * ванильные дефолты предмета, например max_stack_size/rarity/item_model
 * у любого предмета).
 *
 * Используется в /cie export giveCommand, /cie export JSON и в
 * /cie component disable — чтобы явное "снятие" дефолтного компонента
 * (даёт "!component" в /give-синтаксисе) отличалось от простого
 * "компонента никогда не было и его не трогали".
 *
 * Сравнение через свежий ItemStack(item, 1) — тот же трюк, что и в
 * DiffUtil (main vs offhand), только тут "правая" сторона — не второй
 * предмет в руке, а чистый дефолтный стек того же item. См. DiffUtil —
 * оттуда же взят паттерн ComponentMap.getTypes() + .get(type) +
 * Objects.equals для сравнения значений.
 */
public final class ComponentDiffUtil {

    private ComponentDiffUtil() {
    }

    public enum Kind {
        /** Значение отличается от дефолта итема (или компонента не было в дефолте вообще). */
        OVERRIDE,
        /** Компонент есть в дефолтном наборе итема, но на стеке он явно снят (component=!id в /give). */
        REMOVED
    }

    public record Entry(Kind kind, ComponentType<?> type, Object value) {
    }

    /** Список реально изменённых (относительно дефолта итема) компонентов стека. */
    public static List<Entry> diffFromDefault(ItemStack stack) {
        List<Entry> result = new ArrayList<>();

        ItemStack defaultStack = new ItemStack(stack.getItem(), 1);
        ComponentMap stackComponents = stack.getComponents();
        ComponentMap defaultComponents = defaultStack.getComponents();

        Set<ComponentType<?>> stackTypes = stackComponents.getTypes();
        Set<ComponentType<?>> defaultTypes = defaultComponents.getTypes();

        for (ComponentType<?> type : stackTypes) {
            Object value = stackComponents.get(type);
            if (!defaultTypes.contains(type)) {
                result.add(new Entry(Kind.OVERRIDE, type, value));
                continue;
            }
            Object defaultValue = defaultComponents.get(type);
            if (!Objects.equals(value, defaultValue)) {
                result.add(new Entry(Kind.OVERRIDE, type, value));
            }
        }

        for (ComponentType<?> type : defaultTypes) {
            if (!stackTypes.contains(type)) {
                result.add(new Entry(Kind.REMOVED, type, null));
            }
        }

        return result;
    }
}
