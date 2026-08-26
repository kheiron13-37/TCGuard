package net.tcguard.modules;

import net.tcguard.TCGuard;
import net.tcguard.config.ConfigManager;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DiscordWebhook {

    private final TCGuard plugin;
    private final SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private volatile long lastWebhookSendTime = 0;
    private volatile boolean warnedRegionalBlock = false;

    public DiscordWebhook(TCGuard plugin) {
        this.plugin = plugin;
        this.isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public void sendAlert(String title, String playerName, String reason, String details, int hexColor) {
        ConfigManager cfg = plugin.getConfigManager();
        String webhookUrl = plugin.getConfig().getString("discord-webhook.url", "").trim();
        boolean enabled = plugin.getConfig().getBoolean("discord-webhook.enabled", false);

        if (!webhookUrl.isEmpty() && !webhookUrl.startsWith("http://") && !webhookUrl.startsWith("https://")) {
            webhookUrl = "https://" + webhookUrl;
        }

        if (!enabled && (webhookUrl.contains("discord.com/api/webhooks/") || webhookUrl.contains("discordapp.com/api/webhooks/"))) {
            enabled = true;
        }

        if (!enabled || webhookUrl.isEmpty() || webhookUrl.contains("YOUR_WEBHOOK_URL_HERE")) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastWebhookSendTime < 500L) {
            return;
        }
        lastWebhookSendTime = now;

        final String targetUrl = webhookUrl;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            HttpURLConnection conn = null;
            try {
                String timestamp = isoFormat.format(new Date());

                StringBuilder json = new StringBuilder();
                json.append("{");
                json.append("\"username\":\"TCGuard Enterprise\",");
                json.append("\"avatar_url\":\"https://i.imgur.com/8QfJq1O.png\",");
                json.append("\"embeds\":[{");
                json.append("\"title\":").append(escapeJson("🛡️ " + title)).append(",");
                json.append("\"color\":").append(hexColor).append(",");
                json.append("\"timestamp\":\"").append(timestamp).append("\",");
                json.append("\"footer\":{\"text\":\"TCGuard Security Engine\"},");
                json.append("\"fields\":[");

                json.append("{\"name\":\"👤 Player\",\"value\":").append(escapeJson(playerName != null ? playerName : "Unknown")).append(",\"inline\":true},");
                json.append("{\"name\":\"⚠️ Violation\",\"value\":").append(escapeJson(reason)).append(",\"inline\":true},");

                if (details != null && !details.isEmpty()) {
                    json.append("{\"name\":\"📝 Details\",\"value\":").append(escapeJson(details)).append(",\"inline\":false}");
                } else {
                    json.append("{\"name\":\"📊 Status\",\"value\":\"Automatically Blocked\",\"inline\":true}");
                }

                json.append("]");
                json.append("}]");
                json.append("}");

                URL url = new URL(targetUrl);

                if (cfg.isDiscordProxyEnabled()) {
                    Proxy.Type pType = "SOCKS".equalsIgnoreCase(cfg.getDiscordProxyType()) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
                    SocketAddress addr = new InetSocketAddress(cfg.getDiscordProxyHost(), cfg.getDiscordProxyPort());
                    Proxy proxy = new Proxy(pType, addr);
                    conn = (HttpURLConnection) url.openConnection(proxy);
                } else {
                    conn = (HttpURLConnection) url.openConnection();
                }

                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                conn.setDoOutput(true);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                byte[] data = json.toString().getBytes(StandardCharsets.UTF_8);
                conn.setRequestProperty("Content-Length", String.valueOf(data.length));

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(data);
                    os.flush();
                }

                int responseCode = conn.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    warnedRegionalBlock = false;
                } else {
                    String errResponse = readStream(conn.getErrorStream());
                    plugin.getLogger().warning("[Webhook] Discord API HTTP " + responseCode + " - " + errResponse);
                }
            } catch (SocketException se) {
                String msg = se.getMessage() != null ? se.getMessage().toLowerCase() : "";
                if (msg.contains("reset") || msg.contains("connection refused")) {
                    if (!warnedRegionalBlock) {
                        warnedRegionalBlock = true;
                        plugin.getLogger().warning("[TCGuard Webhook] Discord connection blocked/reset. Consider configuring discord proxy or VPN.");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Webhook] Discord error: " + e.getMessage());
            } finally {
                if (conn != null) {
                    try { conn.disconnect(); } catch (Exception ignored) {}
                }
            }
        });
    }

    private String readStream(InputStream is) {
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        String hex = Integer.toHexString(c);
                        sb.append("\\u0000".substring(0, 6 - hex.length())).append(hex);
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
