import numpy as np
import pandas as pd
from tqdm import tqdm
import multiprocessing
from concurrent.futures import ProcessPoolExecutor
import os
import time
import matplotlib.pyplot as plt
import seaborn as sns
from datetime import datetime
import pickle
import argparse

MAX_SIMULATION_TICKS = 600  

ARROW_GRAVITY = 0.05  
ARROW_DRAG = 0.01    
ARROW_DRAG_BEFORE_ACCELERATION = False

POTION_GRAVITY = 0.05  
POTION_DRAG = 0.01    
POTION_DRAG_BEFORE_ACCELERATION = True

def calculate_optimal_launch_angle(horizontal_distance, height_difference):

    if height_difference <= 0:

        return np.radians(45)
    else:

        ratio = height_difference / horizontal_distance

        angle_degrees = max(30, min(70, np.degrees(np.arctan(ratio)) + 45))
        return np.radians(angle_degrees)

def simulate_projectile_trajectory(horizontal_distance, height_difference, angle_radians, velocity,
                                  gravity, drag, drag_before_acceleration):

    horizontal_direction = np.array([1.0, 0.0, 0.0])

    direction = np.array([horizontal_direction[0], np.tan(angle_radians), horizontal_direction[2]])
    direction = direction / np.linalg.norm(direction)

    target_position = np.array([horizontal_distance, height_difference, 0.0])

    position = np.array([0.0, 0.0, 0.0])
    current_velocity = direction * velocity

    min_distance = float('inf')
    closest_point = None
    tick_at_closest_point = 0

    for tick in range(MAX_SIMULATION_TICKS):

        position = position + current_velocity

        if drag_before_acceleration:

            current_velocity = current_velocity * (1.0 - drag)
            current_velocity[1] = current_velocity[1] - gravity
        else:

            current_velocity[1] = current_velocity[1] - gravity
            current_velocity = current_velocity * (1.0 - drag)

        distance = np.linalg.norm(position - target_position)

        if distance < min_distance:
            min_distance = distance
            closest_point = position.copy()
            tick_at_closest_point = tick

        if tick > tick_at_closest_point + 20:
            break

        if position[1] < 0 and target_position[1] >= 0:
            break

    is_overshoot = False
    if closest_point is not None:

        target_to_closest = closest_point - target_position

        projection = np.dot(np.array([target_to_closest[0], 0, target_to_closest[2]]), horizontal_direction)
        is_overshoot = projection > 0

    return min_distance, is_overshoot

def simulate_arrow_trajectory(horizontal_distance, height_difference, angle_radians, velocity):

    return simulate_projectile_trajectory(
        horizontal_distance, height_difference, angle_radians, velocity,
        ARROW_GRAVITY, ARROW_DRAG, ARROW_DRAG_BEFORE_ACCELERATION
    )

def simulate_potion_trajectory(horizontal_distance, height_difference, angle_radians, velocity):

    return simulate_projectile_trajectory(
        horizontal_distance, height_difference, angle_radians, velocity,
        POTION_GRAVITY, POTION_DRAG, POTION_DRAG_BEFORE_ACCELERATION
    )

def estimate_initial_velocity(horizontal_distance, height_difference, projectile_type):

    initial_guess = np.sqrt(horizontal_distance) * 0.2

    if height_difference > 0:
        initial_guess *= (1 + height_difference / horizontal_distance * 0.7)
    elif height_difference < 0:
        initial_guess *= (1 - abs(height_difference) / horizontal_distance * 0.3)

    if projectile_type == "POTION":
        initial_guess *= 0.95

    if horizontal_distance > 200:
        distance_coefficient = min(3.0, 1.0 + (horizontal_distance - 200) / 100)
        initial_guess *= distance_coefficient

    return initial_guess

def find_optimal_velocity(horizontal_distance, height_difference, angle_radians, projectile_type):

    initial_guess = estimate_initial_velocity(horizontal_distance, height_difference, projectile_type)

    lower_bound = max(0.1, initial_guess * 0.5)
    upper_bound = min(10.0, initial_guess * 2.0)

    if horizontal_distance > 300:
        upper_bound = min(10.0, initial_guess * 4.0)

    best_velocity = initial_guess
    best_distance = float('inf')

    iterations = 0
    max_iterations = 30
    precision = 0.01

    simulate_func = simulate_arrow_trajectory if projectile_type == "ARROW" else simulate_potion_trajectory

    while iterations < max_iterations and upper_bound - lower_bound > precision:
        iterations += 1

        mid_velocity = (lower_bound + upper_bound) / 2

        mid_distance, mid_is_overshoot = simulate_func(
            horizontal_distance, height_difference, angle_radians, mid_velocity)

        if mid_distance < best_distance:
            best_distance = mid_distance
            best_velocity = mid_velocity

            if best_distance < 0.5:
                break

        lower_distance, lower_is_overshoot = simulate_func(
            horizontal_distance, height_difference, angle_radians, lower_bound)
        upper_distance, upper_is_overshoot = simulate_func(
            horizontal_distance, height_difference, angle_radians, upper_bound)

        if mid_is_overshoot:
            upper_bound = mid_velocity
        else:
            lower_bound = mid_velocity

    final_distance, is_overshoot = simulate_func(
        horizontal_distance, height_difference, angle_radians, best_velocity)

    return {
        'velocity': best_velocity,
        'distance': final_distance,
        'iterations': iterations
    }

def process_combination(params):

    dL, dH, projectile_type = params

    angle_radians = calculate_optimal_launch_angle(dL, dH)

    if dL < 30 and angle_radians > np.radians(60):
        return None

    try:

        result = find_optimal_velocity(dL, dH, angle_radians, projectile_type)

        return {
            'projectile_type': projectile_type,
            'horizontal_distance': dL,
            'height_difference': dH,
            'angle_radians': angle_radians,
            'angle_degrees': np.degrees(angle_radians),
            'velocity': result['velocity'],
            'min_distance': result['distance'],
            'iterations': result['iterations']
        }
    except Exception as e:
        print(f"Ошибка при обработке комбинации dL={dL}, dH={dH}, тип={projectile_type}: {str(e)}")
        return None

def generate_dataset_parallel(min_dL=20, max_dL=500, height_ratio=0.2, 
                             step_dL=1, step_dH=1, n_jobs=None, 
                             output_file="projectile_velocities_dataset.csv",
                             checkpoint_interval=1000,
                             projectile_types=None):

    if n_jobs is None:
        n_jobs = max(1, multiprocessing.cpu_count() - 1)

    if projectile_types is None:
        projectile_types = ["ARROW", "POTION"]

    param_combinations = []
    for dL in range(min_dL, max_dL + 1, step_dL):

        max_height = int(dL * height_ratio)
        for dH in range(-max_height, max_height + 1, step_dH):
            for pt in projectile_types:
                param_combinations.append((dL, dH, pt))

    total_combinations = len(param_combinations)
    print(f"Генерация датасета с {total_combinations} комбинациями, используя {n_jobs} процессов...")
    print(f"Типы снарядов: {', '.join(projectile_types)}")

    checkpoint_dir = "checkpoints"
    if not os.path.exists(checkpoint_dir):
        os.makedirs(checkpoint_dir)

    checkpoint_files = [f for f in os.listdir(checkpoint_dir) if f.startswith("checkpoint_") and f.endswith(".pkl")]
    data = []
    processed_combinations = set()

    if checkpoint_files:
        print(f"Найдено {len(checkpoint_files)} файлов контрольных точек, загружаем данные...")
        for checkpoint_file in checkpoint_files:
            with open(os.path.join(checkpoint_dir, checkpoint_file), 'rb') as f:
                checkpoint_data = pickle.load(f)
                for item in checkpoint_data:
                    if item is not None:
                        data.append(item)
                        processed_combinations.add((
                            item['horizontal_distance'], 
                            item['height_difference'], 
                            item['projectile_type']
                        ))

        print(f"Загружено {len(data)} записей из контрольных точек. Продолжаем вычисления...")

    remaining_combinations = [combo for combo in param_combinations if combo not in processed_combinations]

    if not remaining_combinations:
        print("Все комбинации уже обработаны.")
    else:
        print(f"Осталось обработать {len(remaining_combinations)} комбинаций...")

        data_chunks = []
        with ProcessPoolExecutor(max_workers=n_jobs) as executor:

            for i in range(0, len(remaining_combinations), checkpoint_interval):
                chunk = remaining_combinations[i:i+checkpoint_interval]
                print(f"Обработка блока {i//checkpoint_interval + 1} из {len(remaining_combinations)//checkpoint_interval + 1}...")

                chunk_results = list(tqdm(executor.map(process_combination, chunk), 
                                         total=len(chunk)))

                chunk_results = [res for res in chunk_results if res is not None]
                data.extend(chunk_results)
                data_chunks.append(chunk_results)

                checkpoint_name = f"checkpoint_{datetime.now().strftime('%Y%m%d_%H%M%S')}.pkl"
                with open(os.path.join(checkpoint_dir, checkpoint_name), 'wb') as f:
                    pickle.dump(chunk_results, f)

                print(f"Сохранена контрольная точка: {checkpoint_name}, обработано {len(chunk_results)} комбинаций")

    if data:
        df = pd.DataFrame(data)
        df.to_csv(output_file, index=False)
        print(f"Датасет сохранен в {output_file}, всего {len(df)} записей")

        df.to_pickle(output_file.replace('.csv', '.pkl'))
        print(f"Датасет также сохранен в формате pickle для быстрой загрузки")

        return df
    else:
        print("Не удалось создать датасет. Проверьте папку с контрольными точками.")
        return None

def visualize_dataset(df, output_dir="visualizations"):

    if not os.path.exists(output_dir):
        os.makedirs(output_dir)

    projectile_types = df['projectile_type'].unique()

    colors = {
        "ARROW": "blue",
        "POTION": "purple",
        "TRIDENT": "green",
        "TNT": "red"
    }

    print("Создание визуализаций...")

    plt.figure(figsize=(15, 10))

    for height in [-50, -25, 0, 25, 50]:

        available_heights = df['height_difference'].unique()
        closest_height = available_heights[np.abs(available_heights - height).argmin()]

        for p_type in projectile_types:
            subset = df[(df['height_difference'] == closest_height) & 
                         (df['projectile_type'] == p_type)]

            if not subset.empty:
                subset = subset.sort_values('horizontal_distance')
                plt.plot(subset['horizontal_distance'], subset['velocity'], 
                        color=colors.get(p_type, 'black'),
                        linestyle='-' if p_type == "ARROW" else '--',
                        label=f'{p_type}, Высота: {closest_height}')

    plt.title('Зависимость скорости от расстояния для разных высот и типов снарядов')
    plt.xlabel('Горизонтальное расстояние (блоки)')
    plt.ylabel('Оптимальная скорость (блоки/тик)')
    plt.legend()
    plt.grid(True)
    plt.savefig(os.path.join(output_dir, 'velocity_vs_distance.png'))

    for p_type in projectile_types:
        df_type = df[df['projectile_type'] == p_type]

        plt.figure(figsize=(16, 12))

        distances_sample = list(range(20, 501, 5))
        heights_sample = list(range(-100, 101, 5))

        pivot_sampled = df_type[
            (df_type['horizontal_distance'].isin(distances_sample)) & 
            (df_type['height_difference'].isin(heights_sample))
        ].pivot_table(
            values='velocity',
            index='horizontal_distance',
            columns='height_difference',
            aggfunc='mean'
        )

        sns.heatmap(pivot_sampled, cmap='viridis')
        plt.title(f'Тепловая карта оптимальной скорости для {p_type} (расстояние × высота)')
        plt.xlabel('Разница высот (блоки)')
        plt.ylabel('Горизонтальное расстояние (блоки)')
        plt.savefig(os.path.join(output_dir, f'velocity_heatmap_{p_type}.png'))

        fig = plt.figure(figsize=(14, 12))
        ax = fig.add_subplot(111, projection='3d')

        sample_size = min(5000, len(df_type))
        df_sample = df_type.sample(sample_size) if len(df_type) > sample_size else df_type

        scatter = ax.scatter(
            df_sample['horizontal_distance'],
            df_sample['height_difference'],
            df_sample['velocity'],
            c=df_sample['velocity'],
            cmap='viridis',
            alpha=0.5
        )

        ax.set_xlabel('Горизонтальное расстояние (блоки)')
        ax.set_ylabel('Разница высот (блоки)')
        ax.set_zlabel('Оптимальная скорость (блоки/тик)')
        ax.set_title(f'3D-зависимость оптимальной скорости от расстояния и высоты для {p_type}')

        fig.colorbar(scatter, ax=ax, label='Скорость (блоки/тик)')
        plt.savefig(os.path.join(output_dir, f'velocity_3d_{p_type}.png'))

    plt.figure(figsize=(12, 8))
    for p_type in projectile_types:
        df_type = df[df['projectile_type'] == p_type]
        sns.kdeplot(df_type['velocity'], label=p_type, color=colors.get(p_type, 'black'))

    plt.title('Сравнение распределений оптимальных скоростей для разных типов снарядов')
    plt.xlabel('Оптимальная скорость (блоки/тик)')
    plt.ylabel('Плотность')
    plt.legend()
    plt.grid(True)
    plt.savefig(os.path.join(output_dir, 'velocity_distribution_comparison.png'))

    plt.figure(figsize=(15, 8))

    for p_type in projectile_types:
        df_type = df[df['projectile_type'] == p_type]

        distance_groups = df_type.groupby('horizontal_distance')['velocity'].mean()

        plt.plot(distance_groups.index, distance_groups.values, 
                label=p_type, color=colors.get(p_type, 'black'))

    plt.title('Сравнение средних оптимальных скоростей по расстояниям')
    plt.xlabel('Горизонтальное расстояние (блоки)')
    plt.ylabel('Средняя оптимальная скорость (блоки/тик)')
    plt.legend()
    plt.grid(True)
    plt.savefig(os.path.join(output_dir, 'average_velocity_by_distance.png'))

    print(f"Визуализации сохранены в директории {output_dir}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Генератор датасета оптимальных скоростей снарядов')

    parser.add_argument('--min_dL', type=int, default=20, help='Минимальное горизонтальное расстояние')
    parser.add_argument('--max_dL', type=int, default=500, help='Максимальное горизонтальное расстояние')
    parser.add_argument('--step_dL', type=int, default=1, help='Шаг по горизонтальному расстоянию')
    parser.add_argument('--step_dH', type=int, default=1, help='Шаг по разнице высот')
    parser.add_argument('--height_ratio', type=float, default=0.2, 
                       help='Коэффициент для диапазона высот (±height_ratio*dL)')
    parser.add_argument('--jobs', type=int, default=None, 
                       help='Количество процессов для параллельного выполнения')
    parser.add_argument('--output', type=str, default="projectile_velocities_dataset.csv",
                       help='Имя файла для сохранения датасета')
    parser.add_argument('--checkpoint_interval', type=int, default=1000,
                       help='Интервал для сохранения контрольных точек')
    parser.add_argument('--visualize_only', action='store_true',
                       help='Только визуализировать существующий датасет')
    parser.add_argument('--projectile_types', nargs='+', default=["ARROW", "POTION"],
                       help='Типы снарядов для моделирования (по умолчанию ARROW и POTION)')

    args = parser.parse_args()

    start_time = time.time()

    if args.visualize_only:
        if os.path.exists(args.output):
            print(f"Загрузка датасета из {args.output} для визуализации...")

            pkl_file = args.output.replace('.csv', '.pkl')
            if os.path.exists(pkl_file):
                df = pd.read_pickle(pkl_file)
            else:
                df = pd.read_csv(args.output)

            visualize_dataset(df)
        else:
            print(f"Датасет {args.output} не найден.")
    else:
        df = generate_dataset_parallel(
            min_dL=args.min_dL,
            max_dL=args.max_dL,
            step_dL=args.step_dL,
            step_dH=args.step_dH,
            height_ratio=args.height_ratio,
            n_jobs=args.jobs,
            output_file=args.output,
            checkpoint_interval=args.checkpoint_interval,
            projectile_types=args.projectile_types
        )

        if df is not None:
            visualize_dataset(df)

    elapsed_time = time.time() - start_time
    print(f"Общее время выполнения: {elapsed_time:.2f} секунд ({elapsed_time/60:.2f} минут)")
