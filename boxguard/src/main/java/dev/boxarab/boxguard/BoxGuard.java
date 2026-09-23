package dev.boxarab.boxguard;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BoxGuard implements ModInitializer {
    public static final String MOD_ID = "boxguard";

    private static boolean killAuraDetection = true;
    private static boolean autoTotemDetection = true;

    private static final Map<UUID, Deque<Long>> ATTACK_TIMES = new HashMap<>();
    private static final Map<UUID, Deque<UUID>> TARGET_HISTORY = new HashMap<>();
    private static final Map<UUID, ItemStack> LAST_OFFHAND = new HashMap<>();
    private static final Map<UUID, Deque<Long>> TOTEM_SWITCHES = new HashMap<>();

    private static final Map<UUID, SuspectState> SUSPECTS = new HashMap<>();
    private static final Set<UUID> STAFF_SYNCED = new HashSet<>();

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(
                BoxGuardPayloads.SuspectSyncPayload.TYPE,
                BoxGuardPayloads.SuspectSyncPayload.CODEC
        );
        PayloadTypeRegistry.playC2S().register(
                BoxGuardPayloads.RequestSuspectsPayload.TYPE,
                BoxGuardPayloads.RequestSuspectsPayload.CODEC
        );

        ServerPlayNetworking.registerGlobalReceiver(BoxGuardPayloads.RequestSuspectsPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.server.execute(() -> {
                if (player.hasPermissions(2)) sendSuspects(player);
            });
        });

        registerCommands();

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (!killAuraDetection) return InteractionResult.PASS;

            long now = System.currentTimeMillis();
            UUID id = serverPlayer.getUUID();
            Deque<Long> times = ATTACK_TIMES.computeIfAbsent(id, ignored -> new ArrayDeque<>());
            while (!times.isEmpty() && now - times.peekFirst() > 1000L) times.removeFirst();
            times.addLast(now);

            Deque<UUID> targets = TARGET_HISTORY.computeIfAbsent(id, ignored -> new ArrayDeque<>());
            while (targets.size() >= 12) targets.removeFirst();
            targets.addLast(entity.getUUID());

            long cps = times.size();
            long distinctTargets = targets.stream().distinct().count();
            if (cps >= 16 || distinctTargets >= 7) {
                warnKillAura(serverPlayer, cps, distinctTargets);
            }
            return InteractionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();

            if (autoTotemDetection) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    UUID id = player.getUUID();
                    ItemStack previous = LAST_OFFHAND.get(id);
                    ItemStack current = player.getOffhandItem();

                    boolean changedToTotem = !current.isEmpty()
                            && current.is(Items.TOTEM_OF_UNDYING)
                            && (previous == null || !previous.is(Items.TOTEM_OF_UNDYING));

                    if (changedToTotem && player.getHealth() <= 8.0F) {
                        Deque<Long> switches = TOTEM_SWITCHES.computeIfAbsent(id, ignored -> new ArrayDeque<>());
                        while (!switches.isEmpty() && now - switches.peekFirst() > 5000L) switches.removeFirst();
                        switches.addLast(now);
                        if (switches.size() >= 3) warnAutoTotem(player, switches.size());
                    }

                    LAST_OFFHAND.put(id, current.copy());
                }
            }

            // Allow fresh evidence after a cooldown instead of permanently flagging a player.
            SUSPECTS.values().removeIf(state -> now - state.lastEvidenceAt > 30 * 60_000L);
        });
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("boxguard")
                        .requires(source -> source.hasPermission(2))
                        .executes(BoxGuard::status)
                        .then(Commands.literal("detect")
                                .then(Commands.literal("killaura")
                                        .then(Commands.literal("on").executes(ctx -> setKillAura(ctx, true)))
                                        .then(Commands.literal("off").executes(ctx -> setKillAura(ctx, false))))
                                .then(Commands.literal("autototem")
                                        .then(Commands.literal("on").executes(ctx -> setAutoTotem(ctx, true)))
                                        .then(Commands.literal("off").executes(ctx -> setAutoTotem(ctx, false)))))
                        .then(Commands.literal("test")
                                .then(Commands.literal("killaura").executes(BoxGuard::testKillAura))
                                .then(Commands.literal("autototem").executes(BoxGuard::testAutoTotem)))
                        .then(Commands.literal("invsee")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(BoxGuard::openInvSee)))
                        .then(Commands.literal("suspects").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            sendSuspects(player);
                            player.sendSystemMessage(Component.literal("§bBoxGuard §7Suspect list sent to your admin GUI."));
                            return 1;
                        }))
                        .then(Commands.literal("reset")
                                .then(Commands.literal("suspects").executes(ctx -> {
                                    SUSPECTS.clear();
                                    syncAllStaff(ctx.getSource().getServer());
                                    ctx.getSource().sendSuccess(() -> Component.literal("§bBoxGuard §aSuspicion list cleared."), true);
                                    return 1;
                                })))
                        .then(Commands.literal("status").executes(BoxGuard::status))
        ));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§bBoxGuard §fStatus: §eKillAura=" + onOff(killAuraDetection)
                        + " §7| §eAutoTotem=" + onOff(autoTotemDetection)
                        + " §7| §eSuspects=" + SUSPECTS.size()), false);
        return 1;
    }

    private static String onOff(boolean value) {
        return value ? "§aON" : "§cOFF";
    }

    private static int setKillAura(CommandContext<CommandSourceStack> ctx, boolean value) {
        killAuraDetection = value;
        ctx.getSource().sendSuccess(() -> Component.literal("§bBoxGuard §7KillAura detector: " + onOff(value)), true);
        return 1;
    }

    private static int setAutoTotem(CommandContext<CommandSourceStack> ctx, boolean value) {
        autoTotemDetection = value;
        ctx.getSource().sendSuccess(() -> Component.literal("§bBoxGuard §7AutoTotem detector: " + onOff(value)), true);
        return 1;
    }

    private static int testKillAura(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§6[BoxGuard TEST] §fKillAura alert simulation triggered."), false);
        broadcastAlert(ctx.getSource().getServer(), "§cKillAura test alert §7(simulated; no punishment)");
        return 1;
    }

    private static int testAutoTotem(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§6[BoxGuard TEST] §fAutoTotem alert simulation triggered."), false);
        broadcastAlert(ctx.getSource().getServer(), "§cAutoTotem test alert §7(simulated; no punishment)");
        return 1;
    }

    private static int openInvSee(CommandContext<CommandSourceStack> ctx) throws Exception {
        ServerPlayer viewer = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        if (viewer.getUUID().equals(target.getUUID())) {
            viewer.displayClientMessage(Component.literal("§cBoxGuard: You cannot inspect yourself with InvSee."), false);
            return 0;
        }

        SimpleContainer snapshot = new SimpleContainer(45);
        Inventory inv = target.getInventory();
        for (int i = 0; i < 41; i++) snapshot.setItem(i, inv.getItem(i).copy());

        viewer.openMenu(new SimpleMenuProvider(
                (id, viewerInventory, ignored) -> new ReadOnlyInvSeeMenu(id, viewerInventory, snapshot),
                Component.literal("§bInvSee: §f" + target.getGameProfile().getName())
        ));

        viewer.displayClientMessage(Component.literal("§bBoxGuard §7Opened a read-only snapshot of §f" + target.getGameProfile().getName()), false);
        return 1;
    }

    public static final class ReadOnlyInvSeeMenu extends ChestMenu {
        public ReadOnlyInvSeeMenu(int id, Inventory inventory, Container container) {
            super(net.minecraft.world.inventory.MenuType.GENERIC_9x5, id, inventory, container, 5);
        }

        @Override
        public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType clickType, Player player) {
            if (slotId >= 0 && slotId < 45) return;
            super.clicked(slotId, button, clickType, player);
        }
    }

    private static void warnKillAura(ServerPlayer player, long cps, long distinctTargets) {
        addEvidence(player, 10, "KillAura (CPS=" + cps + ", targets=" + distinctTargets + ")");
        broadcastAlert(player.server, "§c[BoxGuard] §f" + player.getGameProfile().getName()
                + " §7triggered KillAura heuristics §8(CPS=" + cps + ", targets=" + distinctTargets + ")");
    }

    private static void warnAutoTotem(ServerPlayer player, int switches) {
        addEvidence(player, 8, "AutoTotem (5s switches=" + switches + ")");
        broadcastAlert(player.server, "§c[BoxGuard] §f" + player.getGameProfile().getName()
                + " §7triggered AutoTotem heuristics §8(switches=" + switches + ")");
    }

    private static void addEvidence(ServerPlayer player, int points, String flag) {
        UUID id = player.getUUID();
        SuspectState state = SUSPECTS.computeIfAbsent(id, ignored -> new SuspectState(player.getGameProfile().getName()));
        state.score += points;
        state.flags.add(flag);
        while (state.flags.size() > 5) state.flags.remove(0);
        state.lastEvidenceAt = System.currentTimeMillis();
        syncAllStaff(player.server);
    }

    private static void sendSuspects(ServerPlayer player) {
        if (!player.hasPermissions(2)) return;
        if (!ServerPlayNetworking.canSend(player, BoxGuardPayloads.SuspectSyncPayload.TYPE)) return;

        List<BoxGuardPayloads.SuspectEntry> entries = SUSPECTS.values().stream()
                .sorted(Comparator.comparingInt((SuspectState s) -> s.score).reversed())
                .map(s -> new BoxGuardPayloads.SuspectEntry(s.name, s.score, String.join(" | ", s.flags)))
                .toList();

        ServerPlayNetworking.send(player, new BoxGuardPayloads.SuspectSyncPayload(entries));
        STAFF_SYNCED.add(player.getUUID());
    }

    private static void syncAllStaff(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(2)) sendSuspects(player);
        }
    }

    private static void broadcastAlert(net.minecraft.server.MinecraftServer server, String message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(2)) player.sendSystemMessage(Component.literal(message));
        }
    }

    private static final class SuspectState {
        private final String name;
        private final List<String> flags = new ArrayList<>();
        private int score;
        private long lastEvidenceAt = System.currentTimeMillis();

        private SuspectState(String name) {
            this.name = name;
        }
    }
}
