package com.cjburkey.bettersandmining;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import com.google.common.io.Files;
import com.google.gson.Gson;

public class BetterSandMining extends JavaPlugin {
	
	private static final Logger logger = Logger.getLogger("Minecraft");
	private static BetterSandMiningPlayerList list;
	private static BetterSandMining instance;
	
	public BetterSandMining() {
		instance = this;
	}
	
	public void onEnable() {
		log("Loading config");
		loadConfig();
		log("Loaded config");
		log("Adding event listener");
		registerEvent();
		log("Added event listener");
		log("Loading data");
		list = BetterSandMiningPlayerList.load();
		log("Loaded data");
	}
	
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (label.equals("bettersand")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage("Only in game players may use /bettersand");
				return true;
			}
			Player ply = (Player) sender;
			if (!ply.hasPermission("bettersandmining.toggle")) {
				ply.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("language.noPermToggle")));
				return false;
			}
			if (list.hasEnabled(ply.getUniqueId(), Material.SAND, getConfig().getBoolean("bettersand.enabledByDefault")) || list.hasEnabled(ply.getUniqueId(), Material.GRAVEL, getConfig().getBoolean("bettersand.enabledByDefault"))) {
				list.set(ply.getUniqueId(), false);
			} else {
				list.set(ply.getUniqueId(), true);
			}
			ply.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("language.toggled")));
			return true;
		}
		return false;
	}
	
	private void loadConfig() {
		getConfig().options().copyDefaults(true);
		saveConfig();
	}
	
	private void registerEvent() {
		getServer().getPluginManager().registerEvents(new BetterSandMiningBlockBreakEvent(), this);
	}
	
	private static BetterSandMining getInstance() {
		return instance;
	}
	
	private static void log(String msg) {
		logger.info("[BetterSandMining] " + msg);
	}
	
	private static void handleBreak(Player player, Location start, Material type) {
		World world = start.getWorld();
		Location loc = start.clone();
		handleSet(start.clone());
		if (getInstance().getConfig().getBoolean("bettersand.breakAbove")) {
			loc.setY(loc.getY() + 1);
			while (world.getBlockAt(loc).getType().equals(type)) {
				BlockBreakEvent event = new BlockBreakEvent(world.getBlockAt(loc), player);
				getInstance().getServer().getPluginManager().callEvent(event);
				handleSet(loc);
				loc.setY(loc.getY() + 1);
			}
		}
	}
	
	private static void handleSet(final Location loc) {
		World world = loc.getWorld();
		
		Location loce = loc.clone();
		loce.setX(loc.getX() + 1);
		Location locw = loc.clone();
		locw.setX(loc.getX() - 1);
		Location locn = loc.clone();
		locn.setZ(loc.getZ() + 1);
		Location locs = loc.clone();
		locs.setZ(loc.getZ() - 1);
		
		loc.getWorld().getBlockAt(loc).breakNaturally();
		if (getInstance().getConfig().getBoolean("bettersand.fillWater")) {
			boolean water = world.getBlockAt(loce).getType().equals(Material.STATIONARY_WATER) || world.getBlockAt(locw).getType().equals(Material.STATIONARY_WATER) || world.getBlockAt(locn).getType().equals(Material.STATIONARY_WATER) || world.getBlockAt(locs).getType().equals(Material.STATIONARY_WATER) || world.getBlockAt(loce).getType().equals(Material.WATER) || world.getBlockAt(locw).getType().equals(Material.WATER) || world.getBlockAt(locn).getType().equals(Material.WATER) || world.getBlockAt(locs).getType().equals(Material.WATER);
			if (water) {
				getInstance().getServer().getScheduler().scheduleSyncDelayedTask(getInstance(), () -> world.getBlockAt(loc).setType(Material.STATIONARY_WATER), 1);
			}
		}
	}
	
	private static class BetterSandMiningBlockBreakEvent implements Listener {
		
		private static int workingOnX = Integer.MAX_VALUE;
		private static int workingOnZ = Integer.MAX_VALUE;
		
		@EventHandler
		public void onBlockBroken(BlockBreakEvent e) {
			if (e.isCancelled() || e.getPlayer() == null || e.getBlock() == null || e.getBlock().getType() == null) {
				return;
			}
			Location loc = e.getBlock().getLocation();
			if (loc.getBlockX() == workingOnX && loc.getBlockZ() == workingOnZ) {
				return;	// We're already processing this event
			}
			if (BetterSandMining.getInstance().getConfig().getBoolean("bettersand.requireShovel")) {
				Material mainHand = e.getPlayer().getInventory().getItemInMainHand().getType();
				if (!(mainHand.equals(Material.WOOD_SPADE) || mainHand.equals(Material.STONE_SPADE) || mainHand.equals(Material.GOLD_SPADE) || mainHand.equals(Material.IRON_SPADE) || mainHand.equals(Material.DIAMOND_SPADE))) {
					return;	// Not using a shovel when one is required, handle this event like normal
				}
			}
			Material type = e.getBlock().getType();
			if ((type.equals(Material.SAND) && getCanExecute(e.getPlayer(), Material.SAND, "bettersandmining.sand")) || (type.equals(Material.GRAVEL) && getCanExecute(e.getPlayer(), Material.GRAVEL, "bettersandmining.gravel"))) {
				workingOnX = loc.getBlockX();
				workingOnX = loc.getBlockZ();
				BetterSandMining.handleBreak(e.getPlayer(), loc, e.getBlock().getType());
				workingOnX = Integer.MAX_VALUE;
				workingOnX = Integer.MAX_VALUE;
				return;
			}
		}
		
		private boolean getCanExecute(Player ply, Material material, String permission) {
			return list.hasEnabled(ply.getUniqueId(), material, BetterSandMining.getInstance().getConfig().getBoolean("bettersand.enabledByDefault")) && ply.hasPermission(permission);
		}
		
	}
	
	private static class BetterSandMiningPlayerList {
		
		private static Gson gson = new Gson();
		
		private Set<BetterSandMiningPlayerListEntry> players = new HashSet<>();
		
		private BetterSandMiningPlayerList() {
		}
		
		public static BetterSandMiningPlayerList load() {
			File f = getFile();
			if (!f.exists()) {
				return new BetterSandMiningPlayerList();
			}
			String in = null;
			try {
				in = Files.toString(f, Charset.forName("UTF-8"));
			} catch (IOException e) {
				log("Failed to read data.json");
				e.printStackTrace();
			}
			if (in == null) {
				return new BetterSandMiningPlayerList();
			}
			BetterSandMiningPlayerList built = gson.fromJson(in, BetterSandMiningPlayerList.class);
			if (built == null) {
				return new BetterSandMiningPlayerList(); 
			}
			return built;
		}
		
		public boolean hasEnabled(UUID player, Material block, boolean enabledDefault) {
			BetterSandMiningPlayerListEntry entry = getEntry(player);
			if (entry != null) {
				return entry.hasEnabled(block);
			}
			set(player, enabledDefault);
			return enabledDefault;
		}
		
		public void set(UUID player, boolean enabled) {
			BetterSandMiningPlayerListEntry entry = getEntry(player);
			if (entry != null) {
				entry.setEnabled(enabled);
				return;
			}
			entry = new BetterSandMiningPlayerListEntry(player, enabled);
			players.add(entry);
			try {
				// Just in case, make parent directories
				getFile().getParentFile().mkdirs();
				Files.write(gson.toJson(this), getFile(), Charset.forName("UTF-8"));
			} catch (IOException e) {
				log("Failed to write data.json");
				e.printStackTrace();
			}
		}
		
		private static File getFile() {
			return new File(BetterSandMining.getInstance().getDataFolder(), "data.json");
		}
		
		private BetterSandMiningPlayerListEntry getEntry(UUID player) {
			for (BetterSandMiningPlayerListEntry entry : players) {
				if (entry.player.equals(player)) {
					return entry;
				}
			}
			return null;
		}
		
	}
	
	private static class BetterSandMiningPlayerListEntry {
		
		public final UUID player;
		public final Set<Material> blocks = new HashSet<>();
		
		public BetterSandMiningPlayerListEntry(UUID player, boolean enabledDefault) {
			this.player = player;
			setEnabled(enabledDefault);
		}
		
		public void setEnabled(boolean enabled) {
			blocks.clear();
			if (enabled) {
				blocks.addAll(Arrays.asList(new Material[] { Material.SAND, Material.GRAVEL }));
			}
		}
		
		public boolean hasEnabled(Material mat) {
			return blocks.contains(mat);
		}
		
	}
	
}