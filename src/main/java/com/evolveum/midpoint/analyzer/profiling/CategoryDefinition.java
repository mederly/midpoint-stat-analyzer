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

/**
 *
 */
public class CategoryDefinition {

	@NotNull public final String name;
	@NotNull public final Template methodNameTemplate;
	@NotNull public final Template argumentsTemplate;

	public CategoryDefinition(@NotNull String name) {
		this(name, null, null);
	}

	public CategoryDefinition(@NotNull String name, String methodNamePatternString) {
		this(name, methodNamePatternString, null);
	}

	public CategoryDefinition(@NotNull String name, String methodNamePatternString, String argumentsPatternString) {
		this.name = name;
		methodNameTemplate = Template.compile(methodNamePatternString);
		argumentsTemplate = Template.compile(argumentsPatternString);
	}

}
