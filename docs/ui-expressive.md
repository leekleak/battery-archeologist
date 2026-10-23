# M3 Expressive UI task

Goal: move the app's visual language toward Material 3 Expressive, following
Traffic Light's use of tonal surfaces, large shapes, expressive type, and a
floating toolbar.

## Done (first pass)

- [x] Expressive theme shapes (`10–36 dp` rounded corners).
- [x] Heavier headline/title weights and tighter headline spacing.
- [x] Soft tonal gradient behind the app.
- [x] Permission hero uses a Material shape (`Cookie12Sided`) and gradient.
- [x] Bottom `HorizontalFloatingToolbar` navigation with icon + selected label.
- [x] Custom vector icons for Overview, Timeline, System, Apps, Reports.
- [x] Google Sans Flex typography from Traffic Light.
- [x] Tonal surface cards and a bottom-sheet app detail treatment.
- [x] Test tags on all toolbar tabs for `android input`/UIAutomator.

## Next

- [ ] Adopt Haze/HazeScaffold with a blurred tonal top bar and scrolling title,
      as in Traffic Light.
- [ ] Add an emphasized display style for battery metrics using the variable
      font axes.
- [ ] Use `MaterialShapes` for hero cards and the capture action, not only the
      permission glyph.
- [ ] Animate tab selection, card expansion, and capture progress.
- [ ] Add an expressive permission illustration/rotation, like Traffic Light's
      overview hero.
- [ ] Audit contrast in light/dynamic themes after each change.
- [ ] Take 1280×720 screenshots of every tab and compare against Traffic Light
      spacing/typography.

## Constraints

- Keep data-dense screens readable; expressive styling must not reduce contrast
  or timeline legibility.
- Keep test tags stable so the agentic scripts and smoke tests continue to work.
- Traffic Light is GPL-3.0 and may be reused with attribution.
