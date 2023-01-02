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
package org.springframework.data.neo4j.integration.issues.gh2626;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Sort;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.transaction.Neo4jBookmarkManager;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.data.neo4j.integration.issues.gh2583.GH2583Node;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.data.neo4j.test.BookmarkCapture;
import org.springframework.data.neo4j.test.Neo4jExtension;
import org.springframework.data.neo4j.test.Neo4jImperativeTestConfiguration;
import org.springframework.data.neo4j.test.Neo4jIntegrationTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Michael J. Simons
 */
@Neo4jIntegrationTest
public class GH2626IT {

	protected static Neo4jExtension.Neo4jConnectionSupport neo4jConnectionSupport;

	@BeforeEach
	void setupData(@Autowired Driver driver, @Autowired BookmarkCapture bookmarkCapture) {

		try (Session session = driver.session(bookmarkCapture.createSessionConfig())) {
			session.run("MATCH (n) DETACH DELETE n").consume();
			session.run("CREATE (t1:Team {id: randomUUID(), name: \"T1\"})\n" +
					"CREATE (t2:Team {id: randomUUID(), name: \"T2\"})\n" +
					"CREATE (p1:Player {id: randomUUID(), name: \"P1\"})\n" +
					"CREATE (p2:Player {id: randomUUID(), name: \"P2\"})\n" +
					"CREATE (p1) -[:TEAM_MEMBERSHIPS {start: date(), end: date() + duration({days: 23})}]->(t1)\n" +
					"CREATE (p2) -[:TEAM_MEMBERSHIPS {start: date(), end: date() + duration({days: 23})}]->(t1)\n");
			bookmarkCapture.seedWith(session.lastBookmark());
		}
	}

	@Test
	void cyclicRelationshipPropertiesIncoming(@Autowired TeamRepository teamRepository) {
		List<Team> teams = teamRepository.findAll(Sort.by("name"));
		assertThat(teams)
				.hasSize(2)
				.first()
				.satisfies(team ->
						assertThat(team.getMembers())
								.hasSize(2)
								.allSatisfy(playerMembership -> {
									assertThat(playerMembership.getStart()).isNotNull();
									assertThat(playerMembership.getEnd()).isNotNull();
									assertThat(playerMembership.getPlayer().getTeamMemberships())
											.hasSize(1)
											.first()
											.satisfies(tm -> assertThat(tm.getTeam()).isSameAs(team));
								})
				);
	}

	@Test
	void cyclicRelationshipPropertiesOutgoing(@Autowired PlayerRepository teamRepository) {
		List<Player> players = teamRepository.findAll(Sort.by("name"));
		assertThat(players)
				.hasSize(2)
				.first()
				.satisfies(player ->
						assertThat(player.getTeamMemberships())
								.hasSize(1)
								.first()
								.satisfies(teamMembership -> {
									assertThat(teamMembership.getStart()).isNotNull();
									assertThat(teamMembership.getEnd()).isNotNull();
									assertThat(teamMembership.getTeam().getMembers())
											.map(PlayerMembership::getPlayer)
											.containsAll(players);
								})
				);
	}

	@Test
	@Transactional
	@Rollback(false)
	void writingShouldWork(@Autowired TeamRepository teamRepository) {
		Team team = teamRepository.findOne(Example.of(new Team("T2"))).get();
		Player p3 = new Player("P3");
		TeamMembership teamMembership = new TeamMembership(team);
		teamMembership.setStart(LocalDate.now());
		teamMembership.setEnd(LocalDate.now().plusDays(42));
		PlayerMembership playerMembership = new PlayerMembership(p3);
		playerMembership.setStart(teamMembership.getStart());
		playerMembership.setEnd(teamMembership.getEnd());
		team.getMembers().add(playerMembership);
		p3.getTeamMemberships().add(teamMembership);

		// Make sure safe doesn't fall over with SO
		team = teamRepository.save(team);
		assertThat(team.getMembers())
				.hasSize(1)
				.first()
				.extracting(PlayerMembership::getPlayer)
				.satisfies(member -> {
					assertThat(member.getTeamMemberships()).hasSize(1)
							.first()
							.satisfies(tm -> {
								assertThat(tm.getStart()).isNotNull();
								assertThat(tm.getEnd()).isNotNull();
							});
					assertThat(member.getId()).isNotNull();
					assertThat(member).isSameAs(p3);
				});

		// Verify generated values are properly filled, might be worthwhile rethinking nested save
		team = teamRepository.findById(team.getId()).get();
		assertThat(team.getMembers())
				.hasSize(1)
				.first()
				.satisfies(pm -> assertThat(pm.getId()).isNotNull())
				.extracting(PlayerMembership::getPlayer)
				.satisfies(member -> {
					assertThat(member.getTeamMemberships()).hasSize(1)
							.first()
							.satisfies(tm -> {
								assertThat(tm.getId()).isNotNull();
								assertThat(tm.getStart()).isNotNull();
								assertThat(tm.getEnd()).isNotNull();
							});
					assertThat(member.getId()).isNotNull();
				});
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
		public PlatformTransactionManager transactionManager(
				Driver driver, DatabaseSelectionProvider databaseNameProvider) {

			BookmarkCapture bookmarkCapture = bookmarkCapture();
			return new Neo4jTransactionManager(driver, databaseNameProvider,
					Neo4jBookmarkManager.create(bookmarkCapture));
		}

		@Override
		protected Collection<String> getMappingBasePackages() {
			return Collections.singleton(GH2583Node.class.getPackage().getName());
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
