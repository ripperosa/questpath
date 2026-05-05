package com.questpath.ui;

import com.questpath.planner.DependencyGraph;
import com.questpath.planner.NodeStatus;
import com.questpath.planner.QuestNode;
import com.questpath.planner.SkillGate;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * Replacement for the JTree dependency view. Each row is a JLabel with HTML
 * word-wrap so long quest names ("The Fremennik Trials") render fully across
 * two lines instead of getting clipped to "The Fr".
 *
 * Indentation per depth: a fixed left margin on the row's panel. Status
 * colors come from {@link NodeStatus}; skill gates use orange (unmet) or
 * green (met). Quests with children get a clickable ▼/▶ disclosure widget;
 * the panel remembers which quests are collapsed across re-renders.
 */
public class DependencyTreePanel extends JPanel
{
	private static final Color READY_COLOR = new Color(100, 180, 255);
	private static final int INDENT_PX = 12;
	private static final int FONT_SIZE = 14;
	private static final int DISCLOSURE_SLOT_PX = 18;

	/**
	 * Approximate width available for label text after subtracting the panel's
	 * borders + rail + spacer + indent. RuneLite's PluginPanel is fixed at 225px;
	 * we shave ~30px for chrome and let the HTML wrap engine do the rest.
	 */
	private static final int BASE_TEXT_WIDTH_PX = 172;

	private final Set<String> collapsedIds = new HashSet<>();

	/** Cached so click handlers can re-render without re-fetching the graph. */
	private DependencyGraph currentGraph;
	private String currentRootId;

	public DependencyTreePanel()
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setAlignmentX(LEFT_ALIGNMENT);
	}

	/**
	 * Cap our height to what we actually need. Without this, the parent
	 * BoxLayout.Y_AXIS hands out leftover scroll space evenly across siblings,
	 * leaving a giant blank gap between the tree and the next section.
	 */
	@Override
	public Dimension getMaximumSize()
	{
		Dimension pref = getPreferredSize();
		return new Dimension(Integer.MAX_VALUE, pref.height);
	}

	/** Rebuild contents from the graph rooted at {@code rootQuestId}. */
	public void render(DependencyGraph graph, String rootQuestId)
	{
		this.currentGraph = graph;
		this.currentRootId = rootQuestId;
		rerender();
	}

	private void rerender()
	{
		removeAll();
		if (currentGraph == null || currentRootId == null)
		{
			revalidate();
			repaint();
			return;
		}
		appendQuest(currentGraph, currentRootId, 0, new ArrayDeque<>());
		revalidate();
		repaint();
	}

	/**
	 * @param expanding ids in the current DFS path. If we re-encounter one, we have
	 *                  a cycle (data bug) and must render a leaf instead of recursing.
	 */
	private void appendQuest(DependencyGraph graph, String questId, int depth, Deque<String> expanding)
	{
		QuestNode node = graph.getNode(questId);
		if (node == null)
		{
			add(makeRow(questId, ColorScheme.LIGHT_GRAY_COLOR, depth, NoChildren.INSTANCE, null));
			return;
		}

		// Cycle guard. With sanitized data we shouldn't ever hit this, but render a
		// flat leaf with a hint rather than blowing up if a self-loop slips through.
		if (expanding.contains(questId))
		{
			add(makeRow(node.getDisplayName() + "  (already shown)",
				ColorScheme.LIGHT_GRAY_COLOR, depth, NoChildren.INSTANCE, null));
			return;
		}

		boolean hasChildren = !node.getPrerequisiteQuestIds().isEmpty()
			|| !node.getSkillGates().isEmpty();
		boolean collapsed = collapsedIds.contains(questId);

		ChildState state = !hasChildren
			? NoChildren.INSTANCE
			: collapsed ? new Collapsed(questId) : new Expanded(questId);

		add(makeRow(node.getDisplayName(), colorFor(node.getStatus()), depth, state, this::toggleCollapse));

		if (collapsed)
		{
			return;
		}

		expanding.push(questId);
		try
		{
			// Children: prereq quests first, then skill gates.
			for (String prereqId : node.getPrerequisiteQuestIds())
			{
				appendQuest(graph, prereqId, depth + 1, expanding);
			}
			for (SkillGate gate : node.getSkillGates())
			{
				String label = String.format("%s %d → %d",
					gate.getSkill().getName(), gate.getCurrentLevel(), gate.getRequiredLevel());
				Color color = gate.isMet()
					? ColorScheme.PROGRESS_COMPLETE_COLOR
					: ColorScheme.BRAND_ORANGE;
				add(makeRow(label, color, depth + 1, NoChildren.INSTANCE, null));
			}
		}
		finally
		{
			expanding.pop();
		}
	}

	private void toggleCollapse(String questId)
	{
		if (!collapsedIds.add(questId))
		{
			collapsedIds.remove(questId);
		}
		rerender();
	}

	private static JPanel makeRow(
		String text, Color color, int depth, ChildState state, java.util.function.Consumer<String> onToggle)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(LEFT_ALIGNMENT);

		// Indent (left empty border) + colored rail (matte border) + content padding.
		// MatteBorder auto-stretches with the row's height, including when the
		// label wraps to multiple lines. Vertical padding makes rows easier to
		// click without making the list cramped.
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createEmptyBorder(2, depth * INDENT_PX, 2, 0),
			BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 2, 0, 0, color),
				BorderFactory.createEmptyBorder(0, 4, 0, 0))));

		// Disclosure widget — fixed slot for visual alignment, populated only when
		// the row has children. The slot itself stays clickable so the user gets a
		// stable hit target column.
		JLabel disclosure = new JLabel(state.glyph(), JLabel.CENTER);
		disclosure.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		disclosure.setFont(disclosure.getFont().deriveFont(Font.BOLD, 12f));
		disclosure.setPreferredSize(new Dimension(DISCLOSURE_SLOT_PX, 18));
		disclosure.setMaximumSize(new Dimension(DISCLOSURE_SLOT_PX, 18));
		disclosure.setMinimumSize(new Dimension(DISCLOSURE_SLOT_PX, 18));
		disclosure.setAlignmentY(Component.TOP_ALIGNMENT);
		row.add(disclosure);
		row.add(Box.createRigidArea(new Dimension(4, 0)));

		// JLabel HTML wraps to <body width=N> only — without explicit width,
		// it computes preferred size single-line and gets clipped. Width
		// shrinks with depth to keep deeply-nested rows from overflowing.
		int textWidth = Math.max(70, BASE_TEXT_WIDTH_PX - depth * INDENT_PX);
		JLabel label = new JLabel(
			"<html><body style='width:" + textWidth + "px'>"
				+ escapeHtml(text)
				+ "</body></html>");
		label.setFont(label.getFont().deriveFont(Font.PLAIN, (float) FONT_SIZE));
		label.setForeground(color);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setAlignmentY(Component.TOP_ALIGNMENT);
		label.setToolTipText(text);
		row.add(label);
		row.add(Box.createHorizontalGlue());

		// Whole-row click + hover effect for quest nodes with children. Clicking
		// the row, the label, the disclosure, or the empty space all toggle.
		if (state.questId() != null && onToggle != null)
		{
			final String capturedId = state.questId();
			MouseAdapter rowMouseHandler = new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					onToggle.accept(capturedId);
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					row.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					row.setBackground(ColorScheme.DARK_GRAY_COLOR);
				}
			};
			row.addMouseListener(rowMouseHandler);
			label.addMouseListener(rowMouseHandler);
			disclosure.addMouseListener(rowMouseHandler);
			row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			disclosure.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		}

		return row;
	}

	private static String escapeHtml(String s)
	{
		return s
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;");
	}

	private static Color colorFor(NodeStatus status)
	{
		switch (status)
		{
			case DONE:
				return ColorScheme.PROGRESS_COMPLETE_COLOR;
			case READY:
				return READY_COLOR;
			case BLOCKED_BY_SKILL:
				return ColorScheme.BRAND_ORANGE;
			case BLOCKED_BY_QUEST:
				return ColorScheme.PROGRESS_ERROR_COLOR;
			default:
				return ColorScheme.LIGHT_GRAY_COLOR;
		}
	}

	// ----- Disclosure state ---------------------------------------------------

	private interface ChildState
	{
		String glyph();
		String questId();
	}

	private static final class NoChildren implements ChildState
	{
		static final NoChildren INSTANCE = new NoChildren();

		@Override public String glyph() { return " "; }
		@Override public String questId() { return null; }
	}

	private static final class Expanded implements ChildState
	{
		private final String questId;
		Expanded(String questId) { this.questId = questId; }
		@Override public String glyph() { return "▼"; }
		@Override public String questId() { return questId; }
	}

	private static final class Collapsed implements ChildState
	{
		private final String questId;
		Collapsed(String questId) { this.questId = questId; }
		@Override public String glyph() { return "▶"; }
		@Override public String questId() { return questId; }
	}
}
