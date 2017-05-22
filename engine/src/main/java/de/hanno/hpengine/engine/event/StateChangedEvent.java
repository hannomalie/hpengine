package de.hanno.hpengine.engine.event;

public class StateChangedEvent {

    public String state = "";

    public StateChangedEvent(String newState) {
        state = newState;
    }
}
