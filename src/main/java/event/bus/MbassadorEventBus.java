package event.bus;

import net.engio.mbassy.bus.MBassador;

public class MBassadorEventBus implements EventBus {

    private final MBassador eventBus;
    private final boolean defaultAsync;

    public MBassadorEventBus() {
        this(true);
    }
    public MBassadorEventBus(boolean defaultAsync) {
        this.eventBus = new MBassador();
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
