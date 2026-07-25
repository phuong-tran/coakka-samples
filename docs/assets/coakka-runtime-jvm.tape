Output docs/assets/coakka-runtime-jvm.mp4

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
Type "clear"
Enter
Show

Sleep 800ms
Type "bash run.sh runtime jvm basic 2>&1 | grep -E 'coakka_runtime_response|BUILD SUCCESSFUL'"
Enter

Sleep 18s
