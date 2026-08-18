package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Утилита для /cie chaos — просто фан-команда, ставит случайные значения
 * на набор "безопасных" компонентов для Minecraft 1.21.3.
 */
public final class ChaosUtil {

    private ChaosUtil() {
    }

    private static final Random RANDOM = new Random();

    private static final List<String> RARITIES = List.of("common", "uncommon", "rare", "epic");

    private static final List<String> GIBBERISH_WORDS = List.of(
            // Русские
            "флуб", "варнек", "зюйк", "плонто", "крысь", "шмаль",
            "буздык", "вельпо", "тряксель", "фьюндра", "щурбан",
            "лямза", "трым", "жмур", "цвинь", "квапс", "дрынь",
            "шлёп", "мырк", "блямс", "чвяк", "хрум", "дзынь",
            "пырь", "фырк", "шмяк", "брунь", "жабр", "клюм",
            "зырк", "мандавошка", "пузырь", "хряп", "мозгоплюх",
            "сракопульт", "грымбл", "чвакель", "плюмб", "шурум",
            "крокозябр", "драндул", "бздынь", "мурк", "жужик",
            "крякозяб", "шнырь", "пупырь", "хлопец", "барабулька",

            // Английские
            "flubber", "zonkq", "wibbleton", "snorf", "quazzle",
            "brimtok", "vexnar", "plonko", "skribbit", "twizzle",
            "mungo", "bworp", "xindle", "florp", "krunkle",
            "nabzor", "twoosh", "blorpy", "zibble", "gronk",
            "sploink", "wompus", "dringle", "plumbus", "snibble",
            "glorp", "frizzle", "bonkle", "yapzor", "quonk",
            "blimble", "zorgle", "wobble", "krangle", "floof",
            "snoogle", "bloop", "zorp", "mlem", "yippee",

            // Псевдо-научный мусор
            "оксид", "нейтрон", "протон", "квазимода", "фотон",
            "мегазоид", "турбокислота", "гиперблок", "нанокуб",
            "квант", "спектрон", "изоморф", "плазмоид", "хромат",
            "гравитон", "биофлюкс", "термояд", "молекулоид",
            "нейроплазма", "кристаллоид", "сингуляр", "гиперон",

            // Составные приколы
            "клоп-энерджи", "оксид-мысли", "бабулькор",
            "квазимода-лайт", "гром-мозг", "турбо-жмур",
            "супер-флуб", "мега-буздык", "ультра-мырк",
            "гипер-шмаль", "нано-пузырь", "космо-кряк",
            "псевдо-чвяк", "экстра-дрынь", "делюкс-фырк",
            "премиум-бздынь", "огромный-млем", "абсолютный-зырк"
    );

    private static final List<Integer> CHAOS_COLORS = List.of(
            0xFF0000, 0xFF2222, 0xFF4444, 0xFF5555, 0xFF7777,
            0xFF0055, 0xFF0088, 0xFF3366,
            0xFF5500, 0xFF7700, 0xFF8800, 0xFFAA00,
            0xFFBB22, 0xFFCC44,
            0xFFFF00, 0xFFFF22, 0xFFFF55, 0xFFFF88,
            0xFFFFAA, 0xFFD700,
            0x00FF00, 0x22FF22, 0x55FF55, 0x88FF88,
            0x00FF55, 0x00FFAA, 0x55FF00, 0xAAFF00,
            0x00FFFF, 0x22FFFF, 0x55FFFF, 0x00FFCC,
            0x00FF88, 0x55FFAA,
            0x0000FF, 0x2222FF, 0x5555FF, 0x0088FF,
            0x00AAFF, 0x4488FF, 0x5555AA,
            0x8800FF, 0xAA00FF, 0xCC00FF, 0xFF00FF,
            0xFF55FF, 0xBB55FF, 0x9900CC,
            0xFF0088, 0xFF2299, 0xFF55AA, 0xFF88CC,
            0xFF66FF,
            0xFFFFFF, 0xEEEEEE, 0xCCCCCC, 0xAAAAAA,
            0x888888, 0x666666,
            0x550000, 0x005500, 0x000055, 0x550055,
            0x555500, 0x005555
    );

    private static final double NAME_CHANCE = 0.35;
    private static final double LORE_CHANCE = 0.35;
    private static final int MIN_LORE_LINES = 1;
    private static final int MAX_LORE_LINES = 4;
    private static final int MIN_WORDS_PER_LINE = 2;
    private static final int MAX_WORDS_PER_LINE = 5;

    public static void apply(ItemStack stack, boolean overwrite) {
        if (overwrite) {
            clearChaosComponents(stack);
        }

        // rarity
        RarityUtil.set(stack, RARITIES.get(RANDOM.nextInt(RARITIES.size())));

        // dyed color / map color
        ColorComponentUtil.setDyedColor(stack, randomRgb());
        ColorComponentUtil.setMapColor(stack, randomRgb());

        // repair cost
        RepairCostUtil.set(stack, RANDOM.nextInt(1, 101));

        // custom model data для 1.21.3 (использует CustomModelDataComponent со списком float)
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent((int)(RANDOM.nextFloat() * 10f)));

        // glint override
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, RANDOM.nextBoolean());


        // max stack size
        stack.set(DataComponentTypes.MAX_STACK_SIZE, RANDOM.nextInt(1, 100));

        // custom name
        if (RANDOM.nextDouble() < NAME_CHANCE) {
            stack.set(DataComponentTypes.CUSTOM_NAME, randomGibberishLine());
        }

        // lore
        if (RANDOM.nextDouble() < LORE_CHANCE) {
            int lineCount = RANDOM.nextInt(MIN_LORE_LINES, MAX_LORE_LINES + 1);
            List<Text> lines = new ArrayList<>();
            for (int i = 0; i < lineCount; i++) {
                lines.add(randomGibberishLine());
            }
            stack.set(DataComponentTypes.LORE, new LoreComponent(lines));
        }


    }

    private static MutableText randomGibberishLine() {
        int wordCount = RANDOM.nextInt(MIN_WORDS_PER_LINE, MAX_WORDS_PER_LINE + 1);
        MutableText line = Text.empty();
        for (int i = 0; i < wordCount; i++) {
            if (i > 0) {
                line.append(Text.literal(" "));
            }
            String word = GIBBERISH_WORDS.get(RANDOM.nextInt(GIBBERISH_WORDS.size()));
            int color = CHAOS_COLORS.get(RANDOM.nextInt(CHAOS_COLORS.size()));
            line.append(Text.literal(word).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));
        }
        return line;
    }

    private static int randomRgb() {
        return RANDOM.nextInt(0x1000000);
    }

    private static void clearChaosComponents(ItemStack stack) {
        RarityUtil.clear(stack);
        ColorComponentUtil.removeDyedColor(stack);
        ColorComponentUtil.removeMapColor(stack);
        RepairCostUtil.reset(stack);
        stack.remove(DataComponentTypes.CUSTOM_MODEL_DATA);
        stack.remove(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
        stack.remove(DataComponentTypes.UNBREAKABLE);
        stack.remove(DataComponentTypes.MAX_STACK_SIZE);

        stack.remove(DataComponentTypes.CUSTOM_NAME);
        stack.remove(DataComponentTypes.LORE);
    }
}