package test;

import component.ScriptComponent;
import engine.model.Entity;
import junit.framework.Assert;
import org.junit.Test;

public class ScriptTest extends TestWithAppContext {

    @Test
    public void globalDefines() {
        appContext.getScriptManager().getGlobalContext().put("myInt", 2);

        Assert.assertEquals(2, appContext.getScriptManager().getGlobalContext().get("myInt"));
    }

    @Test
    public void localDefines() {
        ScriptComponent component = new ScriptComponent();
        Entity entity = appContext.getEntityFactory().getEntity().addComponent(component);
        entity.init(appContext);
        component.setInt("myInt", 5);

        Assert.assertEquals(5, component.getContext().getAttribute("myInt"));
    }

    @Test
    public void globalScopeFromLocalScope() {
        appContext.getScriptManager().getGlobalContext().put("myInt", 242);

        Assert.assertEquals(242, appContext.getScriptManager().getGlobalContext().get("myInt"));

        String script = "var myInt = myInt;";
        ScriptComponent component = new ScriptComponent(script);
        Entity entity = appContext.getEntityFactory().getEntity().addComponent(component);
        entity.init(appContext);

        Assert.assertEquals(242, component.getContext().getAttribute("myInt"));
    }

    @Test
    public void scriptUpdateFunctionCall() {
        appContext.getScriptManager().getGlobalContext().put("initCalled", false);
        appContext.getScriptManager().getGlobalContext().put("updateCalled", false);

        Assert.assertEquals(false, appContext.getScriptManager().getGlobalContext().get("initCalled"));
        Assert.assertEquals(false, appContext.getScriptManager().getGlobalContext().get("updateCalled"));

        String script = "var init = function(world) { initCalled = true; };" +
                "var update = function(seconds) { updateCalled = true; };";
        ScriptComponent component = new ScriptComponent(script);
        Entity entity = appContext.getEntityFactory().getEntity().addComponent(component);
        entity.init(appContext);

        entity.update(0.1f);

//        try {
//            world.getScriptManager().eval("marker = true;");
//        } catch (ScriptException e) {
//            e.printStackTrace();
//        }

        component.update(0.1f);
        Assert.assertEquals(true, appContext.getScriptManager().getGlobalContext().get("initCalled"));
        Assert.assertEquals(true, appContext.getScriptManager().getGlobalContext().get("updateCalled"));
    }
}