package com.questpath.ui;

import com.questpath.data.QuestDefinition;
import com.questpath.game.PlayerState;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.api.Skill;
import net.runelite.client.ui.ColorScheme;

/**
 * Quest Helper-style "General requirements" panel — one row per skill /
 * QP gate, color-coded green when the player meets it and red when they
 * don't. Hidden entirely when the target has no requirements.
 *
 * Render contract: caller invokes {@link #render(QuestDefinition, PlayerState)}
 * after each plan refresh; we rebuild the row list and toggle visibility.
 */
public class SkillRequirementsPanel extends JPanel
{
	private static final Color MET = new Color(110, 200, 110);
	private static final Color UNMET = new Color(220, 100, 100);
	private static final int ROW_HEIGHT = 24;

	private final JPanel rowsContainer = new JPanel();

	public SkillRequirementsPanel()
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setAlignmentX(Component.LEFT_ALIGNMENT);

		rowsContainer.setLayout(new BoxLayout(rowsContainer, BoxLayout.Y_AXIS));
		rowsContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		rowsContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
		rowsContainer.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

		add(rowsContainer);

		setVisible(false);
	}

	/** Hug content height — see DependencyTreePanel.getMaximumSize() for rationale. */
	@Override
	public Dimension getMaximumSize()
	{
		Dimension pref = getPreferredSize();
		return new Dimension(Integer.MAX_VALUE, pref.height);
	}

	public void render(QuestDefinition quest, PlayerState state)
	{
		rowsContainer.removeAll();

		if (quest == null)
		{
			setVisible(false);
			return;
		}

		List<Row> rows = new ArrayList<>();

		Map<Skill, Integer> skillReqs = quest.getSkillRequirements();
		if (skillReqs != null)
		{
			for (Map.Entry<Skill, Integer> req : skillReqs.entrySet())
			{
				int have = state == null ? 1 : state.getSkillLevel(req.getKey());
				rows.add(new Row(req.getKey().getName(), req.getValue(), have));
			}
		}
		// Sort by skill name for stable display.
		rows.sort(Comparator.comparing(r -> r.label));

		int qpReq = quest.getQuestPointRequirement();
		if (qpReq > 0)
		{
			int haveQp = state == null ? 0 : state.getQuestPoints();
			rows.add(0, new Row("Quest Points", qpReq, haveQp));
		}

		if (rows.isEmpty())
		{
			setVisible(false);
			revalidate();
			repaint();
			return;
		}

		for (Row r : rows)
		{
			rowsContainer.add(buildRow(r));
		}
		setVisible(true);
		revalidate();
		repaint();
	}

	private static JLabel buildRow(Row row)
	{
		boolean met = row.have >= row.need;
		String text = String.format("%s %d  (you have %d)", row.label, row.need, row.have);
		JLabel label = new JLabel(text);
		label.setForeground(met ? MET : UNMET);
		label.setFont(label.getFont().deriveFont(met ? Font.PLAIN : Font.BOLD, 14f));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
		label.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
		return label;
	}

	private static final class Row
	{
		final String label;
		final int need;
		final int have;

		Row(String label, int need, int have)
		{
			this.label = label;
			this.need = need;
			this.have = have;
		}
	}
}
