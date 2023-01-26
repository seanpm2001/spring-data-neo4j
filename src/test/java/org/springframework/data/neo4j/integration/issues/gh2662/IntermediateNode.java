package org.springframework.data.neo4j.integration.issues.gh2662;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

/**
 * @author Gerrit Meier
 */
@Node("GH2662IntermediateNode")
public class IntermediateNode {

	@Id
	@GeneratedValue
	public Long id;

	@Relationship("TO_END")
	public EndNode endNode;
}
