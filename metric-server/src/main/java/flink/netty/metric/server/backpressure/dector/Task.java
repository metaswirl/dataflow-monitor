package flink.netty.metric.server.backpressure.dector;

import java.util.Objects;

public abstract class Task {
	private Node node;
	private String id;

	public Task(Node node, String id) {
		super();
		this.node = node;
		this.id = id;
	}

	@Override
	public boolean equals(Object o) {
		// self check
		if (this == o)
			return true;
		// null check
		if (o == null)
			return false;
		// type check and cast
		if (getClass() != o.getClass())
			return false;
		Task task = (Task) o;
		// field comparison
		return Objects.equals(node.getId(), task.node.getId()) && Objects.equals(id, task.id);
	}

	public Node getNode() {
		return node;
	}

	public void setNode(Node node) {
		this.node = node;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

}
