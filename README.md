
# Trajectory Prediction and Optimization for Game Projectiles: A Research Study

> **This repository is the plugin.** It builds one Bukkit jar with Maven. The
> machine learning pipeline it started as — a Python trainer, pickled models and
> a Flask server the plugin queried over HTTP — has been replaced by a closed
> form, because projectile motion in the game is a linear recurrence over ticks
> and has an exact solution. Chapters 1 and 2 are the record of how the problem
> was originally approached; **chapter 3 supersedes them**, chapter 4 covers the
> native-code experiment, and chapter 5 documents the artillery station.

## Abstract

This research explores methods for accurately predicting and optimizing projectile trajectories in game environments. We investigate several approaches including direct physical modeling, machine learning, and neural networks to address the challenge of predicting projectile behavior with high precision. Our findings demonstrate that game physics often diverge from real-world physics in significant ways, requiring specialized models for accurate simulation. We introduce the Game Optimized Dataset Collection (GODC) method that reduces data collection time by orders of magnitude while maintaining prediction accuracy. The research culminates in an integrated system capable of precisely targeting projectiles with error rates below 1% for arrows and approximately 4% for TNT explosives.

## Chapter 1: Initial Research Approaches

### 1.1 Direct Physical Modeling

Our initial approach attempted to apply real-world physics formulas to the game environment, disregarding air resistance as we presumed it would not be implemented in the game physics. This assumption proved incorrect, as the game does simulate drag forces. Standard physical trajectory equations from the real world proved inapplicable to the game's reality.

#### Generalized Ballistic Model

At the outset of our research, we began with the following model:

##### Initial Data

- Starting point: $(0, 0, 0)$
- Target point: $(X_t, Y_t, Z_t)$
- Acceptable deviation: radius $R$ meters
- Gravitational acceleration: $g = 9.81 m/s²$

##### General Motion Equations

For a projectile launched with initial velocity v, elevation angle θ and rotation angle φ from the X-axis:

$$x(t) = v \cdot \cos(\theta) \cdot \cos(\phi) \cdot t$$
$$y(t) = v \cdot \sin(\theta) \cdot t - \frac{g \cdot t^2}{2}$$
$$z(t) = v \cdot \cos(\theta) \cdot \sin(\phi) \cdot t$$

##### Flight Time

To hit a point with coordinate $Y_t$ (typically $Y_t = 0$ for a horizontal target):

$$Y_t = v \cdot \sin(\theta) \cdot t - \frac{g \cdot t^2}{2}$$

Solving this equation for $t$:

$$t = \frac{v \cdot \sin(\theta) + \sqrt{(v \cdot \sin(\theta))^2 + 2gY_t}}{g}$$

For the case where $Y_t = 0$ (target at ground level):

$$t = \frac{2 \cdot v \cdot \sin(\theta)}{g}$$

##### Impact Point Coordinates

Substituting flight time $t$ into the equations for $x(t)$ and $z(t)$:

$$X = \frac{v^2 \cdot \sin(2\theta) \cdot \cos(\phi)}{g}$$ (when $Y_t = 0$)
$$Z = \frac{v^2 \cdot \sin(2\theta) \cdot \sin(\phi)}{g}$$ (when $Y_t = 0$)

For arbitrary $Y_t$:

$$X = v \cdot \cos(\theta) \cdot \cos(\phi) \cdot \frac{v \cdot \sin(\theta) + \sqrt{(v \cdot \sin(\theta))^2 + 2gY_t}}{g}$$
$$Z = v \cdot \cos(\theta) \cdot \sin(\phi) \cdot \frac{v \cdot \sin(\theta) + \sqrt{(v \cdot \sin(\theta))^2 + 2gY_t}}{g}$$

##### Target Area Hit Condition

For hitting a circular area with radius $R$ around point $(X_t, Y_t, Z_t)$:

$$(X-X_t)^2 + (Z-Z_t)^2 \leq R^2$$ (when $Y = Y_t$)

##### Inverse Problem: Determining Launch Parameters

For a specified target $(X_t, 0, Z_t)$ on a horizontal plane:

1. Rotation angle $\phi$:
   $$\phi = \arctan\left(\frac{Z_t}{X_t}\right)$$

2. Required flight distance:
   $$L = \sqrt{X_t^2 + Z_t^2}$$

3. Possible elevation angles $\theta$ (two options for one distance):
   $$\theta_{1,2} = \frac{1}{2}\arcsin\left(\frac{gL}{v^2}\right)$$
   or
   $$\theta_1 = 45^\circ - \Delta\theta, \theta_2 = 45^\circ + \Delta\theta$$
   where Δθ depends on the ratio gL/v²

4. Minimum initial velocity for a given distance at the optimal angle θ = 45°:
   $$v_{min} = \sqrt{\frac{gL}{sin(90^\circ)}} = \sqrt{gL}$$

However, these equations failed to accurately predict in-game trajectories due to the unique implementation of physics in the game engine.

### 1.2 Neural Network Approach

Having established that we lacked knowledge of the exact equations governing projectile motion in the game, we considered employing neural networks to learn these patterns. The initial concept was to develop a reinforcement learning (RL) system where arrows would be launched, and a neural network would learn to select the correct velocity.

We investigated using DL4J, a deep learning library for Java. However, this resulted in a plugin with an excessive size of 800MB that ultimately failed to initialize properly.

An alternative approach was to create a bridge between Java and Python: Java would spawn arrows and record impact points, while Python would train the model. This method proved impractically time-consuming. With arrows requiring approximately 8 seconds of flight time on average, processing dozens of launch points with thousands of iterations each would demand excessive time resources.

### 1.3 Standard Neural Network Approach

Our next consideration was a standard neural network that would require a dataset of examples.

We planned to fix the launch angle and adjust the velocity to hit targets at specific horizontal distances (dL) and vertical heights (dH). Binary search and/or gradient descent methods were employed for this purpose. Measurements indicated that an average of 9 test launches were required to determine the necessary velocity for a given target. However, this efficiency gain was offset by the necessity for a large number of training examples.

### 1.4 Breakthrough Discovery

A significant advancement occurred when we discovered formulas and numerical values for velocity calculations on the game's wiki. This enabled us to perform simulations instead of actual arrow launches. We could now conduct "virtual calibration" and hit targets with the first actual shot. This raised the question of how to efficiently create an area of effect.

The table below presents data specifically for arrows, potions, tridents, and TNT from the game's physics system:

| Projectile Type | Acceleration (blocks/tick²) | Acceleration (m/s²) | Drag (1/tick) | Terminal velocity (m/tick) | Terminal velocity (m/s) |
|----------------|---------------------------|-------------------|--------------|--------------------------|----------------------|
| Items, falling blocks, and TNT | 0.04 | 16 | 0.02 | 2.00 | 40.0 |
| Thrown potions | 0.03 | 12 | 0.01 | 3.00 | 60.0 |
| Fired arrows and thrown tridents | 0.05 | 20 | 0.01 | 5.00 | 100.0 |

The formulas governing projectile motion in the game can be represented as:

Starting with an initial upward velocity `initialVelocity`, an entity's velocity after falling for a number of ticks `ticksPassed` can be calculated using:

Drag applied before acceleration:
$$finalVelocity = ((initialVelocity - acceleration) \times (1 - drag)^{ticksPassed}) - (acceleration \times \frac{1 - (1 - drag)^{ticksPassed}}{drag})$$

Drag applied after acceleration:
$$finalVelocity = (initialVelocity \times (1 - drag)^{ticksPassed}) - (acceleration \times \frac{1 - (1 - drag)^{ticksPassed}}{drag} \times (1 - drag))$$

Note: initialVelocity is measured in blocks/tick, finalVelocity in m/tick, and acceleration in blocks/tick².

### 1.5 System Expansion

For implementing area effect attacks, we needed to distribute N arrows precisely within a circle of radius R. Beyond launching an arrow directly at the target (circle center), we needed to launch the remaining arrows so they would land exactly within the circle. Evidently, each arrow would require its own velocity calculation.

Without knowing the exact implementation of the game physics, we would need to individually model each arrow in a volley to find its optimal velocity. For large volleys of 100 arrows, this would require approximately 900 iterations, which could be resource-intensive. This reinforced our need for a model that could quickly predict velocity based on input parameters.

### 1.6 Model Development

Having discovered the formula for velocity calculation, we could perform simulations outside the game. This allowed us to optimize dataset collection using multi-threaded Python processing.

We collected data and applied machine learning methods including RandomForest, GradientBoosting, XGBoost, and others.

### 1.7 Integration

To avoid "cold start" overhead with each artillery shot, we implemented an additional Python server using Flask. This server received requests from the game plugin and returned velocity predictions from the model, creating an efficient end-to-end system.

## Chapter 2: New Projectile Types

### 2.1 Verification of Physics for Potions, Tridents, and TNT

Before creating Python simulations for dataset collection, we verified the flight physics of different projectile types.

Tridents were found to have identical physics to arrows. As expected, they followed the same trajectory patterns, allowing us to use the model trained on arrow velocity prediction.

Potion velocity calculations differed slightly with different coefficients. After modifying the simulation module, we achieved accurate hits, confirming that potion dataset collection could also be performed in Python.

TNT, however, presented significant problems. Despite using the tabulated data in the simulation module, we observed strange discrepancies between modeled and actual trajectories. At close distances, TNT flew further than predicted by the model, while at longer distances, it significantly undershot the prediction.

After reaching the apex of its trajectory, TNT rapidly lost horizontal velocity, which was almost entirely converted to vertical velocity, creating a nearly vertical "drop" at the end of the trajectory.

### 2.2 Visualization

How do different projectile types fly?

After visualizing the trajectories on graphs, we observed that arrows and potions have similar trajectories, indicating similar physical models. However, TNT's horizontal velocity strangely drops rapidly and converts to vertical velocity, producing a steep "fall" at the end of the graph.

![Arrow Trajectory](https://i.ibb.co/DfPX9PZ0/ARROW-trajectory.png)

![TNT Trajectory](https://i.ibb.co/chp4C3WV/TNT-trajectory.png)

The comparison above illustrates the differences in trajectories between arrows and TNT with the same initial velocity.

### 2.3 Challenges

Finding the correct velocity for TNT through simulation proved impossible, necessitating real test launches, which as mentioned earlier, would be time-consuming.

In our simulation, we use approximately 18,000 launch points. Even with parallel processing of 30 points, this still results in 600 segments, requiring approximately 5,400 launches to determine the necessary velocities. With an average flight time of 10 seconds, this would amount to approximately 15 hours of continuous calibration—an impractically long duration.

### 2.4 Alternative Approach

What if we approached the problem differently?

During modeling, we had been fixing the target point and varying the launch point.

What if we fixed the launch point and varied the target point instead?

In this case, we would iterate through all velocities within a certain range and record the flight trajectories.

This represented a fundamental shift in our approach:

Previously, we were iterating through velocities to hit a target - essentially creating a relationship of "one velocity (trajectory) → one point (dH,dL)"

Now, we would obtain dozens or hundreds of dH+dL combinations from a single trajectory (velocity) - a relationship of "one velocity (trajectory) → multiple points (dH,dL)"

This required tracking trajectories for only a couple dozen launches, making the process thousands of times faster.

This method became what we call GODC — Game Optimized Dataset Collection.

### 2.5 Results

The GradientBoosting algorithm demonstrated the best performance.

Example comparison of two models:

**GradientBoosting Training:**

Fitting 3 folds for each of 10 candidates, totaling 30 fits

GradientBoosting:
- Best parameters: {'n_estimators': 200, 'max_depth': 7, 'learning_rate': 0.1}
- MSE: 0.000084
- MAE: 0.005726
- R²: 0.999991
- Average relative error: 0.18%
- Maximum relative error: 9.61%
- Predictions with error < 1%: 96.97%
- Training time: 70.33 seconds

**XGBoost Training:**

Fitting 3 folds for each of 10 candidates, totaling 30 fits

XGBoost:
- Best parameters: {'n_estimators': 200, 'max_depth': 7, 'learning_rate': 0.2, 'gamma': 0}
- MSE: 0.000245
- MAE: 0.010933
- R²: 0.999975
- Average relative error: 0.40%
- Maximum relative error: 24.79%
- Predictions with error < 1%: 92.21%
- Training time: 5.26 seconds

### 2.6 Small GODC Dataset Size

When collecting a dataset in-game, we record the flight trajectories of projectiles launched with velocities from V_min to V_max using a step size of V_step. Even with a relatively small step of 0.05, we obtain a dataset of 16,000 records (compared to the arrow velocity dataset with 50,000 records for an equivalent maximum distance). This slightly affects the model's accuracy—TNT accuracy is approximately 96%, while arrow accuracy is approximately 99%.

We could reduce the step to 0.01, but this would increase the number of launches five-fold (though this would still be manageable since we can increase the number of simultaneously launched TNT).

Alternatively, we could take a different approach—adding synthetic data through interpolation.

![Trajectory Comparison 0.70-0.80](https://i.ibb.co/JbBm9Vp/trajectory-comparison-0-70-0-75-0-80.png)

![Trajectory Comparison 5.15-5.25](https://i.ibb.co/S7D2YVsH/trajectory-comparison-5-15-5-20-5-25.png)

![Trajectory Comparison 10.45-10.55](https://i.ibb.co/spzCQRV0/trajectory-comparison-10-45-10-50-10-55.png)

This approach allows us to decrease the step size by half (to 0.025) while using interpolation to effectively obtain a dataset as if the step were 0.0125.

---

## Chapter 3: Closed-Form Solution

This chapter replaces the machine learning pipeline. No dataset, no model files,
no Flask server, no Java↔Python bridge — the firing solution is arithmetic
evaluated inside the plugin.

### 3.1 The per-tick update, and why the order matters

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

Two notes on the wiki table quoted in §1.4. `ThrownPotion` overrides the generic
throwable gravity of 0.03 with **0.05**, which is why the empirically-tuned
potion constant worked and why potion and arrow trajectories looked "similar" in
§2.2 — they are in fact *identical*. And the table says nothing about operation
order, which is exactly the part that matters for TNT.

### 3.2 A single closed form for all projectile types

Let `u[n]` be the velocity actually used for the displacement on tick `n`.
Under both orderings it satisfies the same first-order linear recurrence:

$$u[n+1] = d \cdot u[n] - g \cdot \hat{e}_y, \qquad d = 1 - c$$

The orderings differ **only in the seed**:

$$u[0] = v_0 \quad \text{(arrow-like)}, \qquad u[0] = v_0 - g \cdot \hat{e}_y \quad \text{(TNT)}$$

Solving the recurrence and summing the displacements, with
$S(n) = \frac{1 - d^n}{c}$ and $v_\infty = -\frac{g}{c}$:

$$x(n) = u_{0x} \, S(n)$$
$$y(n) = v_\infty \, n + (u_{0y} - v_\infty)\, S(n)$$
$$z(n) = u_{0z} \, S(n)$$

That is the whole ballistics model. It is exact for integer `n` — it is a
geometric sum, not an approximation — and inside a tick the entity moves along a
straight chord, so sub-tick positions are a linear interpolation with `u[n]`.

Two consequences worth stating explicitly:

- **Maximum range is finite and known.** As $n \to \infty$, $S(n) \to 1/c$, so no
  shot ever travels further than $u_{0h}/c$ horizontally. For TNT at 45° and
  speed 5 that is 176 blocks — a hard wall no amount of elevation can cross.
- **Arrival time is a logarithm.** $x(t) = L$ inverts directly:
  $t = \log_d\!\left(1 - \frac{cL}{u_{0h}}\right)$.

### 3.3 The inverse problem

For a target at horizontal distance `L` and height difference `H`:

- **Speed for a given angle.** The arrival height at `L` is strictly increasing
  in launch speed — a faster shot reaches any given distance sooner and hence
  higher — so the root is unique. Bisection is bracketed below by
  $v > \frac{cL}{\cos\theta}$ (the speed at which `L` is exactly the asymptotic
  range) and above by growing until the shot overflies. Converges to double
  precision in about 50 iterations of pure arithmetic.
- **Angle for a given speed.** Reachable angles satisfy
  $\frac{v\cos\theta}{c} > L$, which brackets the search exactly. The arrival
  height is unimodal in that interval, so golden-section finds the peak and
  bisection picks the flat or the lobbed arc on either side of it.
- **Minimum-speed shot.** Required speed is unimodal in angle; golden-section
  gives the widest-reaching shot. The plugin falls back to this when the
  preferred angle cannot reach.

Notably the optimal elevation is **not** 45°. Drag shifts it well below: TNT at
300 blocks needs 8.64 blocks/tick at 45°, but only 7.35 at 24.7°.

### 3.4 What the "TNT anomaly" of Chapter 2 actually was

§2.1 reported that TNT "flew further than predicted at close range and
significantly undershot at long range", and concluded the game's TNT physics
were unknown. There was no anomaly. There were two ordinary bugs:

1. **The simulator used potion constants for TNT.** In `dataset_generator.py`,
   `simulate_func = simulate_arrow_trajectory if projectile_type == "ARROW" else
   simulate_potion_trajectory` — every non-arrow projectile, TNT included, was
   simulated with `g = 0.05, c = 0.01`. TNT's real drag is twice as large, so
   the modelled trajectory kept its horizontal speed roughly twice as long as
   the real one. That alone produces exactly the reported symptom:

   | Target distance | Speed the old model prescribed | Where the TNT actually lands | Error |
   |---:|---:|---:|---:|
   | 40 | 1.6045 | 35.2 | −4.8 |
   | 80 | 2.4172 | 64.2 | −15.8 |
   | 120 | 3.1068 | 90.3 | −29.7 |
   | 160 | 3.7369 | 114.5 | −45.5 |
   | 200 | 4.3319 | 137.4 | −62.6 |

   The "steep vertical drop at the end of the trajectory" in §2.2 is not
   horizontal velocity converting into vertical velocity. It is drag: after 150
   ticks a TNT retains $0.98^{150} \approx 5\%$ of its horizontal speed while
   its vertical speed sits at terminal −2.0 blocks/tick.

2. **The fuse outlived the flight.** The plugin set `setFuseTicks(1000)`, so a
   TNT that reached the aim point kept going: it hit the ground, bounced
   (`multiply(0.7, -0.5, 0.7)`) and skidded before detonating up to 50 seconds
   later. At close range, with a shallow impact angle, it slid *past* the target
   — which is why short shots looked long. The solver now knows the exact
   arrival tick, and the fuse is set to it, so the charge bursts on target.

The 15 hours of in-game TNT calibration that motivated GODC were spent fitting
around these two bugs.

### 3.5 Results

Verified by `BallisticsSelfTest` (Java) and `scripts/ballistics.py` (Python),
each against an independent tick-by-tick simulator:

| Check | Result |
|---|---|
| Closed form vs tick simulator, 400 ticks, all types | ≤ 6.6 × 10⁻¹² blocks |
| Firing solutions, 5–1200 blocks, dH from −0.6·dL to +0.6·dL | ≤ 1.3 × 10⁻⁹ blocks |
| Angle solutions, both arcs | ≤ 3.8 × 10⁻¹⁰ blocks |
| Predicted flight time (TNT fuse) | ≤ 1 × 10⁻¹¹ ticks |
| Cost per projectile (Java, warmed) | ≈ 6 µs |
| Cost of a 100-projectile volley | ≈ 0.6 ms, on the server thread |

For comparison, the GradientBoosting models reported 0.16% mean and 7.3% maximum
relative error for TNT, and required a running Python process plus an HTTP
round-trip per volley.

The accuracy is also **distance-independent**, which was the second complaint
about the ML approach. The models were trained on dL ∈ [20, 500] with
|dH| ≤ 0.2·dL; outside that box their error grew without bound. The formula has
no training range. The only limits are physical ones the solver reports honestly:
the asymptotic range $v\cos\theta/c$, the configured speed cap, and a flight-time
cap.

One caveat, stated plainly: within roughly the last 0.1% of a projectile's
asymptotic range, arrival height becomes extraordinarily sensitive to launch
speed (hundreds of blocks per 10⁻⁵ blocks/tick), and the solution degrades to
about 10⁻⁴ blocks. Those shots take thousands of ticks and are refused by the
`max-flight-ticks` limit before they are ever fired.

### 3.6 What changed in the code

| Component | Before | After |
|---|---|---|
| aiming | HTTP request to a Flask server holding a GradientBoosting model | `ballistics.aim(...)`, in process |
| `PythonClient` | msgpack over HTTP | deleted |
| plugin startup | spawned and managed a Python process | loads constants from config |
| TNT fuse | fixed 1000 ticks | computed arrival tick |
| dH limit | shots refused beyond \|dH\| > 0.2·dL, and the aim point silently clamped into that band | removed; any geometry is solved directly |
| unreachable targets | the model returned a plausible-looking number | reported as unreachable, with the reason and the reachable angles |

The Python trainer, dataset generator, Flask server and interpolation scripts
are gone, as are the `TrajectoryRecorder` and `ProjectileTesting` helper
plugins — this repository is now the plugin and nothing else. They remain in the
git history.

Worth recording: the three files that were in `pretrained models/` were not
usable models. Each deserialises to a NumPy array of the three feature *names*;
the estimators they describe (`models/gradientboosting_*.pkl`) were never
committed, so the ML path could not have been restored from this repository in
any case.

### 3.7 Verifying it yourself

The constants are read from `config.yml`, so a game update that retunes
projectile physics is a config edit rather than a recompile. The solver is
checked against an independent tick-by-tick simulator (`TickSimulator` in the
tests) that is a literal transcription of the entity tick order and shares no
code with the closed form, so agreement between them is evidence rather than a
tautology.

```bash
mvn test
```

49 tests: the closed form against the simulator, every inverse solver against
the simulator, the monotonicity the impact-angle solver depends on, station
input validation and the slot map.

### 3.8 Choosing the trajectory

A reachable target can be hit on many different arcs, and which one you want is
a tactical question, not a physical one. The weapon carries an aim mode, set
with an optional `angle:` argument on `/giveartillery`:

```
/giveartillery false RAIN TNT UNIFORM 200 10 5 angle:auto        # default
/giveartillery false RAIN TNT UNIFORM 200 10 5 angle:70          # fixed launch elevation
/giveartillery false RAIN TNT UNIFORM 200 10 5 angle:impact:80   # steep descent onto the target
/giveartillery false RAIN TNT UNIFORM 200 10 5 angle:flat        # minimum-speed shot
```

The argument is named rather than positional, so existing commands keep working
unchanged and it can follow the optional potion arguments.

| Mode | Meaning |
|---|---|
| `auto` | The previous heuristic: 45°, raised toward a target above the launcher. Unchanged default. |
| `<degrees>` or `launch:<degrees>` | Fixed launch elevation, −90 to 90. |
| `impact:<degrees>` | Fixed **descent** angle at the target, 0 to 90. The launch elevation is whatever produces it. |
| `flat` / `min` | The minimum-speed shot — the widest-reaching one for a given speed cap. |

**Launch angle versus impact angle.** These are the two ends of the same arc,
and it is usually the impact angle you actually care about: dropping rounds
into a courtyard, over a wall, or steeply enough not to skip. `impact:` solves
for it directly. Steepening the launch always steepens the descent — verified
monotone over a grid of 1676 launch angles per projectile type — so the launch
angle is recovered by bisection, with the speed re-solved at every step.

**A fixed elevation limits how high you can shoot.** This is real and worth
planning around: at a given elevation θ and speed cap V, the reachable set is
bounded, and a shallower θ raises the target ceiling more slowly with distance.
The plugin no longer guesses about this. When a pinned angle cannot reach, it
says so and reports the descent angles that *are* available for that target:

```
Невозможно построить траекторию (70.0° запуска): target out of range for maximum speed 12.0
Для этой цели доступны углы падения от 31.4° до 78.2°
```

A pinned elevation is never silently traded for a different one — that would
defeat the point of pinning it. Only `auto` widens its search on failure.

**Tick quantisation.** A projectile's velocity is constant within a tick, so the
direction it travels at the target is exactly `u[n]` for the arrival tick. The
achievable descent angles therefore come in steps rather than a continuum. The
solver returns the closest achievable slope — verified against a fine sweep of
launch angles to be optimal within 10⁻¹² degrees — and the plugin tells you when
that differs from what you asked for by more than half a degree. The steps are
at most about 1.5°, and only that wide for near-vertical descents at short
range, where a degree of slope is not distinguishable anyway.

**Cost.** `auto` and a fixed launch angle are essentially free (< 1 µs).
`impact:` and `flat` each need a nested root find, about 1.1 ms and 0.7 ms
respectively — so they are resolved **once per volley** against the centre of
the target area, not once per round. The individual rounds then cost the usual
~6 µs each. Resolving per round would only jitter the elevation by a fraction of
a degree across an impact circle a few blocks wide, while breaking the visual
coherence of a volley arriving on one trajectory family.

---

## Chapter 4: Does native code help?

The solver is a few dozen floating-point operations per bisection step with no
allocation — the shape of code HotSpot compiles well. So rather than assume, the
same algorithm was ported to C++ line for line, exposed through JNI, and timed
against the Java version. `NativeSolverParityTest` asserts the two agree bit for
bit, so the comparison is between runtimes and not between implementations.

Measured on this machine, OpenJDK 21, a 100-point volley, median of 9 timed reps
after warm-up:

| | per volley | per point | speedup |
|---|---:|---:|---:|
| Java | 699.6 µs | 6.996 µs | — |
| JNI, one call per point | 528.5 µs | 5.285 µs | 1.32× |
| JNI, one call per volley | 527.7 µs | 5.277 µs | 1.33× |

Two things stand out.

**The JNI boundary is not the bottleneck.** Batching a hundred points into one
call saved 0.8 µs out of 528 — under 0.2%. At roughly 5 µs of arithmetic per
point, a JNI transition of a few tens of nanoseconds simply does not register.
The usual advice to batch across the boundary is aimed at workloads with far
less work per call than this one.

**The 1.32× is real and irrelevant.** It saves 172 µs per volley. A server tick
is 50,000 µs, and a volley is fired once, so the saving is a third of a percent
of one tick — against shipping and loading a platform-specific binary, a second
implementation to keep in step with the first, and a new class of deployment
failure. Wiring it into the aiming path would also recover less than the
benchmark shows, because the native side returns only a speed: flight time, apex
and impact angle would still be computed in Java afterwards.

**So the plugin does not use it.** The Java solver is the only one on the aiming
path. The experiment is kept, tested and reproducible — `native/README.md` has
the build and benchmark commands — but `native/build/` is gitignored, so a fresh
clone produces a jar with no native code in it, and the build needs no C++
toolchain.

If you want to re-run it on your own hardware:

```bash
./native/build.sh
mvn -q test-compile
java -cp target/classes:target/test-classes \
     org.yudev.airtillery.ballistics.SolverBenchmark
```

---

## Chapter 5: The artillery station

Artillery is a block: a shulker box handed out with `/artillery give`. Placing it
creates a station; right-clicking opens its window instead of the shulker's own
inventory. The block above it is the launch point.

### 5.1 The window

Six rows of nine. Every slot that accepts something has its label in the slot
directly above, and no two interactive slots touch, so a mis-aimed click lands
on inert glass rather than on the wrong control. Both invariants are asserted in
`StationTest`.

```
 row 0    .  .  .  .  T  .  .  .  .      T  ammunition selector
 row 1    .  .  L  .  L  .  L  .  .      L  label
 row 2    .  .  C  .  A  .  N  .  .      C  coordinates   "X Y Z"
 row 3    .  .  .  .  L  .  .  .  .      A  impact angle  "α"
 row 4    .  .  .  .  $  .  .  .  .      N  packet count  "N"
 row 5    .  .  .  F  .  R  .  .  .      $  payment
                                         F  fire    R  reset
```

**Ammunition selector.** Click to cycle. The item shows the type in its own
colour and the price in its lore. Switching ammunition returns any balance held
in the old currency, since gold and diamonds are not interchangeable.

| Ammunition | Pack | Price |
|---|---|---|
| Arrows | 10 | 1 gold ingot |
| Flaming arrows | 10 | 1 gold ingot |
| TNT | 5 | 1 diamond |
| Tridents | 1 | 1 diamond |

Potions are deliberately absent: they carry an effect, a duration and an
amplifier, which need a configuration surface of their own rather than one
toggle. The ballistics layer already supports them for when that exists.

**Inputs** take a sheet of paper renamed in an anvil. Coordinates are `X Y Z`,
the impact angle is a single number, the packet count is a single integer. Each
is validated the moment it is placed and refused with a reason rather than
accepted and mishandled later — a wrong format, a non-number, an angle outside
0–90, a count under 1 or over the configured ceiling. A paper already in a slot
is taken back by clicking it, which also clears the value it stood for.

The packet count counts packs, not rounds: 10 packs of arrows is 100 arrows for
10 gold ingots.

**Payment** absorbs matching items into a running balance, which is what lets a
price exceed one stack. A wrong item is left on the cursor untouched, and so are
renamed or enchanted ones, so nothing valuable disappears into a payment slot by
accident. Overpayment stays credited and comes back through reset.

**Fire** is a redstone block until the order is complete, then an emerald block.
Its lore lists whatever is still missing. Firing charges the balance only after
the shot is known to be solvable, so a refused order never costs anything.

**Reset** returns the balance.

### 5.2 State, and not losing player property

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

### 5.3 Configuration

```yaml
max-packs-per-volley: 64     # ceiling on packs per order
impact-radius: 3.0           # scatter radius around the requested point
fire-mode: RAIN              # RAIN staggers the volley, BURST fires in one tick
rain-spacing-ticks: 3        # ticks between rounds in RAIN mode
```

A hard ceiling of 512 packs applies regardless of the config, so a mis-edited
value cannot order a hundred thousand entities into existence.

---

## Building

```bash
mvn package          # target/AIrtillery-2.0.0.jar
mvn test             # 49 tests, no server required
```

Requires JDK 17 or newer. The `spigot-api` dependency is `provided`, so the jar
carries no dependencies at all. A C++ toolchain is optional and only needed to
reproduce the chapter 4 experiment.
