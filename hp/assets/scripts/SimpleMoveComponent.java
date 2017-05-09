import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import org.lwjgl.util.vector.Vector3f;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.Entity.Instance;

public class SimpleMoveComponent implements LifeCycle {
    public Entity entity;
    private float flip = 1;
    private int counter = 0;
    private Vector3f temp = new Vector3f();

    @Override
    public void update(float seconds) {
        entity.move(new Vector3f(0,0,0.11f));
        for(int i = 0; i < entity.getInstances().size(); i++) {
            Instance currentInstance = entity.getInstances().get(i);
            temp.set(0.001f*(float)i*flip,0f,0.001f*(float)i*flip);
            currentInstance.move(temp);
        }
        if(counter >= 149) {
            flip *= -1;
            counter = 0;
        }
        counter++;
    }
    public boolean isInitialized() { return true; }
}