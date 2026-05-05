package com.questpath.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;

/**
 * Sanity checks for the bundled overrides.json. Catches:
 *   - JSON shape regressions (rename a field and forget to update data)
 *   - Missing key quests in the seed Lunar Diplomacy chain
 *   - Skill enum name changes (Skill.WOODCUTTING → ...)
 *
 * Doesn't test data accuracy against the wiki — that's a Phase 6 problem.
 */
public class OverrideLoaderTest
{
	private OverrideData data;

	@Before
	public void setUp()
	{
		data = new OverrideLoader().getData();
	}

	@Test
	public void overrideFileLoadsAndParses()
	{
		assertNotNull("OverrideData was null", data);
		assertNotNull("quests map was null", data.getQuests());
		assertNotNull("trainingMethods list was null", data.getTrainingMethods());

		assertFalse("Expected at least one quest in seed data", data.getQuests().isEmpty());
		assertFalse("Expected at least one training method in seed data",
			data.getTrainingMethods().isEmpty());
	}

	@Test
	public void lunarDiplomacyHasExpectedShape()
	{
		QuestDefinition lunar = data.getQuests().get("lunar_diplomacy");
		assertNotNull("Lunar Diplomacy missing from seed data", lunar);

		assertEquals("lunar_diplomacy", lunar.getId());
		assertEquals("Lunar Diplomacy", lunar.getDisplayName());
		assertTrue("Lunar Diplomacy should be members-only", lunar.isMembers());

		assertTrue("Lunar Diplomacy should require Fremennik Trials",
			lunar.getPrerequisiteQuestIds().contains("the_fremennik_trials"));
		assertTrue("Lunar Diplomacy should require Lost City",
			lunar.getPrerequisiteQuestIds().contains("lost_city"));
		assertTrue("Lunar Diplomacy should require Rune Mysteries",
			lunar.getPrerequisiteQuestIds().contains("rune_mysteries"));
		assertTrue("Lunar Diplomacy should require Shilo Village",
			lunar.getPrerequisiteQuestIds().contains("shilo_village"));

		Integer magicReq = lunar.getSkillRequirements().get(Skill.MAGIC);
		assertNotNull("Lunar Diplomacy should have a Magic requirement", magicReq);
		assertEquals("Lunar Diplomacy Magic req", 65, (int) magicReq);

		Integer craftingReq = lunar.getSkillRequirements().get(Skill.CRAFTING);
		assertNotNull("Lunar Diplomacy should have a Crafting requirement", craftingReq);
		assertEquals("Lunar Diplomacy Crafting req", 61, (int) craftingReq);
	}

	@Test
	public void chainedPrereqsAllResolve()
	{
		// Override entries are allowed to reference quests that live in the
		// bundled wiki snapshot rather than the overrides file (e.g. our
		// curse_of_the_empty_lord override references desert_treasure /
		// the_restless_ghost which come from the wiki bundle). So we
		// validate against the *merged* repository view, not just overrides.
		java.util.Map<String, QuestDefinition> merged = new java.util.HashMap<>();
		merged.putAll(new WikiDataFetcher().fetchQuests());
		merged.putAll(data.getQuests());

		for (QuestDefinition q : data.getQuests().values())
		{
			for (String prereqId : q.getPrerequisiteQuestIds())
			{
				assertTrue(
					"Override '" + q.getId() + "' has unresolved prereq id: " + prereqId,
					merged.containsKey(prereqId));
			}
		}
	}

	@Test
	public void trainingMethodsHaveSkillAndIdSet()
	{
		for (TrainingMethod m : data.getTrainingMethods())
		{
			assertNotNull("Training method missing id", m.getId());
			assertFalse("Training method id is blank", m.getId().isEmpty());
			assertNotNull("Training method '" + m.getId() + "' missing skill", m.getSkill());
		}
	}
}
