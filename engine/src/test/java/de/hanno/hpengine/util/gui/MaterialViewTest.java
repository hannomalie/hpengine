package de.hanno.hpengine.util.gui;

import org.junit.Ignore;
import org.junit.Test;

public class MaterialViewTest extends ViewTest {

    @Ignore
    @Test
    public void showMaterialView() {
        openViewInFrame(new MaterialView(engine, engine.getScene().getMaterialManager().getDefaultMaterial()));
    }

}
