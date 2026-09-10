package com.origin173.schoolBedrockLink.command;

import com.origin173.schoolBedrockLink.SchoolBedrockLink;
import com.origin173.schoolBedrockLink.audit.AuditLogger;
import com.origin173.schoolBedrockLink.config.PluginConfig;
import com.origin173.schoolBedrockLink.floodgate.BedrockIdentity;
import com.origin173.schoolBedrockLink.floodgate.FloodgateMapping;
import com.origin173.schoolBedrockLink.floodgate.FloodgateProbe;
import com.origin173.schoolBedrockLink.link.ApprovedLink;
import com.origin173.schoolBedrockLink.link.LinkService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class SchoolLinkCommand implements CommandExecutor, TabCompleter {

    private final SchoolBedrockLink plugin;

    public SchoolLinkCommand(SchoolBedrockLink plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("schoolbedrocklink.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限执行此命令。");
            return true;
        }
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            if (args.length == 1 || args.length == 0) {
                sendPluginStatus(sender);
            } else {
                sendPlayerStatus(sender, args[1]);
            }
            return true;
        }
        if ("unlink".equalsIgnoreCase(args[0]) && args.length >= 2) {
            unlink(sender, args[1]);
            return true;
        }
        if ("reload".equalsIgnoreCase(args[0]) && args.length == 1) {
            if (plugin.reloadPluginConfig()) {
                sender.sendMessage(ChatColor.GREEN + "配置已重新加载：OAuth、绑定、消息和限流设置已生效；"
                        + "监听地址、端口、public-base-url、请求超时和 development-mode 需要重启。"
                        + "OAuth 在途会话继续使用原配置；认证码设置变化会使旧码失效。");
            } else {
                sender.sendMessage(ChatColor.RED + "配置加载失败，保留旧配置。");
            }
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "用法: /schoollink status [player] | /schoollink unlink <player> | /schoollink reload");
        return true;
    }

    private void sendPluginStatus(CommandSender sender) {
        PluginConfig config = plugin.configuration();
        sender.sendMessage(ChatColor.AQUA + "SchoolBedrockLink " + (plugin.isReady() ? "READY" : "NOT READY"));
        sender.sendMessage(ChatColor.GRAY + "Floodgate API: " + yesNo(plugin.floodgate().isApiAvailable()));
        sender.sendMessage(ChatColor.GRAY + "PlayerLink: " + yesNo(plugin.floodgate().isReady())
                + " (" + plugin.floodgate().playerLinkName() + ")");
        sender.sendMessage(ChatColor.GRAY + "OAuth: " + yesNo(config.oauthConfigured()));
        sender.sendMessage(ChatColor.GRAY + "HTTP Server: " + yesNo(plugin.httpServer().isRunning()));
        sender.sendMessage(ChatColor.GRAY + "Approved Links: " + plugin.registry().snapshot().size()
                + (plugin.registry().isHealthy() ? "" : " (CORRUPT/FAIL CLOSED)"));
        sender.sendMessage(ChatColor.GRAY + "Pending Links: " + plugin.pending().size());
    }

    private void sendPlayerStatus(CommandSender sender, String identifier) {
        Player player = Bukkit.getPlayerExact(identifier);
        ApprovedLink approved = resolveApproved(identifier);
        BedrockIdentity identity = approved == null && player != null ? identityFor(player) :
                approved == null ? null : identityFor(approved);
        if (identity == null) {
            sender.sendMessage(ChatColor.YELLOW + "找不到该玩家的 Bedrock 身份或 Approved Link。"
                    + "离线查询请使用 Java UUID、Bedrock UUID 或 XUID。");
            return;
        }
        ApprovedLink finalApproved = approved;
        BedrockIdentity finalIdentity = identity;
        sender.sendMessage(ChatColor.GRAY + "正在查询 Floodgate Mapping...");
        CompletableFuture.runAsync(() -> {
            FloodgateMapping mapping = null;
            boolean available = false;
            if (plugin.floodgate().isReady()) {
                var lookup = plugin.floodgate().getMapping(finalIdentity.bedrockUuid(),
                        Duration.ofSeconds(plugin.configuration().http().requestTimeoutSeconds()));
                available = lookup.available();
                mapping = lookup.mapping();
            }
            FloodgateMapping finalMapping = mapping;
            boolean finalAvailable = available;
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(ChatColor.AQUA + "Bedrock UUID: " + finalIdentity.bedrockUuid());
                sender.sendMessage(ChatColor.AQUA + "Gamertag: " + finalIdentity.gamertag());
                sender.sendMessage(ChatColor.AQUA + "XUID: " + AuditLogger.maskXuid(finalIdentity.xuid()));
                sender.sendMessage(ChatColor.AQUA + "School Approved: " + yesNo(finalApproved != null));
                if (finalApproved != null) {
                    sender.sendMessage(ChatColor.AQUA + "Java Username: " + finalApproved.javaUsername());
                    sender.sendMessage(ChatColor.AQUA + "Java UUID: " + finalApproved.javaUuid());
                }
                sender.sendMessage(ChatColor.AQUA + "Floodgate Linked: " + (finalAvailable ? yesNo(finalMapping != null) : "UNKNOWN"));
                if (finalMapping != null) {
                    sender.sendMessage(ChatColor.AQUA + "Floodgate Java UUID: " + finalMapping.javaUuid());
                    sender.sendMessage(ChatColor.AQUA + "Floodgate Java Username: " + finalMapping.javaUsername());
                }
            });
        });
    }

    private void unlink(CommandSender sender, String identifier) {
        ApprovedLink approved = resolveApproved(identifier);
        if (approved == null) {
            sender.sendMessage(ChatColor.YELLOW + "找不到该玩家的 Approved Link。"
                    + "请使用在线玩家名、Java UUID、Bedrock UUID 或 XUID。");
            return;
        }
        ApprovedLink target = approved;
        sender.sendMessage(ChatColor.GRAY + "正在解绑并确认 Floodgate Mapping...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            LinkService.UnlinkOutcome outcome = plugin.links().unlink(target);
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(switch (outcome.status()) {
                case SUCCESS -> ChatColor.GREEN + "解绑成功，Approved Record 已删除。";
                case CONFLICT -> ChatColor.RED + "解绑被拒绝：Floodgate Mapping 存在冲突，未自动覆盖。";
                case NOT_READY -> ChatColor.RED + "Floodgate 或 Approved Registry 未就绪。";
                case FAILED -> ChatColor.RED + "解绑失败；请检查 Floodgate 日志和 Approved Registry。";
            }));
        });
    }

    private BedrockIdentity identityFor(Player player) {
        FloodgateProbe probe = plugin.floodgate().probe(player.getUniqueId());
        return probe.kind() == FloodgateProbe.Kind.BEDROCK ? probe.identity() : null;
    }

    private BedrockIdentity identityFor(ApprovedLink approved) {
        return new BedrockIdentity(approved.bedrockUuid(), approved.xuid(), approved.gamertag());
    }

    private ApprovedLink resolveApproved(String identifier) {
        ApprovedLink stable = plugin.registry().resolveIdentifier(identifier);
        if (stable != null || identifier.contains(":")) return stable;
        Player player = Bukkit.getPlayerExact(identifier);
        if (player != null) {
            ApprovedLink byJava = plugin.registry().findByJava(player.getUniqueId());
            if (byJava != null) {
                return byJava;
            }
            BedrockIdentity identity = identityFor(player);
            return identity == null ? null : plugin.registry().findByBedrock(identity.bedrockUuid());
        }

        return null;
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String yesNo(boolean value) {
        return value ? ChatColor.GREEN + "true" : ChatColor.RED + "false";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("schoolbedrocklink.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("status", "unlink", "reload").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && ("status".equalsIgnoreCase(args[0]) || "unlink".equalsIgnoreCase(args[0]))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> candidates = new ArrayList<>(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName).toList());
            plugin.registry().snapshot().forEach(link -> {
                candidates.add(link.javaUuid().toString());
                candidates.add(link.bedrockUuid().toString());
            });
            return candidates.stream().distinct()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        return List.of();
    }
}
