"""
Particle and magic effects system — Doctor Strange style.
"""

import math
import random
import time
from dataclasses import dataclass

import cv2
import numpy as np

# ── Colour palettes ──────────────────────────────────────────────────────────

ORANGE_GOLD = [
    (0, 140, 255),   # deep orange
    (30, 180, 255),   # orange-gold
    (50, 210, 255),   # bright gold
    (80, 230, 255),   # yellow-gold
    (120, 255, 255),  # white-gold
]

BLUE_MYSTIC = [
    (255, 120, 0),
    (255, 160, 40),
    (255, 200, 80),
    (255, 230, 160),
    (255, 255, 230),
]

GREEN_ENERGY = [
    (0, 180, 0),
    (0, 220, 50),
    (50, 255, 100),
    (120, 255, 180),
    (200, 255, 230),
]

PALETTES = [ORANGE_GOLD, BLUE_MYSTIC, GREEN_ENERGY]


def _pick_color(palette: list) -> tuple:
    return random.choice(palette)


# ── Particle ─────────────────────────────────────────────────────────────────

@dataclass
class Particle:
    x: float
    y: float
    vx: float
    vy: float
    life: float          # seconds remaining
    max_life: float
    size: float
    color: tuple
    kind: str = "spark"  # spark | rune | ring

    def update(self, dt: float) -> bool:
        self.x += self.vx * dt
        self.y += self.vy * dt
        self.vy += 30.0 * dt  # light gravity
        self.life -= dt
        return self.life > 0

    @property
    def alpha(self) -> float:
        return max(0.0, min(1.0, self.life / self.max_life))


# ── Trail point ──────────────────────────────────────────────────────────────

@dataclass
class TrailPoint:
    x: float
    y: float
    t: float  # timestamp


# ── Magic Effect Engine ──────────────────────────────────────────────────────

class MagicEffects:
    def __init__(self):
        self.particles: list[Particle] = []
        self.trails: dict[str, list[TrailPoint]] = {}
        self.portal_angles: dict[str, float] = {}
        self.prev_positions: dict[str, tuple] = {}
        self.palette_index = 0
        self._last_time = time.time()
        self.trail_max_age = 0.6  # seconds
        self.max_particles = 800

    @property
    def palette(self):
        return PALETTES[self.palette_index % len(PALETTES)]

    def cycle_palette(self):
        self.palette_index += 1

    def clear_inactive_hands(self, active_hand_ids: list[str]):
        """Remove portals, trails, and prev_positions for hands no longer in frame."""
        for key in list(self.portal_angles):
            hand_id = key.replace("portal_", "")
            if hand_id not in active_hand_ids:
                del self.portal_angles[key]
        for key in list(self.trails):
            hand_id = key.rsplit("_", 1)[0]
            if hand_id not in active_hand_ids:
                del self.trails[key]
        for key in list(self.prev_positions):
            hand_id = key.rsplit("_", 1)[0]
            if hand_id not in active_hand_ids:
                del self.prev_positions[key]

    # ── update loop ──────────────────────────────────────────────────────

    def update(self):
        now = time.time()
        dt = min(now - self._last_time, 0.1)
        self._last_time = now

        self.particles = [p for p in self.particles if p.update(dt)]

        for key in list(self.trails):
            self.trails[key] = [
                tp for tp in self.trails[key] if now - tp.t < self.trail_max_age
            ]
            if not self.trails[key]:
                del self.trails[key]

        for key in list(self.portal_angles):
            self.portal_angles[key] += 120.0 * dt

    # ── emit helpers ─────────────────────────────────────────────────────

    def _emit_sparks(self, x: float, y: float, count: int, speed: float = 120):
        for _ in range(count):
            angle = random.uniform(0, 2 * math.pi)
            spd = random.uniform(speed * 0.3, speed)
            life = random.uniform(0.3, 0.8)
            self.particles.append(Particle(
                x=x, y=y,
                vx=math.cos(angle) * spd,
                vy=math.sin(angle) * spd,
                life=life, max_life=life,
                size=random.uniform(1.5, 4.0),
                color=_pick_color(self.palette),
                kind="spark",
            ))

    def _emit_rune(self, x: float, y: float):
        life = random.uniform(0.5, 1.0)
        self.particles.append(Particle(
            x=x, y=y, vx=0, vy=-15,
            life=life, max_life=life,
            size=random.uniform(8, 16),
            color=_pick_color(self.palette),
            kind="rune",
        ))

    # ── feed hand landmarks ─────────────────────────────────────────────

    def feed_hand(self, hand_id: str, landmarks: list, w: int, h: int):
        """landmarks: list of mediapipe NormalizedLandmark for one hand."""
        tips = [4, 8, 12, 16, 20]  # thumb, index, middle, ring, pinky
        wrist = landmarks[0]
        wx, wy = int(wrist.x * w), int(wrist.y * h)

        for idx in tips:
            lm = landmarks[idx]
            px, py = int(lm.x * w), int(lm.y * h)
            key = f"{hand_id}_{idx}"

            # trail
            self.trails.setdefault(key, []).append(
                TrailPoint(px, py, time.time())
            )

            # velocity-based sparks
            prev = self.prev_positions.get(key)
            if prev:
                dx, dy = px - prev[0], py - prev[1]
                speed = math.hypot(dx, dy)
                if speed > 4:
                    count = min(int(speed / 3), 8)
                    self._emit_sparks(px, py, count, speed * 2)
                    if random.random() < 0.15:
                        self._emit_rune(px, py)

            self.prev_positions[key] = (px, py)

        # portal around wrist when fingers spread
        spread = self._finger_spread(landmarks, w, h)
        portal_key = f"portal_{hand_id}"
        if spread > 60:
            self.portal_angles.setdefault(portal_key, 0.0)
        else:
            self.portal_angles.pop(portal_key, None)

        # trim particles
        if len(self.particles) > self.max_particles:
            self.particles = self.particles[-self.max_particles:]

    def _finger_spread(self, landmarks, w, h) -> float:
        tips = [8, 12, 16, 20]
        positions = [(landmarks[i].x * w, landmarks[i].y * h) for i in tips]
        if len(positions) < 2:
            return 0
        total = 0
        for i in range(len(positions) - 1):
            total += math.hypot(
                positions[i + 1][0] - positions[i][0],
                positions[i + 1][1] - positions[i][1],
            )
        return total / (len(positions) - 1)

    # ── draw ─────────────────────────────────────────────────────────────

    def draw(self, frame: np.ndarray):
        overlay = frame.copy()
        h, w = frame.shape[:2]
        now = time.time()

        # 1) trails (glowing lines)
        for key, points in self.trails.items():
            if len(points) < 2:
                continue
            for i in range(1, len(points)):
                age = now - points[i].t
                alpha = max(0.0, 1.0 - age / self.trail_max_age)
                thickness = max(1, int(5 * alpha))
                color = _pick_color(self.palette)
                bright = tuple(min(255, int(c * (0.5 + 0.5 * alpha))) for c in color)
                cv2.line(overlay,
                         (int(points[i - 1].x), int(points[i - 1].y)),
                         (int(points[i].x), int(points[i].y)),
                         bright, thickness, cv2.LINE_AA)

        # 2) particles
        for p in self.particles:
            alpha = p.alpha
            if p.kind == "spark":
                radius = max(1, int(p.size * alpha))
                color = tuple(min(255, int(c * alpha)) for c in p.color)
                cv2.circle(overlay, (int(p.x), int(p.y)), radius, color, -1, cv2.LINE_AA)
                # glow halo
                if radius > 2:
                    glow_color = tuple(min(255, int(c * alpha * 0.4)) for c in p.color)
                    cv2.circle(overlay, (int(p.x), int(p.y)), radius * 3, glow_color, 1, cv2.LINE_AA)
            elif p.kind == "rune":
                self._draw_rune(overlay, int(p.x), int(p.y), p.size * alpha, p.color, alpha)

        # 3) portals
        for key, angle in self.portal_angles.items():
            hand_id = key.replace("portal_", "")
            wrist_key = f"{hand_id}_0"
            # find wrist from prev positions
            if f"{hand_id}_8" in self.prev_positions:
                idx_pos = self.prev_positions[f"{hand_id}_8"]
                mid_pos = self.prev_positions.get(f"{hand_id}_12", idx_pos)
                cx = (idx_pos[0] + mid_pos[0]) // 2
                cy = (idx_pos[1] + mid_pos[1]) // 2
                self._draw_portal(overlay, cx, cy, angle, 80)

        cv2.addWeighted(overlay, 0.85, frame, 0.15, 0, frame)

        # 4) extra glow pass (bright core on sparks)
        glow = np.zeros_like(frame)
        for p in self.particles:
            if p.kind == "spark" and p.alpha > 0.5:
                radius = max(1, int(p.size * p.alpha * 0.7))
                color = tuple(min(255, int(c * p.alpha)) for c in p.color)
                cv2.circle(glow, (int(p.x), int(p.y)), radius, color, -1, cv2.LINE_AA)

        if glow.any():
            blurred = cv2.GaussianBlur(glow, (15, 15), 0)
            cv2.add(frame, blurred, frame)

    def _draw_rune(self, img, cx, cy, size, color, alpha):
        """Draw a small mystic symbol."""
        s = int(size)
        if s < 3:
            return
        col = tuple(min(255, int(c * alpha)) for c in color)
        # small circle with cross
        cv2.circle(img, (cx, cy), s, col, 1, cv2.LINE_AA)
        cv2.line(img, (cx - s, cy), (cx + s, cy), col, 1, cv2.LINE_AA)
        cv2.line(img, (cx, cy - s), (cx, cy + s), col, 1, cv2.LINE_AA)

    def _draw_portal(self, img, cx, cy, angle_deg, radius):
        """Draw a rotating Doctor Strange-style portal (mandala ring)."""
        palette = self.palette
        segments = 24
        for i in range(segments):
            a1 = math.radians(angle_deg + i * (360 / segments))
            a2 = math.radians(angle_deg + (i + 0.7) * (360 / segments))
            p1 = (int(cx + math.cos(a1) * radius), int(cy + math.sin(a1) * radius))
            p2 = (int(cx + math.cos(a2) * radius), int(cy + math.sin(a2) * radius))
            col = palette[i % len(palette)]
            cv2.line(img, p1, p2, col, 2, cv2.LINE_AA)

        # inner ring
        inner_r = int(radius * 0.7)
        for i in range(segments):
            a1 = math.radians(-angle_deg * 1.3 + i * (360 / segments))
            a2 = math.radians(-angle_deg * 1.3 + (i + 0.5) * (360 / segments))
            p1 = (int(cx + math.cos(a1) * inner_r), int(cy + math.sin(a1) * inner_r))
            p2 = (int(cx + math.cos(a2) * inner_r), int(cy + math.sin(a2) * inner_r))
            col = palette[(i + 2) % len(palette)]
            cv2.line(img, p1, p2, col, 1, cv2.LINE_AA)

        # small decorative circles on outer ring
        deco = 8
        for i in range(deco):
            a = math.radians(angle_deg * 0.5 + i * (360 / deco))
            px = int(cx + math.cos(a) * radius)
            py = int(cy + math.sin(a) * radius)
            cv2.circle(img, (px, py), 4, palette[i % len(palette)], -1, cv2.LINE_AA)

        # arc sparks
        cv2.ellipse(img, (cx, cy), (radius, radius),
                     angle_deg, 0, 60, palette[0], 2, cv2.LINE_AA)
        cv2.ellipse(img, (cx, cy), (radius, radius),
                     angle_deg + 180, 0, 60, palette[2], 2, cv2.LINE_AA)


# ── Skeleton drawer ──────────────────────────────────────────────────────────

POSE_CONNECTIONS = [
    (0, 1), (1, 2), (2, 3), (3, 7),
    (0, 4), (4, 5), (5, 6), (6, 8),
    (9, 10),
    (11, 12), (11, 13), (13, 15), (15, 17), (15, 19), (15, 21),
    (12, 14), (14, 16), (16, 18), (16, 20), (16, 22),
    (11, 23), (12, 24), (23, 24), (23, 25), (24, 26),
    (25, 27), (26, 28), (27, 29), (28, 30), (29, 31), (30, 32),
]

SKELETON_JOINT_COLOR = (0, 255, 200)
SKELETON_BONE_COLOR = (0, 200, 160)


def draw_skeleton(frame: np.ndarray, landmarks, w: int, h: int):
    """Draw a glowing skeleton on the detected pose."""
    points = []
    for lm in landmarks:
        px, py = int(lm.x * w), int(lm.y * h)
        vis = lm.visibility if hasattr(lm, 'visibility') else 1.0
        points.append((px, py, vis))

    # bones
    for a, b in POSE_CONNECTIONS:
        if a < len(points) and b < len(points):
            if points[a][2] > 0.5 and points[b][2] > 0.5:
                cv2.line(frame,
                         (points[a][0], points[a][1]),
                         (points[b][0], points[b][1]),
                         SKELETON_BONE_COLOR, 3, cv2.LINE_AA)

    # joints
    for px, py, vis in points:
        if vis > 0.5:
            cv2.circle(frame, (px, py), 5, SKELETON_JOINT_COLOR, -1, cv2.LINE_AA)
            cv2.circle(frame, (px, py), 8, (0, 255, 255), 1, cv2.LINE_AA)
