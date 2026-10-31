# CANOPYGUARD UI repair audit

## Inspection findings

The existing application has a single JavaFX entry/application class, `CanopyGuardApp`, which constructs the shell, sidebar, overview, map controls, inspection panel, event list, and persistence interactions. There are no separate FXML views, controllers, view models, chart components, or dialog classes. The only stylesheet is `app-theme.css`; there is no icon bundle or second map stylesheet.

The primary `Stage` did not set a custom undecorated style, fullscreen mode, or custom stage buttons. JavaFX's default stage style is decorated, but the code did not explicitly lock in `StageStyle.DECORATED` or `setResizable(true)`. The launcher starts a JavaFX `Application`; native Windows title-bar buttons are expected and must be tested on the actual app window. Do not duplicate them in client content.

Seven sidebar buttons all replaced the same detail-panel text. Consequently Fire Intelligence, Communities, Habitat Protection, Resources, and Incident History were not separate module views. Several overview actions were functional, but navigation conveyed functionality that did not exist. There were no Reports or Settings destinations.

The global CSS provided only partial normal/hover button styling; pressed, keyboard focus, disabled, destructive, and compact button states were not designed. Spacing, typography, and colors were inline in application methods. There was no reusable component/style-token layer. The dashboard displayed seed counts and shelter stock but only the first five events and no report export or actual shelter reservation details.

MapCanvas renders real bundled GeoJSON geometry and fire cells on a JavaFX Canvas, supports pan/zoom and selection, and resizes via parent listeners. It lacked direct zoom/reset controls, a legend, actionable route rendering, and an easily readable layer hierarchy. It remains vector/data driven; no replacement image is introduced.

The close handler consumed the native window-close event and started shutdown without first persisting a final checkpoint. `stop()` then called `shutdownNow()` on the executors, racing queued persistence and potentially discarding accepted work. No closing feedback was shown. DatabaseService already has a bounded writer and drains queued JDBC operations on orderly `close()`.

The host has an NVIDIA GeForce RTX 3050 A Laptop GPU and Intel UHD Graphics. Runtime logs show JavaFX selected its Direct3D pipeline on Intel UHD; the RTX is not selected for this window. The app does not force a renderer or add CUDA/a GPU-specific dependency. No measured workload justifies a custom GPU path; the standard pipeline retains JavaFX's software fallback.

## Repair changes

1. Explicitly create a decorated, resizable native Stage with display-aware initial bounds and a 900×460 logical minimum suitable for scaled desktop displays.
2. Move shell/navigation construction into a separate JavaFX shell; make every sidebar destination render an actual state-backed module view.
3. Centralize reusable JavaFX button/panel/metric styles and CSS design tokens, including focus, pressed, disabled, warning, and success states.
4. Keep the coordinate-based map as the overview/fire/habitat centerpiece; add direct zoom controls, legend, selection, and readable scenario context. Reuse one map frame across modules and redraw only on real size changes to avoid repeated Canvas buffer recreation. Keep the Canvas unmanaged by its `StackPane` so resizing it from the frame bounds cannot feed its new preferred width back into parent layout; give the frame a bounded preferred width while allowing its `HBox` to grow into available space.
5. Connect community records, routes, shelter reservations, finite resources, missions, incident events, and CSV/HTML report exports to existing domain/persistence services. Do not add inert controls or synthetic live claims.
6. On native close, cancel new scheduled work, drain the simulation executor, finish accepted I/O, save a final versioned checkpoint, enqueue a database checkpoint record, drain SQLite, and then permit the second native close event to terminate the Stage. Show an in-window closing state; never wait on worker termination from the JavaFX thread.
7. Verify compilation/tests and launch the actual packaged JavaFX window. The rendered map and shell were inspected from desktop captures. The window was exercised at 1366×768 physical and at a 900×640 physical resize request on this 1920×1080 display, currently at 125% scaling (1536×864 logical). The native 900-logical-pixel minimum width clamps that second request to 1125 physical pixels. A separate 150% scaling configuration was not tested.

## Verification results

- `scripts/package-windows.ps1` completed successfully; all 20 JUnit tests passed and the Windows app-image and ZIP were produced.
- The Stage reports native caption, system-menu, minimize, maximize, and resizable-frame styles. Native minimize, maximize, restore, resizing, and close requests were exercised.
- UI Automation selected all ten destinations and verified each page's distinct content. Simulation start/pause feedback was verified in the Fire Intelligence view.
- Both idle and active-simulation close requests completed. During the active close, the recovery JSON and newest SQLite snapshot contained the same simulation tick; SQLite `integrity_check` returned `ok`.
- The packaged launch selected JavaFX's Direct3D pipeline on Intel UHD. No JavaFX CSS loop warnings appeared after the stylesheet repair.
- A later visual regression exposed a Canvas/layout feedback loop that let the geographic page's preferred width grow without bound. After making the Canvas unmanaged within its map frame, a fresh packaged launch showed the overview metrics, map zoom controls, and feature inspector within the window; Geographic Intelligence navigation rendered normally. Native maximize and restore were exercised again. A separate automated small-window resize probe was inconclusive because changing the probe thread's DPI-awareness context invalidated its reported coordinates; it is not counted as a verified resize result.

## Known feature boundaries

The existing domain does not implement species-aware movement, wildlife incident case management, configurable official evacuation approvals, actual observation-provider integrations, or a mature chart/reporting suite. The UI must state these boundaries clearly and must not present placeholder numbers or controls as working capabilities.
