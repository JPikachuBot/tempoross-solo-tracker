package com.temporosstracker;

import java.util.ArrayList;
import java.util.List;

public final class ChecklistDefinition
{
    private ChecklistDefinition()
    {
    }

    public static List<PhaseStep> createChecklist()
    {
        List<PhaseStep> steps = new ArrayList<>();

        addPhase(steps, 1, "Phase 1 — Double Spot Fishing Setup", new String[]
        {
            "Fish up to 8 at first spot",
            "Cook fish until double spot appears",
            "Move to double spot",
            "Fish until 16 fish obtained at double spot",
            "Finish cooking all 16 fish",
            "Fill cannon with all 16 fish",
            "Put out any fires on the way back to fishing"
        });

        addPhase(steps, 2, "Phase 2 — Prep and First Damage Phase", new String[]
        {
            "Fish full inventory; utilize double spot as much as possible",
            "Cook full inventory — storm intensity must stay below 93%!",
            "Fill cannon (full inventory)",
            "Damage Tempoross to ~60%"
        }, false, new int[] {2});

        addPhase(steps, 3, "Recovery Phase — Catch Up", new String[]
        {
            "Optional recovery as needed: finish fishing/cooking to recover the total 19 fish if time ran out in Phase 2"
        }, true);

        addPhase(steps, 4, "Phase 3 — Full Inventory and Second Damage Phase", new String[]
        {
            "Fish full inventory",
            "Cook full inventory — storm intensity must stay below 93%!",
            "Fill cannon (full inventory)",
            "Damage Tempoross to ~30%"
        }, false, new int[] {2});

        addPhase(steps, 5, "Phase 4 — Double Fish, Enrage Skip, and Final Damage", new String[]
        {
            "Fish and cook full inventory; utilize double spot as much as possible",
            "Drop all (19) cooked fish at second cannon",
            "Fish and cook 16; utilize double spot as much as possible",
            "Fill first cannon with 16 fish",
            "Pick up full inventory of cooked fish at second cannon",
            "Fill second cannon (full inventory)",
            "Damage Tempoross to 0% to finish with 10 permits"
        });

        return steps;
    }

    private static void addPhase(List<PhaseStep> steps, int phaseNumber, String phaseName, String[] labels)
    {
        addPhase(steps, phaseNumber, phaseName, labels, false, null);
    }

    private static void addPhase(
        List<PhaseStep> steps,
        int phaseNumber,
        String phaseName,
        String[] labels,
        boolean optional
    )
    {
        addPhase(steps, phaseNumber, phaseName, labels, optional, null);
    }

    private static void addPhase(
        List<PhaseStep> steps,
        int phaseNumber,
        String phaseName,
        String[] labels,
        boolean optional,
        int[] warningSteps
    )
    {
        for (int i = 0; i < labels.length; i++)
        {
            int stepNumber = i + 1;
            boolean warning = isWarningStep(stepNumber, warningSteps);
            steps.add(new PhaseStep(phaseNumber, phaseName, stepNumber, labels[i], optional, warning));
        }
    }

    private static boolean isWarningStep(int stepNumber, int[] warningSteps)
    {
        if (warningSteps == null)
        {
            return false;
        }
        for (int warningStep : warningSteps)
        {
            if (warningStep == stepNumber)
            {
                return true;
            }
        }
        return false;
    }
}
