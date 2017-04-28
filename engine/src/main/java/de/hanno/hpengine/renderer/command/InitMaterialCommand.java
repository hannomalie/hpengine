package de.hanno.hpengine.renderer.command;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.renderer.command.InitMaterialCommand.MaterialResult;
import de.hanno.hpengine.renderer.material.Material;

public class InitMaterialCommand implements Command<MaterialResult> {

	private Material material;
	
	public InitMaterialCommand(Material material) {
		this.material = material;
	}

	@Override
	public MaterialResult execute(Engine engine) {
		material.init();
		MaterialResult result = new MaterialResult(material);
		return result;
	}

	public static class MaterialResult extends Result {
		public Material material;
		private boolean successful;
		
		public MaterialResult(Material material) {
			this.material = material;
			if(this.material != null) {
				successful = true;
			}
		}

		@Override
		public boolean isSuccessful() {
			return successful;
		}
	}
}
