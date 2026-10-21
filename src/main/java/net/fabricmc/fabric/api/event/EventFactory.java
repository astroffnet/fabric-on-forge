package net.fabricmc.fabric.api.event;

import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Array;
import java.util.function.Function;

public final class EventFactory {
    private EventFactory() {}

    public static <T> Event<T> createArrayBacked(Class<? super T> type, Function<T[], T> invokerFactory) {
        return new ArrayBackedEvent<>(type, null, invokerFactory);
    }

    public static <T> Event<T> createArrayBacked(Class<T> type, T emptyInvoker, Function<T[], T> invokerFactory) {
        return new ArrayBackedEvent<>(type, emptyInvoker, invokerFactory);
    }

    /** Phases are accepted but not ordered. */
    public static <T> Event<T> createWithPhases(Class<? super T> type, Function<T[], T> invokerFactory,
                                                ResourceLocation... defaultPhases) {
        return new ArrayBackedEvent<>(type, null, invokerFactory);
    }

    private static final class ArrayBackedEvent<T> extends Event<T> {
        private final Class<? super T> type;
        private final T emptyInvoker;
        private final Function<T[], T> invokerFactory;
        private T[] handlers;

        @SuppressWarnings("unchecked")
        ArrayBackedEvent(Class<? super T> type, T emptyInvoker, Function<T[], T> invokerFactory) {
            this.type = type;
            this.emptyInvoker = emptyInvoker;
            this.invokerFactory = invokerFactory;
            this.handlers = (T[]) Array.newInstance(type, 0);
            update();
        }

        @SuppressWarnings("unchecked")
        @Override
        public synchronized void register(T listener) {
            if (listener == null) {
                throw new NullPointerException("Tried to register a null listener");
            }
            T[] next = (T[]) Array.newInstance(type, handlers.length + 1);
            System.arraycopy(handlers, 0, next, 0, handlers.length);
            next[handlers.length] = listener;
            handlers = next;
            update();
        }

        private void update() {
            if (handlers.length == 0 && emptyInvoker != null) {
                invoker = emptyInvoker;
            } else if (handlers.length == 1 && emptyInvoker != null) {
                invoker = handlers[0];
            } else {
                invoker = invokerFactory.apply(handlers);
            }
        }
    }
}
