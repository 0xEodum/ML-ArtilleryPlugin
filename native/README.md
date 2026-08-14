# Native solver (experiment)

A C++ port of the inverse ballistics solver, reachable through JNI. It exists to
answer one question — is the arithmetic worth taking out of the JVM — and the
answer is recorded in section 4 of the top-level README: no.

The plugin never depends on it. `native/build/` is gitignored, so a fresh clone
produces a jar with no native code in it at all. Building the libraries is an
explicit opt-in:

```bash
./native/build.sh            # both targets, skipping absent toolchains
./native/build.sh linux      # libairtillery_ballistics.so   (g++)
./native/build.sh windows    # airtillery_ballistics.dll     (mingw-w64)
```

`pom.xml` picks up whatever lands in `native/build/` and packages it under
`natives/` in the jar, where `NativeSolver` extracts and loads it.

To reproduce the measurement:

```bash
mvn -q test-compile
java -cp target/classes:target/test-classes \
     org.yudev.airtillery.ballistics.SolverBenchmark
```

`NativeSolverParityTest` asserts the two implementations agree bit for bit. It
skips itself when no library is present, so `mvn test` stays green without a C++
toolchain. The C++ is compiled with `-ffp-contract=off` because Java forbids
fused multiply-add contraction; without that flag the results drift in the last
bits and parity fails for a reason that has nothing to do with the algorithm.
