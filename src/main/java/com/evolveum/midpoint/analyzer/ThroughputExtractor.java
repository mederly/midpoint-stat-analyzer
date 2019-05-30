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

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 *
 */
public class ThroughputExtractor {

	private static final File DIR = new File("c:\\midpoint\\tmp\\uwo-slowing-recon\\local-1\\");
	private static final File LOGFILE = new File(DIR, "done.log");
	private static final File OUTFILE = new File(DIR, "minutes.csv");

	public static void main(String[] args) throws IOException {

		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS", Locale.US);

		List<Integer> minutes = new ArrayList<>();

		BufferedReader br = new BufferedReader(new FileReader(LOGFILE));
		String line;
		Date first = null;
		int max = 0;
		while ((line = br.readLine()) != null) {
			if (line.length() < 23) {
				continue;
			}
			String timestampAsString = line.substring(0, 23);
			Date timestamp;
			try {
				timestamp = df.parse(timestampAsString);
			} catch (ParseException e) {
				System.out.println(e.getMessage() + " in " + line);
				continue;
			}
			if (first == null) {
				first = timestamp;
			}
			long delta = timestamp.getTime() - first.getTime();
			int minute = (int) (delta / 60000);
			minutes.add(minute);
			if (minute > max) {
				max = minute;
			}
		}
		br.close();

		int[] counts = new int[max+1];
		minutes.forEach(m -> counts[m]++);

		PrintWriter pw = new PrintWriter(new FileWriter(OUTFILE));
		for (int count : counts) {
			pw.println(count);
		}
		pw.close();
	}
}
