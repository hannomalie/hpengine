package de.hanno.hpengine.engine.event;

import de.hanno.hpengine.engine.model.material.SimpleMaterial;

import java.util.Optional;

public class MaterialChangedEvent {

    private SimpleMaterial material = null;

    public MaterialChangedEvent() { }

    public MaterialChangedEvent(SimpleMaterial material) {
        this.material = material;
    }

    public Optional<SimpleMaterial> getMaterial() {
        return Optional.ofNullable(material);
    }
}
