package pro.sketchware.chat.agentsdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * {@link EventStream#drain(long)} is the barrier that lets a synchronous host
 * turn finish with its own events already rendered.
 *
 * <p>Without it a host that runs {@code runtime.run(...)} and then reads state
 * built from events observes a half-delivered run: {@code emit} only queues
 * delivery on the stream's executor. {@code ChatRuntimeAssemblyE2ETest} caught
 * exactly that — a tool had executed but its completion had not reached the
 * UI listener yet.</p>
 */
public class EventStreamDrainTest {

    private static AgentEvent event(String scId) {
        return new AgentEvent.RunStarted(scId);
    }

    @Test
    public void drainWaitsForEventsQueuedBeforeIt() throws Exception {
        EventStream stream = new EventStream();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<AgentEvent> seen = new CopyOnWriteArrayList<>();

        stream.subscribe(e -> {
            entered.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            seen.add(e);
        });

        stream.emit(event("sc-1"));
        assertTrue("subscriber must have started", entered.await(5, TimeUnit.SECONDS));
        assertTrue("delivery is asynchronous, so nothing has landed yet", seen.isEmpty());

        release.countDown();
        assertTrue("drain must report that delivery caught up", stream.drain(5_000));
        assertEquals("the event is delivered once drain returns", 1, seen.size());
    }

    @Test
    public void drainDeliversEveryEarlierEventInOrder() {
        EventStream stream = new EventStream();
        List<String> seen = new CopyOnWriteArrayList<>();
        stream.subscribe(e -> seen.add(e.getScId()));

        for (int i = 0; i < 200; i++) {
            stream.emit(event("sc-" + i));
        }

        assertTrue(stream.drain(5_000));
        assertEquals("every queued event is delivered", 200, seen.size());
        assertEquals("emission order is preserved", "sc-0", seen.get(0));
        assertEquals("emission order is preserved", "sc-199", seen.get(199));
    }

    @Test
    public void drainIsANoOpForASynchronousExecutor() {
        EventStream stream = new EventStream(Runnable::run, 128);
        List<AgentEvent> seen = new CopyOnWriteArrayList<>();
        stream.subscribe(seen::add);

        stream.emit(event("sc-sync"));

        assertEquals("a synchronous executor has already delivered", 1, seen.size());
        assertTrue(stream.drain(1_000));
    }

    @Test
    public void drainReturnsImmediatelyWithoutSubscribers() {
        EventStream stream = new EventStream();
        long startedAt = System.nanoTime();

        assertTrue(stream.drain(5_000));

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
        assertTrue("nothing to wait for: " + elapsedMs + " ms", elapsedMs < 1_000L);
    }

    @Test
    public void aBrokenSubscriberNeverStallsTheBarrier() {
        EventStream stream = new EventStream();
        List<AgentEvent> seen = new CopyOnWriteArrayList<>();
        stream.subscribe(e -> {
            throw new IllegalStateException("broken listener");
        });
        stream.subscribe(seen::add);

        stream.emit(event("sc-broken"));

        assertTrue("a throwing subscriber must not block delivery", stream.drain(5_000));
        assertEquals("the healthy subscriber still received it", 1, seen.size());
    }

    @Test
    public void drainDoesNotWaitWhenTheTimeoutIsNotPositive() {
        EventStream stream = new EventStream();
        CountDownLatch release = new CountDownLatch(1);
        stream.subscribe(e -> {
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        stream.emit(event("sc-nowait"));

        assertFalse("a non-positive timeout must not block", stream.drain(0));
        release.countDown();
    }
}
