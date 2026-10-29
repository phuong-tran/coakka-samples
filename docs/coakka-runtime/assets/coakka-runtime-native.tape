Output docs/assets/coakka-runtime-native.mp4

Require bash

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
Type "cd /path/to/coakka-samples"
Enter
Type 'export COAKKA_PUBLISH_ROOT="/tmp/coakka-public-only-native-video-$(date +%s)"'
Enter
Type "clear"
Enter
Show

Sleep 800ms
Type "bash run.sh runtime native basic 2>&1 | grep -E 'downloading public artifact|coakka_runtime_'"
Enter

Sleep 18s
