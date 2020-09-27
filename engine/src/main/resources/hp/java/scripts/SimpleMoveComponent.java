package scripts;

import com.carrotsearch.hppc.IntFloatHashMap;
import com.carrotsearch.hppc.IntFloatMap;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.Instance;
import org.joml.Vector3f;

import java.util.Random;

public class SimpleMoveComponent implements LifeCycle {
    public Entity entity;
    private Vector3f temp = new Vector3f();

    private int randomCount = 16000;
    private IntFloatMap randoms = new IntFloatHashMap();
    private int[] counters = new int[randomCount];
    private int[] flips = new int[randomCount];

    public void init() {
        Random random = new Random();
        for(int i = 0; i < randomCount; i++) {
            float v = 100f * random.nextFloat();
            randoms.put(i, v);
            counters[i] = (int)(random.nextFloat() * 150f);
            flips[i] = 1;
        }
    }

    public void update(final float seconds) {
        entity.translateLocal(new Vector3f(0,0,seconds));

        for(int i = 0; i < entity.getInstances().size(); i++) {
            Instance currentInstance = entity.getInstances().get(i);
            temp.set(randoms.get(i%randomCount)*flips[i%randomCount],0f,randoms.get(i%randomCount)*flips[i%randomCount]);
            currentInstance.translate(temp.mul(seconds));

            if(counters[i%randomCount] >= 149) {
                flips[i%randomCount] *= -1;
                counters[i%randomCount] = 0;
            }
            counters[i%randomCount]++;
        }

    }
    public boolean isInitialized() { return true; }
}