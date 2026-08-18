package com.cie.util;

import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.ItemStack;

import java.util.*;

/**
 * С 1.21.5 видимость отдельных строк тултипа (чары, атрибуты, unbreakable,
 * can_break/can_place_on, trim, банки/патерны и т.д.) больше не хранится по
 * одному show_in_tooltip-флагу на каждом компоненте, а централизована в
 * одном компоненте DataComponentTypes.TOOLTIP_DISPLAY (класс
 * TooltipDisplayComponent): hideTooltip (скрыть тултип целиком) +
 * набор ComponentType<?>, которые нужно спрятать из тултипа.
 *
 * РИСК ДЛЯ КОМПИЛЯЦИИ: это самый свежий из всех рефакторингов компонентов,
 * так что имя конструктора/геттеров может отличаться в твоей ревизии
 * маппингов — если не соберётся, открой genSources → TooltipDisplayComponent
 * и поправь только этот файл.
 */
public final class TooltipDisplayUtil {

    private TooltipDisplayUtil() {
    }

    /** Человекочитаемые имена -> реальные типы компонентов, которые можно спрятать из тултипа. */
    public static final Map<String, ComponentType<?>> HIDEABLE = new LinkedHashMap<>();

    static {
        HIDEABLE.put("enchantments", DataComponentTypes.ENCHANTMENTS);
        HIDEABLE.put("stored_enchantments", DataComponentTypes.STORED_ENCHANTMENTS);
        HIDEABLE.put("attribute_modifiers", DataComponentTypes.ATTRIBUTE_MODIFIERS);
        HIDEABLE.put("unbreakable", DataComponentTypes.UNBREAKABLE);
        HIDEABLE.put("can_break", DataComponentTypes.CAN_BREAK);
        HIDEABLE.put("can_place_on", DataComponentTypes.CAN_PLACE_ON);
        HIDEABLE.put("dyed_color", DataComponentTypes.DYED_COLOR);
        HIDEABLE.put("trim", DataComponentTypes.TRIM);
        HIDEABLE.put("jukebox_playable", DataComponentTypes.JUKEBOX_PLAYABLE);
        HIDEABLE.put("banner_patterns", DataComponentTypes.BANNER_PATTERNS);
        HIDEABLE.put("potion_contents", DataComponentTypes.POTION_CONTENTS);
    }

    public static TooltipDisplayComponent getOrCreate(ItemStack stack) {
        TooltipDisplayComponent existing = stack.get(DataComponentTypes.TOOLTIP_DISPLAY);
        return existing != null ? existing : TooltipDisplayComponent.DEFAULT;
    }

    public static void save(ItemStack stack, TooltipDisplayComponent component) {
        stack.set(DataComponentTypes.TOOLTIP_DISPLAY, component);
    }

    public static TooltipDisplayComponent withHideWholeTooltip(TooltipDisplayComponent component, boolean hide) {
        return new TooltipDisplayComponent(hide, component.hiddenComponents());
    }

    public static TooltipDisplayComponent withHiddenComponent(TooltipDisplayComponent component, ComponentType<?> type, boolean hidden) {
        SequencedSet<ComponentType<?>> set = new LinkedHashSet<>(component.hiddenComponents());
        if (hidden) {
            set.add(type);
        } else {
            set.remove(type);
        }
        return new TooltipDisplayComponent(component.hideTooltip(), set);
    }
}
