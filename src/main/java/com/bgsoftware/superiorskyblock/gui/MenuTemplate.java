package com.bgsoftware.superiorskyblock.gui;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.utils.FileUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public enum MenuTemplate {

    PANEL("guis/panel/panel-gui.yml"),
    MEMBERS("guis/panel/members-gui.yml"),
    VISITORS("guis/panel/visitors-gui.yml"),
    MEMBER("guis/panel/member-gui.yml"),
    MEMBER_ROLE("guis/panel/member-role-gui.yml"),

    TOP_ISLANDS("guis/statistics/top-islands.yml"),
    ISLAND_VALUES("guis/statistics/island-values.yml"),

    ISLAND_CREATE("guis/island/island-create.yml"),
    ISLAND_BIOME("guis/island/island-biome.yml"),
    ISLAND_UPGRADES("guis/island/island-upgrades.yml"),

    WARPS("guis/warps/warps.yml"),
    ISLAND_WARPS("guis/warps/island-warps.yml");

    private static boolean legacy = !Bukkit.getBukkitVersion().contains("1.13") && !Bukkit.getBukkitVersion().contains("1.14");

    private static SuperiorSkyblockPlugin plugin = SuperiorSkyblockPlugin.getPlugin();

    private String path;

    private File file;

    MenuTemplate(String path) {
        this.path = path;
    }

    public YamlConfiguration getFile() {
        return YamlConfiguration.loadConfiguration(file);
    }

    private void load() {
        String path = this.path + "";
        if (legacy)
            path = path.replaceFirst("guis", "guis-legacy");

        file = new File(plugin.getDataFolder(), path);

        if (!file.exists())
            FileUtil.saveResource(path);
    }

    public static void loadAll() {
        for (MenuTemplate template : values()) {
            template.load();
        }
    }

}
