package net.md_5.bungee.event;

/**
 * Functional interface that allows to capture an event that should happen when an event occurs.
 *
 * @param <T> type of the event
 */
@FunctionalInterface
public interface EventExecutor<T>
{
    /**
     * Executed when the event occurs and this executor has been registered.
     *
     * @param event event object
     */
    void execute(T event);
}
