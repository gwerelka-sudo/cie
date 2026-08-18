package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Обёртка над WrittenBookContentComponent (данные подписанной книги).
 *
 * САМОЕ РИСКОВОЕ МЕСТО МОДА. В разных ревизиях Yarn для 1.20.5+/1.21.x
 * "фильтруемые" поля книги (title/pages) заворачивались то в
 * RawFilteredPair<T>, то в отдельный класс Filterable<T> — имя и методы
 * могли отличаться от билда к билду. Если этот файл не компилируется:
 *   1. ./gradlew genSources
 *   2. открой WrittenBookContentComponent(.java) в
 *      build/loom-cache/... (или через "Decompile" в IDE)
 *   3. посмотри точные имена полей/методов и поправь только этот файл —
 *      остальной мод его не касается.
 *
 * Поля компонента (по актуальным снапшотам 1.21.x):
 *   title      : RawFilteredPair<String>   (сырой + отфильтрованный текст)
 *   author     : String
 *   generation : int   (0=original,1=copy,2=copy of copy,3=tattered)
 *   pages      : List<RawFilteredPair<Text>>
 *   resolved   : boolean
 */
public final class BookComponentUtil {

    private BookComponentUtil() {
    }

    public static WrittenBookContentComponent getOrCreate(ItemStack stack) {
        WrittenBookContentComponent existing = stack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
        if (existing != null) {
            return existing;
        }
        return new WrittenBookContentComponent(
                RawFilteredPair.of(""),
                "",
                0,
                new ArrayList<>(),
                true
        );
    }

    public static void save(ItemStack stack, WrittenBookContentComponent content) {
        stack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, content);
    }

    /** Полностью убирает данные книги (автор/название/страницы). */
    public static void remove(ItemStack stack) {
        stack.remove(DataComponentTypes.WRITTEN_BOOK_CONTENT);
    }

    public static String rawTitle(WrittenBookContentComponent content) {
        return content.title().raw();
    }

    public static WrittenBookContentComponent withTitle(WrittenBookContentComponent content, String plainTitle) {
        return new WrittenBookContentComponent(
                RawFilteredPair.of(plainTitle),
                content.author(),
                content.generation(),
                content.pages(),
                content.resolved()
        );
    }

    public static WrittenBookContentComponent withAuthor(WrittenBookContentComponent content, String author) {
        return new WrittenBookContentComponent(
                content.title(),
                author,
                content.generation(),
                content.pages(),
                content.resolved()
        );
    }

    public static WrittenBookContentComponent withGeneration(WrittenBookContentComponent content, int generation) {
        return new WrittenBookContentComponent(
                content.title(),
                content.author(),
                generation,
                content.pages(),
                content.resolved()
        );
    }

    public static List<Text> pagesAsText(WrittenBookContentComponent content) {
        List<Text> result = new ArrayList<>();
        for (RawFilteredPair<Text> page : content.pages()) {
            result.add(page.raw());
        }
        return result;
    }

    public static WrittenBookContentComponent withPages(WrittenBookContentComponent content, List<Text> pages) {
        List<RawFilteredPair<Text>> wrapped = new ArrayList<>();
        for (Text page : pages) {
            wrapped.add(RawFilteredPair.of(page));
        }
        return new WrittenBookContentComponent(
                content.title(),
                content.author(),
                content.generation(),
                wrapped,
                content.resolved()
        );
    }
}
