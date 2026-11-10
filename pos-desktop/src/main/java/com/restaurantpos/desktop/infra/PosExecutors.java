package com.restaurantpos.desktop.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded background executor pool for JavaFX asynchronous operations, keeping UI thread non-blocking.
 */
public final class PosExecutors {

    private static final Logger LOG = LoggerFactory.getLogger(PosExecutors.class);

    private PosExecutors() {
    }

    public static ExecutorService newIoExecutor() {
        AtomicInteger counter = new AtomicInteger(1);
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "pos-desktop-io-" + counter.getAndIncrement());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, ex) ->
                    LOG.error("Uncaught exception on desktop worker {}", t.getName(), ex));
            return thread;
        };
        return new ThreadPoolExecutor(
                2,
                4,
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(32),
                factory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
