package de.hanno.hpengine;

import de.hanno.hpengine.config.Config;
import junit.framework.Assert;
import org.junit.Test;
import de.hanno.hpengine.shader.ShaderDefine;

public class ShaderDefineTest {

	@Test
	public void generatesCorrectDefineString() {
		String actual = ShaderDefine.getGlobalDefinesString();
		Assert.assertTrue(actual.contains("const bool RAINEFFECT = " + (Config.getInstance().getRainEffect() != 0.0) + ";\n"));
	}
}
