package com.cie.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Движок нового /cie storage — 100 ФИКСИРОВАННЫХ пронумерованных страниц
 * (1..100), у каждой по 54 слота + переименовываемое имя (по умолчанию
 * = номер страницы) + флаг "заблокирована" (locked — берёшь предмет,
 * а он остаётся на месте, на курсор уходит копия — своего рода
 * бесконечный раздатчик).
 *
 * Файл на страницу: cie/storage/pages/page_&lt;N&gt;.json — создаётся
 * лениво при первом сохранении, до этого страница считается пустой
 * (имя = номер, не заблокирована, все 54 слота пусты).
 * Текущая открытая страница — cie/storage/current_page.txt (просто
 * число текстом).
 */
public final class StoragePageUtil {

    private StoragePageUtil() {
    }

    public static final int PAGE_COUNT = 100;
    public static final int SLOTS_PER_PAGE = 54;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class Page {
        public String name;
        public boolean locked;
        public final ItemStack[] items = new ItemStack[SLOTS_PER_PAGE];

        Page(int index) {
            this.name = String.valueOf(index);
            for (int i = 0; i < SLOTS_PER_PAGE; i++) {
                items[i] = ItemStack.EMPTY;
            }
        }

        public boolean isEmpty() {
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) return false;
            }
            return true;
        }
    }

    // ============================================================
    //  Пути
    // ============================================================

    private static Path pagesDir() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("cie").resolve("storage").resolve("pages");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        return dir;
    }

    private static Path pageFile(int index) {
        return pagesDir().resolve("page_" + index + ".json");
    }

    private static Path currentPageFile() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("cie").resolve("storage");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        return dir.resolve("current_page.txt");
    }

    // ============================================================
    //  Текущая страница
    // ============================================================

    public static int getCurrentPageIndex() {
        Path file = currentPageFile();
        if (Files.exists(file)) {
            try {
                int value = Integer.parseInt(Files.readString(file, StandardCharsets.UTF_8).trim());
                if (value >= 1 && value <= PAGE_COUNT) {
                    return value;
                }
            } catch (Exception ignored) {
            }
        }
        return 1;
    }

    public static void setCurrentPageIndex(int index) {
        try {
            Files.writeString(currentPageFile(), String.valueOf(index), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    // ============================================================
    //  Загрузка / сохранение страницы
    // ============================================================

    public static Page loadPage(int index, RegistryWrapper.WrapperLookup registries) {
        Page page = new Page(index);
        Path file = pageFile(index);
        if (!Files.exists(file)) {
            return page;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) {
                return page;
            }
            if (root.has("name")) {
                page.name = root.get("name").getAsString();
            }
            if (root.has("locked")) {
                page.locked = root.get("locked").getAsBoolean();
            }
            if (root.has("items")) {
                JsonArray items = root.getAsJsonArray("items");
                var ops = registries.getOps(JsonOps.INSTANCE);
                for (int i = 0; i < items.size() && i < SLOTS_PER_PAGE; i++) {
                    JsonElement el = items.get(i);
                    if (el == null || el.isJsonNull()) {
                        page.items[i] = ItemStack.EMPTY;
                        continue;
                    }
                    page.items[i] = ItemStack.CODEC.parse(ops, el).result().orElse(ItemStack.EMPTY);
                }
            }
        } catch (Exception e) {
            System.err.println("[CIE] Не удалось прочитать страницу storage " + index + " — считаем пустой:");
            e.printStackTrace();
        }
        return page;
    }

    public static void savePage(int index, Page page, RegistryWrapper.WrapperLookup registries) {
        JsonObject root = new JsonObject();
        root.addProperty("name", page.name);
        root.addProperty("locked", page.locked);

        var ops = registries.getOps(JsonOps.INSTANCE);
        JsonArray items = new JsonArray();
        for (ItemStack stack : page.items) {
            if (stack == null || stack.isEmpty()) {
                items.add(JsonNull.INSTANCE);
                continue;
            }
            JsonElement encoded = ItemStack.CODEC.encodeStart(ops, stack).result().orElse(JsonNull.INSTANCE);
            items.add(encoded);
        }
        root.add("items", items);

        try {
            Files.writeString(pageFile(index), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[CIE] Не удалось сохранить страницу storage " + index + ":");
            e.printStackTrace();
        }
    }

    // ============================================================
    //  Поиск / список / очистка
    // ============================================================

    /** Ищет страницу по ИМЕНИ среди всех 1..PAGE_COUNT. Если имя — число в диапазоне [1..PAGE_COUNT], сразу трактуем как индекс (без сканирования). */
    public static int findPageIndexByName(String name, RegistryWrapper.WrapperLookup registries) {
        try {
            int asIndex = Integer.parseInt(name.trim());
            if (asIndex >= 1 && asIndex <= PAGE_COUNT) {
                return asIndex;
            }
        } catch (NumberFormatException ignored) {
        }
        for (int i = 1; i <= PAGE_COUNT; i++) {
            Page page = loadPage(i, registries);
            if (page.name.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /** "1:1, 2:MyStuff, 3:3, ..." — только те, у кого есть предметы ИЛИ нестандартное имя (иначе список из 100 строк бесполезен). */
    public static List<String> listNonEmptyPages(RegistryWrapper.WrapperLookup registries) {
        List<String> result = new ArrayList<>();
        for (int i = 1; i <= PAGE_COUNT; i++) {
            Page page = loadPage(i, registries);
            boolean renamed = !page.name.equals(String.valueOf(i));
            if (renamed || !page.isEmpty()) {
                result.add(i + ":" + page.name);
            }
        }
        return result;
    }

    public static void clearPageItems(int index, RegistryWrapper.WrapperLookup registries) {
        Page page = loadPage(index, registries);
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            page.items[i] = ItemStack.EMPTY;
        }
        savePage(index, page, registries);
    }

    public static void clearAllPages(RegistryWrapper.WrapperLookup registries) {
        for (int i = 1; i <= PAGE_COUNT; i++) {
            clearPageItems(i, registries);
        }
    }

    public static void renamePage(int index, String newName, RegistryWrapper.WrapperLookup registries) {
        Page page = loadPage(index, registries);
        page.name = newName;
        savePage(index, page, registries);
    }

    public static void setLocked(int index, boolean locked, RegistryWrapper.WrapperLookup registries) {
        Page page = loadPage(index, registries);
        page.locked = locked;
        savePage(index, page, registries);
    }

    /** Кладёт предмет в первый свободный слот страницы. Возвращает false, если страница полна. */
    public static boolean saveItemToFirstFreeSlot(int index, ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        Page page = loadPage(index, registries);
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            if (page.items[i].isEmpty()) {
                page.items[i] = stack.copy();
                savePage(index, page, registries);
                return true;
            }
        }
        return false;
    }
}
