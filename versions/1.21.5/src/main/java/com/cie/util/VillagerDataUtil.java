package com.cie.util;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;

import java.util.function.Consumer;

/**
 * /cie edit villagerData — работает с тем же компонентом
 * minecraft:entity_data (см. EntitySettingsUtil.getRoot/mutate/saveRoot),
 * что и /cie edit EntitySettings, только читает/пишет НЕ общие поля
 * сущности, а villager-специфичные: VillagerData (type/profession/level),
 * Willing, LastRestock, Offers.Recipes (список сделок).
 *
 * Ключи и структура — по документированному (minecraft.wiki) NBT-формату
 * жителя, стабильному уже много версий подряд. РИСКОВАННЫЙ момент, как
 * и в equipment у EntitySettingsUtil: собран по документации, не
 * проверен вживую в конкретно вашей сборке. Единственное поле, где риск
 * концептуальный, а не "не тот маппинг": "Willing" (готовность к
 * размножению) в некоторых ревизиях вообще не сериализуется в NBT
 * (регенерируется рантайм-AI дерева поведения) — тогда set просто ни на
 * что не повлияет в игре, это ограничение ванильного формата, а не баг.
 *
 * Предметы сделки (buy/buyB/sell) кодируются через ItemStack.CODEC — тот
 * же проверенный на компиляцию в вашей сборке механизм, что и equipment.
 */
public final class VillagerDataUtil {

    private VillagerDataUtil() {
    }

    private static final String VILLAGER_DATA_KEY = "VillagerData";
    private static final String TYPE_KEY = "type";
    private static final String PROFESSION_KEY = "profession";
    private static final String LEVEL_KEY = "level";
    private static final String WILLING_KEY = "Willing";
    private static final String LAST_RESTOCK_KEY = "LastRestock";
    private static final String OFFERS_KEY = "Offers";
    private static final String RECIPES_KEY = "Recipes";

    public static final String DEFAULT_BIOME = "minecraft:plains";
    public static final String DEFAULT_PROFESSION = "minecraft:none";
    public static final int DEFAULT_LEVEL = 1;

    // ============================================================
    //  VillagerData: profession / type(биом) / level
    // ============================================================

    private static NbtCompound getVillagerDataCompound(ItemStack stack) {
        return EntitySettingsUtil.getRoot(stack).getCompoundOrEmpty(VILLAGER_DATA_KEY);
    }

    private static void mutateVillagerData(ItemStack stack, Consumer<NbtCompound> mutator) {
        EntitySettingsUtil.mutate(stack, root -> {
            NbtCompound vd = root.getCompoundOrEmpty(VILLAGER_DATA_KEY);
            mutator.accept(vd);
            root.put(VILLAGER_DATA_KEY, vd);
        });
    }

    public static String getProfession(ItemStack stack) {
        return getVillagerDataCompound(stack).getString(PROFESSION_KEY, DEFAULT_PROFESSION);
    }

    public static void setProfession(ItemStack stack, String professionId) {
        mutateVillagerData(stack, vd -> vd.putString(PROFESSION_KEY, professionId));
    }

    public static void resetProfession(ItemStack stack) {
        setProfession(stack, DEFAULT_PROFESSION);
    }

    public static String getBiome(ItemStack stack) {
        return getVillagerDataCompound(stack).getString(TYPE_KEY, DEFAULT_BIOME);
    }

    public static void setBiome(ItemStack stack, String biomeTypeId) {
        mutateVillagerData(stack, vd -> vd.putString(TYPE_KEY, biomeTypeId));
    }

    public static void resetBiome(ItemStack stack) {
        setBiome(stack, DEFAULT_BIOME);
    }

    public static int getLevel(ItemStack stack) {
        return getVillagerDataCompound(stack).getInt(LEVEL_KEY, DEFAULT_LEVEL);
    }

    public static void setLevel(ItemStack stack, int level) {
        mutateVillagerData(stack, vd -> vd.putInt(LEVEL_KEY, level));
    }

    public static void resetLevel(ItemStack stack) {
        setLevel(stack, DEFAULT_LEVEL);
    }

    // ============================================================
    //  willing / lastRestock
    // ============================================================

    public static boolean getWilling(ItemStack stack) {
        return EntitySettingsUtil.getRoot(stack).getBoolean(WILLING_KEY, false);
    }

    public static void setWilling(ItemStack stack, boolean value) {
        EntitySettingsUtil.mutate(stack, root -> root.putBoolean(WILLING_KEY, value));
    }

    public static long getLastRestock(ItemStack stack) {
        return EntitySettingsUtil.getRoot(stack).getLong(LAST_RESTOCK_KEY, 0L);
    }

    public static void setLastRestock(ItemStack stack, long value) {
        EntitySettingsUtil.mutate(stack, root -> root.putLong(LAST_RESTOCK_KEY, value));
    }

    public static void resetLastRestock(ItemStack stack) {
        EntitySettingsUtil.mutate(stack, root -> root.putLong(LAST_RESTOCK_KEY, 0L));
    }

    // ============================================================
    //  trades (Offers.Recipes) — список торговых предложений
    // ============================================================

    private static NbtList getRecipes(ItemStack stack) {
        return EntitySettingsUtil.getRoot(stack).getCompoundOrEmpty(OFFERS_KEY).getListOrEmpty(RECIPES_KEY);
    }

    private static void saveRecipes(ItemStack stack, NbtList recipes) {
        EntitySettingsUtil.mutate(stack, root -> {
            NbtCompound offers = root.getCompoundOrEmpty(OFFERS_KEY);
            offers.put(RECIPES_KEY, recipes);
            root.put(OFFERS_KEY, offers);
        });
    }

    public static int tradeCount(ItemStack stack) {
        return getRecipes(stack).size();
    }

    /** 0-based индекс. null, если индекс вне диапазона. */
    public static NbtCompound getTrade(ItemStack stack, int index0) {
        NbtList recipes = getRecipes(stack);
        if (index0 < 0 || index0 >= recipes.size()) return null;
        return recipes.getCompoundOrEmpty(index0);
    }

    /** Шаблонная сделка: покупаем 1 камень за 1 камень (максимально нейтральный дефолт под последующий edit). */
    private static NbtCompound buildTemplateTrade(RegistryWrapper.WrapperLookup registries) {
        NbtCompound trade = new NbtCompound();
        var ops = registries.getOps(NbtOps.INSTANCE);
        var stoneEncoded = ItemStack.CODEC.encodeStart(ops, new ItemStack(Items.STONE, 1))
                .getOrThrow(err -> new IllegalStateException(String.valueOf(err)));
        trade.put("buy", stoneEncoded);
        trade.put("sell", stoneEncoded.copy());
        trade.putInt("uses", 0);
        trade.putInt("maxUses", 12);
        trade.putBoolean("rewardExp", true);
        trade.putInt("specialPrice", 0);
        trade.putInt("demand", 0);
        trade.putFloat("priceMultiplier", 0.05f);
        trade.putInt("xp", 1);
        return trade;
    }

    /** index1 — 1-based позиция вставки, допустимо от 1 до tradeCount()+1 (в конец). */
    public static void createTrade(ItemStack stack, int index1, RegistryWrapper.WrapperLookup registries) {
        NbtList recipes = getRecipes(stack).copy();
        int index0 = Math.max(0, Math.min(index1 - 1, recipes.size()));
        recipes.add(index0, buildTemplateTrade(registries));
        saveRecipes(stack, recipes);
    }

    public static boolean removeTrade(ItemStack stack, int index1) {
        NbtList recipes = getRecipes(stack).copy();
        int index0 = index1 - 1;
        if (index0 < 0 || index0 >= recipes.size()) return false;
        recipes.remove(index0);
        saveRecipes(stack, recipes);
        return true;
    }

    public static void clearTrades(ItemStack stack) {
        saveRecipes(stack, new NbtList());
    }

    private static void mutateTrade(ItemStack stack, int index1, Consumer<NbtCompound> mutator) {
        NbtList recipes = getRecipes(stack).copy();
        int index0 = index1 - 1;
        if (index0 < 0 || index0 >= recipes.size()) return;
        NbtCompound trade = recipes.getCompoundOrEmpty(index0).copy();
        mutator.accept(trade);
        recipes.set(index0, trade);
        saveRecipes(stack, recipes);
    }

    // -- rewardExp / maxUses / uses / xp / priceMultiplier / specialPrice / demand --

    public static boolean getRewardExp(ItemStack stack, int index1) {
        NbtCompound trade = getTrade(stack, index1 - 1);
        return trade != null && trade.getBoolean("rewardExp", true);
    }

    public static void setRewardExp(ItemStack stack, int index1, boolean value) {
        mutateTrade(stack, index1, t -> t.putBoolean("rewardExp", value));
    }

    public static int getMaxUses(ItemStack stack, int index1) {
        NbtCompound trade = getTrade(stack, index1 - 1);
        return trade != null ? trade.getInt("maxUses", 0) : 0;
    }

    public static void setMaxUses(ItemStack stack, int index1, int value) {
        mutateTrade(stack, index1, t -> t.putInt("maxUses", value));
    }

    public static void resetMaxUses(ItemStack stack, int index1) {
        mutateTrade(stack, index1, t -> t.putInt("maxUses", 0));
    }

    public static int getUses(ItemStack stack, int index1) {
        NbtCompound trade = getTrade(stack, index1 - 1);
        return trade != null ? trade.getInt("uses", 0) : 0;
    }

    public static void setUses(ItemStack stack, int index1, int value) {
        mutateTrade(stack, index1, t -> t.putInt("uses", value));
    }

    public static void resetUses(ItemStack stack, int index1) {
        mutateTrade(stack, index1, t -> t.putInt("uses", 0));
    }

    public static int getXp(ItemStack stack, int index1) {
        NbtCompound trade = getTrade(stack, index1 - 1);
        return trade != null ? trade.getInt("xp", 0) : 0;
    }

    public static void setXp(ItemStack stack, int index1, int value) {
        mutateTrade(stack, index1, t -> t.putInt("xp", value));
    }

    public static void resetXp(ItemStack stack, int index1) {
        mutateTrade(stack, index1, t -> t.putInt("xp", 0));
    }

    /** По ТЗ — set <int>, поэтому храним ровно тем int'ом, который дали (переведённым во float NBT-поле). */
    public static int getPriceMultiplier(ItemStack stack, int index1) {
        NbtCompound trade = getTrade(stack, index1 - 1);
        return trade != null ? (int) trade.getFloat("priceMultiplier", 0f) : 0;
    }

    public static void setPriceMultiplier(ItemStack stack, int index1, int value) {
        mutateTrade(stack, index1, t -> t.putFloat("priceMultiplier", (float) value));
    }

    public static void resetPriceMultiplier(ItemStack stack, int index1) {
        mutateTrade(stack, index1, t -> t.putFloat("priceMultiplier", 0f));
    }

    public static int getSpecialPrice(ItemStack stack, int index1) {
        NbtCompound trade = getTrade(stack, index1 - 1);
        return trade != null ? trade.getInt("specialPrice", 0) : 0;
    }

    public static void setSpecialPrice(ItemStack stack, int index1, int value) {
        mutateTrade(stack, index1, t -> t.putInt("specialPrice", value));
    }

    public static void resetSpecialPrice(ItemStack stack, int index1) {
        mutateTrade(stack, index1, t -> t.putInt("specialPrice", 0));
    }

    public static int getDemand(ItemStack stack, int index1) {
        NbtCompound trade = getTrade(stack, index1 - 1);
        return trade != null ? trade.getInt("demand", 0) : 0;
    }

    public static void setDemand(ItemStack stack, int index1, int value) {
        mutateTrade(stack, index1, t -> t.putInt("demand", value));
    }

    public static void resetDemand(ItemStack stack, int index1) {
        mutateTrade(stack, index1, t -> t.putInt("demand", 0));
    }

    // -- items: buy / buyB / sell --

    public static ItemStack getTradeItem(ItemStack stack, int index1, String field, RegistryWrapper.WrapperLookup registries) {
        NbtCompound trade = getTrade(stack, index1 - 1);
        if (trade == null || !trade.contains(field)) return ItemStack.EMPTY;
        var ops = registries.getOps(NbtOps.INSTANCE);
        return ItemStack.CODEC.parse(ops, trade.getCompoundOrEmpty(field)).result().orElse(ItemStack.EMPTY);
    }

    public static void setTradeItem(ItemStack stack, int index1, String field, ItemStack item, RegistryWrapper.WrapperLookup registries) {
        var ops = registries.getOps(NbtOps.INSTANCE);
        var encoded = ItemStack.CODEC.encodeStart(ops, item)
                .getOrThrow(err -> new IllegalStateException(String.valueOf(err)));
        mutateTrade(stack, index1, t -> t.put(field, encoded));
    }

    /** "remove # air" по ТЗ — не удаляем ключ (buy/sell обязательны у ванильного TradeOffer), а ставим AIR. */
    public static void removeTradeItem(ItemStack stack, int index1, String field, RegistryWrapper.WrapperLookup registries) {
        setTradeItem(stack, index1, field, new ItemStack(Items.AIR), registries);
    }

    /** items clear — buy/sell на AIR, buyB снимается полностью (необязательное поле). */
    public static void clearTradeItems(ItemStack stack, int index1, RegistryWrapper.WrapperLookup registries) {
        var ops = registries.getOps(NbtOps.INSTANCE);
        var airEncoded = ItemStack.CODEC.encodeStart(ops, new ItemStack(Items.AIR))
                .getOrThrow(err -> new IllegalStateException(String.valueOf(err)));
        mutateTrade(stack, index1, t -> {
            t.put("buy", airEncoded);
            t.put("sell", airEncoded.copy());
            t.remove("buyB");
        });
    }
}