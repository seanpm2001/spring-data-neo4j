package org.springframework.data.neo4j.integration.x;

import java.util.UUID;

import org.springframework.data.neo4j.repository.ReactiveNeo4jRepository;

public interface MovieRepository extends ReactiveNeo4jRepository<Movie, UUID> {
}
