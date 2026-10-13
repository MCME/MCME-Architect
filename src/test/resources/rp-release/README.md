# Release outputs for RpReleaseReportTest

Each file is what MCME-Architect reads from one release: the script's standard output and error in one stream, as
`RpReleaseUtil` starts it. They were captured on 2026-10-12 from real releases by ResourcePackScripts' launcher,
release scripts and pipeline wrapper (the branch that adds the `release note:` line, on its way to `development`), run
under dash in a throwaway automation folder, with the stand-in 7-Zip, gh and converter of ResourcePackScripts' tests.
Run ids, commits and times differ from one capture to the next: the tests check only what doesn't.

The script that captured them is kept outside both repositories.

| File | Release | Exit code |
|---|---|---|
| `sodium-wrapper.txt` | Human v4.1.3, a Sodium pack, through the wrapper, with 5 converter warnings | 0 |
| `general-no-wrapper.txt` | Rohan v1.0, a vanilla-only pack, without the wrapper, as on production | 0 |
| `dynmap-wrapper.txt` | Gondor v1.0, the dynmap pack, through the wrapper | 0 |
| `pull-stop-wrapper.txt` | Rohan v1.0, with the pack's remote pointing at a closed port, so git can't connect, through the wrapper | 1 |
| `7zip-failure.txt` | Rohan v1.0, with 7-Zip failing on `Rohan-Footprints.zip`, without the wrapper | 1 |
| `busy-wrapper.txt` | Rohan v1.1, while another process holds the release lock, with a Human release written in as the running one, through the wrapper | 75 |
| `busy-launcher.txt` | Rohan v1.1, while another process holds the release lock, with a Human release written in as the running one, without the wrapper | 75 |
| `launcher-edited.txt` | Rohan v1.0, with 4 files added to the server's ResourcePackScripts checkout | 3 |
| `checks-failed-wrapper.txt` | Human v4.1.3, with an objmc error line, through the wrapper | 0 |

When ResourcePackScripts changes a line MCME-Architect reads (its `docs/release-pipeline.md`, "What MCME-Architect
reads"), capture them again.
