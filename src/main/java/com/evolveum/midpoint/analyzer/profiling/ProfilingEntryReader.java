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

import com.evolveum.midpoint.analyzer.Constants;
import com.evolveum.midpoint.analyzer.util.LogEntry;
import com.evolveum.midpoint.analyzer.util.LogEntryReader;
import com.evolveum.midpoint.collector.ThroughputCollector;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class ProfilingEntryReader {

	private static final Trace LOGGER = TraceManager.getTrace(ProfilingEntryReader.class);

	@NotNull private final LogEntryReader entryReader;
	private ThroughputCollector throughputCollector = new ThroughputCollector();

	private Date firstTimestamp;
	private Date lastProfilingTimestamp;
	private int profilingBatch = 0;
	private int lastProgress;

	private static final long GAP = 60000L;                                   // 1 minute

	// entry.message is like: #### Entry: 83329    ...model.impl.sync.SynchronizationServiceImpl->notifyChange
	// or ##### Exit: 1     ...task.quartzimpl.TaskManagerQuartzImpl->createTaskInstance etime: 1.314 ms

	private static final Pattern ENTRY_PATTERN = Pattern.compile("#### Entry: (?<seq>\\d+)\\s+\\.\\.\\.(?<method>\\S+)");
	private static final Pattern EXIT_PATTERN = Pattern.compile("##### Exit: (?<seq>\\d+)\\s+\\.\\.\\.(?<method>\\S+) etime: (?<etime>\\S+) ms");
	private static final String ENTRY_MARKER = "#### Entry: ";
	private static final String EXIT_MARKER = "##### Exit: ";

	@SuppressWarnings("WeakerAccess")
	public ProfilingEntryReader(@NotNull LogEntryReader entryReader) {
		this.entryReader = entryReader;
	}

	public ProfilingEntryReader(File directory) throws IOException {
		this(new LogEntryReader(directory));
	}

	public Date getFirstTimestamp() {
		return entryReader.getFirstTimestamp();
	}

	@FunctionalInterface
	public interface NewBatchListener {
		void onNewBatch(int batchNumber, LogEntry logEntry);
	}

	private NewBatchListener newBatchListener;

	private final Map<String, ProfilingItem> openItems = new HashMap<>();

	public ProfilingItem readItem() throws IOException {

		LogEntry entry;
		while ((entry = entryReader.readEntry()) != null) {

			//System.out.println("Entry: " + entry);

			if (firstTimestamp == null) {
				firstTimestamp = entry.timestamp;
			}
			Matcher progressMatcher = Constants.PROGRESS_PATTERN.matcher(entry.message);
			if (progressMatcher.matches()) {
				lastProgress = Integer.parseInt(progressMatcher.group("total"));
				long fromStart = entry.timestamp.getTime() - firstTimestamp.getTime();
				throughputCollector.registerProgress(fromStart);
			}

			if (!Constants.PROFILING.equals(entry.logger)) {
				continue;
			}

			ProfilingItem existingOpenItem = openItems.get(entry.threadName);

			ProfilingItem.Kind kind = getKind(entry.message);
			if (kind != null) {
				boolean newBatch;
				if (lastProfilingTimestamp == null || entry.timestamp.getTime() - lastProfilingTimestamp.getTime() >= GAP) {
					profilingBatch++;
					LOGGER.info("Starting collecting batch {} @ {}", profilingBatch, entry.timestamp);
					if (!openItems.isEmpty()) {
						LOGGER.warn("Found {} open profiling items: {}", openItems.size(), openItems);
						openItems.clear();
					}
					if (newBatchListener != null) {
						newBatchListener.onNewBatch(profilingBatch, entry);
					}
					newBatch = true;
				} else {
					newBatch = false;
				}
				lastProfilingTimestamp = entry.timestamp;

				Pattern pattern;
				switch (kind) {
					case ENTRY: pattern = ENTRY_PATTERN; break;
					case EXIT: pattern = EXIT_PATTERN; break;
					default: throw new AssertionError();
				}
				Matcher matcher = pattern.matcher(entry.message);
				if (!matcher.matches()) {
					LOGGER.warn("Profiling entry/exit message does not match the corresponding pattern: '{}' in {}", entry.message, entry);
				} else {
					int seq = Integer.parseInt(matcher.group("seq"));
					String method = matcher.group("method");
					Long etime;
					if (kind == ProfilingItem.Kind.EXIT) {
						String etimeString = matcher.group("etime");
						BigDecimal time = new BigDecimal(etimeString);
						etime = time.multiply(new BigDecimal(1000)).longValue();
					} else {
						etime = null;
					}
					ProfilingItem currentItem = new ProfilingItem(kind, seq, method, etime, entry, lastProgress, profilingBatch, newBatch);
					if (existingOpenItem != null) {
						LOGGER.info("Unexpected open item {} (got {})", existingOpenItem, entry);
						openItems.put(entry.threadName, currentItem);
						return existingOpenItem;
					}
					openItems.put(entry.threadName, currentItem);
				}
			} else {
				// a continuation
				if (existingOpenItem != null) {
					existingOpenItem.secondLogEntry = entry;
					openItems.remove(entry.threadName);
					return existingOpenItem;
				} else {
					LOGGER.info("Unexpected profiling continuation line: {}, ignoring", entry);
				}
			}
		}

		return null;
	}

	private ProfilingItem.Kind getKind(String message) {
		if (message.contains(ENTRY_MARKER)) {
			return ProfilingItem.Kind.ENTRY;
		} else if (message.contains(EXIT_MARKER)) {
			return ProfilingItem.Kind.EXIT;
		} else {
			return null;
		}
	}

	@SuppressWarnings("unused")
	public NewBatchListener getNewBatchListener() {
		return newBatchListener;
	}

	public void setNewBatchListener(NewBatchListener newBatchListener) {
		this.newBatchListener = newBatchListener;
	}

	public ThroughputCollector getThroughputCollector() {
		return throughputCollector;
	}

	public int getTotalLines() {
		return entryReader.getTotalLines();
	}

	public int getLogEntries() {
		return entryReader.getTotalEntries();
	}
}
