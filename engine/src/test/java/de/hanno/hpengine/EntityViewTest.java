package de.hanno.hpengine;

import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.model.*;
import org.junit.Test;
import de.hanno.hpengine.util.gui.EntityView;

import javax.swing.*;
import java.io.File;

public class EntityViewTest extends TestWithEngine {

    @Test
    public void testEntityViewGui() throws Exception {
        StaticModel model = new OBJLoader().loadTexturedModel(engine.getMaterialManager(), new File(DirectoryManager.WORKDIR_NAME + "/assets/models/sphere.obj"));
        Entity parentEntity = engine.getSceneManager().getScene().getEntityManager().create("parent");

        JFrame frame = new JFrame();

        frame.add(new EntityView(engine, parentEntity));

        frame.setVisible(true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        while(true) {Thread.sleep(100);}
    }

}
