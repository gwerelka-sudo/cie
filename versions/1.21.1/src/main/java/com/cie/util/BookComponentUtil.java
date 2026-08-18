package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Обёртка над WrittenBookContentComponent (данные подписанной книги).
 */
public final class BookComponentUtil {

    private BookComponentUtil() {
    }

    private static <T> RawFilteredPair<T> createPair(T value) {
        return new RawFilteredPair<>(value, Optional.empty());
    }

    public static WrittenBookContentComponent getOrCreate(ItemStack stack) {
        WrittenBookContentComponent existing = stack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
        if (existing != null) {
            return existing;
        }
        return new WrittenBookContentComponent(
                createPair(""),
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
                createPair(plainTitle),
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
            wrapped.add(createPair(page));
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


