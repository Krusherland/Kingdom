package KruDev.Kingdom.util;

import java.util.List;
import java.util.Random;

/**
 * Thematically paired words: [innocentWord, outsiderWord].
 * Pairs are close enough in theme to make drawings ambiguous.
 */
public final class WordBank {

    private static final List<String[]> PAIRS = List.of(
        new String[]{"Castillo", "Torre"},
        new String[]{"Dragón", "Serpiente"},
        new String[]{"Caballero", "Guerrero"},
        new String[]{"Hechicero", "Brujo"},
        new String[]{"Corona", "Cetro"},
        new String[]{"Espada", "Hacha"},
        new String[]{"Escudo", "Armadura"},
        new String[]{"Mazmorra", "Prisión"},
        new String[]{"Trono", "Altar"},
        new String[]{"Goblin", "Demonio"},
        new String[]{"Arquero", "Cazador"},
        new String[]{"Tesoro", "Monedas"},
        new String[]{"Pergamino", "Tomo"},
        new String[]{"Forja", "Yunque"},
        new String[]{"Catapulta", "Ballesta"},
        new String[]{"Bufón", "Payaso"},
        new String[]{"Foso", "Río"},
        new String[]{"Plaga", "Maldición"},
        new String[]{"Estandarte", "Bandera"},
        new String[]{"Taberna", "Posada"}
    );

    private static final Random RANDOM = new Random();

    private WordBank() {}

    public static String[] randomPair() {
        return PAIRS.get(RANDOM.nextInt(PAIRS.size()));
    }
}
