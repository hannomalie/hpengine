package test;

import component.ScriptComponent;
import engine.model.Entity;
import junit.framework.Assert;
import org.junit.Test;

public class ScriptTest extends TestWithWorld {

    @Test
    public void globalDefines() {
        world.getScriptManager().getGlobalContext().put("myInt", 2);

        Assert.assertEquals(2, world.getScriptManager().getGlobalContext().get("myInt"));
    }

    @Test
    public void localDefines() {
        ScriptComponent component = new ScriptComponent();
        Entity entity = world.getEntityFactory().getEntity().addComponent(component);
        entity.init(world);
        component.setInt("myInt", 5);

        Assert.assertEquals(5, component.getContext().getAttribute("myInt"));
    }

    @Test
    public void globalScopeFromLocalScope() {
        world.getScriptManager().getGlobalContext().put("myInt", 242);

        Assert.assertEquals(242, world.getScriptManager().getGlobalContext().get("myInt"));

        String script = "var myInt = myInt;";
        ScriptComponent component = new ScriptComponent(script);
        Entity entity = world.getEntityFactory().getEntity().addComponent(component);
        entity.init(world);

        Assert.assertEquals(242, component.getContext().getAttribute("myInt"));
    }

    @Test
    public void scriptUpdateFunctionCall() {
        world.getScriptManager().getGlobalContext().put("initCalled", false);
        world.getScriptManager().getGlobalContext().put("updateCalled", false);

        Assert.assertEquals(false, world.getScriptManager().getGlobalContext().get("initCalled"));
        Assert.assertEquals(false, world.getScriptManager().getGlobalContext().get("updateCalled"));

        String script = "var init = function(world) { initCalled = true; };" +
                "var update = function(seconds) { updateCalled = true; };";
        ScriptComponent component = new ScriptComponent(script);
        Entity entity = world.getEntityFactory().getEntity().addComponent(component);
        entity.init(world);

        entity.update(0.1f);

//        try {
//            world.getScriptManager().eval("marker = true;");
//        } catch (ScriptException e) {
//            e.printStackTrace();
//        }

        component.update(0.1f);
        Assert.assertEquals(true, world.getScriptManager().getGlobalContext().get("initCalled"));
        Assert.assertEquals(true, world.getScriptManager().getGlobalContext().get("updateCalled"));
    }
}