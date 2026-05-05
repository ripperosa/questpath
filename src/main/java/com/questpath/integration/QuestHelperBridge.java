package com.questpath.integration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;

/**
 * Reflection-based bridge to the Quest Helper plugin (Zoinkwiz/quest-helper).
 *
 * Quest Helper does NOT persist its currently-tracked quest in config and
 * does not expose a public API for setting the active quest. Our only options
 * are reflection or shipping a hard dependency on the QH artifact. We choose
 * reflection so QuestPath stays a single-file Plugin Hub submission with no
 * extra runtime requirements — if QH isn't installed, the integration is
 * just a no-op and our UI hides the "Open in Quest Helper" button.
 *
 * The path through QH internals (verified against current main as of 2026-05):
 *   PluginManager.getPlugins()
 *     → find Plugin whose class is "com.questhelper.QuestHelperPlugin"
 *     → reflectively read its private QuestManager field "questManager"
 *     → reflectively call QuestHelperQuest.getByName(String) to resolve the helper
 *     → reflectively call QuestManager.startUpQuest(QuestHelper, boolean=true)
 *
 * If anything along this chain breaks (QH refactor renames the field, etc.),
 * we log a warning and the button silently disables on the next refresh.
 */
@Slf4j
@Singleton
public class QuestHelperBridge
{
	private static final String QH_PLUGIN_CLASS = "com.questhelper.QuestHelperPlugin";
	private static final String QH_QUEST_ENUM_CLASS = "com.questhelper.questinfo.QuestHelperQuest";
	private static final String QH_QUEST_MGR_FIELD = "questManager";
	private static final String QH_GET_BY_NAME = "getByName";
	private static final String QH_START_UP_QUEST = "startUpQuest";

	private final PluginManager pluginManager;

	// Resolved lazily on first call; nulled out on failure so we degrade gracefully.
	private Plugin cachedQhPlugin;
	private Object cachedQuestManager;
	private Method cachedStartUpQuest;
	private Method cachedGetByName;

	@Inject
	public QuestHelperBridge(PluginManager pluginManager)
	{
		this.pluginManager = pluginManager;
	}

	/**
	 * @return true if Quest Helper is installed and our reflection chain still works
	 *         against the current QH version.
	 */
	public boolean isAvailable()
	{
		try
		{
			return resolve();
		}
		catch (Throwable t)
		{
			log.debug("Quest Helper bridge unavailable: {}", t.toString());
			return false;
		}
	}

	/**
	 * Ask Quest Helper to start tracking the given quest. Maps our snake_case
	 * id to QH's UPPER_SNAKE enum name.
	 *
	 * @return true on success, false if QH isn't installed or the call failed.
	 */
	public boolean openQuest(String questPathId)
	{
		if (questPathId == null || questPathId.isEmpty())
		{
			return false;
		}
		try
		{
			if (!resolve())
			{
				return false;
			}
			String enumName = toQuestHelperEnumName(questPathId);
			Object questHelper = cachedGetByName.invoke(null, enumName);
			if (questHelper == null)
			{
				log.debug("Quest Helper has no entry matching enum name '{}'", enumName);
				return false;
			}
			cachedStartUpQuest.invoke(cachedQuestManager, questHelper, true);
			return true;
		}
		catch (Throwable t)
		{
			log.warn("Failed to hand off '{}' to Quest Helper: {}", questPathId, t.toString());
			// Drop caches so the next call re-resolves — QH may have been reinstalled.
			invalidateCache();
			return false;
		}
	}

	/**
	 * Resolves and caches the QH plugin instance + reflected accessors. Idempotent.
	 * Returns false (without throwing) when QH isn't installed.
	 */
	private boolean resolve() throws Exception
	{
		if (cachedQhPlugin != null && cachedQuestManager != null
			&& cachedStartUpQuest != null && cachedGetByName != null)
		{
			return true;
		}

		Plugin qh = findQhPlugin();
		if (qh == null)
		{
			return false;
		}
		this.cachedQhPlugin = qh;

		Field qmField = qh.getClass().getDeclaredField(QH_QUEST_MGR_FIELD);
		qmField.setAccessible(true);
		Object qm = qmField.get(qh);
		if (qm == null)
		{
			log.debug("QuestHelperPlugin.questManager is null — plugin not started?");
			return false;
		}
		this.cachedQuestManager = qm;

		// startUpQuest(QuestHelper, boolean) — find by name + arg count + boolean tail.
		Method startUp = null;
		for (Method m : qm.getClass().getMethods())
		{
			if (!QH_START_UP_QUEST.equals(m.getName()))
			{
				continue;
			}
			Class<?>[] params = m.getParameterTypes();
			if (params.length == 2 && (params[1] == boolean.class || params[1] == Boolean.class))
			{
				startUp = m;
				break;
			}
		}
		if (startUp == null)
		{
			log.debug("QuestManager has no startUpQuest(QuestHelper, boolean) method");
			return false;
		}
		this.cachedStartUpQuest = startUp;

		Class<?> enumClass = Class.forName(QH_QUEST_ENUM_CLASS, true, qh.getClass().getClassLoader());
		this.cachedGetByName = enumClass.getMethod(QH_GET_BY_NAME, String.class);
		return true;
	}

	private Plugin findQhPlugin()
	{
		for (Plugin p : pluginManager.getPlugins())
		{
			if (QH_PLUGIN_CLASS.equals(p.getClass().getName()))
			{
				return p;
			}
		}
		return null;
	}

	private void invalidateCache()
	{
		cachedQhPlugin = null;
		cachedQuestManager = null;
		cachedStartUpQuest = null;
		cachedGetByName = null;
	}

	/**
	 * Our quest IDs come from {@code questHelperQuest.name().toLowerCase()}, so the
	 * inverse is {@code id.toUpperCase()}. Visible for testing.
	 */
	static String toQuestHelperEnumName(String questPathId)
	{
		return questPathId.toUpperCase();
	}
}
