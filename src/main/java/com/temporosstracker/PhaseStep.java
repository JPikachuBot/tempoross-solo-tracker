package com.temporosstracker;

public class PhaseStep
{
    private final int phaseNumber;
    private final String phaseName;
    private final int stepIndex;
    private final String label;
    private final boolean optional;
    private final boolean warning;
    private boolean checked;

    public PhaseStep(int phaseNumber, String phaseName, int stepIndex, String label, boolean optional)
    {
        this(phaseNumber, phaseName, stepIndex, label, optional, false);
    }

    public PhaseStep(
        int phaseNumber,
        String phaseName,
        int stepIndex,
        String label,
        boolean optional,
        boolean warning
    )
    {
        this.phaseNumber = phaseNumber;
        this.phaseName = phaseName;
        this.stepIndex = stepIndex;
        this.label = label;
        this.optional = optional;
        this.warning = warning;
        this.checked = false;
    }

    public int getPhaseNumber()
    {
        return phaseNumber;
    }

    public String getPhaseName()
    {
        return phaseName;
    }

    public int getStepIndex()
    {
        return stepIndex;
    }

    public String getLabel()
    {
        return label;
    }

    public boolean isOptional()
    {
        return optional;
    }

    public boolean isWarning()
    {
        return warning;
    }

    public boolean isChecked()
    {
        return checked;
    }

    public void setChecked(boolean checked)
    {
        this.checked = checked;
    }
}
