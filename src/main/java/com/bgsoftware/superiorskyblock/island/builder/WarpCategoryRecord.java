package com.bgsoftware.superiorskyblock.island.builder;

import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;

public class WarpCategoryRecord {

    public final String name;
    public final int slot;
    @Nullable
    public final ItemStack icon;

    public WarpCategoryRecord(String name, int slot, @Nullable ItemStack icon) {
        this.name = name;
        this.slot = slot;
        this.icon = icon;
    }

}
