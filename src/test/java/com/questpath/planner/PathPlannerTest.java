package com.questpath.planner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.questpath.data.OverrideLoader;
import com.questpath.data.QuestRepository;
import com.questpath.data.WikiDataFetcher;
import com.questpath.game.PlayerState;
import com.questpath.game.QuestStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;

/**
 * End-to-end planner test against the real seed overrides.json. We don't mock
 * the repository — that defeats the point. Instead we verify the planner
 * produces a sensible plan against fully-known data.
 */
public class PathPlannerTest
{
	private static final String LUNAR_DIPLOMACY = "lunar_diplomacy";

	private PathPlanner planner;

	@Before
	public void setUp()
	{
		QuestRepository repo = new QuestRepository(new OverrideLoader(), new WikiDataFetcher());
		// null config — GapResolver falls back to weight=5 for each preference.
		GapResolver resolver = new GapResolver(repo, null);
		this.planner = new PathPlanner(repo, resolver);
	}

	@Test
	public void freshAccountTargetingLunarDiplomacyHasAllPrereqs()
	{
		Plan plan = planner.plan(LUNAR_DIPLOMACY, PlayerState.empty());

		assertEquals("lunar_diplomacy", plan.getTargetQuestId());
		assertFalse("Plan should not be empty for a fresh account", plan.getSteps().isEmpty());

		List<String> questIdsInOrder = questIdsFromPlan(plan);

		// All 7 seed quests should appear.
		String[] expected = {
			"druidic_ritual", "jungle_potion", "shilo_village",
			"rune_mysteries", "lost_city", "the_fremennik_trials", "lunar_diplomacy"
		};
		for (String q : expected)
		{
			assertTrue("Plan missing quest: " + q, questIdsInOrder.contains(q));
		}
		assertEquals(expected.length, plan.getQuestStepCount());
	}

	@Test
	public void planRespectsTopologicalOrder()
	{
		Plan plan = planner.plan(LUNAR_DIPLOMACY, PlayerState.empty());
		List<String> order = questIdsFromPlan(plan);
		Map<String, Integer> idx = new HashMap<>();
		for (int i = 0; i < order.size(); i++)
		{
			idx.put(order.get(i), i);
		}
		// Each chain edge must hold: prereq before dependent.
		assertBefore(idx, "druidic_ritual", "jungle_potion");
		assertBefore(idx, "jungle_potion", "shilo_village");
		assertBefore(idx, "shilo_village", "lunar_diplomacy");
		assertBefore(idx, "the_fremennik_trials", "lunar_diplomacy");
		assertBefore(idx, "lost_city", "lunar_diplomacy");
		assertBefore(idx, "rune_mysteries", "lunar_diplomacy");
	}

	@Test
	public void planEmitsTrainingOrSkillGateForUnmetSkillReqs()
	{
		Plan plan = planner.plan(LUNAR_DIPLOMACY, PlayerState.empty());

		// Lunar Diplomacy needs Magic 65 / Crafting 61 / Firemaking 49 etc.
		// At level-1 across the board, every gate must surface as TRAINING or SKILL_GATE.
		int trainingOrGate = plan.getTrainingStepCount() + plan.getSkillGateStepCount();
		assertTrue("Expected at least one training/gate step on a fresh account, got "
			+ trainingOrGate, trainingOrGate > 0);
	}

	@Test
	public void allRequirementsMetProducesQuestsOnly()
	{
		// Player has every skill at 99 → no skill gates surface.
		PlayerState.PlayerStateBuilder builder = PlayerState.builder();
		for (Skill s : Skill.values())
		{
			if (s == Skill.OVERALL)
			{
				continue;
			}
			builder.skillLevel(s, 99);
		}
		Plan plan = planner.plan(LUNAR_DIPLOMACY, builder.build());

		assertEquals("No training when all skills 99", 0, plan.getTrainingStepCount());
		assertEquals("No skill gates when all skills 99", 0, plan.getSkillGateStepCount());
		assertTrue("Quests should still be present",
			plan.getQuestStepCount() > 0);
	}

	@Test
	public void targetAlreadyDoneProducesEmptyPlan()
	{
		PlayerState done = PlayerState.builder()
			.questState(LUNAR_DIPLOMACY, QuestStatus.DONE)
			.questState("druidic_ritual", QuestStatus.DONE)
			.questState("jungle_potion", QuestStatus.DONE)
			.questState("shilo_village", QuestStatus.DONE)
			.questState("rune_mysteries", QuestStatus.DONE)
			.questState("lost_city", QuestStatus.DONE)
			.questState("the_fremennik_trials", QuestStatus.DONE)
			.build();
		Plan plan = planner.plan(LUNAR_DIPLOMACY, done);
		assertTrue("Plan should be empty when target already done", plan.isAlreadyComplete());
	}

	@Test
	public void unknownTargetReturnsEmptyPlan()
	{
		Plan plan = planner.plan("not_a_real_quest", PlayerState.empty());
		assertNotNull(plan);
		assertTrue(plan.getSteps().isEmpty());
	}

	private static void assertBefore(Map<String, Integer> idx, String earlier, String later)
	{
		Integer a = idx.get(earlier);
		Integer b = idx.get(later);
		assertNotNull("Plan missing quest: " + earlier, a);
		assertNotNull("Plan missing quest: " + later, b);
		assertTrue(earlier + " should appear before " + later
			+ " (got " + a + " vs " + b + ")", a < b);
	}

	private static List<String> questIdsFromPlan(Plan plan)
	{
		List<String> ids = new ArrayList<>();
		for (PlanStep step : plan.getSteps())
		{
			if (step.getType() == PlanStepType.QUEST)
			{
				ids.add(step.getReferenceId());
			}
		}
		return ids;
	}
}
