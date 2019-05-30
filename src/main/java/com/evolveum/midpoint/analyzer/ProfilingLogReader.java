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

import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.evolveum.midpoint.analyzer.ProfilingLogAnalyzer.LOG_FILE_TIMESTAMP_FORMAT;

/**
 *
 */
public class ProfilingLogReader {

	private static final Trace LOGGER = TraceManager.getTrace(ProfilingLogReader.class);

	private List<LogFileInfo> files;
	private Iterator<LogFileInfo> fileIterator;
	private BufferedReader reader = null;

	ProfilingLogReader(File directory) throws IOException {
		scanFiles(directory);
	}

	private static class LogFileInfo {
		final File file;
		final long startTimestamp;

		LogFileInfo(File file, long startTimestamp) {
			this.file = file;
			this.startTimestamp = startTimestamp;
		}
	}

	private void scanFiles(File directory) throws IOException {
		SimpleDateFormat df = new SimpleDateFormat(LOG_FILE_TIMESTAMP_FORMAT, Locale.US);
		files = new ArrayList<>();
		Iterator<File> iterator = FileUtils.iterateFiles(directory, null, true);
		while (iterator.hasNext()) {
			File file = iterator.next();
			BufferedReader br = new BufferedReader(new FileReader(file));
			String firstLine = br.readLine();
			if (firstLine == null) {
				LOGGER.warn("Empty log file {}, skipping", file);
				continue;
			}
			try {
				String timestamp = firstLine.substring(0, 23);
				Date date = df.parse(timestamp);
				files.add(new LogFileInfo(file, date.getTime()));
			} catch (Throwable t) {
				LOGGER.warn("Cannot parse log file {}, skipping", file, t);
			}
		}
		files.sort(Comparator.comparing(info -> info.startTimestamp));
		fileIterator = files.iterator();
	}

	String readLine() throws IOException {
		for (;;) {
			if (reader == null) {
				if (fileIterator.hasNext()) {
					File file = fileIterator.next().file;
					LOGGER.info("Opening file {}", file);
					reader = new BufferedReader(new FileReader(file));
				} else {
					return null;
				}
			}
			String line = reader.readLine();
			if (line != null) {
				return line;
			} else {
				reader.close();
				reader = null;
			}
		}
	}
}
