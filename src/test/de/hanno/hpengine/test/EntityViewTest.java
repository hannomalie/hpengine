package de.hanno.hpengine.test;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.EntityFactory;
import de.hanno.hpengine.engine.model.Model;
import de.hanno.hpengine.engine.model.OBJLoader;
import org.junit.Test;
import de.hanno.hpengine.util.gui.EntityView;

import javax.swing.*;
import java.io.File;
import java.util.List;

public class EntityViewTest extends TestWithEngine {

    @Test
    public void testEntityViewGui() throws Exception {
        List<Model> models = new OBJLoader().loadTexturedModel(new File(Engine.WORKDIR_NAME + "/assets/models/sphere.obj"));
        Entity parentEntity = EntityFactory.getInstance().getEntity("parent", models);

        JFrame frame = new JFrame();

        frame.add(new EntityView(Engine.getInstance(), parentEntity));

        frame.setVisible(true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        while(true) {Thread.sleep(10);}
    }

}
