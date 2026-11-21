# Third-party UI code notice

This project now directly adapts UI code from the following projects so the
MiuiX-style interface, floating bottom bar and page navigation match the
reference template:

- **KernelSU-Style-UI-Kit** — https://github.com/chenaizhang/KernelSU-Style-UI-Kit
  - License: GNU GPL v3.0 (see `THIRD_PARTY_LICENSES/KernelSU-Style-UI-Kit-GPL-3.0.txt`)
  - Files adapted:
    - `app/src/main/java/com/admi/mifi/ui/components/template/FloatingBottomBar.kt`
    - `app/src/main/java/com/admi/mifi/ui/components/template/DampedDragAnimation.kt`
    - `app/src/main/java/com/admi/mifi/ui/components/template/InteractiveHighlight.kt`
    - `app/src/main/java/com/admi/mifi/ui/components/template/DragGestureInspector.kt`
    - `app/src/main/java/com/admi/mifi/ui/components/BottomBar.kt`
    - `app/src/main/java/com/admi/mifi/MifiApp.kt` (HorizontalPager integration)

- **compose-miuix-ui / Miuix** — https://github.com/compose-miuix-ui/miuix
  - `PagerNavigationSpringSpec` and `springAnimateToPage` behavior in
    `app/src/main/java/com/admi/mifi/ui/components/PagerSpring.kt`
  - License: Apache-2.0 (see `THIRD_PARTY_LICENSES/compose-miuix-ui-Apache-2.0.txt`)

- **io.github.kyant0:backdrop** — Apache-2.0, used for the liquid-glass
  backdrop primitives (`drawBackdrop`, `blur`, `lens`, `vibrancy`, highlights
  and inner shadows).

Because GPL-3.0 code is included, the combined work must be distributed under
GPL-3.0 terms and the corresponding source must be made available.
