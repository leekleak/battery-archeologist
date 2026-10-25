# M3 Expressive UI task

Goal: keep the app's layout language close to Material 3 Expressive — tonal
surfaces, large shapes, expressive type, and a floating toolbar — while using
**stock Material colors**. There is no custom brand palette and no dynamic
Material You color; Haze is used only to blur the content behind the top bar.

The Navigation 3 host puts the reports home at the root and pushes the four-tab
report viewer on top. Capture, import, saved reports, and reset live on the
home; the viewer owns the tab bar.

Traffic Light (`~/Documents/GitHub/traffic-light`, GPL-3.0, same author) is the
reference for the expressive shell and, in particular, for the Haze top bar.
Reusing and adapting that code is allowed with attribution.

## Done

- [x] Navigation 3 host: reports home root, report viewer pushed on top, and
      the permission screen on demand.
- [x] Bottom `HorizontalFloatingToolbar` navigation in the viewer with icon +
      selected label across the four tabs.
- [x] Custom vector icons for Overview, Timeline, System, Apps.
- [x] Tonal surface cards and a bottom-sheet app detail treatment.
- [x] Test tags on all toolbar tabs for `android input`/UIAutomator.
- [x] Report viewer split into Overview, Timeline, System, Apps with their own
      ViewModel state; the reports home owns capture, import, saved reports,
      raw/share, and reset.
- [x] Permission illustration: a `MaterialShapes.Sunny` hero with a
      state-specific icon.
- [x] Stock Material `lightColorScheme()`/`darkColorScheme()`, default
      `Typography()` and `Shapes()`; the custom palette was removed.

## Required (done)

- [x] **Haze blur on the top bar must remain.** Port Traffic Light's
      `HazeScaffold` + `PageTitle` pattern into `ui/components/AppScaffold.kt`
      (`hazeSource` on the scrolling content column, `hazeBlur` with
      `HazeInput.Sources`, `HazeMaterials.ultraThin()`, and a vertical
      progressive gradient 1f → 0f on a title bar with no opaque background).
      Details and the previous artifact are in `docs/session-handoff.md`.
- [x] Verify the title bar in light and dark mode with a cropped screenshot
      before accepting the port.

## Next

- [ ] Use `MaterialShapes` for hero cards and the capture action.
- [ ] Animate tab selection, card expansion, capture progress, and home/viewer
      navigation.
- [ ] Audit contrast in light and dark themes after each change.
- [ ] Take 1280×720 screenshots of the reports home and every viewer tab and
      compare against Traffic Light spacing/typography.

## Constraints

- Keep data-dense screens readable; expressive styling must not reduce contrast
  or timeline legibility.
- Keep test tags stable so the agentic scripts and smoke tests continue to work.
- Traffic Light is GPL-3.0 and may be reused with attribution.
