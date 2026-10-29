# Zig Runtime Basic

This recipe uses the admitted **unpublished 3.0.0 source package** and Core
`1522b4a9f30da1039ef8c7d198bbecfc749a2456`. It does not substitute a working source checkout or an old public archive.
The Zig app uses the source package's runtime owner. Requests and terminal
matching stay in Core; the application does not parse or match responses itself.

Choose a fresh absolute work path with an existing parent. On the development
Mac use external APFS storage. Select the trusted archive whose exact hash is
recorded in coakka-runtime/artifacts/runtime-zig-candidate.json.

~~~sh
export COAKKA_ZIG_ARCHIVE=/absolute/coakka-runtime-zig-3.0.0-source.tar.gz
export COAKKA_SAMPLE_WORK_ROOT=/absolute/existing-parent/fresh-zig-basic
export COAKKA_ZIG_GLOBAL_CACHE_ROOT=/absolute/existing-zig-cache
bash run.sh coakka-runtime zig basic
~~~

The runner refuses archive changes, an existing work path and native-library
overrides. It builds outside the repository, checks startup/request/reply and
route-miss behavior, then closes the owner. Build/package staging is removed;
installed outputs and the admission receipt remain under the chosen work path.

Normal configuration leaves CPU placement, queue capacity, connection strategy
and monitoring to Core. This sample does not establish a new benchmark,
native-image support or additional language/compiler platforms.
