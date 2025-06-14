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
import java.util.stream.Collectors;

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

    @SuppressWarnings("unchecked") // Bỏ qua cảnh báo về ép kiểu không an toàn, vì chúng ta biết nó đúng
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

        // <<< THAY ĐỔI 1: KHAI BÁO KIỂU DỮ LIỆU CỤ THỂ HƠN >>>
        List<Map<String, Object>> effects = (List<Map<String, Object>>) (List<?>) skill.getMapList("effects");

        // <<< THAY ĐỔI 2: CẬP NHẬT VÒNG LẶP FOR >>>
        for (Map<String, Object> effectMap : effects) {
            executeSingleEffect(player, targetEntity, effectMap);
        }
    }

    // <<< THAY ĐỔI 3: CẬP NHẬT CHỮ KÝ CỦA HÀM >>>
    private void executeSingleEffect(Player player, LivingEntity targetEntity, Map<String, Object> effectMap) {
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
                    int count = getIntFromMap(effectMap, "count", 10);
                    double offsetX = getDoubleFromMap(effectMap, "offset_x", 0.5);
                    double offsetY = getDoubleFromMap(effectMap, "offset_y", 0.5);
                    double offsetZ = getDoubleFromMap(effectMap, "offset_z", 0.5);
                    double extra = getDoubleFromMap(effectMap, "extra", 0.0);
                    player.getWorld().spawnParticle(particle, effectLoc, count, offsetX, offsetY, offsetZ, extra);
                    break;
                }
                case "SOUND": {
                    Sound sound = Sound.valueOf(((String) effectMap.get("sound")).toUpperCase());
                    float volume = (float) getDoubleFromMap(effectMap, "volume", 1.0);
                    float pitch = (float) getDoubleFromMap(effectMap, "pitch", 1.0);
                    player.getWorld().playSound(effectLoc, sound, volume, pitch);
                    break;
                }
                case "DAMAGE": {
                    if (targetEntity != null) {
                        double damage = getDoubleFromMap(effectMap, "damage", 1.0);
                        targetEntity.damage(damage, player);
                    }
                    break;
                }
                case "LAUNCH": {
                    double power = getDoubleFromMap(effectMap, "power", 1.0);
                    LivingEntity launchTarget = targetType.equals("TARGET") && targetEntity != null ? targetEntity : player;
                    Vector direction = launchTarget.getLocation().getDirection().setY(0).normalize();
                    Vector velocity = direction.multiply(power).setY(power * 0.5);
                    launchTarget.setVelocity(velocity);
                    break;
                }
                case "POTION": {
                    PotionEffectType potType = PotionEffectType.getByName(((String) effectMap.get("potion_type")).toUpperCase());
                    if (potType != null) {
                        int duration = getIntFromMap(effectMap, "duration", 10) * 20;
                        int amplifier = getIntFromMap(effectMap, "amplifier", 0);
                        LivingEntity potionTarget = targetType.equals("TARGET") && targetEntity != null ? targetEntity : player;
                        potionTarget.addPotionEffect(new PotionEffect(potType, duration, amplifier));
                    }
                    break;
                }
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Lỗi cấu hình kỹ năng: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private LivingEntity getTargetEntity(Player player, int range) {
        List<LivingEntity> nearbyEntities = player.getNearbyEntities(range, range, range).stream()
                .filter(entity -> entity instanceof LivingEntity)
                .map(entity -> (LivingEntity) entity)
                .filter(player::hasLineOfSight)
                .collect(Collectors.toList());

        LivingEntity target = null;
        double minAngle = Double.MAX_VALUE;

        for (LivingEntity entity : nearbyEntities) {
            Vector toEntity = entity.getEyeLocation().toVector().subtract(player.getEyeLocation().toVector());
            double angle = toEntity.angle(player.getEyeLocation().getDirection());

            if (angle < minAngle) {
                minAngle = angle;
                target = entity;
            }
        }
        return target;
    }

    public String formatMessage(String path) {
        String msg = plugin.getConfig().getString(path, "&cKhông tìm thấy tin nhắn: " + path);
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        return ChatColor.translateAlternateColorCodes('&', prefix + msg);
    }

    private int getIntFromMap(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private double getDoubleFromMap(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }
}