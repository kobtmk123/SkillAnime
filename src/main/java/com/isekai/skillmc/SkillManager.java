package com.isekai.skillmc;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SkillManager {

    private final SkillMC plugin;
    private final Map<String, ConfigurationSection> skills = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    public SkillManager(SkillMC plugin) {
        this.plugin = plugin;
    }

    public void loadSkills() {
        skills.clear();
        File skillsFile = new File(plugin.getDataFolder(), "skills.yml");
        FileConfiguration skillsConfig = YamlConfiguration.loadConfiguration(skillsFile);

        for (String key : skillsConfig.getKeys(false)) {
            ConfigurationSection skillSection = skillsConfig.getConfigurationSection(key);
            if (skillSection != null && skillSection.contains("command")) {
                skills.put(skillSection.getString("command").toLowerCase(), skillSection);
            }
        }
        plugin.getLogger().info("Ä Ã£ táº£i " + skills.size() + " ká»¹ nÄƒng tá»« skills.yml.");
    }

    public ConfigurationSection getSkill(String command) {
        return skills.get(command.toLowerCase());
    }

    public Map<String, ConfigurationSection> getAllSkills() {
        return skills;
    }

    public void executeSkill(Player player, ConfigurationSection skill) {
        String skillName = skill.getString("name", "Ká»¹ nÄƒng khÃ'ng tÃªn");

        long cooldownTime = skill.getLong("cooldown", 0) * 1000;
        if (cooldownTime > 0) {
            cooldowns.putIfAbsent(player.getUniqueId(), new HashMap<>());
            Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
            long lastUsed = playerCooldowns.getOrDefault(skill.getName(), 0L);

            if (System.currentTimeMillis() - lastUsed < cooldownTime) {
                long timeLeft = (cooldownTime - (System.currentTimeMillis() - lastUsed)) / 1000;
                player.sendMessage(formatMessage("messages.on_cooldown")
                        .replace("{skill_name}", skillName)
                        .replace("{time}", String.valueOf(timeLeft + 1)));
                return;
            }
        }

        int manaCost = skill.getInt("mana_cost", 0);
        if (player.getLevel() < manaCost) {
            player.sendMessage(formatMessage("messages.not_enough_mana")
                    .replace("{mana_cost}", String.valueOf(manaCost))
                    .replace("{current_mana}", String.valueOf(player.getLevel())));
            return;
        }

        if (manaCost > 0) {
            player.setLevel(player.getLevel() - manaCost);
        }
        if (cooldownTime > 0) {
            cooldowns.get(player.getUniqueId()).put(skill.getName(), System.currentTimeMillis());
        }

        LivingEntity targetEntity = getTargetEntity(player, 30);
        List<Map<?, ?>> effects = skill.getMapList("effects");
        for (Map<?, ?> effectMap : effects) {
            executeSingleEffect(player, targetEntity, effectMap);
        }
    }

    // ===== HÀM ĐÃ ĐƯỢC SỬA LẠI HOÀN TOÀN =====
    private void executeSingleEffect(Player player, LivingEntity targetEntity, Map<?, ?> effectMap) {
        String type = (String) effectMap.get("type");
        if (type == null) return;

        String targetType = ((String) effectMap.getOrDefault("target", "SELF")).toUpperCase();
        Location effectLoc;
        if (targetType.equals("TARGET") && targetEntity != null) {
            effectLoc = targetEntity.getLocation().add(0, targetEntity.getHeight() / 2, 0);
        } else {
            effectLoc = player.getLocation().add(0, 1, 0);
        }

        try {
            switch (type.toUpperCase()) {
                case "PARTICLE": {
                    Particle particle = Particle.valueOf(((String) effectMap.get("particle")).toUpperCase());
                    int count = (int) (Integer) effectMap.getOrDefault("count", 10);
                    double offsetX = (double) (Double) effectMap.getOrDefault("offset_x", 0.5);
                    double offsetY = (double) (Double) effectMap.getOrDefault("offset_y", 0.5);
                    double offsetZ = (double) (Double) effectMap.getOrDefault("offset_z", 0.5);
                    double extra = (double) (Double) effectMap.getOrDefault("extra", 0.0);
                    player.getWorld().spawnParticle(particle, effectLoc, count, offsetX, offsetY, offsetZ, extra);
                    break;
                }
                case "SOUND": {
                    Sound sound = Sound.valueOf(((String) effectMap.get("sound")).toUpperCase());
                    float volume = (float) (double) (Double) effectMap.getOrDefault("volume", 1.0);
                    float pitch = (float) (double) (Double) effectMap.getOrDefault("pitch", 1.0);
                    player.getWorld().playSound(effectLoc, sound, volume, pitch);
                    break;
                }
                case "DAMAGE": {
                    if (targetEntity != null) {
                        double damage = (double) (Double) effectMap.getOrDefault("damage", 1.0);
                        targetEntity.damage(damage, player);
                    }
                    break;
                }
                case "LAUNCH": {
                    double power = (double) (Double) effectMap.getOrDefault("power", 1.0);
                    LivingEntity launchTarget = targetType.equals("TARGET") && targetEntity != null ? targetEntity : player;
                    Vector direction = launchTarget.getLocation().getDirection().setY(0).normalize();
                    Vector velocity = direction.multiply(power).setY(power * 0.5);
                    launchTarget.setVelocity(velocity);
                    break;
                }
                case "POTION": {
                    PotionEffectType potType = PotionEffectType.getByName(((String) effectMap.get("potion_type")).toUpperCase());
                    if (potType != null) {
                        int duration = (int) (Integer) effectMap.getOrDefault("duration", 10) * 20;
                        int amplifier = (int) (Integer) effectMap.getOrDefault("amplifier", 0);
                        LivingEntity potionTarget = targetType.equals("TARGET") && targetEntity != null ? targetEntity : player;
                        potionTarget.addPotionEffect(new PotionEffect(potType, duration, amplifier));
                    }
                    break;
                }
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Lá»--i cáº¥u hÃ¬nh ká»¹ nÄƒng: " + e.getMessage());
            // Để debug tốt hơn, bạn có thể thêm dòng này để in lỗi ra console của server
            // e.printStackTrace();
        }
    }

    private LivingEntity getTargetEntity(Player player, int range) {
        return player.getNearbyEntities(range, range, range).stream()
                .filter(entity -> entity instanceof LivingEntity)
                .map(entity -> (LivingEntity) entity)
                .filter(player::hasLineOfSight)
                .min((e1, e2) -> {
                    Vector toE1 = e1.getEyeLocation().toVector().subtract(player.getEyeLocation().toVector());
                    Vector toE2 = e2.getEyeLocation().toVector().subtract(player.getEyeLocation().toVector());
                    return Double.compare(
                            toE1.angle(player.getEyeLocation().getDirection()),
                            toE2.angle(player.getEyeLocation().getDirection())
                    );
                }).orElse(null);
    }

    public String formatMessage(String path) {
        String msg = plugin.getConfig().getString(path, "&cKhÃ'ng tÃ¬m tháº¥y tin nháº¯n: " + path);
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        return ChatColor.translateAlternateColorCodes('&', prefix + msg);
    }
}