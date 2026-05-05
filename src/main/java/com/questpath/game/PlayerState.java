package com.questpath.game;

import java.util.Map;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import net.runelite.api.Skill;

/**
 * Immutable snapshot of "where the player is" — completed quests + skill levels
 * + QP. The planner reads this and the live game; tests construct it directly.
 *
 * Use the builder for ergonomic construction; missing fields default to a
 * fresh-account state (level 1 everything, nothing done). @Singular gives us
 * questState(id, status) / skillLevel(skill, level) builder methods.
 */
@Value
@Builder(toBuilder = true)
public class PlayerState
{
	@Singular
	Map<String, QuestStatus> questStates;

	@Singular
	Map<Skill, Integer> skillLevels;

	int questPoints;

	/** Fresh-account snapshot: level 1 everything, no quests done, 0 QP. */
	public static PlayerState empty()
	{
		return PlayerState.builder().build();
	}

	/** Defaults to NOT_STARTED for any quest not in the map. */
	public QuestStatus getQuestStatus(String questId)
	{
		QuestStatus s = questStates.get(questId);
		return s == null ? QuestStatus.NOT_STARTED : s;
	}

	/** Defaults to 1 for any skill not in the map. */
	public int getSkillLevel(Skill skill)
	{
		Integer level = skillLevels.get(skill);
		return level == null ? 1 : level;
	}
}
