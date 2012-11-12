import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author lanbo <br>
 * @version 1.0 <br>
 * @date 2012-11-12
 */
public class LogAnalyzer {

	private static final String				LOG_PATTNER	= "(\\d{4}-\\d{2}-\\d{1,2} \\d{1,2}:\\d{1,2}:\\d{1,2})\\Q \\E([A-Z]{4,5})\\Q \\E(.*?)\\Q - \\E(.*)";

	// private static final Pattern pattern = Pattern.compile(LOG_PATTNER);

	private static final Queue<LogEntry>	entries		= new ConcurrentLinkedQueue<LogEntry>();

	private static final Queue<String>		files		= new ConcurrentLinkedQueue<String>();

	private static AtomicInteger			threadCnt	= new AtomicInteger(0);

	private static String					seperator	= System.getProperty("file.separator");

	static abstract class LogEntry {
		String	bizId;
		String	time;
		String	using;
		String	succ;
	}

	static class LogReduceEntry extends LogEntry {
		String	subId;
		String	user;
	}

	static class LogWithholdEntry extends LogEntry {
		String	enough;
	}

	static class LogFlusher implements Runnable {
		BufferedWriter	reduceWriter	= null;
		BufferedWriter	withholdWriter	= null;

		public LogFlusher(String base) throws UnsupportedEncodingException, FileNotFoundException {
			reduceWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(base + "reduce.csv"), "gb2312"));
			withholdWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(base + "withhold.csv"), "gb2312"));
		}

		public void start() {
			new Thread(this).start();
		}

		@Override
		public void run() {
			LogEntry entry = null;
			while (true) {
				entry = entries.poll();
				if (entry == null) {
					if (threadCnt.get() == 0) {
						break;
					}

					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
					}
				} else {
					try {
						if (entry instanceof LogReduceEntry) {
							LogReduceEntry re = (LogReduceEntry) entry;
							reduceWriter.append(re.bizId + "," + re.subId + "," + re.time + "," + re.using + "," + re.succ + "\n");
							reduceWriter.flush();
						} else {
							LogWithholdEntry we = (LogWithholdEntry) entry;
							withholdWriter.append(we.bizId + "," + we.time + "," + we.using + "," + we.succ + "," + we.enough + "\n");
							withholdWriter.flush();
						}
					} catch (IOException e) {
					}
				}
			}
		}
	}

	static class LogFormatter {
		BufferedReader	reader			= null;

		String			reduceKey		= "reduce";
		String			withholdKey		= "withhold";

		Pattern			reduceRegex		= Pattern
												.compile("(\\d{4}-\\d{2}-\\d{1,2}) (\\d{1,2}:\\d{1,2}:\\d{1,2}) (.*? - Final-reduce~IA .*?,tc,)(\\d{1,5})ms\\] orderid=(\\d*) user_id=(\\d*) order_sub_ids=(\\d*)/ \\[(T|F->.*)\\]");
		Pattern			withholdRegex	= Pattern
												.compile("(\\d{4}-\\d{2}-\\d{1,2}) (\\d{1,2}:\\d{1,2}:\\d{1,2}) (.*? - P1-WithholdInventory-Batch .*?,tc-alipay,)(\\d{1,5})ms\\] orderid=(\\d*) \\[(T|F->.*)\\] ,(Enough|Not-Enough)");

		public LogFormatter(String filepath) throws UnsupportedEncodingException, FileNotFoundException {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(filepath), "gb2312"));
		}

		public void execute() {
			threadCnt.incrementAndGet();

			// 日志内容行
			String line = null;
			try {
				line = reader.readLine();
			} catch (IOException e1) {
			}

			while (line != null) {
				Matcher matcher = reduceRegex.matcher(line);

				if (matcher.find()) {
					LogReduceEntry entry = new LogReduceEntry();
					entry.time = matcher.group(2);
					entry.bizId = matcher.group(5);
					entry.subId = matcher.group(7);
					entry.succ = matcher.group(8);
					entry.user = matcher.group(6);
					entry.using = matcher.group(4);
					entries.add(entry);
				} else {
					matcher = withholdRegex.matcher(line);
					if (matcher.find()) {
						LogWithholdEntry entry = new LogWithholdEntry();
						entry.time = matcher.group(2);
						entry.using = matcher.group(4);
						entry.bizId = matcher.group(5);
						entry.succ = matcher.group(6);
						entry.enough = matcher.group(7);
						entries.add(entry);
					}
				}

				try {
					line = reader.readLine();
				} catch (IOException e) {
				}
			}

			try {
				reader.close();
			} catch (IOException e) {
			}

			threadCnt.decrementAndGet();
		}

	}

	static class LogFormatTask implements Runnable {

		private String	baseDir;

		public LogFormatTask(String baseDir) {
			this.baseDir = baseDir;
		}

		@Override
		public void run() {
			while (true) {
				String filename = files.poll();
				if (filename == null) {
					break;
				}

				try {
					new LogFormatter(baseDir + seperator + filename).execute();
				} catch (Exception e) {
				}
			}
		}

	}

	public static void main(String[] args) throws IOException {
		realMain(args);

		// demo(args);
		// regex();
	}

	public static void realMain(String[] args) throws UnsupportedEncodingException, FileNotFoundException {
		File folder = null;
		if (args.length == 0) {
			folder = new File(".");
		} else {
			folder = new File(args[0]);
		}

		String[] list = folder.list();
		for (String filename : list) {
			if (filename.contains("inventoryplatform")) {
				files.add(filename);
			}
		}
		
		int cnt = 5;
		if(args.length > 1) {
			cnt = Integer.valueOf(args[1]);
		}
		for (int i = 0; i < cnt; i++) {
			new Thread(new LogFormatTask(folder.getAbsolutePath())).start();
		}

		File outputFolder = new File(folder.getAbsolutePath() + seperator + "csv" + seperator);
		if (!outputFolder.exists()) {
			outputFolder.mkdir();
		}

		new LogFlusher(folder.getAbsolutePath() + seperator + "csv" + seperator).start();
	}

	public static void regex() {
		String log = "2012-11-11 11:00:40 WARN IPM-Trade - P1-WithholdInventory-Batch [172.24.63.71,tc-alipay,8ms] orderid=171446933883619 [T] ,Not-Enough";
		String regex = "(\\d{4}-\\d{2}-\\d{1,2}) (\\d{1,2}:\\d{1,2}:\\d{1,2}) (.*? - P1-WithholdInventory-Batch .*?,tc-alipay,)(\\d{1,5})ms\\] orderid=(\\d*) \\[(T|F->.*)\\] ,(Enough|Not-Enough)";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(log);
		if (matcher.find()) {
			int groupCount = matcher.groupCount();
			int i = 1;
			while (i <= groupCount) {
				System.out.println(matcher.group(i++));
			}
		}
	}

	public static void demo(String[] args) throws UnsupportedEncodingException, FileNotFoundException, IOException {
		if (args.length < 3) {
			System.out.println("Usage: java LogAnalyzer filepath output matchedKey1 matchedKey2");
			return;
		}

		// 日志行
		String logLine = null;
		boolean matched = false;

		Pattern pattern = Pattern.compile(LOG_PATTNER);

		// 输入文件路径
		String filePath = args[0];

		// 输出文件路径
		String output = null;
		if ("default".equalsIgnoreCase(args[1])) {
			output = filePath + ".output";
		} else {
			output = args[1];
		}

		// 匹配关键字
		String[] matchedKeys = new String[args.length - 2];

		for (int i = 2, j = 0; i < args.length; i++, j++) {
			matchedKeys[j] = args[i];
		}

		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), "gb2312"));
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), "gb2312"));

		// 日志内容行
		String line = reader.readLine();
		while (line != null) {
			Matcher matcher = pattern.matcher(line);
			if (matcher.find()) {
				if (matched) {
					// 输出到文件
					writer.append(logLine);
				}

				logLine = line;

				for (String key : matchedKeys) {
					matched = line.contains(key);
					if (!matched)
						break;
				}
			} else {
				if (!matched) {
					for (String key : matchedKeys) {
						matched = line.contains(key);
						if (!matched)
							break;
					}
				}

				logLine += line + "\n";
			}

			line = reader.readLine();
		}

		reader.close();

		writer.close();
	}
}
