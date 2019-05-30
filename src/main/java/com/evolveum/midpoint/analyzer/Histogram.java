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

package com.evolveum.midpoint.analyzer;

import java.util.*;

/**
 *
 */
public class Histogram {

	private final long bucketSize;
	private final long upperBoundary;
	private long absoluteMaximum;

	public class Variable {
		private String name;
		private final List<Integer> counts = new ArrayList<>();

		Variable(String name) {
			this.name = name;
		}

		void add(long value) {
			int bucket = (int) (Math.min(value, upperBoundary) / bucketSize);
			while (counts.size() <= bucket) {
				counts.add(0);
			}
			counts.set(bucket, counts.get(bucket) + 1);
		}

		public String getName() {
			return name;
		}

		public int getBucket(int number) {
			return number < counts.size() ? counts.get(number) : 0;
		}
	}

	private final Map<String, Variable> variables = new TreeMap<>();

	public Histogram(long bucketSize, long upperBoundary) {
		this.bucketSize = bucketSize;
		this.upperBoundary = upperBoundary;
	}

	public void addValue(String variableName, long value) {
		variables.computeIfAbsent(variableName, Variable::new);
		variables.get(variableName).add(value);
		if (value > absoluteMaximum) {
			absoluteMaximum = value;
		}
	}

	public String[] getVariableNames() {
		return variables.keySet().toArray(new String[0]);
	}

	public int getBuckets() {
		return variables.values().stream()
				.mapToInt(v -> v.counts.size())
				.max()
				.orElse(0);
	}

	public int[] getBucket(int number) {
		int[] rv = new int[variables.size()];
		int i = 0;
		for (Variable variable : variables.values()) {
			rv[i++] = variable.getBucket(number);
		}
		return rv;
	}

	public long getBucketSize() {
		return bucketSize;
	}

	public long getAbsoluteMaximum() {
		return absoluteMaximum;
	}
}
