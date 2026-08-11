# Independent Astra Extra High readback review

Read-only source review 09:33-09:39 found six concrete issues: sampled image layouts; mid-frame dirty descriptor refresh; retired UBO uniform bytes; main aliases after custom FBO writes; per-read command buffers under global pool; nested double abort.

Root implemented all six fixes. Independent read-only recheck 09:42:58-09:44:09 confirmed the normal-path triggers closed, no new confirmed P1/P2. This is source review, not runtime GPU acceptance.

Follow-up pending concerns were also addressed in code (not yet fault-injection tested): reject appendOverlayPostUniform after frameSubmitted; on one-shot readback fence failure attempt queue idle and retain command/pool/buffer/fence group if completion remains unknown. Renderer::close already calls waitDeviceIdle before resource teardown. Generic CommandBuffer destructor was intentionally left unchanged; one-shot operations own short-lived pools.

Latest changes must be included in next unified core build. Historical passing core before these edits must not be called final.

Integration build caught only const CPU vector pointer vs legacy HostVisibleBuffer::uploadToBuffer(void*) overload at buffers.cpp:85; root corrected reference mutability (no data write change); next increment pending.
