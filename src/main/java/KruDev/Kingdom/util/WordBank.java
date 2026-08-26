package KruDev.Kingdom.util;

import java.util.List;
import java.util.Random;

/**
 * Thematically paired words: [innocentWord, outsiderWord].
 * Pairs are close enough in theme to make drawings ambiguous.
 */
public final class WordBank {

    private static final List<String[]> PAIRS = List.of(
        new String[]{"Castle", "Tower"},
        new String[]{"Dragon", "Serpent"},
        new String[]{"Knight", "Warrior"},
        new String[]{"Wizard", "Sorcerer"},
        new String[]{"Crown", "Scepter"},
        new String[]{"Sword", "Axe"},
        new String[]{"Shield", "Armor"},
        new String[]{"Dungeon", "Prison"},
        new String[]{"Throne", "Altar"},
        new String[]{"Goblin", "Imp"},
        new String[]{"Archer", "Hunter"},
        new String[]{"Treasure", "Gold"},
        new String[]{"Scroll", "Tome"},
        new String[]{"Forge", "Anvil"},
        new String[]{"Catapult", "Ballista"},
        new String[]{"Jester", "Fool"},
        new String[]{"Moat", "River"},
        new String[]{"Plague", "Curse"},
        new String[]{"Banner", "Flag"},
        new String[]{"Tavern", "Inn"}
    );

    private static final Random RANDOM = new Random();

    private WordBank() {}

    public static String[] randomPair() {
        return PAIRS.get(RANDOM.nextInt(PAIRS.size()));
    }
}
