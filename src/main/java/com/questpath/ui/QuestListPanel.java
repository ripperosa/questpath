package com.questpath.ui;

import com.questpath.data.QuestDefinition;
import com.questpath.game.PlayerState;
import com.questpath.game.QuestStatus;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
 * Quest Helper-style scrollable quest picker. One row per quest: name on the
 * left, status dot, chevron on the right. Click any row to fire the
 * {@link QuestSelectionListener}; hovering highlights the row.
 *
 * Status dot meanings (best-effort against the player snapshot):
 *   green  — quest already DONE
 *   blue   — all prereqs + skill walls met, ready to start now
 *   orange — skill walls unmet
 *   red    — prereq quest not yet done
 *
 * The whole panel hugs its preferred height so the wrapping JScrollPane in
 * {@link com.questpath.QuestPathPanel} controls the scroll viewport, not us.
 */
public class QuestListPanel extends JPanel
{
	private static final Color DOT_DONE = ColorScheme.PROGRESS_COMPLETE_COLOR;
	private static final Color DOT_READY = new Color(100, 180, 255);
	private static final Color DOT_BLOCKED_QUEST = ColorScheme.PROGRESS_ERROR_COLOR;
	private static final Color DOT_BLOCKED_SKILL = ColorScheme.BRAND_ORANGE;

	private static final int ROW_MIN_HEIGHT = 28;
	private static final int CHEVRON_FONT_SIZE = 14;

	@FunctionalInterface
	public interface QuestSelectionListener
	{
		void onQuestSelected(String questId);
	}

	private final QuestSelectionListener listener;

	public QuestListPanel(QuestSelectionListener listener)
	{
		this.listener = listener;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setAlignmentX(Component.LEFT_ALIGNMENT);
	}

	/** Hug content height so the wrapping scroll pane drives the scrollbar. */
	@Override
	public Dimension getMaximumSize()
	{
		Dimension pref = getPreferredSize();
		return new Dimension(Integer.MAX_VALUE, pref.height);
	}

	/**
	 * Replace the rendered rows with the given quests. The list is rendered in
	 * the order received — caller controls sort.
	 */
	public void render(List<QuestDefinition> quests, PlayerState state)
	{
		removeAll();

		if (quests.isEmpty())
		{
			add(emptyState());
			revalidate();
			repaint();
			return;
		}

		for (QuestDefinition q : quests)
		{
			add(buildRow(q, state));
		}
		revalidate();
		repaint();
	}

	// ----- row builders -------------------------------------------------------

	private JPanel buildRow(QuestDefinition quest, PlayerState state)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_MIN_HEIGHT + 6));
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		// Status dot (8px circle drawn as colored panel — cheap and crisp at sidebar sizes).
		Color dotColor = statusDot(quest, state);
		JPanel dot = new JPanel();
		dot.setBackground(dotColor);
		dot.setPreferredSize(new Dimension(8, 8));
		dot.setMinimumSize(new Dimension(8, 8));
		dot.setMaximumSize(new Dimension(8, 8));
		dot.setAlignmentY(Component.CENTER_ALIGNMENT);
		row.add(dot);
		row.add(Box.createRigidArea(new Dimension(8, 0)));

		// Quest name — HTML wraps long names cleanly inside the 225px sidebar.
		JLabel name = new JLabel(
			"<html><body style='width:150px'>" + escapeHtml(quest.getDisplayName()) + "</body></html>");
		name.setForeground(Color.WHITE);
		name.setFont(name.getFont().deriveFont(Font.PLAIN, 14f));
		name.setAlignmentY(Component.CENTER_ALIGNMENT);
		row.add(name);

		row.add(Box.createHorizontalGlue());

		JLabel chevron = new JLabel("›");
		chevron.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		chevron.setFont(chevron.getFont().deriveFont(Font.BOLD, (float) CHEVRON_FONT_SIZE));
		chevron.setAlignmentY(Component.CENTER_ALIGNMENT);
		row.add(chevron);

		MouseAdapter handler = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (listener != null)
				{
					listener.onQuestSelected(quest.getId());
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		};
		row.addMouseListener(handler);
		// Children swallow events by default — install on each so hovering the
		// label or chevron doesn't break the highlight.
		name.addMouseListener(handler);
		chevron.addMouseListener(handler);
		dot.addMouseListener(handler);

		return row;
	}

	private static JPanel emptyState()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8));

		JLabel label = new JLabel("No quests match. Try clearing search or filters.");
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(label.getFont().deriveFont(Font.ITALIC, 12f));
		panel.add(label);
		return panel;
	}

	// ----- status -------------------------------------------------------------

	/**
	 * Best-effort status color, mirroring NodeStatus colors used in the
	 * dependency tree so the legend is consistent across views.
	 */
	private static Color statusDot(QuestDefinition quest, PlayerState state)
	{
		if (state == null)
		{
			return DOT_READY;
		}
		QuestStatus status = state.getQuestStatus(quest.getId());
		if (status == QuestStatus.DONE)
		{
			return DOT_DONE;
		}

		// Direct (non-transitive) check — fast and good enough for the list view.
		if (quest.getPrerequisiteQuestIds() != null)
		{
			for (String prereqId : quest.getPrerequisiteQuestIds())
			{
				if (state.getQuestStatus(prereqId) != QuestStatus.DONE)
				{
					return DOT_BLOCKED_QUEST;
				}
			}
		}
		if (quest.getSkillRequirements() != null)
		{
			for (Map.Entry<Skill, Integer> req : quest.getSkillRequirements().entrySet())
			{
				if (state.getSkillLevel(req.getKey()) < req.getValue())
				{
					return DOT_BLOCKED_SKILL;
				}
			}
		}
		return DOT_READY;
	}

	private static String escapeHtml(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
