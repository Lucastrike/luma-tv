# Luma 2.5.4 — November 3, 2026

## Now Playing preview and position

The production card showed a real YouTube title with a generic icon and `0:00`. Android media sessions in
the QA package reproduced two defects:

- After a pause at `0:45`, a closing `STOPPED` update at zero replaced the saved position with `0:00`.
- Artwork supplied as a bitmap disappeared after discarding static state and reloading local data.

Playback memory now retains the last valid position across closing updates at zero and unknown positions.
A real seek to the start, published as paused or playing at zero, remains valid. Partial updates for the
same video preserve its known duration and artwork. Both live and Resume cards use this information.
When no position is known, the widget does not invent `0:00`.

Artwork is saved to one private file with atomic background writes, limited to 512 pixels on its longest
side. The file identifies the video it belongs to. A different media identifier prevents another video
with the same title from inheriting the preview or position. Artwork loaded from a URI is cached too.

No permission is added. Resume still opens the source app; it does not add a seek command to YouTube.
Data already lost by the previous version cannot be reconstructed from the saved title.

## Verification

Both main regressions failed before the change and passed afterwards on the reference Xiaomi. Additional
cases cover partial metadata, deliberate seeks to zero, and videos with the same title. Reloading static
state checks persistence in QA; it does not by itself establish real YouTube playback behavior.

All **25 checks** passed. The local log is `.local/diagnostics/now-playing-final-regression.txt`. An initial
run failed while checking off-screen metadata; the isolated case and a full repeat passed. The check now
reads state on the main thread, where Android delivers callbacks. QA is removed afterwards and Luma is
restored as Home.

## Real YouTube check and artwork limit

Version 2.5.4 (`versionCode=31`) was installed with `install -r`, using the same certificate as 2.5.3 without
deleting data. The default Home remained `dev.luma.tv/.MainActivity`.

A real session published a pause at 8,229 ms; Resume showed `0:08` after returning Home. This establishes
retention of Android's published position; YouTube can publish advertisement time under the video's title.
After the advertisement ended, the content was paused at **404,731 ms (6:44)**. Returning Home showed
**6:44**, the progress bar, and remaining time. The TV was left at Home with playback paused.

The content still had no thumbnail: YouTube published `ART=null`, no alternate bitmap, artwork URI,
`MEDIA_ID`, or `MEDIA_URI`, and no YouTube rows in `watch_next_program` or `preview_program`. The fix retains
images an app supplies; it cannot obtain a preview from those empty fields.

Evidence is retained locally in `.local/diagnostics/now-playing-content-after.png`,
`now-playing-youtube-content-paused.txt`, and `now-playing-source-probe.txt` in the same directory. A
temporary instrumented helper used our signing key and Luma's existing authorization. It did not enable
another package's notification access or read notification payloads. The helper `dev.luma.probe` was
removed afterwards.

Unknown positions follow
[`PlaybackState.PLAYBACK_POSITION_UNKNOWN`](https://developer.android.com/reference/android/media/session/PlaybackState#PLAYBACK_POSITION_UNKNOWN).
Artwork writes use [`AtomicFile`](https://developer.android.com/reference/android/util/AtomicFile).
