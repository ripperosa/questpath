package com.questpath;

import com.google.inject.Provides;
import com.questpath.data.QuestRepository;
import com.questpath.game.GameStateReader;
import com.questpath.game.PlayerState;
import com.questpath.planner.PathPlanner;
import com.questpath.planner.Plan;
import com.questpath.planner.PlanStep;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "QuestPath",
	description = "Quest prerequisite visualizer and planner",
	tags = {"quest", "planning", "prerequisites", "skills"}
)
public class QuestPathPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private QuestPathConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private QuestRepository questRepository;

	@Inject
	private GameStateReader gameStateReader;

	@Inject
	private PathPlanner pathPlanner;

	private QuestPathPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp() throws Exception
	{
		log.info("QuestPath starting up");
		log.info("QuestPath data layer: {} quests, {} training methods loaded",
			questRepository.questCount(), questRepository.trainingMethodCount());

		// Build the panel; it owns its own initial plan render via the constructor's
		// selectTarget() call. We just hand it the deps and the persisted target id.
		panel = new QuestPathPanel(
			questRepository,
			gameStateReader,
			pathPlanner,
			configManager,
			config,
			config.targetQuestId());

		// Also dump the initial plan to console for log-driven debugging.
		Plan initial = pathPlanner.plan(config.targetQuestId(), PlayerState.empty());
		dumpPlanToConsole(initial);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");

		navButton = NavigationButton.builder()
			.tooltip("QuestPath")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("QuestPath shutting down");
		clientToolbar.removeNavigation(navButton);
		panel = null;
		navButton = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		if (config.logSkillsOnLogin())
		{
			logAllSkillLevels();
		}
		// Refresh the panel against live state.
		if (panel != null)
		{
			panel.refresh();
		}
	}

	/**
	 * When the target quest config changes (either via the panel dropdown or the
	 * RuneLite config UI), refresh the panel so both views stay in sync. Panel
	 * sets-then-refreshes itself on dropdown change, so this catches the
	 * "external edit" case primarily.
	 */
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!QuestPathConfig.GROUP.equals(event.getGroup()) || panel == null)
		{
			return;
		}
		String key = event.getKey();
		if (QuestPathConfig.TARGET_QUEST_KEY.equals(key)
			|| QuestPathConfig.FILTER_MODE_KEY.equals(key)
			|| QuestPathConfig.DIFFICULTY_FILTER_KEY.equals(key)
			|| QuestPathConfig.SORT_MODE_KEY.equals(key)
			|| QuestPathConfig.TIME_WEIGHT_KEY.equals(key)
			|| QuestPathConfig.GP_WEIGHT_KEY.equals(key)
			|| QuestPathConfig.AFK_WEIGHT_KEY.equals(key))
		{
			panel.refresh();
		}
	}

	private void logAllSkillLevels()
	{
		log.info("=== QuestPath: skill snapshot ===");
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			int level = client.getRealSkillLevel(skill);
			int xp = client.getSkillExperience(skill);
			log.info("  {} — level {} ({} xp)", skill.getName(), level, xp);
		}
		log.info("=== end snapshot ===");
	}

	private static void dumpPlanToConsole(Plan plan)
	{
		log.info("=== QuestPath plan: {} ===", plan.getTargetQuestDisplayName());
		log.info("Steps: {} quest, {} training, {} skill-gate    Total: ~{} hr   GP: {}",
			plan.getQuestStepCount(), plan.getTrainingStepCount(), plan.getSkillGateStepCount(),
			String.format("%.1f", plan.getTotalEstimatedHours()),
			String.format("%.0f", plan.getTotalEstimatedGpCost()));
		int i = 1;
		for (PlanStep step : plan.getSteps())
		{
			log.info("  {}. [{}] {}", i++, step.getType(), step.getTitle());
			if (step.getSubtitle() != null && !step.getSubtitle().isEmpty())
			{
				log.info("     {}", step.getSubtitle());
			}
		}
		log.info("=== end plan ===");
	}

	@Provides
	QuestPathConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(QuestPathConfig.class);
	}
}
