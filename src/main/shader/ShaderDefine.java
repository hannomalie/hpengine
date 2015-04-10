package main.shader;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Set;

import main.World;
import main.renderer.material.Material.MAP;

public enum ShaderDefine {
	
	DIFFUSE(MAP.DIFFUSE),
	NORMAL(MAP.NORMAL),
	SPECULAR(MAP.SPECULAR),
	OCCLUSION(MAP.OCCLUSION),
	HEIGHT(MAP.HEIGHT),
	REFLECTION(MAP.REFLECTION),
	ROUGHNESS(MAP.ROUGHNESS);

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
	
	public static String getGlobalDefinesString() {
		StringBuilder builder = new StringBuilder();

		appendWithSemicolonAndNewLine(builder, "const bool RAINEFFECT = " + (World.RAINEFFECT != 0.0));
		appendWithSemicolonAndNewLine(builder, "const bool MULTIPLE_DIFFUSE_SAMPLES = " + World.MULTIPLE_DIFFUSE_SAMPLES);
		appendWithSemicolonAndNewLine(builder, "const bool MULTIPLE_DIFFUSE_SAMPLES_PROBES = " + World.MULTIPLE_DIFFUSE_SAMPLES_PROBES);
		appendWithSemicolonAndNewLine(builder, "const bool USE_CONETRACING_FOR_DIFFUSE = " + World.USE_CONETRACING_FOR_DIFFUSE);
		appendWithSemicolonAndNewLine(builder, "const bool USE_CONETRACING_FOR_DIFFUSE_PROBES = " + World.USE_CONETRACING_FOR_DIFFUSE_PROBES);
		appendWithSemicolonAndNewLine(builder, "const bool PRECOMPUTED_RADIANCE = " + World.PRECOMPUTED_RADIANCE);
		appendWithSemicolonAndNewLine(builder, "const bool SCATTERING = " + World.SCATTERING);
		
		return builder.toString();
	}
	
	private static StringBuilder appendWithSemicolonAndNewLine(StringBuilder builder, String toAppend) {
		builder.append(toAppend);
		builder.append(";\n");
		return builder;
	}
}
