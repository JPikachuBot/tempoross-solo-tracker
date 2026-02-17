package com.temporosstracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TrackerState
{
    private final List<PhaseStep> steps;

    private TrackerState(List<PhaseStep> steps)
    {
        this.steps = new ArrayList<>(steps);
    }

    public static TrackerState fromChecklist(List<PhaseStep> checklist)
    {
        return new TrackerState(copySteps(checklist, null));
    }

    public static TrackerState deserialize(String raw, List<PhaseStep> checklist)
    {
        List<Boolean> values = parseSerialized(raw, checklist.size());
        return new TrackerState(copySteps(checklist, values));
    }

    public String serialize()
    {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < steps.size(); i++)
        {
            if (i > 0)
            {
                builder.append(',');
            }
            builder.append(steps.get(i).isChecked() ? '1' : '0');
        }
        return builder.toString();
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

    public int getActiveStepIndex()
    {
        int firstOptionalUnchecked = -1;
        for (int i = 0; i < steps.size(); i++)
        {
            PhaseStep step = steps.get(i);
            if (step.isChecked())
            {
                continue;
            }

            if (!step.isOptional())
            {
                return i;
            }

            if (firstOptionalUnchecked == -1)
            {
                firstOptionalUnchecked = i;
            }
        }
        return firstOptionalUnchecked;
    }

    public void setChecked(int index, boolean checked)
    {
        PhaseStep step = getStep(index);
        step.setChecked(checked);
    }

    public void toggleStep(int index)
    {
        PhaseStep step = getStep(index);
        step.setChecked(!step.isChecked());
    }

    public void reset()
    {
        for (PhaseStep step : steps)
        {
            step.setChecked(false);
        }
    }

    private static List<Boolean> parseSerialized(String raw, int expectedSize)
    {
        if (raw == null || raw.trim().isEmpty())
        {
            return new ArrayList<>(Collections.nCopies(expectedSize, Boolean.FALSE));
        }

        String[] parts = raw.split(",");
        List<Boolean> values = new ArrayList<>();
        for (String part : parts)
        {
            String normalized = part.trim();
            values.add("1".equals(normalized));
        }

        if (values.size() < expectedSize)
        {
            values.addAll(Collections.nCopies(expectedSize - values.size(), Boolean.FALSE));
        }
        else if (values.size() > expectedSize)
        {
            values = new ArrayList<>(values.subList(0, expectedSize));
        }
        return values;
    }

    private static List<PhaseStep> copySteps(List<PhaseStep> checklist, List<Boolean> checkedValues)
    {
        List<PhaseStep> copies = new ArrayList<>(checklist.size());
        for (int i = 0; i < checklist.size(); i++)
        {
            PhaseStep source = checklist.get(i);
            PhaseStep copy = new PhaseStep(
                source.getPhaseNumber(),
                source.getPhaseName(),
                source.getStepIndex(),
                source.getLabel(),
                source.isOptional(),
                source.isWarning()
            );
            if (checkedValues != null && i < checkedValues.size())
            {
                copy.setChecked(checkedValues.get(i));
            }
            copies.add(copy);
        }
        return copies;
    }
}
