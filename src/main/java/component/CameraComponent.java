package component;

import camera.Camera;
import engine.model.Entity;

public class CameraComponent extends BaseComponent {

    Camera camera;

    public CameraComponent(Camera camera) {
        this.camera = camera;
    }

    @Override
    public String getIdentifier() {
        return "CameraComponent";
    }

    public Camera getCamera() {
        return camera;
    }

    public void setCamera(Camera camera) {
        this.camera = camera;
    }

    @Override
    public void update(float seconds) {
        camera.update(seconds);
    }

    @Override
    public void initAfterAdd(Entity entity) {
        camera.setTransform(entity.getTransform());
    }
}
