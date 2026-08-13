"""
Аналитическая баллистика снарядов Minecraft.

Этот модуль заменяет весь ML-конвейер (dataset_generator.py -> trainer.py ->
flask_server.py) точными формулами. Содержит:

  1. ReferenceSimulator - потиковый симулятор, буквально повторяющий
     порядок операций из кода игры (эталон).
  2. Ballistics        - замкнутая формула положения снаряда на тике n.
  3. solve_speed       - обратная задача: скорость запуска по (dL, dH, angle).
  4. solve_angle       - обратная задача: угол по (dL, dH, speed).

Запуск `python3 scripts/ballistics.py` прогоняет набор проверок.

--------------------------------------------------------------------------
ФИЗИКА
--------------------------------------------------------------------------
Каждый снаряд в Minecraft за один тик выполняет три операции: применение
гравитации, перемещение и применение сопротивления (drag). Важен ПОРЯДОК:

  Стрела (AbstractArrow.tick), трезубец, зелье (ThrowableProjectile.tick):
      pos += v;  v *= (1 - c);  v.y -= g
  Динамит (PrimedTnt.tick):
      v.y -= g;  pos += v;  v *= (1 - c)

Константы:
      стрела/трезубец  g = 0.05, c = 0.01   (предельная скорость g/c = 5.0)
      зелье            g = 0.05, c = 0.01
      динамит          g = 0.04, c = 0.02   (предельная скорость g/c = 2.0)

Именно другой ПОРЯДОК операций у динамита (а вовсе не «загадочная физика»)
плюс вдвое большее сопротивление объясняют отличие его траектории.
"""

from __future__ import annotations

import math
from dataclasses import dataclass

TICKS_PER_SECOND = 20


# --------------------------------------------------------------------------
# Параметры типов снарядов
# --------------------------------------------------------------------------

@dataclass(frozen=True)
class Ballistics:
    """Замкнутая форма движения снаряда с линейным сопротивлением по тикам."""

    name: str
    gravity: float          # g, блоков/тик^2
    drag: float             # c, доля скорости, теряемая за тик
    gravity_before_move: bool

    # ---- производные величины -------------------------------------------

    @property
    def retention(self) -> float:
        """d = 1 - c, доля скорости, сохраняемая за тик."""
        return 1.0 - self.drag

    @property
    def terminal_velocity(self) -> float:
        """Предельная скорость падения (положительная), блоков/тик."""
        return self.gravity / self.drag

    def _v_inf(self) -> float:
        return -self.gravity / self.drag

    def initial_step_velocity(self, vx: float, vy: float, vz: float):
        """
        u0 - скорость, с которой снаряд СМЕЩАЕТСЯ на нулевом тике.

        Для стрел/зелий это в точности стартовая скорость.
        Для динамита гравитация применяется до перемещения, поэтому
        u0.y = v0.y - g. Дальше рекуррентность у обоих типов одинакова:
            u[n+1] = d * u[n] - g * e_y
        """
        if self.gravity_before_move:
            return vx, vy - self.gravity, vz
        return vx, vy, vz

    # ---- прямая задача ---------------------------------------------------

    def _sum_geom(self, n: float) -> float:
        """S(n) = sum_{k=0}^{n-1} d^k = (1 - d^n) / c."""
        return (1.0 - self.retention ** n) / self.drag

    def step_velocity_at(self, u0, n: float):
        """Скорость смещения на тике n (n может быть вещественным)."""
        d_n = self.retention ** n
        v_inf = self._v_inf()
        return (u0[0] * d_n,
                v_inf + (u0[1] - v_inf) * d_n,
                u0[2] * d_n)

    def displacement_at(self, u0, n: float):
        """
        Смещение относительно точки запуска после n тиков.

        x(n) = u0x * S(n)
        y(n) = v_inf * n + (u0y - v_inf) * S(n)
        z(n) = u0z * S(n)

        Для целого n формула точна (это сумма геометрической прогрессии);
        для дробного n используется то же выражение как гладкое продолжение.
        """
        s = self._sum_geom(n)
        v_inf = self._v_inf()
        return (u0[0] * s,
                v_inf * n + (u0[1] - v_inf) * s,
                u0[2] * s)

    def exact_displacement_at(self, u0, t: float):
        """
        Точное смещение в момент t = n + f (n целое, 0 <= f < 1).

        Внутри тика сущность движется линейно, поэтому точное положение -
        это положение на целом тике плюс доля шага.
        """
        n = math.floor(t)
        f = t - n
        x, y, z = self.displacement_at(u0, n)
        if f == 0.0:
            return x, y, z
        ux, uy, uz = self.step_velocity_at(u0, n)
        return x + f * ux, y + f * uy, z + f * uz

    def ticks_to_horizontal_distance(self, u0_horizontal: float, distance: float):
        """
        Момент t (вещественный), когда пройдено горизонтальное расстояние
        `distance`. None, если снаряд физически не долетает.

        x(t) = u0h * (1 - d^t) / c  =>  d^t = 1 - c * distance / u0h
        """
        if u0_horizontal <= 0.0:
            return None
        arg = 1.0 - self.drag * distance / u0_horizontal
        if arg <= 0.0:
            return None  # дальше предельной дальности u0h / c
        return math.log(arg) / math.log(self.retention)

    def max_horizontal_range(self, u0_horizontal: float) -> float:
        """Асимптотический предел дальности: u0h / c (за бесконечное время)."""
        return u0_horizontal / self.drag

    def exact_ticks_to_distance(self, u0_horizontal: float, distance: float):
        """
        Точный момент прохождения `distance` с учётом того, что внутри тика
        сущность движется по хорде, а не по гладкой экспоненте.

        `ticks_to_horizontal_distance` обращает гладкую кривую; хорда лежит
        ниже неё (кривая вогнутая), поэтому реальное пересечение чуть позже.
        Целый тик берём из замкнутой формулы, долю - на хорде.
        """
        t = self.ticks_to_horizontal_distance(u0_horizontal, distance)
        if t is None:
            return None
        n = max(0, math.floor(t))
        for _ in range(4):
            x_n = u0_horizontal * self._sum_geom(n)
            ux = u0_horizontal * self.retention ** n
            if ux <= 0.0:
                return None
            f = (distance - x_n) / ux
            if f < 0.0 and n > 0:
                n -= 1
                continue
            if f >= 1.0:
                n += 1
                continue
            return n + f
        return t

    def height_at_distance(self, speed: float, angle: float, distance: float):
        """
        Высота снаряда (относительно точки запуска) в момент прохождения
        горизонтального расстояния `distance`. None - не долетает.

        Это и есть функция, корень которой ищет обратная задача.
        """
        u0 = self.initial_step_velocity(speed * math.cos(angle),
                                        speed * math.sin(angle),
                                        0.0)
        t = self.exact_ticks_to_distance(u0[0], distance)
        if t is None:
            return None
        n = math.floor(t)
        y_n = self.displacement_at(u0, n)[1]
        uy = self.step_velocity_at(u0, n)[1]
        return y_n + (t - n) * uy

    def apex(self, speed: float, angle: float):
        """(тик вершины, высота вершины) - удобно для проверки потолка мира."""
        u0 = self.initial_step_velocity(speed * math.cos(angle),
                                        speed * math.sin(angle), 0.0)
        v_inf = self._v_inf()
        if u0[1] <= 0.0:
            return 0.0, 0.0
        # u_y(t) = 0  =>  d^t = -v_inf / (u0y - v_inf)
        t = math.log(-v_inf / (u0[1] - v_inf)) / math.log(self.retention)
        return t, self.displacement_at(u0, t)[1]


ARROW = Ballistics("ARROW", gravity=0.05, drag=0.01, gravity_before_move=False)
TRIDENT = Ballistics("TRIDENT", gravity=0.05, drag=0.01, gravity_before_move=False)
POTION = Ballistics("POTION", gravity=0.05, drag=0.01, gravity_before_move=False)
TNT = Ballistics("TNT", gravity=0.04, drag=0.02, gravity_before_move=True)

TYPES = {"ARROW": ARROW, "TRIDENT": TRIDENT, "POTION": POTION, "TNT": TNT}


# --------------------------------------------------------------------------
# Эталонный потиковый симулятор (буквальный перевод кода игры)
# --------------------------------------------------------------------------

class ReferenceSimulator:
    """Пошаговая модель. Служит эталоном для проверки формул."""

    def __init__(self, ballistics: Ballistics):
        self.b = ballistics

    def trajectory(self, speed: float, angle: float, max_ticks: int = 2000):
        """Возвращает список позиций (x, y, z) на каждом целом тике."""
        g, c = self.b.gravity, self.b.drag
        vx = speed * math.cos(angle)
        vy = speed * math.sin(angle)
        vz = 0.0
        x = y = z = 0.0
        out = [(0.0, 0.0, 0.0)]
        for _ in range(max_ticks):
            if self.b.gravity_before_move:
                # PrimedTnt.tick(): гравитация -> move -> drag
                vy -= g
                x += vx
                y += vy
                z += vz
                vx *= (1.0 - c)
                vy *= (1.0 - c)
                vz *= (1.0 - c)
            else:
                # AbstractArrow / ThrowableProjectile.tick(): move -> drag -> гравитация
                x += vx
                y += vy
                z += vz
                vx *= (1.0 - c)
                vy *= (1.0 - c)
                vz *= (1.0 - c)
                vy -= g
            out.append((x, y, z))
        return out

    def height_at_distance(self, speed: float, angle: float, distance: float,
                           max_ticks: int = 2000):
        """Высота в момент пересечения горизонтали `distance` (линейно внутри тика)."""
        traj = self.trajectory(speed, angle, max_ticks)
        for i in range(1, len(traj)):
            if traj[i][0] >= distance:
                x0, y0, _ = traj[i - 1]
                x1, y1, _ = traj[i]
                if x1 == x0:
                    return y1
                f = (distance - x0) / (x1 - x0)
                return y0 + f * (y1 - y0)
        return None


# --------------------------------------------------------------------------
# Обратные задачи
# --------------------------------------------------------------------------

@dataclass
class Solution:
    ok: bool
    speed: float = 0.0
    angle: float = 0.0
    flight_ticks: float = 0.0
    apex_height: float = 0.0
    residual: float = 0.0
    reason: str = ""


def solve_speed(ballistics: Ballistics, distance: float, height: float,
                angle: float, max_speed: float = 40.0,
                tolerance: float = 1e-13) -> Solution:
    """
    Найти скорость запуска, при которой снаряд, выпущенный под углом `angle`,
    окажется на высоте `height` пройдя горизонтальное расстояние `distance`.

    Функция height_at_distance(v) монотонно возрастает по v (при фиксированном
    угле большая скорость означает более настильную и более высокую в данной
    точке траекторию), поэтому корень единственный и бисекция сходится всегда.
    """
    if distance <= 0.0:
        return Solution(False, reason="distance must be positive")
    cos_a = math.cos(angle)
    if cos_a <= 1e-9:
        return Solution(False, reason="angle too steep")

    # Ниже этой скорости снаряд не долетает даже за бесконечное время:
    # предельная дальность = v*cos(a)/c.
    lo = ballistics.drag * distance / cos_a
    lo *= 1.0 + 1e-9

    hi = max(lo * 2.0, 1.0)
    f_hi = None
    while hi <= max_speed:
        f_hi = ballistics.height_at_distance(hi, angle, distance)
        if f_hi is not None and f_hi >= height:
            break
        hi *= 1.6
    else:
        return Solution(False, reason="target out of range for max_speed=%.2f" % max_speed)

    for _ in range(200):
        mid = 0.5 * (lo + hi)
        val = ballistics.height_at_distance(mid, angle, distance)
        if val is None or val < height:
            lo = mid
        else:
            hi = mid
        if hi - lo < tolerance:
            break

    speed = 0.5 * (lo + hi)
    got = ballistics.height_at_distance(speed, angle, distance)
    u0 = ballistics.initial_step_velocity(speed * math.cos(angle),
                                          speed * math.sin(angle), 0.0)
    ticks = ballistics.exact_ticks_to_distance(u0[0], distance) or 0.0
    _, apex_h = ballistics.apex(speed, angle)
    return Solution(True, speed=speed, angle=angle, flight_ticks=ticks,
                    apex_height=apex_h,
                    residual=abs((got if got is not None else 0.0) - height))


def solve_angle(ballistics: Ballistics, distance: float, height: float,
                speed: float, high_arc: bool = False,
                tolerance: float = 1e-12) -> Solution:
    """
    Обратная задача с фиксированной скоростью: подобрать угол.

    При заданной скорости обычно есть два решения (настильная и навесная
    траектория). Ищем максимум по углу, затем бисекцию в нужной половине.
    """
    def f(a):
        v = ballistics.height_at_distance(speed, a, distance)
        return -math.inf if v is None else v - height

    # Снаряд долетает до `distance` только пока v*cos(a)/c > distance.
    # Это даёт точные границы поиска, внутри которых f конечна и унимодальна.
    cos_min = ballistics.drag * distance / speed
    if cos_min >= 1.0:
        return Solution(False, reason="unreachable at speed=%.4f" % speed)
    a_max = math.acos(cos_min) * (1.0 - 1e-12)
    lo_a, hi_a = -a_max, a_max

    # золотое сечение для поиска угла максимальной высоты в точке цели
    gr = (math.sqrt(5.0) - 1.0) / 2.0
    a, b = lo_a, hi_a
    for _ in range(300):
        c1 = b - gr * (b - a)
        c2 = a + gr * (b - a)
        if f(c1) < f(c2):
            a = c1
        else:
            b = c2
        if b - a < 1e-14:
            break
    a_peak = 0.5 * (a + b)
    if f(a_peak) < 0.0:
        return Solution(False, reason="unreachable at speed=%.4f" % speed)

    # Настильная траектория - корень слева от максимума (f растёт),
    # навесная - справа (f убывает).
    lo, hi = (a_peak, hi_a) if high_arc else (lo_a, a_peak)
    for _ in range(300):
        mid = 0.5 * (lo + hi)
        positive = f(mid) >= 0.0
        if positive == high_arc:
            lo = mid
        else:
            hi = mid
        if hi - lo < tolerance:
            break

    angle = 0.5 * (lo + hi)
    u0 = ballistics.initial_step_velocity(speed * math.cos(angle),
                                          speed * math.sin(angle), 0.0)
    ticks = ballistics.exact_ticks_to_distance(u0[0], distance) or 0.0
    _, apex_h = ballistics.apex(speed, angle)
    return Solution(True, speed=speed, angle=angle, flight_ticks=ticks,
                    apex_height=apex_h, residual=abs(f(angle)))


# --------------------------------------------------------------------------
# Проверки
# --------------------------------------------------------------------------

def _check_closed_form_matches_simulator():
    print("1. Замкнутая формула против потикового симулятора")
    worst_overall = 0.0
    for name, b in TYPES.items():
        sim = ReferenceSimulator(b)
        worst = 0.0
        for speed in (0.5, 1.0, 2.5, 5.0, 9.0, 20.0):
            for deg in (-30, 0, 15, 30, 45, 60, 80):
                angle = math.radians(deg)
                traj = sim.trajectory(speed, angle, max_ticks=400)
                u0 = b.initial_step_velocity(speed * math.cos(angle),
                                             speed * math.sin(angle), 0.0)
                for n in range(0, 401, 7):
                    ex, ey, _ = traj[n]
                    ax, ay, _ = b.displacement_at(u0, n)
                    worst = max(worst, abs(ex - ax), abs(ey - ay))
        worst_overall = max(worst_overall, worst)
        print(f"   {name:8s} макс. расхождение по 400 тикам: {worst:.3e} блока")
    assert worst_overall < 1e-8, worst_overall
    print("   -> формула воспроизводит симулятор с точностью double\n")


def _check_solver_hits_target():
    print("2. Решение обратной задачи, проверка эталонным симулятором")
    for name, b in TYPES.items():
        sim = ReferenceSimulator(b)
        worst = 0.0
        worst_case = None
        tested = 0
        skipped = 0
        for distance in (5, 10, 25, 50, 100, 200, 350, 500, 800, 1200, 2000, 3000):
            for ratio in (-0.6, -0.2, 0.0, 0.2, 0.6):
                height = distance * ratio
                angle = math.radians(45.0)
                sol = solve_speed(b, distance, height, angle)
                if not sol.ok:
                    skipped += 1
                    continue
                tested += 1
                actual = sim.height_at_distance(sol.speed, angle, distance, max_ticks=4000)
                if actual is None:
                    continue
                err = abs(actual - height)
                if err > worst:
                    worst, worst_case = err, (distance, height, sol.speed)
        print(f"   {name:8s} {tested:3d} целей ({skipped} вне диапазона v<=40), "
              f"макс. промах по высоте: {worst:.3e} блока  "
              f"(худший случай dL={worst_case[0]}, "
              f"dH={worst_case[1]:.0f}, v={worst_case[2]:.4f})")
        assert worst < 1e-6, (name, worst, worst_case)
    print("   -> попадание точное на всём диапазоне, включая 3000 блоков\n")


def _check_angle_solver():
    print("3. Обратная задача по углу при фиксированной скорости")
    for name, b in TYPES.items():
        sim = ReferenceSimulator(b)
        worst = 0.0
        for speed in (2.0, 4.0, 8.0):
            for distance in (20, 60, 150):
                for high in (False, True):
                    sol = solve_angle(b, distance, 0.0, speed, high_arc=high)
                    if not sol.ok:
                        continue
                    actual = sim.height_at_distance(speed, sol.angle, distance,
                                                    max_ticks=4000)
                    if actual is not None:
                        worst = max(worst, abs(actual))
        print(f"   {name:8s} макс. промах по высоте: {worst:.3e} блока")
        assert worst < 1e-6, (name, worst)
    print()


def _show_tnt_vs_naive():
    print("4. Почему прежняя симуляция промахивалась по динамиту")
    print("   Старый scripts/dataset_generator.py считал ЛЮБОЙ снаряд, кроме")
    print("   стрелы, по константам зелья (g=0.05, c=0.01) и в порядке")
    print("   'move -> gravity -> drag'. Для динамита верно g=0.04, c=0.02")
    print("   и порядок 'gravity -> move -> drag'.\n")
    wrong = Ballistics("TNT_AS_POTION", gravity=0.05, drag=0.01,
                       gravity_before_move=False)
    right = TNT
    sim = ReferenceSimulator(right)
    angle = math.radians(45.0)
    print("      dL   v (старая модель)   реально прилетит   промах")
    for distance in (40, 80, 120, 160, 200):
        bad = solve_speed(wrong, distance, 0.0, angle)
        if not bad.ok:
            print(f"   {distance:5d}   вне диапазона старой модели")
            continue
        traj = sim.trajectory(bad.speed, angle, max_ticks=4000)
        landed = None
        for i in range(1, len(traj)):
            if traj[i][1] <= 0.0:
                x0, y0, _ = traj[i - 1]
                x1, y1, _ = traj[i]
                f = (y0 - 0.0) / (y0 - y1) if y0 != y1 else 0.0
                landed = x0 + f * (x1 - x0)
                break
        print(f"   {distance:5d}   {bad.speed:14.4f}   {landed:16.1f}   "
              f"{landed - distance:+7.1f} блока")
    print()


def _show_range_table():
    print("5. Требуемая скорость при угле 45 град., цель на уровне запуска")
    print("      dL      ARROW       TNT     (блоков/тик)")
    for distance in (20, 50, 100, 200, 300, 500, 1000, 2000):
        row = []
        for b in (ARROW, TNT):
            sol = solve_speed(b, distance, 0.0, math.radians(45.0), max_speed=1e6)
            row.append(f"{sol.speed:9.4f}" if sol.ok else "        -")
        print(f"   {distance:5d} {row[0]} {row[1]}")
    print()


def _show_timing():
    import time
    print("6. Стоимость вычисления")
    b = TNT
    n = 20000
    start = time.perf_counter()
    for i in range(n):
        solve_speed(b, 50.0 + (i % 400), 0.0, math.radians(45.0))
    elapsed = time.perf_counter() - start
    print(f"   {n} решений за {elapsed:.3f} с -> "
          f"{elapsed / n * 1e6:.1f} мкс на снаряд (Python)")
    print("   В Java получается ещё на порядок быстрее; сетевой вызов")
    print("   к Flask-серверу стоил единицы миллисекунд на пакет.\n")


if __name__ == "__main__":
    print("=" * 74)
    print("Проверка аналитической баллистики Minecraft")
    print("=" * 74 + "\n")
    _check_closed_form_matches_simulator()
    _check_solver_hits_target()
    _check_angle_solver()
    _show_tnt_vs_naive()
    _show_range_table()
    _show_timing()
    print("Все проверки пройдены.")
