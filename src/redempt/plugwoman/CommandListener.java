package redempt.plugwoman;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.SimplePluginManager;
import redempt.redlib.commandmanager.CommandHook;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CommandListener {
	
	private SimplePluginManager manager = (SimplePluginManager) Bukkit.getPluginManager();
	private Map<CommandSender, List<Plugin>> confirm = new HashMap<>();
	
	@CommandHook("enable")
	public void enablePlugin(CommandSender sender, Plugin[] plugins) {
		for (Plugin plugin : plugins) {
			manager.enablePlugin(plugin);
			sender.sendMessage(ChatColor.GREEN + "Plugin " + plugin.getName() + " enabled!");
		}
	}
	
	@CommandHook("disable")
	public void disablePlugin(CommandSender sender, Plugin[] plugins) {
		for (Plugin plugin : plugins) {
			manager.disablePlugin(plugin);
			sender.sendMessage(ChatColor.GREEN + "Plugin " + plugin.getName() + " disabled!");
		}
	}
	
	@CommandHook("unload")
	public void unloadPlugin(CommandSender sender, Plugin[] plugins) {
		for (Plugin plugin : plugins) {
			PlugWoman.getInstance().unloadPlugin(plugin);
			PlugWoman.getInstance().syncCommands();
			sender.sendMessage(ChatColor.GREEN + "Plugin " + plugin.getName() + " unloaded!");
		}
	}
	
	@CommandHook("reloadcommands")
	public void reloadCommands(CommandSender sender) {
		PlugWoman.getInstance().syncCommands();
		sender.sendMessage(ChatColor.GREEN + "Server commands reloaded!");
	}
	
	@CommandHook("load")
	public void loadPlugin(CommandSender sender, Path[] paths) {
		for (Path path : paths) {
			if (!Files.exists(path) || !path.toString().endsWith(".jar")) {
				sender.sendMessage(ChatColor.RED + "No such jar: " + path.getFileName().toString());
				return;
			}
			String msg = PlugWoman.getInstance().loadPlugin(path).orElse(null);
			if (msg == null) {
				msg = ChatColor.GREEN + "Jar " + path.getFileName().toString() + " loaded!";
			} else {
				msg = ChatColor.RED + msg;
			}
			PlugWoman.getInstance().syncCommands();
			sender.sendMessage(msg);
		}
	}
	
	@CommandHook("reload")
	public void reload(CommandSender sender, Plugin[] pluginArr, boolean nodeep, boolean noconfirm) {
		PluginJarCache.clear();
		List<Plugin> plugins;
		if (!nodeep) {
			plugins = PlugWoman.getInstance().getDeepReload(pluginArr);
		} else {
			plugins = new ArrayList<>();
			Collections.addAll(plugins, pluginArr);
		}
		String list = plugins.stream().map(Plugin::getName).collect(Collectors.joining(", "));
		sender.sendMessage(ChatColor.GREEN + "Plugins to reload: " + ChatColor.YELLOW + list);
		if (!noconfirm) {
			sender.sendMessage(ChatColor.GREEN + "Run /plug confirm to confirm reload");
			sender.sendMessage(ChatColor.RED + "This confirmation will expire in 30 seconds");
			confirm.put(sender, plugins);
			Bukkit.getScheduler().scheduleSyncDelayedTask(PlugWoman.getInstance(), () -> {
				confirm.remove(sender, plugins);
			}, 20 * 30);
		} else {
			confirm.put(sender, plugins);
			confirm(sender);
		}
	}
	
	@CommandHook("confirm")
	public void confirm(CommandSender sender) {
		List<Plugin> plugins = confirm.remove(sender);
		if (plugins == null) {
			sender.sendMessage(ChatColor.RED + "You have not queued a reload!");
			return;
		}
		Map<Plugin, String> errors = PlugWoman.getInstance().reloadPlugins(plugins);
		if (errors.size() == 0) {
			sender.sendMessage(ChatColor.GREEN + "All plugins reloaded successfully!");
			return;
		}
		sender.sendMessage(ChatColor.RED + "The following plugins could not be reloaded:");
		errors.forEach((k, v) -> {
			sender.sendMessage(ChatColor.RED + k.getName() + ChatColor.YELLOW + ": " + v);
		});
	}
	
	@CommandHook("delcmd")
	public void unregisterCommand(CommandSender sender, String[] commands) {
		for (String command : commands) {
			if (command.equals("plug")) {
				sender.sendMessage(ChatColor.RED + "You cannot disable /plug!");
				continue;
			}
			PlugWoman.getInstance().getCommandMap().remove(command);
		}
		sender.sendMessage(ChatColor.GREEN + "Commands unregistered!");
		if (sender instanceof Player) {
			((Player) sender).updateCommands();
		}
	}
	
}
