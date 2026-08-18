package com.cie.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.visitor.StringNbtWriter;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;

/**
 * Строит текст команды /give @s <item>[component=value,...] <count>,
 * которая один в один воспроизводит текущий предмет.
 *
 * РИСК ДЛЯ КОМПИЛЯЦИИ: StringNbtWriter — тот самый класс, что ванильные
 * команды используют для печати SNBT (обратная операция к
 * StringNbtReader, который уже используется в component set). Если у
 * тебя другая ревизия маппингов и класс/метод называется иначе — открой
 * genSources и поищи класс, реализующий "NbtElement -> текстовый SNBT".
 *
 * ИСПРАВЛЕНО: раньше экспортировались ВСЕ компоненты, которые реально
 * есть на смёрженном ComponentMap стека — включая ванильные дефолты
 * предмета (max_stack_size, rarity, item_model и т.д.), из-за чего
 * команда/JSON раздувались до сотен строк мусора. Теперь берём только
 * реальный дифф относительно чистого дефолтного стека того же item —
 * см. ComponentDiffUtil. Явно снятые дефолтные компоненты (Kind.REMOVED)
 * попадают в /give как "!component_id" — это тот же синтаксис, которым
 * ванильные команды умеют явно убирать компонент из дефолтного набора.
 */
public final class ExportUtil {

    private ExportUtil() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String toGiveCommand(ItemStack stack) {
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        StringBuilder components = new StringBuilder();

        for (ComponentDiffUtil.Entry entry : ComponentDiffUtil.diffFromDefault(stack)) {
            ComponentType<?> type = entry.type();
            Identifier typeId = Registries.DATA_COMPONENT_TYPE.getId((ComponentType) type);
            if (typeId == null) {
                continue;
            }

            if (entry.kind() == ComponentDiffUtil.Kind.REMOVED) {
                if (components.length() > 0) {
                    components.append(',');
                }
                components.append('!').append(typeId);
                continue;
            }

            Codec codec = type.getCodec();
            if (codec == null) {
                // Компонент без кодека — участвовал в диффе, но записать его в SNBT нечем.
                continue;
            }
            try {
                DataResult<NbtElement> result = codec.encodeStart(NbtOps.INSTANCE, entry.value());
                NbtElement nbt = result.getOrThrow();
                StringNbtWriter writer = new StringNbtWriter();
                nbt.accept(writer);
                String snbt = writer.getString();
                if (components.length() > 0) {
                    components.append(',');
                }
                components.append(typeId).append('=').append(snbt);
            } catch (Exception ignored) {
                // Ошибка сериализации конкретного компонента — не ломаем экспорт целиком.
            }
        }

        StringBuilder command = new StringBuilder("/give @s ").append(itemId);
        if (components.length() > 0) {
            command.append('[').append(components).append(']');
        }
        if (stack.getCount() != 1) {
            command.append(' ').append(stack.getCount());
        }
        return command.toString();
    }

    /** JSON-описание предмета (id, count, только РЕАЛЬНО изменённые относительно дефолта компоненты) — для "export JSON". */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String toJson(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        RegistryOps<JsonElement> ops = registries.getOps(JsonOps.INSTANCE);

        JsonObject root = new JsonObject();
        root.addProperty("id", itemId.toString());
        root.addProperty("count", stack.getCount());

        JsonObject componentsJson = new JsonObject();
        JsonObject removedJson = new JsonObject();
        for (ComponentDiffUtil.Entry entry : ComponentDiffUtil.diffFromDefault(stack)) {
            ComponentType<?> type = entry.type();
            Identifier typeId = Registries.DATA_COMPONENT_TYPE.getId((ComponentType) type);
            if (typeId == null) continue;

            if (entry.kind() == ComponentDiffUtil.Kind.REMOVED) {
                // Явное снятие дефолтного компонента отмечаем отдельным флагом "true",
                // чтобы не путать с "компонент = булево false".
                removedJson.addProperty(typeId.toString(), true);
                continue;
            }

            Codec codec = type.getCodec();
            if (codec == null) continue;
            try {
                DataResult<JsonElement> result = codec.encodeStart(ops, entry.value());
                JsonElement json = result.getOrThrow();
                componentsJson.add(typeId.toString(), json);
            } catch (Exception ignored) {
                // Пропускаем проблемный компонент, чтобы не сломать экспорт целиком.
            }
        }
        root.add("components", componentsJson);
        if (removedJson.size() > 0) {
            root.add("removed_components", removedJson);
        }
        return root.toString();
    }
}
