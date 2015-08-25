package test;

import config.Config;
import junit.framework.Assert;
import org.junit.Test;
import shader.ShaderDefine;

public class ShaderDefineTest {

	@Test
	public void generatesCorrectDefineString() {
		String actual = ShaderDefine.getGlobalDefinesString();
		Assert.assertTrue(actual.contains("const bool RAINEFFECT = " + (Config.RAINEFFECT != 0.0) + ";\n"));
	}
}
