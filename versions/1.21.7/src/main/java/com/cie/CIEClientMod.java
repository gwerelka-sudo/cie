package com.cie;

import com.cie.command.CIECommand;
import com.cie.text.CIELang;
import com.cie.text.MiniMessageBridge;
import com.cie.util.CommandHistoryUtil;
import com.cie.util.LivePreviewRenderer;
import com.cie.util.StorageUtil;
import com.cie.util.UiColorDefaults;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.List;

public class CIEClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 0. Регистрируем все дефолты покраски GUI (UiColorUtil) сразу при
        //    старте клиента — чтобы "/cie paint <key>" показывал ключи вроде
        //    armorStandMenu.* сразу в автодополнении, а не только после
        //    того как игрок хотя бы раз откроет соответствующий экран.
        UiColorDefaults.registerAll();

        // 1. Сначала СТРОГО загружаем языковой файл
        CIELang.load();

        // 2. Только после этого регистрируем команды
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            CIECommand.register(dispatcher, registryAccess);
        });

        // 2.1. HUD-виджет /cie livePreview (см. класс) — регистрируется один раз на весь
        //      клиент, сам смотрит на LivePreviewUtil.isEnabled() при каждом кадре.
        LivePreviewRenderer.register();

        // 2.5. Слушаем ВСЕ команды, отправляемые игроком в чат — это
        //      единственная точка, откуда видно и содержимое, и порядок
        //      команд /cie, что нужно и /cie repeat, и /cie macro (запись).
        //      Никогда не блокируем отправку (return true) — это чисто
        //      подслушивание, а не фильтр.
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            CommandHistoryUtil.onCommandSent(MinecraftClient.getInstance().player, command);
            return true;
        });

        // 3. Сканируем хранилище предметов на битые файлы сразу при загрузке мода
        //    (переносим их в storage/corrupt/ заранее, не дожидаясь, пока игрок
        //    попробует их вызвать). Само уведомление в чат откладываем до входа
        //    в мир — раньше чата ещё физически не существует.
        List<String> corruptedOnBoot = StorageUtil.scanAndQuarantineCorrupt();
        boolean[] alreadyNotified = {false};

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (corruptedOnBoot.isEmpty() || alreadyNotified[0]) {
                return;
            }
            if (client.player == null) {
                return;
            }
            alreadyNotified[0] = true;
            Text header = MiniMessageBridge.miniMessageToVanilla(
                    CIELang.getFormatted("storage_boot_corrupt_header", corruptedOnBoot.size()),
                    client.world.getRegistryManager());
            client.player.sendMessage(header, false);
            for (String name : corruptedOnBoot) {
                Text entry = MiniMessageBridge.miniMessageToVanilla(
                        CIELang.getFormatted("storage_boot_corrupt_entry", name),
                        client.world.getRegistryManager());
                client.player.sendMessage(entry, false);
            }
        });
    }
}