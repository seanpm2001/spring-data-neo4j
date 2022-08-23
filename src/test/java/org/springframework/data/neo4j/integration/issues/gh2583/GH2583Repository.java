/*
 * Copyright 2011-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.neo4j.integration.issues.gh2583;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

interface GH2583Repository extends Neo4jRepository<GH2583Node, Long> {

	@Query(value = "MATCH (s:GH2583Node) " +
			"WITH s OPTIONAL MATCH (s)-[r:LINKED]->(t:GH2583Node) " +
			"RETURN s, collect(r), collect(t) " +
			":#{orderBy(#pageable)} SKIP $skip LIMIT $limit",
			countQuery = "MATCH (s:hktxjm) RETURN count(s)")
	Page<GH2583Node> getNodesByCustomQuery(Pageable pageable);
}
