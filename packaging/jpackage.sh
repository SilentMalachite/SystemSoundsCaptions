#!/usr/bin/env bash
# Packages the jar with its own Java runtime, so users can start the app without installing Java.
#   macOS:   target/dist/SystemSoundsCaptions-<version>-mac-<arm64|x64>.dmg
#   Windows: target/dist/SystemSoundsCaptions-<version>-windows-x64.zip
# Run after `mvn package`, with the JDK to bundle (25 or later) on the PATH. jpackage builds only
# for the OS it runs on. The app is not signed.
set -euo pipefail

cd "$(dirname "$0")/.."
version=${1:-$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version)}
name="System Sounds Captions"
case "$(uname -m)" in
    arm64 | aarch64) arch=arm64 ;;
    *) arch=x64 ;;
esac

input=target/jpackage/input
image=target/jpackage/image
dist=target/dist
rm -rf target/jpackage "$dist"
mkdir -p "$input" "$image" "$dist"
cp target/SystemSoundsCaptions.jar "$input/"

# jdeps lists the first four; jdk.charsets has the Windows code pages VoskNative encodes
# model paths in (MS932 for Japanese), jdk.accessibility lets screen readers read the window
common=(
    --name "$name"
    --app-version "$version"
    --vendor SilentMalachite
    --input "$input"
    --main-jar SystemSoundsCaptions.jar
    --add-modules java.base,java.desktop,java.net.http,java.prefs,jdk.charsets,jdk.accessibility
    --jlink-options "--strip-debug --no-man-pages --no-header-files --strip-native-commands --compress=zip-6"
    # the jar manifest enables native access only for `java -jar`
    --java-options --enable-native-access=ALL-UNNAMED
)

case "$(uname -s)" in
    Darwin)
        jpackage --type app-image "${common[@]}" \
            --icon packaging/icons/SystemSoundsCaptions.icns \
            --mac-package-identifier io.github.silentmalachite.systemsoundscaptions \
            --dest "$image"
        app="$image/$name.app"
        # shown when macOS asks for the microphone; the default text is English
        /usr/libexec/PlistBuddy -c "Set :NSMicrophoneUsageDescription マイクやシステム音声の音を字幕にするために使います。" \
            "$app/Contents/Info.plist"
        codesign --force --deep --sign - "$app" # editing Info.plist broke the ad-hoc signature
        jpackage --type dmg --name "$name" --app-version "$version" --app-image "$app" --dest "$image"
        mv "$image/$name-$version.dmg" "$dist/SystemSoundsCaptions-$version-mac-$arch.dmg"
        ;;
    MINGW* | MSYS* | CYGWIN*)
        jpackage --type app-image "${common[@]}" \
            --icon packaging/icons/SystemSoundsCaptions.ico \
            --dest "$image"
        (cd "$image" && powershell.exe -NoProfile -Command \
            "Compress-Archive -Path '$name' -DestinationPath 'SystemSoundsCaptions.zip'")
        mv "$image/SystemSoundsCaptions.zip" "$dist/SystemSoundsCaptions-$version-windows-$arch.zip"
        ;;
    *)
        echo "jpackage.sh supports macOS and Windows; use the jar elsewhere" >&2
        exit 1
        ;;
esac

ls -l "$dist"
