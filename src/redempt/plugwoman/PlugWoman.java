package redempt.plugwoman;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommandYamlParser;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.event.Listener;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import redempt.ordinate.command.ArgType;
import redempt.ordinate.spigot.SpigotCommandManager;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiConsumer;

public class PlugWoman extends JavaPlugin implements Listener {
	
	private SimplePluginManager manager = (SimplePluginManager) Bukkit.getPluginManager();
	private Map<String, org.bukkit.command.Command> commandMap;
	
	public static PlugWoman getInstance() {
		return JavaPlugin.getPlugin(PlugWoman.class);
	}
	
	@Override
	public void onEnable() {
		getServer().getPluginManager().registerEvents(this, this);
		SpigotCommandManager.getInstance(this).loadMessages()
				.getParser().setHookTargets(new CommandListener())
				.addArgTypes(
						new ArgType<>("plugin", Bukkit.getPluginManager()::getPlugin)
								.completerStream((c, s) -> Arrays.stream(Bukkit.getPluginManager().getPlugins()).map(Plugin::getName)),
						new ArgType<>("jar", s -> Paths.get("plugins").resolve(s))
								.completerStream((c, str) -> {
									try {
										return Files.list(Paths.get("plugins")).map(p -> p.getFileName().toString()).filter(s -> s.endsWith(".jar"));
									} catch (IOException e) {
										e.printStackTrace();
										return null;
									}
								}),
						new ArgType<>("command", s -> commandMap.containsKey(s) ? s : null).completerStream((c, s) -> commandMap.keySet().stream())
				).parse(getResource("command.ordn")).register();
		PluginEnableErrorHandler.register();
		Field commandMapField;
		try {
			commandMapField = manager.getClass().getDeclaredField("commandMap");
			commandMapField.setAccessible(true);
			SimpleCommandMap commandMap = (SimpleCommandMap) commandMapField.get(manager);
			Class<?> clazz = commandMap.getClass();
			while (!clazz.getSimpleName().equals("SimpleCommandMap")) {
				clazz = clazz.getSuperclass();
			}
			Field mapField = clazz.getDeclaredField("knownCommands");
			mapField.setAccessible(true);
			this.commandMap = (Map<String, org.bukkit.command.Command>) mapField.get(commandMap);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			e.printStackTrace();
		}
	}
	
	public Map<String, org.bukkit.command.Command> getCommandMap() {
		return commandMap;
	}
	
	public Map<Plugin, List<org.bukkit.command.Command>> getCommandsByPlugin() {
		Map<Plugin, List<org.bukkit.command.Command>> map = new HashMap<>();
		commandMap.values().stream().filter(PluginIdentifiableCommand.class::isInstance).forEach(c -> {
			Plugin plugin = ((PluginIdentifiableCommand) c).getPlugin();
			map.computeIfAbsent(plugin, k -> new ArrayList<>()).add(c);
		});
		return map;
	}
	
	@Override
	public void onDisable() {
		new Thread(() -> {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			PluginEnableErrorHandler.unregister();
		}).start();
	}
	
	public Map<Plugin, Set<Plugin>> getDependencyMap() {
		Map<Plugin, Set<Plugin>> map = new HashMap<>();
		for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
			PluginDescriptionFile description = plugin.getDescription();
			Set<Plugin> set = new HashSet<>();
			for (String depend : description.getDepend()) {
				set.add(Bukkit.getPluginManager().getPlugin(depend));
			}
			for (String depend : description.getSoftDepend()) {
				set.add(Bukkit.getPluginManager().getPlugin(depend));
			}
			map.put(plugin, set);
		}
		return map;
	}
	
	public void unloadPlugin(Plugin plugin, Map<Plugin, List<org.bukkit.command.Command>> commandsByPlugin) {
		manager.disablePlugin(plugin);
		try {
			List<Command> commands = PluginCommandYamlParser.parse(plugin);
			commands.addAll(commandsByPlugin.getOrDefault(plugin, new ArrayList<>()));
			commands.forEach(c -> {
				commandMap.remove(c.getName());
				c.getAliases().forEach(commandMap::remove);
			});
			if (Version.MID_VERSION >= 9) {
				List<NamespacedKey> toRemove = new ArrayList<>();
				Bukkit.recipeIterator().forEachRemaining(recipe -> {
					if (recipe instanceof Keyed) {
						NamespacedKey key = ((Keyed) recipe).getKey();
						if (key.getNamespace().equalsIgnoreCase(plugin.getName())) {
							toRemove.add(key);
						}
					}
				});
				toRemove.forEach(Bukkit::removeRecipe);
			}
			List<Plugin> plugins = (List<Plugin>) accessible(manager.getClass().getDeclaredField("plugins")).get(manager);
			plugins.remove(plugin);
			Map<String, Plugin> lookupNames = (Map<String, Plugin>) accessible(manager.getClass().getDeclaredField("lookupNames")).get(manager);;
			lookupNames.remove(plugin.getName());
			URLClassLoader loader = (URLClassLoader) plugin.getClass().getClassLoader();
			if (Version.MID_VERSION < 13) {
				accessible(loader.getClass().getDeclaredField("plugin")).set(loader, null);
				accessible(loader.getClass().getDeclaredField("pluginInit")).set(loader, null);
				loader.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private Field accessible(Field field) {
		field.setAccessible(true);
		return field;
	}
	
	public Optional<String> loadPlugin(Path path) {
		try {
			if (path == null || !Files.exists(path)) {
				return Optional.of("Plugin jar does not exist");
			}
			Plugin plugin = Bukkit.getPluginManager().loadPlugin(path.toFile());
			try {
				plugin.onLoad();
				String[] err = {null};
				BiConsumer<String, Throwable> listener = (s, t) -> {
					if (s.equals(plugin.getName())) {
						StringBuilder builder = new StringBuilder("Error on enable: ").append(t.getClass().getSimpleName());
						Throwable cause = t.getCause();
						while (cause != null) {
							builder.append(" -> " + cause.getClass().getSimpleName());
							cause = cause.getCause();
						}
						err[0] = builder.toString();
					}
				};
				PluginEnableErrorHandler.addListener(listener);
				Bukkit.getPluginManager().enablePlugin(plugin);
				PluginEnableErrorHandler.removeListener(listener);
				if (err[0] != null) {
					return Optional.of(err[0]);
				}
			} catch (Exception e) {
				e.printStackTrace();
				return Optional.of("Plugin could not be enabled");
			}
			return Optional.empty();
		} catch (UnknownDependencyException e) {
			e.printStackTrace();
			return Optional.of("Plugin missing dependency");
		} catch (InvalidDescriptionException e) {
			e.printStackTrace();
			return Optional.of("Plugin has invalid plugin.yml");
		} catch (InvalidPluginException e) {
			e.printStackTrace();
			return Optional.of("Plugin is invalid");
		}
	}
	
	public List<Plugin> getDeepReload(Plugin[] p) {
		Map<Plugin, Set<Plugin>> map = getDependencyMap();
		HashSet<Plugin> set = new HashSet<>();
		List<Plugin> toReload = new ArrayList<>();
		Collections.addAll(set, p);
		Collections.addAll(toReload, p);
		for (int i = 0; i < toReload.size(); i++) {
			Plugin plugin = toReload.get(i);
			map.forEach((k, v) -> {
				if (v.contains(plugin)) {
					if (set.add(k)) {
						toReload.add(k);
					}
				}
			});
		}
		boolean swap = false;
		do {
			swap = false;
			for (int i = 0; i < toReload.size(); i++) {
				Plugin plugin = toReload.get(i);
				for (Plugin depend : map.get(plugin)) {
					int first = toReload.indexOf(depend);
					if (first > i) {
						if (map.get(depend).contains(plugin) && map.get(plugin).contains(depend)) {
							continue;
						}
						toReload.set(first, plugin);
						toReload.set(i, depend);
						swap = true;
					}
				}
			}
		} while (swap);
		return toReload;
	}
	
	public void syncCommands() {
		if (Version.MID_VERSION >= 13) {
			try {
				Method method = getServer().getClass().getDeclaredMethod("syncCommands");
				method.setAccessible(true);
				method.invoke(getServer());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	public Map<Plugin, String> reloadPlugins(List<Plugin> plugins) {
		Map<Plugin, List<org.bukkit.command.Command>> commandsByPlugin = getCommandsByPlugin();
		for (int i = plugins.size() - 1; i >= 0; i--) {
			unloadPlugin(plugins.get(i), commandsByPlugin);
		}
		Map<Plugin, String> errors = new HashMap<>();
		for (Plugin plugin : plugins) {
			loadPlugin(PluginJarCache.getJarPath(plugin)).ifPresent(s -> errors.put(plugin, s));
		}
		syncCommands();
		return errors;
	}
	
}
