package com.questpath;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(QuestPathConfig.GROUP)
public interface QuestPathConfig extends Config
{
	String GROUP = "questpath";

	String TARGET_QUEST_KEY = "targetQuestId";
	String FILTER_MODE_KEY = "filterMode";
	String DIFFICULTY_FILTER_KEY = "difficultyFilter";
	String SORT_MODE_KEY = "sortMode";
	String TIME_WEIGHT_KEY = "timeWeight";
	String GP_WEIGHT_KEY = "gpWeight";
	String AFK_WEIGHT_KEY = "afkWeight";
	String HELP_EXPANDED_KEY = "helpExpanded";

	enum FilterMode
	{
		ALL("Show all"),
		QUEST("Quest"),
		MINIQUEST("Miniquest"),
		ACHIEVEMENT_DIARY("Achievement diary"),
		FREE_TO_PLAY("Free-to-Play"),
		MEMBERS("Members"),
		MEETS_REQS("Meets requirements");

		private final String label;
		FilterMode(String label) { this.label = label; }
		public String getLabel() { return label; }
		@Override public String toString() { return label; }
	}

	enum DifficultyFilter
	{
		ALL("All difficulties"),
		NOVICE("Novice"),
		INTERMEDIATE("Intermediate"),
		EXPERIENCED("Experienced"),
		MASTER("Master"),
		GRANDMASTER("Grandmaster"),
		SPECIAL("Special");

		private final String label;
		DifficultyFilter(String label) { this.label = label; }
		public String getLabel() { return label; }
		@Override public String toString() { return label; }
	}

	enum SortMode
	{
		A_Z("A → Z"),
		Z_A("Z → A"),
		QP_DESC("Quest Points ↓"),
		QP_ASC("Quest Points ↑");

		private final String label;
		SortMode(String label) { this.label = label; }
		public String getLabel() { return label; }
		@Override public String toString() { return label; }
	}

	@ConfigItem(
		keyName = TARGET_QUEST_KEY,
		name = "Target quest",
		description = "The quest you're trying to reach. Persisted across sessions; usually changed via the sidebar dropdown rather than this field."
	)
	default String targetQuestId()
	{
		return "lunar_diplomacy";
	}

	@ConfigItem(
		keyName = FILTER_MODE_KEY,
		name = "Filter mode",
		description = "Which quests to show in the target dropdown."
	)
	default FilterMode filterMode()
	{
		return FilterMode.ALL;
	}

	@ConfigItem(
		keyName = DIFFICULTY_FILTER_KEY,
		name = "Difficulty filter",
		description = "Restrict the target dropdown to a specific quest difficulty band."
	)
	default DifficultyFilter difficultyFilter()
	{
		return DifficultyFilter.ALL;
	}

	@ConfigItem(
		keyName = SORT_MODE_KEY,
		name = "Sort order",
		description = "How the target dropdown is ordered."
	)
	default SortMode sortMode()
	{
		return SortMode.A_Z;
	}

	@ConfigItem(
		keyName = TIME_WEIGHT_KEY,
		name = "Time preference",
		description = "How much to weight training method speed (0 = ignore, 10 = heavy preference). Higher = pick fastest methods."
	)
	default int timeWeight()
	{
		return 5;
	}

	@ConfigItem(
		keyName = GP_WEIGHT_KEY,
		name = "GP preference",
		description = "How much to weight training method profitability (0 = ignore, 10 = heavy preference). Higher = pick methods that earn / lose less GP."
	)
	default int gpWeight()
	{
		return 5;
	}

	@ConfigItem(
		keyName = AFK_WEIGHT_KEY,
		name = "AFK preference",
		description = "How much to weight how AFK-friendly methods are (0 = ignore, 10 = heavy preference). Higher = pick low-attention methods."
	)
	default int afkWeight()
	{
		return 5;
	}

	@ConfigItem(
		keyName = HELP_EXPANDED_KEY,
		name = "Help panel expanded",
		description = "Whether the Help & Tips panel in the sidebar starts expanded. Toggled by clicking the panel header."
	)
	default boolean helpExpanded()
	{
		// First-launch: expanded so the user can read the legend. They collapse it
		// once they're oriented and the choice persists.
		return true;
	}

	@ConfigItem(
		keyName = "logSkillsOnLogin",
		name = "Log skill snapshot on login",
		description = "Dev toggle: prints all skill levels to the console when you log in."
	)
	default boolean logSkillsOnLogin()
	{
		return false;
	}
}
