package de.hanno.hpengine.engine.graphics.renderer.command;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.renderer.command.InitMaterialCommand.MaterialResult;
import de.hanno.hpengine.engine.model.material.SimpleMaterial;

public class InitMaterialCommand implements Command<MaterialResult> {

	private SimpleMaterial material;
	
	public InitMaterialCommand(SimpleMaterial material) {
		this.material = material;
	}

	@Override
	public MaterialResult execute(Engine engine) {
		material.init(engine.getScene().getMaterialManager());
		MaterialResult result = new MaterialResult(material);
		return result;
	}

	public static class MaterialResult extends Result {
		public SimpleMaterial material;
		private boolean successful;
		
		public MaterialResult(SimpleMaterial material) {
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
