package com.bgsoftware.superiorskyblock.handlers;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.config.CommentedConfiguration;
import com.bgsoftware.superiorskyblock.gui.GUIInventory;
import com.bgsoftware.superiorskyblock.hooks.EconomyHook;
import com.bgsoftware.superiorskyblock.utils.FileUtil;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class UpgradesHandler {

    private SuperiorSkyblockPlugin plugin;
    private Map<String, UpgradeData> upgrades = new HashMap<>();

    private GUIInventory upgradesMenu;

    public UpgradesHandler(SuperiorSkyblockPlugin plugin){
        this.plugin = plugin;
        loadUpgrades();
    }

    public void openUpgradesMenu(SuperiorPlayer superiorPlayer){
        if(!Bukkit.isPrimaryThread()){
            new Thread(() -> openUpgradesMenu(superiorPlayer));
            return;
        }

        Inventory inventory = upgradesMenu.clonedInventory();

        Island island;

        for(String upgrade : upgrades.keySet()){
            int level = (island = superiorPlayer.getIsland()) == null ? 1 : island.getUpgradeLevel(upgrade);
            double nextLevelPrice = getUpgradePrice(upgrade, level);
            UpgradeData upgradeData = upgrades.get(upgrade);
            if(upgradeData.items.containsKey(level)) {
                ItemData itemData = upgradeData.items.get(level);

                inventory.setItem(itemData.slot, EconomyHook.getMoneyInBank(superiorPlayer) >= nextLevelPrice ?
                                itemData.hasNextLevel : itemData.noNextLevel);
            }
        }

        upgradesMenu.openInventory(superiorPlayer, inventory);
    }

    public String getTitle() {
        return upgradesMenu.getTitle();
    }

    public double getUpgradePrice(String upgradeName, int level){
        if(isUpgrade(upgradeName)){
            UpgradeData upgradeData = upgrades.get(upgradeName);

            level = Math.min(level, getMaxUpgradeLevel(upgradeName));

           return upgradeData.prices.get(level);
        }

        return 1;
    }

    public String getUpgrade(int slot){
        for(String _upgradeName : upgrades.keySet()) {
            for (ItemData itemData : upgrades.get(_upgradeName).items.values()) {
                if (slot == itemData.slot){
                    return _upgradeName;
                }
            }
        }

        return "";
    }

    public List<String> getUpgradeCommands(String upgradeName, int level){
        if(isUpgrade(upgradeName)){
            return upgrades.get(upgradeName).commands.getOrDefault(level, new ArrayList<>());
        }

        return new ArrayList<>();
    }

    public int getMaxUpgradeLevel(String upgradeName){
        if(!isUpgrade(upgradeName))
            return -1;

        UpgradeData upgradeData = upgrades.get(upgradeName);
        int maxLevel = 0;

        while (upgradeData.prices.containsKey(maxLevel + 1) && upgradeData.commands.containsKey(maxLevel + 1))
            maxLevel++;

        return maxLevel;
    }

    public boolean isUpgrade(String upgradeName){
        return upgrades.containsKey(upgradeName.toLowerCase());
    }

    public List<String> getAllUpgrades(){
        return new ArrayList<>(upgrades.keySet());
    }

    public Sound getClickSound(String upgradeName, int level, boolean hasNextLevel){
        UpgradeData upgradeData = upgrades.get(upgradeName);
        Sound sound = null;

        if(upgradeData.items.containsKey(level)){
            ItemData itemData = upgradeData.items.get(level);
            sound = hasNextLevel ? itemData.hasNextLevelSound : itemData.noNextLevelSound;
        }

        return sound;
    }

    private void loadUpgrades(){
        File file = new File(plugin.getDataFolder(), "upgrades.yml");

        if(!file.exists())
            plugin.saveResource("upgrades.yml", false);

        CommentedConfiguration cfg = new CommentedConfiguration(null, file);

        ConfigurationSection upgrades = cfg.getConfigurationSection("upgrades");

        for(String upgradeName : upgrades.getKeys(false)){
            UpgradeData upgradeData = new UpgradeData();
            for(String _level : upgrades.getConfigurationSection(upgradeName).getKeys(false)){
                int level = Integer.valueOf(_level);
                upgradeData.prices.put(level, upgrades.getDouble(upgradeName + "." + level + ".price"));
                upgradeData.commands.put(level, upgrades.getStringList(upgradeName + "." + level + ".commands"));
            }
            this.upgrades.put(upgradeName, upgradeData);
        }
    }

    private Sound getSound(String name){
        try{
            return Sound.valueOf(name);
        }catch(Exception ex){
            return null;
        }
    }

    private class UpgradeData{

        private Map<Integer, Double> prices = new HashMap<>();
        private Map<Integer, List<String>> commands = new HashMap<>();
        private Map<Integer, ItemData> items = new HashMap<>();

    }

    private class ItemData{

        private ItemStack hasNextLevel, noNextLevel;
        private int slot;
        private Sound hasNextLevelSound, noNextLevelSound;

        private ItemData(ItemStack hasNextLevel, ItemStack noNextLevel, int slot, Sound hasNextLevelSound, Sound noNextLevelSound){
            this.hasNextLevel = hasNextLevel;
            this.noNextLevel = noNextLevel;
            this.slot = slot;
            this.hasNextLevelSound = hasNextLevelSound;
            this.noNextLevelSound = noNextLevelSound;
        }

    }

}
