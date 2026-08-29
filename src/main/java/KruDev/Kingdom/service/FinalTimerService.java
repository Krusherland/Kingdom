package KruDev.Kingdom.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
@RequiredArgsConstructor
public class FinalTimerService implements ApplicationContextAware {

    private final TaskScheduler taskScheduler;
    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        this.applicationContext = ctx;
    }

    private final Map<String, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();

    public void schedule(String code, int delaySecs) {
        cancel(code);
        ScheduledFuture<?> future = taskScheduler.schedule(
            () -> applicationContext.getBean(GameService.class).resolveFinalPhaseByTimer(code),
            Instant.now().plusSeconds(delaySecs)
        );
        timers.put(code, future);
    }

    public void cancel(String code) {
        ScheduledFuture<?> f = timers.remove(code);
        if (f != null) f.cancel(false);
    }
}
