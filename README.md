
# Trajectory Prediction and Optimization for Game Projectiles: A Research Study

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
