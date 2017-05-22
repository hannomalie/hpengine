package de.hanno.hpengine.engine.event;

public class AveragesDumpEvent {
    private final String dump;

    public AveragesDumpEvent(String dump) {
        this.dump = dump;
    }

    public String getDump() {
        return dump;
    }
}
