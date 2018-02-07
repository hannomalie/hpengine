package de.hanno.hpengine.engine.component;

import java.io.Serializable;

public class InputControllerComponent extends BaseComponent implements Serializable {

    private static final long serialVersionUID = 1L;

    @Override
    public String getIdentifier() {
        return "InputControllerComponent";
    }

    @Override
    public boolean isInitialized() { return true; }
}
