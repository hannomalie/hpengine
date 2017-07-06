package de.hanno.hpengine;

import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.model.*;
import org.junit.Test;
import de.hanno.hpengine.util.gui.EntityView;

import javax.swing.*;
import java.io.File;

public class EntityViewTest extends TestWithEngine {

    @Test
    public void testEntityViewGui() throws Exception {
        StaticModel model = new OBJLoader().loadTexturedModel(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/sphere.obj"));
        Entity parentEntity = EntityFactory.getInstance().getEntity("parent", model);

        JFrame frame = new JFrame();

        frame.add(new EntityView(Engine.getInstance(), parentEntity));

        frame.setVisible(true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        while(true) {Thread.sleep(100);}
    }

}
