package redempt.plugwoman;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommandYamlParser;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;

import redempt.redlib.commandmanager.ArgType;
import redempt.redlib.commandmanager.CommandHook;
import redempt.redlib.commandmanager.CommandParser;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Main extends JavaPlugin implements Listener {
	
	private SimplePluginManager manager = (SimplePluginManager) Bukkit.getPluginManager();
	
	@Override
	public void onEnable() {
		getServer().getPluginManager().registerEvents(this, this);
		new CommandParser(this.getResource("command.txt"))
				.setArgTypes(new ArgType<>("plugin", Bukkit.getPluginManager()::getPlugin)
								.tabStream(s -> Arrays.stream(Bukkit.getPluginManager().getPlugins()).map(Plugin::getName)),
						new ArgType<>("jar", s -> Paths.get("plugins").resolve(s))
								.tabStream(c -> {
									try {
										return Files.list(Paths.get("plugins")).map(p -> p.getFileName().toString()).filter(s -> s.endsWith(".jar"));
									} catch (IOException e) {
										e.printStackTrace();
										return null;
									}
								}))
				.parse().register("plugwoman", this);
	}
	
	@CommandHook("enable")
	public void enablePlugin(CommandSender sender, Plugin plugin) {
		manager.enablePlugin(plugin);
		sender.sendMessage(ChatColor.GREEN + "Plugin enabled!");
	}
	
	@CommandHook("disable")
	public void disablePlugin(CommandSender sender, Plugin plugin) {
		manager.disablePlugin(plugin);
		sender.sendMessage(ChatColor.GREEN + "Plugin disabled!");
	}
	
	@CommandHook("unload")
	public void unloadPlugin(CommandSender sender, Plugin plugin) {
		unloadPlugin(plugin);
		sender.sendMessage(ChatColor.GREEN + "Plugin unloaded!");
	}
	
	@CommandHook("load")
	public void loadPlugin(CommandSender sender, Path path) {
		if (!Files.exists(path) || !path.toString().endsWith(".jar")) {
			sender.sendMessage(ChatColor.RED + "No such jar!");
			return;
		}
		try {
			Plugin plugin = Bukkit.getPluginManager().loadPlugin(path.toFile());
			Bukkit.getPluginManager().enablePlugin(plugin);
			sender.sendMessage(ChatColor.GREEN + "Plugin loaded!");
		} catch (UnknownDependencyException | InvalidPluginException | InvalidDescriptionException e) {
			e.printStackTrace();
			sender.sendMessage(ChatColor.RED + "The plugin could not be loaded: " + e.getMessage());
		}
	}
	
	@CommandHook("reload")
	public void reload(CommandSender sender, Plugin plugin) {
		if (reloadPlugin(plugin)) {
			sender.sendMessage(ChatColor.GREEN + "Plugin reloaded!");
		} else {
			sender.sendMessage(ChatColor.RED + "The plugin could not be reloaded");
		}
	}
	
	private void unloadPlugin(Plugin plugin) {
		manager.disablePlugin(plugin);
		try {
			Field commandMapField = manager.getClass().getDeclaredField("commandMap");
			commandMapField.setAccessible(true);
			SimpleCommandMap commandMap = (SimpleCommandMap) commandMapField.get(manager);
			Class<?> clazz = commandMap.getClass();
			while (!clazz.getSimpleName().equals("SimpleCommandMap")) {
				clazz = clazz.getSuperclass();
			}
			Field mapField = clazz.getDeclaredField("knownCommands");
			mapField.setAccessible(true);
			Map<String, Command> knownCommands = (Map<String, org.bukkit.command.Command>) mapField.get(commandMap);
			List<Command> commands = PluginCommandYamlParser.parse(plugin);
			PluginDescriptionFile f;
			for (org.bukkit.command.Command command : commands) {
				knownCommands.remove(command.getName());
			}
			Iterator<Recipe> iterator = Bukkit.recipeIterator();
			while (iterator.hasNext()) {
				Recipe recipe = iterator.next();
				if (recipe instanceof Keyed) {
					NamespacedKey key = ((Keyed) recipe).getKey();
					if (key.getNamespace().equalsIgnoreCase(plugin.getName())) {
						iterator.remove();
					}
				}
			}
			Field pluginsField = manager.getClass().getDeclaredField("plugins");
			pluginsField.setAccessible(true);
			List<Plugin> plugins = (List<Plugin>) pluginsField.get(manager);
			plugins.remove(plugin);
			Field loadersField = manager.getClass().getDeclaredField("fileAssociations");
			loadersField.setAccessible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private boolean loadPlugin(Path path) {
		try {
			Plugin plugin = Bukkit.getPluginManager().loadPlugin(path.toFile());
			plugin.onLoad();
			Bukkit.getPluginManager().enablePlugin(plugin);
			return true;
		} catch (UnknownDependencyException | InvalidPluginException | InvalidDescriptionException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	private boolean reloadPlugin(Plugin plugin) {
		unloadPlugin(plugin);
		try {
			return loadPlugin(Paths.get(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()));
		} catch (URISyntaxException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	private PluginDescriptionFile getDescription(Plugin plugin) {
		try {
			return new PluginDescriptionFile(plugin.getResource("plugin.yml"));
		} catch (InvalidDescriptionException e) {
			e.printStackTrace();
			return null;
		}
	}
	
}
