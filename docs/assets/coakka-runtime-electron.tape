Output docs/assets/coakka-runtime-electron.mp4

Require bash
Require node
Require npm

Set Shell "bash"
Set FontSize 20
Set FontFamily "Menlo"
Set Width 1280
Set Height 720
Set Padding 24
Set Framerate 30
Set TypingSpeed 90ms
Set Theme "Catppuccin Mocha"
Set WindowBar "Colorful"
Set WindowBarSize 32

Hide
Type "cd /Users/phuongtran/workingDir/coakka-samples"
Enter
Type "export COAKKA_PUBLISH_ROOT=/tmp/coakka-public-only-electron-video"
Enter
Type "rm -rf /tmp/coakka-public-only-electron-video"
Enter
Type "clear"
Enter
Show

Sleep 800ms
Type "bash run.sh runtime electron basic 2>&1 | grep -E 'downloading public artifact|coakka_electron_runtime_response'"
Enter

Sleep 14s
