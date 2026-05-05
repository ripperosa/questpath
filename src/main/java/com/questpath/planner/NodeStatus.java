package com.questpath.planner;

/**
 * Status of a quest node in the dependency graph after evaluation against
 * a PlayerState. Drives color coding in the Phase 4 UI.
 */
public enum NodeStatus
{
	/** Quest is already done — short-circuit; no plan step needed. */
	DONE,
	/** All prereq quests done AND all skill gates met — can do right now. */
	READY,
	/** At least one prereq quest is incomplete. */
	BLOCKED_BY_QUEST,
	/** All prereq quests done but at least one skill gate unmet. */
	BLOCKED_BY_SKILL
}
