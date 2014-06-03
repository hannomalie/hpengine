package main.shader;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Set;

import main.Material.MAP;

public enum ShaderDefine {
	
	DIFFUSE(MAP.DIFFUSE),
	NORMAL(MAP.NORMAL),
	SPECULAR(MAP.SPECULAR),
	OCCLUSION(MAP.OCCLUSION),
	HEIGHT(MAP.HEIGHT),
	REFLECTION(MAP.REFLECTION);

	private String defineString;
	private MAP map;
	
	public String getDefineText() {
		return defineString;
	};
	
	private ShaderDefine(MAP map) {
		this.map = map;
		this.defineString = "#define use_" + map.shaderVariableName;
	}
	
	public static String getDefineString(EnumSet<ShaderDefine> defines) {
		if (defines == null) {
			return "";
		}
		StringBuilder builder = new StringBuilder();
		
		for (ShaderDefine shaderDefine : defines) {
			builder.append(shaderDefine.getDefineText());
			builder.append("\n");
		}
		return builder.toString();
	}

	public static String getDefinesString(Set<MAP> maps) {
		StringBuilder builder = new StringBuilder();
		EnumSet<ShaderDefine> allDefines = EnumSet.allOf(ShaderDefine.class);
		
		for (MAP map : maps) {
			for (ShaderDefine shaderDefine : allDefines) {
				if (shaderDefine.map == map) {
					builder.append(shaderDefine.defineString);
					builder.append("\n");
				}
			}
		}
		
		return builder.toString();
	}
}
