# Inline F3 FPS and PT section counts — 2026-09-18

User requests replacing only the vanilla FPS number while FG is enabled, no new lines or text, and checking availability of the C counter.

Changes:
- Replace reads of Minecraft.fpsString in DebugScreenOverlay.getGameInformation using ModifyExpressionValue. Only its leading numeric FPS is replaced with the measured real+generated application presentation rate, when FG option is enabled and a nonzero sample exists. Disabled FG or an unavailable first sample keeps vanilla text. All suffixes and other debug values remain untouched. Remove previous extra line and its now-unused translations.
- Replace only the `C: numerator/denominator` prefix produced by LevelRenderer.getSectionStatistics in F3. No changes to LevelRenderer gameplay/rendering APIs or line count.
- New native getter uses the existing chunk mutex and counts sections with non-null BLAS in the native chunk vector; denominator is that vector's allocated slot count. This is the same geometry eligibility as WorldPrepareContext's chunk instance loop, including transformed/external sections stored there. It does not count entities or Ponder entity meshes. No GPU readback or new graphics synchronization.
- Semantic distinction: vanilla C counts visible nonempty 16x16x16 sections / ViewArea slots. Radiance bypasses visibleSections; PT retains off-screen sections for secondary rays. C now means PT-ready sections / native section slots, not camera visibility. Existing Java countCompiledSections helper is unused and was not repurposed as GPU-ready data.
- Both full and reduced F3 branches access the intercepted fields/methods. Local Minecraft source confirmed both targets. JNI initialized guard preserves original value if native backend/world is unavailable.

Before files retained alongside this report. No game/config changes or commits. Full build passed in 1m04s. Generated JNI declaration verified. Packaged mixin contains inline FPS/C hooks and no old extra-line hook; both obsolete translations absent. Embedded native DLL hash matches built DLL and contains new JNI symbol. Both authorized instances deployed with identical JAR hashes (deployment.json); previous artifacts backed up. User performs actual F3 acceptance; game not launched/restarted.

JAR SHA256 9217FC8292A5C03F6F2CDC51DC3B9E74657F9B3BEE5307002DBC2B4D23FBE2B2; DLL SHA256 352B47B6DF2981F607FDD9D0AE311E62BE5DF63FA5E14D2CDF994065D802EEB5.
