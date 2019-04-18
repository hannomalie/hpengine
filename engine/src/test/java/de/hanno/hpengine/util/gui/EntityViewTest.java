package de.hanno.hpengine.util.gui;

import de.hanno.hpengine.TestWithEngine;
import de.hanno.hpengine.engine.directory.DirectoryManager;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.model.*;
import org.junit.Ignore;
import org.junit.Test;

import javax.swing.*;
import java.io.File;

public class EntityViewTest extends TestWithEngine {

    @Test
    @Ignore("Manual test only")
    public void testEntityViewGui() throws Exception {
        StaticModel model = new OBJLoader().loadTexturedModel(engine.getScene().getMaterialManager(), new File(DirectoryManager.engineDir + "/assets/models/sphere.obj"));
        Entity parentEntity = engine.getSceneManager().getScene().getEntityManager().create("parent");

        JFrame frame = new JFrame();

        frame.add(new EntityView(engine, parentEntity));

        frame.setVisible(true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        while(true) {Thread.sleep(100);}
    }

}
