package org.springframework.data.neo4j.integration.issues.gh2662;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.List;

/**
 * @author Gerrit Meier
 */
@Node("GH2662StartNode")
public class StartNode {
	@Id
	@GeneratedValue
	public Long id;

	String name;

	Integer age;

	@Relationship("TO_INTERMEDIATE")
	public List<IntermediateNode> aintermediateNodes;

	@Relationship(type = "TO_START", direction = Relationship.Direction.INCOMING)
	public EndNode endNode;

}
