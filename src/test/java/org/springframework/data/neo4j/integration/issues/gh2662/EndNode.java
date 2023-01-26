package org.springframework.data.neo4j.integration.issues.gh2662;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.List;

/**
 * @author Gerrit Meier
 */
@Node("GH2662EndNode")
public class EndNode {
	@Id
	@GeneratedValue
	public Long id;

	@Relationship("TO_START")
	public List<StartNode> startNodes;
}
