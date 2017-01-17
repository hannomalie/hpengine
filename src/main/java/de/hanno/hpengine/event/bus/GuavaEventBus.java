package de.hanno.hpengine.event.bus;

public class GuavaEventBus implements EventBus {

    private final com.google.common.eventbus.EventBus eventBus;

    public GuavaEventBus() {
        this.eventBus = new com.google.common.eventbus.EventBus();
    }

    @Override
    public <EVENT_TYPE> void post(EVENT_TYPE event) {
        eventBus.post(event);
    }

    @Override
    public void register(Object object) {
        eventBus.register(object);
    }

    @Override
    public void unregister(Object object) {
        eventBus.unregister(object);
    }
}
