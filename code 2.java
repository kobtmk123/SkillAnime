package com.isekai.skillmc;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

public final class SkillMC extends JavaPlugin {

    private SkillManager skillManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        File skillsFile = new File(getDataFolder(), "skills.yml");
        if (!skillsFile.exists()) {
            saveResource("skills.yml", false);
        }

        this.skillManager = new SkillManager(this);
        skillManager.loadSkills();

        getCommand("skillmc").setExecutor(new SkillCommand(this, skillManager));
        getCommand("skillmc").setTabCompleter(new SkillCommand(this, skillManager));
        getCommand("skill").setExecutor(new SkillCommand(this, skillManager));
        getCommand("skill").setTabCompleter(new SkillCommand(this, skillManager));

        getLogger().info("Plugin SkillMC (Tiếng Việt) đã được bật!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Plugin SkillMC đã bị tắt.");
    }

    public void reloadPlugin() {
        reloadConfig();
        skillManager.loadSkills();
    }
}