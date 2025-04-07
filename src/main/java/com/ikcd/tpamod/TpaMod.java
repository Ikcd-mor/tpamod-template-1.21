package com.ikcd.tpamod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import com.mojang.brigadier.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.util.math.Vec3d;
public class TpaMod implements ModInitializer {
	public static final String MOD_ID = "tpa-mod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("TPA Mod 初始化完成");
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			Commands.register(dispatcher);
		});
	}

	public static class Commands {
		private static final HashMap<UUID, Long> cooldowns = new HashMap<>();
		private static final long COOLDOWN_TIME = 1000;

		public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
			registerTeleportToPlayerCommand(dispatcher);
		}

		private static void registerTeleportToPlayerCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
			dispatcher.register(CommandManager.literal("tpa")
					.then(CommandManager.argument("targetPlayer", EntityArgumentType.player()) // 使用 EntityArgumentType
							.suggests((context, builder) -> {
								// 自动补全在线玩家名（原版已内置，可删除自定义补全逻辑）
								return CommandSource.suggestMatching(
										context.getSource().getServer().getPlayerNames(),
										builder
								);
							})
							.executes(context -> teleportToPlayer(
									context,
									EntityArgumentType.getPlayer(context, "targetPlayer") // 直接获取 PlayerEntity
							))
					)
			);
		}


		// 删除registerTeleportToCoordinateCommand方法
		// 删除teleportToCoordinates方法

		private static int teleportToPlayer(CommandContext<ServerCommandSource> context, PlayerEntity target) {
			ServerCommandSource source = context.getSource();
			PlayerEntity player = source.getPlayer();

			if (player == null) {
				source.sendError(Text.of("该命令只能在游戏内由玩家执行"));
				return 0;
			}

			UUID uuid = player.getUuid();
			if (!checkCooldown(uuid, source)) {
				return 0;
			}

			source.getServer().execute(() -> {
				try {
					// 直接使用已解析的 target 对象
					executeTeleport(player, target);
					source.sendFeedback(() -> Text.of("成功传送至玩家 " + target.getName().getString()), false);
				} catch (Exception e) {
					source.sendError(Text.of("传送失败: " + e.getMessage()));
				}
			});

			return Command.SINGLE_SUCCESS;
		}

		/**
		 * 冷却时间检查统一逻辑
		 * @return 是否通过冷却检查
		 */
		private static boolean checkCooldown(UUID uuid, ServerCommandSource source) {
			if (!canUseCommand(uuid)) {
				long remaining = getRemainingCooldown(uuid);
				source.sendError(Text.of("传送冷却剩余：" + remaining + "秒）"));
				return false;
			}
			setCooldown(uuid);
			return true;
		}

		/**
		 * 执行实际传送操作
		 */
		private static void executeTeleport(PlayerEntity player, PlayerEntity target) {
			// 获取目标玩家的维度和坐标
			ServerWorld targetWorld = (ServerWorld) target.getWorld();
			player.teleport(
					targetWorld,
					target.getX(),
					target.getY(),
					target.getZ(),
					Set.of(),
					target.getYaw(),
					target.getPitch()
			);
		}


		/**
		 * 检查指定玩家是否可以使用命令
		 * @param uuid 玩家UUID
		 * @return 是否已过冷却时间
		 */
		private static boolean canUseCommand(UUID uuid) {
			// 如果未记录或已过冷却时间则返回true
			return !cooldowns.containsKey(uuid) ||
					// 检查冷却状态
					(System.currentTimeMillis() - cooldowns.get(uuid)) >= COOLDOWN_TIME;
		}
		/**
		 * 设置玩家的最后使用时间戳
		 */
		private static void setCooldown(UUID uuid) {
			// 记录当前时间戳
			cooldowns.put(uuid, System.currentTimeMillis());
		}
		/**
		 * 计算剩余冷却时间（秒）
		 * @return 保证非负的剩余秒数
		 */
		private static long getRemainingCooldown(UUID uuid) {
			long elapsed = System.currentTimeMillis() - cooldowns.get(uuid);
			// 使用Math.max确保不会返回负数
			return Math.max(0, (COOLDOWN_TIME - elapsed) / 1000);
		}
	}
}