package com.questpath.data;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads the bundled overrides.json from the plugin classpath. This is the
 * authoritative data source — wins over wiki-fetched data when both exist.
 *
 * Phase 2: read once at startup. A future iteration may hot-reload from disk
 * for power users, but the bundled file is enough to get the planner running.
 */
@Slf4j
@Singleton
public class OverrideLoader
{
	private static final String RESOURCE_PATH = "/data/overrides.json";

	private final OverrideData cached;

	/**
	 * Plugin Hub forbids constructing fresh Gson instances in plugin code —
	 * we inject the client's shared one at runtime. Tests construct one
	 * explicitly via {@link #OverrideLoader(Gson)} since they don't run inside
	 * Guice.
	 */
	@Inject
	public OverrideLoader(Gson gson)
	{
		this.cached = loadFromClasspath(gson);
	}

	public OverrideData getData()
	{
		return cached;
	}

	private OverrideData loadFromClasspath(Gson gson)
	{
		try (InputStream in = getClass().getResourceAsStream(RESOURCE_PATH))
		{
			if (in == null)
			{
				log.warn("overrides.json not found on classpath at {}", RESOURCE_PATH);
				return emptyData();
			}

			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				OverrideData data = gson.fromJson(reader, OverrideData.class);
				if (data == null)
				{
					log.warn("overrides.json parsed to null — returning empty");
					return emptyData();
				}
				int questCount = data.getQuests() == null ? 0 : data.getQuests().size();
				int methodCount = data.getTrainingMethods() == null ? 0 : data.getTrainingMethods().size();
				log.info("Loaded overrides.json: {} quests, {} training methods", questCount, methodCount);
				return data;
			}
		}
		catch (IOException e)
		{
			log.error("Failed to read overrides.json", e);
			return emptyData();
		}
	}

	private static OverrideData emptyData()
	{
		return new OverrideData(new java.util.HashMap<>(), Collections.emptyList());
	}
}
