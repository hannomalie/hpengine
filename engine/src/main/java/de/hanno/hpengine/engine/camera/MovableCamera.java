package de.hanno.hpengine.engine.camera;

import de.hanno.hpengine.engine.component.InputControllerComponent;

public class MovableCamera extends Camera {
    public MovableCamera() {
        InputControllerComponent component = new MovableInputComponent();
        addComponent(component);
    }
}
