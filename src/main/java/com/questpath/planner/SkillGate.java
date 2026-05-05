package com.questpath.planner;

import lombok.Value;
import net.runelite.api.Skill;

/**
 * One skill requirement on a quest node, evaluated against the current player state.
 *
 * If currentLevel >= requiredLevel, {@link #isMet()} is true and the planner skips it.
 * Otherwise the planner inserts a TRAINING (or SKILL_GATE) step before the gating quest.
 */
@Value
public class SkillGate
{
	Skill skill;
	int requiredLevel;
	int currentLevel;

	public boolean isMet()
	{
		return currentLevel >= requiredLevel;
	}

	public int levelsToTrain()
	{
		return Math.max(0, requiredLevel - currentLevel);
	}
}
