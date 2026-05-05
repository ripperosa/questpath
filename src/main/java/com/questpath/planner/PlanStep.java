package com.questpath.planner;

import lombok.Builder;
import lombok.Value;
import net.runelite.api.Skill;

/**
 * One unit of the plan. Either "do quest X", "train skill Y to level Z via
 * method M", or "train skill Y to level Z (no method known)".
 */
@Value
@Builder
public class PlanStep
{
	PlanStepType type;

	/** Quest id (for QUEST) or training method id (for TRAINING). null for SKILL_GATE. */
	String referenceId;

	/** Display title shown in the UI / console. */
	String title;

	/** Sub-line — e.g. "Magic 55 → 65 via High Alch" or "Prereq for Lunar Diplomacy". */
	String subtitle;

	/** Skill being trained (for TRAINING / SKILL_GATE). null for QUEST. */
	Skill skill;

	int currentLevel;
	int targetLevel;

	/** Hours estimate. 0 if unknown. */
	double estimatedHours;

	/** GP cost. Negative = profit. 0 if unknown. */
	double estimatedGpCost;

	/** Free-form rationale ("Required for Shilo Village"). */
	String reasoning;
}
