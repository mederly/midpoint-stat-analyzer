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

import com.evolveum.midpoint.prism.Objectable;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.schema.MidPointPrismContextFactory;
import com.evolveum.midpoint.schema.constants.MidPointConstants;
import com.evolveum.midpoint.util.PrettyPrinter;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.MappingsStatisticsEntryType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.TaskType;
import org.apache.commons.io.FileUtils;
import org.xml.sax.SAXException;

import javax.xml.datatype.XMLGregorianCalendar;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 *
 */
public class TaskSetAnalyzer {

	private static final Trace LOGGER = TraceManager.getTrace(TaskSetAnalyzer.class);

	private static final String DIRECTORY = "c:\\midpoint\\tmp\\uwo-slowing-recon\\run-6\\tasks\\";

	private static final List<String> MARKER_MAPPINGS = Arrays.asList("*", "Office 365 Access", "Office 365 License - Undergraduate Student", "Office 365 License - Student Applicant");

	static class MappingInfo {
		int count;
		long duration;

		public MappingInfo(int count, long duration) {
			this.count = count;
			this.duration = duration;
		}
	}

	static class MappingPerf {
		int count;
		float avgTime;
	}

	public static void main(String[] args) throws SAXException, IOException, SchemaException {
		LOGGER.info("Initializing prism context");

		PrettyPrinter.setDefaultNamespacePrefix(MidPointConstants.NS_MIDPOINT_PUBLIC_PREFIX);
		PrismTestUtil.resetPrismContext(MidPointPrismContextFactory.FACTORY);
		PrismContext prismContext = PrismTestUtil.getPrismContext();

		LOGGER.info("Reading files from {}", DIRECTORY);

		TreeSet<TaskType> tasks = new TreeSet<>(Comparator.comparing(TaskSetAnalyzer::getTimestampAsLong));
		int files = 0;
		Iterator<File> iterator = FileUtils.iterateFiles(new File(DIRECTORY), null, true);
		while (iterator.hasNext()) {
			File file = iterator.next();
			LOGGER.debug("Parsing {}", file);
			PrismObject<?> object = prismContext.parserFor(file).xml().parse();
			Objectable objectable = object.asObjectable();
			if (objectable instanceof TaskType) {
				TaskType task = (TaskType) objectable;
				if (task.getOperationStats() != null && task.getOperationStats().getTimestamp() != null) {
					tasks.add(task);
					files++;
				} else {
					LOGGER.warn("Task with no timestamped operational stats: {}", file);
				}
			} else {
				LOGGER.warn("{} is not a Task, skipping (it is {})", file, objectable.getClass());
			}
		}
		LOGGER.info("{} tasks snapshot read from {} files", tasks.size(), files);
		int lastCount = 0;
		long lastTimestamp = 0;
		List<MappingInfo> lastMappings = new ArrayList<>();
		for (int i = 0; i < MARKER_MAPPINGS.size(); i++) {
			lastMappings.add(new MappingInfo(0, 0));
		}

		PrintWriter pw = new PrintWriter(new FileWriter(DIRECTORY + "../stat.csv"));

		for (TaskType task : tasks) {
			XMLGregorianCalendar timestamp = task.getOperationStats().getTimestamp();
			long timestampLong = XmlTypeConverter.toMillis(timestamp);
			int count = task.getOperationStats().getIterativeTaskInformation().getTotalSuccessCount() +
					task.getOperationStats().getIterativeTaskInformation().getTotalFailureCount();
			int delta;
			float perMinute;
			MappingPerf[] mappingsPerf;
			if (lastTimestamp == 0) {
				delta = 0;
				perMinute = 0;
				mappingsPerf = new MappingPerf[MARKER_MAPPINGS.size()];
				for (int i = 0; i < mappingsPerf.length; i++) {
					mappingsPerf[i] = new MappingPerf();
				}
			} else {
				delta = count - lastCount;
				perMinute = 60000.0f * delta / (timestampLong - lastTimestamp);
				mappingsPerf = computeMappingsPerformance(task, lastMappings);
			}
			System.out.print(String.format(Locale.US, "%-40tc %8d %5d %8.1f", timestamp.toGregorianCalendar(), count, delta, perMinute));
			for (MappingPerf mappingPerf: mappingsPerf) {
				System.out.print(String.format(Locale.US, " %7d %5.1f", mappingPerf.count, mappingPerf.avgTime));
			}
			System.out.println();

			if (lastTimestamp > 0) {
				pw.print(String.format(Locale.US, "%tc;%d;%d;%8.1f", timestamp.toGregorianCalendar(), count, delta, perMinute));
				for (MappingPerf mappingPerf: mappingsPerf) {
					pw.print(String.format(Locale.US, ";%d;%f", mappingPerf.count, mappingPerf.avgTime));
				}
				pw.println();
			} else {
				pw.print("timestamp;Objects-abs;Objects-delta;Objects per minute");
				for (int i = 0; i < MARKER_MAPPINGS.size(); i++) {
					pw.print(String.format(";M%d-count;M%d-avg-ms", i+1, i+1));
				}
				pw.println();
			}

			lastCount = count;
			lastTimestamp = timestampLong;
		}

		pw.close();
	}

	private static MappingPerf[] computeMappingsPerformance(TaskType task, List<MappingInfo> lastMappings) {
		MappingPerf[] rv = new MappingPerf[MARKER_MAPPINGS.size()];
		List<MappingInfo> currentMappings = extractMappings(task);
		for (int i = 0; i < MARKER_MAPPINGS.size(); i++) {
			MappingInfo current = currentMappings.get(i);
			MappingInfo last = lastMappings.get(i);
			int deltaCount = current.count - last.count;
			long deltaTime = current.duration - last.duration;
			rv[i] = new MappingPerf();
			rv[i].count = deltaCount;
			rv[i].avgTime = deltaCount != 0 ? (float) deltaTime / deltaCount : 0;
			//System.out.println(String.format("deltaCount = %d, deltaTime = %d, avg = %f", deltaCount, deltaTime, rv[i]));

			lastMappings.set(i, current);
		}
		return rv;
	}

	private static List<MappingInfo> extractMappings(TaskType task) {
		List<MappingInfo> rv = new ArrayList<>();
		List<MappingsStatisticsEntryType> entries = task.getOperationStats().getEnvironmentalPerformanceInformation()
				.getMappingsStatistics().getEntry();
		for (String markerMapping : MARKER_MAPPINGS) {
			if ("*".equals(markerMapping)) {
				int count = 0;
				long time = 0;
				for (MappingsStatisticsEntryType entry : entries) {
					count += entry.getCount();
					time += entry.getTotalTime();
				}
				rv.add(new MappingInfo(count, time));
			} else {
				Optional<MappingsStatisticsEntryType> found = entries.stream()
						.filter(entry -> markerMapping.equals(entry.getObject()))
						.findFirst();
				if (found.isPresent()) {
					rv.add(new MappingInfo(found.get().getCount(), found.get().getTotalTime()));
				} else {
					rv.add(new MappingInfo(0, 0));
				}
			}
		}
		return rv;
	}

	private static XMLGregorianCalendar getTimestamp(TaskType task) {
		return task.getOperationStats() != null ? task.getOperationStats().getTimestamp() : null;
	}

	private static long getTimestampAsLong(TaskType task) {
		XMLGregorianCalendar timestamp = getTimestamp(task);
		return timestamp != null ? XmlTypeConverter.toMillis(timestamp) : Long.MAX_VALUE;
	}
}
