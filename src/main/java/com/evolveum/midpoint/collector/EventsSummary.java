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

import java.util.*;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

/**
 *
 */
public class EventsSummary {

	// key is event type
	private final Map<String, Times> eventsMap = new HashMap<>();

	void registerEvent(Event event) {
		eventsMap.computeIfAbsent(event.getType(), key -> new Times());
		eventsMap.get(event.getType()).registerEvent(event);
	}

	public String dump() {
		StringBuilder sb = new StringBuilder();

		List<String> names = new ArrayList<>(eventsMap.keySet());
		names.sort(String::compareTo);
		for (String name : names) {
			Times times = eventsMap.get(name);
			sb.append(String.format(Locale.US, " - %-80s: %6d in %10.3f ms [min: %9.3f max: %9.3f avg: %9.3f]\n",
					name, times.getCount(), times.getTotalTime() / 1000.0f,
					defaultIfNull(times.getMinTime(), 0L) / 1000.0f,
					defaultIfNull(times.getMaxTime(), 0L) / 1000.0f,
					times.getCount() > 0 ? (float) times.getTotalTime() / 1000.0f / times.getCount() : 0));
		}

		return sb.toString();
	}

	public Times get(String eventType) {
		return eventsMap.get(eventType);
	}
}
