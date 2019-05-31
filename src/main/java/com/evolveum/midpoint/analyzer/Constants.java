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

import java.util.regex.Pattern;

/**
 *
 */
public class Constants {

	public static final String LOG_FILE_TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss,SSS";
	public static final String PROFILING = "PROFILING";
	public static final String LOG_FILE_TIMESTAMP_REGEX = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3}";

	// ...... (this one: 2984 ms, avg: 2984 ms) (total progress: 1, wall clock avg: 4098 ms)
	public static final Pattern PROGRESS_PATTERN = Pattern.compile(".*\\(total progress: (?<total>\\d+), wall clock avg: \\d+ ms\\)$");
}
