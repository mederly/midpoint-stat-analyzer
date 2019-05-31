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

import com.evolveum.midpoint.analyzer.util.Counters;
import com.evolveum.midpoint.analyzer.util.Histogram;
import com.evolveum.midpoint.collector.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

import static com.evolveum.midpoint.analyzer.Constants.LOG_FILE_TIMESTAMP_FORMAT;
import static com.evolveum.midpoint.analyzer.profiling.ProfilingItem.Kind.ENTRY;
import static com.evolveum.midpoint.analyzer.profiling.ProfilingItem.Kind.EXIT;

/**
 *
 */
public class ProfilingLogAnalyzer {

	private static final Trace LOGGER = TraceManager.getTrace(ProfilingLogAnalyzer.class);

	private static final String RUN = "local-11";

	private static final File DIRECTORY = new File("d:\\midpoint\\tmp\\uwo-slowing-recon\\" + RUN + "\\logs\\");
	private static final File ALL_INVOCATIONS_FILE = new File(DIRECTORY, "../invocations-all.txt");
	private static final File SELECTED_INVOCATIONS_FILE = new File(DIRECTORY, "../invocations-selected.csv");
	private static final File OBJECTS_PER_MINUTE_FILE = new File(DIRECTORY, "../per-minute.csv");
	private static final String LONG_INVOCATIONS_TXT_FILE_NAME_FORMAT = "../invocations-long-%d.txt";
	private static final String LONG_INVOCATIONS_CSV_FILE_NAME_FORMAT = "../invocations-long-%d.csv";
	private static final String PERFORMANCE_HISTOGRAM_FILE_NAME_FORMAT = "../methods-performance-histogram-%d%s%s.csv";
	private static final String SLOW_QUERY_CATEGORY_COUNTS_FILE_NAME_FORMAT = "../slow-query-category-counts-%d.csv";

	private static final long HISTOGRAM_STEP = 10_000L;                         // in microseconds
	private static final long HISTOGRAM_UPPER_BOUNDARY = 1_000_000L;            // in microseconds
	private static final long LONG_TIMES_THRESHOLD = 50_000L;                   // in microseconds

	private static boolean HISTOGRAM_PER_BATCH = false;
	private static boolean HISTOGRAM_PER_THREAD_TYPE = true;

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

	private static final List<Pattern> LONG_TIMES_INCLUDE = Arrays.asList(
			Pattern.compile(".*SqlRepositoryServiceImpl->searchObjects"),
			Pattern.compile(".*SqlRepositoryServiceImpl->searchShadowOwner"),
			Pattern.compile(".*SqlRepositoryServiceImpl->listAccountShadowOwner")
	);

	@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
	private static final List<Pattern> LONG_TIMES_EXCLUDE = Arrays.asList();

	private static final List<CategoryDefinition> CATEGORY_DEFINITIONS = Arrays.asList(
			new CategoryDefinition("search-shadow-attr-resource", "repo.sql.SqlRepositoryServiceImpl->searchObjects",
					"(ShadowType, Q{AND(OR(EQUAL: attributes/##{attr}##,PPV(String:##{name}##),NONE),REF: resourceRef,PRV(oid=##{resourceOid}##, targetType=null)),##{paging}##, ##{other}##, R(##{}##))"),
			new CategoryDefinition("search-shadow-attr-OC-resource", "repo.sql.SqlRepositoryServiceImpl->searchObjects",
					"(ShadowType, Q{AND(EQUAL: attributes/##{attr}##,PPV(String:##{uid}##),EQUAL: objectClass,PPV(##{oc}##),REF: resourceRef,PRV(oid=##{resourceOid}##, targetType=null)),##{paging}##, ##{other}##, R(##{}##))"),
			new CategoryDefinition("search-shadow-attr-OC-resource-not-dead", "repo.sql.SqlRepositoryServiceImpl->searchObjects",
					"(ShadowType, Q{AND(EQUAL: attributes/##{attr}##,PPV(String:##{name}##),EQUAL: objectClass,PPV(##{oc}##),REF: resourceRef,PRV(oid=##{resourceOid}##, targetType=null),OR(EQUAL: dead,PPV(Boolean:false),EQUAL: dead,)),##{paging}##, ##{other}##, R(##{}##))"),
			new CategoryDefinition("search-shadow-other", "repo.sql.SqlRepositoryServiceImpl->searchObjects",
					"(ShadowType, ##{}##)"),
			new CategoryDefinition("search-user", "repo.sql.SqlRepositoryServiceImpl->searchObjects",
					"(UserType, ##{}##)"),
			new CategoryDefinition("search-other", "repo.sql.SqlRepositoryServiceImpl->searchObjects",
					"(##{}##)"),

			new CategoryDefinition("searchShadowOwner", "repo.sql.SqlRepositoryServiceImpl->searchShadowOwner"),
			new CategoryDefinition("listAccountShadowOwner", "repo.sql.SqlRepositoryServiceImpl->listAccountShadowOwner"),
			new CategoryDefinition("other")
	);

	private static final List<SubcategoryDefinition> SUBCATEGORY_DEFINITIONS = Arrays.asList(
			new SubcategoryDefinition("no-paging", "paging", "null paging"),
			new SubcategoryDefinition("after-oid-null", "paging", "PAGING: M: ##{}##,, after OID: ##{:null.*}##"),         // sometimes there's a comma after null
			new SubcategoryDefinition("after-oid-X", "paging", "PAGING: M: ##{}##,, after OID: ##{:(?!null.*).*}##")
	);

	public static void main(String[] args) throws IOException {

		SimpleDateFormat df = new SimpleDateFormat(LOG_FILE_TIMESTAMP_FORMAT, Locale.US);

		Histogram histogram = new Histogram(HISTOGRAM_STEP, HISTOGRAM_UPPER_BOUNDARY);

		Collector collector = new CollectorImpl();
		Map<String, Integer> currentlyCollecting = new HashMap<>();
		Map<Integer, ProfilingItem> openMethodEntries = new HashMap<>();

		List<MethodInvocation> longInvocations = new ArrayList<>();

		PrintWriter pwAllInvocations = new PrintWriter(new FileWriter(ALL_INVOCATIONS_FILE));

		PrintWriter pwSelectedInvocations = new PrintWriter(new FileWriter(SELECTED_INVOCATIONS_FILE));
		pwSelectedInvocations.print("Timestamp;Second;Thread;Progress");
		for (String methodName : EXTRACTING) {
			pwSelectedInvocations.print(";"+StringUtils.substringAfter(methodName, "->"));
		}
		pwSelectedInvocations.println();

		ProfilingEntryReader profilingEntryReader = new ProfilingEntryReader(DIRECTORY);
		profilingEntryReader.setNewBatchListener(((batchNumber, logEntry) -> {
			collector.reset();
			currentlyCollecting.clear();
			openMethodEntries.clear();
		}));


		ProfilingItem item;
		while ((item = profilingEntryReader.readItem()) != null) {

			// generally useful information
			String threadName = item.getThreadName();
			Date timestamp = item.firstLogEntry.timestamp;
			int profilingBatch = item.batch;
			Date firstTimestamp = profilingEntryReader.getFirstTimestamp();
			assert firstTimestamp != null;

			// are we collecting?
			if (!currentlyCollecting.containsKey(threadName)) {
				if (item.kind == ENTRY && isMainMethod(item.method)) {
					currentlyCollecting.put(threadName, item.sequenceNumber);
				} else {
					continue;
				}
			}

			assert currentlyCollecting.containsKey(threadName);
			int enclosingEntry = currentlyCollecting.get(threadName);

			if (item.kind == ENTRY) {
				openMethodEntries.put(item.sequenceNumber, item);
			} else if (item.kind == EXIT) {

				ProfilingItem methodEntry = openMethodEntries.get(item.sequenceNumber);
				if (methodEntry == null) {
					LOGGER.warn("Method exit without entry: {}", item);
				} else {
					if (item.etime >= LONG_TIMES_THRESHOLD && matches(item.method, LONG_TIMES_INCLUDE) && !matches(item.method, LONG_TIMES_EXCLUDE)) {
						longInvocations.add(new MethodInvocation(methodEntry, item));
					}
					openMethodEntries.remove(item.sequenceNumber);
				}

				ThreadType threadType = ThreadType.determine(threadName);
				if (!matches(item.method, EXCLUDE_FROM_HISTOGRAM)) {
					if (HISTOGRAM_PER_BATCH) {
						if (HISTOGRAM_PER_THREAD_TYPE) {
							histogram.addValue(String.format("%s:%03d:%s", item.method, profilingBatch, threadType), item.etime);
						}
						histogram.addValue(String.format("%s:%03d", item.method, profilingBatch), item.etime);
					} else if (HISTOGRAM_PER_THREAD_TYPE) {
						histogram.addValue(String.format("%s:%s", item.method, threadType), item.etime);
					}
					histogram.addValue(String.format("%s", item.method), item.etime);
				}

				collector.registerEvent(threadName, new Event(item.method, timestamp.getTime(), item.etime));

				if (item.sequenceNumber == enclosingEntry) {
					EventsSummary summary = collector.closeTag(threadName);
					pwAllInvocations.println(String.format(Locale.US, "Method calls for entry #%d [%s] at %s (progress: %d):", enclosingEntry, threadName,
							df.format(timestamp), item.progress));
					pwAllInvocations.println(summary.dump());
					pwAllInvocations.println();
					currentlyCollecting.remove(threadName);

					pwSelectedInvocations.print(String.format("%s;%d;%s;%d",
							df.format(timestamp), (timestamp.getTime() - firstTimestamp.getTime()) / 1000, threadName, item.progress));
					for (String methodName : EXTRACTING) {
						Times times = summary.get(methodName);
						long max = times != null && times.getMaxTime() != null ? times.getMaxTime() : 0;
						pwSelectedInvocations.print(String.format(";%d", max));
					}
					pwSelectedInvocations.println();
				}
			} else {
				throw new AssertionError("kind: " + item.kind);
			}
		}
		pwAllInvocations.close();
		pwSelectedInvocations.close();

		PrintWriter pwPerMinute = new PrintWriter(new FileWriter(OBJECTS_PER_MINUTE_FILE));
		pwPerMinute.println("Minute;Objects");
		int[] countsPerMinute = profilingEntryReader.getThroughputCollector().getCountsPerMinute();
		for (int i = 0; i < countsPerMinute.length; i++) {
			pwPerMinute.println(i + ";" + countsPerMinute[i]);
		}
		pwPerMinute.close();

		String histogramFileName = String.format(PERFORMANCE_HISTOGRAM_FILE_NAME_FORMAT, HISTOGRAM_STEP,
				HISTOGRAM_PER_BATCH ? "-batch" : "", HISTOGRAM_PER_THREAD_TYPE ? "-thread" : "");
		File histogramFile = new File(DIRECTORY, histogramFileName);
		PrintWriter pwHistogram = new PrintWriter(new FileWriter(histogramFile));
		pwHistogram.print("Bucket;From;To;Millis");
		for (String variableName : histogram.getVariableNames()) {
			pwHistogram.print(";" + variableName);
		}
		pwHistogram.println();
		int buckets = histogram.getBuckets();
		for (int i = 0; i < buckets; i++) {
			long lower = i * histogram.getBucketSize();
			long upper = i < buckets - 1 ? (i + 1) * histogram.getBucketSize() - 1 : histogram.getAbsoluteMaximum();
			double millis = (upper+1) / 1000.0;
			pwHistogram.print(String.format(Locale.US, "%d;%d;%d;%f", i, lower, upper, millis));
			int[] bucket = histogram.getBucket(i);
			for (int count : bucket) {
				pwHistogram.print(";" + count);
			}
			pwHistogram.println();
		}
		pwHistogram.close();

		Counters<String> categoryCounters = new Counters<>();
		longInvocations.sort(Comparator.comparing(MethodInvocation::getExecutionTime, Comparator.reverseOrder()));
		long thresholdMillis = LONG_TIMES_THRESHOLD / 1000;
		File longInvocationsTxtFile = new File(DIRECTORY, String.format(LONG_INVOCATIONS_TXT_FILE_NAME_FORMAT, thresholdMillis));
		File longInvocationsCsvFile = new File(DIRECTORY, String.format(LONG_INVOCATIONS_CSV_FILE_NAME_FORMAT, thresholdMillis));
		PrintWriter pwLongInvocationsTxt = new PrintWriter(new FileWriter(longInvocationsTxtFile));
		PrintWriter pwLongInvocationsCsv = new PrintWriter(new FileWriter(longInvocationsCsvFile));
		for (MethodInvocation invocation : longInvocations) {
			String timestamp = df.format(invocation.getTimestamp());
			String method = invocation.getMethodName();
			String thread = invocation.getThreadName();
			long micros = invocation.getExecutionTime();
			String arguments = invocation.getArguments();
			String returnValue = invocation.getReturnValue();
			invocation.categorize(CATEGORY_DEFINITIONS, SUBCATEGORY_DEFINITIONS);
			String categoryName = invocation.getCategoryName();
			String parameters = String.valueOf(invocation.getCategorizationParameters());
			pwLongInvocationsTxt.println(String.format(Locale.US, "%s %-30s %-60s %10d %-70s %-100s %s -> %s", timestamp, "["+thread+"]", method, micros, categoryName, parameters, arguments, returnValue));
			pwLongInvocationsCsv.println(String.format(Locale.US, "%s;%s;%s;%d;%s;%s;%s;%s", timestamp, thread, method, micros, categoryName, parameters, arguments, returnValue));
			categoryCounters.increment(categoryName);
		}
		pwLongInvocationsTxt.close();
		pwLongInvocationsCsv.close();

		File slowQueryCategoryCountsFile = new File(DIRECTORY, String.format(SLOW_QUERY_CATEGORY_COUNTS_FILE_NAME_FORMAT, thresholdMillis));
		PrintWriter pwQueryCategoryCounts = new PrintWriter(new FileWriter(slowQueryCategoryCountsFile));
		pwQueryCategoryCounts.println("Category;Count");
		for (Map.Entry<String, Integer> entry : categoryCounters.getCountsMap().entrySet()) {
			pwQueryCategoryCounts.println(entry.getKey() + ";" + entry.getValue());
		}
		pwQueryCategoryCounts.close();

		int totalLines = profilingEntryReader.getTotalLines();
		int logEntries = profilingEntryReader.getLogEntries();
		LOGGER.info("Total lines: {}, log entry lines: {}, continuation lines: {}", totalLines, logEntries, totalLines-logEntries);
		LOGGER.info("Histogram written to: {}", histogramFile);
		LOGGER.info("Long invocations: {}", longInvocations.size());
	}

	@SuppressWarnings("SameParameterValue")
	private static boolean matches(String s, List<Pattern> patterns) {
		return patterns.stream().anyMatch(p -> p.matcher(s).matches());
	}

	private static boolean isMainMethod(String method) {
		return MAIN_METHODS.contains(method);
	}

	enum ThreadType {
		COORDINATOR, WORKER, OTHER;

		public static ThreadType determine(String name) {
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
