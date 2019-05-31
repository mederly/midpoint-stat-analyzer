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

import com.evolveum.midpoint.analyzer.Constants;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.evolveum.midpoint.analyzer.Constants.LOG_FILE_TIMESTAMP_FORMAT;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

/**
 *
 */
public class LogEntryReader {

	private static final Trace LOGGER = TraceManager.getTrace(LogEntryReader.class);
	private static final int MARK_AFTER = 500_000;

	@NotNull private final LogLineReader lineReader;

	// 2019-05-27 09:42:11,230 [midPointScheduler_Worker-6] DEBUG: #### Entry: 83329 ...model.impl.sync.SynchronizationServiceImpl->notifyChange
	// 2019-05-29 16:43:51,904 [pool-1-thread-1] DEBUG (PROFILING): ##### Exit: 817268    ...repo.sql.SqlRepositoryServiceImpl->getObject etime: 7.708 ms
	private static final Pattern LOG_LINE_PATTERN = Pattern.compile("(?<timestamp>" + Constants.LOG_FILE_TIMESTAMP_REGEX + ") \\[(?<thread>\\S+)] (?<level>\\S+)(?:\\s+\\((?<logger>\\S+)\\))?: (?<message>.*)");

	private static final SimpleDateFormat df = new SimpleDateFormat(LOG_FILE_TIMESTAMP_FORMAT, Locale.US);

	private int totalLines;
	private int totalEntries;
	private Date firstTimestamp;

	private LogEntry currentEntry;

	private String defaultLogger = Constants.PROFILING;

	public LogEntryReader(File directory) throws IOException {
		this(new LogLineReader(directory));
	}

	public LogEntryReader(@NotNull LogLineReader lineReader) {
		this.lineReader = lineReader;
	}

	public LogEntry readEntry() throws IOException {
		String line;
		while ((line = lineReader.readLine()) != null) {
			totalLines++;
			if (totalLines % MARK_AFTER == 0) {
				LOGGER.info("{} lines processed ({} entries)", totalLines, totalEntries);
			}
			Matcher matcher = LOG_LINE_PATTERN.matcher(line);
			if (matcher.matches()) {
				LogLineReader.LogFilePosition position = lineReader.getCurrentPosition();
				String timestampAsString = matcher.group("timestamp");
				String threadName = matcher.group("thread");
				String logLevel = matcher.group("level");
				String logger = defaultIfNull(matcher.group("logger"), defaultLogger);
				String message = matcher.group("message");
				Date timestamp;
				try {
					timestamp = df.parse(timestampAsString);
				} catch (ParseException e) {
					LOGGER.warn("Cannot parse timestamp in {}: {}", position, line, e);
					continue;
				}
				if (firstTimestamp == null) {
					firstTimestamp = timestamp;
				}
				LogEntry newEntry = new LogEntry(timestamp, threadName, logLevel, logger, message, line, position);
				totalEntries++;
				if (currentEntry != null) {
					LogEntry rv = currentEntry;
					currentEntry = newEntry;
					return rv;
				} else {
					currentEntry = newEntry;
					// continue reading the next line
				}
			} else if (currentEntry != null) {
				currentEntry.addLine(line);
			} else {
				LOGGER.warn("Log line without context -- skipping: {}", line);
			}
		}
		if (currentEntry != null) {
			LogEntry rv = currentEntry;
			currentEntry = null;
			return rv;
		} else {
			return null;
		}
	}

	public int getTotalLines() {
		return totalLines;
	}

	public int getTotalEntries() {
		return totalEntries;
	}

	public String getDefaultLogger() {
		return defaultLogger;
	}

	public void setDefaultLogger(String defaultLogger) {
		this.defaultLogger = defaultLogger;
	}

	public Date getFirstTimestamp() {
		return firstTimestamp;
	}
}
