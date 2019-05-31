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

import com.evolveum.midpoint.analyzer.util.LogEntry;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a profiling log item (entry or exit).
 */
public class ProfilingItem {

	public enum Kind {
		ENTRY, EXIT
	}

	@NotNull public final Kind kind;
	public final int sequenceNumber;
	public final String method;
	public final Long etime;
	@NotNull public final LogEntry firstLogEntry;
	public LogEntry secondLogEntry;
	@NotNull public final int progress;
	public final int batch;
	public final boolean newBatch;

	public ProfilingItem(@NotNull Kind kind, int sequenceNumber, String method, Long etime,
			@NotNull LogEntry firstLogEntry,
			int progress, int batch, boolean newBatch) {
		this.kind = kind;
		this.sequenceNumber = sequenceNumber;
		this.method = method;
		this.etime = etime;
		this.firstLogEntry = firstLogEntry;
		this.progress = progress;
		this.batch = batch;
		this.newBatch = newBatch;
	}

	public String getThreadName() {
		return firstLogEntry.threadName;
	}

	@Override
	public String toString() {
		return kind + " #" + sequenceNumber + " (" + method + ":" + etime + ") p:" + progress + ", b:" + batch +
				(newBatch ? " (new)" : " ") + firstLogEntry + " / " + secondLogEntry;
	}
}
