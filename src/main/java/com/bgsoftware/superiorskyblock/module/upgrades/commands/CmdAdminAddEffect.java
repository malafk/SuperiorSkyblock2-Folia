package com.bgsoftware.superiorskyblock.module.upgrades.commands;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.commands.CommandTabCompletes;
import com.bgsoftware.superiorskyblock.commands.IAdminIslandCommand;
import com.bgsoftware.superiorskyblock.commands.arguments.CommandArguments;
import com.bgsoftware.superiorskyblock.commands.arguments.NumberArgument;
import com.bgsoftware.superiorskyblock.core.messages.Message;
import com.bgsoftware.superiorskyblock.core.formatting.Formatters;
import com.bgsoftware.superiorskyblock.core.events.EventResult;
import org.bukkit.command.CommandSender;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CmdAdminAddEffect implements IAdminIslandCommand {

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("addeffect");
    }

    @Override
    public String getPermission() {
        return "superior.admin.addeffect";
    }

    @Override
    public String getUsage(java.util.Locale locale) {
        return "admin addeffect <" +
                Message.COMMAND_ARGUMENT_PLAYER_NAME.getMessage(locale) + "/" +
                Message.COMMAND_ARGUMENT_ISLAND_NAME.getMessage(locale) + "/" +
                Message.COMMAND_ARGUMENT_ALL_ISLANDS.getMessage(locale) + "> <" +
                Message.COMMAND_ARGUMENT_EFFECT.getMessage(locale) + "> <" +
                Message.COMMAND_ARGUMENT_LEVEL.getMessage(locale) + ">";
    }

    @Override
    public String getDescription(java.util.Locale locale) {
        return Message.COMMAND_DESCRIPTION_ADMIN_ADD_EFFECT.getMessage(locale);
    }

    @Override
    public int getMinArgs() {
        return 5;
    }

    @Override
    public int getMaxArgs() {
        return 5;
    }

    @Override
    public boolean canBeExecutedByConsole() {
        return true;
    }

    @Override
    public boolean supportMultipleIslands() {
        return true;
    }

    @Override
    public void execute(SuperiorSkyblockPlugin plugin, CommandSender sender, SuperiorPlayer targetPlayer, List<Island> islands, String[] args) {
        PotionEffectType effectType = CommandArguments.getPotionEffect(sender, args[3]);

        if (effectType == null)
            return;

        NumberArgument<Integer> arguments = CommandArguments.getLevel(sender, args[4]);

        if (!arguments.isSucceed())
            return;

        int level = arguments.getNumber();

        boolean anyIslandChanged = false;

        for (Island island : islands) {
            int newLevel = island.getPotionEffectLevel(effectType) + level;
            if (newLevel <= 0) {
                if (plugin.getEventsBus().callIslandRemoveEffectEvent(sender, island, effectType)) {
                    anyIslandChanged = true;
                    island.removePotionEffect(effectType);
                }
            } else {
                EventResult<Integer> eventResult = plugin.getEventsBus().callIslandChangeEffectLevelEvent(sender, island, effectType, newLevel);
                anyIslandChanged |= !eventResult.isCancelled();
                if (!eventResult.isCancelled())
                    island.setPotionEffect(effectType, eventResult.getResult());
            }
        }

        if (!anyIslandChanged)
            return;

        if (islands.size() > 1)
            Message.CHANGED_ISLAND_EFFECT_LEVEL_ALL.send(sender, Formatters.CAPITALIZED_FORMATTER.format(effectType.getName()));
        else if (targetPlayer == null)
            Message.CHANGED_ISLAND_EFFECT_LEVEL_NAME.send(sender, Formatters.CAPITALIZED_FORMATTER.format(effectType.getName()), islands.get(0).getName());
        else
            Message.CHANGED_ISLAND_EFFECT_LEVEL.send(sender, Formatters.CAPITALIZED_FORMATTER.format(effectType.getName()), targetPlayer.getName());
    }

    @Override
    public List<String> adminTabComplete(SuperiorSkyblockPlugin plugin, CommandSender sender, Island island, String[] args) {
        return args.length == 4 ? CommandTabCompletes.getPotionEffects(args[3]) : Collections.emptyList();
    }

}
