package com.example.exaroton;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class ExarotonManager extends JavaPlugin implements CommandExecutor {

    private static final String API_BASE = "https://api.exaroton.com/v1";
    private static final String PREFIX = ChatColor.DARK_PURPLE + "[" + ChatColor.LIGHT_PURPLE + "Exaroton" + ChatColor.DARK_PURPLE + "] " + ChatColor.RESET;
    private static final String PLUGIN_URL = "https://github.com/egemen/exaroton-manager";
    private static final String[] CREDITS = {
        ChatColor.GOLD + "CREATED BY EGEMEN",
        ChatColor.YELLOW + "TESEKKURLER / THANKS TO: KIMI K3, GEMINI 3.1"
    };

    private String apiKey = "";
    private boolean pluginEnabled = true;
    private boolean aternosMode = false;
    private final Map<String, String> serverNameToIdCache = new HashMap<>();
    private final Map<String, String> serverAddressCache = new HashMap<>();
    private long lastCacheUpdate = 0;
    private static final long CACHE_TTL = 30000;

    private static final Map<String, Integer> STATUS_MAP = new HashMap<>();
    static {
        STATUS_MAP.put("offline", 0); STATUS_MAP.put("closed", 0);
        STATUS_MAP.put("online", 1); STATUS_MAP.put("started", 1); STATUS_MAP.put("open", 1);
        STATUS_MAP.put("starting", 2); STATUS_MAP.put("stopping", 3);
        STATUS_MAP.put("restarting", 4); STATUS_MAP.put("saving", 5);
        STATUS_MAP.put("loading", 6); STATUS_MAP.put("crashed", 7);
        STATUS_MAP.put("pending", 8); STATUS_MAP.put("transferring", 9); STATUS_MAP.put("preparing", 10);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        this.apiKey = getConfig().getString("api-key", "");
        this.pluginEnabled = getConfig().getBoolean("enabled", true);
        this.aternosMode = getConfig().getBoolean("aternos-mode", false);

        getConfig().set("api-key", this.apiKey);
        getConfig().set("enabled", this.pluginEnabled);
        getConfig().set("aternos-mode", this.aternosMode);
        saveConfig();

        if (getCommand("exaroton") != null) getCommand("exaroton").setExecutor(this);

        getLogger().info("ExarotonManager enabled!");
        getLogger().info("Mode: " + (aternosMode ? "Aternos -> Exaroton" : "Exaroton"));
    }

    @Override
    public void onDisable() {
        getLogger().info("ExarotonManager disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!label.equalsIgnoreCase("exaroton")) return false;
        if (args.length == 0) {
            sendMainHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (!pluginEnabled && !sub.equals("enable") && !sub.equals("help") && !sub.equals("info") && !sub.equals("source")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Plugin is disabled. Use /exaroton enable to activate.");
            return true;
        }

        if (aternosMode) {
            String[] allowed = {"help", "info", "source", "plugin", "enable", "disable", "api", "aternos", "start", "stop", "switch", "list", "status"};
            boolean ok = false;
            for (String a : allowed) if (a.equals(sub)) { ok = true; break; }
            if (!ok && args.length >= 2 && args[1].equalsIgnoreCase("if")) ok = true;
            if (!ok) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Command not available in Aternos mode.");
                return true;
            }
        }

        switch (sub) {
            case "help":
                sendMainHelp(sender);
                return true;
            case "info":
                sendInfo(sender);
                return true;
            case "source":
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Source: " + ChatColor.AQUA + PLUGIN_URL);
                return true;
            case "plugin":
                if (args.length > 1 && args[1].equalsIgnoreCase("url")) {
                    sender.sendMessage(PREFIX + ChatColor.GREEN + "Plugin URL: " + ChatColor.AQUA + PLUGIN_URL);
                } else {
                    sender.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /exaroton plugin url");
                }
                return true;
            case "enable":
                pluginEnabled = true;
                getConfig().set("enabled", true);
                saveConfig();
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Plugin enabled!");
                return true;
            case "disable":
                pluginEnabled = false;
                getConfig().set("enabled", false);
                saveConfig();
                sender.sendMessage(PREFIX + ChatColor.RED + "Plugin disabled!");
                return true;
            case "api":
                if (args.length < 2) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /exaroton api add <key> | /exaroton api remove");
                    return true;
                }
                String apiAction = args[1].toLowerCase();
                if (apiAction.equals("add") && args.length >= 3) {
                    this.apiKey = args[2];
                    getConfig().set("api-key", this.apiKey);
                    saveConfig();
                    sender.sendMessage(PREFIX + ChatColor.GREEN + "API key added and saved!");
                } else if (apiAction.equals("remove")) {
                    this.apiKey = "";
                    getConfig().set("api-key", "");
                    saveConfig();
                    sender.sendMessage(PREFIX + ChatColor.GREEN + "API key removed!");
                } else {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /exaroton api add <key> | /exaroton api remove");
                }
                return true;
            case "aternos":
                if (args.length > 1 && args[1].equalsIgnoreCase("mode")) {
                    aternosMode = !aternosMode;
                    getConfig().set("aternos-mode", aternosMode);
                    saveConfig();
                    reloadConfig();
                    sender.sendMessage(PREFIX + ChatColor.GREEN + "Aternos mode: " + (aternosMode ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
                    if (aternosMode) {
                        sender.sendMessage(PREFIX + ChatColor.YELLOW + "Aternos -> Exaroton transfer active.");
                    }
                } else {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /exaroton aternos mode");
                }
                return true;
            case "list":
                if (checkApiKey(sender)) return true;
                listServers(sender);
                return true;
            case "start":
                if (checkApiKey(sender)) return true;
                if (args.length < 2) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /exaroton start <server> [log <msg>] [if starts switch <player>]");
                    return true;
                }
                handleStartCommand(sender, args);
                return true;
            case "stop":
                if (checkApiKey(sender)) return true;
                if (args.length < 2) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /exaroton stop <server>");
                    return true;
                }
                serverAction(args[1], "stop", sender);
                return true;
            case "restart":
                if (checkApiKey(sender)) return true;
                if (args.length < 2) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /exaroton restart <server>");
                    return true;
                }
                serverAction(args[1], "restart", sender);
                return true;
            case "status":
                if (checkApiKey(sender)) return true;
                if (args.length < 2) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /exaroton status <server>");
                    return true;
                }
                serverStatus(args[1], sender);
                return true;
            case "command":
                if (checkApiKey(sender)) return true;
                if (args.length < 3) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /exaroton command <server> <cmd>");
                    return true;
                }
                String remoteCmd = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                sendServerCommand(args[1], remoteCmd, sender);
                return true;
            case "run":
                if (args.length < 2) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /exaroton run <cmd1>, <cmd2>, ...");
                    return true;
                }
                String allCmds = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                String[] cmds = allCmds.split(",");
                for (String cmd : cmds) {
                    String c = cmd.trim();
                    if (c.startsWith("/")) c = c.substring(1);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
                    sender.sendMessage(PREFIX + ChatColor.GREEN + "Executed: " + ChatColor.WHITE + c);
                }
                return true;
            case "switch":
                if (args.length < 3) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /exaroton switch <server> <player>");
                    return true;
                }
                handleSwitchCommand(sender, args[1], args[2]);
                return true;
            default:
                if (args.length >= 4 && args[1].equalsIgnoreCase("if")) {
                    if (checkApiKey(sender)) return true;
                    String serverName = args[0];
                    String condition = args[2].toLowerCase();
                    if (!args[3].equalsIgnoreCase("run")) {
                        sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /exaroton <server> IF <status> run <command>");
                        return true;
                    }
                    String cmdToRun = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
                    if (cmdToRun.startsWith("/")) cmdToRun = cmdToRun.substring(1);
                    Integer targetStatus = STATUS_MAP.get(condition);
                    if (targetStatus == null) {
                        sender.sendMessage(PREFIX + ChatColor.RED + "Invalid status! Use: online, offline, starting, stopping, restarting, saving, loading, crashed, pending, preparing");
                        return true;
                    }
                    checkStatusAndRun(serverName, targetStatus, cmdToRun, sender);
                    return true;
                }
                sendMainHelp(sender);
                return true;
        }
    }

    private void sendMainHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "========== ExarotonManager ==========");
        sender.sendMessage(ChatColor.YELLOW + "/exaroton help" + ChatColor.GRAY + " - Show this help");
        sender.sendMessage(ChatColor.YELLOW + "/exaroton info" + ChatColor.GRAY + " - Plugin info & credits");
        sender.sendMessage(ChatColor.YELLOW + "/exaroton source" + ChatColor.GRAY + " - Source code link");
        sender.sendMessage(ChatColor.YELLOW + "/exaroton plugin url" + ChatColor.GRAY + " - Plugin download URL");
        sender.sendMessage(ChatColor.YELLOW + "/exaroton enable/disable" + ChatColor.GRAY + " - Toggle plugin");
        sender.sendMessage(ChatColor.YELLOW + "/exaroton api add/remove <key>" + ChatColor.GRAY + " - Manage API key");
        sender.sendMessage(ChatColor.YELLOW + "/exaroton aternos mode" + ChatColor.GRAY + " - Toggle Aternos transfer mode");
        sender.sendMessage(ChatColor.YELLOW + "/exaroton list" + ChatColor.GRAY + " - List all servers");
        sender.sendMessage(ChatColor.YELLOW + "/exaroton start <server>" + ChatColor.GRAY + " - Start server");
        sender.sendMessage(ChatColor.YELLOW + "/exaroton stop <server>" + ChatColor.GRAY + " - Stop server");
        sender.sendMessage(ChatColor.YELLOW + "/exaroton restart <server>" + ChatColor.GRAY + " - Restart server");
        sender.sendMessage(ChatColor.YELLOW + "/exaroton status <server>" + ChatColor.GRAY + " - Server status");
        sender.sendMessage(ChatColor.YELLOW + "/exaroton command <server> <cmd>" + ChatColor.GRAY + " - Remote command");
        sender.sendMessage(ChatColor.YELLOW + "/exaroton <server> IF <status> run <cmd>" + ChatColor.GRAY + " - Conditional command");
        sender.sendMessage(ChatColor.YELLOW + "/exaroton switch <server> <player>" + ChatColor.GRAY + " - Transfer player to server");
        sender.sendMessage(ChatColor.YELLOW + "/exaroton run <cmd1>, <cmd2>..." + ChatColor.GRAY + " - Run multiple commands");
        sender.sendMessage(ChatColor.GOLD + "=====================================");
    }

    private void sendInfo(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.DARK_PURPLE + "============================================");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "           ExarotonManager v2.2");
        sender.sendMessage(ChatColor.DARK_PURPLE + "============================================");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "    " + CREDITS[0]);
        sender.sendMessage(ChatColor.YELLOW + "    " + CREDITS[1]);
        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "    Plugin URL: " + ChatColor.WHITE + PLUGIN_URL);
        sender.sendMessage(ChatColor.AQUA + "    Mode: " + ChatColor.WHITE + (aternosMode ? "Aternos -> Exaroton" : "Exaroton"));
        sender.sendMessage(ChatColor.AQUA + "    Status: " + ChatColor.WHITE + (pluginEnabled ? "Enabled" : "Disabled"));
        sender.sendMessage("");
        sender.sendMessage(ChatColor.DARK_PURPLE + "============================================");
        sender.sendMessage("");
    }

    private boolean checkApiKey(CommandSender sender) {
        if (apiKey == null || apiKey.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Set API key first: " + ChatColor.YELLOW + "/exaroton api add YOUR_KEY");
            return true;
        }
        return false;
    }

    private String getStatusName(int code) {
        switch (code) {
            case 0: return ChatColor.RED + "OFFLINE";
            case 1: return ChatColor.GREEN + "ONLINE";
            case 2: return ChatColor.BLUE + "STARTING";
            case 3: return ChatColor.LIGHT_PURPLE + "STOPPING";
            case 4: return ChatColor.DARK_PURPLE + "RESTARTING";
            case 5: return ChatColor.AQUA + "SAVING";
            case 6: return ChatColor.YELLOW + "LOADING";
            case 7: return ChatColor.DARK_RED + "CRASHED";
            case 8: return ChatColor.GRAY + "PENDING";
            case 9: return ChatColor.GRAY + "TRANSFERRING";
            case 10: return ChatColor.GOLD + "PREPARING";
            default: return ChatColor.GRAY + "UNKNOWN (" + code + ")";
        }
    }

    private void handleStartCommand(CommandSender sender, String[] args) {
        String serverName = args[1];
        String logMsg = null;
        String switchPlayer = null;

        for (int i = 2; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("log") && i + 1 < args.length) {
                logMsg = args[i + 1];
                i++;
            } else if (args[i].equalsIgnoreCase("if") && i + 1 < args.length && args[i + 1].equalsIgnoreCase("starts")) {
                if (i + 2 < args.length && args[i + 2].equalsIgnoreCase("switch") && i + 3 < args.length) {
                    switchPlayer = args[i + 3];
                    i += 3;
                }
            }
        }

        final String finalLogMsg = logMsg;
        final String finalSwitchPlayer = switchPlayer;
        final String finalServerName = serverName;

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String sid = resolveServerId(serverName);
                if (sid == null) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Server not found: " + serverName);
                    return;
                }
                JsonObject json = apiGet("/servers/" + sid + "/start/");
                if (json != null && json.has("success") && json.get("success").getAsBoolean()) {
                    sender.sendMessage(PREFIX + ChatColor.GREEN + "Server is starting...");

                    if (finalLogMsg != null) {
                        Bukkit.getScheduler().runTask(this, () -> {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "say " + finalLogMsg);
                        });
                    }

                    if (finalSwitchPlayer != null) {
                        Bukkit.getScheduler().runTaskLater(this, () -> {
                            handleSwitchCommand(sender, finalServerName, finalSwitchPlayer);
                        }, 60L);
                    }
                } else {
                    String err = (json != null && json.has("error") && !json.get("error").isJsonNull()) ? json.get("error").getAsString() : "Unknown error";
                    sender.sendMessage(PREFIX + ChatColor.RED + "Start failed: " + err);
                }
            } catch (Exception e) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Error: " + e.getMessage());
            }
        });
    }

    private void handleSwitchCommand(CommandSender sender, String serverName, String playerName) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String sid = resolveServerId(serverName);
                if (sid == null) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Server not found: " + serverName);
                    return;
                }
                String address = getServerAddress(sid);
                if (address == null || address.isEmpty()) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Could not get server address for: " + serverName);
                    return;
                }

                Bukkit.getScheduler().runTask(this, () -> {
                    if (playerName.equals("@a")) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            transferPlayer(p, address);
                        }
                        sender.sendMessage(PREFIX + ChatColor.GREEN + "Switching all players to " + serverName + " (" + address + ")");
                    } else if (playerName.equals("@p")) {
                        if (sender instanceof Player) {
                            transferPlayer((Player) sender, address);
                            sender.sendMessage(PREFIX + ChatColor.GREEN + "Switching you to " + serverName + " (" + address + ")");
                        } else {
                            sender.sendMessage(PREFIX + ChatColor.RED + "@p can only be used by a player.");
                        }
                    } else {
                        Player p = Bukkit.getPlayerExact(playerName);
                        if (p != null) {
                            transferPlayer(p, address);
                            sender.sendMessage(PREFIX + ChatColor.GREEN + "Switching " + playerName + " to " + serverName + " (" + address + ")");
                        } else {
                            sender.sendMessage(PREFIX + ChatColor.RED + "Player not found: " + playerName);
                        }
                    }
                });
            } catch (Exception e) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Error: " + e.getMessage());
            }
        });
    }

    private void transferPlayer(Player player, String address) {
        String[] parts = address.split(":");
        String ip = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 25565;

        try {
            Method transferMethod = Player.class.getMethod("transfer", String.class, int.class);
            transferMethod.invoke(player, ip, port);
            player.sendMessage(PREFIX + ChatColor.GREEN + "Connecting to " + address + "...");
        } catch (NoSuchMethodException e) {
            try {
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                java.io.DataOutputStream out = new java.io.DataOutputStream(b);
                out.writeUTF("Connect");
                out.writeUTF(ip + ":" + port);
                player.sendPluginMessage(this, "BungeeCord", b.toByteArray());
                player.sendMessage(PREFIX + ChatColor.GREEN + "Connecting via BungeeCord...");
            } catch (Exception ex) {
                player.sendMessage(PREFIX + ChatColor.RED + "Transfer failed: " + ex.getMessage());
            }
        } catch (Exception e) {
            player.sendMessage(PREFIX + ChatColor.RED + "Transfer failed: " + e.getMessage());
        }
    }

    private String getServerAddress(String serverId) throws Exception {
        String cached = serverAddressCache.get(serverId);
        if (cached != null) return cached;

        JsonObject json = apiGet("/servers/" + serverId + "/");
        if (json != null && json.has("data")) {
            JsonObject data = json.getAsJsonObject("data");
            if (data.has("address") && !data.get("address").isJsonNull()) {
                String addr = data.get("address").getAsString();
                serverAddressCache.put(serverId, addr);
                return addr;
            }
        }
        return null;
    }

    private void listServers(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "Fetching servers...");
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                JsonObject json = apiGet("/servers/");
                if (json == null || !json.has("data")) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Could not fetch server list.");
                    return;
                }
                JsonArray data = json.getAsJsonArray("data");
                sender.sendMessage(ChatColor.GOLD + "--- Exaroton Server List ---");
                serverNameToIdCache.clear();
                serverAddressCache.clear();
                for (JsonElement el : data) {
                    JsonObject s = el.getAsJsonObject();
                    String id = s.get("id").getAsString();
                    String name = s.get("name").getAsString();
                    int status = s.get("status").getAsInt();
                    int players = 0, maxPlayers = 0;
                    String address = "";
                    if (s.has("players") && !s.get("players").isJsonNull()) {
                        JsonObject p = s.getAsJsonObject("players");
                        if (p.has("count")) players = p.get("count").getAsInt();
                        if (p.has("max")) maxPlayers = p.get("max").getAsInt();
                    }
                    if (s.has("address") && !s.get("address").isJsonNull()) {
                        address = s.get("address").getAsString();
                        serverAddressCache.put(id, address);
                    }
                    serverNameToIdCache.put(name.toLowerCase(), id);
                    serverNameToIdCache.put(id.toLowerCase(), id);
                    sender.sendMessage(ChatColor.AQUA + name + ChatColor.GRAY + " | ID: " + ChatColor.YELLOW + id +
                            ChatColor.GRAY + " | " + getStatusName(status) + ChatColor.GRAY +
                            " | Players: " + ChatColor.WHITE + players + "/" + maxPlayers +
                            ChatColor.GRAY + " | " + ChatColor.DARK_GRAY + address);
                }
                lastCacheUpdate = System.currentTimeMillis();
                sender.sendMessage(ChatColor.GOLD + "----------------------------");
            } catch (Exception e) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Error: " + e.getMessage());
            }
        });
    }

    private void serverAction(String target, String action, CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String sid = resolveServerId(target);
                if (sid == null) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Server not found: " + target);
                    return;
                }
                JsonObject json = apiGet("/servers/" + sid + "/" + action + "/");
                if (json != null && json.has("success") && json.get("success").getAsBoolean()) {
                    String msg = action.equals("start") ? "starting..." : action.equals("stop") ? "stopping..." : "restarting...";
                    sender.sendMessage(PREFIX + ChatColor.GREEN + "Server is " + msg);
                } else {
                    String err = (json != null && json.has("error") && !json.get("error").isJsonNull()) ? json.get("error").getAsString() : "Unknown error";
                    sender.sendMessage(PREFIX + ChatColor.RED + "Action failed: " + err);
                }
            } catch (Exception e) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Error: " + e.getMessage());
            }
        });
    }

    private void serverStatus(String target, CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String sid = resolveServerId(target);
                if (sid == null) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Server not found: " + target);
                    return;
                }
                JsonObject json = apiGet("/servers/" + sid + "/");
                if (json != null && json.has("data")) {
                    JsonObject s = json.getAsJsonObject("data");
                    String name = s.get("name").getAsString();
                    int status = s.get("status").getAsInt();
                    sender.sendMessage(ChatColor.GOLD + "--- " + name + " Status ---");
                    sender.sendMessage(ChatColor.YELLOW + "Status: " + getStatusName(status));
                    if (s.has("address") && !s.get("address").isJsonNull())
                        sender.sendMessage(ChatColor.YELLOW + "Address: " + ChatColor.WHITE + s.get("address").getAsString());
                    if (s.has("players") && !s.get("players").isJsonNull()) {
                        JsonObject p = s.getAsJsonObject("players");
                        sender.sendMessage(ChatColor.YELLOW + "Players: " + ChatColor.WHITE +
                                p.get("count").getAsInt() + "/" + p.get("max").getAsInt());
                    }
                    sender.sendMessage(ChatColor.GOLD + "---------------------------");
                } else {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Could not get status.");
                }
            } catch (Exception e) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Error: " + e.getMessage());
            }
        });
    }

    private void sendServerCommand(String target, String command, CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String sid = resolveServerId(target);
                if (sid == null) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Server not found: " + target);
                    return;
                }
                JsonObject body = new JsonObject();
                body.addProperty("command", command);
                JsonObject json = apiPost("/servers/" + sid + "/command/", body.toString());
                if (json != null && json.has("success") && json.get("success").getAsBoolean()) {
                    sender.sendMessage(PREFIX + ChatColor.GREEN + "Command sent: " + ChatColor.WHITE + command);
                } else {
                    String err = (json != null && json.has("error") && !json.get("error").isJsonNull()) ? json.get("error").getAsString() : "Unknown error";
                    sender.sendMessage(PREFIX + ChatColor.RED + "Command failed: " + err);
                }
            } catch (Exception e) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Error: " + e.getMessage());
            }
        });
    }

    private void checkStatusAndRun(String target, int expectedStatus, String command, CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String sid = resolveServerId(target);
                if (sid == null) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Server not found: " + target);
                    return;
                }
                JsonObject json = apiGet("/servers/" + sid + "/");
                if (json != null && json.has("data")) {
                    int current = json.getAsJsonObject("data").get("status").getAsInt();
                    if (current == expectedStatus) {
                        Bukkit.getScheduler().runTask(this, () -> {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                            sender.sendMessage(PREFIX + ChatColor.GREEN + "Condition met (" + getStatusName(current) + ChatColor.GREEN + "). Command executed.");
                        });
                    } else {
                        sender.sendMessage(PREFIX + ChatColor.YELLOW + "Condition not met. Current status: " + getStatusName(current));
                    }
                } else {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Could not get status.");
                }
            } catch (Exception e) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Error: " + e.getMessage());
            }
        });
    }

    private String resolveServerId(String input) throws Exception {
        if (System.currentTimeMillis() - lastCacheUpdate < CACHE_TTL) {
            String cached = serverNameToIdCache.get(input.toLowerCase());
            if (cached != null) return cached;
        }
        JsonObject json = apiGet("/servers/");
        if (json != null && json.has("data")) {
            serverNameToIdCache.clear();
            serverAddressCache.clear();
            JsonArray arr = json.getAsJsonArray("data");
            for (JsonElement el : arr) {
                JsonObject s = el.getAsJsonObject();
                String id = s.get("id").getAsString();
                String name = s.get("name").getAsString();
                serverNameToIdCache.put(name.toLowerCase(), id);
                serverNameToIdCache.put(id.toLowerCase(), id);
                if (s.has("address") && !s.get("address").isJsonNull()) {
                    serverAddressCache.put(id, s.get("address").getAsString());
                }
            }
            lastCacheUpdate = System.currentTimeMillis();
            return serverNameToIdCache.get(input.toLowerCase());
        }
        return null;
    }

    private JsonObject apiGet(String endpoint) throws IOException {
        URL url = new URL(API_BASE + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        try {
            int code = conn.getResponseCode();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    code >= 400 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            return JsonParser.parseString(sb.toString()).getAsJsonObject();
        } finally {
            conn.disconnect();
        }
    }

    private JsonObject apiPost(String endpoint, String jsonBody) throws IOException {
        URL url = new URL(API_BASE + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        try {
            int code = conn.getResponseCode();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    code >= 400 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            return JsonParser.parseString(sb.toString()).getAsJsonObject();
        } finally {
            conn.disconnect();
        }
    }
}
