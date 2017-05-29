package de.hanno.hpengine.engine.camera;

import de.hanno.hpengine.engine.component.InputControllerComponent;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.Transform;
import de.hanno.hpengine.engine.input.Input;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class MovableCamera extends Camera {

    protected float rotationDelta = 10f;
    protected float scaleDelta = 0.1f;
    protected float posDelta = 100f;

    public MovableCamera() {
        addComponent(new InputControllerComponent() {
                         private static final long serialVersionUID = 1L;

                        private float absolutePitch;
                        private float absoluteYaw;

                        private float pitchAccel = 0;
                        private float yawAccel = 0;

                         @Override
                         public void update(float seconds) {

                             float turbo = 1f;
                             if (Input.isKeyPressed(Keyboard.KEY_LSHIFT)) {
                                 turbo = 3f;
                             }

                             float rotationAmount = 100.1f * turbo * rotationDelta * Config.getInstance().getCameraSpeed();
                             if (Input.isMouseClicked(0)) {
                                 double pitchAmount = Math.toRadians((Input.getDYSmooth() * rotationAmount) % 360);
                                 pitchAccel = (float) Math.max(2 * Math.PI, pitchAccel + pitchAmount);
                                 absolutePitch += pitchAmount;
                                 pitchAccel = Math.max(0, pitchAccel * 0.9f);

                                 double yawAmount = Math.toRadians((Input.getDXSmooth() * rotationAmount) % 360);
                                 yawAccel = (float) Math.max(2 * Math.PI, yawAccel + yawAmount);
                                 absoluteYaw += -yawAmount;
                                 yawAccel = Math.max(0, yawAccel * 0.9f);


                                 Quaternion pitchQuat = new Quaternion();
                                 pitchQuat.setFromAxisAngle(new Vector4f(Transform.WORLD_RIGHT.x, Transform.WORLD_RIGHT.y, Transform.WORLD_RIGHT.z, (float) Math.toRadians(absolutePitch)));
                                 Quaternion yawQuat = new Quaternion();
                                 yawQuat.setFromAxisAngle(new Vector4f(Transform.WORLD_UP.x, Transform.WORLD_UP.y, Transform.WORLD_UP.z, (float) Math.toRadians(absoluteYaw)));
                                 getEntity().setOrientation(Quaternion.mul(yawQuat,pitchQuat,  null).normalise(null));
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
