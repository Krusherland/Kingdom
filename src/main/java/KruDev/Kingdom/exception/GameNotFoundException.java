package KruDev.Kingdom.exception;

public class GameNotFoundException extends RuntimeException {
    public GameNotFoundException(String code) {
        super("Game not found: " + code);
    }
}
