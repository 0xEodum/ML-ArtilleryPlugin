"""
Проверка аналитической модели по РЕАЛЬНЫМ записям полёта из игры.

Читает CSV, которые пишет плагин TrajectoryRecorder
(plugins/TrajectoryRecorder/trajectories/*.csv), и отвечает на два вопроса:

  1. Какие константы физики на самом деле в вашей версии игры?
     Они восстанавливаются прямо из записи, без подгонки:
         d = vx[n+1] / vx[n]            -> drag = 1 - d
         g выводится из вертикальной компоненты
     Порядок операций в тике определяется сравнением фактического смещения
     за тик со скоростью на этом тике.

  2. Насколько точно замкнутая формула воспроизводит запись?
     Траектория предсказывается от первой точки до конца и сравнивается
     поточечно.

Использование:
    python3 scripts/verify_recording.py путь/к/trajectories/*.csv
    python3 scripts/verify_recording.py --self-check      # проверка самого скрипта

Скрипт не требует ничего, кроме стандартной библиотеки.
"""

from __future__ import annotations

import csv
import glob
import math
import os
import statistics
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from ballistics import TYPES, Ballistics, ReferenceSimulator  # noqa: E402


# --------------------------------------------------------------------------
# Чтение записи
# --------------------------------------------------------------------------

class Recording:
    def __init__(self, path, rows):
        self.path = path
        self.rows = rows
        self.name = os.path.basename(path)

    @property
    def projectile_type(self):
        value = self.rows[0].get("projectile_type", "").strip().upper()
        return value if value in TYPES else "TNT"

    def column(self, key):
        return [float(r[key]) for r in self.rows]


def load(path):
    with open(path, newline="", encoding="utf-8") as handle:
        rows = [r for r in csv.DictReader(handle)]
    required = ("x", "y", "z", "vx", "vy", "vz")
    rows = [r for r in rows if all(r.get(k) not in (None, "") for k in required)]
    if len(rows) < 8:
        return None
    return Recording(path, rows)


# --------------------------------------------------------------------------
# Восстановление констант из записи
# --------------------------------------------------------------------------

def estimate_drag(rec):
    """
    drag = 1 - vx[n+1]/vx[n].

    Горизонтальная скорость затухает чисто геометрически (гравитация на неё
    не действует), поэтому отношение соседних значений даёт d напрямую.
    Берём медиану, чтобы отдельные выбросы не портили оценку.
    """
    ratios = []
    for axis in ("vx", "vz"):
        values = rec.column(axis)
        for a, b in zip(values, values[1:]):
            if abs(a) > 1e-4:
                ratios.append(b / a)
    if not ratios:
        return None
    return 1.0 - statistics.median(ratios)


def estimate_gravity(rec, drag, gravity_before_move):
    """
    Стрела:  v[n+1].y = d*v[n].y - g      ->  g = d*v[n].y - v[n+1].y
    Динамит: v[n+1].y = d*(v[n].y - g)    ->  g = v[n].y - v[n+1].y/d
    """
    d = 1.0 - drag
    values = rec.column("vy")
    estimates = []
    for a, b in zip(values, values[1:]):
        if gravity_before_move:
            estimates.append(a - b / d)
        else:
            estimates.append(d * a - b)
    if not estimates:
        return None
    return statistics.median(estimates)


def detect_order(rec, drag):
    """
    Определяет, применяется ли гравитация до перемещения.

    Смещение за тик равно скорости, использованной для перемещения:
        стрела:  dy[n] = vy[n]
        динамит: dy[n] = vy[n] - g
    Проверяем обе гипотезы и возвращаем ту, что описывает запись точнее,
    вместе с величиной невязки каждой.
    """
    y = rec.column("y")
    vy = rec.column("vy")
    dy = [b - a for a, b in zip(y, y[1:])]

    scores = {}
    for gravity_before_move in (False, True):
        g = estimate_gravity(rec, drag, gravity_before_move)
        if g is None:
            continue
        offset = g if gravity_before_move else 0.0
        residuals = [abs(step - (v - offset)) for step, v in zip(dy, vy)]
        scores[gravity_before_move] = statistics.median(residuals)

    if not scores:
        return None, {}
    best = min(scores, key=scores.get)
    return best, scores


# --------------------------------------------------------------------------
# Сравнение записи с замкнутой формулой
# --------------------------------------------------------------------------

def replay_error(rec, ballistics):
    """
    Предсказывает всю траекторию от первой точки записи и возвращает
    (макс. ошибка, средняя ошибка, число сравненных точек) в блоках.
    """
    x = rec.column("x")
    y = rec.column("y")
    z = rec.column("z")
    v0 = (float(rec.rows[0]["vx"]), float(rec.rows[0]["vy"]), float(rec.rows[0]["vz"]))

    u0 = ballistics.initial_step_velocity(*v0)
    errors = []
    for n in range(len(x)):
        dx, dy, dz = ballistics.displacement_at(u0, n)
        errors.append(math.dist((x[0] + dx, y[0] + dy, z[0] + dz),
                                (x[n], y[n], z[n])))
    return max(errors), sum(errors) / len(errors), len(errors)


def shift_velocities(rec, shift):
    """
    Новая запись, в которой к позиции строки i приписана скорость строки
    i+shift. Строки, для которых пары нет, отбрасываются.
    """
    rows = []
    for i in range(len(rec.rows)):
        j = i + shift
        if j < 0 or j >= len(rec.rows):
            continue
        row = dict(rec.rows[i])
        for key in ("vx", "vy", "vz"):
            row[key] = rec.rows[j][key]
        rows.append(row)
    return Recording(rec.path, rows)


def best_alignment(rec, ballistics):
    """
    Запись ведёт отдельная задача планировщика, поэтому выборка скорости может
    быть сдвинута на тик относительно позиции. Пробуем сдвиги -1, 0, +1 и
    возвращаем лучший вместе с величиной сдвига.
    """
    best = None
    for shift in (0, 1, -1):
        candidate = shift_velocities(rec, shift)
        if len(candidate.rows) < 8:
            continue
        result = replay_error(candidate, ballistics)
        if best is None or result[0] < best[0][0]:
            best = (result, shift)
    return best


# --------------------------------------------------------------------------
# Отчёт
# --------------------------------------------------------------------------

def report(paths):
    print(f"{'файл':<44} {'тип':<8} {'drag':>8} {'g':>8} {'порядок':>10} "
          f"{'макс.ошиб':>10} {'сред.ошиб':>10}")
    print("-" * 104)

    canonical_ok = 0
    total = 0
    for path in paths:
        rec = load(path)
        if rec is None:
            print(f"{os.path.basename(path):<44} слишком короткая запись, пропущена")
            continue
        total += 1

        drag = estimate_drag(rec)
        if drag is None or not (0.0 < drag < 1.0):
            print(f"{rec.name:<44} не удалось оценить drag "
                  f"(горизонтальная скорость почти нулевая?)")
            continue

        gravity_before_move, scores = detect_order(rec, drag)
        gravity = estimate_gravity(rec, drag, gravity_before_move)

        canonical = TYPES[rec.projectile_type]
        measured = Ballistics("MEASURED", gravity=gravity, drag=drag,
                              gravity_before_move=gravity_before_move)

        (max_err, mean_err, _), shift = best_alignment(rec, canonical)

        order = "g->move" if gravity_before_move else "move->g"
        print(f"{rec.name[:44]:<44} {rec.projectile_type:<8} "
              f"{drag:>8.5f} {gravity:>8.5f} {order:>10} "
              f"{max_err:>10.4f} {mean_err:>10.4f}"
              + ("" if shift == 0 else f"  (сдвиг скорости {shift:+d})"))

        matches = (abs(drag - canonical.drag) < 5e-4
                   and abs(gravity - canonical.gravity) < 5e-4
                   and gravity_before_move == canonical.gravity_before_move)
        if matches:
            canonical_ok += 1
        else:
            print(f"{'':<44} ВНИМАНИЕ: расходится с эталоном "
                  f"({canonical.name}: drag={canonical.drag}, g={canonical.gravity}, "
                  f"{'g->move' if canonical.gravity_before_move else 'move->g'}). "
                  f"Пропишите измеренные значения в config.yml -> physics.")
        _ = measured

    print("-" * 104)
    if total:
        print(f"Совпало с эталонными константами: {canonical_ok} из {total} записей")


# --------------------------------------------------------------------------
# Самопроверка на синтетических данных
# --------------------------------------------------------------------------

def self_check():
    """
    Генерирует записи в точном формате TrajectoryRecorder из эталонного
    потикового симулятора и проверяет, что скрипт восстанавливает исходные
    константы. Проверяет сам скрипт, а не игру.
    """
    import tempfile

    print("Самопроверка: синтетические записи из эталонного симулятора\n")
    ok = True
    with tempfile.TemporaryDirectory() as tmp:
        paths = []
        for name, b in TYPES.items():
            speed, angle = 2.5, math.radians(45.0)
            # повторяем цикл симулятора, попутно записывая скорость
            g, c = b.gravity, b.drag
            vx, vy, vz = speed * math.cos(angle), speed * math.sin(angle), 0.0
            x = y = z = 0.0
            rows = []
            for tick in range(160):
                rows.append((tick, x, y, z, vx, vy, vz))
                if b.gravity_before_move:
                    vy -= g
                    x += vx
                    y += vy
                    z += vz
                    vx, vy, vz = vx * (1 - c), vy * (1 - c), vz * (1 - c)
                else:
                    x += vx
                    y += vy
                    z += vz
                    vx, vy, vz = vx * (1 - c), vy * (1 - c), vz * (1 - c)
                    vy -= g

            path = os.path.join(tmp, f"{name}_synthetic_speed_2.50_angle_45.0.csv")
            with open(path, "w", newline="", encoding="utf-8") as handle:
                handle.write("tick,x,y,z,vx,vy,vz,horizontal_distance,"
                             "height_difference,relative_z,velocity,angle_radians,"
                             "projectile_type\n")
                for tick, px, py, pz, wx, wy, wz in rows:
                    handle.write(f"{tick},{px:.6f},{py:.6f},{pz:.6f},"
                                 f"{wx:.6f},{wy:.6f},{wz:.6f},"
                                 f"{math.hypot(px, pz):.6f},{py:.6f},{pz:.6f},"
                                 f"2.500000,{angle:.6f},{name}\n")
            paths.append(path)

            rec = load(path)
            drag = estimate_drag(rec)
            order, _ = detect_order(rec, drag)
            gravity = estimate_gravity(rec, drag, order)
            max_err, _, _ = replay_error(rec, b)

            drag_ok = abs(drag - b.drag) < 1e-6
            grav_ok = abs(gravity - b.gravity) < 1e-6
            order_ok = order == b.gravity_before_move
            # Рекордер пишет координаты с шестью знаками, так что ошибка
            # воспроизведения упирается в округление файла, а не в формулу.
            replay_ok = max_err < 2e-5
            ok &= drag_ok and grav_ok and order_ok and replay_ok
            print(f"   {name:8s} drag {drag:.6f} {'OK' if drag_ok else 'FAIL'}   "
                  f"g {gravity:.6f} {'OK' if grav_ok else 'FAIL'}   "
                  f"порядок {'g->move' if order else 'move->g'} "
                  f"{'OK' if order_ok else 'FAIL'}   "
                  f"воспроизведение {max_err:.2e} {'OK' if replay_ok else 'FAIL'}")

        print()
        report(paths)

    print()
    print("Самопроверка пройдена." if ok else "САМОПРОВЕРКА НЕ ПРОЙДЕНА.")
    return 0 if ok else 1


def main(argv):
    if not argv or argv[0] == "--self-check":
        return self_check()

    paths = []
    for pattern in argv:
        paths.extend(sorted(glob.glob(pattern)))
    if not paths:
        print("Файлы не найдены. Укажите путь к CSV из TrajectoryRecorder, "
              "например plugins/TrajectoryRecorder/trajectories/*.csv")
        return 1
    report(paths)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
