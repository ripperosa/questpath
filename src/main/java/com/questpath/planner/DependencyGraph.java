package com.questpath.planner;

import com.questpath.data.QuestDefinition;
import com.questpath.data.QuestRepository;
import com.questpath.game.PlayerState;
import com.questpath.game.QuestStatus;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;

/**
 * Builds the prereq DAG for a target quest and stamps each node with its
 * status against the supplied PlayerState.
 *
 * Defends against cycles (shouldn't exist in OSRS quests but log + skip if found).
 * Defends against missing quest definitions (log warning, treat as a leaf).
 */
@Slf4j
public final class DependencyGraph
{
	private final Map<String, QuestNode> nodesById;

	private DependencyGraph(Map<String, QuestNode> nodesById)
	{
		this.nodesById = nodesById;
	}

	public Map<String, QuestNode> getNodes()
	{
		return nodesById;
	}

	public QuestNode getNode(String questId)
	{
		return nodesById.get(questId);
	}

	public int size()
	{
		return nodesById.size();
	}

	/** Walks prereqs from {@code targetQuestId}, evaluating each node against {@code state}. */
	public static DependencyGraph build(QuestRepository repo, String targetQuestId, PlayerState state)
	{
		Map<String, QuestNode> nodes = new LinkedHashMap<>();
		Set<String> visiting = new HashSet<>();
		walk(targetQuestId, repo, state, nodes, visiting);
		return new DependencyGraph(nodes);
	}

	private static void walk(
		String questId,
		QuestRepository repo,
		PlayerState state,
		Map<String, QuestNode> nodes,
		Set<String> visiting)
	{
		if (nodes.containsKey(questId))
		{
			return;
		}
		if (!visiting.add(questId))
		{
			log.warn("Cycle detected at quest id '{}' — skipping re-entry", questId);
			return;
		}

		QuestDefinition def = repo.getQuest(questId);
		if (def == null)
		{
			log.warn("Quest '{}' referenced but not in repository — adding leaf placeholder", questId);
			nodes.put(questId, QuestNode.builder()
				.questId(questId)
				.displayName(questId)
				.status(NodeStatus.BLOCKED_BY_QUEST)
				.build());
			visiting.remove(questId);
			return;
		}

		// Recurse into prereqs first so they're inserted before the dependent quest.
		List<String> prereqIds = def.getPrerequisiteQuestIds() == null
			? new ArrayList<>()
			: new ArrayList<>(def.getPrerequisiteQuestIds());
		for (String prereqId : prereqIds)
		{
			walk(prereqId, repo, state, nodes, visiting);
		}

		List<SkillGate> gates = new ArrayList<>();
		if (def.getSkillRequirements() != null)
		{
			for (Map.Entry<Skill, Integer> req : def.getSkillRequirements().entrySet())
			{
				gates.add(new SkillGate(
					req.getKey(),
					req.getValue(),
					state.getSkillLevel(req.getKey())));
			}
		}

		NodeStatus status = computeStatus(questId, prereqIds, gates, state, nodes);

		nodes.put(questId, QuestNode.builder()
			.questId(questId)
			.displayName(def.getDisplayName())
			.prerequisiteQuestIds(prereqIds)
			.skillGates(gates)
			.status(status)
			.build());

		visiting.remove(questId);
	}

	private static NodeStatus computeStatus(
		String questId,
		List<String> prereqIds,
		List<SkillGate> gates,
		PlayerState state,
		Map<String, QuestNode> existingNodes)
	{
		if (state.getQuestStatus(questId) == QuestStatus.DONE)
		{
			return NodeStatus.DONE;
		}

		boolean prereqsBlocked = false;
		for (String prereqId : prereqIds)
		{
			QuestStatus s = state.getQuestStatus(prereqId);
			if (s != QuestStatus.DONE)
			{
				prereqsBlocked = true;
				break;
			}
		}

		boolean skillsBlocked = false;
		for (SkillGate gate : gates)
		{
			if (!gate.isMet())
			{
				skillsBlocked = true;
				break;
			}
		}

		if (prereqsBlocked)
		{
			return NodeStatus.BLOCKED_BY_QUEST;
		}
		if (skillsBlocked)
		{
			return NodeStatus.BLOCKED_BY_SKILL;
		}
		return NodeStatus.READY;
	}
}
