package de.hanno.hpengine.engine.graphics.renderer.command;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.model.Entity;

public class RemoveEntityCommand implements Command {

	private Entity entity;

	public RemoveEntityCommand(Entity entity) {
		this.entity = entity;
	}
	
	@Override
	public Result execute(Engine engine) {
		boolean result = engine.getSceneManager().getScene().removeEntity(entity);
		if (result) {
			return new Result() {
				@Override
				public boolean isSuccessful() {
					return true;
				}
			};
		} else {
			return new Result() {
				@Override
				public boolean isSuccessful() {
					return false;
				}
			};
		}
	}
}
