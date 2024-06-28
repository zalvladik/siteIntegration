package org.example.mc.siteintegration.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.example.mc.siteintegration.entities.User;
import org.example.mc.siteintegration.managers.UserManager;
import org.example.mc.siteintegration.utils.PlayerMessageUtil;

import java.time.Instant;

public class PlayerListener implements Listener {

    private final UserManager userManager;
    private PlayerMessageUtil messageUtil;

    public PlayerListener( UserManager userManager) {
        this.userManager = userManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        this.messageUtil = new PlayerMessageUtil(player);

        restrictPlayer(player);

        String playerName = player.getName();
        String playerIp = player.getAddress().getAddress().getHostAddress();

        User user = userManager.getUser(playerName);

        if (user == null) {
            // Если пользователь не зарегистрирован
            messageUtil.toChat("&cЗареєструйтесь: /reg <пароль> <повторітьПароль>");
        } else {
            // Если пользователь зарегистрирован
            if (user.getIp().equals(playerIp) && Instant.now().isBefore(user.getMcSession())) {
                // Если IP совпадает и сессия еще действительна
                messageUtil.toChat("&2Успішне відновлення сессії.");

                freePlayer(player);
            } else {
                // Если IP не совпадает или сессия истекла
                restrictPlayer(player);
                messageUtil.toChat("&Авторизуйтесь: /login <пароль>");
            }
        }
    }

    private void restrictPlayer(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1));
        player.setWalkSpeed(0);
        player.setFlySpeed(0);
        player.setAllowFlight(false);
        player.setInvulnerable(true);
        player.chat(null);
    }

    private void freePlayer(Player player) {
        player.removePotionEffect(PotionEffectType.BLINDNESS); 
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.2f);
        player.setAllowFlight(true);
        player.setInvulnerable(false);
    }
}