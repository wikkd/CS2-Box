package com.reclizer.csgobox.box;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Watches a single directory (typically {@code config/csbox/}) for JSON file
 * changes and invokes a debounced reload callback. Designed for use by the
 * per-version {@code CsgoBox} main class, but free of Minecraft imports so it
 * lives in the common module.
 *
 * <p>Threading model: a single daemon thread polls the {@link WatchService},
 * coalesces events into 300 ms debounce windows, and submits a reload task
 * to a single-thread {@link ScheduledExecutorService}. The reload task is
 * guarded by an in-flight flag and a pending-changes flag so that back-to-back
 * file saves (e.g. atomic temp-rename) trigger exactly one reload per quiet
 * period, and a reload that races with new events triggers an extra pass
 * instead of dropping changes.</p>
 *
 * <p>If the platform does not support {@link WatchService} or the directory
 * cannot be registered, {@link #start} returns {@code null} and logs an
 * error. The command {@code /csbox reload} remains available as a manual
 * fallback.</p>
 */
public final class BoxFileWatcher {

    private static final long DEBOUNCE_MILLIS = 300L;

    private final Path dir;
    private final BoxReloadCallback callback;
    private final WatchService watchService;
    private final ScheduledExecutorService reloadExecutor;
    private final Consumer<String> infoLogger;
    private final BiConsumer<String, Throwable> errorLogger;

    private final AtomicBoolean pendingChanges = new AtomicBoolean(false);
    private final AtomicBoolean reloadInFlight = new AtomicBoolean(false);
    private final AtomicReference<ScheduledFuture<?>> pendingFuture = new AtomicReference<>();

    private volatile Thread pollThread;
    private volatile boolean stopped;

    private BoxFileWatcher(Path dir,
                           BoxReloadCallback callback,
                           WatchService watchService,
                           ScheduledExecutorService reloadExecutor,
                           Consumer<String> infoLogger,
                           BiConsumer<String, Throwable> errorLogger) {
        this.dir = dir;
        this.callback = callback;
        this.watchService = watchService;
        this.reloadExecutor = reloadExecutor;
        this.infoLogger = infoLogger;
        this.errorLogger = errorLogger;
    }

    /**
     * Start watching {@code dir}. Returns the watcher instance on success, or
     * {@code null} if the watch service could not be created or the directory
     * could not be registered.
     *
     * @param dir         directory to watch (must exist)
     * @param callback    invoked on a debounced file change
     * @param infoLogger  receives {@code "[BoxFileWatcher] ..."} info lines
     * @param errorLogger receives {@code "[BoxFileWatcher] ..."} error lines with a Throwable
     */
    public static BoxFileWatcher start(Path dir,
                                       BoxReloadCallback callback,
                                       Consumer<String> infoLogger,
                                       BiConsumer<String, Throwable> errorLogger) {
        WatchService ws;
        try {
            ws = dir.getFileSystem().newWatchService();
        } catch (IOException e) {
            errorLogger.accept("Failed to create WatchService for " + dir, e);
            return null;
        }
        try {
            dir.register(ws,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
        } catch (IOException e) {
            errorLogger.accept("Failed to register WatchService on " + dir, e);
            try {
                ws.close();
            } catch (IOException ignored) {
                // shutdown in progress — safe to ignore
            }
            return null;
        }

        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "csgobox-reload-" + seq.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });

        BoxFileWatcher watcher = new BoxFileWatcher(dir, callback, ws, exec, infoLogger, errorLogger);
        Thread poll = new Thread(watcher::pollLoop, "csgobox-watcher-poll");
        poll.setDaemon(true);
        watcher.pollThread = poll;
        poll.start();
        infoLogger.accept("Started watching " + dir + " (debounce=" + DEBOUNCE_MILLIS + "ms)");
        return watcher;
    }

    /**
     * Stop watching, cancel any pending debounced reload, and wait up to
     * 5 seconds for in-flight reload to finish. Safe to call multiple times.
     */
    public void stop() {
        if (stopped) return;
        stopped = true;

        try {
            watchService.close();
        } catch (IOException ignored) {
            // stop() called during shutdown — safe to ignore
        }

        Thread t = pollThread;
        if (t != null) {
            try {
                t.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        ScheduledFuture<?> f = pendingFuture.getAndSet(null);
        if (f != null) f.cancel(false);

        reloadExecutor.shutdown();
        try {
            if (!reloadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                reloadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            reloadExecutor.shutdownNow();
        }
        infoLogger.accept("Stopped watching " + dir);
    }

    private void pollLoop() {
        while (!stopped) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ClosedWatchServiceException e) {
                return;
            }

            boolean sawJsonChange = false;
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }
                Object ctx = event.context();
                if (ctx instanceof Path changed && changed.toString().endsWith(".json")) {
                    sawJsonChange = true;
                }
            }
            if (sawJsonChange) {
                scheduleReload();
            }

            if (!key.reset()) {
                errorLogger.accept("WatchKey no longer valid for " + dir + " — auto-reload disabled", null);
                return;
            }
        }
    }

    private void scheduleReload() {
        pendingChanges.set(true);
        ScheduledFuture<?> newFuture = reloadExecutor.schedule(this::runReload, DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
        ScheduledFuture<?> oldFuture = pendingFuture.getAndSet(newFuture);
        if (oldFuture != null) {
            oldFuture.cancel(false);
        }
    }

    private void runReload() {
        pendingFuture.set(null);
        if (stopped) return;

        if (!reloadInFlight.compareAndSet(false, true)) {
            // A reload is already running; it will pick up pendingChanges in its loop.
            return;
        }
        try {
            while (pendingChanges.compareAndSet(true, false)) {
                try {
                    callback.reload();
                } catch (Exception e) {
                    errorLogger.accept("Box reload callback failed", e);
                }
                if (!pendingChanges.get()) {
                    break;
                }
            }
        } finally {
            reloadInFlight.set(false);
        }
    }
}
