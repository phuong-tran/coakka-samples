# Mojo Runtime Basic

This recipe uses the admitted **unpublished 3.0.0-source** archive and exact f6
Core. It does not substitute a working source checkout or an old public archive.
Mojo remains an executable over the package's C smoke boundary, not a stable
high-level Mojo application API. The old sample-local transport copy is removed.

Choose a fresh absolute work path with an existing parent. On the development
Mac use external APFS storage. Select the trusted archive whose exact hash is
recorded in coakka-runtime/artifacts/runtime-mojo-candidate.json.

~~~sh
export COAKKA_MOJO_ARCHIVE=/absolute/coakka-runtime-mojo-3.0.0-source.tar.gz
export COAKKA_SAMPLE_WORK_ROOT=/absolute/existing-parent/fresh-mojo-basic
bash run.sh coakka-runtime mojo basic
~~~

The runner refuses archive changes, an existing work path and native-library
overrides. It builds outside the repository, checks startup/request/reply and
route-miss behavior, then closes the owner. Build/package staging is removed;
installed outputs and the admission receipt remain under the chosen work path.

Normal configuration leaves CPU placement, queue capacity, connection strategy
and monitoring to Core. This sample does not establish a new benchmark,
native-image support or additional language/compiler platforms.
