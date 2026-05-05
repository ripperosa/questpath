package com.questpath.planner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * One quest in the dependency graph, after the graph builder has resolved its
 * prereqs and skill gates against a PlayerState.
 *
 * Mutable so the planner can flip status as it walks (e.g. a quest whose prereqs
 * become satisfied mid-walk after upstream quests get scheduled).
 */
@Data
@Builder
@AllArgsConstructor
public class QuestNode
{
	private final String questId;
	private final String displayName;

	/** Quest IDs this node depends on (only those also in the involved set). */
	@Builder.Default
	private final List<String> prerequisiteQuestIds = new ArrayList<>();

	/** Skill requirements, with current vs required filled in. */
	@Builder.Default
	private final List<SkillGate> skillGates = new ArrayList<>();

	private NodeStatus status;

	public List<SkillGate> unmetSkillGates()
	{
		List<SkillGate> unmet = new ArrayList<>();
		for (SkillGate g : skillGates)
		{
			if (!g.isMet())
			{
				unmet.add(g);
			}
		}
		return Collections.unmodifiableList(unmet);
	}
}
