package main.octree;

import static main.log.ConsoleLogger.getLogger;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.logging.Logger;

import main.Camera;
import main.DataChannels;
import main.Frustum;
import main.IEntity;
import main.Renderer;
import main.VertexBuffer;
import main.World;
import main.shader.Program;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class Octree {

	private static Logger LOGGER = getLogger();

	private static FloatBuffer matrix44Buffer = null;
	private static Matrix4f modelMatrix = new Matrix4f();
	
	public static final float defaultSize = 1000;
	public int maxDeepness;

	private int currentDeepness = 0;
	
	public int getCurrentDeepness() {
		return rootNode.getMaxDeepness();
	}

	public Node rootNode;

	private List<IEntity> entities = new ArrayList<>();
	
	public Octree() {
		this(new Vector3f());
	}

	public Octree(Vector3f center) {
		this(center, defaultSize);
	}
	public Octree(Vector3f center, float size) {
		this(center, size, 20);
	}
	public Octree(Vector3f center, int maxDeepness) {
		this(center, defaultSize, maxDeepness);
	}
	public Octree(Vector3f center, float size, int maxDeepness) {
		this.maxDeepness = maxDeepness;
		this.rootNode = new Node(this, center, size);
	}
	
	public void insert(IEntity entity) {
		List<IEntity> toDispatch = new ArrayList<>();
		toDispatch.add(entity);
		
		while (!toDispatch.isEmpty()) {
			List<IEntity> returns = rootNode.insert(toDispatch.get(0));
			toDispatch.remove(0);
			toDispatch.addAll(returns);
		}
		
		entities.add(entity);
	}
	
	public void insert(List<IEntity> entities){
		for (IEntity iEntity : entities) {
			insert(iEntity);
		}
		entities.addAll(entities);
	}
	
	public List<IEntity> getVisible(Camera camera) {
		List<IEntity> result = new ArrayList<>();
		
		result.addAll(rootNode.getVisible(camera));
		result.addAll(rootNode.entities);
		return result;
	}
	
	public void drawDebug(Renderer renderer, Program program) {
		if (matrix44Buffer == null) {
			 matrix44Buffer = BufferUtils.createFloatBuffer(16);
			 matrix44Buffer.rewind();
			 modelMatrix.store(matrix44Buffer);
			 matrix44Buffer.rewind();
		}
		program.setUniformAsMatrix4("modelMatrix", matrix44Buffer);
		
		// List of 24*3 = 72 floats per floatarray
		List<float[]> arrays = rootNode.getPointsForLineDrawing();
		float[] points = new float[arrays.size() * 72];
		for (int i = 0; i < arrays.size(); i++) {
			float[] array = arrays.get(i);
			for (int z = 0; z < 72; z++) {
				points[24*3*i + z] = array[z];
			}
			
		};
		VertexBuffer buffer = new VertexBuffer(points, EnumSet.of(DataChannels.POSITION3)).upload();
		buffer.drawDebug();
	}

	/**
	 * children: index is clockwise 0-3 for top: left front, left back, right back, right front and 4-7 bottom: right back, right front, left front, left back 
	 * 
	 *
	 */
	public static class Node {
		Octree octree;
		Node parent;
		public Node[] children = new Node[8];
		public List<IEntity> entities = new ArrayList();
		Vector3f center;
		float size;
		Box aabb;
		private int deepness;
		private boolean hasChildren = false;

		public Node(Octree octree, Vector3f center, float size, int deepness) {
			this.octree = octree;
			this.center = center;
			this.size = size;
			this.deepness = deepness;
			if (octree.currentDeepness < deepness) {
				octree.currentDeepness = deepness;
			}
			this.aabb = new Box(center, size);

//			LOGGER.log(Level.INFO, "Created " + this.toString() + " with " + this.aabb.toString());
		}


		public List<IEntity> getVisible(Camera camera) {
			List<IEntity> result = new ArrayList<IEntity>();
			if (deepness == 0 ) {
				if (hasChildren) {
					for(int i = 0; i < 8; i++) {
						result.addAll(children[i].getVisible(camera));
					}	
				}
			} else if (aabb.isInFrustum(camera)) {
				result.addAll(getAllEntitiesInAndBelow());
			}
			
			return result;
		}
		
		public boolean isVisible(Camera camera) {
			return aabb.isInFrustum(camera);
		}
		
		public List<IEntity> getAllEntitiesInAndBelow() {
			List<IEntity> result = new ArrayList<IEntity>();
			if (hasChildren) {
				for(int i = 0; i < 8; i++) {
					result.addAll(children[i].getAllEntitiesInAndBelow());
				}	
			} else {
				result.addAll(entities);
			}
			return result;
		}

		public List<float[]> getPointsForLineDrawing() {
			List<float[]> arrays = new ArrayList<float[]>();
			arrays.add(getPoints());
			if (hasChildren()) {
				for (int i = 0; i < 8; i++) {
					arrays.addAll(children[i].getPointsForLineDrawing());
				}
			}
			return arrays;
		}

		public Node(Octree octree, Vector3f center, float size) {
			this(octree, center, size, 0);
		}
		
		// index is clockwise 0-3 for top: left front, left back, right back, right front
		// and 4-7 bottom: right back, right front, left front, left back 
		private Node(Node parent, int index) {
			this(parent.octree, getCenterForNewChild(parent, index), parent.size/2, parent.deepness + 1);
		}
		
		public List<IEntity> insert(IEntity entity) {
			List<IEntity> toDispatch = new ArrayList<>();
			
//			LOGGER.log(Level.INFO, String.format("Inserting %s ...", entity));
			if(hasChildren()) {
				Node hit = searchNodeForInsertion(children, entity);
				// If insertion point found, child can handle entity
				// else node has to handle it by himself and delete all his
				// child nodes and take their entities...
				if (hit != null) {
//					LOGGER.log(Level.INFO, String.format("Found hit in %s, inserting ...", hit));
					hit.insert(entity);
				} else {
//					LOGGER.log(Level.INFO, String.format("No hit found, collapsing ... "));
					collapseBecauseOfAndAdd(entity);
				}
			} else if (deepness < octree.maxDeepness) {
//				LOGGER.log(Level.INFO, String.format("No children for node, expanding ... "));
				toDispatch.addAll(this.expand());
				this.insert(entity);
			} else if (deepness >= octree.maxDeepness) {
//				LOGGER.log(Level.INFO, String.format("Max deepness reached, entity added to deepest node ... "));
				this.entities.add(entity);
				setHasChildren(false);
			}
			checkValid();
			return toDispatch;
		}
		
		public void insert(List<IEntity> toInsert){
			for (IEntity iEntity : toInsert) {
				insert(iEntity);
			}
		}

		// Node has to insert an entity which its children can't handle, so
		// the node has to delete all children and take their entities plus
		// the cause of the collapse
		private void collapseBecauseOfAndAdd(IEntity entity) {
			if (deepness == 0) {
//				LOGGER.log(Level.INFO, String.format("Small deepness, should not collapse ... "));
				entities.add(entity);
				return;
			}
			entities.addAll(collectAllEntitiesFromChildren());
			entities.add(entity);
			setHasChildren(false);
			checkValid();
		}

		private Collection<IEntity> collectAllEntitiesFromChildren() {
			
			List<IEntity> result = new ArrayList<>();
			for (int i = 0; i < 8; i++) {
				Node node = children[i];

				if (!node.hasChildren()) {
					result.addAll(node.entities);
					node.entities.clear();
				} else {
					result.addAll(node.collectAllEntitiesFromChildren());
				}
			}
			checkValid();
			return result;
		}

		public Node searchNodeForInsertion(Node[] source, IEntity entity) {
			Vector4f[] minMaxWorld = entity.getMinMaxWorld();
			for (int i = 0; i < source.length; i++) {
				Node node = source[i];
				if (node.contains(minMaxWorld)) {
					return node;
				}
			}
			checkValid();
			return null;
		}

		private boolean contains(Vector4f[] minMaxWorld) {
			Vector4f min = minMaxWorld[0];
			Vector4f max = minMaxWorld[1];
			
			if (aabb.contains(min) && aabb.contains(max)) {

//				LOGGER.log(Level.INFO, String.format("(%.2f, %.2f, %.2f) is in %s", min.x, min.y, min.z, aabb));
//				LOGGER.log(Level.INFO, String.format("(%.2f, %.2f, %.2f) is in %s", max.x, max.y, max.z, aabb));
				return true;
			}

//			LOGGER.log(Level.INFO, String.format("(%.2f, %.2f, %.2f) not in %s", min.x, min.y, min.z, aabb));
//			LOGGER.log(Level.INFO, String.format("(%.2f, %.2f, %.2f) not in %s", max.x, max.y, max.z, aabb));
			return false;
		}

		// Creates 8 new child nodes
		public List<IEntity> expand() {
			for (int i = 0; i < 8; i++) {
				children[i] = new Node(this, i);
			}
			hasChildren = true;

			List<IEntity> toDispatchList = new ArrayList<IEntity>();
			toDispatchList.addAll(entities);
			entities.clear();
			
			checkValid();
			return toDispatchList;
		}
		
		
		private static Vector3f getCenterForNewChild(Node parent, int index) {
			Vector3f newNodeCenter = new Vector3f(parent.center);
			float offset = parent.size/4;
			switch (index) {
			case 0:
				newNodeCenter.x -= offset;
				newNodeCenter.y += offset;
				newNodeCenter.z += offset;
				break;
			case 1:
				newNodeCenter.x -= offset;
				newNodeCenter.y += offset;
				newNodeCenter.z -= offset;
				break;
			case 2:
				newNodeCenter.x += offset;
				newNodeCenter.y += offset;
				newNodeCenter.z -= offset;
				break;
			case 3:
				newNodeCenter.x += offset;
				newNodeCenter.y += offset;
				newNodeCenter.z += offset;
				break;
			case 4:
				newNodeCenter.x += offset;
				newNodeCenter.y -= offset;
				newNodeCenter.z -= offset;
				break;
			case 5:
				newNodeCenter.x += offset;
				newNodeCenter.y -= offset;
				newNodeCenter.z += offset;
				break;
			case 6:
				newNodeCenter.x -= offset;
				newNodeCenter.y -= offset;
				newNodeCenter.z += offset;
				break;
			case 7:
				newNodeCenter.x -= offset;
				newNodeCenter.y -= offset;
				newNodeCenter.z -= offset;
				break;

			default:
				break;
			}
			return newNodeCenter;
		}

		public boolean hasChildren() {
			return hasChildren;
		}
		
		public void setHasChildren(boolean hasChildren) {
			this.hasChildren = hasChildren;
			if (!hasChildren) {
				for (int i = 0; i < 8; i++) {
					children[i] = null;
				}
			}
		}

		@Override
		public String toString() {
			return String.format("Node(%.2f) @ (%.2f, %.2f, %.2f)", size, center.x, center.y, center.z);
		}

		public Vector3f getCenter() {
			return new Vector3f(center);
		}

		public float getSize() {
			return size;
		}
		public int getMaxDeepness() {
			if (hasChildren()) {
				int childrenMaxDeepness = deepness;
				for (int i = 0; i < children.length; i++) {
					int d = children[i].getMaxDeepness();
					childrenMaxDeepness = d > childrenMaxDeepness? d : childrenMaxDeepness;
				}
				return childrenMaxDeepness;
			} else {
				return deepness;
			}
		}

		public void drawDebug(Renderer renderer, Program program) {
			VertexBuffer buffer = new VertexBuffer(getPoints(), EnumSet.of(DataChannels.POSITION3)).upload();
			buffer.drawDebug();
			if (hasChildren()) {
				for (int i = 0; i < 8; i++) {
					children[i].drawDebug(renderer, program);
				}
			}
		}

		private float[] getPoints() {
			List<Vector3f> points = aabb.getPoints();
			List<Vector3f> pointsForLineDrawing = new ArrayList<>();
			pointsForLineDrawing.add(points.get(0));
			pointsForLineDrawing.add(points.get(1));
			pointsForLineDrawing.add(points.get(1));
			pointsForLineDrawing.add(points.get(2));
			pointsForLineDrawing.add(points.get(2));
			pointsForLineDrawing.add(points.get(3));
			pointsForLineDrawing.add(points.get(3));
			pointsForLineDrawing.add(points.get(0));
			
			pointsForLineDrawing.add(points.get(4));
			pointsForLineDrawing.add(points.get(5));
			pointsForLineDrawing.add(points.get(5));
			pointsForLineDrawing.add(points.get(6));
			pointsForLineDrawing.add(points.get(6));
			pointsForLineDrawing.add(points.get(7));
			pointsForLineDrawing.add(points.get(7));
			pointsForLineDrawing.add(points.get(4));

			pointsForLineDrawing.add(points.get(0));
			pointsForLineDrawing.add(points.get(6));
			pointsForLineDrawing.add(points.get(1));
			pointsForLineDrawing.add(points.get(7));
			pointsForLineDrawing.add(points.get(2));
			pointsForLineDrawing.add(points.get(4));
			pointsForLineDrawing.add(points.get(3));
			pointsForLineDrawing.add(points.get(5));
			
			float[] dest = new float[3* pointsForLineDrawing.size()];
			for (int i = 0; i < pointsForLineDrawing.size(); i++) {
				dest[3*i] = pointsForLineDrawing.get(i).x;
				dest[3*i+1] = pointsForLineDrawing.get(i).y;
				dest[3*i+2] = pointsForLineDrawing.get(i).z;
			}
			return dest;
		}


		public int getDeepness() {
			return deepness;
		}
		
		private boolean checkValid() {
			boolean valid = false;
			if ((hasChildren() && entities.isEmpty()) || getDeepness() == 0 || !hasChildren()) {
				valid = true;
			} else {
				valid = false;
			}
			return valid;
		}
	}

	public List<IEntity> getEntities() {
		return entities;
	}

}
