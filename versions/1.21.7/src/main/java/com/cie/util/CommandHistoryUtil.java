package com.cie.util;

import net.minecraft.client.network.ClientPlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Отслеживает команды мода (/cie ...), которые игрок реально отправляет в
 * чат, — источник данных для /cie repeat (последняя команда) и /cie macro
 * (запись последовательности во время активной записи).
 *
 * Хук вызывается из ClientSendMessageEvents.ALLOW_COMMAND (см.
 * CIEClientMod), которое стреляет ДО фактического выполнения команды.
 * Поэтому "последняя команда"/содержимое макроса — это то, что игрок
 * ПОПЫТАЛСЯ выполнить, а не гарантированно успешный результат: если
 * аргументы кривые, команда всё равно попадёт в repeat/макрос, а при
 * повторном выполнении просто снова завершится ошибкой (как и в шелле —
 * repeat повторяет буквально то, что было набрано).
 *
 * Живёт только в памяти клиента (сбрасывается при выходе из игры), как и
 * UndoUtil — постоянного хранилища на диске для этого не нужно.
 */
public final class CommandHistoryUtil {

    private CommandHistoryUtil() {
    }

    private static final Set<String> ROOTS = Set.of("cie", "ie", "ei", "commanditemeditor");
    private static final int MAX_MACRO_LENGTH = 300;

    private static final Map<UUID, String> LAST_COMMAND = new HashMap<>();
    private static final Map<UUID, List<String>> RECORDING = new HashMap<>();

    /** Вызывается из ClientSendMessageEvents.ALLOW_COMMAND с текстом БЕЗ ведущего "/". */
    public static void onCommandSent(ClientPlayerEntity player, String rawCommand) {
        if (player == null || rawCommand == null) {
            return;
        }
        String trimmed = rawCommand.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        String[] parts = trimmed.split("\\s+", 2);
        String root = parts[0].toLowerCase(Locale.ROOT);
        if (!ROOTS.contains(root)) {
            return;
        }
        String remainder = parts.length > 1 ? parts[1] : "";
        String remainderLower = remainder.toLowerCase(Locale.ROOT);

        boolean isRepeatCall = remainderLower.equals("repeat");
        boolean isMacroControl = remainderLower.equals("macro start") || remainderLower.equals("macro stop");

        UUID uuid = player.getUuid();

        // repeat не должен становиться "последней командой" сам для себя —
        // иначе он бы повторял сам себя вместо реальной предыдущей правки.
        if (!isRepeatCall) {
            LAST_COMMAND.put(uuid, trimmed);
        }

        // Управляющие команды самого макроса (start/stop) не пишутся внутрь
        // записываемого макроса, как и repeat.
        List<String> buffer = RECORDING.get(uuid);
        if (buffer != null && !isMacroControl && !isRepeatCall) {
            if (buffer.size() < MAX_MACRO_LENGTH) {
                buffer.add(trimmed);
            }
        }

        // /cie stats: "edit <field> ..." -> учитываем поле как кандидата
        // на "самый частый компонент".
        if (!remainder.isEmpty()) {
            String[] remainderParts = remainder.split("\\s+");
            if (remainderParts.length >= 2 && remainderParts[0].equalsIgnoreCase("edit")) {
                StatsUtil.recordEditedField(remainderParts[1].toLowerCase(Locale.ROOT));
            }
        }
    }

    public static String getLastCommand(UUID uuid) {
        return LAST_COMMAND.get(uuid);
    }

    public static boolean isRecording(UUID uuid) {
        return RECORDING.containsKey(uuid);
    }

    public static void startRecording(UUID uuid) {
        RECORDING.put(uuid, new ArrayList<>());
    }

    /** Останавливает запись и возвращает записанные команды (null, если запись не была начата). */
    public static List<String> stopRecording(UUID uuid) {
        return RECORDING.remove(uuid);
    }
}
