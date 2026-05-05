package com.questpath.ui;

import com.questpath.data.QuestDefinition;
import com.questpath.game.PlayerState;
import com.questpath.game.QuestStatus;
import com.questpath.integration.QuestHelperBridge;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.util.function.BooleanSupplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;

/**
 * Quest Helper-style header card for the currently-selected target quest.
 *
 * Layout (top → bottom):
 *   - Orange title bar with the quest's display name
 *   - Status pill ("Not Started" / "In Progress" / "Complete") with traffic-light color
 *   - Inline badges: Members / F2P, difficulty, QP reward
 *   - Action row: [Open in Quest Helper] (only if QH installed) [Wiki]
 *
 * The card is self-contained; the panel just calls {@link #render(QuestDefinition, QuestStatus)}
 * whenever the target or game state changes.
 */
@Slf4j
public class QuestInfoCard extends JPanel
{
	private static final Color TITLE_BG = ColorScheme.BRAND_ORANGE;
	private static final Color CARD_BG = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color BADGE_BG = ColorScheme.DARK_GRAY_HOVER_COLOR;
	private static final Color STATUS_DONE = ColorScheme.PROGRESS_COMPLETE_COLOR;
	private static final Color STATUS_PROGRESS = ColorScheme.BRAND_ORANGE;
	private static final Color STATUS_NOT_STARTED = ColorScheme.PROGRESS_ERROR_COLOR;

	private final QuestHelperBridge questHelperBridge;
	/** User-controlled toggle — when false, the QH button is hidden entirely
	 *  and we never invoke the (reflection-based) bridge. Off by default. */
	private final BooleanSupplier questHelperHandoffEnabled;

	private final JLabel titleLabel = new JLabel(" ");
	private final JLabel statusLabel = new JLabel(" ");
	private final JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
	private final JButton qhButton = new JButton("Open in Quest Helper");
	private final JButton wikiButton = new JButton("Wiki");

	// Held so the buttons' action listeners can target the right quest.
	private QuestDefinition currentQuest;

	public QuestInfoCard(QuestHelperBridge questHelperBridge, BooleanSupplier questHelperHandoffEnabled)
	{
		this.questHelperBridge = questHelperBridge;
		this.questHelperHandoffEnabled = questHelperHandoffEnabled;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(CARD_BG);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.BORDER_COLOR));

		add(buildTitleBar());
		add(buildBodyPanel());
	}

	/** Stay at preferred height in BoxLayout.Y_AXIS parents (tight packing). */
	@Override
	public Dimension getMaximumSize()
	{
		Dimension pref = getPreferredSize();
		return new Dimension(Integer.MAX_VALUE, pref.height);
	}

	// ----- public API ----------------------------------------------------------

	public void render(QuestDefinition quest, QuestStatus status)
	{
		this.currentQuest = quest;

		if (quest == null)
		{
			titleLabel.setText("No quest selected");
			statusLabel.setText(" ");
			statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			badgeRow.removeAll();
			qhButton.setVisible(false);
			wikiButton.setEnabled(false);
			revalidate();
			repaint();
			return;
		}

		titleLabel.setText(quest.getDisplayName());

		statusLabel.setText(statusText(status));
		statusLabel.setForeground(statusColor(status));

		badgeRow.removeAll();
		badgeRow.add(badge(quest.isMembers() ? "Members" : "Free-to-play",
			quest.isMembers() ? new Color(190, 100, 100) : new Color(110, 160, 110)));
		if (quest.getDifficulty() != null && !quest.getDifficulty().isEmpty())
		{
			badgeRow.add(badge(prettyDifficulty(quest.getDifficulty()), null));
		}
		if (quest.getQuestPointReward() > 0)
		{
			badgeRow.add(badge(quest.getQuestPointReward() + " QP", null));
		}

		boolean handoffOptedIn = questHelperHandoffEnabled != null
			&& questHelperHandoffEnabled.getAsBoolean();
		// Only consult the (reflection-based) bridge when the user has explicitly
		// opted in via plugin settings. Default-off keeps QuestPath strictly
		// reflection-free for users who never touch the toggle.
		qhButton.setVisible(handoffOptedIn);
		if (handoffOptedIn)
		{
			boolean qhAvailable = questHelperBridge != null && questHelperBridge.isAvailable();
			qhButton.setEnabled(qhAvailable);
			qhButton.setToolTipText(qhAvailable
				? "Hand off to the Quest Helper plugin"
				: "Quest Helper plugin not installed or not running");
		}

		wikiButton.setEnabled(quest.getWikiUrl() != null && !quest.getWikiUrl().isEmpty());

		revalidate();
		repaint();
	}

	// ----- layout helpers ------------------------------------------------------

	private JPanel buildTitleBar()
	{
		JPanel bar = new JPanel(new BorderLayout());
		bar.setBackground(TITLE_BG);
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);
		bar.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		titleLabel.setForeground(Color.WHITE);
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
		bar.add(titleLabel, BorderLayout.WEST);
		return bar;
	}

	private JPanel buildBodyPanel()
	{
		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(CARD_BG);
		body.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 14f));
		statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(statusLabel);
		body.add(Box.createRigidArea(new Dimension(0, 6)));

		badgeRow.setBackground(CARD_BG);
		badgeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		badgeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		body.add(badgeRow);
		body.add(Box.createRigidArea(new Dimension(0, 8)));

		body.add(buildActionRow());
		return body;
	}

	private JPanel buildActionRow()
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setBackground(CARD_BG);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		styleActionButton(qhButton);
		qhButton.addActionListener(e -> handleOpenInQuestHelper());
		row.add(qhButton);

		row.add(Box.createRigidArea(new Dimension(6, 0)));

		styleActionButton(wikiButton);
		wikiButton.addActionListener(e -> handleOpenWiki());
		row.add(wikiButton);

		row.add(Box.createHorizontalGlue());
		return row;
	}

	private static void styleActionButton(JButton button)
	{
		button.setMargin(new Insets(2, 8, 2, 8));
		button.setFocusable(false);
	}

	private static JLabel badge(String text, Color tint)
	{
		JLabel label = new JLabel(text);
		label.setOpaque(true);
		label.setBackground(tint == null ? BADGE_BG : tint);
		label.setForeground(Color.WHITE);
		label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
		label.setBorder(BorderFactory.createEmptyBorder(3, 7, 3, 7));
		return label;
	}

	private void handleOpenInQuestHelper()
	{
		if (currentQuest == null)
		{
			return;
		}
		boolean ok = questHelperBridge.openQuest(currentQuest.getId());
		if (!ok)
		{
			showFailureToast("Couldn't reach the Quest Helper plugin. "
				+ "Make sure it's installed and enabled.");
		}
	}

	private void handleOpenWiki()
	{
		if (currentQuest == null || currentQuest.getWikiUrl() == null)
		{
			return;
		}
		LinkBrowser.browse(currentQuest.getWikiUrl());
	}

	private void showFailureToast(String message)
	{
		// Lightweight, non-blocking-style modal — cheap and avoids depending on
		// RuneLite's notification system.
		SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
			SwingUtilities.getWindowAncestor(this),
			message,
			"QuestPath",
			JOptionPane.WARNING_MESSAGE));
	}

	// ----- formatting ----------------------------------------------------------

	private static String statusText(QuestStatus status)
	{
		if (status == null)
		{
			return "Status unknown";
		}
		switch (status)
		{
			case DONE:
				return "✓ Complete";
			case IN_PROGRESS:
				return "● In Progress";
			case NOT_STARTED:
			default:
				return "○ Not Started";
		}
	}

	private static Color statusColor(QuestStatus status)
	{
		if (status == null)
		{
			return ColorScheme.LIGHT_GRAY_COLOR;
		}
		switch (status)
		{
			case DONE:
				return STATUS_DONE;
			case IN_PROGRESS:
				return STATUS_PROGRESS;
			case NOT_STARTED:
			default:
				return STATUS_NOT_STARTED;
		}
	}

	private static String prettyDifficulty(String raw)
	{
		// "GRANDMASTER" → "Grandmaster", "ACHIEVEMENT_DIARY" → "Diary"
		if ("ACHIEVEMENT_DIARY".equals(raw))
		{
			return "Diary";
		}
		if ("MINIQUEST".equals(raw))
		{
			return "Miniquest";
		}
		String lower = raw.toLowerCase().replace('_', ' ');
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	/** Convenience overload for callers that have a PlayerState on hand. */
	public void render(QuestDefinition quest, PlayerState state)
	{
		QuestStatus status = (quest == null || state == null)
			? null
			: state.getQuestStatus(quest.getId());
		render(quest, status);
	}
}
