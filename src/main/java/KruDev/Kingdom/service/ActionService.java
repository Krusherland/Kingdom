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

        List<GamePlayer> allPlayers = gamePlayerRepository.findByGameWithPlayerOrderByDrawOrder(game);
        Round currentRound = roundRepository.findByGameAndRoundNumber(game, game.getCurrentRound())
            .orElseThrow(() -> new InvalidGameStateException("Round not found"));

        boolean isTwoStepRole = actor.getRole() == Role.ALCHEMIST || actor.getRole() == Role.ROYAL_GUARD;

        if (isTwoStepRole && request.getVoteTargetNickname() != null && !request.getVoteTargetNickname().isBlank()) {
            ActionType primaryType = actor.getRole() == Role.ALCHEMIST ? ActionType.SHIELD : ActionType.REVEAL;

            GamePlayer primaryTarget = findValidTarget(allPlayers, actor, request.getTargetNickname(), primaryType);
            Action primaryAction = new Action();
            primaryAction.setRound(currentRound);
            primaryAction.setActor(actor);
            primaryAction.setTarget(primaryTarget);
            primaryAction.setType(primaryType);
            actionRepository.save(primaryAction);

            GamePlayer voteTarget = findValidTarget(allPlayers, actor, request.getVoteTargetNickname(), ActionType.VOTE);
            Action voteAction = new Action();
            voteAction.setRound(currentRound);
            voteAction.setActor(actor);
            voteAction.setTarget(voteTarget);
            voteAction.setType(ActionType.VOTE);
            actionRepository.save(voteAction);

            actor.setHasUsedSpecialAbility(true);
            actor.setHasActedThisNight(true);
            gamePlayerRepository.save(actor);
        } else {
            ActionType expectedType;
            if (isTwoStepRole && !actor.isHasUsedSpecialAbility()) {
                expectedType = actor.getRole() == Role.ALCHEMIST ? ActionType.SHIELD : ActionType.REVEAL;
            } else if (isTwoStepRole) {
                expectedType = ActionType.VOTE;
            } else {
                expectedType = expectedActionType(actor.getRole());
            }

            if (request.getActionType() != expectedType) {
                throw new InvalidGameStateException(
                    "Your role (" + actor.getRole() + ") must perform " + expectedType + " at this step");
            }

            GamePlayer target = findValidTarget(allPlayers, actor, request.getTargetNickname(), request.getActionType());

            Action action = new Action();
            action.setRound(currentRound);
            action.setActor(actor);
            action.setTarget(target);
            action.setType(request.getActionType());
            actionRepository.save(action);

            if (isTwoStepRole && !actor.isHasUsedSpecialAbility()) {
                actor.setHasUsedSpecialAbility(true);
                gamePlayerRepository.save(actor);
            } else {
                actor.setHasActedThisNight(true);
                gamePlayerRepository.save(actor);
            }
        }

        gameService.checkAndResolveNightIfAllActed(game);
    }

    private GamePlayer findValidTarget(List<GamePlayer> allPlayers, GamePlayer actor, String nickname, ActionType actionType) {
        GamePlayer target = allPlayers.stream()
            .filter(gp -> gp.getPlayer().getNickname().equalsIgnoreCase(nickname))
            .findFirst()
            .orElseThrow(() -> new InvalidGameStateException("Player not found: " + nickname));

        if (!target.isAlive()) {
            throw new InvalidGameStateException("Cannot target a dead player");
        }
        if (target.getId().equals(actor.getId())) {
            boolean isAlchemistShielding = actor.getRole() == Role.ALCHEMIST && actionType == ActionType.SHIELD;
            if (!isAlchemistShielding) {
                throw new InvalidGameStateException("You cannot target yourself");
            }
        }
        if (actor.getRole() == Role.OUTSIDER && target.getRole() == Role.OUTSIDER) {
            throw new InvalidGameStateException("Outsiders cannot target other Outsiders");
        }
        return target;
    }

    // ─── Word-guess phase ─────────────────────────────────────────────────────

    public boolean processWordGuess(String code, String sessionToken, WordGuessRequest request) {
        Game game = gameService.findGame(code);

        if (game.getStatus() != GameStatus.WORD_GUESS) {
            throw new InvalidGameStateException("Word guessing is not available right now");
        }

        GamePlayer me = gameService.findGamePlayer(game, sessionToken);

        if (me.getRole() != Role.OUTSIDER) {
            throw new InvalidGameStateException("Only the Outsider can submit a word guess");
        }
        if (me.isHasActedFinalPhase()) {
            throw new InvalidGameStateException("You have already submitted your guess");
        }

        boolean correct = game.getInnocentWord().equalsIgnoreCase(request.getWord().trim());
        me.setGuessedCorrectly(correct);
        me.setHasActedFinalPhase(true);
        gamePlayerRepository.save(me);

        gameService.checkAndResolveFinalPhase(game);

        return correct;
    }

    // ─── Final-vote phase ─────────────────────────────────────────────────────

    public void processFinalVote(String code, String sessionToken, FinalVoteRequest request) {
        Game game = gameService.findGame(code);

        if (game.getStatus() != GameStatus.WORD_GUESS) {
            throw new InvalidGameStateException("Voting is not available right now");
        }

        GamePlayer me = gameService.findGamePlayer(game, sessionToken);

        if (!me.isAlive()) {
            throw new InvalidGameStateException("Dead players cannot vote");
        }
        if (me.getRole() == Role.OUTSIDER) {
            throw new InvalidGameStateException("The Outsider cannot vote in the final phase");
        }
        if (me.getFinalVoteTarget() != null) {
            throw new InvalidGameStateException("You have already voted");
        }

        List<GamePlayer> allPlayers = gamePlayerRepository.findByGameWithPlayerOrderByDrawOrder(game);
        boolean targetExists = allPlayers.stream().anyMatch(gp ->
            gp.getPlayer().getNickname().equalsIgnoreCase(request.getTargetNickname())
            && gp.isAlive()
            && !gp.getPlayer().getNickname().equalsIgnoreCase(me.getPlayer().getNickname())
        );
        if (!targetExists) {
            throw new InvalidGameStateException("Invalid target: " + request.getTargetNickname());
        }

        me.setFinalVoteTarget(request.getTargetNickname());
        gamePlayerRepository.save(me);

        gameService.checkAndResolveFinalPhase(game);
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
