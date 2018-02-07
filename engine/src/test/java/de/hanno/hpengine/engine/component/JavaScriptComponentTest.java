package de.hanno.hpengine.engine.component;

import de.hanno.hpengine.TestWithEngine;
import de.hanno.hpengine.engine.model.Entity;
import junit.framework.Assert;
import org.junit.Test;

public class JavaScriptComponentTest extends TestWithEngine {

    @Test
    public void globalDefines() {
        engine.getScriptManager().getGlobalContext().put("myInt", 2);

        Assert.assertEquals(2, engine.getScriptManager().getGlobalContext().get("myInt"));
    }

    @Test
    public void localDefines() {
        JavaScriptComponent component = new JavaScriptComponent();
        Entity entity = engine.getEntityFactory().getEntity().addComponent(component);
        entity.initialize();
        component.setInt("myInt", 5);

        Assert.assertEquals(5, component.getContext().getAttribute("myInt"));
    }

    @Test
    public void globalScopeFromLocalScope() {
        engine.getScriptManager().getGlobalContext().put("myInt", 242);

        Assert.assertEquals(242, engine.getScriptManager().getGlobalContext().get("myInt"));

        String script = "var myInt = myInt;";
        JavaScriptComponent component = new JavaScriptComponent(script);
        Entity entity = engine.getEntityFactory().getEntity().addComponent(component);
        entity.initialize();

        Assert.assertEquals(242, component.getContext().getAttribute("myInt"));
    }

    @Test
    public void scriptUpdateFunctionCall() {
        engine.getScriptManager().getGlobalContext().put("initCalled", false);
        engine.getScriptManager().getGlobalContext().put("updateCalled", false);

        Assert.assertEquals(false, engine.getScriptManager().getGlobalContext().get("initCalled"));
        Assert.assertEquals(false, engine.getScriptManager().getGlobalContext().get("updateCalled"));

        String script = "var init = function(world) { initCalled = true; };" +
                "var update = function(seconds) { updateCalled = true; };";
        JavaScriptComponent component = new JavaScriptComponent(script);
        Entity entity = engine.getEntityFactory().getEntity().addComponent(component);
        entity.initialize();

        entity.update(engine, 0.1f);

//        try {
//            world.getScriptManager().eval("marker = true;");
//        } catch (ScriptException e) {
//            e.printStackTrace();
//        }

        component.update(engine, 0.1f);
        Assert.assertEquals(true, engine.getScriptManager().getGlobalContext().get("initCalled"));
        Assert.assertEquals(true, engine.getScriptManager().getGlobalContext().get("updateCalled"));
    }
}
