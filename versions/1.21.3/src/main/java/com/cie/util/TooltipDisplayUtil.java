package com.cie.util;

import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.JukeboxPlayableComponent;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.item.BlockPredicatesChecker;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Unit;
import net.minecraft.item.equipment.trim.ArmorTrim;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * ВЕРСИЯ 1.21.4: DataComponentTypes.TOOLTIP_DISPLAY (класс
 * TooltipDisplayComponent, централизованный hideTooltip + набор скрытых
 * ComponentType<?>) появился только в 1.21.5 — на этой версии его нет.
 * Видимость каждого компонента в тултипе управляется ИНДИВИДУАЛЬНО, через
 * собственное поле showInTooltip на самом компоненте (подтверждено
 * декомпилом реальных .class файлов): ItemEnchantmentsComponent,
 * AttributeModifiersComponent, UnbreakableComponent, BlockPredicatesChecker
 * (can_break/can_place_on), DyedColorComponent, ArmorTrim (trim),
 * JukeboxPlayableComponent — у ВСЕХ есть withShowInTooltip(boolean).
 *
 * ИСКЛЮЧЕНИЕ: BannerPatternsComponent и PotionContentsComponent на этой
 * версии вообще НЕ несут showInTooltip (подтверждено декомпилом — это
 * простые record без такого поля) — выборочно спрятать именно чары/эффекты
 * зелья или паттерны баннера из тултипа here нельзя, они либо видны, либо
 * скрываются целиком через HIDE_ADDITIONAL_TOOLTIP вместе со всем прочим.
 *
 * Полное скрытие тултипа (hideall) — компонент DataComponentTypes
 * .HIDE_TOOLTIP (Unit, присутствие/отсутствие). "Скрыть дополнительную
 * информацию" (в новой модели — то, что не входит в hideTooltip, но не
 * основной текст) — DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP (тоже Unit).
 * Здесь used только HIDE_TOOLTIP для hideall — HIDE_ADDITIONAL_TOOLTIP не
 * задействован, чтобы не путать семантику с per-component hide/show, у
 * которого свой независимый набор компонентов.
 *
 * Публичный контракт (getOrCreate/save/withHideWholeTooltip/
 * withHiddenComponent/HIDEABLE) сохранён таким же, каким он был на 1.21.5+
 * — TooltipDisplayState здесь заменяет TooltipDisplayComponent как
 * промежуточное представление, а вся реальная работа с компонентами
 * ItemStack спрятана внутри getOrCreate()/save().
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

    /**
     * Промежуточное представление состояния тултипа — замена
     * TooltipDisplayComponent из 1.21.5+ для этой версии. hiddenComponents
     * содержит только те типы из HIDEABLE, чей showInTooltip реально
     * читается false на текущий момент (для banner_patterns/potion_contents
     * — компонентов без showInTooltip — никогда не попадёт в этот набор,
     * поскольку их нельзя скрыть индивидуально).
     */
    public static final class TooltipDisplayState {
        private final boolean hideTooltip;
        private final Set<ComponentType<?>> hiddenComponents;

        private TooltipDisplayState(boolean hideTooltip, Set<ComponentType<?>> hiddenComponents) {
            this.hideTooltip = hideTooltip;
            this.hiddenComponents = hiddenComponents;
        }

        public boolean hideTooltip() {
            return hideTooltip;
        }

        public Set<ComponentType<?>> hiddenComponents() {
            return hiddenComponents;
        }
    }

    public static TooltipDisplayState getOrCreate(ItemStack stack) {
        boolean hideTooltip = stack.contains(DataComponentTypes.HIDE_TOOLTIP);
        Set<ComponentType<?>> hidden = new LinkedHashSet<>();

        ItemEnchantmentsComponent ench = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (ench != null && !ench.isEmpty() && !enchShowInTooltip(ench)) {
            hidden.add(DataComponentTypes.ENCHANTMENTS);
        }
        ItemEnchantmentsComponent storedEnch = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
        if (storedEnch != null && !storedEnch.isEmpty() && !enchShowInTooltip(storedEnch)) {
            hidden.add(DataComponentTypes.STORED_ENCHANTMENTS);
        }
        AttributeModifiersComponent attrs = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (attrs != null && !attrs.showInTooltip()) {
            hidden.add(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        }
        UnbreakableComponent unbreakable = stack.get(DataComponentTypes.UNBREAKABLE);
        if (unbreakable != null && !unbreakable.showInTooltip()) {
            hidden.add(DataComponentTypes.UNBREAKABLE);
        }
        BlockPredicatesChecker canBreak = stack.get(DataComponentTypes.CAN_BREAK);
        if (canBreak != null && !canBreak.showInTooltip()) {
            hidden.add(DataComponentTypes.CAN_BREAK);
        }
        BlockPredicatesChecker canPlace = stack.get(DataComponentTypes.CAN_PLACE_ON);
        if (canPlace != null && !canPlace.showInTooltip()) {
            hidden.add(DataComponentTypes.CAN_PLACE_ON);
        }
        DyedColorComponent dyed = stack.get(DataComponentTypes.DYED_COLOR);
        if (dyed != null && !dyed.showInTooltip()) {
            hidden.add(DataComponentTypes.DYED_COLOR);
        }
        ArmorTrim trim = stack.get(DataComponentTypes.TRIM);
        if (trim != null && !trim.showInTooltip()) {
            hidden.add(DataComponentTypes.TRIM);
        }
        JukeboxPlayableComponent jukebox = stack.get(DataComponentTypes.JUKEBOX_PLAYABLE);
        if (jukebox != null && !jukebox.showInTooltip()) {
            hidden.add(DataComponentTypes.JUKEBOX_PLAYABLE);
        }
        // banner_patterns / potion_contents: нет showInTooltip на этой
        // версии — никогда не добавляются в hidden индивидуально.

        return new TooltipDisplayState(hideTooltip, hidden);
    }

    /**
     * ItemEnchantmentsComponent не хранит showInTooltip напрямую доступным
     * геттером в старых сборках — читаем через сравнение с копией,
     * полученной через withShowInTooltip(true): если объект остался тем
     * же (equals), значит showInTooltip уже был true, иначе — false.
     * Надёжнее, чем полагаться на конкретное имя приватного поля.
     */
    private static boolean enchShowInTooltip(ItemEnchantmentsComponent component) {
        return component.equals(component.withShowInTooltip(true));
    }

    public static void save(ItemStack stack, TooltipDisplayState state) {
        if (state.hideTooltip) {
            stack.set(DataComponentTypes.HIDE_TOOLTIP, Unit.INSTANCE);
        } else {
            stack.remove(DataComponentTypes.HIDE_TOOLTIP);
        }

        ItemEnchantmentsComponent ench = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (ench != null) {
            stack.set(DataComponentTypes.ENCHANTMENTS, ench.withShowInTooltip(!state.hiddenComponents.contains(DataComponentTypes.ENCHANTMENTS)));
        }
        ItemEnchantmentsComponent storedEnch = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
        if (storedEnch != null) {
            stack.set(DataComponentTypes.STORED_ENCHANTMENTS, storedEnch.withShowInTooltip(!state.hiddenComponents.contains(DataComponentTypes.STORED_ENCHANTMENTS)));
        }
        AttributeModifiersComponent attrs = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (attrs != null) {
            stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, attrs.withShowInTooltip(!state.hiddenComponents.contains(DataComponentTypes.ATTRIBUTE_MODIFIERS)));
        }
        UnbreakableComponent unbreakable = stack.get(DataComponentTypes.UNBREAKABLE);
        if (unbreakable != null) {
            stack.set(DataComponentTypes.UNBREAKABLE, unbreakable.withShowInTooltip(!state.hiddenComponents.contains(DataComponentTypes.UNBREAKABLE)));
        }
        BlockPredicatesChecker canBreak = stack.get(DataComponentTypes.CAN_BREAK);
        if (canBreak != null) {
            stack.set(DataComponentTypes.CAN_BREAK, canBreak.withShowInTooltip(!state.hiddenComponents.contains(DataComponentTypes.CAN_BREAK)));
        }
        BlockPredicatesChecker canPlace = stack.get(DataComponentTypes.CAN_PLACE_ON);
        if (canPlace != null) {
            stack.set(DataComponentTypes.CAN_PLACE_ON, canPlace.withShowInTooltip(!state.hiddenComponents.contains(DataComponentTypes.CAN_PLACE_ON)));
        }
        DyedColorComponent dyed = stack.get(DataComponentTypes.DYED_COLOR);
        if (dyed != null) {
            stack.set(DataComponentTypes.DYED_COLOR, dyed.withShowInTooltip(!state.hiddenComponents.contains(DataComponentTypes.DYED_COLOR)));
        }
        ArmorTrim trim = stack.get(DataComponentTypes.TRIM);
        if (trim != null) {
            stack.set(DataComponentTypes.TRIM, trim.withShowInTooltip(!state.hiddenComponents.contains(DataComponentTypes.TRIM)));
        }
        JukeboxPlayableComponent jukebox = stack.get(DataComponentTypes.JUKEBOX_PLAYABLE);
        if (jukebox != null) {
            stack.set(DataComponentTypes.JUKEBOX_PLAYABLE, jukebox.withShowInTooltip(!state.hiddenComponents.contains(DataComponentTypes.JUKEBOX_PLAYABLE)));
        }
        // banner_patterns / potion_contents: нет showInTooltip — сохранять некуда, значение из hiddenComponents для них молча игнорируется.
    }

    public static TooltipDisplayState withHideWholeTooltip(TooltipDisplayState state, boolean hide) {
        return new TooltipDisplayState(hide, state.hiddenComponents);
    }

    public static TooltipDisplayState withHiddenComponent(TooltipDisplayState state, ComponentType<?> type, boolean hidden) {
        Set<ComponentType<?>> set = new LinkedHashSet<>(state.hiddenComponents);
        if (hidden) {
            set.add(type);
        } else {
            set.remove(type);
        }
        return new TooltipDisplayState(state.hideTooltip, set);
    }
}
