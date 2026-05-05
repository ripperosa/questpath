package com.questpath.planner;

import com.questpath.QuestPathConfig;
import com.questpath.data.QuestRepository;
import com.questpath.data.TrainingMethod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;

/**
 * Picks a single training method to fill a skill gap.
 *
 * Phase 5: weighted scoring. The user's Time / GP / AFK preference sliders
 * (held in QuestPathConfig) drive a scoring function over the qualifying
 * candidates; the highest-scoring method wins. When all weights are zero we
 * fall back to "highest xp/hr" as a sane default.
 */
@Slf4j
@Singleton
public class GapResolver
{
	private static final String QUEST_REQ_KEY = "QUEST";

	private final QuestRepository repo;
	private final QuestPathConfig config;

	@Inject
	public GapResolver(QuestRepository repo, QuestPathConfig config)
	{
		this.repo = repo;
		this.config = config;
	}

	/**
	 * Returns the best training method for {@code skill} given the current level
	 * and which quests are completed (or scheduled earlier in the plan).
	 *
	 * Empty if no method qualifies — caller emits a SKILL_GATE step.
	 */
	public Optional<TrainingMethod> pickMethodForSkill(
		Skill skill,
		int currentLevel,
		Set<String> completedQuestIds)
	{
		List<TrainingMethod> qualifying = new ArrayList<>();
		for (TrainingMethod m : repo.getTrainingMethodsForSkill(skill))
		{
			if (m.getMinLevel() > currentLevel)
			{
				continue;
			}
			if (!questPrereqsSatisfied(m, completedQuestIds))
			{
				continue;
			}
			qualifying.add(m);
		}
		if (qualifying.isEmpty())
		{
			return Optional.empty();
		}

		// Tolerate null config (used in tests that construct without DI).
		int timeW = config == null ? 5 : clamp(config.timeWeight());
		int gpW = config == null ? 5 : clamp(config.gpWeight());
		int afkW = config == null ? 5 : clamp(config.afkWeight());
		int totalW = timeW + gpW + afkW;

		// All zero → sensible default: pick fastest.
		if (totalW == 0)
		{
			return qualifying.stream().max(Comparator.comparingDouble(TrainingMethod::getXpPerHour));
		}

		// Normalize each sub-score to [0,1] across the candidate set so the user's
		// weights mix consistently regardless of the absolute numbers.
		double maxXp = qualifying.stream().mapToDouble(TrainingMethod::getXpPerHour).max().orElse(1.0);
		double maxGp = qualifying.stream().mapToDouble(TrainingMethod::getGpPerHour).max().orElse(0.0);
		double minGp = qualifying.stream().mapToDouble(TrainingMethod::getGpPerHour).min().orElse(0.0);
		double gpRange = maxGp - minGp;

		final int t = timeW, g = gpW, a = afkW, w = totalW;
		final double maxXpFinal = maxXp <= 0 ? 1.0 : maxXp;
		final double minGpFinal = minGp;
		final double gpRangeFinal = gpRange;

		return qualifying.stream()
			.max(Comparator.comparingDouble(m -> {
				double timeScore = m.getXpPerHour() / maxXpFinal;
				double gpScore = gpRangeFinal == 0 ? 0.5 : (m.getGpPerHour() - minGpFinal) / gpRangeFinal;
				double afkScore = m.getAfkRating() / 10.0;
				return (t * timeScore + g * gpScore + a * afkScore) / w;
			}));
	}

	private static int clamp(int v)
	{
		if (v < 0) return 0;
		if (v > 10) return 10;
		return v;
	}

	private static boolean questPrereqsSatisfied(TrainingMethod method, Set<String> completedQuestIds)
	{
		if (method.getRequirements() == null || method.getRequirements().isEmpty())
		{
			return true;
		}
		String questReq = method.getRequirements().get(QUEST_REQ_KEY);
		if (questReq == null)
		{
			return true;
		}
		return completedQuestIds.contains(questReq);
	}
}
