package de.hanno.hpengine.engine.event;

import de.hanno.hpengine.engine.model.material.Material;

import java.util.Optional;

public class MaterialChangedEvent {

    private Material material = null;

    public MaterialChangedEvent() { }

    public MaterialChangedEvent(Material material) {
        this.material = material;
    }

    public Optional<Material> getMaterial() {
        return Optional.ofNullable(material);
    }
}
