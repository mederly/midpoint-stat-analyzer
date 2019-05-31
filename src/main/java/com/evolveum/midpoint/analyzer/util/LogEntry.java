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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Represents a log file entry.
 */
public class LogEntry {

	@NotNull public final Date timestamp;
	@NotNull public final String threadName;
	@NotNull public final String logLevel;
	@NotNull public final String logger;
	@NotNull public final String message;
	@NotNull public final String firstLine;
	@NotNull public final LogLineReader.LogFilePosition position;
	@NotNull public final List<String> otherLines = new ArrayList<>();

	public LogEntry(@NotNull Date timestamp, @NotNull String threadName, @NotNull String logLevel, @NotNull String logger,
			@NotNull String message,
			@NotNull String firstLine, @NotNull LogLineReader.LogFilePosition position) {
		this.timestamp = timestamp;
		this.threadName = threadName;
		this.logLevel = logLevel;
		this.logger = logger;
		this.message = message;
		this.firstLine = firstLine;
		this.position = position;
	}

	public void addLine(String line) {
		otherLines.add(line);
	}

	@NotNull
	public Date getTimestamp() {
		return timestamp;
	}

	@NotNull
	public String getThreadName() {
		return threadName;
	}

	@NotNull
	public String getLogLevel() {
		return logLevel;
	}

	@NotNull
	public String getMessage() {
		return message;
	}

	@NotNull
	public String getFirstLine() {
		return firstLine;
	}

	@NotNull
	public List<String> getOtherLines() {
		return otherLines;
	}

	@Override
	public String toString() {
		return "@" + timestamp + " [" + threadName + "] " + logLevel + " (" + logger + "): " + StringUtils.abbreviate(message, 30) + " @" + position;
	}
}
