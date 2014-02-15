package main.log;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class SimpleLogFormatter extends Formatter {
	@Override
	public String format(LogRecord record) {
		return formatMessage(record) + "\r\n";
	}
}
