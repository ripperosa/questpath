package com.questpath.game;

/**
 * Mirror of RuneLite's QuestState but kept independent so the planner doesn't
 * couple to the live client API. Mapping happens in GameStateReader.
 */
public enum QuestStatus
{
	NOT_STARTED,
	IN_PROGRESS,
	DONE
}
