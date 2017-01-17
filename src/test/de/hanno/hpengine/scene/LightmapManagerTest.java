package de.hanno.hpengine.scene;

import org.junit.Assert;
import org.junit.Test;
import org.lwjgl.util.vector.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static de.hanno.hpengine.scene.LightmapManager.PADDING;

public class LightmapManagerTest {

    @Test
    public void testConstructor() {
        LightmapManager lightmapManager = new LightmapManager();
        Assert.assertEquals(1f, lightmapManager.getWidth(), 0.0001f);
        Assert.assertEquals(1f, lightmapManager.getHeight(), 0.0001f);
    }

    @Test
    public void testSimpleAddition() {
        LightmapManager lightmapManager = new LightmapManager();
        {
            List<Vector3f[]> lightmapCoords = getLightmapCoords(new Vector3f(), new Vector3f(0, 10, 0), new Vector3f(10, 0, 0));
            List<Vector3f[]> resultingLightmapCords = lightmapManager.add(lightmapCoords);

            Assert.assertEquals(new Vector3f(), resultingLightmapCords.get(0)[0]);
            Assert.assertEquals(new Vector3f(0, 10, 0), resultingLightmapCords.get(0)[1]);
            Assert.assertEquals(new Vector3f(10, 0, 0), resultingLightmapCords.get(0)[2]);
            Assert.assertEquals(10f+ PADDING, lightmapManager.getWidth(), 0.0001f);
            Assert.assertEquals(10f+ PADDING, lightmapManager.getHeight(), 0.0001f);

        }
        {
            List<Vector3f[]> lightmapCoords = getLightmapCoords(new Vector3f(), new Vector3f(0, 15, 0), new Vector3f(15, 0, 0));
            List<Vector3f[]> resultingLightmapCords = lightmapManager.add(lightmapCoords);

            Assert.assertEquals(new Vector3f(0, 11, 0), resultingLightmapCords.get(0)[0]);
            Assert.assertEquals(new Vector3f(0, 15+11, 0), resultingLightmapCords.get(0)[1]);
            Assert.assertEquals(new Vector3f(15, 11, 0), resultingLightmapCords.get(0)[2]);
        }
        {
            List<Vector3f[]> lightmapCoords = getLightmapCoords(new Vector3f(), new Vector3f(0, 15, 0), new Vector3f(15, 0, 0));
            List<Vector3f[]> resultingLightmapCords = lightmapManager.add(lightmapCoords);

            Assert.assertEquals(new Vector3f(16, 11, 0), resultingLightmapCords.get(0)[0]);
            Assert.assertEquals(new Vector3f(16, 15+11, 0), resultingLightmapCords.get(0)[1]);
            Assert.assertEquals(new Vector3f(15+16, 11, 0), resultingLightmapCords.get(0)[2]);
        }

//        lightmapManager.showLightmap();
//        while(true) {}
    }

    @Test
    public void testTriangleEnlargement() {
        LightmapManager lightmapManager = new LightmapManager();
        {
            List<Vector3f[]> lightmapCoords = getLightmapCoords(new Vector3f(), new Vector3f(0, .5f, 0), new Vector3f(.5f, 0, 0));
            List<Vector3f[]> resultingLightmapCords = lightmapManager.add(lightmapCoords);

            Assert.assertEquals(new Vector3f(), resultingLightmapCords.get(0)[0]);
            Assert.assertEquals(new Vector3f(0, LightmapManager.MIN_SIZE, 0), resultingLightmapCords.get(0)[0]);
            Assert.assertEquals(new Vector3f(LightmapManager.MIN_SIZE, 0, 0), resultingLightmapCords.get(0)[0]);
            Assert.assertEquals(LightmapManager.MIN_SIZE + PADDING, lightmapManager.getWidth(), 0.0001f);
            Assert.assertEquals(LightmapManager.MIN_SIZE + PADDING, lightmapManager.getHeight(), 0.0001f);

        }
    }

    public List<Vector3f[]> getLightmapCoords(Vector3f a, Vector3f b, Vector3f c) {
        List<Vector3f[]> lightmapCoords = new ArrayList<>(1);
        Vector3f[] actualLightmapCoords = new Vector3f[3];
        actualLightmapCoords[0] = a;
        actualLightmapCoords[1] = b;
        actualLightmapCoords[2] = c;
        lightmapCoords.add(actualLightmapCoords);
        return lightmapCoords;
    }

}
