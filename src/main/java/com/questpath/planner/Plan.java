package com.questpath.planner;

import java.util.Collections;
import java.util.List;
import lombok.Value;

/**
 * The full ordered plan from current state to target quest, plus rolled-up
 * estimates so the UI doesn't have to recompute.
 */
@Value
public class Plan
{
	String targetQuestId;
	String targetQuestDisplayName;

	List<PlanStep> steps;

	int questStepCount;
	int trainingStepCount;
	int skillGateStepCount;

	double totalEstimatedHours;
	double totalEstimatedGpCost;

	public List<PlanStep> getSteps()
	{
		return Collections.unmodifiableList(steps);
	}

	public boolean isAlreadyComplete()
	{
		return steps.isEmpty();
	}

	public static Plan empty(String targetId, String targetName)
	{
		return new Plan(targetId, targetName, Collections.emptyList(),
			0, 0, 0, 0.0, 0.0);
	}
}
