package main.octree;

import static main.log.ConsoleLogger.getLogger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import main.IEntity;

import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class Octree {
	private static Logger LOGGER = getLogger();

	public static final float defaultSize = 1000;
	public int maxDeepness;

	private int currentDeepness = 0;
	
	public int getCurrentDeepness() {
		return rootNode.getMaxDeepness();
	}

	public Node rootNode;
	
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
		Vector4f[] minMaxWorld = entity.getMinMaxWorld();
		rootNode.insert(entity);
	}

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

		public Node(Octree octree, Vector3f center, float size) {
			this(octree, center, size, 0);
		}
		
		// index is clockwise 0-3 for top: left front, left back, right back, right front
		// and 4-7 bottom: right back, right front, left front, left back 
		private Node(Node parent, int index) {
			this(parent.octree, getCenterForNewChild(parent, index), parent.size/2, parent.deepness + 1);
		}
		
		public void insert(IEntity entity) {
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
					collapseBecauseOf(entity);
				}
			} else if (deepness < octree.maxDeepness) {
//				LOGGER.log(Level.INFO, String.format("No children for node, expanding ... "));
				this.expand();
				this.insert(entity);
			} else if (deepness >= octree.maxDeepness) {
//				LOGGER.log(Level.INFO, String.format("Max deepness reached, entity added to deepest node ... "));
				this.entities.add(entity);
			}
		}

		// Node has to insert an entity which its children can't handle, so
		// the node has to delete all children and take their entities plus
		// the cause of the collapse
		private void collapseBecauseOf(IEntity entity) {
			entities.addAll(collectAllEntitiesFromChildrenAndRemoveNode());
			this.entities.add(entity);
			hasChildren = false;
		}

		private Collection<IEntity> collectAllEntitiesFromChildrenAndRemoveNode() {
			
			List<IEntity> result = new ArrayList<>();
			for (int i = 0; i < 8; i++) {
				Node node = children[i];

				if (!node.hasChildren()) {
					result.addAll(node.entities);
					node.entities.clear();
				} else {
					result.addAll(node.collectAllEntitiesFromChildrenAndRemoveNode());
				}
			}
			return result;
		}

		public Node searchNodeForInsertion(Node[] source, IEntity entity) {
			Vector4f[] minMaxWorld = entity.getMinMaxWorld();
			for (int i = 0; i < source.length; i++) {
				Node node = source[i];
				if (node.canContain(minMaxWorld)) {
					return node;
				}
			}
			return null;
		}

		private boolean canContain(Vector4f[] minMaxWorld) {
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
		public void expand() {
			for (int i = 0; i < 8; i++) {
				children[i] = new Node(this, i);
			}
			hasChildren = true;
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
		
		@Override
		public String toString() {
			return String.format("Node(%.2f) @ (%.2f, %.2f, %.2f)", size, center.x, center.y, center.z);
		}

		public Object getCenter() {
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
	}
	
}
