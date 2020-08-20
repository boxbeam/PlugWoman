package redempt.plugwoman;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommandYamlParser;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import redempt.redlib.commandmanager.ArgType;
import redempt.redlib.commandmanager.CommandHook;
import redempt.redlib.commandmanager.CommandParser;
import redempt.redlib.misc.ChatPrompt;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
			loadPlugin(path);
			sender.sendMessage(ChatColor.GREEN + "Plugin loaded!");
		} catch (Exception e) {
			e.printStackTrace();
			sender.sendMessage(ChatColor.RED + "The plugin could not be loaded: " + e.getMessage());
		}
	}
	
	@CommandHook("reload")
	public void deepReload(Player sender, Plugin plugin, boolean deep) throws InvalidDescriptionException {
		List<Plugin> plugins;
		if (deep) {
			 plugins = getDeepReload(plugin);
		} else {
			plugins = new ArrayList<>();
			plugins.add(plugin);
		}
		String list = plugins.stream().map(Plugin::getName).collect(Collectors.joining(", "));
		sender.sendMessage(ChatColor.GREEN + "Plugins to reload: " + ChatColor.YELLOW + list);
		ChatPrompt.prompt(sender, ChatColor.GREEN + "Type 'confirm' to confirm reload", s -> {
			if (!s.equals("confirm")) {
				sender.sendMessage(ChatColor.RED + "Reload cancelled.");
				return;
			}
			Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
				deepReloadPlugin(plugins, b -> {
					if (b) {
						sender.sendMessage(ChatColor.GREEN + "Plugins reloaded!");
					} else {
						sender.sendMessage(ChatColor.RED + "There was an error while performing a deep reload");
					}
				});
			});
		}, r -> {
			sender.sendMessage(ChatColor.RED + "Reload cancelled.");
		});
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
			for (Plugin plug : Bukkit.getPluginManager().getPlugins()) {
				if (plug.equals(plugin)) {
					continue;
				}
				ClassLoader loader = plug.getClass().getClassLoader();
			}
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
	
	private List<Plugin> getDeepReload(Plugin p) {
		HashSet<Plugin> set = new HashSet<>();
		List<Plugin> toReload = new ArrayList<>();
		set.add(p);
		toReload.add(p);
		for (int pos = 0; pos < toReload.size(); pos++) {
			for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
				PluginDescriptionFile description = getDescription(plugin);
				if (description == null) {
					continue;
				}
				if (description.getSoftDepend().stream().map(Bukkit.getPluginManager()::getPlugin).anyMatch(set::contains)
						|| description.getDepend().stream().map(Bukkit.getPluginManager()::getPlugin).anyMatch(set::contains)) {
					if (set.add(plugin)) {
						toReload.add(plugin);
					}
				}
			}
		}
		return toReload;
	}
	
	private void deepReloadPlugin(List<Plugin> plugins, Consumer<Boolean> result) {
		for (int i = plugins.size() - 1; i >= 0; i--) {
			unloadPlugin(plugins.get(i));
		}
//		for (Plugin plugin : plugins) {
//			unloadPlugin(plugin);
//		}
		for (Plugin plugin : plugins) {
			try {
				if (!loadPlugin(Paths.get(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()))) {
					result.accept(false);
					return;
				}
			} catch (URISyntaxException e) {
				e.printStackTrace();
				result.accept(false);
				return;
			}
		}
		result.accept(true);
	}
	
	private PluginDescriptionFile getDescription(Plugin plugin) {
		try {
			InputStream stream = plugin.getResource("plugin.yml");
			if (stream == null) {
			return null;
			}
			return new PluginDescriptionFile(stream);
		} catch (InvalidDescriptionException e) {
			e.printStackTrace();
			return null;
		}
	}
	
}
