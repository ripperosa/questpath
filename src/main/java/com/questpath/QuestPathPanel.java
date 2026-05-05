package com.questpath;

import com.questpath.QuestPathConfig.DifficultyFilter;
import com.questpath.QuestPathConfig.FilterMode;
import com.questpath.QuestPathConfig.SortMode;
import com.questpath.data.QuestDefinition;
import com.questpath.data.QuestRepository;
import com.questpath.game.GameStateReader;
import com.questpath.game.PlayerState;
import com.questpath.game.QuestStatus;
import com.questpath.planner.DependencyGraph;
import com.questpath.planner.PathPlanner;
import com.questpath.planner.Plan;
import com.questpath.planner.PlanStep;
import com.questpath.planner.PlanStepType;
import com.questpath.ui.DependencyTreePanel;
import com.questpath.ui.HelpPanel;
import com.questpath.ui.QuestInfoCard;
import com.questpath.ui.QuestListPanel;
import com.questpath.ui.SkillRequirementsPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/**
 * Sidebar panel — Phase 4 + 7. Filter / difficulty / sort dropdowns drive the
 * target list; target dropdown drives the plan. Refresh re-snapshots game
 * state. Two stacked body sections: the dependency tree (custom indented
 * panel with HTML word-wrap) and the ordered plan step list.
 */
@Slf4j
public class QuestPathPanel extends PluginPanel
{
	// ----- Font scale ---------------------------------------------------------
	// One pass through the panel reads the same constants so size tweaks land
	// in a single place. "Recommended Order" step cards intentionally use the
	// JLabel default — the user said those are already readable.
	private static final float FONT_SUBTITLE = 12f;
	private static final float FONT_LABEL = 14f;
	private static final float FONT_SECTION_HEADER = 14f;
	private static final float FONT_BODY = 14f;

	private static final String CARD_BROWSE = "browse";
	private static final String CARD_PLAN = "plan";
	private static final String CARD_SETTINGS = "settings";

	private final QuestRepository repository;
	private final GameStateReader gameStateReader;
	private final PathPlanner pathPlanner;
	private final ConfigManager configManager;
	private final QuestPathConfig config;

	private final JComboBox<FilterMode> filterCombo;
	private final JComboBox<DifficultyFilter> difficultyCombo;
	private final JComboBox<SortMode> sortCombo;
	private final JTextField searchField = new JTextField();
	private final QuestListPanel questListPanel;
	private final CardLayout bodyCardLayout = new CardLayout();
	private final JPanel bodyCards = new JPanel(bodyCardLayout);
	private final JSlider timeSlider;
	private final JSlider gpSlider;
	private final JSlider afkSlider;
	private final JLabel timeValueLabel = new JLabel();
	private final JLabel gpValueLabel = new JLabel();
	private final JLabel afkValueLabel = new JLabel();
	private final JLabel summaryLabel = new JLabel();
	private final QuestInfoCard questInfoCard;
	private final SkillRequirementsPanel skillReqsPanel = new SkillRequirementsPanel();
	private final DependencyTreePanel dependencyTree = new DependencyTreePanel();
	private final JPanel stepsContainer = new JPanel();
	private final HelpPanel helpPanel;

	/** Suppresses combo-box action events while we programmatically set the selection. */
	private boolean suppressComboEvents = false;

	// RuneLite's config proxy doesn't reflect setConfiguration() writes until
	// the ConfigChanged event lands, so cache the user's current selections here
	// to avoid reading stale values from config.filterMode() etc. mid-handler.
	private FilterMode currentFilter;
	private DifficultyFilter currentDifficulty;
	private SortMode currentSort;
	private String currentSearch = "";

	public QuestPathPanel(
		QuestRepository repository,
		GameStateReader gameStateReader,
		PathPlanner pathPlanner,
		ConfigManager configManager,
		QuestPathConfig config,
		String initialTargetId)
	{
		super(false);
		this.repository = repository;
		this.gameStateReader = gameStateReader;
		this.pathPlanner = pathPlanner;
		this.configManager = configManager;
		this.config = config;
		this.questInfoCard = new QuestInfoCard();
		this.helpPanel = new HelpPanel(config.helpExpanded(), expanded ->
			configManager.setConfiguration(QuestPathConfig.GROUP, QuestPathConfig.HELP_EXPANDED_KEY, expanded));
		this.questListPanel = new QuestListPanel(this::onQuestSelectedFromList);

		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		this.currentFilter = config.filterMode();
		this.currentDifficulty = config.difficultyFilter();
		this.currentSort = config.sortMode();

		this.filterCombo = buildEnumCombo(FilterMode.values(), currentFilter,
			selected -> {
				currentFilter = selected;
				configManager.setConfiguration(QuestPathConfig.GROUP, QuestPathConfig.FILTER_MODE_KEY, selected);
				rebuildQuestList();
			});
		this.difficultyCombo = buildEnumCombo(DifficultyFilter.values(), currentDifficulty,
			selected -> {
				currentDifficulty = selected;
				configManager.setConfiguration(QuestPathConfig.GROUP, QuestPathConfig.DIFFICULTY_FILTER_KEY, selected);
				rebuildQuestList();
			});
		this.sortCombo = buildEnumCombo(SortMode.values(), currentSort,
			selected -> {
				currentSort = selected;
				configManager.setConfiguration(QuestPathConfig.GROUP, QuestPathConfig.SORT_MODE_KEY, selected);
				rebuildQuestList();
			});

		this.timeSlider = buildWeightSlider(config.timeWeight(), timeValueLabel,
			v -> configManager.setConfiguration(QuestPathConfig.GROUP, QuestPathConfig.TIME_WEIGHT_KEY, v));
		this.gpSlider = buildWeightSlider(config.gpWeight(), gpValueLabel,
			v -> configManager.setConfiguration(QuestPathConfig.GROUP, QuestPathConfig.GP_WEIGHT_KEY, v));
		this.afkSlider = buildWeightSlider(config.afkWeight(), afkValueLabel,
			v -> configManager.setConfiguration(QuestPathConfig.GROUP, QuestPathConfig.AFK_WEIGHT_KEY, v));

		add(buildHeader(), BorderLayout.NORTH);
		add(buildScrollableBody(), BorderLayout.CENTER);

		rebuildQuestList();   // populate the Browse list
		// Returning users land on the plan view of their persisted target;
		// fresh installs default to the Browse list since lunar_diplomacy
		// won't mean much to them.
		boolean hasUserTarget = initialTargetId != null
			&& !initialTargetId.isEmpty()
			&& !"lunar_diplomacy".equals(initialTargetId);
		bodyCardLayout.show(bodyCards, hasUserTarget ? CARD_PLAN : CARD_BROWSE);
		refresh();
	}

	/** Listener — a row in the Quest List was clicked. Persist + show plan. */
	private void onQuestSelectedFromList(String questId)
	{
		configManager.setConfiguration(QuestPathConfig.GROUP, QuestPathConfig.TARGET_QUEST_KEY, questId);
		showPlanCard();
		refresh();
	}

	// ----- Public API ---------------------------------------------------------

	/** Re-render the panel for a freshly computed plan. Safe from any thread. */
	public void updatePlan(Plan plan)
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			renderPlan(plan);
		}
		else
		{
			SwingUtilities.invokeLater(() -> renderPlan(plan));
		}
	}

	/** External nudge: re-snapshot game state and re-plan against current target. */
	public void refresh()
	{
		// Re-sync cached fields from config in case it was changed externally
		// (e.g. via RuneLite's plugin settings UI rather than our combos).
		FilterMode newFilter = config.filterMode();
		DifficultyFilter newDifficulty = config.difficultyFilter();
		SortMode newSort = config.sortMode();
		boolean filtersChanged = newFilter != currentFilter
			|| newDifficulty != currentDifficulty
			|| newSort != currentSort;
		currentFilter = newFilter;
		currentDifficulty = newDifficulty;
		currentSort = newSort;
		if (filtersChanged)
		{
			suppressComboEvents = true;
			try
			{
				filterCombo.setSelectedItem(currentFilter);
				difficultyCombo.setSelectedItem(currentDifficulty);
				sortCombo.setSelectedItem(currentSort);
			}
			finally
			{
				suppressComboEvents = false;
			}
			rebuildQuestList();
		}

		String targetId = (String) configManager.getConfiguration(QuestPathConfig.GROUP, QuestPathConfig.TARGET_QUEST_KEY);
		if (targetId == null || targetId.isEmpty())
		{
			targetId = "lunar_diplomacy";
		}
		// MEETS_REQS depends on game state — rebuild list when state may have changed.
		// Same for the per-row status dots, which all read from PlayerState.
		rebuildQuestList();

		PlayerState state = gameStateReader.snapshot();
		Plan plan = pathPlanner.plan(targetId, state);
		updatePlan(plan);
	}

	// ----- Header / dropdowns -------------------------------------------------

	private JPanel buildHeader()
	{
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

		header.add(buildTitleRow());

		// Summary line ("3 quest · 1 train · 0 gap") — populated by renderPlan().
		summaryLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.PLAIN, FONT_BODY));
		summaryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		summaryLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		header.add(summaryLabel);

		return header;
	}

	/**
	 * Title bar: "QuestPath" on the left, ⚙ gear button on the right (toggles
	 * the body to the Settings card). Subtitle on its own row underneath.
	 */
	private JPanel buildTitleRow()
	{
		JPanel column = new JPanel();
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setBackground(ColorScheme.DARK_GRAY_COLOR);
		column.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel title = new JLabel("QuestPath");
		title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
		title.setForeground(Color.WHITE);

		// GitHub link — opens the public repo in the user's browser via
		// RuneLite's LinkBrowser (allowed API, no reflection, no restrictions).
		JButton github = new JButton("GH");
		github.setToolTipText("View source / report issues on GitHub");
		github.setMargin(new Insets(0, 6, 0, 6));
		github.setFont(github.getFont().deriveFont(Font.BOLD, 12f));
		github.setFocusable(false);
		github.addActionListener(e -> LinkBrowser.browse("https://github.com/ripperosa/questpath"));

		JButton gear = new JButton("⚙");
		gear.setToolTipText("Settings — preferences and help");
		gear.setMargin(new Insets(0, 6, 0, 6));
		gear.setFont(gear.getFont().deriveFont(Font.PLAIN, 16f));
		gear.setFocusable(false);
		gear.addActionListener(e -> showSettingsCard());

		row.add(title);
		row.add(Box.createHorizontalGlue());
		row.add(github);
		row.add(Box.createRigidArea(new Dimension(4, 0)));
		row.add(gear);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		JLabel subtitle = new JLabel("Quest prerequisite planner");
		subtitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, FONT_SUBTITLE));
		subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		subtitle.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));

		column.add(row);
		column.add(subtitle);
		return column;
	}

	/** "Search" text field row — filters the target combo as the user types. */
	private JPanel buildSearchRow()
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

		JLabel label = new JLabel("Search:");
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(label.getFont().deriveFont(Font.PLAIN, FONT_LABEL));
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
		Dimension labelSize = new Dimension(56, 22);
		label.setPreferredSize(labelSize);
		label.setMinimumSize(labelSize);

		searchField.setFont(searchField.getFont().deriveFont(Font.PLAIN, FONT_BODY));
		searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		searchField.setToolTipText("Type to filter the quest list");
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override public void insertUpdate(DocumentEvent e) { onChange(); }
			@Override public void removeUpdate(DocumentEvent e) { onChange(); }
			@Override public void changedUpdate(DocumentEvent e) { onChange(); }
			private void onChange()
			{
				currentSearch = searchField.getText().trim();
				rebuildQuestList();
			}
		});

		row.add(label);
		row.add(searchField);
		return row;
	}

	private JSlider buildWeightSlider(int initial, JLabel valueLabel, java.util.function.IntConsumer onCommit)
	{
		JSlider slider = new JSlider(0, 10, Math.max(0, Math.min(10, initial)));
		slider.setBackground(ColorScheme.DARK_GRAY_COLOR);
		slider.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		slider.setMajorTickSpacing(5);
		slider.setMinorTickSpacing(1);
		slider.setSnapToTicks(true);
		slider.setOpaque(false);
		valueLabel.setText(String.valueOf(slider.getValue()));
		valueLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, FONT_BODY));
		slider.addChangeListener(e -> {
			valueLabel.setText(String.valueOf(slider.getValue()));
			if (!slider.getValueIsAdjusting())
			{
				onCommit.accept(slider.getValue());
				// Re-pick training methods against the new weights.
				refresh();
			}
		});
		return slider;
	}

	/**
	 * Stacked slider row: label + value on top (justified to opposite ends),
	 * full-width slider track below. The horizontal-row layout we used to have
	 * fought BoxLayout for space and let the slider thumb / value clip off the
	 * right edge once the scrollbar took its 16px. This way the slider always
	 * gets the entire content width regardless of font size.
	 */
	private JPanel sliderRow(String labelText, JSlider slider, JLabel valueLabel)
	{
		JPanel column = new JPanel()
		{
			@Override
			public Dimension getMaximumSize()
			{
				Dimension pref = getPreferredSize();
				return new Dimension(Integer.MAX_VALUE, pref.height);
			}
		};
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setBackground(ColorScheme.DARK_GRAY_COLOR);
		column.setAlignmentX(Component.LEFT_ALIGNMENT);
		column.setBorder(BorderFactory.createEmptyBorder(4, 0, 6, 0));

		// Top sub-row: name on the left, current value on the right.
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

		JLabel label = new JLabel(labelText);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(label.getFont().deriveFont(Font.PLAIN, FONT_LABEL));
		header.add(label);
		header.add(Box.createHorizontalGlue());

		valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, FONT_BODY));
		valueLabel.setForeground(Color.WHITE);
		header.add(valueLabel);

		column.add(header);
		column.add(Box.createRigidArea(new Dimension(0, 2)));

		// Slider track: full width, no horizontal competition.
		slider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		slider.setAlignmentX(Component.LEFT_ALIGNMENT);
		column.add(slider);

		return column;
	}

	private JPanel labeledRow(String labelText, Component control)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

		JLabel label = new JLabel(labelText);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(label.getFont().deriveFont(Font.PLAIN, FONT_LABEL));
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
		Dimension labelSize = new Dimension(56, 22);
		label.setPreferredSize(labelSize);
		label.setMinimumSize(labelSize);

		if (control instanceof JComponent)
		{
			JComponent jc = (JComponent) control;
			jc.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
			jc.setFont(jc.getFont().deriveFont(Font.PLAIN, FONT_BODY));
		}

		row.add(label);
		row.add(control);
		return row;
	}

	private <E extends Enum<E>> JComboBox<E> buildEnumCombo(E[] values, E initial, java.util.function.Consumer<E> onChange)
	{
		JComboBox<E> combo = new JComboBox<>(values);
		combo.setSelectedItem(initial);
		combo.addActionListener(e -> {
			if (suppressComboEvents)
			{
				return;
			}
			@SuppressWarnings("unchecked")
			E selected = (E) combo.getSelectedItem();
			if (selected == null)
			{
				return;
			}
			onChange.accept(selected);
		});
		return combo;
	}

	/**
	 * Apply Filter / Difficulty / Sort / Search and push the result into the
	 * Quest List view. Replaces the old combo-rebuild flow now that the list
	 * itself is the picker.
	 */
	private void rebuildQuestList()
	{
		List<QuestDefinition> quests = filteredAndSortedQuests();
		PlayerState state = gameStateReader.snapshot();
		questListPanel.render(quests, state);
	}

	private List<QuestDefinition> filteredAndSortedQuests()
	{
		// Use cached fields, not config.X(), because config proxy values may be
		// stale immediately after setConfiguration() — see currentFilter docs.
		FilterMode filter = currentFilter;
		DifficultyFilter difficulty = currentDifficulty;
		SortMode sort = currentSort;
		String searchLower = currentSearch == null ? "" : currentSearch.toLowerCase();
		PlayerState state = filter == FilterMode.MEETS_REQS ? gameStateReader.snapshot() : null;

		List<QuestDefinition> matches = new ArrayList<>();
		for (QuestDefinition q : repository.getAllQuests())
		{
			if (!passesFilter(q, filter, state))
			{
				continue;
			}
			if (!passesDifficulty(q, difficulty))
			{
				continue;
			}
			if (!searchLower.isEmpty()
				&& !q.getDisplayName().toLowerCase().contains(searchLower))
			{
				continue;
			}
			matches.add(q);
		}

		Comparator<QuestDefinition> cmp;
		switch (sort)
		{
			case Z_A:
				cmp = Comparator.comparing((QuestDefinition q) -> q.getDisplayName().toLowerCase()).reversed();
				break;
			case QP_DESC:
				cmp = Comparator.comparingInt((QuestDefinition q) -> -q.getQuestPointReward())
					.thenComparing(q -> q.getDisplayName().toLowerCase());
				break;
			case QP_ASC:
				cmp = Comparator.comparingInt(QuestDefinition::getQuestPointReward)
					.thenComparing(q -> q.getDisplayName().toLowerCase());
				break;
			case A_Z:
			default:
				cmp = Comparator.comparing(q -> q.getDisplayName().toLowerCase());
				break;
		}
		matches.sort(cmp);
		return matches;
	}

	private static boolean passesFilter(QuestDefinition q, FilterMode filter, PlayerState state)
	{
		String type = q.getQuestType();
		switch (filter)
		{
			case ALL:
				return true;
			case QUEST:
				return type == null || "F2P".equals(type) || "P2P".equals(type);
			case MINIQUEST:
				return "MINIQUEST".equals(type);
			case ACHIEVEMENT_DIARY:
				return "ACHIEVEMENT_DIARY".equals(type);
			case FREE_TO_PLAY:
				return !q.isMembers();
			case MEMBERS:
				return q.isMembers();
			case MEETS_REQS:
				return meetsAllRequirements(q, state);
			default:
				return true;
		}
	}

	private static boolean passesDifficulty(QuestDefinition q, DifficultyFilter filter)
	{
		if (filter == DifficultyFilter.ALL)
		{
			return true;
		}
		String d = q.getDifficulty();
		if (d == null)
		{
			// Unknown difficulty entries can't satisfy a specific-difficulty filter.
			return false;
		}
		return d.equals(filter.name());
	}

	/** Direct check: every prereq DONE + every skill req met. Not transitive. */
	private static boolean meetsAllRequirements(QuestDefinition q, PlayerState state)
	{
		if (q.getPrerequisiteQuestIds() != null)
		{
			for (String prereqId : q.getPrerequisiteQuestIds())
			{
				if (state.getQuestStatus(prereqId) != QuestStatus.DONE)
				{
					return false;
				}
			}
		}
		if (q.getSkillRequirements() != null)
		{
			for (Map.Entry<Skill, Integer> req : q.getSkillRequirements().entrySet())
			{
				if (state.getSkillLevel(req.getKey()) < req.getValue())
				{
					return false;
				}
			}
		}
		return true;
	}

	// ----- Body / cards -------------------------------------------------------

	private JScrollPane buildScrollableBody()
	{
		bodyCards.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bodyCards.add(buildBrowseCard(), CARD_BROWSE);
		bodyCards.add(buildPlanCard(), CARD_PLAN);
		bodyCards.add(buildSettingsCard(), CARD_SETTINGS);
		// Initial card is chosen in the constructor after this method returns.

		JScrollPane scroll = new JScrollPane(bodyCards,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
			JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(null);
		scroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
	}

	/**
	 * Browse card: the Quest Helper-style picker. Filter / Diff / Sort dropdowns
	 * + Search input at the top, then a scrollable list of clickable quest rows.
	 * Click any row to commit it as the target and switch to the Plan card.
	 */
	private JPanel buildBrowseCard()
	{
		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);

		body.add(labeledRow("Filter:", filterCombo));
		body.add(labeledRow("Diff:", difficultyCombo));
		body.add(labeledRow("Sort:", sortCombo));
		body.add(buildSearchRow());
		body.add(Box.createRigidArea(new Dimension(0, 8)));

		body.add(sectionHeader("Quests"));
		questListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(questListPanel);

		body.add(Box.createVerticalGlue());
		return body;
	}

	/**
	 * Plan card: the in-depth view of the currently-selected target quest.
	 * Has a "← Browse" button at the top to return to the picker.
	 */
	private JPanel buildPlanCard()
	{
		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JButton browse = new JButton("← Browse quests");
		browse.setMargin(new Insets(2, 8, 2, 8));
		browse.setFocusable(false);
		browse.setAlignmentX(Component.LEFT_ALIGNMENT);
		browse.addActionListener(e -> showBrowseCard());
		body.add(browse);
		body.add(Box.createRigidArea(new Dimension(0, 8)));

		questInfoCard.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(questInfoCard);
		body.add(Box.createRigidArea(new Dimension(0, 6)));

		body.add(sectionHeader("General Requirements"));
		skillReqsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(skillReqsPanel);
		body.add(Box.createRigidArea(new Dimension(0, 6)));

		body.add(sectionHeader("Dependency Tree"));
		dependencyTree.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(dependencyTree);
		body.add(Box.createRigidArea(new Dimension(0, 6)));

		body.add(sectionHeader("Recommended Order"));
		stepsContainer.setLayout(new BoxLayout(stepsContainer, BoxLayout.Y_AXIS));
		stepsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		stepsContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(tightWrap(stepsContainer));

		body.add(Box.createVerticalGlue());
		return body;
	}

	/**
	 * Settings card: ← Back button, Preferences sliders, Help &amp; Tips.
	 * Reached via the gear button in the header.
	 */
	private JPanel buildSettingsCard()
	{
		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JButton back = new JButton("← Back");
		back.setMargin(new Insets(2, 8, 2, 8));
		back.setFocusable(false);
		back.setAlignmentX(Component.LEFT_ALIGNMENT);
		back.addActionListener(e -> showPlanCard());
		body.add(back);
		body.add(Box.createRigidArea(new Dimension(0, 8)));

		body.add(sectionHeader("Preferences"));
		body.add(sliderRow("Time:", timeSlider, timeValueLabel));
		body.add(sliderRow("GP:", gpSlider, gpValueLabel));
		body.add(sliderRow("AFK:", afkSlider, afkValueLabel));
		body.add(Box.createRigidArea(new Dimension(0, 12)));

		helpPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(helpPanel);

		body.add(Box.createVerticalGlue());
		return body;
	}

	private void showBrowseCard()
	{
		bodyCardLayout.show(bodyCards, CARD_BROWSE);
	}

	private void showPlanCard()
	{
		bodyCardLayout.show(bodyCards, CARD_PLAN);
	}

	private void showSettingsCard()
	{
		bodyCardLayout.show(bodyCards, CARD_SETTINGS);
	}

	private static JLabel sectionHeader(String text)
	{
		JLabel label = new JLabel(text.toUpperCase());
		label.setFont(label.getFont().deriveFont(Font.BOLD, FONT_SECTION_HEADER));
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		return label;
	}

	/**
	 * Wraps a child panel so its maximum size hugs its preferred height, keeping
	 * BoxLayout.Y_AXIS from over-stretching when the scroll viewport has slack.
	 */
	private static JPanel tightWrap(JPanel inner)
	{
		JPanel wrap = new JPanel()
		{
			@Override
			public Dimension getMaximumSize()
			{
				Dimension pref = getPreferredSize();
				return new Dimension(Integer.MAX_VALUE, pref.height);
			}
		};
		wrap.setLayout(new BorderLayout());
		wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrap.add(inner, BorderLayout.CENTER);
		return wrap;
	}

	// ----- Render -------------------------------------------------------------

	private void renderPlan(Plan plan)
	{
		summaryLabel.setText(String.format(
			"<html><b>%d</b> quest · <b>%d</b> train · <b>%d</b> gap"
				+ "  ~%.1f hr · %s gp</html>",
			plan.getQuestStepCount(),
			plan.getTrainingStepCount(),
			plan.getSkillGateStepCount(),
			plan.getTotalEstimatedHours(),
			formatGp(plan.getTotalEstimatedGpCost())));

		// One snapshot drives the info card, skill reqs, and dep tree — keeps them
		// consistent and avoids three live reads of the client-thread state.
		PlayerState state = gameStateReader.snapshot();
		QuestDefinition target = repository.getQuest(plan.getTargetQuestId());
		questInfoCard.render(target, state);
		skillReqsPanel.render(target, state);

		DependencyGraph graph = DependencyGraph.build(repository, plan.getTargetQuestId(), state);
		dependencyTree.render(graph, plan.getTargetQuestId());

		stepsContainer.removeAll();
		if (plan.getSteps().isEmpty())
		{
			stepsContainer.add(messageCard("Already complete.", ColorScheme.PROGRESS_COMPLETE_COLOR));
		}
		else
		{
			int i = 1;
			for (PlanStep step : plan.getSteps())
			{
				stepsContainer.add(stepCard(i++, step));
				stepsContainer.add(Box.createRigidArea(new Dimension(0, 6)));
			}
		}
		stepsContainer.revalidate();
		stepsContainer.repaint();
	}

	private static JPanel stepCard(int index, PlanStep step)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, accentFor(step.getType())),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);

		// HTML wrap so long names (e.g. "The Depths Of Despair") don't clip past
		// the right edge. 170px = 225 sidebar - 20 panel margin - 3 left rail
		// - 16 horizontal card padding - room for JLabel internals.
		JLabel headline = new JLabel("<html><body style='width:170px'>"
			+ index + ".&nbsp;&nbsp;" + escapeHtml(step.getTitle())
			+ "</body></html>");
		headline.setFont(headline.getFont().deriveFont(Font.BOLD));
		headline.setForeground(Color.WHITE);
		headline.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(headline);

		if (step.getSubtitle() != null && !step.getSubtitle().isEmpty())
		{
			JLabel sub = new JLabel("<html>" + step.getSubtitle() + "</html>");
			sub.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			sub.setAlignmentX(Component.LEFT_ALIGNMENT);
			card.add(sub);
		}
		if (step.getReasoning() != null && !step.getReasoning().isEmpty())
		{
			JLabel reason = new JLabel("<html><i>" + step.getReasoning() + "</i></html>");
			reason.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			reason.setAlignmentX(Component.LEFT_ALIGNMENT);
			card.add(reason);
		}
		return card;
	}

	private static JPanel messageCard(String message, Color accent)
	{
		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel label = new JLabel(message);
		label.setForeground(Color.WHITE);
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		card.add(label, BorderLayout.CENTER);
		return card;
	}

	private static Color accentFor(PlanStepType type)
	{
		switch (type)
		{
			case QUEST:
				return new Color(100, 180, 255);
			case TRAINING:
				return ColorScheme.BRAND_ORANGE;
			case SKILL_GATE:
				return ColorScheme.PROGRESS_ERROR_COLOR;
			default:
				return ColorScheme.LIGHT_GRAY_COLOR;
		}
	}

	/** Minimal HTML escape so quest titles with special chars (e.g. "Romeo &amp; Juliet")
	 *  don't break our HTML-wrapped labels. */
	private static String escapeHtml(String s)
	{
		if (s == null) return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String formatGp(double gp)
	{
		if (gp == 0)
		{
			return "0";
		}
		String sign = gp > 0 ? "-" : "+";
		double abs = Math.abs(gp);
		if (abs >= 1_000_000)
		{
			return sign + String.format("%.1fm", abs / 1_000_000);
		}
		if (abs >= 1_000)
		{
			return sign + String.format("%.0fk", abs / 1_000);
		}
		return sign + String.format("%.0f", abs);
	}

}
