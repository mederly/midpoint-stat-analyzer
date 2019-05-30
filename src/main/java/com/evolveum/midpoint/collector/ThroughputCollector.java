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

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class ThroughputCollector {

	private final List<Integer> countsPerMinute = new ArrayList<>();

	public void registerProgress(long time) {
		int minute = (int) (time / 60000);
		while (countsPerMinute.size() < minute+1) {
			countsPerMinute.add(0);
		}
		countsPerMinute.set(minute, countsPerMinute.get(minute) + 1);
	}

	public int[] getCountsPerMinute() {
		int[] rv = new int[countsPerMinute.size()];
		for (int i = 0; i < countsPerMinute.size(); i++) {
			rv[i] = countsPerMinute.get(i);
		}
		return rv;
	}
}
