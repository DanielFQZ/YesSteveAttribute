package com.yessteveattribute.paper;

import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.stream.Collectors;

/** Paper command and dynamic completion for the YSA service plugin. */
final class YsaCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT = List.of("reload", "status", "attr", "resource", "role", "level", "profile", "debug", "ui");
    private final YesSteveAttributePaper plugin;

    YsaCommand(YesSteveAttributePaper plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || equals(args, 0, "status")) return status(sender);
        if (equals(args, 0, "reload")) return reload(sender);
        if (equals(args, 0, "debug") || equals(args, 0, "ui")) return debug(sender);
        if (equals(args, 0, "attr")) return attr(sender, args);
        if (equals(args, 0, "resource")) return resource(sender, args);
        if (equals(args, 0, "profile")) return profile(sender, args);
        if (equals(args, 0, "role")) return role(sender, args);
        if (equals(args, 0, "level")) return level(sender, args);
        sender.sendMessage(ChatColor.YELLOW + "用法: /ysa reload|status|attr|resource|role|level|profile|debug");
        return true;
    }

    private boolean status(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "[YSA] 配置: " + plugin.lastReload() +
                ChatColor.GRAY + "，属性: " + plugin.configuration().getAttributes().getAttributes().size() +
                "，资源: " + plugin.configuration().getAttributes().getResources().size() +
                "，角色: " + plugin.configuration().getRoles().getRoles().size());
        return true;
    }
    private boolean reload(CommandSender sender) {
        if (!admin(sender)) return true;
        boolean ok = plugin.reloadConfiguration();
        sender.sendMessage((ok ? ChatColor.GREEN : ChatColor.RED) + "[YSA] reload " + plugin.lastReload());
        return true;
    }
    private boolean debug(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.YELLOW + "该界面只能由玩家打开。"); return true; }
        if (!player.hasPermission("ysa.debug") && !player.hasPermission("ysa.admin")) { sender.sendMessage(ChatColor.RED + "没有权限。"); return true; }
        if (!plugin.openDebugUi(player)) sender.sendMessage(ChatColor.YELLOW + "临时调试界面不可用：请确认 EmpyreanUI/MineBrowse 已启用且客户端已连接。");
        return true;
    }
    private boolean attr(CommandSender sender, String[] args) {
        Player player = target(sender, args, 1); if (player == null) return true;
        String id = args.length >= 3 ? args[2] : null;
        if (id != null && !plugin.configuration().getAttributes().getAttributes().containsKey(id)) { sender.sendMessage(ChatColor.RED + "未知属性: " + id); return true; }
        if (id == null) plugin.profile(player).snapshot().all().forEach((key, value) -> sender.sendMessage(ChatColor.GRAY + key + " = " + value));
        else sender.sendMessage(ChatColor.GRAY + id + " = " + plugin.profile(player).snapshot().get(id));
        return true;
    }
    private boolean resource(CommandSender sender, String[] args) {
        Player player = target(sender, args, 1); if (player == null) return true;
        if (args.length < 3) { plugin.profile(player).resources().forEach((id, state) -> sender.sendMessage(ChatColor.GRAY + id + " = " + state.current() + "/" + state.maximum())); return true; }
        var state = plugin.profile(player).resources().get(args[2]);
        if (state == null) { sender.sendMessage(ChatColor.RED + "未知资源: " + args[2]); return true; }
        if (args.length == 3) { sender.sendMessage(ChatColor.GRAY + args[2] + " = " + state.current() + "/" + state.maximum() + " (恢复 " + state.recovery() + "/s, 衰减 " + state.decay() + "/s)"); return true; }
        if (!admin(sender)) return true;
        if (args.length < 5 || (!args[3].equalsIgnoreCase("set") && !args[3].equalsIgnoreCase("add"))) { sender.sendMessage(ChatColor.YELLOW + "用法: /ysa resource <玩家> <资源> [set|add] <数值>"); return true; }
        try { double value = Double.parseDouble(args[4]); plugin.setResource(player, args[2], args[3].equalsIgnoreCase("add") ? state.current() + value : value); sender.sendMessage(ChatColor.GREEN + "[YSA] 资源已更新: " + args[2] + " = " + plugin.profile(player).resources().get(args[2]).current()); }
        catch (RuntimeException ex) { sender.sendMessage(ChatColor.RED + "资源更新失败: " + ex.getMessage()); }
        return true;
    }
    private boolean profile(CommandSender sender, String[] args) {
        Player player = target(sender, args, 1); if (player == null) return true;
        var p = plugin.profile(player); sender.sendMessage(ChatColor.AQUA + "[YSA] " + player.getName() + " 角色=" + p.role() + " 等级=" + p.level());
        sender.sendMessage(ChatColor.GRAY + "属性 " + p.snapshot().all().size() + " 项，资源 " + p.resources().size() + " 项"); return true;
    }
    private boolean role(CommandSender sender, String[] args) {
        if (!admin(sender)) return true; Player player = target(sender, args, 1); if (player == null) return true;
        if (args.length < 3) { sender.sendMessage(ChatColor.YELLOW + "用法: /ysa role <玩家> <角色> [等级]"); return true; }
        if (!plugin.configuration().getRoles().getRoles().containsKey(args[2])) { sender.sendMessage(ChatColor.RED + "未知角色: " + args[2]); return true; }
        int level = args.length >= 4 ? parseInt(args[3], 1) : 1; plugin.setRole(player, args[2], level); sender.sendMessage(ChatColor.GREEN + "[YSA] 已设置角色 " + args[2] + " 等级 " + level); return true;
    }
    private boolean level(CommandSender sender, String[] args) {
        if (!admin(sender)) return true; Player player = target(sender, args, 1); if (player == null) return true;
        if (args.length < 3) { sender.sendMessage(ChatColor.YELLOW + "用法: /ysa level <玩家> <等级>"); return true; }
        plugin.setLevel(player, parseInt(args[2], 1)); sender.sendMessage(ChatColor.GREEN + "[YSA] 已设置等级 " + plugin.profile(player).level()); return true;
    }
    private Player target(CommandSender sender, String[] args, int index) {
        if (args.length <= index) { if (sender instanceof Player p) return p; sender.sendMessage(ChatColor.YELLOW + "请指定在线玩家。"); return null; }
        Player player = plugin.getServer().getPlayerExact(args[index]);
        if (player == null) { sender.sendMessage(ChatColor.RED + "找不到在线玩家: " + args[index]); return null; }
        if (sender instanceof Player owner && !owner.getUniqueId().equals(player.getUniqueId()) && !sender.hasPermission("ysa.admin")) {
            sender.sendMessage(ChatColor.RED + "只能查询自己的 YSA 数据。"); return null;
        }
        return player;
    }
    private boolean admin(CommandSender sender) { if (!sender.hasPermission("ysa.admin")) { sender.sendMessage(ChatColor.RED + "没有权限。"); return false; } return true; }
    private static boolean equals(String[] args, int index, String value) { return args.length > index && args[index].equalsIgnoreCase(value); }
    private static int parseInt(String value, int fallback) { try { return Math.max(1, Integer.parseInt(value)); } catch (NumberFormatException ex) { throw new IllegalArgumentException("等级必须是整数"); } }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return prefix(ROOT, args[0]);
        String root = args[0].toLowerCase(Locale.ROOT);
        if (Set.of("attr", "resource", "role", "level", "profile").contains(root) && args.length == 2) return prefix(onlinePlayers(), args[1]);
        if (root.equals("attr") && args.length == 3) return prefix(plugin.configuration().getAttributes().getAttributes().keySet(), args[2]);
        if (root.equals("resource") && args.length == 3) return prefix(plugin.configuration().getAttributes().getResources().keySet(), args[2]);
        if (root.equals("resource") && args.length == 4) return prefix(List.of("set", "add"), args[3]);
        if (root.equals("role") && args.length == 3) return prefix(plugin.configuration().getRoles().getRoles().keySet(), args[2]);
        return List.of();
    }
    private List<String> onlinePlayers() { return plugin.getServer().getOnlinePlayers().stream().map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList(); }
    private static List<String> prefix(Collection<String> values, String token) { String lower = token.toLowerCase(Locale.ROOT); return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(lower)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList()); }
}
