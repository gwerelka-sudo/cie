package com.cie.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.screen.PlayerScreenHandler;

/**
 * Утилита для /cie clearinv — полностью очищает инвентарь игрока
 * (основные 36 слотов + броня + офф-хенд), работая только в творческом
 * режиме, тем же способом, что и остальной мод синхронизирует
 * одиночные предметы: локальное обновление инвентаря + пакет на сервер
 * на каждый слот.
 */
public final class ClearInventoryUtil {

    private ClearInventoryUtil() {
    }

    public static int clear(ClientPlayerEntity player) {
        return clearRange(player, 0, player.getInventory().size());
    }

    /** Только хотбар (слоты 0-8 PlayerInventory). */
    public static int clearHotbar(ClientPlayerEntity player) {
        return clearRange(player, 0, 9);
    }

    /** Основной инвентарь без хотбара (слоты 9-35 PlayerInventory). */
    public static int clearMain(ClientPlayerEntity player) {
        return clearRange(player, 9, 36);
    }

    /** Броня (слоты 36-39 PlayerInventory: ноги, торс, шлем, голова). */
    public static int clearArmor(ClientPlayerEntity player) {
        return clearRange(player, 36, 40);
    }

    /** Офф-хенд (слот 40 PlayerInventory). */
    public static int clearOffhand(ClientPlayerEntity player) {
        return clearRange(player, 40, 41);
    }

    /** Только текущий выбранный слот руки (основной хотбар-слот игрока). */
    public static int clearHand(ClientPlayerEntity player) {
        int slot = player.getInventory().getSelectedSlot();
        return clearRange(player, slot, slot + 1);
    }

    private static int clearRange(ClientPlayerEntity player, int fromInclusive, int toExclusive) {
        int cleared = 0;
        MinecraftClient client = MinecraftClient.getInstance();

        for (int slot = fromInclusive; slot < toExclusive; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            cleared++;
            player.getInventory().setStack(slot, ItemStack.EMPTY);

            if (client.getNetworkHandler() != null) {
                int packetSlot = toPacketSlot(slot);
                client.getNetworkHandler().sendPacket(new CreativeInventoryActionC2SPacket(packetSlot, ItemStack.EMPTY));
            }
        }
        return cleared;
    }

    /**
     * PlayerInventory индексирует: 0-8 хотбар, 9-35 основной инвентарь,
     * 36-39 броню (ноги, торс, шлем, голова — по убыванию слота), 40 офф-хенд.
     * PlayerScreenHandler (и, соответственно, CreativeInventoryActionC2SPacket,
     * который адресуется именно по его индексам) раскладывает то же самое
     * иначе: 0 — результат крафта, 1-4 — сетка крафта, 5-8 — броня (шлем..ноги,
     * по возрастанию), 9-35 — основной инвентарь, 36-44 — хотбар, 45 — офф-хенд.
     * Нужен явный пересчёт под каждый диапазон, иначе для брони/офф-хенда
     * пакет уйдёт не в тот слот.
     */
    private static int toPacketSlot(int inventorySlot) {
        if (inventorySlot < 9) {
            // хотбар: 0-8 -> 36-44
            return 36 + inventorySlot;
        }
        if (inventorySlot < 36) {
            // основной инвентарь: 9-35 -> 9-35 (совпадает)
            return inventorySlot;
        }
        if (inventorySlot < 40) {
            // броня: PlayerInventory 36(ноги)-39(шлем) -> PlayerScreenHandler 8(ноги)-5(шлем)
            return 8 - (inventorySlot - 36);
        }
        // офф-хенд: 40 -> 45
        return 45;
    }
}