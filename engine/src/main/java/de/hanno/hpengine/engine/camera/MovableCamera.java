package de.hanno.hpengine.engine.camera;

import de.hanno.hpengine.engine.component.InputControllerComponent;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.Transform;
import de.hanno.hpengine.engine.input.Input;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector3f;

public class MovableCamera extends Camera {

    protected float rotationDelta = 125f;
    protected float scaleDelta = 0.1f;
    protected float posDelta = 100f;

    public MovableCamera() {
        addComponent(new InputControllerComponent() {
                         private static final long serialVersionUID = 1L;

                         @Override
                         public void update(float seconds) {

                             float turbo = 1f;
                             if (Input.isKeyPressed(Keyboard.KEY_LSHIFT)) {
                                 turbo = 3f;
                             }

                             float rotationAmount = 1.1f * turbo * rotationDelta * seconds * Config.getInstance().getCameraSpeed();
                             if (Input.isMouseClicked(0)) {
                                 getEntity().rotate(Transform.WORLD_UP, -Input.getDX() * rotationAmount);
//                                 getEntity().rotate(Transform.WORLD_RIGHT, Input.getDY() * rotationAmount);
//                                 getEntity().rotate(Transform.WORLD_UP, -Input.getDX() * rotationAmount);
//                                 getEntity().rotate(getUpDirection(), -Input.getDX() * rotationAmount);
                             }
                             if (Input.isMouseClicked(1)) {
                                 getEntity().rotate(Transform.WORLD_RIGHT, Input.getDY() * rotationAmount);
                             }
                             if (Input.isMouseClicked(2)) {
                                 getEntity().rotate(Transform.WORLD_VIEW, Input.getDX() * rotationAmount);
                             }

                             float moveAmount = turbo * posDelta * seconds * Config.getInstance().getCameraSpeed();
                             if (Input.isKeyPressed(Keyboard.KEY_W)) {
                                 getEntity().move(new Vector3f(0, 0, -moveAmount));
                             }
                             if (Input.isKeyPressed(Keyboard.KEY_A)) {
                                 getEntity().move(new Vector3f(-moveAmount, 0, 0));
                             }
                             if (Input.isKeyPressed(Keyboard.KEY_S)) {
                                 getEntity().move(new Vector3f(0, 0, moveAmount));
                             }
                             if (Input.isKeyPressed(Keyboard.KEY_D)) {
                                 getEntity().move(new Vector3f(moveAmount, 0, 0));
                             }
                             if (Input.isKeyPressed(Keyboard.KEY_Q)) {
                                 getEntity().move(new Vector3f(0, -moveAmount, 0));
                             }
                             if (Input.isKeyPressed(Keyboard.KEY_E)) {
                                 getEntity().move(new Vector3f(0, moveAmount, 0));
                             }
                         }
                     }
        );
    }
}
