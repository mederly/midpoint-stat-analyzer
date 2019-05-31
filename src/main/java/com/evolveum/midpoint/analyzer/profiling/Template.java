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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
class Template {

	private static final String GROUP_MARKER_START = "##{";
	private static final String GROUP_MARKER_END = "}##";

	private Pattern pattern;
	private List<String> groups;

	public Template(Pattern pattern, List<String> groups) {
		this.pattern = pattern;
		this.groups = groups;
	}

	public static Template compile(String text) {
		if (text != null) {
			return compileFromText(text);
		} else {
			return new Template(Pattern.compile(".*"), Collections.emptyList());
		}
	}

	private static Template compileFromText(String text) {
		StringBuilder sb = new StringBuilder();
		List<String> groups = new ArrayList<>();

		System.out.println("Parsing: " + text);

		for (int current = 0; current < text.length(); ) {
			int markerStartIndex = text.indexOf(GROUP_MARKER_START, current);
			if (markerStartIndex < 0) {
				sb.append(escape(text.substring(current)));
				break;
			} else {
				sb.append(escape(text.substring(current, markerStartIndex)));
				int markerEndIndex = text.indexOf(GROUP_MARKER_END, markerStartIndex);
				if (markerEndIndex < 0) {
					throw new IllegalArgumentException("Malformed template: " + text);
				}
				String wildcard = text.substring(markerStartIndex + GROUP_MARKER_START.length(), markerEndIndex);
				int colonIndex = wildcard.indexOf(':');
				String groupName;
				String regex;
				if (colonIndex < 0) {
					groupName = wildcard;
					regex = ".*";
				} else {
					groupName = wildcard.substring(0, colonIndex);
					regex = wildcard.substring(colonIndex + 1);
				}
				if (!groupName.isEmpty()) {
					sb.append("(?<").append(groupName).append(">").append(regex).append(")");
					groups.add(groupName);
				} else {
					sb.append(regex);
				}
				current = markerEndIndex + GROUP_MARKER_END.length();
			}
		}
		System.out.println("Regexp: " + sb);
		System.out.println("Groups: " + groups);
		return new Template(Pattern.compile(sb.toString()), groups);
	}

	private static String escape(String text) {
		return text.replaceAll("([^a-zA-Z0-9])", "\\\\$1");
	}

	public Match match(String text) {
		Matcher matcher = pattern.matcher(text);
		if (matcher.matches()) {
			Map<String, String> values = new LinkedHashMap<>();
			for (String groupName : groups) {
				String value = matcher.group(groupName);
				values.put(groupName, value);
			}
			return new Match(values);
		} else {
			return null;
		}
	}

}
