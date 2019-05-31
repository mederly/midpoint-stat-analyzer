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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class Test {

	public static void main(String[] args) {

		Pattern p = Pattern.compile("(?<timestamp>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3}) \\[(?<thread>\\S+)] (?<level>\\S+)(?: \\((?<logger>\\S+)\\))?: (?<message>.*)");
		Matcher m1 = p.matcher("2019-05-27 09:42:11,230 [midPointScheduler_Worker-6] DEBUG: #### Entry: 83329 ...model.impl.sync.SynchronizationServiceImpl->notifyChange");
		Matcher m2 = p.matcher("2019-05-29 16:43:51,904 [pool-1-thread-1] DEBUG (PROFILING): ##### Exit: 817268    ...repo.sql.SqlRepositoryServiceImpl->getObject etime: 7.708 ms");

		System.out.println("m1: " + m1.matches() + ": " + dump(m1));
		System.out.println("m2: " + m2.matches() + ": " + dump(m2));
	}

	private static String dump(Matcher m) {
		StringBuilder sb = new StringBuilder();
		for (int i = 1; i < m.groupCount(); i++) {
			sb.append(i).append("=[").append(m.group(i)).append("] ");
		}
		sb.append(" ||| ts=");
		sb.append(m.group("timestamp"));
		sb.append(" ||| thr=");
		sb.append(m.group("thread"));
		sb.append(" ||| l=");
		sb.append(m.group("level"));
		sb.append(" ||| logger=");
		sb.append(m.group("logger"));
		sb.append(" ||| m=");
		sb.append(m.group("message"));
		return sb.toString();
	}

}
