package event;

import scene.EnvironmentProbe;

public class ProbeAddedEvent {
    public EnvironmentProbe getProbe() {
        return probe;
    }

    private final EnvironmentProbe probe;

    public ProbeAddedEvent(EnvironmentProbe probe) {
        this.probe = probe;
    }
}
