package net.md_5.bungee.event;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public final class EventExecutorMethod implements EventExecutor<Object>
{

    private final Logger logger;
    @Getter
    private final Object listener;
    @Getter
    private final Method method;

    @Override
    public void execute(Object event)
    {
        try
        {
            method.invoke( listener, event );
        } catch ( IllegalAccessException ex )
        {
            throw new Error( "Method became inaccessible: " + event, ex );
        } catch ( IllegalArgumentException ex )
        {
            throw new Error( "Method rejected target/argument: " + event, ex );
        } catch ( InvocationTargetException ex )
        {
            logger.log( Level.WARNING, MessageFormat.format( "Error dispatching event {0} to listener {1}", event, listener ), ex.getCause() );
        }
    }
}
