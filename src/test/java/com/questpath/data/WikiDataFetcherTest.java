package com.questpath.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;

/**
 * Sanity checks for the bundled wiki_data.json snapshot. Catches:
 *   - JSON shape regressions (rename a field and forget to update bundled data)
 *   - Skill enum name drift (Skill.WOODCUTTING etc.)
 *   - Missing must-have quests
 *   - Dangling prereq references between wiki entries
 */
public class WikiDataFetcherTest
{
	private Map<String, QuestDefinition> quests;

	@Before
	public void setUp()
	{
		quests = new WikiDataFetcher().fetchQuests();
	}

	@Test
	public void wikiBundleLoadsAndIsLarge()
	{
		assertNotNull(quests);
		assertTrue("Expected at least 150 quests in the wiki bundle, got " + quests.size(),
			quests.size() >= 150);
	}

	@Test
	public void coreQuestsArePresent()
	{
		// Must-haves — if these IDs disappear from the bundle, something's wrong upstream.
		// Quest Helper splits Recipe for Disaster into per-sub-quest entries
		// (recipe_for_disaster_start, ..._finale, etc.) — there's no umbrella entry,
		// so we assert the finale exists as the canonical "RFD completed" marker.
		String[] required = {
			"lunar_diplomacy",
			"the_fremennik_trials",
			"lost_city",
			"rune_mysteries",
			"shilo_village",
			"jungle_potion",
			"druidic_ritual",
			"dragon_slayer_i",
			"dragon_slayer_ii",
			"monkey_madness_i",
			"monkey_madness_ii",
			"recipe_for_disaster_finale",
			"cooks_assistant"
		};
		for (String id : required)
		{
			assertNotNull("Required quest missing from wiki bundle: " + id, quests.get(id));
		}
	}

	@Test
	public void prereqsResolveToBundleQuests()
	{
		for (QuestDefinition q : quests.values())
		{
			if (q.getPrerequisiteQuestIds() == null)
			{
				continue;
			}
			for (String prereq : q.getPrerequisiteQuestIds())
			{
				assertTrue(
					"Quest '" + q.getId() + "' references unknown prereq '" + prereq + "'",
					quests.containsKey(prereq));
			}
		}
	}

	@Test
	public void skillRequirementsUseValidEnumValues()
	{
		// Gson would fail to deserialize unknown Skill names, so reaching here proves
		// every key in skillRequirements is a real Skill. Belt-and-suspenders check that
		// at least one quest has at least one skill req.
		boolean foundOne = false;
		for (QuestDefinition q : quests.values())
		{
			Map<Skill, Integer> reqs = q.getSkillRequirements();
			if (reqs == null || reqs.isEmpty())
			{
				continue;
			}
			for (Skill s : reqs.keySet())
			{
				assertNotNull(s);
			}
			foundOne = true;
		}
		assertTrue("Expected at least one quest in the wiki bundle to have a skill requirement", foundOne);
	}

	@Test
	public void displayNamesPopulated()
	{
		for (QuestDefinition q : quests.values())
		{
			assertNotNull(q.getDisplayName());
			assertFalse("Empty displayName for id " + q.getId(), q.getDisplayName().isEmpty());
		}
	}

	@Test
	public void lunarDiplomacyHasMembersFlag()
	{
		// Note: questPointReward isn't extracted from Quest Helper source (it lives in
		// per-quest getQuestPointReward() methods we don't parse), so we only verify
		// the bundle correctly tags Lunar Diplomacy as members-only.
		QuestDefinition lunar = quests.get("lunar_diplomacy");
		assertNotNull(lunar);
		assertTrue("Lunar Diplomacy should be members", lunar.isMembers());
	}
}
