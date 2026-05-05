package com.questpath.planner;

import com.questpath.data.QuestDefinition;
import com.questpath.data.QuestRepository;
import com.questpath.data.TrainingMethod;
import com.questpath.game.PlayerState;
import com.questpath.game.QuestStatus;
import com.questpath.game.XpTable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;

/**
 * Orchestrator. Given a target quest and a PlayerState, returns an ordered
 * Plan: training steps inserted right before the quest that requires them,
 * then quests in topological order.
 *
 * Phase 3 design choices (deliberately simple):
 *   - One training method per skill gap (no chaining low-level → high-level methods)
 *   - Quest XP rewards do NOT close subsequent gaps yet (Phase 5)
 *   - No user weights yet (Phase 5)
 *   - SKILL_GATE step emitted when no method in DB qualifies — user fills in
 */
@Slf4j
@Singleton
public class PathPlanner
{
	private final QuestRepository repo;
	private final GapResolver gapResolver;

	@Inject
	public PathPlanner(QuestRepository repo, GapResolver gapResolver)
	{
		this.repo = repo;
		this.gapResolver = gapResolver;
	}

	public Plan plan(String targetQuestId, PlayerState state)
	{
		QuestDefinition target = repo.getQuest(targetQuestId);
		if (target == null)
		{
			log.warn("Unknown target quest id '{}'", targetQuestId);
			return Plan.empty(targetQuestId, targetQuestId);
		}

		DependencyGraph graph = DependencyGraph.build(repo, targetQuestId, state);

		// Filter to nodes still needing work, preserve insertion order.
		Map<String, QuestNode> pending = new LinkedHashMap<>();
		for (QuestNode node : graph.getNodes().values())
		{
			if (node.getStatus() != NodeStatus.DONE)
			{
				pending.put(node.getQuestId(), node);
			}
		}

		List<String> order = topoSort(pending);

		// Track skill levels and completed quests as we walk so we can re-evaluate
		// gates that earlier inserted training steps already covered.
		Map<Skill, Integer> skillLevelsSoFar = new EnumMap<>(Skill.class);
		state.getSkillLevels().forEach(skillLevelsSoFar::put);

		Set<String> completedSoFar = new HashSet<>();
		state.getQuestStates().forEach((id, status) -> {
			if (status == QuestStatus.DONE)
			{
				completedSoFar.add(id);
			}
		});

		List<PlanStep> steps = new ArrayList<>();
		for (String questId : order)
		{
			QuestNode node = pending.get(questId);
			for (SkillGate gate : node.getSkillGates())
			{
				int currentLevel = skillLevelsSoFar.getOrDefault(gate.getSkill(), 1);
				if (currentLevel >= gate.getRequiredLevel())
				{
					continue; // satisfied by earlier training or initial state
				}

				Optional<TrainingMethod> picked = gapResolver.pickMethodForSkill(
					gate.getSkill(), currentLevel, completedSoFar);

				if (picked.isPresent())
				{
					steps.add(buildTrainingStep(picked.get(), currentLevel, gate.getRequiredLevel(), node));
				}
				else
				{
					steps.add(buildSkillGateStep(gate.getSkill(), currentLevel, gate.getRequiredLevel(), node));
				}
				skillLevelsSoFar.put(gate.getSkill(), gate.getRequiredLevel());
			}
			steps.add(buildQuestStep(node));
			completedSoFar.add(questId);
		}

		return summarize(targetQuestId, target.getDisplayName(), steps);
	}

	private static List<String> topoSort(Map<String, QuestNode> nodes)
	{
		Map<String, Integer> inDegree = new HashMap<>();
		Map<String, List<String>> outgoing = new HashMap<>();
		for (String id : nodes.keySet())
		{
			inDegree.put(id, 0);
			outgoing.put(id, new ArrayList<>());
		}
		for (QuestNode n : nodes.values())
		{
			for (String prereq : n.getPrerequisiteQuestIds())
			{
				if (!nodes.containsKey(prereq))
				{
					continue; // prereq already DONE — skip edge
				}
				outgoing.get(prereq).add(n.getQuestId());
				inDegree.merge(n.getQuestId(), 1, Integer::sum);
			}
		}

		Deque<String> ready = new ArrayDeque<>();
		for (Map.Entry<String, Integer> e : inDegree.entrySet())
		{
			if (e.getValue() == 0)
			{
				ready.add(e.getKey());
			}
		}

		List<String> sorted = new ArrayList<>();
		while (!ready.isEmpty())
		{
			String id = ready.poll();
			sorted.add(id);
			for (String next : outgoing.get(id))
			{
				int d = inDegree.merge(next, -1, Integer::sum);
				if (d == 0)
				{
					ready.add(next);
				}
			}
		}

		if (sorted.size() != nodes.size())
		{
			log.warn("Topological sort incomplete: {} of {} nodes ordered (cycle?). Appending remainder unordered.",
				sorted.size(), nodes.size());
			for (String id : nodes.keySet())
			{
				if (!sorted.contains(id))
				{
					sorted.add(id);
				}
			}
		}
		return sorted;
	}

	private PlanStep buildQuestStep(QuestNode node)
	{
		String subtitle;
		if (node.getPrerequisiteQuestIds().isEmpty())
		{
			subtitle = "No quest prerequisites";
		}
		else
		{
			List<String> displayNames = new ArrayList<>();
			for (String prereqId : node.getPrerequisiteQuestIds())
			{
				QuestDefinition def = repo.getQuest(prereqId);
				displayNames.add(def != null ? def.getDisplayName() : prereqId);
			}
			subtitle = "Prereqs: " + String.join(", ", displayNames);
		}

		return PlanStep.builder()
			.type(PlanStepType.QUEST)
			.referenceId(node.getQuestId())
			.title("Quest: " + node.getDisplayName())
			.subtitle(subtitle)
			.reasoning("Required to reach the target quest.")
			.build();
	}

	private static PlanStep buildTrainingStep(
		TrainingMethod method, int currentLevel, int targetLevel, QuestNode forQuest)
	{
		int xpNeeded = XpTable.xpBetween(currentLevel, targetLevel);
		double hours = method.getXpPerHour() > 0 ? xpNeeded / method.getXpPerHour() : 0;
		double gpCost = -method.getGpPerHour() * hours; // gpPerHour > 0 means profit, so cost is negative
		String label = method.getSkill().getName() + " " + currentLevel + " → " + targetLevel;

		return PlanStep.builder()
			.type(PlanStepType.TRAINING)
			.referenceId(method.getId())
			.title("Train " + label)
			.subtitle("via " + humanize(method.getId())
				+ "  •  ~" + formatHours(hours) + "  •  " + formatGp(gpCost))
			.skill(method.getSkill())
			.currentLevel(currentLevel)
			.targetLevel(targetLevel)
			.estimatedHours(hours)
			.estimatedGpCost(gpCost)
			.reasoning("Needed for " + forQuest.getDisplayName())
			.build();
	}

	private static PlanStep buildSkillGateStep(Skill skill, int currentLevel, int targetLevel, QuestNode forQuest)
	{
		String label = skill.getName() + " " + currentLevel + " → " + targetLevel;
		return PlanStep.builder()
			.type(PlanStepType.SKILL_GATE)
			.title("Train " + label)
			.subtitle("No training method in database — fill in yourself")
			.skill(skill)
			.currentLevel(currentLevel)
			.targetLevel(targetLevel)
			.reasoning("Needed for " + forQuest.getDisplayName())
			.build();
	}

	private static Plan summarize(String targetId, String targetName, List<PlanStep> steps)
	{
		int questCount = 0;
		int trainingCount = 0;
		int gateCount = 0;
		double totalHours = 0;
		double totalGp = 0;
		for (PlanStep step : steps)
		{
			switch (step.getType())
			{
				case QUEST:
					questCount++;
					break;
				case TRAINING:
					trainingCount++;
					break;
				case SKILL_GATE:
					gateCount++;
					break;
			}
			totalHours += step.getEstimatedHours();
			totalGp += step.getEstimatedGpCost();
		}
		return new Plan(targetId, targetName, steps, questCount, trainingCount, gateCount, totalHours, totalGp);
	}

	private static String humanize(String snake)
	{
		StringBuilder sb = new StringBuilder();
		boolean upper = true;
		for (char c : snake.toCharArray())
		{
			if (c == '_')
			{
				sb.append(' ');
				upper = true;
			}
			else
			{
				sb.append(upper ? Character.toUpperCase(c) : c);
				upper = false;
			}
		}
		return sb.toString();
	}

	private static String formatHours(double hours)
	{
		if (hours <= 0)
		{
			return "?";
		}
		if (hours < 1)
		{
			return String.format("%.0f min", hours * 60);
		}
		return String.format("%.1f hr", hours);
	}

	private static String formatGp(double gp)
	{
		if (gp == 0)
		{
			return "free";
		}
		String sign = gp > 0 ? "-" : "+";
		double abs = Math.abs(gp);
		if (abs >= 1_000_000)
		{
			return sign + String.format("%.1fm gp", abs / 1_000_000);
		}
		if (abs >= 1_000)
		{
			return sign + String.format("%.0fk gp", abs / 1_000);
		}
		return sign + String.format("%.0f gp", abs);
	}
}
