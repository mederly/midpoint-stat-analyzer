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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 *
 */
public class MethodInvocation {

	@NotNull private final ProfilingItem entry;
	@NotNull private final ProfilingItem exit;
	private InvocationCategorization primaryCategorization;
	private List<Subcategorization> secondaryCategorization = new ArrayList<>();

	public MethodInvocation(@NotNull ProfilingItem entry, @NotNull ProfilingItem exit) {
		this.entry = entry;
		this.exit = exit;
	}

	public Date getTimestamp() {
		return exit.firstLogEntry.timestamp;
	}

	public String getMethodName() {
		return entry.method;
	}

	public String getThreadName() {
		return entry.getThreadName();
	}

	public long getExecutionTime() {
		return exit.etime;
	}

	@NotNull
	public String getArguments() {
		if (entry.secondLogEntry != null) {
			return StringUtils.removeStart(entry.secondLogEntry.message, "###### args: ");
		} else {
			return "";
		}
	}

	@NotNull
	public String getReturnValue() {
		if (exit.secondLogEntry != null) {
			return StringUtils.removeStart(exit.secondLogEntry.message, "###### retval: ");
		} else {
			return "";
		}
	}

	public void categorize(List<CategoryDefinition> categoryDefinitions,
			List<SubcategoryDefinition> subcategoryDefinitions) {
		primaryCategorization = getPrimaryCategorization(categoryDefinitions);
		secondaryCategorization.clear();
		addSecondaryCategorization(subcategoryDefinitions);
	}

	@Nullable
	private InvocationCategorization getPrimaryCategorization(List<CategoryDefinition> categoryDefinitions) {
		for (CategoryDefinition definition : categoryDefinitions) {
			InvocationCategorization categorization = tryMatchingPrimary(definition);
			if (categorization != null) {
				return categorization;
			}
		}
		return null;
	}

	private void addSecondaryCategorization(List<SubcategoryDefinition> subcategoryDefinitions) {
		for (SubcategoryDefinition subcategoryDefinition : subcategoryDefinitions) {
			Subcategorization sub = tryMatchingSecondary(subcategoryDefinition);
			if (sub != null) {
				secondaryCategorization.add(sub);
			}
		}
	}

	private InvocationCategorization tryMatchingPrimary(CategoryDefinition definition) {
		String methodName = getMethodName();
		String arguments = getArguments();
		Match methodMatch = definition.methodNameTemplate.match(methodName);
		if (methodMatch == null) {
			return null;
		}
		Match argumentsMatch = definition.argumentsTemplate.match(arguments);
		if (argumentsMatch == null) {
			return null;
		}
		return new InvocationCategorization(definition, methodMatch, argumentsMatch);
	}

	private Subcategorization tryMatchingSecondary(SubcategoryDefinition definition) {
		String value = primaryCategorization.parameters.get(definition.propertyName);
		if (value != null) {
			Match valueMatch = definition.propertyValueTemplate.match(value);
			if (valueMatch != null) {
				return new Subcategorization(definition.name, valueMatch);
			}
		}
		return null;
	}

	public String getCategoryName() {
		if (primaryCategorization != null) {
			return primaryCategorization.definition.name +
					secondaryCategorization.stream().map(c -> "." + c.name).collect(Collectors.joining());
		} else {
			return null;
		}
	}

	@NotNull
	public Map<String, String> getCategorizationParameters() {
		return primaryCategorization != null ? primaryCategorization.parameters : Collections.emptyMap();
	}

}
