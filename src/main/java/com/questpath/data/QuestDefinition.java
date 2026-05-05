package com.questpath.data;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.runelite.api.Skill;

/**
 * One quest's definition — name, prereqs, skill walls, rewards.
 *
 * Source priority at load time: hand-authored overrides.json wins, wiki fills gaps.
 * Field defaults stay non-null so the planner can iterate without null checks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestDefinition
{
	/** Stable snake_case id (e.g. "lunar_diplomacy"). Used in prereq edges and serialization keys. */
	private String id;

	/** Display name as shown in-game (e.g. "Lunar Diplomacy"). */
	private String displayName;

	/** Quest points awarded on completion. */
	private int questPointReward;

	/** Quest IDs that must be completed before this quest can start. */
	@Builder.Default
	private List<String> prerequisiteQuestIds = new java.util.ArrayList<>();

	/** Skill level required to start the quest. Boostable distinctions live elsewhere (future). */
	@Builder.Default
	private Map<Skill, Integer> skillRequirements = new EnumMap<>(Skill.class);

	/** XP rewards granted on completion, per skill. */
	@Builder.Default
	private Map<Skill, Integer> skillRewards = new EnumMap<>(Skill.class);

	/** Recommended (not strict) combat level. 0 if irrelevant. */
	private int recommendedCombatLevel;

	/** Canonical wiki URL for the quest. */
	private String wikiUrl;

	/** Members-only? */
	private boolean members;

	/** Wiki estimate of completion time in minutes. */
	private int estimatedMinutes;

	/** Free-form notes; useful for "boostable", "AFK", caveats etc. */
	private String notes;

	/**
	 * Quest Helper category: "F2P" / "P2P" / "MINIQUEST" / "ACHIEVEMENT_DIARY".
	 * Null for hand-authored entries that don't specify it.
	 */
	private String questType;

	/**
	 * Quest Helper difficulty: NOVICE / INTERMEDIATE / EXPERIENCED / MASTER /
	 * GRANDMASTER / SPECIAL / MINIQUEST / ACHIEVEMENT_DIARY. Null when unknown.
	 */
	private String difficulty;

	/** Quest points required to start the quest (if any). 0 = no QP gate. */
	private int questPointRequirement;
}
