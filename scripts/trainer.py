import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from sklearn.model_selection import train_test_split, GridSearchCV, RandomizedSearchCV
from sklearn.ensemble import RandomForestRegressor, GradientBoostingRegressor, HistGradientBoostingRegressor
from sklearn.metrics import mean_squared_error, mean_absolute_error, r2_score
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import Pipeline
import xgboost as xgb
import joblib
import time
import os
import argparse
from tqdm import tqdm
import json
from datetime import datetime

def load_dataset(dataset_file):
    print(f"Загрузка датасета из {dataset_file}...")

    pkl_file = dataset_file.replace('.csv', '.pkl')
    if os.path.exists(pkl_file):
        df = pd.read_pickle(pkl_file)
    else:
        df = pd.read_csv(dataset_file)

    print(f"Размер датасета: {len(df)} записей")
    print(df.head())

    missing_values = df.isnull().sum().sum()
    if missing_values > 0:
        print(f"Обнаружено {missing_values} пропущенных значений. Удаляем строки с пропусками.")
        df = df.dropna()

    q1 = df['velocity'].quantile(0.01)
    q3 = df['velocity'].quantile(0.99)
    iqr = q3 - q1

    lower_bound = q1 - (1.5 * iqr)
    upper_bound = q3 + (1.5 * iqr)

    outliers = df[(df['velocity'] < lower_bound) | (df['velocity'] > upper_bound)]
    if len(outliers) > 0:
        print(f"Обнаружено {len(outliers)} выбросов ({len(outliers)/len(df)*100:.2f}% от общего числа).")
        print("Сохраняем выбросы для анализа...")
        outliers.to_csv('outliers.csv', index=False)

    return df

def create_features(df, add_derived_features=True):

    has_projectile_type = 'projectile_type' in df.columns

    features = ['horizontal_distance', 'height_difference', 'angle_radians']

    if has_projectile_type:

        projectile_dummies = pd.get_dummies(df['projectile_type'], prefix='projectile')

        dummy_columns = list(projectile_dummies.columns)[:-1]

        X = pd.concat([df[features], projectile_dummies[dummy_columns]], axis=1)

        print(f"Добавлены dummy-переменные для типов снарядов: {dummy_columns}")
    else:
        X = df[features].copy()

    y = df['velocity'].copy()

    if add_derived_features:

        X['height_distance_ratio'] = df['height_difference'] / df['horizontal_distance']

        X['horizontal_distance_squared'] = df['horizontal_distance'] ** 2
        X['height_difference_squared'] = df['height_difference'] ** 2
        X['horizontal_distance_sqrt'] = np.sqrt(df['horizontal_distance'])

        X['distance_angle_interaction'] = df['horizontal_distance'] * df['angle_radians']
        X['height_angle_interaction'] = df['height_difference'] * df['angle_radians']

        X['sin_angle'] = np.sin(df['angle_radians'])
        X['cos_angle'] = np.cos(df['angle_radians'])
        X['tan_angle'] = np.tan(df['angle_radians'])

        if has_projectile_type:
            for dummy in dummy_columns:
                X[f'{dummy}_distance'] = X[dummy] * df['horizontal_distance']
                X[f'{dummy}_height'] = X[dummy] * df['height_difference']

    return X, y

def get_available_models():

    models_config = {
        'RandomForest': {
            'model': RandomForestRegressor(random_state=42, n_jobs=-1),
            'params': {
                'n_estimators': [100, 200],
                'max_depth': [None, 10, 20],
                'min_samples_split': [2, 5, 10]
            }
        },
        'GradientBoosting': {
            'model': GradientBoostingRegressor(random_state=42),
            'params': {
                'n_estimators': [100, 200],
                'learning_rate': [0.01, 0.1, 0.2],
                'max_depth': [3, 5, 7]
            }
        },
        'XGBoost': {
            'model': xgb.XGBRegressor(random_state=42, n_jobs=-1),
            'params': {
                'n_estimators': [100, 200],
                'learning_rate': [0.01, 0.1, 0.2],
                'max_depth': [3, 5, 7],
                'gamma': [0, 0.1, 0.2]
            }
        },
        'HistGradientBoosting': {
            'model': HistGradientBoostingRegressor(random_state=42),
            'params': {
                'max_iter': [100, 200],
                'learning_rate': [0.01, 0.1, 0.2],
                'max_depth': [None, 5, 10],
                'min_samples_leaf': [1, 5, 10],
                'l2_regularization': [0, 0.1, 1.0]
            }
        }

    }

    return models_config

def train_and_evaluate_models(X_train, X_test, y_train, y_test, selected_models=None, models_config=None):

    if models_config is None:
        models_config = get_available_models()

    if selected_models:
        filtered_config = {}
        for model_name in selected_models:
            if model_name in models_config:
                filtered_config[model_name] = models_config[model_name]
            else:
                print(f"Предупреждение: Модель '{model_name}' не найдена и будет пропущена.")

        if not filtered_config:
            print("Ошибка: Ни одна из указанных моделей не найдена.")
            return {}

        models_config = filtered_config

    results = {}

    print("\nОценка моделей:")
    for name, config in models_config.items():
        print(f"\nОбучение {name}...")
        start_time = time.time()

        model = config['model']
        param_grid = config['params']

        search = RandomizedSearchCV(
            model,
            param_grid,
            n_iter=10,  
            cv=3,
            scoring='neg_mean_squared_error',
            n_jobs=-1,
            verbose=1,
            random_state=42
        )

        search.fit(X_train, y_train)

        best_model = search.best_estimator_

        y_pred = best_model.predict(X_test)
        mse = mean_squared_error(y_test, y_pred)
        mae = mean_absolute_error(y_test, y_pred)
        r2 = r2_score(y_test, y_pred)

        relative_error = np.mean(np.abs((y_test - y_pred) / y_test)) * 100

        max_rel_error = np.max(np.abs((y_test - y_pred) / y_test)) * 100

        error_percent = np.mean(np.abs((y_test - y_pred) / y_test) < 0.01) * 100

        elapsed_time = time.time() - start_time

        print(f"{name}:")
        print(f"  Лучшие параметры: {search.best_params_}")
        print(f"  MSE: {mse:.6f}")
        print(f"  MAE: {mae:.6f}")
        print(f"  R2: {r2:.6f}")
        print(f"  Средняя относительная ошибка: {relative_error:.2f}%")
        print(f"  Максимальная относительная ошибка: {max_rel_error:.2f}%")
        print(f"  Предсказаний с ошибкой < 1%: {error_percent:.2f}%")
        print(f"  Время обучения: {elapsed_time:.2f} секунд")

        results[name] = {
            'model': best_model,
            'mse': mse,
            'mae': mae,
            'r2': r2,
            'relative_error': relative_error,
            'max_rel_error': max_rel_error,
            'error_percent': error_percent,
            'best_params': search.best_params_,
            'training_time': elapsed_time
        }

    return results

def fine_tune_best_model(best_model_name, best_model, X_train, X_test, y_train, y_test):

    print(f"\nТонкая настройка модели {best_model_name}...")

    if best_model_name == 'RandomForest':
        param_grid = {
            'n_estimators': [200, 300, 400, 500],
            'max_depth': [None, 15, 20, 30],
            'min_samples_split': [2, 3, 5],
            'min_samples_leaf': [1, 2, 4]
        }
    elif best_model_name == 'GradientBoosting':
        param_grid = {
            'n_estimators': [200, 300, 400, 500],
            'learning_rate': [0.05, 0.07, 0.1, 0.15],
            'max_depth': [4, 5, 6, 7],
            'min_samples_split': [2, 5],
            'subsample': [0.8, 0.9, 1.0]
        }
    elif best_model_name == 'XGBoost':
        param_grid = {
            'n_estimators': [200, 300, 400, 500],
            'learning_rate': [0.03, 0.05, 0.07, 0.1],
            'max_depth': [4, 5, 6, 7],
            'min_child_weight': [1, 3, 5],
            'gamma': [0, 0.1, 0.2],
            'subsample': [0.8, 0.9, 1.0],
            'colsample_bytree': [0.8, 0.9, 1.0]
        }
    elif best_model_name == 'HistGradientBoosting':
        param_grid = {
            'max_iter': [200, 300, 400, 500],
            'learning_rate': [0.03, 0.05, 0.07, 0.1],
            'max_depth': [None, 5, 8, 10],
            'min_samples_leaf': [1, 5, 10, 20],
            'l2_regularization': [0, 0.1, 0.5, 1.0]
        }
    else:
        print(f"Для модели {best_model_name} нет предопределенной сетки параметров.")
        return best_model, {}

    start_time = time.time()

    search = RandomizedSearchCV(
        best_model,
        param_grid,
        n_iter=20,  
        cv=5,        
        scoring='neg_mean_squared_error',
        n_jobs=-1,
        verbose=2,
        random_state=42
    )

    search.fit(X_train, y_train)

    tuned_model = search.best_estimator_

    y_pred = tuned_model.predict(X_test)
    mse = mean_squared_error(y_test, y_pred)
    mae = mean_absolute_error(y_test, y_pred)
    r2 = r2_score(y_test, y_pred)

    relative_error = np.mean(np.abs((y_test - y_pred) / y_test)) * 100

    max_rel_error = np.max(np.abs((y_test - y_pred) / y_test)) * 100

    error_percent = np.mean(np.abs((y_test - y_pred) / y_test) < 0.01) * 100

    elapsed_time = time.time() - start_time

    print(f"\nРезультаты тонкой настройки модели {best_model_name}:")
    print(f"  Лучшие параметры: {search.best_params_}")
    print(f"  MSE: {mse:.6f}")
    print(f"  MAE: {mae:.6f}")
    print(f"  R2: {r2:.6f}")
    print(f"  Средняя относительная ошибка: {relative_error:.2f}%")
    print(f"  Максимальная относительная ошибка: {max_rel_error:.2f}%")
    print(f"  Предсказаний с ошибкой < 1%: {error_percent:.2f}%")
    print(f"  Время обучения: {elapsed_time:.2f} секунд")

    tuning_results = {
        'best_params': search.best_params_,
        'mse': mse,
        'mae': mae,
        'r2': r2,
        'relative_error': relative_error,
        'max_rel_error': max_rel_error,
        'error_percent': error_percent,
        'training_time': elapsed_time
    }

    return tuned_model, tuning_results

def visualize_model_performance(y_test, predictions, model_name, output_dir):

    if not os.path.exists(output_dir):
        os.makedirs(output_dir)

    plt.figure(figsize=(12, 8))

    plt.scatter(y_test, predictions, alpha=0.5)
    plt.plot([y_test.min(), y_test.max()], [y_test.min(), y_test.max()], 'r--')
    plt.xlabel('Реальная скорость')
    plt.ylabel('Предсказанная скорость')
    plt.title(f'Сравнение реальных и предсказанных скоростей ({model_name})')
    plt.grid(True)
    plt.savefig(os.path.join(output_dir, f'{model_name}_performance.png'))

    plt.figure(figsize=(12, 8))
    errors = y_test - predictions
    plt.hist(errors, bins=50, alpha=0.7)
    plt.xlabel('Ошибка (реальная - предсказанная)')
    plt.ylabel('Частота')
    plt.title(f'Распределение ошибок ({model_name})')
    plt.grid(True)
    plt.savefig(os.path.join(output_dir, f'{model_name}_errors.png'))

    plt.figure(figsize=(12, 8))
    relative_errors = np.abs(errors / y_test) * 100
    plt.hist(relative_errors, bins=50, alpha=0.7)
    plt.xlabel('Относительная ошибка (%)')
    plt.ylabel('Частота')
    plt.title(f'Распределение относительных ошибок ({model_name})')
    plt.grid(True)
    plt.axvline(x=1, color='r', linestyle='--', label='Ошибка 1%')
    plt.legend()
    plt.savefig(os.path.join(output_dir, f'{model_name}_relative_errors.png'))

    plt.figure(figsize=(14, 10))

    error_df = pd.DataFrame({
        'horizontal_distance': X_test['horizontal_distance'] if 'horizontal_distance' in X_test.columns else None,
        'height_difference': X_test['height_difference'] if 'height_difference' in X_test.columns else None,
        'relative_error': relative_errors
    })

    if 'horizontal_distance' in error_df.columns and 'height_difference' in error_df.columns:

        pivot = error_df.pivot_table(
            values='relative_error', 
            index='horizontal_distance', 
            columns='height_difference',
            aggfunc='mean'
        )

        sns.heatmap(pivot, cmap='viridis_r')
        plt.title(f'Зависимость относительной ошибки от расстояния и высоты ({model_name})')
        plt.xlabel('Разница высот')
        plt.ylabel('Горизонтальное расстояние')
        plt.savefig(os.path.join(output_dir, f'{model_name}_error_heatmap.png'))

    print(f"Визуализации сохранены в директории {output_dir}")

def analyze_feature_importance(model, X, model_name, output_dir):

    if not os.path.exists(output_dir):
        os.makedirs(output_dir)

    if hasattr(model, 'feature_importances_'):
        importances = model.feature_importances_
    else:
        print(f"Модель {model_name} не поддерживает извлечение важности признаков")
        return

    feature_importance = pd.DataFrame({
        'Feature': X.columns,
        'Importance': importances
    }).sort_values(by='Importance', ascending=False)

    plt.figure(figsize=(12, 8))
    sns.barplot(x='Importance', y='Feature', data=feature_importance)
    plt.title(f'Важность признаков ({model_name})')
    plt.tight_layout()
    plt.savefig(os.path.join(output_dir, f'{model_name}_feature_importance.png'))

    feature_importance.to_csv(os.path.join(output_dir, f'{model_name}_feature_importance.csv'), index=False)

    print(f"Анализ важности признаков сохранен в директории {output_dir}")

def train_model(dataset_file, output_dir="models", add_derived_features=True, visualize=True, 
               selected_models=None, do_fine_tune=True):

    start_time = time.time()

    if not os.path.exists(output_dir):
        os.makedirs(output_dir)

    df = load_dataset(dataset_file)

    X, y = create_features(df, add_derived_features)

    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

    print(f"Тренировочная выборка: {X_train.shape[0]} записей")
    print(f"Тестовая выборка: {X_test.shape[0]} записей")

    results = train_and_evaluate_models(X_train, X_test, y_train, y_test, selected_models)

    if not results:
        print("Ошибка: Не удалось обучить ни одну модель.")
        return None, None

    best_model_name = min(results, key=lambda x: results[x]['relative_error'])
    best_model = results[best_model_name]['model']

    print(f"\nЛучшая модель: {best_model_name} с относительной ошибкой = {results[best_model_name]['relative_error']:.2f}%")

    if do_fine_tune:
        tuned_model, tuning_results = fine_tune_best_model(best_model_name, best_model, X_train, X_test, y_train, y_test)
        final_model = tuned_model
        final_results = tuning_results
    else:
        print("\nПропускаем тонкую настройку модели, как указано в параметрах.")
        final_model = best_model
        final_results = {
            'best_params': results[best_model_name]['best_params'],
            'mse': results[best_model_name]['mse'],
            'mae': results[best_model_name]['mae'],
            'r2': results[best_model_name]['r2'],
            'relative_error': results[best_model_name]['relative_error'],
            'max_rel_error': results[best_model_name]['max_rel_error'],
            'error_percent': results[best_model_name]['error_percent'],
            'training_time': results[best_model_name]['training_time']
        }

    model_timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    model_file = os.path.join(output_dir, f"{best_model_name.lower()}_{model_timestamp}.pkl")
    joblib.dump(final_model, model_file)
    print(f"\nЛучшая модель сохранена в {model_file}")

    model_info = {
        'model_name': best_model_name,
        'model_file': model_file,
        'features': list(X.columns),
        'add_derived_features': add_derived_features,
        'train_size': X_train.shape[0],
        'test_size': X_test.shape[0],
        'initial_params': results[best_model_name]['best_params'],
        'tuned_params': final_results['best_params'] if do_fine_tune else None,
        'metrics': {
            'mse': final_results['mse'],
            'mae': final_results['mae'],
            'r2': final_results['r2'],
            'relative_error': final_results['relative_error'],
            'max_rel_error': final_results['max_rel_error'],
            'error_percent': final_results['error_percent']
        },
        'timestamp': datetime.now().isoformat()
    }

    info_file = os.path.join(output_dir, f"model_info_{model_timestamp}.json")
    with open(info_file, 'w') as f:
        json.dump(model_info, f, indent=4)

    print(f"Информация о модели сохранена в {info_file}")

    if visualize:
        y_pred = final_model.predict(X_test)
        visualize_model_performance(y_test, y_pred, best_model_name, os.path.join(output_dir, 'visualizations'))

        analyze_feature_importance(final_model, X, best_model_name, os.path.join(output_dir, 'visualizations'))

    elapsed_time = time.time() - start_time
    print(f"\nОбщее время обучения: {elapsed_time:.2f} секунд ({elapsed_time/60:.2f} минут)")

    return final_model, model_info

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Обучение модели для предсказания скоростей снарядов')
    parser.add_argument('--dataset', type=str, default="projectile_velocities_dataset.csv",
                       help='Путь к файлу датасета')
    parser.add_argument('--output', type=str, default="models",
                       help='Директория для сохранения моделей')
    parser.add_argument('--no-derived-features', action='store_false', dest='add_derived_features',
                       help='Не добавлять производные признаки')
    parser.add_argument('--no-visualize', action='store_false', dest='visualize',
                       help='Не создавать визуализации')

    parser.add_argument('--models', nargs='+', choices=['RandomForest', 'GradientBoosting', 'XGBoost', 'HistGradientBoosting'],
                       help='Список моделей для обучения (можно указать несколько)')
    parser.add_argument('--no-fine-tune', action='store_false', dest='do_fine_tune',
                       help='Не выполнять тонкую настройку лучшей модели')

    args = parser.parse_args()

    if not os.path.exists(args.dataset):
        print(f"Датасет {args.dataset} не найден.")
    else:
        train_model(
            dataset_file=args.dataset,
            output_dir=args.output,
            add_derived_features=args.add_derived_features,
            visualize=args.visualize,
            selected_models=args.models,
            do_fine_tune=args.do_fine_tune
        )
