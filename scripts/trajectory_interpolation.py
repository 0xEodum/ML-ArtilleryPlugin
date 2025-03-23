import os
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from scipy.interpolate import interp1d, CubicSpline
import glob
from itertools import product

def load_trajectory(file_path):
    data = pd.read_csv(file_path)

    file_name = os.path.basename(file_path)
    if "speed" in file_name:
        speed_part = file_name.split("speed_")[1].split(".csv")[0]
        if "_angle_" in speed_part:
            speed_part = speed_part.split("_angle_")[0]
        speed = float(speed_part)
    else:
        speed = data['velocity'].iloc[0]

    return data, speed

def find_trajectories_with_angle(directory, angle_degrees=None):
    if angle_degrees:
        file_pattern = os.path.join(directory, f"*_angle_{angle_degrees}*.csv")
    else:
        file_pattern = os.path.join(directory, "*.csv")

    matched_files = []

    for file_path in glob.glob(file_pattern):
        try:
            _, speed = load_trajectory(file_path)
            matched_files.append((file_path, speed))
        except Exception as e:
            print(f"Ошибка при обработке файла {file_path}: {e}")

    matched_files.sort(key=lambda x: x[1])
    return matched_files

def select_speed_ranges(trajectories, num_ranges=3):
    speeds = [speed for _, speed in trajectories]

    min_speed, max_speed = min(speeds), max(speeds)
    range_size = (max_speed - min_speed) / num_ranges

    selected_pairs = []

    for i in range(num_ranges):
        range_min = min_speed + i * range_size
        range_max = min_speed + (i + 1) * range_size

        range_speeds = [speed for speed in speeds if range_min <= speed <= range_max]

        if len(range_speeds) >= 3:
            for v1, v2 in product(range_speeds, repeat=2):
                if abs(v2 - v1 - 0.1) < 0.001:
                    mid_speed = (v1 + v2) / 2

                    if any(abs(v - mid_speed) < 0.001 for v in range_speeds):
                        selected_pairs.append((v1, mid_speed, v2))
                        break

        if not any(pair[0] >= range_min and pair[2] <= range_max for pair in selected_pairs):
            range_speeds.sort()
            for i in range(len(range_speeds) - 1):
                if abs(range_speeds[i+1] - range_speeds[i] - 0.1) < 0.001:
                    v1, v2 = range_speeds[i], range_speeds[i+1]
                    mid_speed = (v1 + v2) / 2

                    if any(abs(v - mid_speed) < 0.001 for v in range_speeds):
                        selected_pairs.append((v1, mid_speed, v2))
                        break

    return selected_pairs

def normalize_trajectory(data):
    y_values = data['y'] if 'y' in data.columns else data['height_difference']
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

    descent_data = data.iloc[descent_idx:].copy()

    descent_data['t_norm'] = np.linspace(0, 1, len(descent_data))

    return descent_data

def interpolate_trajectory(traj1, traj2, target_speed):
    norm_traj1 = normalize_trajectory(traj1)
    norm_traj2 = normalize_trajectory(traj2)

    speed1 = traj1['velocity'].iloc[0]
    speed2 = traj2['velocity'].iloc[0]

    if not (min(speed1, speed2) <= target_speed <= max(speed1, speed2)):
        raise ValueError(f"Целевая скорость {target_speed} вне диапазона [{min(speed1, speed2)}, {max(speed1, speed2)}]")

    t_grid = np.linspace(0, 1, 100)

    columns_to_interpolate = []
    for col in ['x', 'y', 'z', 'horizontal_distance', 'height_difference']:
        if col in norm_traj1.columns and col in norm_traj2.columns:
            columns_to_interpolate.append(col)

    if not columns_to_interpolate:
        raise ValueError("Не найдены общие столбцы для интерполяции")

    interp_funcs = {}
    for speed, traj in [(speed1, norm_traj1), (speed2, norm_traj2)]:
        interp_funcs[speed] = {}
        for col in columns_to_interpolate:
            interp_funcs[speed][col] = CubicSpline(traj['t_norm'], traj[col])

    interp_values = {col: [] for col in columns_to_interpolate}

    for t in t_grid:
        for col in columns_to_interpolate:
            values = [interp_funcs[speed][col](t) for speed in [speed1, speed2]]

            weight = (target_speed - speed1) / (speed2 - speed1)
            interp_value = values[0] * (1 - weight) + values[1] * weight

            interp_values[col].append(interp_value)

    interp_traj = pd.DataFrame({'t_norm': t_grid})
    for col in columns_to_interpolate:
        interp_traj[col] = interp_values[col]

    interp_traj['velocity'] = target_speed

    return interp_traj

def calculate_error(real_traj, interp_traj):
    norm_real = normalize_trajectory(real_traj)

    columns_to_compare = []
    for col in ['horizontal_distance', 'height_difference']:
        if col in norm_real.columns and col in interp_traj.columns:
            columns_to_compare.append(col)

    if not columns_to_compare:
        for col in ['x', 'y', 'z']:
            if col in norm_real.columns and col in interp_traj.columns:
                columns_to_compare.append(col)

    if not columns_to_compare:
        raise ValueError("Не найдены общие столбцы для сравнения")

    real_interp = {}
    for col in columns_to_compare:
        real_cs = CubicSpline(norm_real['t_norm'], norm_real[col])
        real_interp[col] = real_cs(interp_traj['t_norm'])

    errors = {}
    for col in columns_to_compare:
        abs_error = np.abs(real_interp[col] - interp_traj[col])
        with np.errstate(divide='ignore', invalid='ignore'):
            rel_error = abs_error / np.abs(real_interp[col])
            rel_error = np.where(np.isfinite(rel_error), rel_error, 0)

        errors[f"{col}_abs_mean"] = np.mean(abs_error)
        errors[f"{col}_abs_max"] = np.max(abs_error)
        errors[f"{col}_rel_mean"] = np.mean(rel_error) * 100
        errors[f"{col}_rel_max"] = np.max(rel_error) * 100

    return errors, real_interp, interp_traj['t_norm']

def plot_comparison(traj1, traj2, real_mid_traj, interp_traj, speeds, errors):
    if 'horizontal_distance' in traj1.columns and 'height_difference' in traj1.columns:
        x_col, y_col = 'horizontal_distance', 'height_difference'
    else:
        x_col, y_col = 'x', 'y'

    plt.figure(figsize=(12, 8))

    norm_traj1 = normalize_trajectory(traj1)
    norm_traj2 = normalize_trajectory(traj2)
    norm_real_mid = normalize_trajectory(real_mid_traj)

    plt.plot(norm_traj1[x_col], norm_traj1[y_col], 'b-', label=f'V = {speeds[0]:.2f}', alpha=0.7)
    plt.plot(norm_traj2[x_col], norm_traj2[y_col], 'g-', label=f'V = {speeds[2]:.2f}', alpha=0.7)

    plt.plot(norm_real_mid[x_col], norm_real_mid[y_col], 'k-', 
             label=f'Фактическая V = {speeds[1]:.2f}', linewidth=2)

    plt.plot(interp_traj[x_col], interp_traj[y_col], 'r--', 
             label=f'Интерполяция V = {speeds[1]:.2f}', linewidth=2)

    error_text = f"Средняя абс. ошибка: {errors[f'{x_col}_abs_mean']:.2f} блоков, " + \
                f"макс. абс. ошибка: {errors[f'{x_col}_abs_max']:.2f} блоков\n" + \
                f"Средняя отн. ошибка: {errors[f'{x_col}_rel_mean']:.2f}%, " + \
                f"макс. отн. ошибка: {errors[f'{x_col}_rel_max']:.2f}%"

    plt.xlabel(f'{x_col} (блоки)')
    plt.ylabel(f'{y_col} (блоки)')
    plt.title(f'Сравнение траекторий для скоростей {speeds[0]:.2f}, {speeds[1]:.2f}, {speeds[2]:.2f}\n{error_text}')
    plt.grid(True)
    plt.legend()

    plt.savefig(f'trajectory_comparison_{speeds[0]:.2f}_{speeds[1]:.2f}_{speeds[2]:.2f}.png', dpi=300, bbox_inches='tight')
    plt.close()

def main():
    trajectory_dir = "trajectories"
    angle_degrees = 45.0

    all_trajectories = find_trajectories_with_angle(trajectory_dir, angle_degrees)

    if len(all_trajectories) < 9:
        print(f"Недостаточно траекторий для сравнения. Найдено: {len(all_trajectories)}")
        return

    print(f"Найдено {len(all_trajectories)} траекторий")

    desired_pairs = [
        (0.70, 0.75, 0.80),
        (5.15, 5.20, 5.25),
        (10.45, 10.50, 10.55)
    ]

    speeds = [speed for _, speed in all_trajectories]
    speed_to_traj = {speed: traj for traj, speed in all_trajectories}

    pairs_to_test = []
    for v1, v2, v3 in desired_pairs:
        closest_v1 = min(speeds, key=lambda x: abs(x - v1))
        closest_v2 = min(speeds, key=lambda x: abs(x - v2))
        closest_v3 = min(speeds, key=lambda x: abs(x - v3))

        if abs(closest_v1 - v1) < 0.01 and abs(closest_v2 - v2) < 0.01 and abs(closest_v3 - v3) < 0.01:
            pairs_to_test.append((closest_v1, closest_v2, closest_v3))

    if not pairs_to_test:
        print("Не найдены траектории с требуемыми скоростями. Выбираем близкие пары...")
        pairs_to_test = select_speed_ranges(all_trajectories, 3)

    if not pairs_to_test:
        print("Не удалось выбрать подходящие пары скоростей")
        return

    print(f"Выбрано {len(pairs_to_test)} троек скоростей для сравнения:")
    for v1, v2, v3 in pairs_to_test:
        print(f"  {v1:.2f}, {v2:.2f}, {v3:.2f}")

    for v1, v2, v3 in pairs_to_test:
        print(f"\nАнализ траекторий для скоростей {v1:.2f}, {v2:.2f}, {v3:.2f}...")

        traj1, _ = load_trajectory(speed_to_traj[v1])
        real_mid_traj, _ = load_trajectory(speed_to_traj[v2])
        traj2, _ = load_trajectory(speed_to_traj[v3])

        try:
            interp_traj = interpolate_trajectory(traj1, traj2, v2)
            print(f"Траектория успешно интерполирована для скорости {v2:.2f}")

            errors, real_interp_values, t_norm = calculate_error(real_mid_traj, interp_traj)

            print("Статистика ошибок:")
            for key, value in errors.items():
                print(f"  {key}: {value:.6f}")

            plot_comparison(traj1, traj2, real_mid_traj, interp_traj, (v1, v2, v3), errors)

            output_file = f"interpolated_trajectory_speed_{v2:.2f}.csv"
            interp_traj.to_csv(output_file, index=False)
            print(f"Интерполированная траектория сохранена в файл {output_file}")

        except Exception as e:
            print(f"Ошибка при интерполяции: {e}")

if __name__ == "__main__":
    main()
