package com.restaurantpos.application;

import com.restaurantpos.application.concurrency.BoundedExecutors;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedExecutorsTest {

    @Test
    void executesWorkOnNamedDaemonThread() throws Exception {
        ExecutorService pool = BoundedExecutors.newBoundedPool("pos-test-worker", 1, 2, 4);
        try {
            Future<String> threadName = pool.submit(() -> Thread.currentThread().getName());
            assertTrue(threadName.get().startsWith("pos-test-worker-"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void rejectsInvalidSizingParameters() {
        assertThrows(IllegalArgumentException.class, () -> BoundedExecutors.newBoundedPool("bad", 0, 1, 1));
        assertEquals(IllegalArgumentException.class,
                assertThrows(IllegalArgumentException.class,
                        () -> BoundedExecutors.newBoundedPool("bad", 2, 1, 1)).getClass());
    }
}
