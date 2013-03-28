[Android Ogg Stream Player](http://radzio.github.com/AndroidOggStreamPlayer/)
=========================

## Introduction ##

This library allows you to play Ogg Live Streams on any Android device. It is based on JOrbis  so everything is written in Java code.

## Usage ##

```java
        OggStreamPlayer player = new OggStreamPlayer("http://78.28.48.14:8000/stream.ogg");
        player.start();
```

## Goal ##

Implement API similar to the native Android audio player.