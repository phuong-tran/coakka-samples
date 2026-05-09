# C# Logger Pressure

Starts the C# logger with queue capacity `2`, verifies later writes are rejected,
then drains the accepted records and prints pressure counters.

```sh
bash run.sh logger csharp pressure
```
