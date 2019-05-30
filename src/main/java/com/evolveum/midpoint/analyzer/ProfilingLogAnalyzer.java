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

import com.evolveum.midpoint.collector.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class ProfilingLogAnalyzer {

	private static final Trace LOGGER = TraceManager.getTrace(ProfilingLogAnalyzer.class);

	private static final File DIRECTORY = new File("d:\\midpoint\\tmp\\uwo-slowing-recon\\vilo-1\\logs\\");
	private static final File OUTFILE = new File(DIRECTORY, "../output.txt");
	private static final File EXTRACTS_FILE = new File(DIRECTORY, "../extracts.csv");
	private static final File OBJECTS_PER_MINUTE_FILE = new File(DIRECTORY, "../per-minute.csv");
	private static final String PERFORMANCE_HISTOGRAM_FILE_NAME_FORMAT = "../methods-performance-histogram-%d%s%s.csv";

	private static final long GAP = 60000L;                                   // 1 minute
	private static final long HISTOGRAM_STEP = 10_000L;                         // in microseconds
	private static final long HISTOGRAM_UPPER_BOUNDARY = 1_000_000L;            // in microseconds

	private static boolean HISTOGRAM_PER_BATCH = false;
	private static boolean HISTOGRAM_PER_THREAD_TYPE = true;

	static final String LOG_FILE_TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss,SSS";
	private static final Pattern LOG_LINE_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3}) \\[(\\S+)] (.*)");
	private static final Pattern EXIT_PATTERN = Pattern.compile("##### Exit: (\\d+)\\s+\\.\\.\\.(\\S+) etime: (\\S+) ms");
	private static final String ENTRY_MARKER = "#### Entry: ";
	private static final String EXIT_MARKER = "##### Exit: ";

	private static final Pattern PROGRESS_PATTERN = Pattern.compile(".*\\(total progress: (\\d+), wall clock avg: \\d+ ms\\)$");

	private static final List<String> MAIN_METHODS = Arrays.asList(
			"model.impl.sync.SynchronizationServiceImpl->notifyChange",
			"repo.sql.SqlRepositoryServiceImpl->searchObjects");

	private static final List<String> EXTRACTING = Arrays.asList(
			"repo.sql.SqlRepositoryServiceImpl->searchObjectsIterative",
			"repo.sql.SqlRepositoryServiceImpl->searchObjects",
			"repo.sql.SqlRepositoryServiceImpl->searchShadowOwner",
			"repo.sql.SqlRepositoryServiceImpl->listAccountShadowOwner");

	private static final List<Pattern> EXCLUDE_FROM_HISTOGRAM = Arrays.asList(
			Pattern.compile(".*enterConstraintsCheckerCache.*"),
			Pattern.compile(".*exitConstraintsCheckerCache.*"),
			Pattern.compile(".*createAndRegisterConflictWatcher.*"),
			Pattern.compile(".*RepositoryCache->.*"),
			Pattern.compile(".*hasConflict.*"),
			Pattern.compile(".*getFullTextSearchConfiguration.*"),
			Pattern.compile(".*unregisterConflictWatcher.*"),
			Pattern.compile(".*TaskManagerQuartzImpl.*"),
			Pattern.compile(".*WorkflowManagerImpl.*"));

	@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
	private static final List<Pattern> LONG_TIMES_FOR = Arrays.asList(
			Pattern.compile(".*SqlRepositoryServiceImpl.*"));

	public static void main(String[] args) throws IOException {

		SimpleDateFormat df = new SimpleDateFormat(LOG_FILE_TIMESTAMP_FORMAT, Locale.US);

		Histogram histogram = new Histogram(HISTOGRAM_STEP, HISTOGRAM_UPPER_BOUNDARY);

		ProfilingLogReader reader = new ProfilingLogReader(DIRECTORY);
		String line;
		long lastProfilingTimestamp = 0;

		Collector collector = new CollectorImpl();
		Map<String, Integer> currentlyCollecting = new HashMap<>();

		int lastProgress = 0;

		PrintWriter out = new PrintWriter(new FileWriter(OUTFILE));

		PrintWriter extractsOut = new PrintWriter(new FileWriter(EXTRACTS_FILE));
		extractsOut.print("Timestamp;Second;Thread;Progress");
		for (String methodName : EXTRACTING) {
			extractsOut.print(";"+StringUtils.substringAfter(methodName, "->"));
		}
		extractsOut.println();

		Long firstTimestamp = null;
		int logEntryLines = 0;
		int totalLines = 0;
		int profilingBatch = 0;
		ThroughputCollector throughputCollector = new ThroughputCollector();

		while ((line = reader.readLine()) != null) {
			totalLines++;
			Matcher lineMatcher = LOG_LINE_PATTERN.matcher(line);
			if (!lineMatcher.matches()) {
				continue;
			}
			logEntryLines++;
			String timestampAsString = lineMatcher.group(1);
			String threadName = lineMatcher.group(2);
			Date timestamp;
			try {
				timestamp = df.parse(timestampAsString);
			} catch (ParseException e) {
				System.out.println(e.getMessage() + " in " + line);
				continue;
			}
			if (firstTimestamp == null) {
				firstTimestamp = timestamp.getTime();
			}
			Matcher progressMatcher = PROGRESS_PATTERN.matcher(line);
			if (progressMatcher.matches()) {
				lastProgress = Integer.parseInt(progressMatcher.group(1));
				long fromStart = timestamp.getTime() - firstTimestamp;
				throughputCollector.registerProgress(fromStart);
			}

			if (line.contains(ENTRY_MARKER) || line.contains(EXIT_MARKER)) {
				if (timestamp.getTime() - lastProfilingTimestamp >= GAP) {
					profilingBatch++;
					LOGGER.info("Starting collecting batch {} @ {}", profilingBatch, timestamp);
					collector.reset();
					currentlyCollecting.clear();
				}
				lastProfilingTimestamp = timestamp.getTime();
			}

			// Recognizing start of collection:
			// 2019-05-27 09:42:11,230 [midPointScheduler_Worker-6] DEBUG: #### Entry: 83329 ...model.impl.sync.SynchronizationServiceImpl->notifyChange

			int entryIndex = line.indexOf(ENTRY_MARKER);
			if (!currentlyCollecting.containsKey(threadName)) {
				if (entryIndex >= 0 && isMainMethodEntry(line)) {
					String fromEntry = line.substring(entryIndex + ENTRY_MARKER.length());
					String entryAsString = fromEntry.substring(0, fromEntry.indexOf(' '));
					int entryNumber = Integer.parseInt(entryAsString);
					//System.out.println("Found entry #" + entryNumber + " for " + threadName + " in: " + line);
					currentlyCollecting.put(threadName, entryNumber);
				} else {
					continue;
				}
			}

			assert currentlyCollecting.containsKey(threadName);
			int enclosingEntry = currentlyCollecting.get(threadName);

			// Recognizing an event
			// 2019-05-27 09:42:11,240 [midPointScheduler_Worker-6] DEBUG: ##### Exit: 83330   ...repo.cache.RepositoryCache->modifyObject etime: 10.046 ms

			//System.out.println("Payload: " + payload);
			int exitIndex = line.indexOf(EXIT_MARKER);
			if (exitIndex >= 0) {
				Matcher matcher = EXIT_PATTERN.matcher(line.substring(exitIndex));
				if (matcher.matches()) {
					int entryNumber = Integer.parseInt(matcher.group(1));
					String method = matcher.group(2);
					BigDecimal time = new BigDecimal(matcher.group(3));
					long micros = time.multiply(new BigDecimal(1000)).longValue();

					//System.out.println(String.format("[%-30s] #%7d %-80s %10s ms = %6d us", threadName, entryNumber, method, time, micros));

					ThreadType threadType = ThreadType.determine(threadName);
					if (!matches(method, EXCLUDE_FROM_HISTOGRAM)) {
						if (HISTOGRAM_PER_BATCH) {
							if (HISTOGRAM_PER_THREAD_TYPE) {
								histogram.addValue(String.format("%s:%03d:%s", method, profilingBatch, threadType), micros);
							}
							histogram.addValue(String.format("%s:%03d", method, profilingBatch), micros);
						} else if (HISTOGRAM_PER_THREAD_TYPE) {
							histogram.addValue(String.format("%s:%s", method, threadType), micros);
						}
						histogram.addValue(String.format("%s", method), micros);
					}

					collector.registerEvent(threadName, new Event(method, timestamp.getTime(), micros));

					if (entryNumber == enclosingEntry) {
						EventsSummary summary = collector.closeTag(threadName);
						out.println(String.format(Locale.US, "Method calls for entry #%d [%s] at %s (progress: %d):", enclosingEntry, threadName,
								timestampAsString, lastProgress));
						out.println(summary.dump());
						out.println();
						currentlyCollecting.remove(threadName);

						extractsOut.print(String.format("%s;%d;%s;%d", timestampAsString, (timestamp.getTime() - firstTimestamp) / 1000, threadName, lastProgress));
						for (String methodName : EXTRACTING) {
							Times times = summary.get(methodName);
							long max = times != null && times.getMaxTime() != null ? times.getMaxTime() : 0;
							extractsOut.print(String.format(";%d", max));
						}
						extractsOut.println();
					}
				}
			}
		}
		out.close();
		extractsOut.close();

		PrintWriter perMinuteOut = new PrintWriter(new FileWriter(OBJECTS_PER_MINUTE_FILE));
		perMinuteOut.println("Minute;Objects");
		int[] countsPerMinute = throughputCollector.getCountsPerMinute();
		for (int i = 0; i < countsPerMinute.length; i++) {
			perMinuteOut.println(i + ";" + countsPerMinute[i]);
		}
		perMinuteOut.close();

		String histogramFileName = String.format(PERFORMANCE_HISTOGRAM_FILE_NAME_FORMAT, HISTOGRAM_STEP,
				HISTOGRAM_PER_BATCH ? "-batch" : "", HISTOGRAM_PER_THREAD_TYPE ? "-thread" : "");
		File histogramFile = new File(DIRECTORY, histogramFileName);
		PrintWriter histogramOut = new PrintWriter(new FileWriter(histogramFile));
		histogramOut.print("Bucket;From;To;Millis");
		for (String variableName : histogram.getVariableNames()) {
			histogramOut.print(";" + variableName);
		}
		histogramOut.println();
		int buckets = histogram.getBuckets();
		for (int i = 0; i < buckets; i++) {
			long lower = i * histogram.getBucketSize();
			long upper = i < buckets - 1 ? (i + 1) * histogram.getBucketSize() - 1 : histogram.getAbsoluteMaximum();
			double millis = (upper+1) / 1000.0;
			histogramOut.print(String.format(Locale.US, "%d;%d;%d;%f", i, lower, upper, millis));
			int[] bucket = histogram.getBucket(i);
			for (int count : bucket) {
				histogramOut.print(";" + count);
			}
			histogramOut.println();
		}
		histogramOut.close();

		LOGGER.info("Total lines: {}, log entry lines: {}, continuation lines: {}", totalLines, logEntryLines, totalLines-logEntryLines);
		LOGGER.info("Histogram written to: {}", histogramFile);
	}

	@SuppressWarnings("SameParameterValue")
	private static boolean matches(String s, List<Pattern> patterns) {
		return patterns.stream().anyMatch(p -> p.matcher(s).matches());
	}

	private static boolean isMainMethodEntry(String line) {
		for (String mainMethod : MAIN_METHODS) {
			if (line.endsWith("..."+mainMethod)) {
				return true;
			}
		}
		return false;
	}

	enum ThreadType {
		COORDINATOR, WORKER, OTHER;

		private static ThreadType determine(String name) {
			if (name.startsWith("midPointScheduler_Worker-")) {
				return COORDINATOR;
			} else if (name.startsWith("pool-")) {
				return WORKER;
			} else {
				return OTHER;
			}
		}
	}


}
