package de.hanno.hpengine.test;

import de.hanno.hpengine.engine.Engine;
import junit.framework.Assert;
import org.junit.Test;
import de.hanno.hpengine.shader.ShaderDefine;

public class ShaderDefineTest {

	@Test
	public void generatesCorrectDefineString() {
		String actual = ShaderDefine.getGlobalDefinesString();
		Assert.assertTrue(actual.contains("const bool RAINEFFECT = " + (Engine.getInstance().getConfig().getRainEffect() != 0.0) + ";\n"));
	}
}
