package pro.sketchware.chat.agentsdk;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Thread-safe event bus for {@link AgentEvent}s emitted by the {@link Runner}.
 *
 * <p>This is the runtime's observation port: hosts (UI, telemetry, tests)
 * subscribe instead of polling text state. On Android, callers that touch
 * views should pass a main-thread {@link Executor}; the default executor
 * delivers on a single background thread, preserving emission order.</p>
 *
 * <p>Subscriber failures are contained — one broken listener never affects
 * the run loop or other subscribers. A bounded replay buffer keeps late
 * subscribers useful (e.g. recreating the screen after rotation).</p>
 */
public final class EventStream {

    private static final int DEFAULT_REPLAY_CAPACITY = 128;

    private final List<Consumer<AgentEvent>> subscribers = new CopyOnWriteArrayList<>();
    private final Executor executor;
    private final AgentEvent[] replay;
    private int replayHead;
    private int replaySize;

    public EventStream() {
        this(Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "axion-agent-events");
            t.setDaemon(true);
            return t;
        }), DEFAULT_REPLAY_CAPACITY);
    }

    public EventStream(Executor executor, int replayCapacity) {
        this.executor = executor == null ? Runnable::run : executor;
        this.replay = replayCapacity <= 0 ? null : new AgentEvent[replayCapacity];
    }

    /** Subscribes to future events (plus replayed recent ones, if buffered). */
    public AutoCloseable subscribe(Consumer<AgentEvent> subscriber) {
        if (subscriber == null) {
            return () -> { };
        }
        subscribers.add(subscriber);
        replayTo(subscriber);
        return () -> subscribers.remove(subscriber);
    }

    /** Emits an event to all subscribers asynchronously, preserving order. */
    public void emit(AgentEvent event) {
        if (event == null) {
            return;
        }
        bufferForReplay(event);
        if (subscribers.isEmpty()) {
            return;
        }
        executor.execute(() -> {
            for (Consumer<AgentEvent> subscriber : subscribers) {
                try {
                    subscriber.accept(event);
                } catch (Exception ignored) {
                    // A broken subscriber must never break the run loop.
                }
            }
        });
    }

    /**
     * Blocks until every event emitted <em>before this call</em> has been
     * handed to all subscribers, or until the timeout elapses.
     *
     * <p>{@link #emit(AgentEvent)} is asynchronous: it queues delivery on this
     * stream's executor and returns. A host that finishes a synchronous run
     * and immediately reads state built from events would therefore observe a
     * half-delivered run. Because the default executor is single-threaded and
     * preserves emission order, a barrier task queued now runs strictly after
     * all events queued earlier — so when it completes, delivery has caught
     * up.</p>
     *
     * @param timeoutMillis how long to wait; values &lt;= 0 mean "do not wait"
     * @return true when delivery caught up, false on timeout, interruption or
     *         a rejected barrier (a shut-down executor)
     */
    public boolean drain(long timeoutMillis) {
        if (subscribers.isEmpty()) {
            return true;
        }
        CountDownLatch barrier = new CountDownLatch(1);
        try {
            executor.execute(barrier::countDown);
        } catch (RuntimeException rejected) {
            // Executor shut down (closed stream): nothing left to deliver.
            return false;
        }
        if (timeoutMillis <= 0) {
            return barrier.getCount() == 0;
        }
        try {
            return barrier.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void replayTo(Consumer<AgentEvent> subscriber) {
        if (replay == null || replaySize == 0) {
            return;
        }
        for (int i = 0; i < replaySize; i++) {
            int idx = (replayHead + i) % replay.length;
            try {
                subscriber.accept(replay[idx]);
            } catch (Exception ignored) {
                // Same containment rule as live delivery.
            }
        }
    }

    private void bufferForReplay(AgentEvent event) {
        if (replay == null) {
            return;
        }
        synchronized (this) {
            int tail = (replayHead + replaySize) % replay.length;
            if (replaySize < replay.length) {
                replay[tail] = event;
                replaySize++;
            } else {
                replay[replayHead] = event;
                replayHead = (replayHead + 1) % replay.length;
            }
        }
    }
}
