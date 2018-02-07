package de.hanno.hpengine.engine.camera;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.component.InputControllerComponent;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.input.Input;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

public class MovableCamera extends Camera {

    protected float rotationDelta = 10f;
    protected float scaleDelta = 0.1f;
    protected float posDelta = 100f;

    public MovableCamera() {
        addComponent(new InputControllerComponent() {
                         private static final long serialVersionUID = 1L;

                        public Vector3f linearAcc = new Vector3f();
                        public Vector3f linearVel = new Vector3f();

                        /** Always rotation about the local XYZ axes of the camera! */
                        public Vector3f angularAcc = new Vector3f();
                        public Vector3f angularVel = new Vector3f();

                        public Vector3f position = new Vector3f(0, 0, 10);
                        public Quaternionf rotation = new Quaternionf();

                        private float pitchAccel = 0;
                        private float yawAccel = 0;

                         @Override
                         public void update(Engine engine, float seconds) {
//                             linearVel.fma(seconds, linearAcc);
//                             // update angular velocity based on angular acceleration
//                             angularVel.fma(seconds, angularAcc);
//                             // update the rotation based on the angular velocity
//                             rotation.integrate(seconds, angularVel.x, angularVel.y, angularVel.z);
//                             // update position based on linear velocity
//                             position.fma(seconds, linearVel);

                             float turbo = 1f;
                             if (engine.getInput().isKeyPressed(GLFW_KEY_LEFT_SHIFT)) {
                                 turbo = 3f;
                             }

                             float rotationAmount = 10.1f * turbo * seconds * rotationDelta * Config.getInstance().getCameraSpeed();
                             if (engine.getInput().isMouseClicked(0)) {
                                 double pitchAmount = Math.toRadians((engine.getInput().getDYSmooth() * rotationAmount) % 360);
                                 pitchAccel = (float) Math.max(2 * Math.PI, pitchAccel + pitchAmount);
                                 pitchAccel = Math.max(0, pitchAccel * 0.9f);

                                 double yawAmount = Math.toRadians((engine.getInput().getDXSmooth() * rotationAmount) % 360);
                                 yawAccel = (float) Math.max(2 * Math.PI, yawAccel + yawAmount);
                                 yawAccel = Math.max(0, yawAccel * 0.9f);

                                 rotation.rotateY((float) yawAmount);
                                 rotation.rotateX((float) -pitchAmount);

                                 getEntity().rotateY((float) -yawAmount);
                                 getEntity().rotateX((float) pitchAmount);
                             }

                             float moveAmount = turbo * posDelta * seconds * Config.getInstance().getCameraSpeed();
                             if (engine.getInput().isKeyPressed(GLFW_KEY_W)) {
                                 getEntity().translate(new Vector3f(0, 0, -moveAmount));
                             }
                             if (engine.getInput().isKeyPressed(GLFW_KEY_S)) {
                                 getEntity().translate(new Vector3f(0, 0, moveAmount));
                             }
                             if (engine.getInput().isKeyPressed(GLFW_KEY_A)) {
                                 getEntity().translate(new Vector3f(-moveAmount, 0, 0));
                             }
                             if (engine.getInput().isKeyPressed(GLFW_KEY_D)) {
                                 getEntity().translate(new Vector3f(moveAmount, 0, 0));
                             }
                             if (engine.getInput().isKeyPressed(GLFW_KEY_Q)) {
                                 getEntity().translate(new Vector3f(0, -moveAmount, 0));
                             }
                             if (engine.getInput().isKeyPressed(GLFW_KEY_E)) {
                                 getEntity().translate(new Vector3f(0, moveAmount, 0));
                             }


//                             getEntity().rotate(rotation).translate(-position.x, -position.y, -position.z);
                         }
                     }
        );
    }
}
