package event.bus;

public interface EventBus {

    <EVENT_TYPE extends Object> void post(EVENT_TYPE event);

    void register(Object object);

    void unregister(Object object);
}
