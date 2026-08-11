package com.cie.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Consumer;

/**
 * Экран /cie edit container и /cie edit bundle — настоящий контейнер
 * (как Storage/MouseHistory), только БЕЗ страниц/кнопок: просто сетка
 * + инвентарь игрока снизу.
 *
 * Слоты с индексом >= handler().getCapacity() (когда рядов больше, чем
 * нужно для capacity — например, воронка: capacity=5, но сетка 1x9)
 * визуально затемняются и полностью блокируют клики (упрощённая
 * реализация без иконок-подсказок а-ля smithing_table, но функционально
 * равнозначная — положить туда предмет физически нельзя).
 *
 * При закрытии экрана вызывается onSave(contents) — вызывающий код сам
 * решает, куда это записать (в предмет в руке через ContainerEditUtil/
 * BundleEditUtil).
 */
public class ContainerEditScreen extends GenericContainerScreen {

    private final Consumer<List<ItemStack>> onSave;
    private boolean saved;

    public ContainerEditScreen(ContainerEditScreenHandler handler, PlayerInventory inventory, Text title, Consumer<List<ItemStack>> onSave) {
        super(handler, inventory, title);
        this.onSave = onSave;
    }

    private ContainerEditScreenHandler handler() {
        return (ContainerEditScreenHandler) this.handler;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int size = handler().getRows() * 9;
        for (int i = handler().getCapacity(); i < size; i++) {
            Slot slot = this.handler.slots.get(i);
            context.fill(this.x + slot.x, this.y + slot.y, this.x + slot.x + 16, this.y + slot.y + 16, 0xB0000000);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        persist();
        super.removed();
    }

    private void persist() {
        if (saved) {
            return;
        }
        saved = true;
        onSave.accept(handler().snapshotContents());
    }

    @Override
    protected void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType) {
        if (slot != null && slot.id < handler().getRows() * 9 && handler().isDisabledSlot(slot.id)) {
            return;
        }
        this.handler.onSlotClick(slot == null ? slotId : slot.id, button, actionType, this.client.player);
    }
}
