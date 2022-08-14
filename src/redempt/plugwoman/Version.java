package redempt.plugwoman;

import org.bukkit.Bukkit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Version {
	
	public static final int MID_VERSION = getMidVersion();
	
	private static int getMidVersion() {
		Pattern pattern = Pattern.compile("1\\.([0-9]+)");
		Matcher matcher = pattern.matcher(Bukkit.getBukkitVersion());
		matcher.find();
		return Integer.parseInt(matcher.group(1));
	}
	
}
