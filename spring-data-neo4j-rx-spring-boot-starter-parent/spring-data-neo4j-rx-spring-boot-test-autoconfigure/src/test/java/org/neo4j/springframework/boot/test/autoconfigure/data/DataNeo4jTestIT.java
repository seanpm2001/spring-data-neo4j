/*
 * Copyright (c) 2019-2020 "Neo4j,"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.springframework.boot.test.autoconfigure.data;

import static org.assertj.core.api.Assertions.*;

import java.net.URI;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AccessMode;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.springframework.boot.test.autoconfigure.Neo4jTestHarnessAutoConfiguration;
import org.neo4j.driver.summary.ResultSummary;
import org.neo4j.harness.ServerControls;
import org.neo4j.harness.TestServerBuilders;
import org.neo4j.springframework.data.core.Neo4jClient;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ContextConfiguration;

/**
 * Integration tests for the SDN/RX Neo4j test slice.
 *
 * @author Michael J. Simons
 * @since 1.0
 */
class DataNeo4jTestIT {

	@Nested
	@DisplayName("Usage with explicit test harness")
	@DataNeo4jTest
	@ContextConfiguration(classes = ExplicitTestHarness3xConfiguration.class)
	class DriverBasedOnTestHarness extends TestBase {

		@Test
		void driverShouldBeConnectedToNeo4jTestHarness(@Autowired ServerControls serverControls,
			@Autowired Neo4jClient client) {

			ResultSummary summary = client.query("RETURN 1 AS result").run();
			URI uri = serverControls.boltURI();
			assertThat(summary.server().address()).endsWith(String.format("%s:%d", uri.getHost(), uri.getPort()));
		}
	}

	@TestConfiguration
	static class ExplicitTestHarness3xConfiguration {

		@Bean
		ServerControls serverControls() {
			return TestServerBuilders.newInProcessBuilder().newServer();
		}
	}

	@Nested
	@DisplayName("Properties should be applied.")
	@DataNeo4jTest(properties = "spring.profiles.active=test")
	class DataNeo4jTestPropertiesIT {

		@Autowired
		private Environment environment;

		@Test
		void environmentWithNewProfile() {
			assertThat(this.environment.getActiveProfiles()).containsExactly("test");
		}
	}

	@Nested
	@DisplayName("Include filter should work")
	@DataNeo4jTest(includeFilters = @ComponentScan.Filter(Service.class))
	class DataNeo4jTestWithIncludeFilterIntegrationTests {

		@Autowired
		private ExampleService service;

		@Test
		void testService() {
			assertThat(this.service.hasNode(ExampleEntity.class)).isFalse();
		}
	}

	@Nested
	@DisplayName("Usage with driver auto configuration")
	@ContextConfiguration(initializers = TestContainerInitializer.class)
	@DataNeo4jTest(excludeAutoConfiguration = Neo4jTestHarnessAutoConfiguration.class)
	class DriverBasedOnAutoConfiguration extends TestBase {
	}

	/**
	 * Tests for both scenarios.
	 */
	abstract class TestBase {
		@Autowired
		private Driver driver;

		@Autowired
		private ExampleRepository exampleRepository;

		@Autowired
		private ApplicationContext applicationContext;

		@Test
		void testRepository() {
			ExampleEntity entity = new ExampleEntity("Look, new @DataNeo4jTest!");
			assertThat(entity.getId()).isNull();
			ExampleEntity persistedEntity = this.exampleRepository.save(entity);
			assertThat(persistedEntity.getId()).isNotNull();

			try (Session session = driver
				.session(SessionConfig.builder().withDefaultAccessMode(AccessMode.READ).build())) {
				long cnt = session.run("MATCH (n:ExampleEntity) RETURN count(n) as cnt").single().get("cnt").asLong();
				assertThat(cnt).isEqualTo(1L);
			}
		}

		@Test
		void didNotInjectExampleService() {
			assertThatExceptionOfType(NoSuchBeanDefinitionException.class)
				.isThrownBy(() -> this.applicationContext.getBean(ExampleService.class));
		}
	}
}
