package component;

import camera.Camera;

public class CameraComponent extends BaseComponent {

    Camera camera;

    public CameraComponent(Camera camera) {
        this.camera = camera;
    }

    @Override
    public Class getIdentifier() {
        return CameraComponent.class;
    }

    public Camera getCamera() {
        return camera;
    }

    public void setCamera(Camera camera) {
        this.camera = camera;
    }
}
