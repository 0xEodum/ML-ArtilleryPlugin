import os
import glob
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from scipy.interpolate import interp1d
from sklearn.ensemble import GradientBoostingRegressor
from sklearn.model_selection import train_test_split
from sklearn.metrics import mean_squared_error, r2_score

def process_trajectory_file(file_path):

    try:
        file_name = os.path.basename(file_path)
        if "speed" in file_name:
            speed_part = file_name.split("speed_")[1].split(".csv")[0]
            if "_angle_" in speed_part:
                speed_part = speed_part.split("_angle_")[0]
            initial_velocity = float(speed_part)
        else:
            data = pd.read_csv(file_path)
            if 'velocity' in data.columns:
                initial_velocity = data['velocity'].iloc[0]
            else:
                initial_velocity = np.sqrt(data['vx'].iloc[0]**2 + data['vy'].iloc[0]**2 + data['vz'].iloc[0]**2)
    except (IndexError, ValueError, KeyError) as e:
        print(f"Error extracting speed from {file_path}: {e}")
        return None

    data = pd.read_csv(file_path)

    if not all(col in data.columns for col in ['horizontal_distance', 'height_difference']):
        data['horizontal_distance'] = np.sqrt(data['relative_x']**2 + data['relative_z']**2)
        data['height_difference'] = data['relative_y']

    if 'angle_radians' in data.columns:
        angle_radians = data['angle_radians'].iloc[0]
    else:
        angle_radians = np.radians(45)

    if 'projectile_type' in data.columns:
        projectile_type = data['projectile_type'].iloc[0]
    else:
        projectile_type = 'TNT'

    if 'y' in data.columns:
        y_values = data['y']
    else:
        y_values = data['height_difference'] + data['y'].iloc[0] if 'y' in data.columns else data['height_difference']

    peak_idx = 0
    for i in range(1, len(y_values)):
        if y_values.iloc[i] < y_values.iloc[i-1]:
            peak_idx = i-1
            break

    if peak_idx == 0 and len(y_values) > 1:
        peak_idx = 0

    peak_height = y_values.iloc[peak_idx]

    descent_idx = peak_idx
    for i in range(peak_idx + 1, len(y_values)):
        if y_values.iloc[i] <= peak_height - 2:
            descent_idx = i
            break

    descent_data = data.iloc[descent_idx:]

    if 'height_difference' in descent_data.columns:
        height_values = descent_data['height_difference']
    else:
        height_values = descent_data['relative_y']

    min_height = max(height_values.min(), -50)
    max_height = min(height_values.max(), 50)

    if len(descent_data) > 1:
        interp_func = interp1d(
            height_values, 
            descent_data['horizontal_distance'],
            bounds_error=False,
            fill_value=np.nan
        )

        target_heights = np.arange(int(min_height), int(max_height) + 1, 1)

        results = []
        for height in target_heights:
            distance = interp_func(height)
            if not np.isnan(distance):
                results.append({
                    'velocity': initial_velocity,
                    'height_difference': height,
                    'horizontal_distance': distance,
                    'angle_radians': angle_radians,
                    'projectile_type': projectile_type
                })

        return pd.DataFrame(results)
    else:
        return None

def process_all_trajectories(directory="trajectories", pattern="TNT_*.csv"):
    all_results = []
    file_pattern = os.path.join(directory, pattern)

    for file_path in glob.glob(file_pattern):
        try:
            trajectory_results = process_trajectory_file(file_path)
            if trajectory_results is not None and not trajectory_results.empty:
                all_results.append(trajectory_results)
        except Exception as e:
            print(f"Error processing {file_path}: {e}")

    if all_results:
        final_dataset = pd.concat(all_results, ignore_index=True)

        final_dataset.to_csv('tnt_trajectory_dataset.csv', index=False)
        print(f"Created dataset with {len(final_dataset)} data points")
        print("Dataset ready for use with the training script")

        return final_dataset
    else:
        print("No valid data found")
        return None

def visualize_trajectories(dataset):
    if dataset is None or dataset.empty:
        print("No data to visualize")
        return

    plt.figure(figsize=(12, 8))

    unique_velocities = sorted(dataset['velocity'].unique())
    velocities_to_plot = unique_velocities[::max(1, len(unique_velocities) // 10)]

    for velocity in velocities_to_plot:
        subset = dataset[dataset['velocity'] == velocity]
        plt.plot(subset['horizontal_distance'], subset['height_difference'], 
                 label=f'V={velocity:.2f}')

    plt.xlabel('Горизонтальное расстояние (блоки)')
    plt.ylabel('Разница высот (блоки)')
    plt.legend()
    plt.title('Траектории TNT при разных начальных скоростях')
    plt.grid(True)
    plt.savefig('tnt_trajectories.png')
    plt.close()

    plt.figure(figsize=(14, 10))

    unique_heights = sorted(dataset['height_difference'].unique())
    heights_to_plot = unique_heights[::max(1, len(unique_heights) // 5)]

    for height in heights_to_plot:
        height_data = dataset[np.isclose(dataset['height_difference'], height, atol=0.5)]

        if not height_data.empty:
            grouped = height_data.groupby('velocity')['horizontal_distance'].mean().reset_index()

            plt.plot(grouped['velocity'], grouped['horizontal_distance'], 
                     label=f'dH={height:.1f}', marker='o')

    plt.xlabel('Начальная скорость')
    plt.ylabel('Горизонтальное расстояние (блоки)')
    plt.legend()
    plt.title('Зависимость расстояния от начальной скорости для разных высот')
    plt.grid(True)
    plt.savefig('tnt_velocity_distance.png')
    plt.close()

def train_prediction_model(dataset):
    if dataset is None or dataset.empty:
        print("No data to train model")
        return None

    X = dataset[['horizontal_distance', 'height_difference']]
    y = dataset['velocity']

    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)
    model = GradientBoostingRegressor(
        n_estimators=200,
        learning_rate=0.1,
        max_depth=3,
        random_state=42
    )

    model.fit(X_train, y_train)

    y_pred = model.predict(X_test)
    mse = mean_squared_error(y_test, y_pred)
    r2 = r2_score(y_test, y_pred)

    print(f"Model trained. MSE: {mse:.4f}, R²: {r2:.4f}")

    with open('model_evaluation.txt', 'w') as f:
        f.write(f"Mean Squared Error: {mse:.4f}\n")
        f.write(f"R² Score: {r2:.4f}\n")

        f.write("\nExample predictions:\n")
        for i in range(min(10, len(X_test))):
            f.write(f"Distance: {X_test.iloc[i, 0]:.2f}, Height diff: {X_test.iloc[i, 1]:.2f} -> ")
            f.write(f"Predicted speed: {y_pred[i]:.2f}, Actual speed: {y_test.iloc[i]:.2f}\n")

    plt.figure(figsize=(8, 4))
    plt.bar(['Distance', 'Height Difference'], model.feature_importances_)
    plt.title('Feature Importance')
    plt.ylabel('Importance')
    plt.tight_layout()
    plt.savefig('feature_importance.png')
    plt.close()

    return model

def main():
    print("Starting TNT trajectory analysis...")

    if not os.path.exists('trajectories'):
        print("Trajectory directory not found. Please run the plugin first.")
        return

    dataset = process_all_trajectories()

    if dataset is not None and not dataset.empty:
        visualize_trajectories(dataset)

        model = train_prediction_model(dataset)

        if model is not None:
            print("\nCreating prediction function...")

            print("\nExample predictions:")
            for distance in [10, 20, 30, 40]:
                for height_diff in [-5, 0, 5]:
                    predicted_speed = model.predict([[distance, height_diff]])[0]
                    print(f"To reach distance {distance} blocks with height difference {height_diff} blocks, " 
                          f"use speed: {predicted_speed:.2f}")

    print("Analysis complete!")

if __name__ == "__main__":
    main()