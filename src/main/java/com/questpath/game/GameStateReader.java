package com.questpath.game;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * Reads the live RuneLite client into a PlayerState snapshot. When not logged
 * in (LOGIN_SCREEN, HOPPING, etc.) returns PlayerState.empty() so the planner
 * can still produce a fresh-account plan.
 *
 * Phase 3 is mostly stub-driven (Rodney can't log in dev RuneLite with Jagex
 * auth) but the wiring is real — once we have a logged-in test account, this
 * reads accurately without further changes.
 */
@Slf4j
@Singleton
public class GameStateReader
{
	private final Client client;

	@Inject
	public GameStateReader(Client client)
	{
		this.client = client;
	}

	/** Snapshots the current player state, or empty() when not logged in. */
	public PlayerState snapshot()
	{
		if (client == null || client.getGameState() != GameState.LOGGED_IN)
		{
			log.debug("GameStateReader: not logged in, returning empty PlayerState");
			return PlayerState.empty();
		}

		Map<String, QuestStatus> questStates = new HashMap<>();
		for (Quest quest : Quest.values())
		{
			questStates.put(idForQuest(quest), translate(quest.getState(client)));
		}

		Map<Skill, Integer> skillLevels = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			skillLevels.put(skill, client.getRealSkillLevel(skill));
		}

		// QP read deferred — VarPlayer constant differs across RuneLite versions.
		// Phase 3 doesn't gate on QP yet; Phase 5+ will.
		PlayerState.PlayerStateBuilder builder = PlayerState.builder().questPoints(0);

		// PlayerState's @Singular doesn't accept Maps directly; fold them in.
		questStates.forEach(builder::questState);
		skillLevels.forEach(builder::skillLevel);

		return builder.build();
	}

	/** Converts RuneLite's Quest enum name into the snake_case ids the planner uses. */
	static String idForQuest(Quest quest)
	{
		return quest.name().toLowerCase();
	}

	private static QuestStatus translate(QuestState state)
	{
		if (state == null)
		{
			return QuestStatus.NOT_STARTED;
		}
		switch (state)
		{
			case FINISHED:
				return QuestStatus.DONE;
			case IN_PROGRESS:
				return QuestStatus.IN_PROGRESS;
			case NOT_STARTED:
			default:
				return QuestStatus.NOT_STARTED;
		}
	}
}
