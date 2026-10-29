Output docs/assets/coakka-runtime-bun.mp4

Require bash
Require bun

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
Type 'export PATH="$HOME/.bun/bin:$PATH"'
Enter
Type "export COAKKA_PUBLISH_ROOT=/tmp/coakka-public-only-bun-video"
Enter
Type "rm -rf /tmp/coakka-public-only-bun-video"
Enter
Type "clear"
Enter
Show

Sleep 800ms
Type "bash run.sh runtime bun basic"
Enter

Sleep 10s
