# C# Logger Pressure

Installs published `CoAkka.Logger==1.2.3` into a temporary `net8.0` project,
starts the logger with queue capacity `2`, verifies later writes are rejected,
then drains the accepted records and prints pressure counters.

```sh
bash run.sh logger csharp pressure
```
