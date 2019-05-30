/*
 * Copyright (c) 2010-2019 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.collector;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class CollectorImpl implements Collector {

	// tag -> current events
	private Map<String, EventsSummary> openTagsMap = new HashMap<>();

	@Override
	public void registerEvent(String tag, Event event) {
		openTagsMap.computeIfAbsent(tag, key -> new EventsSummary());
		openTagsMap.get(tag).registerEvent(event);
	}

	@Override
	public EventsSummary closeTag(String tag) {
		return openTagsMap.remove(tag);
	}

	@Override
	public void reset() {
		openTagsMap.clear();
	}
}
