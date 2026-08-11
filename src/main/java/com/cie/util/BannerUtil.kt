package com.cie.util

import com.cie.util.BannerUtil
import net.minecraft.block.entity.BannerPattern
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.BannerPatternsComponent
import net.minecraft.item.ItemStack
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.util.DyeColor
import java.util.ArrayList
import kotlin.math.max
import kotlin.math.min

/**
 * Утилита для работы с узорами баннера (`minecraft:banner_patterns`)
 * и его базовым цветом (`minecraft:base_color`).
 *
 *
 * "minecraft:base" в качестве идентификатора узора в команде /banner add
 * трактуется отдельно — как смена базового цвета баннера, а не как
 * добавление слоя узора.
 */
object BannerUtil {
    fun getLayers(stack: ItemStack): MutableList<BannerPatternsComponent.Layer?> {
        val comp = stack.get<BannerPatternsComponent?>(DataComponentTypes.BANNER_PATTERNS)
        return if (comp != null) ArrayList<BannerPatternsComponent.Layer?>(comp.layers()) else ArrayList<BannerPatternsComponent.Layer?>()
    }

    private fun setLayers(stack: ItemStack, layers: MutableList<BannerPatternsComponent.Layer?>?) {
        stack.set<BannerPatternsComponent?>(DataComponentTypes.BANNER_PATTERNS, BannerPatternsComponent(layers))
    }

    fun addPattern(stack: ItemStack, pattern: RegistryEntry<BannerPattern?>?, color: DyeColor?) {
        val layers = getLayers(stack)
        layers.add(BannerPatternsComponent.Layer(pattern, color))
        setLayers(stack, layers)
    }

    fun insertPattern(stack: ItemStack, index: Int, pattern: RegistryEntry<BannerPattern?>?, color: DyeColor?) {
        val layers = getLayers(stack)
        val clamped: Int = max(0, min(index, layers.size))
        layers.add(clamped, BannerPatternsComponent.Layer(pattern, color))
        setLayers(stack, layers)
    }

    fun removePattern(stack: ItemStack, index: Int) {
        val layers = getLayers(stack)
        if (index < 0 || index >= layers.size) return
        layers.removeAt(index)
        setLayers(stack, layers)
    }

    fun getBaseColor(stack: ItemStack): DyeColor? {
        return stack.get<DyeColor?>(DataComponentTypes.BASE_COLOR)
    }

    fun setBaseColor(stack: ItemStack, color: DyeColor): ItemStack {
        val bannerItem = getBannerItemByColor(color)

        // 1. Создаем новый ItemStack с нужным предметом
        val newStack = ItemStack(bannerItem, stack.count)

        // 2. Переносим все компоненты со старого стека
        newStack.applyChanges(stack.componentChanges)

        // 3. Устанавливаем новый базовый цвет
        newStack.set(DataComponentTypes.BASE_COLOR, color)

        return newStack
    }

    private fun getBannerItemByColor(color: DyeColor): net.minecraft.item.Item {
        return when (color) {
            DyeColor.WHITE -> net.minecraft.item.Items.WHITE_BANNER
            DyeColor.ORANGE -> net.minecraft.item.Items.ORANGE_BANNER
            DyeColor.MAGENTA -> net.minecraft.item.Items.MAGENTA_BANNER
            DyeColor.LIGHT_BLUE -> net.minecraft.item.Items.LIGHT_BLUE_BANNER
            DyeColor.YELLOW -> net.minecraft.item.Items.YELLOW_BANNER
            DyeColor.LIME -> net.minecraft.item.Items.LIME_BANNER
            DyeColor.PINK -> net.minecraft.item.Items.PINK_BANNER
            DyeColor.GRAY -> net.minecraft.item.Items.GRAY_BANNER
            DyeColor.LIGHT_GRAY -> net.minecraft.item.Items.LIGHT_GRAY_BANNER
            DyeColor.CYAN -> net.minecraft.item.Items.CYAN_BANNER
            DyeColor.PURPLE -> net.minecraft.item.Items.PURPLE_BANNER
            DyeColor.BLUE -> net.minecraft.item.Items.BLUE_BANNER
            DyeColor.BROWN -> net.minecraft.item.Items.BROWN_BANNER
            DyeColor.GREEN -> net.minecraft.item.Items.GREEN_BANNER
            DyeColor.RED -> net.minecraft.item.Items.RED_BANNER
            DyeColor.BLACK -> net.minecraft.item.Items.BLACK_BANNER
        }
    }

    /** Очищает все узоры и сбрасывает базовый цвет баннера до белого.  */
    fun clear(stack: ItemStack) {
        stack.remove<BannerPatternsComponent?>(DataComponentTypes.BANNER_PATTERNS)
        stack.set<DyeColor?>(DataComponentTypes.BASE_COLOR, DyeColor.WHITE)
    }
}