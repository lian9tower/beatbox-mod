package de.beatbox;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Partikel-Lichtshow: Sobald eine Beatbox-Disc in eine Jukebox gelegt wird, startet um die
 * Jukebox eine Show im Takt der Musik (BPM steht im Dateinamen, z. B. "Song_128bpm.ogg").
 *
 * Läuft komplett auf dem Server - die Spieler sehen die Partikel, ohne dass der Client
 * extra Code braucht.
 */
public final class DiscParty {
	private DiscParty() {
	}

	/** Spieler weiter weg als so viele Blöcke sehen die Show sowieso nicht. */
	private static final double SHOW_RANGE = 48.0;

	/** Eine laufende (oder gleich startende) Show an einer Jukebox. */
	private static final class Show {
		final ModItems.DiscInfo disc;
		final long createdTick;
		long startTick = -1;

		Show(ModItems.DiscInfo disc, long createdTick) {
			this.disc = disc;
			this.createdTick = createdTick;
		}
	}

	private static final Map<ServerLevel, Map<BlockPos, Show>> SHOWS = new WeakHashMap<>();

	public static void initialize() {
		// Rechtsklick auf eine Jukebox mit einer Beatbox-Disc in der Hand merken.
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (!(level instanceof ServerLevel serverLevel)) {
				return InteractionResult.PASS;
			}
			ItemStack held = player.getItemInHand(hand);
			ModItems.DiscInfo disc = ModItems.DISCS.get(held.getItem());
			if (disc == null) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hitResult.getBlockPos();
			if (!serverLevel.getBlockState(pos).is(Blocks.JUKEBOX)) {
				return InteractionResult.PASS;
			}
			SHOWS.computeIfAbsent(serverLevel, l -> new HashMap<>())
					.put(pos.immutable(), new Show(disc, serverLevel.getGameTime()));
			return InteractionResult.PASS; // Minecraft legt die Disc ganz normal ein
		});

		// Jeden Tick alle Shows weiterlaufen lassen.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerLevel level : server.getAllLevels()) {
				tickLevel(level);
			}
		});
	}

	private static void tickLevel(ServerLevel level) {
		Map<BlockPos, Show> shows = SHOWS.get(level);
		if (shows == null || shows.isEmpty()) {
			return;
		}
		long now = level.getGameTime();

		Iterator<Map.Entry<BlockPos, Show>> it = shows.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<BlockPos, Show> entry = it.next();
			BlockPos pos = entry.getKey();
			Show show = entry.getValue();

			// Song zu Ende? Dann ist auch die Show vorbei.
			long reference = show.startTick >= 0 ? show.startTick : show.createdTick;
			if (now - reference > show.disc.lengthTicks()) {
				it.remove();
				continue;
			}

			double x = pos.getX() + 0.5;
			double y = pos.getY() + 1.0;
			double z = pos.getZ() + 0.5;
			if (!playerNearby(level, x, y, z)) {
				continue; // niemand in der Nähe -> nichts tun (und keine Chunks laden)
			}

			BlockState state = level.getBlockState(pos);
			boolean hasRecord = state.is(Blocks.JUKEBOX) && state.getValue(BlockStateProperties.HAS_RECORD);

			if (show.startTick < 0) {
				// Disc wurde noch nicht eingelegt - kurz warten, sonst vergessen.
				if (!hasRecord) {
					if (now - show.createdTick > 10) {
						it.remove();
					}
					continue;
				}
				show.startTick = now;
			} else if (!hasRecord) {
				// Disc wurde wieder herausgenommen (oder die Jukebox abgebaut).
				it.remove();
				continue;
			}

			play(level, x, y, z, now - show.startTick, show.disc.bpm());
		}
	}

	private static boolean playerNearby(ServerLevel level, double x, double y, double z) {
		double rangeSq = SHOW_RANGE * SHOW_RANGE;
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(x, y, z) < rangeSq) {
				return true;
			}
		}
		return false;
	}

	/** Ein Tick der Show. t = Ticks seit Songstart. */
	private static void play(ServerLevel level, double x, double y, double z, long t, int bpm) {
		double beatsPerTick = bpm / 1200.0; // 20 Ticks pro Sekunde, 60 Sekunden pro Minute
		long beat = (long) Math.floor(t * beatsPerTick);
		boolean onBeat = beat != (long) Math.floor((t - 1) * beatsPerTick);

		// 1) Leuchtende Helix, die dauernd nach oben wandert (jeden Tick)
		double angle = t * 0.35;
		double rise = (t % 50) / 50.0 * 3.2;
		for (int i = 0; i < 3; i++) {
			double a = angle + i * (Math.PI * 2.0 / 3.0);
			level.sendParticles(ParticleTypes.END_ROD, x + Math.cos(a), y + rise, z + Math.sin(a), 1, 0, 0, 0, 0);
		}

		if (!onBeat) {
			return;
		}

		// 2) Auf jeden Beat: Regenbogen-Ring aus Noten (die x-Geschwindigkeit bestimmt die Farbe)
		int notes = 16;
		for (int i = 0; i < notes; i++) {
			double a = i * (Math.PI * 2.0 / notes);
			double hue = ((i + beat) % 24) / 24.0;
			level.sendParticles(ParticleTypes.NOTE, x + Math.cos(a) * 1.4, y + 0.3, z + Math.sin(a) * 1.4, 0, hue, 0, 0, 1.0);
		}

		// 3) Auf jeden Beat: Funken-Druckwelle nach außen
		int sparks = 20;
		for (int i = 0; i < sparks; i++) {
			double a = i * (Math.PI * 2.0 / sparks);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.2, z, 0, Math.cos(a), 0.05, Math.sin(a), 0.35);
		}

		// 4) Jeder 4. Beat ("Drop"): Feuerwerk + Konfetti
		if (beat % 4 == 0) {
			level.sendParticles(ParticleTypes.FIREWORK, x, y + 1.0, z, 30, 0.2, 0.2, 0.2, 0.2);
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y + 0.8, z, 25, 0.5, 0.6, 0.5, 0.3);
		}

		// 5) Jeder 8. Beat: Licht-Säulen schießen in den Himmel
		if (beat % 8 == 0) {
			for (int i = 0; i < 24; i++) {
				double ox = Math.sin(i * 12.9898 + beat) * 0.6;
				double oz = Math.cos(i * 78.233 + beat) * 0.6;
				double speed = 0.5 + Math.abs(Math.sin(i * 3.1 + beat)) * 0.6;
				level.sendParticles(ParticleTypes.END_ROD, x + ox, y + 0.2, z + oz, 0, 0, 1.0, 0, speed);
			}
		}
	}
}
