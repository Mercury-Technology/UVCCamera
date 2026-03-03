# UVCCamera

A USB Video Class (UVC) camera library for Android and a plugin for Flutter.

This project is a fork of the original [Alexey Pelykh](https://alexey-pelykh.com).

## Extensions

#### OSD Timestamp Overlay

The Flutter plugin supports burning a **date/time timestamp** directly onto captured photos and recorded videos via
an On-Screen Display (OSD) overlay.

**Taking a picture with a timestamp:**

```dart
final XFile photo = await controller.takePicture(timestamp: true);
```

**Recording a video with a timestamp:**

```dart
await controller.startVideoRecording(videoRecordingMode, timestamp: true);
```

When `timestamp` is `true`:

- **Photos** – the current date and time are rendered onto the JPEG image in the bottom-right corner before it is saved.
- **Videos** – a dedicated OpenGL render thread (`GLRenderThread`) intercepts the camera frames, composites a
  live-updating date/time overlay using OpenGL ES 2.0, and feeds the result to the `MediaRecorder` encoder.

The overlay appearance adapts to the camera resolution: the font size scales linearly between 8 px (at 384 p height)
and 32 px (at 720 p height), with a semi-transparent black background box in the bottom-right corner of the frame.
