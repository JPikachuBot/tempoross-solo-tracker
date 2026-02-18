package com.temporosstracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory checklist state.
 *
 * Highlighting rule (per Jackson): highlight the step immediately after the
 * lowest checked box, regardless of gaps above. If nothing is checked, highlight
 * the first step. If all are checked, no active step.
 */
public class TrackerState
{
    private final List<PhaseStep> steps;

    private TrackerState(List<PhaseStep> steps)
    {
        this.steps = new ArrayList<>(steps);
    }

    public static TrackerState fromChecklist(List<PhaseStep> checklist)
    {
        return new TrackerState(copySteps(checklist));
    }

    public List<PhaseStep> getSteps()
    {
        return Collections.unmodifiableList(steps);
    }

    public int size()
    {
        return steps.size();
    }

    public PhaseStep getStep(int index)
    {
        if (index < 0 || index >= steps.size())
        {
            throw new IllegalArgumentException("Index out of range: " + index);
        }
        return steps.get(index);
    }

    /**
     * @return the active step index, or -1 if all steps are checked.
     */
    public int getActiveStepIndex()
    {
        int lastChecked = -1;
        for (int i = 0; i < steps.size(); i++)
        {
            if (steps.get(i).isChecked())
            {
                lastChecked = i;
            }
        }

        int next = lastChecked + 1;
        if (next < 0)
        {
            next = 0;
        }
        return next >= steps.size() ? -1 : next;
    }

    public void setChecked(int index, boolean checked)
    {
        getStep(index).setChecked(checked);
    }

    public void reset()
    {
        for (PhaseStep step : steps)
        {
            step.setChecked(false);
        }
    }

    private static List<PhaseStep> copySteps(List<PhaseStep> checklist)
    {
        List<PhaseStep> copies = new ArrayList<>(checklist.size());
        for (PhaseStep source : checklist)
        {
            PhaseStep copy = new PhaseStep(
                source.getPhaseNumber(),
                source.getPhaseName(),
                source.getStepIndex(),
                source.getLabel(),
                source.isOptional(),
                source.isWarning()
            );
            copy.setChecked(source.isChecked());
            copies.add(copy);
        }
        return copies;
    }
}
