package com.isekai.skillmc;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SkillCommand implements CommandExecutor, TabCompleter {

    private final SkillMC plugin;
    private final SkillManager skillManager;

    public SkillCommand(SkillMC plugin, SkillManager skillManager) {
        this.plugin = plugin;
        this.skillManager = skillManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String commandName = command.getName().toLowerCase();

        if (commandName.equals("skillmc") || commandName.equals("kynang")) {
            return handleSkillMCCommand(sender, args);
        }
        if (commandName.equals("skill") || commandName.equals("sk")) {
            return handleSkillUsageCommand(sender, args);
        }
        return false;
    }

    private boolean handleSkillMCCommand(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(skillManager.formatMessage("messages.help_header"));
            sender.sendMessage(skillManager.formatMessage("messages.help_list"));
            sender.sendMessage(skillManager.formatMessage("messages.help_reload"));
            sender.sendMessage(skillManager.formatMessage("messages.help_use"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("skillmc.admin")) {
                sender.sendMessage(skillManager.formatMessage("messages.no_permission"));
                return true;
            }
            plugin.reloadPlugin();
            sender.sendMessage(skillManager.formatMessage("messages.reload"));
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(skillManager.formatMessage("messages.skill_list_header"));
            for (ConfigurationSection skill : skillManager.getAllSkills().values()) {
                String permission = skill.getString("permission", "");
                if (permission.isEmpty() || sender.hasPermission(permission)) {
                    sender.sendMessage(skillManager.formatMessage("messages.skill_list_entry")
                            .replace("{command}", skill.getString("command", "N/A"))
                            .replace("{name}", skill.getString("name", "Chưa đặt tên"))
                            .replace("{mana}", String.valueOf(skill.getInt("mana_cost", 0)))
                            .replace("{cooldown}", String.valueOf(skill.getInt("cooldown", 0))));
                }
            }
            return true;
        }
        return false;
    }

    private boolean handleSkillUsageCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(skillManager.formatMessage("messages.player_only"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(skillManager.formatMessage("messages.prefix") + "Sử dụng: /skill <tên_lệnh_kỹ_năng>");
            return true;
        }

        Player player = (Player) sender;
        String skillCommand = args[0].toLowerCase();
        ConfigurationSection skill = skillManager.getSkill(skillCommand);

        if (skill == null) {
            player.sendMessage(skillManager.formatMessage("messages.skill_not_found").replace("{skill}", skillCommand));
            return true;
        }

        String permission = skill.getString("permission", "");
        if (!permission.isEmpty() && !player.hasPermission(permission)) {
            player.sendMessage(skillManager.formatMessage("messages.no_permission"));
            return true;
        }

        skillManager.executeSkill(player, skill);
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        String commandName = command.getName().toLowerCase();
        
        if (commandName.equals("skillmc") || commandName.equals("kynang")) {
            if (args.length == 1) {
                List<String> subCommands = new ArrayList<>(Arrays.asList("help", "list"));
                if (sender.hasPermission("skillmc.admin")) {
                    subCommands.add("reload");
                }
                return subCommands.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        
        if (commandName.equals("skill") || commandName.equals("sk")) {
            if (args.length == 1) {
                return skillManager.getAllSkills().values().stream()
                        .filter(skill -> {
                            String perm = skill.getString("permission", "");
                            return perm.isEmpty() || sender.hasPermission(perm);
                        })
                        .map(skill -> skill.getString("command"))
                        .filter(cmd -> cmd != null && cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }
}