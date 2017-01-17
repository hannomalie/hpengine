package de.hanno.hpengine.event;

public class StateChangedEvent {

    public String state = "";

    public StateChangedEvent(String newState) {
        state = newState;
    }
}
