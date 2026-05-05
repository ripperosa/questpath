package com.questpath.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class XpTableTest
{
	@Test
	public void xpForLevel1IsZero()
	{
		assertEquals(0, XpTable.xpForLevel(1));
	}

	@Test
	public void knownLevelXpValues()
	{
		// Canonical values from the wiki — sanity check the formula didn't drift.
		assertEquals(83, XpTable.xpForLevel(2));
		assertEquals(174, XpTable.xpForLevel(3));
		assertEquals(13_034_431, XpTable.xpForLevel(99));
	}

	@Test
	public void capsAt99()
	{
		assertEquals(13_034_431, XpTable.xpForLevel(120));
		assertEquals(13_034_431, XpTable.xpForLevel(99));
	}

	@Test
	public void xpBetweenIsNeverNegative()
	{
		assertEquals(0, XpTable.xpBetween(50, 50));
		assertEquals(0, XpTable.xpBetween(50, 30));
		assertTrue("Crafting 1->32 should require positive XP",
			XpTable.xpBetween(1, 32) > 0);
	}

	@Test
	public void xpBetweenAddsCorrectly()
	{
		assertEquals(
			XpTable.xpForLevel(70) - XpTable.xpForLevel(50),
			XpTable.xpBetween(50, 70));
	}
}
