package de.hanno.hpengine.util.gui;

import org.junit.Ignore;
import org.junit.Test;

public class MainLightViewTest extends ViewTest {

    @Test
    @Ignore("Manual test only")
    public void showMaterialView() {
        openViewInFrame(new MainLightView(engine));
    }

}
