# AIrtillery

Artillery for Bukkit/Spigot servers. A player places an artillery station, writes
target coordinates and a descent angle on renamed paper, pays for the ammunition
and fires a volley that arrives on the requested point.

Aiming is **closed-form arithmetic**, computed inside the plugin. Projectile
motion in Minecraft is a first-order linear recurrence over ticks, so it has an
exact solution: there is no simulation loop, no trained model, no external
process and nothing to wait on. A firing solution costs about 6 µs, so a
100-round volley is solved and launched in the same tick it was ordered.

- One jar, no runtime dependencies (`spigot-api` is `provided`).
- Exact for arrows, tridents, potions and TNT, including TNT's different tick order.
- Physics constants live in `config.yml`, so a game update is a config edit.
- Verified against an independent tick-by-tick simulator: 47 tests, no server required.

---

## Contents

- [Quick start](#quick-start)
- [The artillery station](#the-artillery-station)
- [Configuration](#configuration)
- [Ballistics](#ballistics)
  - [The per-tick update](#the-per-tick-update)
  - [The closed form](#the-closed-form)
  - [What follows from it](#what-follows-from-it)
  - [The inverse problems](#the-inverse-problems)
  - [Impact angle and tick quantisation](#impact-angle-and-tick-quantisation)
  - [Worked numbers](#worked-numbers)
- [Accuracy](#accuracy)
- [Performance](#performance)
- [The native solver experiment](#the-native-solver-experiment)
- [Project layout](#project-layout)
- [Building](#building)

---

## Quick start

```bash
mvn package                  # target/AIrtillery-2.0.0.jar
```

Drop the jar into `plugins/` and restart. Then, in game:

```
/artillery give [player]     # hands out the station block (permission: artillery.give)
```

Place the block, right-click it, fill in the four inputs and press fire. The
block above the station is the launch point.

| Permission | Default | Grants |
|---|---|---|
| `artillery.give` | op | `/artillery give` |
| `artillery.use` | everyone | operating a placed station |

The command surface is deliberately one verb wide: everything the artillery can
be told is set on the block itself.

---

## The artillery station

The station is a shulker box tagged in its `PersistentDataContainer`. Placing it
creates a station; right-clicking opens the station window instead of the
shulker's own inventory.

### The window

Six rows of nine — the size of a double chest. Every slot that accepts something
has its label in the slot directly above it, and no two interactive slots touch,
so a mis-aimed click lands on inert glass rather than on the wrong control. Both
invariants are asserted in `StationTest`.

```
 row 0    .  .  .  .  T  .  .  .  .      T  ammunition selector
 row 1    .  .  L  .  L  .  L  .  .      L  label
 row 2    .  .  C  .  A  .  N  .  .      C  coordinates   "X Y Z"
 row 3    .  .  .  .  L  .  .  .  .      A  impact angle  "α"
 row 4    .  .  .  .  $  .  .  .  .      N  packet count  "N"
 row 5    .  .  .  F  .  R  .  .  .      $  payment
                                         F  fire    R  reset
```

### Ammunition

Click the selector to cycle. The item shows the type in its own colour and the
price in its lore. Switching ammunition returns any balance held in the old
currency, since gold and diamonds are not interchangeable.

| Ammunition | Pack | Price |
|---|---|---|
| Arrows | 10 | 1 gold ingot |
| Flaming arrows | 10 | 1 gold ingot |
| TNT | 5 | 1 diamond |
| Tridents | 1 | 1 diamond |

Potions are deliberately absent from the selector: they carry an effect, a
duration and an amplifier, which need a configuration surface of their own
rather than one toggle. The ballistics layer already supports them for when that
surface exists.

### Inputs

Each input takes a sheet of paper renamed in an anvil:

| Slot | Format | Example |
|---|---|---|
| Coordinates | three numbers, `X Y Z` (commas accepted as decimal points) | `120 64 -350` |
| Impact angle | one number, strictly between 0 and 90 degrees | `75` |
| Packet count | one integer, 1 to the configured ceiling | `10` |

The impact angle is the **descent** angle at the target, not the launch
elevation — usually what you actually care about when dropping rounds into a
courtyard or over a wall. 0 is excluded because a projectile is never travelling
level when it arrives, and 90 because a perfectly vertical descent needs
infinite elevation. The achievable range is narrower still and depends on the
target; the solver reports the real bounds when it cannot meet a request:

```
Залп не выполнен: impact angle 80.0 deg is outside the achievable 31.4-78.2 deg for this target
Для этой цели доступны углы падения от 31.4° до 78.2°
```

Each paper is validated the moment it is placed and refused with a reason rather
than accepted and mishandled later. A paper already in a slot is taken back by
clicking it, which also clears the value it stood for.

The packet count counts packs, not rounds: 10 packs of arrows is 100 arrows for
10 gold ingots.

### Payment, fire and reset

**Payment** absorbs matching items into a running balance, which is what lets a
price exceed one stack. A wrong item is left on the cursor untouched, and so are
renamed or enchanted ones, so nothing valuable disappears into a payment slot by
accident. Overpayment stays credited and comes back through reset.

**Fire** is a redstone block until the order is complete, then an emerald block;
its lore lists whatever is still missing. Firing charges the balance only after
the shot is known to be solvable, so a refused order never costs anything. Aim
points that have no solution are skipped and reported rather than fired blindly.

**Reset** returns the balance.

### State, and not losing player property

The block is the source of truth, not the open window: state lives in the
shulker box's `PersistentDataContainer`, so closing the window loses nothing and
two players see the same balance. What is stored is the *text* on each paper
rather than the paper item — a renamed sheet has no other state worth keeping,
and storing strings means the stored value and its validation message can never
disagree.

Anything a player put in comes back out:

- **Breaking the station** drops the block, the balance and the input papers.
  Vanilla drops are suppressed first, because a shulker box carries its
  block-entity data into the dropped item and would otherwise restore a station
  with a balance nobody paid for.
- **Explosions** do not raise a break event, so a station caught in one would
  take the balance with it. Since TNT artillery makes that a realistic way to
  lose money, stations are removed from explosion block lists instead.
- **Every click in the window is cancelled and then acted on by hand.** Letting
  vanilla move the items and correcting afterwards is how duplication bugs
  happen: shift-click, hotbar swap, double-click gather and drag all move stacks
  in ways that are awkward to undo once they have happened.

---

## Configuration

`config.yml`, written on first start:

```yaml
max-launch-speed: 12.0       # largest launch speed the solver will use, blocks/tick
max-flight-ticks: 600        # longest flight it will fire (20 ticks = 1 second)

max-packs-per-volley: 64     # ceiling on packs per order
impact-radius: 3.0           # scatter radius around the requested point
fire-mode: RAIN              # RAIN staggers the volley, BURST fires it in one tick
rain-spacing-ticks: 3        # ticks between rounds in RAIN mode
debug-mode: false            # logs the solution behind each volley

physics:                     # per-type constants; defaults match the game
  arrow:
    gravity: 0.05
    drag: 0.01
    gravity-before-move: false
  trident: ...                # same as arrow
  potion: ...                 # same as arrow
  tnt:
    gravity: 0.04
    drag: 0.02
    gravity-before-move: true
```

`max-launch-speed` sets the range envelope. At the default 12 blocks/tick and
level ground, arrows reach about 880 blocks and TNT about 533; targets beyond
that are reported as unreachable rather than fired at.

`max-flight-ticks` refuses shots that creep toward the target for thousands of
ticks near the absolute range limit — the chunk would unload or the entity
despawn long before impact, and that regime is also the one place the arithmetic
loses precision (see [Accuracy](#accuracy)).

A hard ceiling of 512 packs applies regardless of `max-packs-per-volley`, so a
mis-edited value cannot order a hundred thousand entities into existence.

The first round of a volley always goes to the exact point; the rest are
scattered inside `impact-radius`, distributed by area rather than by radius so
the pattern does not clump in the middle.

The `physics` section exists so that a game update or another plugin retuning
projectile motion is a config edit rather than a recompile. The built-in
defaults match vanilla and normally need no changes.

---

## Ballistics

All of it lives in `ProjectileBallistics`, which carries no Bukkit types on
purpose: it is plain arithmetic and is unit-tested outside the server.

### The per-tick update

The game does not integrate a differential equation. Each tick, a projectile
entity performs exactly three operations, and different entity classes perform
them in **different orders**:

```
AbstractArrow.tick()          (arrows, tridents)
ThrowableProjectile.tick()    (potions)
        pos += v
        v   *= (1 - c)
        v.y -= g

PrimedTnt.tick()              (TNT)
        v.y -= g
        pos += v
        v   *= (1 - c)
```

| Projectile | gravity `g` | drag `c` | terminal `g/c` | gravity before move |
|---|---|---|---|---|
| Arrow, trident | 0.05 | 0.01 | 5.0 | no |
| Potion | 0.05 | 0.01 | 5.0 | no |
| TNT | 0.04 | 0.02 | 2.0 | **yes** |

`ThrownPotion` overrides the generic throwable gravity of 0.03 with **0.05**,
which makes potion physics identical to arrow physics. The operation order is
the part that is easy to miss and matters most: applying gravity before the move
costs TNT one gravity tick on its very first step, and that difference alone
visibly changes the trajectory.

### The closed form

Let `u[n]` be the velocity actually used for the displacement on tick `n`. Under
both orderings it satisfies the same first-order linear recurrence:

$$u[n+1] = d \cdot u[n] - g \cdot \hat{e}_y, \qquad d = 1 - c$$

The orderings differ **only in the seed**:

$$u[0] = v_0 \quad \text{(arrow-like)}, \qquad u[0] = v_0 - g \cdot \hat{e}_y \quad \text{(TNT)}$$

The recurrence has the fixed point $v_\infty = -g/c$ (terminal velocity), so

$$u_x[n] = u_{0x}\, d^{\,n}, \qquad u_y[n] = v_\infty + (u_{0y} - v_\infty)\, d^{\,n}$$

Displacement after `n` ticks is the sum of the first `n` step velocities. With
the geometric sum

$$S(n) = \sum_{k=0}^{n-1} d^{\,k} = \frac{1 - d^{\,n}}{c}$$

that gives the whole ballistics model:

$$x(n) = u_{0x}\, S(n)$$
$$y(n) = v_\infty\, n + (u_{0y} - v_\infty)\, S(n)$$
$$z(n) = u_{0z}\, S(n)$$

These are **exact** for integer `n` — a geometric sum, not an approximation.
Inside a tick the entity moves along a straight chord, so sub-tick positions are
a linear interpolation with `u[n]`:

$$p(n + f) = p(n) + f \cdot u[n], \qquad 0 \le f < 1$$

The apex is where the vertical step velocity crosses zero:

$$t_{apex} = \log_d \frac{-v_\infty}{u_{0y} - v_\infty}$$

### What follows from it

**Maximum range is finite and known.** As $n \to \infty$, $S(n) \to 1/c$, so no
shot ever travels further than

$$L_\infty = \frac{u_{0h}}{c}$$

horizontally, whatever the elevation. For TNT launched at 45° with speed 5 that
is 176.8 blocks — a hard wall. For an arrow at the same speed and angle it is
353.6 blocks, because arrow drag is half as large.

**Arrival time is a logarithm.** Setting $x(t) = L$ inverts directly:

$$t = \log_d\!\left(1 - \frac{c\,L}{u_{0h}}\right)$$

The plugin then refines the whole tick on the chord the entity actually travels,
which is what `exactTicksToDistance` does. Knowing the arrival tick exactly is
what lets TNT be given a fuse equal to its flight time, so the charge bursts on
the aim point instead of landing, bouncing and skidding past it.

**Height at a distance** is the function every inverse problem inverts: locate
the arrival tick `t`, then interpolate inside it.

$$H(v, \theta, L) = y(\lfloor t \rfloor) + (t - \lfloor t \rfloor)\, u_y[\lfloor t \rfloor]$$

### The inverse problems

For a target at horizontal distance `L` and height difference `H`:

| Problem | Method | Bracket |
|---|---|---|
| **Speed for a given angle** (`solveSpeed`) | bisection to double precision | $v > \dfrac{cL}{\cos\theta}$ below, grown ×1.6 up to the speed cap above |
| **Angle for a given speed** (`solveAngle`) | golden-section for the peak, then bisection on each side | $\dfrac{v\cos\theta}{c} > L$ brackets it exactly |
| **Minimum-speed shot** (`solveMinimumSpeed`) | golden-section over the elevation | −80° to 89° |
| **Descent angle** (`solveImpactAngle`) | bisection on the launch angle, re-solving the speed at each step | the feasible angle range for the target |

Each rests on a property that is checked rather than assumed:

- $H(v, \theta, L)$ is **strictly increasing in launch speed** — a faster shot
  reaches any given distance sooner, and therefore higher — so the speed root is
  unique.
- $H$ is **unimodal in the launch angle** over the reachable interval, so
  golden-section finds the peak and each arc is bisected on one side of it.
- The **descent angle increases monotonically with the launch angle**, verified
  over a dense sweep of launch angles per projectile type, which is what makes
  the impact-angle bisection valid.

The lower bracket $v > cL/\cos\theta$ is just the asymptotic range condition
rearranged: below that speed the target is beyond $L_\infty$ and no flight time
gets there.

Notably, the optimal elevation is **not** 45°. Drag shifts it well below: TNT at
300 blocks needs 8.64 blocks/tick at 45°, but only 7.35 at 24.7°.

The plugin resolves the elevation **once per volley** against the centre of the
target area, then solves a speed per round. The rounds of a volley are metres
apart on a target hundreds of metres away, so per-round elevation solving would
only jitter the angle by a fraction of a degree while costing a nested root find
each time and breaking the visual coherence of rounds arriving on one trajectory
family.

When the requested descent angle cannot be met, the elevation is never silently
traded for a different one — that would defeat the point of asking for it. The
volley is refused with the reason and the achievable descent angles for that
target.

### Impact angle and tick quantisation

Velocity is constant within a tick, so the direction of travel at the target is
exactly `u[n]` for the arrival tick — the same direction as the segment the
entity visibly moves along:

$$\alpha_{impact} = \operatorname{atan2}\left(-u_y[n],\ u_x[n]\right)$$

Interpolating toward `u[n+1]` would read smoother but would not be what the
projectile does; for TNT, whose velocity turns fastest, the two differ by up to
about three degrees.

Because it is piecewise constant, the achievable descent angles come in steps
rather than a continuum. The solver returns the closest achievable slope —
verified against a fine sweep of launch angles to be optimal — and the steps are
at most about 2.5°, and only that wide for steep descents at short range, where
a degree of slope is not distinguishable anyway.

### Worked numbers

Launch speed needed on level ground, and the flight time it implies:

| Distance | Arrow @ 45° | Arrow, min speed | TNT @ 45° | TNT, min speed |
|---:|---|---|---|---|
| 50 | 1.827 b/t, 49 t | 1.814 @ 40.7°, 45 t | 2.028 b/t, 59 t | 1.969 @ 36.1°, 49 t |
| 100 | 2.772 b/t, 71 t | 2.736 @ 38.8°, 63 t | 3.360 b/t, 91 t | 3.162 @ 32.2°, 68 t |
| 200 | 4.332 b/t, 105 t | 4.223 @ 36.4°, 88 t | 5.966 b/t, 146 t | 5.310 @ 27.8°, 94 t |
| 300 | 5.734 b/t, 134 t | 5.520 @ 34.8°, 108 t | 8.643 b/t, 198 t | 7.354 @ 24.7°, 113 t |
| 500 | 8.387 b/t, 184 t | 7.877 @ 32.1°, 138 t | 14.176 b/t, 299 t | 11.353 @ 20.8°, 141 t |
| 800 | 12.289 b/t, 252 t | 11.153 @ 29.2°, 171 t | 22.630 b/t, 449 t | 17.292 @ 17.0°, 170 t |

Anything above the default `max-launch-speed: 12.0` is outside the envelope and
is refused: the 45° TNT shots at 500 and 800 blocks, the minimum-speed TNT at
800, and the 45° arrow at 800 — whose minimum-speed shot, at 11.15, still gets
there.

---

## Accuracy

The solver is checked against `TickSimulator`, a literal transcription of the
entity tick order that shares no code with the closed form, so agreement between
them is evidence rather than a tautology.

| Check | Result |
|---|---|
| Closed form vs tick simulator, 400 ticks, all types | ≤ 6.8 × 10⁻¹² blocks |
| Firing solutions, 5–1200 blocks, dH from −0.6·dL to +0.6·dL, within the 600-tick cap | ≤ 3.7 × 10⁻⁹ blocks |
| Angle solutions, both arcs | ≤ 3.8 × 10⁻¹⁰ blocks |
| Reported descent angle vs simulated | ≤ 5.9 × 10⁻¹³ degrees |
| Predicted flight time (TNT fuse) | ≤ 9.6 × 10⁻¹² ticks |

The accuracy is distance-independent — there is no fitted range to fall outside
of. The only limits are physical ones the solver reports honestly: the
asymptotic range $v\cos\theta/c$, the configured speed cap, and the flight-time
cap.

One caveat, stated plainly: within roughly the last 0.1% of a projectile's
asymptotic range, arrival height becomes extraordinarily sensitive to launch
speed (hundreds of blocks per 10⁻⁵ blocks/tick), and the solution degrades to
about 10⁻⁴ blocks. Those shots take a thousand ticks or more and are refused by
`max-flight-ticks` long before they are fired; the 3.7 × 10⁻⁹ figure above is
the worst case inside the default cap.

```bash
mvn test        # 47 tests, no server required
```

The suite covers the closed form against the simulator, every inverse solver
against the simulator, the monotonicity the impact-angle solver depends on,
aim-mode parsing, station input validation and the slot map. Building the
optional native library adds two parity tests, for 49.

---

## Performance

Measured on OpenJDK 21, a 100-point volley, median of 9 timed reps after warm-up
(`SolverBenchmark`):

| Operation | Cost |
|---|---:|
| One firing solution (`solveSpeed`, warmed) | ≈ 5.9 µs |
| A 100-round volley | ≈ 0.59 ms, on the server thread |
| Minimum-speed shot (`solveMinimumSpeed`) | ≈ 0.56 ms |
| Descent-angle solve (`solveImpactAngle`) | ≈ 0.97 ms |

The last two are nested root finds, which is why they are resolved once per
volley rather than once per round. For scale, a server tick is 50 ms and a volley
is ordered once.

---

## The native solver experiment

The solver is a few dozen floating-point operations per bisection step with no
allocation — the shape of code HotSpot compiles well. So rather than assume, the
same algorithm was ported to C++ line for line, exposed through JNI, and timed
against the Java version. `NativeSolverParityTest` asserts the two agree bit for
bit, so the comparison is between runtimes and not between implementations.

| | per volley | per point | speedup |
|---|---:|---:|---:|
| Java | 590.4 µs | 5.904 µs | — |
| JNI, one call per point | 450.8 µs | 4.508 µs | 1.31× |
| JNI, one call per volley | 472.9 µs | 4.729 µs | 1.25× |

Two things stand out.

**The JNI boundary is not the bottleneck.** Batching a hundred points into one
call bought nothing measurable — it came out slightly slower here. At roughly
5 µs of arithmetic per point, a JNI transition of a few tens of nanoseconds does
not register. The usual advice to batch across the boundary is aimed at
workloads with far less work per call than this one.

**The 1.3× is real and irrelevant.** It saves about 140 µs per volley: a third
of a percent of one server tick, once, against shipping and loading a
platform-specific binary, a second implementation to keep in step with the
first, and a new class of deployment failure. Wiring it into the aiming path
would also recover less than the benchmark shows, because the native side
returns only a speed — flight time, apex and impact angle would still be
computed in Java afterwards.

**So the plugin does not use it.** The Java solver is the only one on the aiming
path. The experiment is kept, tested and reproducible — `native/README.md` has
the build and benchmark commands — but `native/build/` is gitignored, so a fresh
clone produces a jar with no native code in it, and the build needs no C++
toolchain.

To re-run it on your own hardware:

```bash
./native/build.sh
mvn -q test-compile
java -cp target/classes:target/test-classes \
     org.yudev.airtillery.ballistics.SolverBenchmark
```

---

## Project layout

```
src/main/java/org/yudev/airtillery/
  ArtilleryPlugin.java          startup: config → ballistics registry → listeners
  ArtilleryManager.java         fire control: order → aim points → entities
  ArtilleryCommandExecutor.java /artillery give
  TargetPoint.java              one aim point and its solution
  TntExplosionListener.java     detonates artillery TNT on contact
  ballistics/
    ProjectileBallistics.java   the closed form and the inverse solvers
    BallisticsRegistry.java     per-type constants, limits, aim-mode resolution
    BallisticSolution.java      speed, angle, flight time, apex, impact angle
    AimMode.java                auto / launch:<deg> / impact:<deg> / flat
    NativeSolver.java           optional JNI path (experiment, unused)
  station/
    StationListener.java        every interaction with the block and its window
    StationMenu.java            renders block state into slots, reports the order
    StationLayout.java          the slot map
    StationInputs.java          parsing and validation of the renamed papers
    StationState.java           persistence in the block's PersistentDataContainer
    ProjectileKind.java         ammunition types, pack sizes and prices
src/test/java/                  solver tests, tick simulator, benchmark, station tests
native/                         the C++/JNI experiment
```

`AimMode` supports four trajectory choices — `auto`, a fixed launch elevation,
a fixed descent angle, and the minimum-speed shot. The station window exposes
the descent angle, which is the one that matters tactically; the others are
available to callers of the ballistics API.

---

## Building

```bash
mvn package          # target/AIrtillery-2.0.0.jar
mvn test             # 47 tests, no server required
```

Requires JDK 17 or newer. The `spigot-api` dependency is `provided`, so the jar
carries no dependencies at all. A C++ toolchain is optional and only needed to
reproduce the native experiment.
