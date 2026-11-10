package com.restaurantpos.application.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Factory for bounded thread pools designed for predictable memory footprint on low-end hardware.
 */
public final class BoundedExecutors {

    private static final Logger LOG = LoggerFactory.getLogger(BoundedExecutors.class);

    private BoundedExecutors() {
    }

    public static ExecutorService newBoundedPool(String poolName, int coreThreads, int maxThreads, int queueCapacity) {
        if (coreThreads <= 0 || maxThreads < coreThreads || queueCapacity <= 0) {
            throw new IllegalArgumentException("Invalid pool sizing parameters");
        }
        AtomicInteger threadCounter = new AtomicInteger(1);
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, poolName + "-" + threadCounter.getAndIncrement());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, ex) ->
                    LOG.error("Uncaught exception in worker thread {}", t.getName(), ex));
            return thread;
        };
        return new ThreadPoolExecutor(
                coreThreads,
                maxThreads,
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                factory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
