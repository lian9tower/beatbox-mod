package de.beatbox;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeatBoxMod implements ModInitializer {
	public static final String MOD_ID = "beatbox";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModItems.initialize();
		DiscParty.initialize();
		LOGGER.info("Beatbox geladen: {} Disc(s)", ModItems.DISCS.size());
	}
}
