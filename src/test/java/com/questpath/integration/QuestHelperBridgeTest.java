package com.questpath.integration;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Method;
import org.junit.Test;

/**
 * The reflection chain into Quest Helper can't realistically be exercised
 * without standing up a QH plugin instance, but the enum-name mapping is
 * load-bearing (one wrong character means QH can't find the quest) and is
 * safe to lock down in a unit test.
 */
public class QuestHelperBridgeTest
{
	@Test
	public void mapsSnakeCaseIdToUpperSnakeEnumName() throws Exception
	{
		assertEquals("LUNAR_DIPLOMACY", invokeToEnumName("lunar_diplomacy"));
		assertEquals("MONKEY_MADNESS_II", invokeToEnumName("monkey_madness_ii"));
		assertEquals("RECIPE_FOR_DISASTER_FINALE", invokeToEnumName("recipe_for_disaster_finale"));
	}

	private static String invokeToEnumName(String id) throws Exception
	{
		Method m = QuestHelperBridge.class.getDeclaredMethod("toQuestHelperEnumName", String.class);
		m.setAccessible(true);
		return (String) m.invoke(null, id);
	}
}
