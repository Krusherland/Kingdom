package KruDev.Kingdom.service;

import KruDev.Kingdom.dto.*;
import KruDev.Kingdom.exception.*;
import KruDev.Kingdom.model.*;
import KruDev.Kingdom.model.enums.*;
import KruDev.Kingdom.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class ActionService {

    private final GameService gameService;
    private final GameRepository gameRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final RoundRepository roundRepository;
    private final ActionRepository actionRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // ─── Night action ─────────────────────────────────────────────────────────

    public void submitAction(String code, String sessionToken, ActionRequest request) {
        Game game = gameService.findGame(code);

        if (game.getStatus() != GameStatus.NIGHT) {
            throw new InvalidGameStateException("Actions can only be submitted during the night phase");
        }

        GamePlayer actor = gameService.findGamePlayer(game, sessionToken);

        if (!actor.isAlive()) {
            throw new InvalidGameStateException("Dead players cannot perform actions");
        }
        if (actor.isHasActedThisNight()) {
            throw new InvalidGameStateException("You have already acted this night");
        }

        // Validate the action type matches the player's role
        ActionType expectedType = expectedActionType(actor.getRole());
        if (request.getActionType() != expectedType) {
            throw new InvalidGameStateException(
                "Your role (" + actor.getRole() + ") must perform " + expectedType);
        }

        // Find target
        List<GamePlayer> allPlayers = gamePlayerRepository.findByGameWithPlayerOrderByDrawOrder(game);

        // Validate it is this player's sequential turn
        List<GamePlayer> aliveOrdered = allPlayers.stream().filter(GamePlayer::isAlive).collect(Collectors.toList());
        int currentNightIdx = game.getCurrentNightActorIndex();
        if (currentNightIdx >= aliveOrdered.size() || !aliveOrdered.get(currentNightIdx).getId().equals(actor.getId())) {
            throw new InvalidGameStateException("No es tu turno de actuar");
        }

        GamePlayer target = allPlayers.stream()
            .filter(gp -> gp.getPlayer().getNickname().equalsIgnoreCase(request.getTargetNickname()))
            .findFirst()
            .orElseThrow(() -> new InvalidGameStateException(
                "Player not found: " + request.getTargetNickname()));

        if (!target.isAlive()) {
            throw new InvalidGameStateException("Cannot target a dead player");
        }
        if (target.getId().equals(actor.getId())) {
            // Alchemist may shield themselves; others may not self-target
            if (actor.getRole() != Role.ALCHEMIST) {
                throw new InvalidGameStateException("You cannot target yourself");
            }
        }
        // Outsiders cannot target other Outsiders
        if (actor.getRole() == Role.OUTSIDER && target.getRole() == Role.OUTSIDER) {
            throw new InvalidGameStateException("Outsiders cannot target other Outsiders");
        }

        Round currentRound = roundRepository.findByGameAndRoundNumber(game, game.getCurrentRound())
            .orElseThrow(() -> new InvalidGameStateException("Round not found"));

        Action action = new Action();
        action.setRound(currentRound);
        action.setActor(actor);
        action.setTarget(target);
        action.setType(request.getActionType());
        actionRepository.save(action);

        actor.setHasActedThisNight(true);
        gamePlayerRepository.save(actor);

        gameService.advanceNightActorAfterAction(game);
    }

    // ─── Word-guess phase ─────────────────────────────────────────────────────

    public boolean processWordGuess(String code, String sessionToken, WordGuessRequest request) {
        Game game = gameService.findGame(code);

        if (game.getStatus() != GameStatus.WORD_GUESS) {
            throw new InvalidGameStateException("Word guessing is not available right now");
        }

        GamePlayer me = gameService.findGamePlayer(game, sessionToken);
        if (me.isGuessedCorrectly()) {
            throw new InvalidGameStateException("You have already submitted your guess");
        }

        boolean correct = game.getInnocentWord().equalsIgnoreCase(request.getWord().trim());
        if (correct) {
            me.setScore(me.getScore() + 10);
            me.setGuessedCorrectly(true);
        }
        gamePlayerRepository.save(me);

        // Finish game once all alive players have guessed
        List<GamePlayer> alive = gameService.getAlivePlayersOrdered(game);
        boolean allGuessed = alive.stream().allMatch(GamePlayer::isGuessedCorrectly);
        if (allGuessed) {
            List<GamePlayer> all = gamePlayerRepository.findByGameWithPlayerOrderByDrawOrder(game);
            gameService.finishGame(game, all, "INNOCENTS");
        }

        return correct;
    }

    private ActionType expectedActionType(Role role) {
        return switch (role) {
            case PLEBEIAN    -> ActionType.VOTE;
            case ALCHEMIST   -> ActionType.SHIELD;
            case ROYAL_GUARD -> ActionType.REVEAL;
            case OUTSIDER    -> ActionType.KILL;
        };
    }
}
