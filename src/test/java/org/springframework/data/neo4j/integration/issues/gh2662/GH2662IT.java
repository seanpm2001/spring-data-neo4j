/*
 * Copyright 2011-2023 the original author or authors.
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
package org.springframework.data.neo4j.integration.issues.gh2662;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.transaction.Neo4jBookmarkManager;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.neo4j.test.BookmarkCapture;
import org.springframework.data.neo4j.test.Neo4jExtension;
import org.springframework.data.neo4j.test.Neo4jImperativeTestConfiguration;
import org.springframework.data.neo4j.test.Neo4jIntegrationTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Gerrit Meier
 */
@Neo4jIntegrationTest
class GH2662IT {

	protected static Neo4jExtension.Neo4jConnectionSupport neo4jConnectionSupport;

	@BeforeEach
	void setupData(@Autowired Driver driver, @Autowired BookmarkCapture bookmarkCapture) {

		try (Session session = driver.session(bookmarkCapture.createSessionConfig())) {
			session.run("MATCH (n) DETACH DELETE n").consume();
			bookmarkCapture.seedWith(session.lastBookmark());
		}
	}

//	@RepeatedTest(100)
	@Test
	void hydrateTriangleCompletely(@Autowired GH2662Repository repository) {
		StartNode startNode = new StartNode();
		EndNode endNode = new EndNode();
		List<IntermediateNode> intermediateNodes = new ArrayList<>();

		IntStream.range(0, 10).forEach(i -> {
			IntermediateNode intermediateNode = new IntermediateNode();
			intermediateNode.endNode = endNode;
			intermediateNodes.add(intermediateNode);
		});

		startNode.endNode = endNode;
		startNode.aintermediateNodes = intermediateNodes;
		endNode.startNodes = Collections.singletonList(startNode);

		repository.save(startNode);

		StartNode loadedStartNode = repository.queryStartNode();

		assertThat(loadedStartNode.endNode).isNotNull();
		assertThat(loadedStartNode.endNode.startNodes).hasSize(1);
		assertThat(loadedStartNode.aintermediateNodes).hasSize(10);
	}

	interface GH2662Repository extends Neo4jRepository<StartNode, Long> {

		@Query("MATCH (workflow:GH2662EndNode)-[can_select:TO_START]->(field:GH2662StartNode) \n" +
				"OPTIONAL MATCH (field)-[has_input:TO_INTERMEDIATE]->(crossflow:GH2662IntermediateNode)\n" +
				"OPTIONAL MATCH (crossflow)-[owned_by:TO_END]->(crossflow_workflow:GH2662EndNode) \n" +
				"RETURN field,\n" +
				" collect(distinct can_select), collect(distinct workflow), \n" +
				" collect(has_input), collect(crossflow),\n" +
				" collect(owned_by), collect(crossflow_workflow)")
		StartNode queryStartNode();
	}

	@Configuration
	@EnableTransactionManagement
	@EnableNeo4jRepositories(considerNestedRepositories = true)
	static class Config extends Neo4jImperativeTestConfiguration {

		@Bean
		public BookmarkCapture bookmarkCapture() {
			return new BookmarkCapture();
		}

		@Override
		public PlatformTransactionManager transactionManager(Driver driver, DatabaseSelectionProvider databaseNameProvider) {

			BookmarkCapture bookmarkCapture = bookmarkCapture();
			return new Neo4jTransactionManager(driver, databaseNameProvider,
					Neo4jBookmarkManager.create(bookmarkCapture));
		}

		@Bean
		public Driver driver() {
			return neo4jConnectionSupport.getDriver();
		}

		@Override
		public boolean isCypher5Compatible() {
			return neo4jConnectionSupport.isCypher5SyntaxCompatible();
		}
	}
}
