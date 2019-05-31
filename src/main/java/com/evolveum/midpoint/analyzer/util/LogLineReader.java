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
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Reads a set of log files. Provides virtual readLine() method that returns the next line,
 * irrespective on the log file it resides in.
 */
public class LogLineReader {

	private static final Trace LOGGER = TraceManager.getTrace(LogLineReader.class);

	private Iterator<LogFileInfo> fileIterator;
	private LogFileInfo currentFileInfo;
	private BufferedReader reader;
	private int lineNumber;

	public LogLineReader(File directory) throws IOException {
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

	public static class LogFilePosition {
		@NotNull private final File file;
		private final int lineNumber;

		public LogFilePosition(@NotNull File file, int lineNumber) {
			this.file = file;
			this.lineNumber = lineNumber;
		}

		@NotNull
		public File getFile() {
			return file;
		}

		public int getLineNumber() {
			return lineNumber;
		}

		@Override
		public String toString() {
			return "[" + file + ":" + lineNumber + ']';
		}
	}

	private void scanFiles(File directory) throws IOException {
		SimpleDateFormat df = new SimpleDateFormat(Constants.LOG_FILE_TIMESTAMP_FORMAT, Locale.US);
		List<LogFileInfo> files = new ArrayList<>();
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
			} catch (Throwable t) { // fix this :)
				LOGGER.warn("Cannot parse log file {}, skipping", file, t);
			}
		}
		files.sort(Comparator.comparing(info -> info.startTimestamp));
		fileIterator = files.iterator();
	}

	public String readLine() throws IOException {
		for (;;) {
			if (reader == null) {
				if (fileIterator.hasNext()) {
					currentFileInfo = fileIterator.next();
					File file = currentFileInfo.file;
					LOGGER.info("Opening file {}", file);
					reader = new BufferedReader(new FileReader(file));
					lineNumber = 0;
				} else {
					currentFileInfo = null;
					return null;
				}
			}
			String line = reader.readLine();
			if (line != null) {
				lineNumber++;
				return line;
			} else {
				reader.close();
				reader = null;
			}
		}
	}

	public LogFilePosition getCurrentPosition() {
		if (currentFileInfo != null) {
			return new LogFilePosition(currentFileInfo.file, lineNumber);
		} else {
			return null;
		}
	}
}
