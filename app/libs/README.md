Place the Android FFmpegKitNext AAR here as:

```text
ffmpeg-kit-next.aar
```

Debug builds can compile without the artifact so the UI and tests remain usable. Release packaging runs
`verifyFfmpegKitNextAar` and fails with a clear message until the AAR is present.
