package com.temporosstracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class TemporossSoloTrackerPanel extends PluginPanel
{
    private static final Color ACTIVE_STEP_COLOR = new Color(255, 253, 231);
    private static final Color GOLD_COLOR = new Color(242, 140, 0);

    // Width hint for HTML-wrapped checkbox labels (RuneLite sidebar is narrow).
    private static final int LABEL_WRAP_PX = 145;

    private final JButton resetButton;
    private final JPanel content;
    private final TrackerState trackerState;
    private final List<StepRow> stepRows = new ArrayList<>();

    private Runnable onReset;

    public TemporossSoloTrackerPanel()
    {
        this(ChecklistDefinition.createChecklist(), null);
    }

    public TemporossSoloTrackerPanel(List<PhaseStep> checklist, TrackerState trackerState)
    {
        TrackerState state = trackerState != null ? trackerState : TrackerState.fromChecklist(checklist);
        this.trackerState = state;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        content = new JPanel();
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel title = new JLabel("Tempoross Solo Tracker");
        title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        content.add(title);

        resetButton = new JButton("Reset");
        resetButton.setAlignmentX(LEFT_ALIGNMENT);
        resetButton.addActionListener(event -> handleReset());
        content.add(resetButton);

        buildChecklist(state);
        updateActiveStepHighlight();

        // RuneLite already provides scrolling for sidebar panels.
        add(content, BorderLayout.CENTER);
    }

    public void setOnReset(Runnable onReset)
    {
        this.onReset = onReset;
    }

    /**
     * Programmatic reset (used by the plugin when auto-reset triggers).
     */
    public void resetChecklist()
    {
        handleReset();
    }

    public TrackerState getTrackerState()
    {
        return trackerState;
    }

    private void buildChecklist(TrackerState state)
    {
        int currentPhase = -1;
        for (int i = 0; i < state.size(); i++)
        {
            PhaseStep step = state.getStep(i);
            if (step.getPhaseNumber() != currentPhase)
            {
                currentPhase = step.getPhaseNumber();
                boolean phaseOptional = isPhaseOptional(state, currentPhase);
                content.add(createPhaseHeader(step.getPhaseName(), phaseOptional));
            }

            JCheckBox checkbox = new JCheckBox(formatLabel(step.getLabel(), step.isChecked()), step.isChecked());
            checkbox.setAlignmentX(LEFT_ALIGNMENT);
            checkbox.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            checkbox.setBackground(ColorScheme.DARK_GRAY_COLOR);
            checkbox.setOpaque(false);

            JPanel row = new JPanel(new BorderLayout());
            row.setAlignmentX(LEFT_ALIGNMENT);
            row.setBackground(ColorScheme.DARK_GRAY_COLOR);
            row.setBorder(new EmptyBorder(2, 0, 2, 0));
            row.add(checkbox, BorderLayout.WEST);

            int stepIndex = i;
            checkbox.addActionListener(event -> {
                trackerState.setChecked(stepIndex, checkbox.isSelected());
                updateActiveStepHighlight();
            });

            stepRows.add(new StepRow(stepIndex, row, checkbox, step.getLabel(), checkbox.getFont(), step.isWarning()));
            content.add(row);
        }
    }

    private JLabel createPhaseHeader(String phaseName, boolean optional)
    {
        JLabel header = new JLabel(optional ? phaseName + " (Optional)" : phaseName);
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.setForeground(GOLD_COLOR);
        Font baseFont = header.getFont().deriveFont(Font.BOLD, 13f);
        if (optional)
        {
            baseFont = baseFont.deriveFont(Font.ITALIC);
        }
        header.setFont(baseFont);
        header.setBorder(new EmptyBorder(10, 0, 4, 0));
        return header;
    }

    private boolean isPhaseOptional(TrackerState state, int phaseNumber)
    {
        boolean sawPhase = false;
        for (int i = 0; i < state.size(); i++)
        {
            PhaseStep step = state.getStep(i);
            if (step.getPhaseNumber() != phaseNumber)
            {
                if (sawPhase)
                {
                    break;
                }
                continue;
            }
            sawPhase = true;
            if (!step.isOptional())
            {
                return false;
            }
        }
        return true;
    }

    private void updateActiveStepHighlight()
    {
        int activeIndex = trackerState.getActiveStepIndex();
        for (StepRow row : stepRows)
        {
            boolean isActive = (activeIndex != -1) && row.index == activeIndex;
            row.panel.setBackground(isActive ? ACTIVE_STEP_COLOR : ColorScheme.DARK_GRAY_COLOR);
            row.panel.setOpaque(true);

            PhaseStep step = trackerState.getStep(row.index);
            boolean checked = step.isChecked();
            row.checkbox.setSelected(checked);
            row.checkbox.setText(formatLabel(row.labelText, checked));

            if (checked)
            {
                row.checkbox.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
                row.checkbox.setFont(row.baseFont);
            }
            else if (row.isWarning)
            {
                row.checkbox.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                row.checkbox.setFont(row.baseFont.deriveFont(Font.BOLD));
            }
            else
            {
                row.checkbox.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                row.checkbox.setFont(row.baseFont);
            }
        }
        content.revalidate();
        content.repaint();
    }

    private void handleReset()
    {
        trackerState.reset();
        for (StepRow row : stepRows)
        {
            row.checkbox.setSelected(false);
        }
        updateActiveStepHighlight();
        if (onReset != null)
        {
            onReset.run();
        }
    }

    private static final class StepRow
    {
        private final int index;
        private final JPanel panel;
        private final JCheckBox checkbox;
        private final String labelText;
        private final Font baseFont;
        private final boolean isWarning;

        private StepRow(int index, JPanel panel, JCheckBox checkbox, String labelText, Font baseFont, boolean isWarning)
        {
            this.index = index;
            this.panel = panel;
            this.checkbox = checkbox;
            this.labelText = labelText;
            this.baseFont = baseFont;
            this.isWarning = isWarning;
        }
    }

    private String formatLabel(String labelText, boolean checked)
    {
        String safe = escapeHtml(labelText);

        if (!checked)
        {
            return "<html><div style='width:" + LABEL_WRAP_PX + "px'>" + safe + "</div></html>";
        }

        return "<html><div style='width:" + LABEL_WRAP_PX + "px'><span style='text-decoration: line-through;'>"
            + safe
            + "</span></div></html>";
    }

    private String escapeHtml(String text)
    {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}
