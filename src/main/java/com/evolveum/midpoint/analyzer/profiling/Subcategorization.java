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

package com.evolveum.midpoint.analyzer.profiling;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 */
public class Subcategorization {

	@NotNull public final String name;
	@NotNull public final Map<String, String> parameters = new LinkedHashMap<>();

	public Subcategorization(String name, Match... matches) {
		this.name = name;
		for (Match match : matches) {
			parameters.putAll(match.getGroupMatches());
		}
	}
}
