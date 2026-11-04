
# Overview

This folder contains 3D printing resources for the project: source models, the BambuStudio project file, and prepared print jobs.

## Contents

- File: [res/3dprinting/traffic_simulation.3mf](res/3dprinting/traffic_simulation.3mf) — 3MF project file for BambuStudio containing multiple print plates with all the objects configured for printing.
- Folder: [res/3dprinting/models](res/3dprinting/models) — Source model files in STL format of bridges for level 1 and 2 used for slicing and editing. These must be altered, if there need to be changes made to the models themselfes.
- Folder: [res/3dprinting/gcode](res/3dprinting/gcode) — Exported print jobs with correct settings for printing and color already applied. Files here contain the print and color settings are ready to be sent to a printer.
The streets are printed in white with a red stripe on top. Bridges of level 1 are printed in blue, and bridges of level 2 are blue on the bottom and green on the top part.


Model sources in `models`:

- `bridge_level1.stl` — Level 1 bridge model (STL) used in the traffic simulation.
- `bridge_level2.stl` — Level 2 bridge model (STL) used in the traffic simulation.


G-code files for printing in `gcode`:

- `traffic_simulation_alle.gcode.3mf` — Combined print job including all parts of the simulation.
- `traffic_simulation_1x_lang.gcode.3mf` — Slicer-exported print job for one long street.
- `traffic_simulation_1x_lang_4x_kurz.gcode.3mf` — Slicer-exported print job for one long and four short streets.
- `traffic_simulation_2x_mittel.gcode.3mf` — Slicer-exported print job for two streets with medium length.
- `traffic_simulation_2x_mittel_2x_kurz.gcode.3mf` — Slicer-exported print job for two street of medium length and two short ones.
- `traffic_simulation_2x_Bruecke_E1_2x_Bruecke_E2_support.gcode.3mf` — Slicer-exported print job for two bridges for level 1 and two bridges for level 2. This print job is configured to use support structures for the arches of the bridges and the infill in increased to make the bridges more robust.
