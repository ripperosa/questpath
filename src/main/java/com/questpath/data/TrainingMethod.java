package com.questpath.data;

import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.runelite.api.Skill;

/**
 * One way to train a skill — chopping willows, fletching darts, splashing, etc.
 *
 * The planner uses this to fill skill gaps that quest XP rewards alone don't close.
 * Numbers are approximate level-bucketed estimates; refinement is a Phase 6 problem.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingMethod
{
	/** Stable snake_case id (e.g. "willows_at_draynor"). */
	private String id;

	/** Skill this method trains. */
	private Skill skill;

	/** Minimum level to start (skill requirement). */
	private int minLevel;

	/** Level above which a faster method generally wins. Useful for ordering candidates. */
	private int maxEffectiveLevel;

	/** Approximate XP per hour at the level range. */
	private double xpPerHour;

	/** GP per hour: positive = profit, negative = cost. */
	private double gpPerHour;

	/** 0 (full attention) - 10 (set & forget). */
	private int afkRating;

	/** 0 (safe) - 10 (deep wilderness, PVP zone). */
	private int riskLevel;

	/**
	 * Other prereqs beyond minLevel. Loose schema:
	 *   {"QUEST": "shilo_village"}, {"SKILL_FISHING": "30"}, {"MEMBERS": "true"}
	 * The planner inspects the keys it cares about and ignores the rest.
	 */
	@Builder.Default
	private Map<String, String> requirements = new HashMap<>();

	/** Human-readable location for the UI. */
	private String location;

	/** Free-form tips (banking notes, click cycles, etc.). */
	private String notes;
}
