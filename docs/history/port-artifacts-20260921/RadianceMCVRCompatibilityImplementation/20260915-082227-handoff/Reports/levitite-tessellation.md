# Levitite tessellation integration next scope

Source facts newly checked by root:
- AeroRenderTypes.java registers levitite and levititeGhosts as custom BLOCK/QUADS layers, fixed AFTER_BLOCK_ENTITIES and AFTER_WEATHER buffers, VeilRenderBridge.patchState(4).
- Program assets/aeronautics/pinwheel/shaders/program/levitite/levitite.json uses vertex, fragment, tesselation_control/evaluation; sources .vsh/.fsh/.tcsh/.tesh exist. DiffuseDepthSampler is minecraft:main:depth.
- Aero SodiumWorldRendererMixin.drawChunkLayer initializes world offsets/time and LevititeShaderManager; a Radiance consumer needs its own equivalent world/sublevel state because original Sodium path is replaced.
- Existing native VkDevice features did not enable tessellation; DynamicGraphicsPipelineBuilder had no pTessellationState.

Root groundwork now written (pending build):
- vk::Device enables tessellationShader only if physically supported, exposes hasTessellation().
- DynamicGraphicsPipelineBuilder.definePatchControlPoints(count) selects PATCH_LIST and supplies pTessellationState after checking feature and nonzero patch size.
- Veil public tessellation capability remains false until complete shader/patch draw path is installed; groundwork does not count as Levitite implementation.

Next implementation must support four-stage ShaderProgram/ShaderRegistry/native stage registration, 4-control-point QUADS index contract (not triangle-expanded6), last-stage GL→VK clip conversion, actual gl_FragCoord target-height contract, required uniforms/depth source and world/sublevel render routing. Need fixed source shader compilation and non-window GPU patch test, then A/B user visual acceptance. Do not re-investigate these already-read callers or omit the consumer because capability false.
