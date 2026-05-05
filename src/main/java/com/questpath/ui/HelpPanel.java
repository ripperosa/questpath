package com.questpath.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * Collapsible "Help &amp; Tips" panel — color legend + slider preset
 * recommendations. Sits at the top of the body so first-time users get
 * oriented; click the header to collapse once they know the codes.
 *
 * The collapsed/expanded state is owned by the parent (persisted to config),
 * so this component only renders + reports clicks via its toggle callback.
 */
public class HelpPanel extends JPanel
{
	// Re-declared from DependencyTreePanel / step cards so the legend swatches
	// match exactly what the user sees elsewhere. Keep in sync if those change.
	private static final Color COLOR_DONE = ColorScheme.PROGRESS_COMPLETE_COLOR;
	private static final Color COLOR_READY = new Color(100, 180, 255);
	private static final Color COLOR_BLOCKED_QUEST = ColorScheme.PROGRESS_ERROR_COLOR;
	private static final Color COLOR_BLOCKED_SKILL = ColorScheme.BRAND_ORANGE;

	private final JLabel headerLabel = new JLabel();
	private final JPanel body = new JPanel();
	private final Consumer<Boolean> onToggle;

	private boolean expanded;

	public HelpPanel(boolean initialExpanded, Consumer<Boolean> onToggle)
	{
		this.expanded = initialExpanded;
		this.onToggle = onToggle;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setAlignmentX(Component.LEFT_ALIGNMENT);

		add(buildHeader());
		buildBody();
		add(body);
		applyExpanded();
	}

	/** Hug content height so BoxLayout.Y_AXIS doesn't gift us blank space. */
	@Override
	public Dimension getMaximumSize()
	{
		Dimension pref = getPreferredSize();
		return new Dimension(Integer.MAX_VALUE, pref.height);
	}

	// ----- header --------------------------------------------------------------

	private JPanel buildHeader()
	{
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		updateHeaderLabel();
		headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 14f));
		headerLabel.setForeground(ColorScheme.BRAND_ORANGE);
		header.add(headerLabel);
		header.add(Box.createHorizontalGlue());

		MouseAdapter clickToggle = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				expanded = !expanded;
				applyExpanded();
				updateHeaderLabel();
				if (onToggle != null)
				{
					onToggle.accept(expanded);
				}
			}
		};
		header.addMouseListener(clickToggle);
		headerLabel.addMouseListener(clickToggle);

		return header;
	}

	private void updateHeaderLabel()
	{
		headerLabel.setText((expanded ? "▼ " : "▶ ") + "HELP & TIPS");
	}

	private void applyExpanded()
	{
		body.setVisible(expanded);
		revalidate();
		repaint();
	}

	// ----- body ----------------------------------------------------------------

	private void buildBody()
	{
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		body.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 6));

		body.add(subheading("Color legend"));
		body.add(legendRow(COLOR_DONE, "Done — quest finished or skill met"));
		body.add(legendRow(COLOR_READY, "Ready — all prereqs met, can start"));
		body.add(legendRow(COLOR_BLOCKED_QUEST, "Blocked — needs another quest first"));
		body.add(legendRow(COLOR_BLOCKED_SKILL, "Skill gate — needs more training"));

		body.add(Box.createRigidArea(new Dimension(0, 8)));
		body.add(subheading("Slider presets"));
		body.add(tip("Speedrunner: Time 10, GP 0, AFK 0 — ignore cost, optimize hours."));
		body.add(tip("Iron / GP-mindful: Time 5, GP 8, AFK 2 — bias toward profitable methods."));
		body.add(tip("AFK Andy: Time 2, GP 3, AFK 10 — pick what you can do while watching TV."));

		body.add(Box.createRigidArea(new Dimension(0, 8)));
		body.add(subheading("Tips"));
		body.add(tip("Click any quest in the dependency tree to collapse / expand its prereqs."));
		body.add(tip("If Quest Helper is installed, the Open in Quest Helper button hands the quest off so QH walks you through it step-by-step."));
		body.add(tip("Use the Filter dropdown to narrow the target list to F2P quests, miniquests, or only what you can start right now."));
	}

	private static JLabel subheading(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		return label;
	}

	private static JPanel legendRow(Color swatchColor, String text)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

		JPanel swatch = new JPanel();
		swatch.setBackground(swatchColor);
		swatch.setPreferredSize(new Dimension(12, 12));
		swatch.setMinimumSize(new Dimension(12, 12));
		swatch.setMaximumSize(new Dimension(12, 12));
		swatch.setAlignmentY(Component.CENTER_ALIGNMENT);
		row.add(swatch);
		row.add(Box.createRigidArea(new Dimension(8, 0)));

		JLabel label = new JLabel(text);
		label.setForeground(Color.WHITE);
		label.setFont(label.getFont().deriveFont(Font.PLAIN, 13f));
		row.add(label);
		row.add(Box.createHorizontalGlue());
		return row;
	}

	private static JLabel tip(String text)
	{
		// HTML body width keeps long tips wrapping inside the 225px sidebar.
		// 160 leaves margin: 225 - 10 outer panel border × 2 - 6 helpPanel body
		// padding × 2 - a bit for JLabel internal padding ≈ ~185 actual room,
		// and 160 is a comfortable fit at 13pt without chopping the last word.
		JLabel label = new JLabel("<html><body style='width:160px'>• " + text + "</body></html>");
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(label.getFont().deriveFont(Font.PLAIN, 13f));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		return label;
	}
}
