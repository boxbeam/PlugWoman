package redempt.plugwoman;

import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class PluginJarCache {
	
	private static Map<String, Path> pluginMap = null;
	
	public static void populate() {
		pluginMap = new HashMap<>();
		try {
			Files.list(Paths.get("plugins")).forEach(p -> {
				if (!p.toString().endsWith(".jar")) {
					return;
				}
				try {
					JarFile jar = new JarFile(p.toFile());
					ZipEntry entry = jar.getEntry("plugin.yml");
					if (entry == null) {
						return;
					}
					InputStream stream = jar.getInputStream(entry);
					PluginDescriptionFile description = new PluginDescriptionFile(stream);
					String name = description.getName();
					pluginMap.put(name, p);
				} catch (IOException | InvalidDescriptionException e) {
					e.printStackTrace();
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void clear() {
		pluginMap = null;
	}
	
	public static Path getJarPath(Plugin plugin) {
		try {
			Path path = Paths.get(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
			if (Files.exists(path)) {
				return path;
			}
			if (pluginMap != null) {
				return pluginMap.get(plugin.getName());
			}
			populate();
			return pluginMap.get(plugin.getName());
		} catch (URISyntaxException e) {
			e.printStackTrace();
			return null;
		}
	}
	
}
