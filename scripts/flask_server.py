import os
import sys
import msgpack
import numpy as np
import joblib
import pickle
import time
import pandas as pd
from flask import Flask, request, Response

app = Flask(__name__)

script_dir = os.path.dirname(os.path.abspath(__file__))
print(f"Script directory: {script_dir}")

model_paths = [
    os.path.join(script_dir, "models"),
    os.path.join(script_dir, "..", "models"),
    os.path.join(os.getcwd(), "models"),
    "models"
]

models_dir = None
for path in model_paths:
    if os.path.exists(path) and os.path.isdir(path):
        models_dir = path
        print(f"Found models directory: {models_dir}")
        break

if not models_dir:
    print("WARNING: Models directory not found in any of the checked locations!")
    print(f"Checked paths: {model_paths}")
    print(f"Current working directory: {os.getcwd()}")
    models_dir = os.path.join(script_dir, "models")
    os.makedirs(models_dir, exist_ok=True)
    print(f"Created models directory: {models_dir}")

models = {
    'ARROW': None,
    'TRIDENT': None,
    'POTION': None,
    'TNT': None
}

models_info = {
    'ARROW': None,
    'TRIDENT': None,
    'POTION': None,
    'TNT': None
}

MODEL_PATHS = {
    'ARROW': os.path.join(models_dir, 'arrow_trident_model.pkl'),
    'TRIDENT': os.path.join(models_dir, 'arrow_trident_model.pkl'),
    'POTION': os.path.join(models_dir, 'potion_model.pkl'),
    'TNT': os.path.join(models_dir, 'tnt_model.pkl')
}

MODEL_INFO_PATHS = {
    'ARROW': os.path.join(models_dir, 'arrow_trident_model_info.json'),
    'TRIDENT': os.path.join(models_dir, 'arrow_trident_model_info.json'),
    'POTION': os.path.join(models_dir, 'potion_model_info.json'),
    'TNT': os.path.join(models_dir, 'tnt_model_info.json')
}

def load_model(model_path, info_path, projectile_type):
    model = None
    model_info = None

    if os.path.exists(model_path):
        try:
            try:
                print(f"Попытка загрузки модели {projectile_type} через joblib из {model_path}")
                model = joblib.load(model_path)
                print(f"Модель {projectile_type} успешно загружена через joblib")
            except:
                print(f"Joblib не удалось загрузить модель, пробуем pickle")
                with open(model_path, 'rb') as f:
                    model = pickle.load(f)
                print(f"Модель {projectile_type} успешно загружена через pickle")

            if hasattr(model, 'predict'):
                print(f"Модель {projectile_type} имеет метод predict. Тип модели: {type(model).__name__}")
            else:
                print(f"ПРЕДУПРЕЖДЕНИЕ: Модель {projectile_type} не имеет метода predict!")
                print(f"Тип объекта: {type(model).__name__}")
                if hasattr(model, 'shape'):
                    print(f"Форма: {model.shape}")
                elif hasattr(model, '__len__'):
                    print(f"Длина: {len(model)}")
                model = None
        except Exception as e:
            print(f"Ошибка при загрузке модели {projectile_type}: {str(e)}")
            model = None
    else:
        print(f"Файл модели не найден: {model_path}")

    if os.path.exists(info_path):
        try:
            with open(info_path, 'r') as f:
                model_info = msgpack.loads(f.read())
            print(f"Загружена информация о модели {projectile_type}: {model_info['model_name']}")
            if 'metrics' in model_info:
                print(f"Метрики модели: R² = {model_info['metrics'].get('r2', 'N/A')}, " +
                      f"Относительная ошибка = {model_info['metrics'].get('relative_error', 'N/A')}%")
        except Exception as e:
            print(f"Ошибка при загрузке информации о модели {projectile_type}: {str(e)}")
    else:
        print(f"Файл с информацией о модели не найден: {info_path}")

    return model, model_info

def load_models():
    print("\n====== Загрузка моделей ======")
    for projectile_type in models.keys():
        model_path = MODEL_PATHS[projectile_type]
        info_path = MODEL_INFO_PATHS[projectile_type]

        model, model_info = load_model(model_path, info_path, projectile_type)
        models[projectile_type] = model
        models_info[projectile_type] = model_info
    print("====== Загрузка моделей завершена ======\n")

def create_features_for_prediction(input_data, model_info=None):
    try:
        if not isinstance(input_data, pd.DataFrame):
            input_data = pd.DataFrame([input_data])

        base_features = ['horizontal_distance', 'height_difference', 'angle_radians']

        has_projectile_type = 'projectile_type' in input_data.columns

        X = input_data[base_features].copy()

        if model_info and 'features' in model_info:
            expected_features = model_info['features']

            dummy_features = [f for f in expected_features if f.startswith('projectile_')]

            if dummy_features and has_projectile_type:
                projectile_dummies = pd.get_dummies(input_data['projectile_type'], prefix='projectile')

                for feature in dummy_features:
                    if feature not in projectile_dummies.columns:
                        projectile_dummies[feature] = 0

                X = pd.concat([X, projectile_dummies[dummy_features]], axis=1)

            derived_features = [f for f in expected_features if not f in base_features and not f in dummy_features]

            if derived_features:
                if 'height_distance_ratio' in derived_features:
                    X['height_distance_ratio'] = input_data['height_difference'] / input_data['horizontal_distance']

                if 'horizontal_distance_squared' in derived_features:
                    X['horizontal_distance_squared'] = input_data['horizontal_distance'] ** 2

                if 'height_difference_squared' in derived_features:
                    X['height_difference_squared'] = input_data['height_difference'] ** 2

                if 'horizontal_distance_sqrt' in derived_features:
                    X['horizontal_distance_sqrt'] = np.sqrt(input_data['horizontal_distance'])

                if 'distance_angle_interaction' in derived_features:
                    X['distance_angle_interaction'] = input_data['horizontal_distance'] * input_data['angle_radians']

                if 'height_angle_interaction' in derived_features:
                    X['height_angle_interaction'] = input_data['height_difference'] * input_data['angle_radians']

                if 'sin_angle' in derived_features:
                    X['sin_angle'] = np.sin(input_data['angle_radians'])

                if 'cos_angle' in derived_features:
                    X['cos_angle'] = np.cos(input_data['angle_radians'])

                if 'tan_angle' in derived_features:
                    X['tan_angle'] = np.tan(input_data['angle_radians'])

                for feature in derived_features:
                    if '_distance' in feature and feature.startswith('projectile_'):
                        dummy_feature = feature.split('_distance')[0]
                        if dummy_feature in X.columns:
                            X[feature] = X[dummy_feature] * input_data['horizontal_distance']

                    if '_height' in feature and feature.startswith('projectile_'):
                        dummy_feature = feature.split('_height')[0]
                        if dummy_feature in X.columns:
                            X[feature] = X[dummy_feature] * input_data['height_difference']

            for feature in expected_features:
                if feature not in X.columns:
                    X[feature] = 0

            X = X[expected_features]
        else:
            X['height_distance_ratio'] = input_data['height_difference'] / input_data['horizontal_distance']

            X['sin_angle'] = np.sin(input_data['angle_radians'])
            X['cos_angle'] = np.cos(input_data['angle_radians'])
            X['tan_angle'] = np.tan(input_data['angle_radians'])

            if has_projectile_type:
                projectile_dummies = pd.get_dummies(input_data['projectile_type'], prefix='projectile')
                dummy_columns = list(projectile_dummies.columns)[:-1]
                X = pd.concat([X, projectile_dummies[dummy_columns]], axis=1)

        print(f"Созданы признаки для предсказания: {X.columns.tolist()}")
        return X

    except Exception as e:
        print(f"Ошибка при создании признаков: {str(e)}")
        return input_data[['horizontal_distance', 'height_difference', 'angle_radians']]

@app.route('/health', methods=['GET'])
def health_check():
    response_data = {
        'status': 'ok',
        'models_loaded': {k: v is not None for k, v in models.items()}
    }
    return Response(msgpack.packb(response_data), mimetype='application/msgpack')

@app.route('/predict', methods=['POST'])
def predict():
    try:
        request_data = request.get_data()
        print(f"Received MessagePack request: {len(request_data)} bytes")

        try:
            data = msgpack.unpackb(request_data, raw=False)
        except Exception as e:
            print(f"Error unpacking MessagePack data: {str(e)}")
            error_response = {'error': 'Invalid MessagePack data'}
            return Response(msgpack.packb(error_response), status=400, mimetype='application/msgpack')

        print(f"Unpacked data with keys: {list(data.keys()) if isinstance(data, dict) else 'not a dict'}")

        targets = data.get('targets', [])

        if not targets:
            print("Error: No targets provided in request")
            error_response = {'error': 'No targets provided'}
            return Response(msgpack.packb(error_response), status=400, mimetype='application/msgpack')

        print(f"Processing {len(targets)} targets")

        velocities = []

        for i, target in enumerate(targets):
            try:
                print(f"Processing target {i+1}: {target}")

                horizontal_distance = target.get('horizontal_distance')
                height_difference = target.get('height_difference')
                angle_radians = target.get('angle_radians')
                projectile_type = target.get('projectile_type', 'ARROW')

                if None in [horizontal_distance, height_difference, angle_radians]:
                    print(f"Error in target {i+1}: Missing required parameters")
                    error_response = {'error': f'Missing required parameters in target {i+1}'}
                    return Response(msgpack.packb(error_response), status=400, mimetype='application/msgpack')

                print(f"Target {i+1} data: distance={horizontal_distance}, height={height_difference}, angle={angle_radians}, type={projectile_type}")

                if models[projectile_type] is not None and hasattr(models[projectile_type], 'predict'):
                    try:
                        X = np.array([[float(horizontal_distance), float(height_difference), float(angle_radians)]])
                        print(f"Model input features for target {i+1}: {X}")

                        velocity = float(models[projectile_type].predict(X)[0])
                        print(f"Model prediction for target {i+1}: {velocity}")
                    except Exception as e:
                        print(f"Error during model prediction for target {i+1}: {str(e)}")
                        velocity = estimate_velocity(horizontal_distance, height_difference, projectile_type)
                        print(f"Falling back to estimate for target {i+1}: {velocity}")
                else:
                    print(f"No valid model for {projectile_type}, using estimate")
                    velocity = estimate_velocity(horizontal_distance, height_difference, projectile_type)
                    print(f"Estimated velocity for target {i+1}: {velocity}")

                velocities.append(velocity)

            except Exception as e:
                print(f"Error processing target {i+1}: {str(e)}")
                error_response = {'error': f'Error processing target {i+1}: {str(e)}'}
                return Response(msgpack.packb(error_response), status=500, mimetype='application/msgpack')

        print(f"Returning velocities: {velocities}")
        response_data = {'velocities': velocities}
        return Response(msgpack.packb(response_data), mimetype='application/msgpack')

    except Exception as e:
        import traceback
        print(f"Unexpected error: {str(e)}")
        print(traceback.format_exc())
        error_response = {'error': str(e)}
        return Response(msgpack.packb(error_response), status=500, mimetype='application/msgpack')

def estimate_velocity(horizontal_distance, height_difference, projectile_type):
    try:
        horizontal_distance = float(horizontal_distance)
        height_difference = float(height_difference)

        if horizontal_distance <= 0:
            print(f"Warning: Invalid horizontal distance: {horizontal_distance}, using default value")
            horizontal_distance = 1.0

        base_velocity = np.sqrt(horizontal_distance) * 0.2

        if height_difference > 0:
            base_velocity *= (1 + height_difference / horizontal_distance * 0.7)
        elif height_difference < 0:
            base_velocity *= (1 - abs(height_difference) / horizontal_distance * 0.3)

        projectile_type = projectile_type.upper() if isinstance(projectile_type, str) else "ARROW"

        if projectile_type == "POTION":
            base_velocity *= 0.95
        elif projectile_type == "TNT":
            base_velocity *= 1.0
        elif projectile_type == "TRIDENT":
            base_velocity *= 0.95

        if base_velocity < 0.1:
            base_velocity = 0.1
        elif base_velocity > 10.0:
            base_velocity = 10.0

        return base_velocity

    except Exception as e:
        print(f"Error in estimate_velocity: {str(e)}")
        return 1.0

if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description='Artillery prediction server')
    parser.add_argument('--models-dir', type=str, help='Path to models directory')
    parser.add_argument('--port', type=int, default=5000, help='Port to run the server on')
    parser.add_argument('--create-models', action='store_true', help='Create simple models if none are found')
    args = parser.parse_args()

    if args.models_dir:
        print(f"Using models directory from command line: {args.models_dir}")
        models_dir = args.models_dir

        MODEL_PATHS = {
            'ARROW': os.path.join(models_dir, 'arrow_trident_model.pkl'),
            'TRIDENT': os.path.join(models_dir, 'arrow_trident_model.pkl'),
            'POTION': os.path.join(models_dir, 'potion_model.pkl'),
            'TNT': os.path.join(models_dir, 'tnt_model.pkl')
        }

    model_files_exist = all(os.path.exists(path) for path in MODEL_PATHS.values())

    if not model_files_exist:
        print("Not all model files exist. Checking required libraries...")
        try:
            import sklearn
            print(f"sklearn version: {sklearn.__version__}")
            print("sklearn is available, can create fallback models if needed")
        except ImportError:
            print("sklearn is not installed - fallback models will not be available")

    load_models()

    all_models_loaded = all(model is not None for model in models.values())

    if not all_models_loaded and args.create_models:
        create_fallback_models()

    print(f"Starting Flask server on port {args.port}")
    app.run(host='0.0.0.0', port=args.port, debug=False)