package com.questpath.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Top-level shape of overrides.json. Gson deserializes the file into this object.
 *
 * <pre>
 * {
 *   "quests": { "lunar_diplomacy": { ... }, ... },
 *   "trainingMethods": [ { "id": "willows_at_draynor", ... }, ... ]
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OverrideData
{
	private Map<String, QuestDefinition> quests = new HashMap<>();
	private List<TrainingMethod> trainingMethods = new java.util.ArrayList<>();
}
