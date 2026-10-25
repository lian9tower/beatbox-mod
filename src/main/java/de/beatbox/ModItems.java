package de.beatbox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.Rarity;

/**
 * Registriert für jede Zeile in assets/beatbox/discs.txt eine Musik-Disc.
 * Die Datei discs.txt wird von tools/generate_discs.py aus dem Ordner "discs/" erzeugt.
 */
public final class ModItems {
	private ModItems() {
	}

	/** Infos zu einer Disc (aus discs.txt). */
	public record DiscInfo(String id, String title, double lengthSeconds, int bpm) {
		/** Songlänge in Ticks (20 Ticks = 1 Sekunde) plus kleiner Puffer. */
		public long lengthTicks() {
			return (long) Math.ceil(lengthSeconds * 20.0) + 20L;
		}
	}

	/** Alle registrierten Discs: Item -> Infos. */
	public static final Map<Item, DiscInfo> DISCS = new LinkedHashMap<>();

	public static void initialize() {
		List<Item> items = new ArrayList<>();

		for (DiscInfo info : readIndex()) {
			ResourceKey<JukeboxSong> song = ResourceKey.create(Registries.JUKEBOX_SONG, id(info.id()));
			Item disc = register("disc_" + info.id(), Item::new,
					new Item.Properties().stacksTo(1).rarity(Rarity.RARE).jukeboxPlayable(song));
			DISCS.put(disc, info);
			items.add(disc);
		}

		// Alle Discs erscheinen im Kreativ-Reiter "Werkzeuge & Hilfsmittel" (bei den normalen Discs).
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(tab -> {
			for (Item disc : items) {
				tab.accept(disc);
			}
		});
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(BeatBoxMod.MOD_ID, path);
	}

	private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
		T item = factory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	/** Liest assets/beatbox/discs.txt aus der Mod-Datei. Format pro Zeile: id|titel|sekunden|bpm */
	private static List<DiscInfo> readIndex() {
		List<DiscInfo> result = new ArrayList<>();

		Optional<Path> file = FabricLoader.getInstance()
				.getModContainer(BeatBoxMod.MOD_ID)
				.flatMap(container -> container.findPath("assets/" + BeatBoxMod.MOD_ID + "/discs.txt"));

		if (file.isEmpty()) {
			BeatBoxMod.LOGGER.warn("discs.txt nicht gefunden - es werden keine Discs registriert.");
			return result;
		}

		try {
			for (String line : Files.readAllLines(file.get())) {
				line = line.strip();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				String[] parts = line.split("\\|");
				if (parts.length < 4) {
					BeatBoxMod.LOGGER.warn("Ungültige Zeile in discs.txt: {}", line);
					continue;
				}
				try {
					result.add(new DiscInfo(parts[0], parts[1], Double.parseDouble(parts[2]), Integer.parseInt(parts[3])));
				} catch (NumberFormatException e) {
					BeatBoxMod.LOGGER.warn("Ungültige Zahlen in discs.txt: {}", line);
				}
			}
		} catch (IOException e) {
			BeatBoxMod.LOGGER.error("discs.txt konnte nicht gelesen werden", e);
		}
		return result;
	}
}
