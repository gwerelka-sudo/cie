package com.cie.util;

import net.minecraft.component.ComponentType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Буфер обмена мода для ОДНОГО компонента: /cie component copy <type>
 * кладёт сюда (type, value) со стека в руке, /cie component paste
 * переносит это значение на текущий предмет в руке (в т.ч. на другой
 * item — компонент просто ставится как есть, никакой привязки к типу
 * предмета-источника нет).
 *
 * Живёт только в памяти клиента (никакого файла на диске) — обычный
 * copy/paste, обнуляется при выходе из игры. Хранится по UUID игрока —
 * тот же паттерн, что и у CommandHistoryUtil/MacroUtil, на случай если
 * когда-нибудь появится больше одного локального контекста игрока.
 *
 * Значения компонентов в этой ревизии игры — практически всегда records
 * (иммутабельные), поэтому переиспользование объекта из одного стека на
 * другом безопасно: они не мутируются setComponent/getComponent.
 */
public final class ComponentClipboardUtil {

    private ComponentClipboardUtil() {
    }

    public record Entry(ComponentType<?> type, Object value) {
    }

    private static final Map<UUID, Entry> CLIPBOARD = new ConcurrentHashMap<>();

    public static void copy(UUID uuid, ComponentType<?> type, Object value) {
        CLIPBOARD.put(uuid, new Entry(type, value));
    }

    public static Entry get(UUID uuid) {
        return CLIPBOARD.get(uuid);
    }

    public static void clear(UUID uuid) {
        CLIPBOARD.remove(uuid);
    }
}
