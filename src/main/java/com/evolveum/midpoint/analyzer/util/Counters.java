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

package com.evolveum.midpoint.analyzer.util;

import java.util.*;

/**
 *
 */
public class Counters<T extends Comparable<T>> {

	private TreeMap<T, Integer> countsMap = new TreeMap<>();

	public void increment(T key) {
		Integer count = countsMap.get(key);
		countsMap.put(key, count != null ? count + 1 : 1);
	}

	public List<T> getNames() {
		return new ArrayList<>(countsMap.keySet());
	}

	public int[] getCounts() {
		int[] rv = new int[countsMap.size()];
		int i = 0;
		for (Integer value : countsMap.values()) {
			rv[i++] = value;
		}
		return rv;
	}

	public TreeMap<T, Integer> getCountsMap() {
		return countsMap;
	}
}
