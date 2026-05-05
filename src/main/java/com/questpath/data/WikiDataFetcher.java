package com.questpath.data;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads bundled wiki snapshot data ({@code /data/wiki_data.json}). The snapshot
 * was scraped via the OSRS Wiki MediaWiki API at build time and contains every
 * quest's basic metadata (name, members, QP reward). A subset of quests also
 * have prereqs / skill requirements / skill rewards filled in from the wiki;
 * the rest carry empty collections (the planner sees them as quests with no
 * gates).
 *
 * Hand-authored {@link OverrideLoader} data wins over wiki data on id collision —
 * {@link QuestRepository} handles that merge.
 */
@Slf4j
@Singleton
public class WikiDataFetcher
{
	private static final String RESOURCE_PATH = "/data/wiki_data.json";

	private final Map<String, QuestDefinition> cached;

	public WikiDataFetcher()
	{
		this.cached = loadFromClasspath();
	}

	/** Returns wiki-derived quest definitions, keyed by id. Never null. */
	public Map<String, QuestDefinition> fetchQuests()
	{
		return cached;
	}

	private static Map<String, QuestDefinition> loadFromClasspath()
	{
		try (InputStream in = WikiDataFetcher.class.getResourceAsStream(RESOURCE_PATH))
		{
			if (in == null)
			{
				log.warn("wiki_data.json not on classpath at {} — wiki snapshot unavailable", RESOURCE_PATH);
				return Collections.emptyMap();
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				OverrideData data = new Gson().fromJson(reader, OverrideData.class);
				if (data == null || data.getQuests() == null)
				{
					return Collections.emptyMap();
				}
				log.info("Wiki snapshot loaded: {} quests", data.getQuests().size());
				return new HashMap<>(data.getQuests());
			}
		}
		catch (IOException e)
		{
			log.error("Failed to read wiki_data.json", e);
			return Collections.emptyMap();
		}
	}
}
