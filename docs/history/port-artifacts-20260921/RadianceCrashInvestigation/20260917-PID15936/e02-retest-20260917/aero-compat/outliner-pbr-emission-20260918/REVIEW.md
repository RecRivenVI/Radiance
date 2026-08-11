# Outliner ordinary PBR emission — 2026-09-18
User requested normal PBR self emission on the current corrected Outliner, without special rendering treatment.
Changed RadianceOutlinerContext storage defaultAlbedoEmission from 0 to 1 for both original strokes and textured faces. Existing PBR vertex albedoEmission flows through the standard material packing and hit shaders. Ordinary world PT routing and coverage alpha preserved. Auxiliary initialization fix retained. No native/shader changes.
Build: distributedJar; visual acceptance pending manual launch. Diagnostics retained, expected submitted maxEmission=1.000.

Build successful; deployed to Radiance Test, SHA256 verified: 25BB74A3BF540E79BA02639740031F28D64D373A5CD46A1CF8D60ED8A7D98F6F. Game not launched by agent. Uncommitted.
