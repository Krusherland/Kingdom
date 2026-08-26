package KruDev.Kingdom.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
@RequiredArgsConstructor
public class DrawingTimerService {

    private final TaskScheduler taskScheduler;
    private GameService gameService;

    // Setter breaks the constructor circular dependency
    @Autowired
    public void setGameService(GameService gameService) {
        this.gameService = gameService;
    }

    private final Map<String, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();

    public void schedule(String code, int delaySecs) {
        cancel(code);
        ScheduledFuture<?> future = taskScheduler.schedule(
            () -> gameService.advanceDrawerByTimer(code),
            Instant.now().plusSeconds(delaySecs)
        );
        timers.put(code, future);
    }

    public void cancel(String code) {
        ScheduledFuture<?> f = timers.remove(code);
        if (f != null) f.cancel(false);
    }
}
