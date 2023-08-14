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
package org.springframework.data.neo4j.core.transaction;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.neo4j.driver.Bookmark;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;

/**
 * Default bookmark manager.
 *
 * @author Michael J. Simons
 * @author Dmitriy Tverdiakov
 * @author Gerrit Meier
 * @since 7.1.2
 */
final class ReactiveDefaultBookmarkManager extends AbstractBookmarkManager {

	private final Set<Bookmark> bookmarks = Collections.synchronizedSet(new HashSet<>());

	private final Supplier<Set<Bookmark>> bookmarksSupplier;

	@Nullable
	private ApplicationEventPublisher applicationEventPublisher;

	ReactiveDefaultBookmarkManager(@Nullable Supplier<Set<Bookmark>> bookmarksSupplier) {
		this.bookmarksSupplier = bookmarksSupplier == null ? Collections::emptySet : bookmarksSupplier;
	}

	@Override
	public Collection<Bookmark> getBookmarks() {
		this.bookmarks.addAll(bookmarksSupplier.get());
		return Set.copyOf(this.bookmarks);
	}

	@Override
	public void updateBookmarks(Collection<Bookmark> usedBookmarks, Collection<Bookmark> newBookmarks) {
		bookmarks.removeAll(usedBookmarks);
		newBookmarks.stream().filter(Objects::nonNull).forEach(bookmarks::add);
		if (applicationEventPublisher != null) {
			applicationEventPublisher.publishEvent(new Neo4jBookmarksUpdatedEvent(new HashSet<>(bookmarks)));
		}
	}

	@Override
	public void setApplicationEventPublisher(@Nullable ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}
}