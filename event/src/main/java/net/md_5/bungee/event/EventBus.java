package net.md_5.bungee.event;

import com.google.common.collect.ImmutableSet;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.Value;

public class EventBus
{

    private static final Comparator<EventRegistration> PRIORITY_COMPARATOR = Comparator.comparing( EventRegistration::getPriority );

    private final Map<Object, List<EventRegistration>> byListener = new HashMap<>();
    private final Map<Class<?>, EventRegistration[]> byEventSorted = new ConcurrentHashMap<>();
    private final Lock lock = new ReentrantLock();
    private final Logger logger;

    public EventBus()
    {
        this( null );
    }

    public EventBus(Logger logger)
    {
        this.logger = ( logger == null ) ? Logger.getLogger( Logger.GLOBAL_LOGGER_NAME ) : logger;
    }

    // Generics are erased at runtime anyways, and we check for compatibility when the event is registered
    @SuppressWarnings("unchecked")
    public <T> void post(T event)
    {
        EventRegistration[] registrations = byEventSorted.get( event.getClass() );

        if ( registrations != null )
        {
            for ( EventRegistration registration : registrations )
            {
                ( (EventExecutor<T>) registration.executor ).execute( event );
            }
        }
    }

    private List<EventRegistration> discoverRegistrationsFor(Object listener)
    {
        List<EventRegistration> registrations = new ArrayList<>();
        Set<Method> methods = ImmutableSet.<Method>builder()
                .add( listener.getClass().getMethods() )
                .add( listener.getClass().getDeclaredMethods() )
                .build();

        for ( final Method m : methods )
        {
            EventHandler annotation = m.getAnnotation( EventHandler.class );
            if ( annotation != null )
            {
                Class<?>[] params = m.getParameterTypes();
                if ( params.length != 1 )
                {
                    logger.log( Level.INFO, "Method {0} in class {1} annotated with {2} does not have single argument", new Object[]
                    {
                        m, listener.getClass(), annotation
                    } );
                    continue;
                }

                EventExecutor<?> executor = new EventExecutorMethod( logger, listener, m );
                registrations.add( new EventRegistration( listener, params[0], annotation.priority(), executor ) );
            }
        }

        return registrations;
    }

    public <T> void register(Object listener, Class<T> eventClass, byte priority, EventExecutor<T> executor)
    {
        lock.lock();
        try
        {
            EventRegistration registration = new EventRegistration( listener, eventClass, priority, executor );
            register( registration );
            byListener.computeIfAbsent( listener, e -> new ArrayList<>() ).add( registration );
        } finally
        {
            lock.unlock();
        }
    }

    public void register(Object listener)
    {
        List<EventRegistration> pendingRegistrations = discoverRegistrationsFor( listener );

        lock.lock();
        try
        {
            for ( EventRegistration registration : pendingRegistrations )
            {
                register( registration );
            }

            List<EventRegistration> currentRegistrations = byListener.get( listener );

            if ( currentRegistrations == null )
            {
                byListener.put( listener, pendingRegistrations );
            } else
            {
                currentRegistrations.addAll( pendingRegistrations );
            }
        } finally
        {
            lock.unlock();
        }
    }

    private void register(EventRegistration registration)
    {
        EventRegistration[] currentRegistrations = byEventSorted.get( registration.eventClass );

        if ( currentRegistrations == null )
        {
            byEventSorted.put( registration.eventClass, new EventRegistration[]
            {
                registration
            } );
        } else
        {
            System.out.println( Arrays.toString( currentRegistrations ) );
            int index = Arrays.binarySearch( currentRegistrations, registration, PRIORITY_COMPARATOR );

            if ( index < 0 )
            {
                index = -index - 1;
            }

            EventRegistration[] newRegistrations = new EventRegistration[ currentRegistrations.length + 1 ];
            System.arraycopy( currentRegistrations, 0, newRegistrations, 0, index );
            newRegistrations[ index ] = registration;
            System.arraycopy( currentRegistrations, index, newRegistrations, index + 1, currentRegistrations.length - index );

            byEventSorted.put( registration.eventClass, newRegistrations );
        }
    }

    public void unregister(Object listener)
    {
        List<EventRegistration> registrations = byListener.get( listener );

        if ( registrations == null )
            return;

        lock.lock();
        try
        {
            for ( EventRegistration registration : registrations )
            {
                EventRegistration[] currentRegistrations = byEventSorted.get( registration.eventClass );

                if ( currentRegistrations.length == 1 )
                {
                    byEventSorted.remove( registration.eventClass );
                } else
                {
                    EventRegistration[] newRegistrations = new EventRegistration[ currentRegistrations.length - 1 ];
                    int i = 0;

                    for ( EventRegistration currentRegistration : currentRegistrations )
                    {
                        if ( currentRegistration != registration )
                        {
                            newRegistrations[i++] = currentRegistration;
                        }
                    }

                    byEventSorted.put( registration.eventClass, newRegistrations );
                }
            }
        } finally
        {
            lock.unlock();
        }
    }

    @Value
    private static class EventRegistration
    {
        Object listener;
        Class<?> eventClass;
        byte priority;
        EventExecutor<?> executor;
    }
}
