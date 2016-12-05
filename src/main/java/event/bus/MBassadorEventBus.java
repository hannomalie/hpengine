package event.bus;

import net.engio.mbassy.bus.MBassador;
import net.engio.mbassy.bus.config.BusConfiguration;
import net.engio.mbassy.bus.config.Feature;
import net.engio.mbassy.bus.config.IBusConfiguration;

import java.util.logging.Level;
import java.util.logging.Logger;

public class MBassadorEventBus implements EventBus {

    private static final Logger LOGGER = Logger.getLogger(EventBus.class.getName());

    private final MBassador eventBus;
    private final boolean defaultAsync;

    public MBassadorEventBus() {
        this(true);
    }
    public MBassadorEventBus(boolean defaultAsync) {

        IBusConfiguration config = new BusConfiguration()
            .addFeature(Feature.SyncPubSub.Default())
            .addFeature(Feature.AsynchronousHandlerInvocation.Default())
            .addFeature(Feature.AsynchronousMessageDispatch.Default())
            .addPublicationErrorHandler(error -> {
                LOGGER.severe(error.getMessage());
                LOGGER.severe(String.valueOf(error.getCause()));
                LOGGER.severe(error.getPublishedMessage().toString());
                throw new IllegalStateException("Eventbus error");
            });
        this.eventBus = new MBassador(config);

        this.defaultAsync = defaultAsync;
    }

    @Override
    public <EVENT_TYPE> void post(EVENT_TYPE event) {
        if(defaultAsync) {
            eventBus.post(event).asynchronously();
        } else {
            eventBus.post(event).now();
        }
    }

    @Override
    public void register(Object object) {
        eventBus.subscribe(object);
    }

    @Override
    public void unregister(Object object) {
//        eventBus.unsubscribe(object);
//        No need to unsubscribe since WeakReferences are used
    }
}
