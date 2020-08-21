package redempt.plugwoman;

import org.bukkit.Bukkit;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class PluginEnableErrorHandler {
	
	private static Handler handler;
	private static Set<BiConsumer<String, Throwable>> pluginErrorListeners = new HashSet<>();
	
	public static void register() {
		handler = new Handler() {
			public void publish(LogRecord record) {
				if (record.getLevel() == Level.SEVERE) {
					String msg = record.getMessage();
					if (msg.startsWith("Error occurred while enabling ")) {
						String name = msg.substring(30);
						String fname = name.substring(0, name.indexOf(' '));
						pluginErrorListeners.forEach(c -> {
							c.accept(fname, record.getThrown());
						});
					}
				}
			}
			public void flush() {}
			public void close() throws SecurityException {}
		};
		Bukkit.getServer().getLogger().addHandler(handler);
	}
	
	public static void unregister() {
		Bukkit.getServer().getLogger().removeHandler(handler);
	}
	
	public static void addListener(BiConsumer<String, Throwable> listener) {
		pluginErrorListeners.add(listener);
	}
	
	public static void removeListener(BiConsumer<String, Throwable> listener) {
		pluginErrorListeners.remove(listener);
	}
	
}
