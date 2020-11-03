package redempt.plugwoman;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.SimplePluginManager;
import redempt.redlib.commandmanager.CommandHook;
import redempt.redlib.misc.ChatPrompt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CommandListener {
	
	private SimplePluginManager manager = (SimplePluginManager) Bukkit.getPluginManager();
	private Map<CommandSender, List<Plugin>> confirm = new HashMap<>();
	
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
		PlugWoman.getInstance().unloadPlugin(plugin);
		sender.sendMessage(ChatColor.GREEN + "Plugin unloaded!");
	}
	
	@CommandHook("load")
	public void loadPlugin(CommandSender sender, Path path) {
		if (!Files.exists(path) || !path.toString().endsWith(".jar")) {
			sender.sendMessage(ChatColor.RED + "No such jar!");
			return;
		}
		String msg = PlugWoman.getInstance().loadPlugin(path).orElse(null);
		if (msg == null) {
			msg = ChatColor.GREEN + "Plugin loaded!";
		} else {
			msg = ChatColor.RED + msg;
		}
		sender.sendMessage(msg);
	}
	
	@CommandHook("reload")
	public void reload(CommandSender sender, Plugin plugin, boolean nodeep, boolean noconfirm) {
		PluginJarCache.clear();
		List<Plugin> plugins;
		if (!nodeep) {
			plugins = PlugWoman.getInstance().getDeepReload(plugin);
		} else {
			plugins = new ArrayList<>();
			plugins.add(plugin);
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
	
}
