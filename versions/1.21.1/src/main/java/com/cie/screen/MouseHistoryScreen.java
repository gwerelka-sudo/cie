package com.cie.screen;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

/**
 * Экран истории кликов мышью — визуально и функционально настоящий
 * двойной сундук (54 слота сверху + инвентарь игрока снизу), со всем
 * ванильным drag-n-drop, шифт-кликом и т.д. "бесплатно" за счёт того,
 * что это реальный {@link GenericContainerScreen} поверх
 * {@link MouseHistoryScreenHandler}.
 *
 * Единственное отличие от обычного сундука: {@link #onMouseClick} не
 * шлёт пакет на сервер (как это делает ванильный HandledScreen через
 * client.interactionManager.clickSlot), а выполняет клик НАПРЯМУЮ в
 * локальном handler.onSlotClick(...) — потому что верхние 54 слота не
 * существуют ни на каком сервере, синхронизировать их не с чем.
 * Нижние слоты (инвентарь игрока) в handler указывают на настоящий
 * PlayerInventory, поэтому перекладывание предметов туда-обратно
 * работает как обычно визуально, просто без серверного подтверждения.
 */
public class MouseHistoryScreen extends GenericContainerScreen {

    public MouseHistoryScreen(MouseHistoryScreenHandler handler, PlayerInventory inventory) {
        super(handler, inventory, Text.literal("Mouse History"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType) {
        this.handler.onSlotClick(slot == null ? slotId : slot.id, button, actionType, this.client.player);
    }
}