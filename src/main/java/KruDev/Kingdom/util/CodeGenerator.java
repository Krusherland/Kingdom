package KruDev.Kingdom.util;

import java.security.SecureRandom;
import java.util.function.Predicate;

public final class CodeGenerator {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CodeGenerator() {}

    /** Generates a unique room code by retrying if the predicate returns true (code exists). */
    public static String generateUniqueCode(Predicate<String> existsCheck) {
        String code;
        do {
            code = generate();
        } while (existsCheck.test(code));
        return code;
    }

    private static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
