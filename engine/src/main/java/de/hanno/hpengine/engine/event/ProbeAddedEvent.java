package de.hanno.hpengine.engine.event;

import de.hanno.hpengine.engine.scene.EnvironmentProbe;

public class ProbeAddedEvent {
    public EnvironmentProbe getProbe() {
        return probe;
    }

    private final EnvironmentProbe probe;

    public ProbeAddedEvent(EnvironmentProbe probe) {
        this.probe = probe;
    }
}
