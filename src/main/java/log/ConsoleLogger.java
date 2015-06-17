package log;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConsoleLogger {
	public static final Logger LOGGER = Logger.getLogger(ConsoleLogger.class.getName());
	
	static {
		ConsoleHandler handler = new ConsoleHandler();
		handler.setFormatter(new SimpleLogFormatter());
		LOGGER.addHandler(handler);
		LOGGER.setUseParentHandlers(false);
		LOGGER.setLevel(Level.INFO);
	}
	
	public static Logger getLogger() {
		return LOGGER;
	}
}
