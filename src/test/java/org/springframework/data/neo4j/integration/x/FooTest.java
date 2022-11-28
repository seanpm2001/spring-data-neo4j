package org.springframework.data.neo4j.integration.x;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Query;
import org.neo4j.driver.Session;
import org.neo4j.driver.reactive.ReactiveQueryRunner;
import org.neo4j.driver.reactive.RxQueryRunner;
import org.neo4j.driver.reactivestreams.ReactiveResult;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.neo4j.driver.reactivestreams.ReactiveTransaction;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.config.AbstractNeo4jConfig;
import org.springframework.data.neo4j.config.AbstractReactiveNeo4jConfig;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.ReactiveDatabaseSelectionProvider;
import org.springframework.data.neo4j.core.transaction.Neo4jBookmarkManager;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.data.neo4j.repository.config.EnableReactiveNeo4jRepositories;
import org.springframework.data.neo4j.test.BookmarkCapture;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(SpringExtension.class)
@SpringJUnitConfig(FooTest.Config.class)
public class FooTest {

	@Test
	void f(@Autowired MovieRepository movieRepository, @Autowired Driver driver) throws InterruptedException {
		UUID id = UUID.randomUUID();
		Flux
				.range(1, 5)
				// .doOnCancel(() -> System.out.println("doOnCancel (outer)"))
				.flatMap(
						i -> movieRepository
								.findById(id)
								.switchIfEmpty(Mono.error(new RuntimeException()))
						//.doOnCancel(() -> System.out.println("doOnCancel"))
				)
				//.collectList()
				//.as(TransactionalOperator.create(transactionManager)::transactional)
				.then()
				.as(StepVerifier::create)
				.verifyError();
		System.out.println("---- complete");
		Thread.currentThread().join();
		try (Session session = driver.session()) {
			System.out.println(session.run("RETURN 1").single().get(0));
		}
	}


	@Test
	void f(@Autowired Driver driver) throws InterruptedException {
		UUID id = UUID.randomUUID();
		Flux
				.range(1, 5)
				// .doOnCancel(() -> System.out.println("doOnCancel (outer)"))
				.flatMap(
						i -> {
							var query = new Query("MATCH (p:Product) WHERE p.id = $id RETURN p.title", Collections.singletonMap("id", 0));
							return Flux.usingWhen(
											Mono.fromSupplier(() -> driver.session(ReactiveSession.class)),
											session -> Flux.from(session.run(query))
													.flatMap(result -> Flux.from(result.records()))
													.map(record -> record.get(0).asString()),
											session -> Mono.fromDirect(session.close()))
									.switchIfEmpty(Mono.error(new RuntimeException()));
						}
						//.doOnCancel(() -> System.out.println("doOnCancel"))
				)
				//.collectList()
				//.as(TransactionalOperator.create(transactionManager)::transactional)
				.then()
				.as(StepVerifier::create)
				.verifyError();
		System.out.println("---- complete");
		// Thread.currentThread().join();
		try (Session session = driver.session()) {
			System.out.println(session.run("RETURN 1").single().get(0));
		}
	}

	record SessionAndTx(ReactiveSession session, ReactiveTransaction tx) {
	}

	@Test
	void f2(@Autowired Driver driver) {
		Flux
				.range(1, 5)
				.flatMap(
						i -> {
							Mono<SessionAndTx> f = Mono
									.just(driver.session(ReactiveSession.class))
									.flatMap(s -> Mono.fromDirect(s.beginTransaction()).map(tx -> new SessionAndTx(s, tx)));
							return Flux.usingWhen(f,
									h -> Flux.from(h.tx.run("MATCH (n) WHERE false = true RETURN n")).flatMap(ReactiveResult::records),
									h -> Mono.from(h.tx.commit()).then(Mono.from(h.session.close())),
									(h, e) -> Mono.from(h.tx.rollback()).then(Mono.from(h.session.close())),
									h -> Mono.from(h.tx.rollback()).then(Mono.from(h.session.close()))
							).switchIfEmpty(Mono.error(new RuntimeException()));
						}
				)
				.then()
				.as(StepVerifier::create)
				.verifyError();
		System.out.println("---- complete");
		try (Session session = driver.session()) {
			System.out.println(session.run("RETURN 1").single().get(0));
		}
	}

	@Configuration
	@EnableTransactionManagement
	@EnableReactiveNeo4jRepositories(considerNestedRepositories = true)
	static class Config extends AbstractReactiveNeo4jConfig {

		@Bean
		public Driver driver() {
			org.neo4j.driver.Config config = org.neo4j.driver.Config.builder()
					.withMaxConnectionPoolSize(2)
					.withConnectionAcquisitionTimeout(20, TimeUnit.SECONDS)
					.withLeakedSessionsLogging()
					.build();
			return
					GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "secret"), config);
		}
	}

}
