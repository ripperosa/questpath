package com.questpath.planner;

public enum PlanStepType
{
	/** Do this quest. */
	QUEST,
	/** Train this skill via this method (we found a fitting method). */
	TRAINING,
	/** Train this skill, but the database has no method we could pick — user fills in. */
	SKILL_GATE
}
