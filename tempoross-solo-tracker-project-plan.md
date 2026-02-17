# Tempoross Solo Tracker — RuneLite Plugin Project Plan

## Overview
A RuneLite sidebar plugin that provides an interactive checklist for solo Tempoross runs. It tracks the player's current phase, highlights the active step, sends chat notifications at 92%+ storm intensity, auto-resets on new games (via region ID detection), and persists progress.

---

## Tech Stack & Environment
- **Language**: Java 11+
- **Build**: Gradle (RuneLite plugin standard)
- **Framework**: RuneLite Plugin API (`runelite-api`, `runelite-client`)
- **UI**: Java Swing (RuneLite sidebar panel pattern)
- **Testing target**: RuneLite Developer mode / Plugin Hub submission (later)

---

## Plugin Architecture

### Files to Create

```
tempoross-solo-tracker/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── src/main/java/com/temporosstracker/
│   ├── TemporossSoloTrackerPlugin.java      # Main plugin class
│   ├── TemporossSoloTrackerConfig.java       # Config interface (if any toggles needed)
│   ├── TemporossSoloTrackerPanel.java        # Sidebar Swing panel with checkboxes
│   ├── TemporossSoloTrackerOverlay.java      # (Optional) overlay if we want on-screen step display
│   ├── PhaseStep.java                        # Data model for a single checklist step
│   └── TrackerState.java                     # State manager (persistence, reset, active step tracking)
├── src/main/resources/
│   └── com/temporosstracker/
│       └── icon.png                          # Sidebar icon (16x16 or 24x24)
└── runelite-plugin.properties                # Plugin descriptor for RuneLite
```

---

## Checklist Data Model

Each step is a `PhaseStep` object:

```java
public class PhaseStep {
    int phaseNumber;       // 1-5 (including optional recovery phase)
    String phaseName;      // e.g. "Phase 1 - Double Spot Fishing Setup"
    int stepIndex;         // order within phase
    String label;          // checkbox display text
    boolean checked;       // state
    boolean optional;      // if true, step can be skipped without blocking progress
}
```

### Full Checklist Definition

#### Phase 1 — Double Spot Fishing Setup
| # | Checkbox Label |
|---|----------------|
| 1 | Fish up to 8 at first spot |
| 2 | Cook fish until double spot appears |
| 3 | Move to double spot |
| 4 | Fish until 16 fish obtained at double spot |
| 5 | Finish cooking all 16 fish |
| 6 | Fill cannon with all 16 fish |
| 7 | Put out any fires on the way back to fishing |

#### Phase 2 — Prep and First Damage Phase
| # | Checkbox Label |
|---|----------------|
| 1 | Fish full inventory; utilize double spot as much as possible |
| 2 | Cook full inventory — Storm intensity must stay ≤93%! |
| 3 | Fill cannon (full inventory) |
| 4 | Damage Tempoross to ~60% |

#### Recovery Phase — Catch Up (Optional)
> This phase appears between Phase 2 and Phase 3. It is needed in case you need to catch up on fishing due to storm intensity if unlucky with fish timing on the first full inventory.

| # | Checkbox Label | Optional? |
|---|----------------|-----------|
| 1 | Optional recovery as needed: finish fishing/cooking to recover the total 19 fish if time ran out in Phase 2 | ✅ Yes |

**UI behavior for optional recovery phase:**
- This phase should be visually distinct (e.g., italic header, muted color, or a "(Optional)" tag in the header).
- The single checkbox can be **skipped** — the user can click Phase 3's first step without checking this one, and the highlight should advance past it.
- If the user does check it, it behaves like any other checkbox (gray out, advance).
- The auto-advance logic should treat optional steps as skippable: if the user clicks Phase 3 step 1 without checking the recovery step, the highlight jumps to Phase 3 step 1 without flagging recovery as incomplete.

#### Phase 3 — Full Inventory and Second Damage Phase
| # | Checkbox Label |
|---|----------------|
| 1 | Fish full inventory |
| 2 | Cook full inventory — Storm intensity must stay ≤93%! |
| 3 | Fill cannon (full inventory) |
| 4 | Damage Tempoross to ~30% |

#### Phase 4 — Double Fish, Enrage Skip, and Final Damage
| # | Checkbox Label |
|---|----------------|
| 1 | Fish and cook full inventory; utilize double spot as much as possible |
| 2 | Drop all (19) cooked fish at second cannon |
| 3 | Fish and cook 16; utilize double spot as much as possible |
| 4 | Fill first cannon with 16 fish |
| 5 | Pick up full inventory of cooked fish at second cannon |
| 6 | Fill second cannon (full inventory) |
| 7 | Damage Tempoross to 0% to finish with 10 permits |

---

## Feature Specifications

### 1. Sidebar Panel (`TemporossSoloTrackerPanel.java`)

- Renders as a RuneLite sidebar tab (icon in the sidebar strip).
- Panel layout (top to bottom):
  - **Title**: "Tempoross Solo Tracker"
  - **Reset Button**: Manual reset (resets all checkboxes)
  - **Phase Sections** (5 sections, each with a header — including the optional Recovery Phase):
    - Phase header is a bold label with the phase name
    - Recovery Phase header should include "(Optional)" and be visually distinct (italic or muted)
    - Each step is a `JCheckBox` with the label text
    - The **current active step** (first unchecked non-optional step, or first unchecked optional step if no required steps precede it) is highlighted with a distinct background color (e.g., light yellow `#FFFDE7` or RuneLite orange tint)
    - Checked items get a muted/gray strikethrough style
  - **Storm Intensity Warning Labels** (Phase 2 step 2 and Phase 3 step 2): Render in **red/orange bold text** as a visual label rather than a normal checkbox — but still include a checkbox so the user can acknowledge/advance past it.
- **Clicking a checkbox**:
  - Toggles the checked state
  - Automatically advances the highlight to the next unchecked step (skipping optional unchecked steps if a later required step is clicked)
  - Persists state immediately
- **Scrollable**: Wrap in `JScrollPane` in case panel is taller than sidebar.

### 2. Auto-Reset on New Game

- **Detection method**: Check the local player's region ID.
  - Tempoross lobby/waiting area region: `12588` (**verified**, Jackson, 2026-02-17)
  - Tempoross island fight region: `12076` (**verified**, Jackson, 2026-02-17)
- **Trigger**: When the player enters the Tempoross fight region AND the previous state was NOT in the fight region → reset all checkboxes.
- Edge case: Don't reset if the player is already on the island (e.g., sidebar opened mid-game).

### 3. Storm Intensity Monitoring & Notifications

- **Source (verified)**: Storm intensity is available from a RuneLite widget during the Tempoross encounter.
  - Widget: **group 437, child 23** (text like `Storm intensity: 86%`) — verified by Jackson (2026-02-17)
  - Alternative (preferred if found later): VarPlayer/VarBit; see Appendix C.
- **Behavior**:
  - On every game tick (`onGameTick`), read the storm intensity value.
  - If intensity ≥ 92%:
    - Send a **RuneLite chat notification** (using `notifier.notify("⚠️ Storm intensity at " + intensity + "%! Wait before filling cannon!")`)
    - **Repeat** every ~5 game ticks (~3 seconds) while intensity remains ≥ 92%
    - Use a cooldown counter to avoid spam (notify once per ~5 ticks)
  - Also visually update the warning steps (Phase 2 step 2 and Phase 3 step 2) in the panel — e.g., turn the background red when intensity ≥ 92%.

### 4. State Persistence

- Use RuneLite's `ConfigManager` to save/load the checklist state.
- Config key: `tempoross-solo-tracker.checklistState`
- Serialize as a simple comma-separated string of 0s and 1s representing each checkbox state in order.
- Load on plugin startup, save on every checkbox toggle.
- Clear on game reset (write all 0s).

### 5. Config Options (`TemporossSoloTrackerConfig.java`)

| Config Key | Type | Default | Description |
|---|---|---|---|
| `notifyAt92` | boolean | true | Enable/disable 92% storm intensity notification |
| `notifyCooldownTicks` | int | 5 | Ticks between repeated 92% warnings |
| `autoReset` | boolean | true | Auto-reset checkboxes on new Tempoross game |
| `highlightCurrentStep` | boolean | true | Highlight the next unchecked step |

---

## Implementation Notes for the Developer

### RuneLite Plugin Boilerplate
- Annotate main class with `@PluginDescriptor(name = "Tempoross Solo Tracker", description = "Interactive checklist for solo Tempoross runs", tags = {"tempoross", "minigame", "tracker", "checklist"})`
- Register the panel via `NavigationButton` in `startUp()`:
  ```java
  final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
  navButton = NavigationButton.builder()
      .tooltip("Tempoross Solo Tracker")
      .icon(icon)
      .panel(panel)
      .priority(5)
      .build();
  clientToolbar.addNavigation(navButton);
  ```
- Inject: `Client`, `ClientToolbar`, `Notifier`, `ConfigManager`
- Subscribe: `@Subscribe onGameTick(GameTick event)`, `@Subscribe onGameStateChanged(GameStateChanged event)`

### Widget / Var Research Required
The implementing developer needs to research and verify (see Appendices A–C for step-by-step guides):
1. **Tempoross region IDs** — See Appendix A
2. **Storm intensity widget** — See Appendix B
3. **Storm intensity VarPlayer/VarBit (alternative)** — See Appendix C
4. **Tempoross HP / energy percentage** — If we want to show current Tempoross HP in the panel (nice-to-have), find the relevant widget or var using the same techniques from Appendices B and C

### Swing Panel Styling
- Use `PluginPanel` as the base class (extends `JPanel` with RuneLite's color scheme)
- Use `ColorScheme` from RuneLite client for consistent dark theme colors
- Active step highlight: `ColorScheme.PROGRESS_INPROGRESS_COLOR` or a custom light accent
- Checked step: gray text, optional strikethrough via HTML labels `<html><s>text</s></html>`
- Phase headers: bold, slightly larger font, with a separator line
- Recovery Phase header: italic, with "(Optional)" appended

### Gradle Build
- Use RuneLite's standard plugin Gradle template
- Target RuneLite API version: latest stable (currently ~1.10.x — verify at time of implementation)
- Plugin should compile with `./gradlew build` and be loadable via RuneLite's "Load External Plugin" developer feature

---

## Development notes

### 2026-02-16 — Build dependency roadblock (`repo.runelite.net` / Gradle)

**Symptom**
- `./gradlew build` failed during dependency resolution with messages like:
  - `Could not find any matches for net.runelite:runelite-client:...`
  - and earlier confusion around `repo.runelite.net` returning errors when accessed directly.

**Root cause**
- The build file used the wrong Maven coordinates (`net.runelite:runelite-api` and `net.runelite:runelite-client`). Those artifacts are not published under those names.
- RuneLite external plugin best practice is to depend on `net.runelite:client` (and optionally `net.runelite:jshell`) from `https://repo.runelite.net`.

**Fix**
- Updated `build.gradle` to match the official RuneLite example plugin pattern:
  - Repositories: `mavenLocal()`, `maven { url = "https://repo.runelite.net" }`, `mavenCentral()`
  - Version: `latest.release`
  - Dependencies: `compileOnly "net.runelite:client:<version>"`, plus test deps (`client`, `jshell`).
  - Compile target remains Java 11 via `options.release.set(11)` (works with JDK 17 installed).

**Verification**
- `./gradlew build` succeeded after the change.

## Acceptance Criteria

1. ✅ Sidebar panel appears with "Tempoross Solo Tracker" title and icon
2. ✅ All 5 phases render with correct headers and checkbox labels (including optional Recovery Phase)
3. ✅ Recovery Phase is visually distinct and skippable
4. ✅ Clicking a checkbox checks it, grays it out, and highlights the next step
5. ✅ Manual "Reset" button clears all checkboxes
6. ✅ Entering the Tempoross fight region resets all checkboxes automatically
7. ✅ Storm intensity ≥92% triggers a repeating RuneLite chat notification (~every 3 seconds)
8. ✅ Warning steps in the panel visually change when intensity ≥92%
9. ✅ Checklist state persists across sidebar open/close and client restarts
10. ✅ Config panel allows toggling notifications and auto-reset
11. ✅ Plugin compiles cleanly with Gradle and loads in RuneLite developer mode

---

## Nice-to-Have (Future Enhancements)
- Show Tempoross HP percentage in the panel
- Auto-check steps based on game events (e.g., detect fishing/cooking animations)
- Phase auto-collapse when all steps are checked
- Sound alert option for 92% warning
- Run counter / personal best tracker

---

## Appendix A: How to Find Tempoross Region IDs (Step-by-Step)

> **Status (verified)**: Region IDs have been verified in-game.
> - Lobby / waiting room region ID: `12588` (Jackson, 2026-02-17)
> - Fight / island region ID: `12076` (Jackson, 2026-02-17)

### What You'll Need
- RuneLite client (installed and logged in)
- A character that can access Tempoross (Fishing level 35+)

### Steps

1. **Open RuneLite** and log in to your account.

2. **Enable Developer Tools**:
   - Click the wrench icon (Configuration) in the RuneLite sidebar.
   - Search for "Developer Tools" in the plugin list.
   - Toggle it **ON**.

3. **Open the Developer Tools panel**:
   - A new icon should appear in the sidebar (looks like a code bracket `<>`).
   - Click it. You'll see tabs like "Widgets", "Vars", "Inventory", etc.
   - You DON'T need to click any tab for this step — we'll use the chat output method instead.

4. **Open the chat console** (the RuneLite one):
   - In the Developer Tools panel, find the **"Shell"** or **"Console"** tab.
   - If there's no shell, you can instead use a quick temporary plugin snippet. The easier method: just proceed to step 5 and read the output from the game chat.

5. **Use the Region ID logger**:
   - The simplest method: When Claude Code builds the plugin, it should include a **temporary debug line** in the `onGameTick` method that prints the region ID to the chat:
     ```java
     // TEMPORARY DEBUG — REMOVE AFTER VERIFYING REGION IDS
     client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Region ID: " + client.getLocalPlayer().getWorldLocation().getRegionID(), null);
     ```
   - Compile and load the plugin with this debug line active.

6. **Go to the Tempoross lobby**:
   - Travel to the Tempoross dock (Al Kharid port, talk to Ferryman).
   - Stand in the **waiting area** (before boarding the boat).
   - Look at the RuneLite chat — note the **Region ID** number displayed. This is your **lobby region ID**.
   - **Write it down.**

7. **Start a Tempoross game**:
   - Board the boat and start the encounter.
   - Once you're on the **Tempoross island** (the fight area), look at the chat again.
   - Note the **Region ID** displayed. This is your **fight region ID**.
   - **Write it down.**

8. **Update the plugin code**:
   - `TemporossSoloTrackerPlugin.java` has been updated to the verified values (`12588` lobby, `12076` fight).
   - If you ever re-verify and find different values, update those constants and recompile.

### What to Tell Claude Code
After you have the region IDs, tell Claude Code:
> "The Tempoross lobby region ID is [YOUR_NUMBER] and the fight region ID is [YOUR_NUMBER]. Update the constants in TemporossSoloTrackerPlugin.java."

---

## Appendix B: How to Find the Storm Intensity Widget ID (Step-by-Step)

> **Why this is needed**: The plugin reads the storm intensity percentage from a UI widget to trigger the 92% notification. We need the exact widget group ID and child ID.

### What You'll Need
- RuneLite client with Developer Tools enabled (see Appendix A, step 2)
- Be **inside an active Tempoross fight** (you need the storm intensity HUD visible on screen)

### Steps

1. **Start a Tempoross game** and get to the point where the storm intensity bar/percentage is visible on your screen.

2. **Open the Widget Inspector**:
   - Click the Developer Tools sidebar icon.
   - Click the **"Widgets"** tab at the top.
   - You'll see a tree of widget groups on the left side (numbered like 0, 1, 2, ... 160, etc.).

3. **Enable "Pick" mode** (easiest method):
   - At the top of the Widget Inspector, look for a **"Pick"** button or toggle.
   - Click it to enable pick mode.
   - Now **hover your mouse** over the storm intensity display in the game. The widget inspector should highlight and show the widget info.
   - If pick mode isn't available, proceed to step 4 for manual searching.

4. **Manual search (if pick mode doesn't work)**:
   - In the widget tree, look for widget groups that are currently **visible** (they may be highlighted or bold).
   - During a Tempoross fight, a widget group specific to the minigame HUD should be active.
   - Try groups in the range **437–500+** (minigame-specific widgets tend to be higher numbers, but this varies).
   - Click through each visible group and expand its children. Look for a child widget whose **text content** shows a number like "45%" or "Storm Intensity" or similar.

5. **Note the Widget IDs**:
   - Once you find the storm intensity widget, note TWO numbers:
     - **Widget Group ID** (the parent number, e.g., `437`)
     - **Widget Child ID** (the child index, e.g., `16`)
   - Together, these identify the widget as `437.16` (or whatever the real values are).
   - **Write these down.**

6. **Verify by watching it change**:
   - Keep the Widget Inspector open on that widget.
   - Play for a bit and watch the storm intensity change in-game.
   - Confirm that the widget's text value updates as the storm intensity changes.
   - If it doesn't update, you found the wrong widget — go back to step 4.

### What to Tell Claude Code
After you have the widget IDs, tell Claude Code:
> "The storm intensity widget is at group ID [NUMBER], child ID [NUMBER]. Update the storm intensity reading code in TemporossSoloTrackerPlugin.java."

---

## Appendix C: How to Check VarPlayer/VarBit Values (Alternative to Widget)

> **Why this might be better**: Sometimes game values like storm intensity are stored in a VarPlayer or VarBit rather than (or in addition to) a widget. Varps are more reliable to read programmatically.

### What You'll Need
- RuneLite client with Developer Tools enabled
- Be **inside an active Tempoross fight**

### Steps

1. **Start a Tempoross game** and get to the fight.

2. **Open the Var Inspector**:
   - Click the Developer Tools sidebar icon.
   - Click the **"Vars"** tab at the top.
   - You'll see two sub-tabs: **"VarPlayer"** and **"VarBit"**.

3. **Enable change tracking**:
   - At the top of the Var Inspector, there should be a **"Track changes"** or **"Highlight changed"** toggle.
   - Turn it **ON**. This will highlight any var that changes value in real-time.

4. **Watch for storm intensity changes**:
   - Play the Tempoross game and fish/cook to make the storm intensity go up.
   - Watch the Var Inspector — any VarPlayer or VarBit that changes will be highlighted.
   - Look for a var whose value changes in a way that matches the storm intensity percentage (e.g., goes from 30 to 35 to 40 as the storm builds).

5. **Note the Var ID**:
   - Once you find a var that tracks storm intensity, note:
     - Whether it's a **VarPlayer** or **VarBit**
     - The **index number** (e.g., VarPlayer 1234 or VarBit 5678)
   - **Write it down.**

6. **Verify**:
   - Watch it change a few more times to confirm it matches storm intensity.
   - Also check if it resets to 0 when a new round starts.

### What to Tell Claude Code
After you have the var info, tell Claude Code:
> "Storm intensity is tracked by [VarPlayer/VarBit] index [NUMBER]. Update the storm intensity reading code to use `client.getVarpValue([NUMBER])` or `client.getVarbitValue([NUMBER])` instead of widget reading."

---

## Appendix D: Giving Claude Code Your Verified Values

Once you've gathered the real values from Appendices A–C, you can update the plugin in one shot. Give Claude Code this prompt:

> "Update the Tempoross Solo Tracker plugin with these verified in-game values:
> - Tempoross lobby region ID: [NUMBER]
> - Tempoross fight region ID: [NUMBER]
> - Storm intensity source: [WIDGET group X child Y] or [VarPlayer/VarBit INDEX]
>
> Replace all TODO placeholders and placeholder constants with these real values. Remove any temporary debug logging lines."

---

## How to Use This Plan

Feed this entire document to **Claude Code** or **Codex** with the prompt:

> "Implement this RuneLite plugin according to the project plan. Create all files in the specified directory structure. Use RuneLite's standard plugin patterns and API. For any widget IDs or region IDs marked as needing verification, use the placeholder values in the plan and add TODO comments with instructions for the developer to verify in-game (referencing the Appendices). Include the temporary debug region ID logger from Appendix A step 5 (commented out, with a note to uncomment for testing). Start with the Gradle build files, then the data model, then the plugin class, then the panel UI."

The AI agent should produce a complete, compilable project from this spec.
