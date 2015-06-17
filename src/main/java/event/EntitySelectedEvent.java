package event;


import engine.model.Entity;

public class EntitySelectedEvent {

	private Entity entity;

	public EntitySelectedEvent(Entity entity) {
		this.setEntity(entity);
	}

	public Entity getEntity() {
		return entity;
	}

	public void setEntity(Entity entity) {
		this.entity = entity;
	}

}
