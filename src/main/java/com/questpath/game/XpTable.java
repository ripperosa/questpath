package com.questpath.game;

/**
 * Standard OSRS XP-for-level lookup. The formula is well-defined:
 *
 *   xp(L) = floor( 0.25 * sum from x=1 to L-1 of floor( x + 300 * 2^(x/7) ) )
 *
 * which gives xp(2) = 83, xp(99) = 13_034_431, xp(126) = 200_000_000 (virtual).
 *
 * Table cached at class-load time. Levels above 99 cap at 99.
 */
public final class XpTable
{
	private static final int MAX_LEVEL = 99;
	private static final int[] XP_FOR_LEVEL = computeTable();

	private XpTable() {}

	private static int[] computeTable()
	{
		int[] table = new int[MAX_LEVEL + 1];
		double sum = 0;
		table[1] = 0;
		for (int level = 1; level < MAX_LEVEL; level++)
		{
			sum += Math.floor(level + 300.0 * Math.pow(2, level / 7.0));
			table[level + 1] = (int) Math.floor(sum / 4.0);
		}
		return table;
	}

	/** XP required to reach the start of {@code level}. xpForLevel(1) = 0, xpForLevel(99) = 13034431. */
	public static int xpForLevel(int level)
	{
		if (level < 1)
		{
			return 0;
		}
		if (level > MAX_LEVEL)
		{
			return XP_FOR_LEVEL[MAX_LEVEL];
		}
		return XP_FOR_LEVEL[level];
	}

	/** XP needed to go from {@code fromLevel} (current) to {@code toLevel} (target). Never negative. */
	public static int xpBetween(int fromLevel, int toLevel)
	{
		return Math.max(0, xpForLevel(toLevel) - xpForLevel(fromLevel));
	}
}
