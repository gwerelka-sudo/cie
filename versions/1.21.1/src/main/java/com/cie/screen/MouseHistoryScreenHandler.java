package com.cie.screen;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;

import java.util.List;

/**
 * Настоящий {@link GenericContainerScreenHandler} (тот же класс, что и у
 * двойного сундука), но полностью клиентский: верхние 54 слота — это
 * НЕЗАВИСИМЫЙ {@link SimpleInventory}, заполненный КОПИЯМИ предметов из
 * MouseHistoryUtil на момент открытия, а не сама история напрямую.
 * Поэтому взятие предмета отсюда не убирает его из истории — при
 * следующем открытии /cie mouseHistory там снова будут все те же
 * предметы (осознанное дублирование, как и просили в задаче).
 *
 * syncId выбран заведомо не пересекающимся с реальными серверными
 * (у ванильных контейнеров syncId всегда >= 0), просто для порядка —
 * пакеты на сервер мы всё равно никогда не отправляем (см.
 * MouseHistoryScreen.onMouseClick, который вызывает handler.onSlotClick(...)
 * напрямую вместо client.interactionManager.clickSlot(...)).
 */
public class MouseHistoryScreenHandler extends GenericContainerScreenHandler {

    public static final int SYNC_ID = -2718;
    public static final int ROWS = 6;

    public MouseHistoryScreenHandler(PlayerInventory playerInventory, List<ItemStack> historySnapshot) {
        super(ScreenHandlerType.GENERIC_9X6, SYNC_ID, playerInventory,
                buildInventory(historySnapshot), ROWS);
    }

    private static SimpleInventory buildInventory(List<ItemStack> historySnapshot) {
        SimpleInventory inventory = new SimpleInventory(ROWS * 9);
        for (int i = 0; i < historySnapshot.size() && i < inventory.size(); i++) {
            inventory.setStack(i, historySnapshot.get(i).copy());
        }
        return inventory;
    }
}