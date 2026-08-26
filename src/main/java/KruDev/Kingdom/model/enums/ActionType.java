package KruDev.Kingdom.model.enums;

public enum ActionType {
    /** Plebeian — community vote to eliminate a player */
    VOTE,
    /** Alchemist — protect a player from elimination this night */
    SHIELD,
    /** Royal Guard — learn a player's role privately */
    REVEAL,
    /** Outsider — eliminate a player from the game */
    KILL
}
