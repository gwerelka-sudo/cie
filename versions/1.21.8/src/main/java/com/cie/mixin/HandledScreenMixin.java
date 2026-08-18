package com.cie.mixin;

import com.cie.util.MouseHistoryUtil;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ловит каждый клик ЛКМ/ПКМ по слоту в любом экране-контейнере
 * (HandledScreen — базовый класс для сундуков, верстака, инвентаря игрока
 * и т.д.) и сохраняет копию предмета в MouseHistoryUtil.
 *
 * Ловим ТОЛЬКО на HEAD (что лежало в слоте ДО клика) — этого одного
 * достаточно: покрывает обычный подбор предмета, шифт-клик (предмет
 * улетает квик-мувом, но в момент клика он ещё в слоте) и клонирование
 * (слот не пустеет, содержимое всё ещё видно на HEAD). Специально НЕ
 * ловим дополнительно на RETURN (курсор после клика) — раньше так было
 * сделано "для подстраховки", но при обычном подборе предмета И HEAD, И
 * RETURN видят один и тот же предмет одновременно, что даёт ДВЕ записи
 * в историю на один клик. RETURN также ложно срабатывает на "положить
 * предмет обратно" (курсор просто продолжает содержать остаток),
 * поэтому от него отказались.
 *
 * У HandledScreen ТРИ перегрузки метода "onMouseClick": (Click),
 * (Slot, SlotActionType) и нужная нам (Slot, int, int, SlotActionType).
 * Из-за этого имени "onMouseClick" одного недостаточно — Mixin не может
 * понять, к какой перегрузке цепляться, и падает с
 * InvalidInjectionException. Поэтому здесь указан полный JVM-дескриптор
 * метода явной строкой (см. ON_MOUSE_CLICK_DESC).
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    private static final String ON_MOUSE_CLICK_DESC =
            "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V";

    @Inject(method = ON_MOUSE_CLICK_DESC, at = @At("HEAD"))
    private void cie$onSlotClick(Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
        if (slot == null || !slot.hasStack()) {
            return;
        }
        MouseHistoryUtil.record(slot.getStack());
    }
}