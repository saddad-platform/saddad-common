package com.sadad.common.errors;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * The wording of every error code, held in memory, refreshed in the background.
 *
 * <p><b>Why in memory at all.</b> Error messages are read on a failure path, and a failure
 * path is the worst possible place to add a database round trip or a network call: it runs
 * when something is already wrong, often when a dependency is already slow, and it runs for
 * every failing request at once. Reads here are a single {@code HashMap} lookup against an
 * immutable snapshot - no lock, no allocation, no I/O.
 *
 * <p><b>How it stays current.</b> The snapshot is replaced wholesale by an atomic reference
 * write; readers see either the old map or the new one, never a half-updated one. A read
 * that notices the snapshot has aged past {@link #ttl} triggers a refresh <em>after</em>
 * answering, so no request ever waits for one. Only one refresh runs at a time.
 *
 * <p><b>Why it can never fail a request.</b> Every code ships with English and Arabic text
 * compiled into {@link ErrorCode}, so a resolver works fully before any refresh has ever
 * succeeded - on a cold start, with saddad-admin down, or on a service that cannot reach it
 * at all. The database only ever <em>overrides</em> that wording. This is the same division
 * the platform already uses for permissions: the set of codes lives in code where it can be
 * checked at compile time, and the part humans edit lives in data.
 */
@Slf4j
public class ErrorCatalogCache {

    /** One code's editable wording. Immutable, so sharing it across threads is free. */
    public record Translation(String code, String messageEn, String messageAr, Integer httpStatus) {

        public String forLocale(String locale) {
            boolean arabic = locale != null && locale.startsWith("ar");
            String chosen = arabic ? messageAr : messageEn;
            // A half-filled catalogue row must not produce a blank message. Falling back to
            // the other language is worse than ideal and far better than an empty bubble.
            if (chosen == null || chosen.isBlank()) {
                chosen = arabic ? messageEn : messageAr;
            }
            return chosen;
        }
    }

    private record Snapshot(Map<String, Translation> byCode, Instant loadedAt) {}

    private final Duration ttl;
    private final Supplier<Map<String, Translation>> loader;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    /** Volatile rather than synchronized: readers must never contend with a refresh. */
    private volatile Snapshot snapshot = new Snapshot(Map.of(), Instant.EPOCH);

    public ErrorCatalogCache(Duration ttl, Supplier<Map<String, Translation>> loader) {
        this.ttl = ttl;
        this.loader = loader;
    }

    /**
     * The override for a code, or null when the catalogue has nothing for it - in which
     * case the caller uses the text compiled into {@link ErrorCode}.
     */
    public Translation find(String code) {
        Snapshot current = snapshot;
        if (Instant.now().isAfter(current.loadedAt().plus(ttl))) {
            // After the read, never before it. Refreshing first would put a network call on
            // the path of an error response.
            refreshInBackground();
        }
        return current.byCode().get(code);
    }

    /** Loads now, on the calling thread. Used at startup and by tests. */
    public void refreshNow() {
        if (!refreshing.compareAndSet(false, true)) return;
        try {
            Map<String, Translation> loaded = loader.get();
            if (loaded == null) return;
            snapshot = new Snapshot(new HashMap<>(loaded), Instant.now());
            log.info("Error catalogue refreshed: {} entries", loaded.size());
        } catch (Exception e) {
            // Deliberately swallowed. Every code has compiled-in wording, so a failure here
            // costs translations an operator edited, not the ability to report errors. It
            // must never propagate into whatever request happened to trigger it.
            log.warn("Could not refresh the error catalogue - continuing with {} cached entr{} and built-in text: {}",
                    snapshot.byCode().size(), snapshot.byCode().size() == 1 ? "y" : "ies", e.getMessage());
            // Bumping the timestamp stops a permanently unreachable admin service from
            // provoking a refresh attempt on every single error the platform reports.
            snapshot = new Snapshot(snapshot.byCode(), Instant.now());
        } finally {
            refreshing.set(false);
        }
    }

    private void refreshInBackground() {
        if (refreshing.get()) return;
        Thread.ofVirtual().name("error-catalog-refresh").start(this::refreshNow);
    }

    /** For tests and diagnostics. */
    public int size() {
        return snapshot.byCode().size();
    }
}
