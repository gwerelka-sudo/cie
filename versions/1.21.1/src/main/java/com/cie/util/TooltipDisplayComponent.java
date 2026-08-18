package com.cie.util;

import net.minecraft.component.ComponentType;

import java.util.LinkedHashSet;
import java.util.SequencedSet;

/**
 * Стаб под ванильный {@code net.minecraft.component.type.TooltipDisplayComponent},
 * которого на 1.21.4 не существует (появился только в 1.21.5 вместе с
 * компонентом {@code minecraft:tooltip_display}). API 1-в-1 совпадает
 * с тем, что использует CIECommand — так что сам файл команд трогать
 * не пришлось.
 *
 * На 1.21.4 это НЕ настоящий компонент предмета: реальной видимостью
 * строк тултипа управляют старые булевые show_in_tooltip-флаги на
 * каждом компоненте по отдельности (unbreakable/hide_additional_tooltip
 * и т.д.), которых этот стаб не трогает. Значение просто хранится в NBT
 * через {@link TooltipDisplayUtil}, чтобы команды /cie tooltip ... не
 * падали и оставались согласованными сами с собой.
 */
public final class TooltipDisplayComponent {

    public static final TooltipDisplayComponent DEFAULT =
            new TooltipDisplayComponent(false, new LinkedHashSet<>());

    private final boolean hideTooltip;
    private final SequencedSet<ComponentType<?>> hiddenComponents;

    public TooltipDisplayComponent(boolean hideTooltip, SequencedSet<ComponentType<?>> hiddenComponents) {
        this.hideTooltip = hideTooltip;
        this.hiddenComponents = hiddenComponents;
    }

    public boolean hideTooltip() {
        return hideTooltip;
    }

    public SequencedSet<ComponentType<?>> hiddenComponents() {
        return hiddenComponents;
    }
}
