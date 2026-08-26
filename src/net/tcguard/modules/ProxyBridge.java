package net.tcguard.modules;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.tcguard.TCGuard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Collection;

public class ProxyBridge implements PluginMessageListener {

    private final TCGuard plugin;
    private static final String BUNGEE_CHANNEL = "BungeeCord";
    private static final String TCGUARD_CHANNEL = "tcguard:bridge";

    private boolean isProxyNetwork = false;

    public ProxyBridge(TCGuard plugin) {
        this.plugin = plugin;
    }

    public void register() {
        if (!plugin.getConfig().getBoolean("proxy-bridge.enabled", true)) {
            return;
        }

        try {
            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
            Bukkit.getMessenger().registerIncomingPluginChannel(plugin, BUNGEE_CHANNEL, this);

            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, TCGUARD_CHANNEL);
            Bukkit.getMessenger().registerIncomingPluginChannel(plugin, TCGUARD_CHANNEL, this);

            plugin.getLogger().info("[ProxyBridge] BungeeCord / Velocity bridge channels registered.");
        } catch (Exception e) {
            plugin.getLogger().warning("[ProxyBridge] Channel registration error: " + e.getMessage());
        }
    }

    public void kickFromNetwork(String playerName, String reason) {
        if (playerName == null || playerName.isEmpty()) return;
        if (!plugin.getConfig().getBoolean("proxy-bridge.kick-from-network", true)) return;

        Player sender = getAnyOnlinePlayer();
        if (sender == null) return;

        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("KickPlayer");
            out.writeUTF(playerName);
            out.writeUTF(reason != null ? reason : "§8[§cTCG-Network§8] §cDisconnected from network.");

            sender.sendPluginMessage(plugin, BUNGEE_CHANNEL, out.toByteArray());
            isProxyNetwork = true;
        } catch (Exception e) {
            plugin.getLogger().fine("[ProxyBridge] Network Kick failed: " + e.getMessage());
        }
    }

    public void broadcastNetworkAlert(String message) {
        if (message == null || message.isEmpty()) return;
        if (!plugin.getConfig().getBoolean("proxy-bridge.broadcast-staff-alerts", true)) return;

        Player sender = getAnyOnlinePlayer();
        if (sender == null) return;

        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Message");
            out.writeUTF("ALL");
            out.writeUTF("§8[§4§lTCG-NETWORK§8] §c" + message);

            sender.sendPluginMessage(plugin, BUNGEE_CHANNEL, out.toByteArray());
            isProxyNetwork = true;
        } catch (Exception ignored) {}
    }

    public void syncAttackModeToNetwork() {
        if (!plugin.getConfig().getBoolean("proxy-bridge.sync-attack-mode-network", true)) return;

        Player sender = getAnyOnlinePlayer();
        if (sender == null) return;

        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Forward");
            out.writeUTF("ALL");
            out.writeUTF(TCGUARD_CHANNEL);

            ByteArrayDataOutput customData = ByteStreams.newDataOutput();
            customData.writeUTF("ATTACK_MODE_START");
            customData.writeLong(System.currentTimeMillis());

            byte[] dataBytes = customData.toByteArray();
            out.writeShort(dataBytes.length);
            out.write(dataBytes);

            sender.sendPluginMessage(plugin, BUNGEE_CHANNEL, out.toByteArray());
        } catch (Exception ignored) {}
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(BUNGEE_CHANNEL) && !channel.equals(TCGUARD_CHANNEL)) return;
        isProxyNetwork = true;

        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            String subChannel = in.readUTF();

            if (subChannel.equals(TCGUARD_CHANNEL) || channel.equals(TCGUARD_CHANNEL)) {
                short len = in.readShort();
                byte[] msgbytes = new byte[len];
                in.readFully(msgbytes);

                ByteArrayDataInput msgin = ByteStreams.newDataInput(msgbytes);
                String command = msgin.readUTF();

                if ("ATTACK_MODE_START".equals(command)) {
                    plugin.getThreatIntelligence().triggerAttackMode();
                    plugin.alertAdmins("§c[TCG-Network] Cross-server Attack Signal received! Attack Shield active.");
                }
            }
        } catch (Exception ignored) {}
    }

    private Player getAnyOnlinePlayer() {
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        if (players.isEmpty()) return null;
        return players.iterator().next();
    }

    public boolean isProxyNetwork() {
        return isProxyNetwork;
    }
}
