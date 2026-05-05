package com.questpath.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;

/**
 * Single source of truth for quest + training method lookups.
 *
 * Merge rule: hand-authored override entries beat wiki-fetched entries on the same id.
 * Wiki data fills gaps. The planner asks this class only — it doesn't know about either source.
 */
@Slf4j
@Singleton
public class QuestRepository
{
	private final Map<String, QuestDefinition> quests;
	private final List<TrainingMethod> trainingMethods;

	@Inject
	public QuestRepository(OverrideLoader overrideLoader, WikiDataFetcher wikiDataFetcher)
	{
		final OverrideData overrides = overrideLoader.getData();
		final Map<String, QuestDefinition> wikiQuests = wikiDataFetcher.fetchQuests();

		this.quests = sanitize(mergeQuests(overrides.getQuests(), wikiQuests));
		this.trainingMethods = overrides.getTrainingMethods() == null
			? Collections.emptyList()
			: new ArrayList<>(overrides.getTrainingMethods());

		log.info("QuestRepository ready: {} quests, {} training methods",
			this.quests.size(), this.trainingMethods.size());
	}

	/**
	 * Defensive cleanup before the data is exposed to the planner / tree view:
	 *   1. Drop self-referential prereqs ({@code X} → {@code X}). Quest Helper's
	 *      helper classes sometimes reference their own enum entry for state-tracking,
	 *      and our regex parser can't tell that from a real prereq edge — see
	 *      {@code gertrudes_cat} in the bundled data. A self-prereq would otherwise
	 *      explode the dependency tree into infinite recursion.
	 *   2. Drop dangling prereq references whose target id isn't in the bundle.
	 *      The planner already tolerates these but the tree view shouldn't waste
	 *      a row asking the user about a quest we know nothing about.
	 */
	private static Map<String, QuestDefinition> sanitize(Map<String, QuestDefinition> raw)
	{
		int selfPrereqsRemoved = 0;
		int danglingPrereqsRemoved = 0;
		for (QuestDefinition q : raw.values())
		{
			List<String> prereqs = q.getPrerequisiteQuestIds();
			if (prereqs == null || prereqs.isEmpty())
			{
				continue;
			}
			List<String> cleaned = new ArrayList<>(prereqs.size());
			for (String prereqId : prereqs)
			{
				if (prereqId == null || prereqId.equals(q.getId()))
				{
					selfPrereqsRemoved++;
					continue;
				}
				if (!raw.containsKey(prereqId))
				{
					danglingPrereqsRemoved++;
					continue;
				}
				cleaned.add(prereqId);
			}
			if (cleaned.size() != prereqs.size())
			{
				q.setPrerequisiteQuestIds(cleaned);
			}
		}
		if (selfPrereqsRemoved > 0 || danglingPrereqsRemoved > 0)
		{
			log.info("QuestRepository sanitized: dropped {} self-prereqs, {} dangling prereqs",
				selfPrereqsRemoved, danglingPrereqsRemoved);
		}
		return raw;
	}

	private static Map<String, QuestDefinition> mergeQuests(
		Map<String, QuestDefinition> overrides,
		Map<String, QuestDefinition> wiki)
	{
		final Map<String, QuestDefinition> merged = new HashMap<>();
		// Wiki first so overrides can replace.
		if (wiki != null)
		{
			merged.putAll(wiki);
		}
		if (overrides != null)
		{
			merged.putAll(overrides);
		}
		return merged;
	}

	/** Look up a quest by id. Returns null if unknown — caller decides what to do. */
	public QuestDefinition getQuest(String questId)
	{
		return quests.get(questId);
	}

	/** All known quests, in insertion order (i.e. unspecified). Read-only. */
	public Collection<QuestDefinition> getAllQuests()
	{
		return Collections.unmodifiableCollection(quests.values());
	}

	/** Training methods that target a given skill, in insertion order. */
	public List<TrainingMethod> getTrainingMethodsForSkill(Skill skill)
	{
		return trainingMethods.stream()
			.filter(m -> m.getSkill() == skill)
			.collect(Collectors.toList());
	}

	/** All known training methods. Read-only. */
	public List<TrainingMethod> getAllTrainingMethods()
	{
		return Collections.unmodifiableList(trainingMethods);
	}

	public int questCount()
	{
		return quests.size();
	}

	public int trainingMethodCount()
	{
		return trainingMethods.size();
	}
}
