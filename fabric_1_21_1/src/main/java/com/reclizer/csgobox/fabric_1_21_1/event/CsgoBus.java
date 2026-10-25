package com.reclizer.csgobox.fabric_1_21_1.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal static event bus replacing {@code NeoForge.EVENT_BUS} for the mod's
 * own events (BoxOpenedEvent / BoxOpeningEvent / BoxEntityDropEvent /
 * TerminalBuyEvent / TerminalBuyAttemptEvent / ArmoryRecycleEvent) on Fabric.
 *
 * <p>Firing walks the event's class hierarchy, so a listener registered on a
 * base type still receives subclass firings. Listeners run on the posting
 * thread, exactly like NeoForge's synchronous bus.</p>
 */
public final class CsgoBus {
    public interface Listener<E> {
        void accept(E event);
    }

    private static final Map<Class<?>, List<Listener<?>>> LISTENERS = new ConcurrentHashMap<>();

    private CsgoBus() {
    }

    public static <E> void register(Class<E> type, Listener<E> listener) {
        LISTENERS.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void post(Object event) {
        for (Class<?> c = event.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            List<Listener<?>> listeners = LISTENERS.get(c);
            if (listeners != null) {
                for (Listener listener : listeners) {
                    listener.accept(event);
                }
            }
        }
    }
}
