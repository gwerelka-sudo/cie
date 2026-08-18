package com.cie.util;

import com.mojang.serialization.DataResult;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * /cie edit bundle — читает/пишет minecraft:bundle_contents.
 *
 * ФИКС (после багрепорта "нельзя положить нестакаемые предметы, а два
 * одинаковых предмета в 2 слота превращаются в 1"):
 *
 * Настоящий публичный API — BundleContentsComponent.Builder — это НЕ
 * нейтральный контейнер данных, а воспроизведение РЕАЛЬНОЙ игровой
 * логики бандла: Builder.add(ItemStack) вызывает addInternal(), который
 * СКЛАДЫВАЕТ одинаковые стакающиеся предметы в один стек, и
 * getMaxAllowed(ItemStack), который ОТБРАСЫВАЕТ предмет, если тот не
 * лезет по "занятости" бандла (нестакающиеся — тяжёлые, лимит 64
 * условных единицы). Именно этим Builder'ом раньше и была написана
 * setStacks — отсюда и баги, это не баг мода, а то, что я применил не
 * тот инструмент (Builder предназначен для симуляции ПКМ-добавления
 * предмета игроком, а не для прямой записи произвольного состояния).
 *
 * Правильный путь — в обход Builder, напрямую через Codec.
 * minecraft:bundle_contents в NBT/JSON — это просто список ItemStack
 * без обёртки (см. https://minecraft.wiki/w/Data_component_format/bundle_contents:
 * "[NBT List / JSON Array] minecraft:bundle_contents: The items stored
 * inside this bundle."). Т.е. кодек — это по сути ItemStack.CODEC.listOf(),
 * который при ДЕКОДИРОВАНИИ (загрузке уже сохранённого состояния, а не
 * добавлении нового предмета игроком) не имеет ПРИЧИН на валидацию —
 * иначе поломалась бы загрузка старых сохранений. Кодируем список сами
 * через ItemStack.CODEC и скармливаем результат обратно тому же
 * BundleContentsComponent.CODEC — получаем компонент 1:1 с тем, что
 * передали: без склеивания одинаковых стеков и без отбрасывания
 * нестакаемых предметов.
 *
 * ВАЖНО (ограничение формата, не мода): minecraft:bundle_contents — это
 * ПЛОТНЫЙ список без слотовых индексов (в отличие от minecraft:container
 * у ContainerUtil, где есть настоящие позиции через DefaultedList).
 * Значит, "дырки" между предметами (пустые слоты) физически негде
 * хранить — при переоткрытии редактора все предметы всегда будут
 * плотно упакованы с начала. Это ТО ЖЕ САМОЕ, что видно и в реальной
 * ванильной игре при открытии бандла (там тоже нет фиксированных
 * слотов — только заполненный список). SLOT_COUNT=54 — это просто
 * потолок редактора, а не число реальных "ячеек" бандла.
 */
public final class BundleUtil {

    private BundleUtil() {
    }

    /** По ТЗ /cie edit bundle open — 54-слотовый редактор (в отличие от 27 у container). Не соответствует реальным "слотам" бандла — см. javadoc класса. */
    public static final int SLOT_COUNT = 54;

    /** Список длиной SLOT_COUNT, хвост — ItemStack.EMPTY (см. ограничение формата в javadoc класса — позиции внутри списка НЕ сохраняются между открытиями). */
    public static List<ItemStack> getStacks(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        List<ItemStack> result = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            result.add(ItemStack.EMPTY);
        }

        BundleContentsComponent component = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
        if (component == null) {
            return result;
        }

        RegistryOps<NbtElement> ops = registries.getOps(NbtOps.INSTANCE);
        NbtElement encoded = BundleContentsComponent.CODEC.encodeStart(ops, component)
                .getOrThrow(err -> new IllegalStateException(String.valueOf(err)));
        if (!(encoded instanceof NbtList list)) {
            return result;
        }
        for (int i = 0; i < list.size() && i < SLOT_COUNT; i++) {
            ItemStack parsed = ItemStack.CODEC.parse(ops, list.get(i)).result().orElse(ItemStack.EMPTY);
            result.set(i, parsed);
        }
        return result;
    }

    /** stacks — список длиной SLOT_COUNT (пустые как ItemStack.EMPTY). Пустые слоты просто пропускаются при записи (см. ограничение формата в javadoc класса), непустые пишутся как есть — БЕЗ склеивания и БЕЗ отбрасывания. */
    public static void setStacks(ItemStack stack, List<ItemStack> stacks, RegistryWrapper.WrapperLookup registries) {
        RegistryOps<NbtElement> ops = registries.getOps(NbtOps.INSTANCE);
        NbtList list = new NbtList();
        for (ItemStack s : stacks) {
            if (s.isEmpty()) continue;
            NbtElement encoded = ItemStack.CODEC.encodeStart(ops, s)
                    .getOrThrow(err -> new IllegalStateException(String.valueOf(err)));
            list.add(encoded);
        }

        if (list.isEmpty()) {
            stack.remove(DataComponentTypes.BUNDLE_CONTENTS);
            return;
        }

        DataResult<BundleContentsComponent> result = BundleContentsComponent.CODEC.parse(ops, list);
        BundleContentsComponent built = result.getOrThrow(err -> new IllegalStateException(String.valueOf(err)));
        stack.set(DataComponentTypes.BUNDLE_CONTENTS, built);
    }

    public static void clear(ItemStack stack) {
        stack.remove(DataComponentTypes.BUNDLE_CONTENTS);
    }

    public static ItemStack getSlot(ItemStack stack, int index, RegistryWrapper.WrapperLookup registries) {
        if (index < 0 || index >= SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        return getStacks(stack, registries).get(index).copy();
    }

    /** Кладёт предмет в первый свободный слот (по позиции в рабочем списке — см. ограничение формата в javadoc класса). false, если все SLOT_COUNT заняты. */
    public static boolean addItem(ItemStack stack, ItemStack item, RegistryWrapper.WrapperLookup registries) {
        List<ItemStack> stacks = getStacks(stack, registries);
        for (int i = 0; i < stacks.size(); i++) {
            if (stacks.get(i).isEmpty()) {
                stacks.set(i, item.copy());
                setStacks(stack, stacks, registries);
                return true;
            }
        }
        return false;
    }
}