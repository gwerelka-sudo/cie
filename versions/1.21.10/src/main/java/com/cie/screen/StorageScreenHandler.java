package com.cie.screen;

import com.cie.util.StoragePageUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;

/**
 * Локальный (несинхронизируемый с сервером) ScreenHandler для одной
 * страницы /cie storage — та же схема, что и MouseHistoryScreenHandler:
 * реальный GenericContainerScreenHandler поверх независимого
 * SimpleInventory, заполненного копиями содержимого страницы при
 * открытии. Изменения сохраняются обратно на диск явным вызовом
 * {@link StoragePageUtil#savePage} при закрытии экрана
 * (см. StorageScreen.removed()), а НЕ автоматически.
 */
public class StorageScreenHandler extends GenericContainerScreenHandler {

    public static final int SYNC_ID = -2719;
    public static final int ROWS = 6;

    private int pageIndex;
    private boolean locked;

    public StorageScreenHandler(PlayerInventory playerInventory, int pageIndex, StoragePageUtil.Page page) {
        super(ScreenHandlerType.GENERIC_9X6, SYNC_ID, playerInventory, buildInventory(page), ROWS);
        this.pageIndex = pageIndex;
        this.locked = page.locked;
    }

    private static SimpleInventory buildInventory(StoragePageUtil.Page page) {
        SimpleInventory inventory = new SimpleInventory(StoragePageUtil.SLOTS_PER_PAGE);
        for (int i = 0; i < StoragePageUtil.SLOTS_PER_PAGE; i++) {
            ItemStack stack = page.items[i];
            inventory.setStack(i, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        return inventory;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public boolean isLocked() {
        return locked;
    }

    /**
     * Перезагружает содержимое ВЕРХНИХ 54 слотов НА МЕСТЕ (без пересоздания
     * ScreenHandler/Screen) — используется для мгновенного переключения
     * страницы по вводу номера/кнопкам, не теряя фокус текстовых полей.
     */
    public void reloadFromPage(int newPageIndex, StoragePageUtil.Page page) {
        this.pageIndex = newPageIndex;
        this.locked = page.locked;
        for (int i = 0; i < StoragePageUtil.SLOTS_PER_PAGE; i++) {
            ItemStack stack = page.items[i];
            this.getSlot(i).setStack(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
    }

    /** Текущее содержимое верхних 54 слотов (страница), для сохранения на диск. */
    public ItemStack[] snapshotTopSlots() {
        ItemStack[] result = new ItemStack[StoragePageUtil.SLOTS_PER_PAGE];
        for (int i = 0; i < StoragePageUtil.SLOTS_PER_PAGE; i++) {
            ItemStack stack = this.slots.get(i).getStack();
            result[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }
        return result;
    }
}