package engine.model;

import camera.Camera;
import component.Component;
import component.ModelComponent;
import engine.Transform;
import engine.World;
import engine.lifecycle.LifeCycle;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.material.MaterialFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class Entity implements Transformable, LifeCycle, Serializable {
	private static final long serialVersionUID = 1;
	public static int count = 0;

	public Entity addComponent(Component component) {
		return addComponent(component, component.getIdentifier());
	}
	public Entity addComponent(Component component , String key) {
		component.setEntity(this);
		getComponents().put(key, component);
		component.initAfterAdd(this);
		return this;
	}

	public void removeComponent(Component component) {
		getComponents().remove(component.getIdentifier(), component);
	}
	public void removeComponent(String key) {
		getComponents().remove(key);
	}

	public <T extends Component> T getComponent(Class<T> type) {
		Component component = getComponents().get(type.getSimpleName().toString());
		return type.cast(component);
	}
	public <T extends Component> T getComponent(Class<T> type, String key) {
		Component component = getComponents().get(key);
		return type.cast(component);
	}

	public <T extends Component> Optional<T> getComponentOption(Class<T> type) {
		Component component = getComponents().get(type.getSimpleName().toString());
		return Optional.ofNullable(type.cast(component));
	}

	private boolean hasComponent(Class<ModelComponent> type) {
		return getComponents().keySet().contains(type);
	}

	public List<Entity> getAllChildrenAndSelf() {
		List<Entity> allChildrenAndSelf = new ArrayList<>();
		allChildrenAndSelf.add(this);
		if(hasChildren()) {
			for (Entity child: children) {
				allChildrenAndSelf.addAll(child.getAllChildrenAndSelf());
			}
		}
		return allChildrenAndSelf;
	}

	public Entity getParent() {
		return parent;
	}

	public enum Update {
		STATIC,
		DYNAMIC
	}

	private Update update = Update.DYNAMIC;

	transient public Matrix4f modelMatrix = new Matrix4f();
	
	private Transform transform = new Transform();
	transient protected FloatBuffer matrix44Buffer;

	protected transient World world;

	protected String name = "Entity_" + System.currentTimeMillis();

	private Entity parent = null;
	private List<Entity> children = new ArrayList<>();

	private boolean selected = false;
	private boolean visible = true;
	
	public HashMap<String, Component> components = new HashMap<>();

	protected Entity() { }

	protected Entity(World world, MaterialFactory materialFactory, Model model, String materialName) {
		this(world, materialFactory, new Vector3f(0, 0, 0), model.getName(), model, materialName);
	}

	protected Entity(World world, MaterialFactory materialFactory, Vector3f position, String name, Model model, String materialName) {
		modelMatrix = new Matrix4f();
		matrix44Buffer = BufferUtils.createFloatBuffer(16);
		matrix44Buffer.rewind();

		addComponent(new ModelComponent(model, materialName));
		this.name = name;
		
		transform.setPosition(position);
	}

	@Override
	public void init(World world) {
		LifeCycle.super.init(world);
		matrix44Buffer = BufferUtils.createFloatBuffer(16);
		matrix44Buffer.rewind();

		for(Component component : components.values()) {
			component.init(world);
		}
		for(Entity child: children) {
			child.init(world);
		}
//		children.parallelStream().forEach(child -> child.init(world));
	}

	public boolean hasParent() {
		return parent != null;
	}

	public boolean hasChildren() {
		return !children.isEmpty();
	}

	public List<Entity> getChildren() {
		return children;
	}

	public void removeParent() {
		parent.getChildren().remove(this);
		parent = null;
	}
	public void setParent(Entity parent) {
		this.parent = parent;
		parent.addChild(this);
		getTransform().setParent(parent.getTransform());
		getTransform().recalculate();
	}

	private void addChild(Entity child) {
		if(!children.contains(child)) {
			children.add(child);
		}
	}

	private void removeChild(Entity entity) {
		children.remove(entity);
	}

	@Override
	public void update(float seconds) {
		for (Component c : components.values()) {
			c.update(seconds);
		}
		modelMatrix = calculateCurrentModelMatrix();
	}

	@Override
	public void setWorld(World world) {
		this.world = world;
	}

	@Override
	public World getWorld() {
		return world;
	}

	public HashMap<String,Component> getComponents() {
		return components;
	};

	protected Matrix4f calculateCurrentModelMatrix() {
		modelMatrix = transform.getTransformation();
		synchronized(this) {
			matrix44Buffer.rewind();
			modelMatrix.store(matrix44Buffer);
			matrix44Buffer.rewind();
		}
		return modelMatrix;
	}

	public Matrix4f getModelMatrix() {
		return calculateCurrentModelMatrix();
	}

	public FloatBuffer getModelMatrixAsBuffer() {
		return matrix44Buffer;
	}

	public void setModelMatrix(Matrix4f modelMatrix) {
		this.modelMatrix = modelMatrix;
	}

	public void setTransform(Transform transform) {
		this.transform = transform;
	}
	public Transform getTransform() {
		return transform;
	}

	public String getName() {
		return name;
	}
	public void setName(String string) {
		this.name = string;
	}

	public boolean isInFrustum(Camera camera) {
		Vector4f[] minMaxWorld = getMinMaxWorld();
		Vector4f minWorld = minMaxWorld[0];
		Vector4f maxWorld = minMaxWorld[1];
		
		Vector3f centerWorld = new Vector3f();
		centerWorld.x = (maxWorld.x + minWorld.x)/2;
		centerWorld.y = (maxWorld.y + minWorld.y)/2;
		centerWorld.z = (maxWorld.z + minWorld.z)/2;
		
		Vector3f distVector = new Vector3f();
		Vector3f.sub(new Vector3f(maxWorld.x, maxWorld.y, maxWorld.z),
						new Vector3f(minWorld.x, minWorld.y, minWorld.z), distVector);

//		if (camera.getFrustum().pointInFrustum(minWorld.x, minWorld.y, minWorld.z) ||
//			camera.getFrustum().pointInFrustum(maxWorld.x, maxWorld.y, maxWorld.z)) {
//		if (camera.getFrustum().cubeInFrustum(cubeCenterX, cubeCenterY, cubeCenterZ, size)) {
//		if (camera.getFrustum().pointInFrustum(minView.x, minView.y, minView.z)
//				|| camera.getFrustum().pointInFrustum(maxView.x, maxView.y, maxView.z)) {
		if (camera.getFrustum().sphereInFrustum(centerWorld.x, centerWorld.y, centerWorld.z, distVector.length()/2)) {
			return true;
		}
		return false;
	}

	public boolean isVisible() {
		return visible;
	}
	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	public Vector4f[] getMinMaxWorld() {
		Vector4f[] minMax;

		if(hasComponent(ModelComponent.class)) {
			ModelComponent modelComponent = getComponent(ModelComponent.class);
			if(modelComponent == null || modelComponent.getVertexBuffer() == null) {
				int x = 5;
			}
			minMax = modelComponent.getVertexBuffer().getMinMax();

			Vector4f minView = new Vector4f(0,0,0,1);
			Vector4f maxView = new Vector4f(0,0,0,1);

			Matrix4f modelMatrix = getModelMatrix();

			Matrix4f.transform(modelMatrix, minMax[0], minView);
			Matrix4f.transform(getModelMatrix(), minMax[1], maxView);

			minView.w = 0;
			maxView.w = 0;
			minMax = new Vector4f[] {minView, maxView};

			return minMax;
		} else {
			minMax = new Vector4f[2];
			Vector4f vector = new Vector4f(getPosition().x, getPosition().y, getPosition().z, 1);
			minMax[0] = vector;
			minMax[1] = vector;
		}

		return minMax;
	}

	public Vector3f getCenter() {
		if(hasComponent(ModelComponent.class)) {
			ModelComponent modelComponent = getComponent(ModelComponent.class);
			Vector4f[] minMax = modelComponent.getVertexBuffer().getMinMax();

			Vector4f center = Vector4f.sub(minMax[1], minMax[0], null);
			center.w = 1;

			Matrix4f modelMatrix = getModelMatrix();

			Matrix4f.transform(modelMatrix, center, center);

			return new Vector3f(center.x, center.y, center.z);
		} else {
			return getPosition();
		}
	}

	public boolean isSelected() {
		return selected;
	}
	public void setSelected(boolean selected) {
		this.selected = selected;
	}
	
	@Override
	public String toString() {
		return name;
	}
	
	public static boolean write(Entity entity, String resourceName) {
		String fileName = FilenameUtils.getBaseName(resourceName);
		FileOutputStream fos = null;
		ObjectOutputStream out = null;

		try {
			fos = new FileOutputStream(getDirectory() + fileName + ".hpentity");
			out = new ObjectOutputStream(fos);
			out.writeObject(entity);

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				out.close();
				fos.close();
				return true;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return false;
	}

	public static String getDirectory() {
		return World.WORKDIR_NAME + "/assets/entities/";
	}
	public boolean equals(Object other) {
		if (!(other instanceof Entity)) {
			return false;
		}
		
		Entity b = (Entity) other;
		
		return b.getName().equals(getName());
	}

	public void destroy() {

//		// Select the VAO
//		GL30.glBindVertexArray(vaoId);
//		
//		// Disable the VBO index from the VAO attributes list
//		GL20.glDisableVertexAttribArray(0);
//		GL20.glDisableVertexAttribArray(1);
//		
//		// Delete the vertex VBO
//		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
//		GL15.glDeleteBuffers(vboId);
//		
//		// Delete the index VBO
//		GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
//		GL15.glDeleteBuffers(vboiId);
//		
//		// Delete the VAO
//		GL30.glBindVertexArray(0);
//		GL30.glDeleteVertexArrays(vaoId);
//		
	}

	public void setHasMoved(boolean value) { transform.setHasMoved(value); }

	public boolean hasMoved() {
		return transform.isHasMoved();
	}

	public Update getUpdate() {
		return update;
	}

	public void setUpdate(Update update) {
		this.update = update;
		if (hasChildren()) {
			for (Entity child : children) {
				child.setUpdate(update);
			}
		}
	}
}
