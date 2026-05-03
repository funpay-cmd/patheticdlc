#!/usr/bin/env python3
"""
Magic Camera — Doctor Strange edition.

Webcam app with:
  • Full-body pose detection + skeleton overlay
  • Hand & finger tracking
  • Magic particle effects, glowing trails, and rotating portals

Controls (while the window is focused):
  Q / ESC   — quit
  P         — cycle colour palette (orange-gold → blue → green)
  M         — toggle mirror mode
  S         — toggle skeleton visibility
  H         — show / hide help overlay
"""

import sys
import time

import cv2
import mediapipe as mp
import numpy as np

from effects import MagicEffects, draw_skeleton

# ── MediaPipe setup ──────────────────────────────────────────────────────────

mp_pose = mp.solutions.pose
mp_hands = mp.solutions.hands

POSE_CFG = dict(
    static_image_mode=False,
    model_complexity=1,
    smooth_landmarks=True,
    min_detection_confidence=0.5,
    min_tracking_confidence=0.5,
)
HANDS_CFG = dict(
    static_image_mode=False,
    max_num_hands=2,
    model_complexity=0,
    min_detection_confidence=0.5,
    min_tracking_confidence=0.5,
)

# ── HUD ──────────────────────────────────────────────────────────────────────

HELP_TEXT = [
    "Q / ESC  - quit",
    "P        - cycle palette",
    "M        - mirror mode",
    "S        - toggle skeleton",
    "H        - this help",
]


def draw_hud(frame, fps, show_help, mirror, show_skeleton, palette_idx):
    h, w = frame.shape[:2]
    palette_names = ["Orange-Gold", "Blue-Mystic", "Green-Energy"]

    cv2.putText(frame, f"FPS: {fps:.0f}", (10, 30),
                cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 0), 2)

    status = f"Mirror: {'ON' if mirror else 'OFF'}  |  Skeleton: {'ON' if show_skeleton else 'OFF'}  |  Palette: {palette_names[palette_idx % 3]}"
    cv2.putText(frame, status, (10, h - 15),
                cv2.FONT_HERSHEY_SIMPLEX, 0.5, (200, 200, 200), 1)

    if show_help:
        bx, by, bw, bh = w - 280, 10, 270, 30 + 25 * len(HELP_TEXT)
        sub = frame[by:by + bh, bx:bx + bw]
        black = np.zeros_like(sub)
        cv2.addWeighted(sub, 0.4, black, 0.6, 0, sub)
        for i, line in enumerate(HELP_TEXT):
            cv2.putText(frame, line, (bx + 10, by + 28 + i * 25),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1)


# ── Main loop ────────────────────────────────────────────────────────────────

def main():
    cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        print("[ERROR] Cannot open webcam. Check that a camera is connected.")
        sys.exit(1)

    cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)

    pose = mp_pose.Pose(**POSE_CFG)
    hands = mp_hands.Hands(**HANDS_CFG)
    effects = MagicEffects()

    mirror = True
    show_skeleton = True
    show_help = True
    fps = 0.0
    frame_count = 0
    fps_timer = time.time()

    print("[INFO] Magic Camera started. Press H for controls.")

    while True:
        ret, frame = cap.read()
        if not ret:
            break

        if mirror:
            frame = cv2.flip(frame, 1)

        h, w = frame.shape[:2]
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

        # ── pose ─────────────────────────────────────────────────────────
        pose_results = pose.process(rgb)
        if pose_results.pose_landmarks and show_skeleton:
            draw_skeleton(frame, pose_results.pose_landmarks.landmark, w, h)

        # ── hands ────────────────────────────────────────────────────────
        hand_results = hands.process(rgb)
        active_hands = []
        if hand_results.multi_hand_landmarks:
            for idx, hand_lm in enumerate(hand_results.multi_hand_landmarks):
                label = hand_results.multi_handedness[idx].classification[0].label
                hand_id = f"hand_{label}"
                active_hands.append(hand_id)
                effects.feed_hand(hand_id, hand_lm.landmark, w, h)
        effects.clear_inactive_hands(active_hands)

        # ── effects ──────────────────────────────────────────────────────
        effects.update()
        effects.draw(frame)

        # ── HUD ──────────────────────────────────────────────────────────
        frame_count += 1
        elapsed = time.time() - fps_timer
        if elapsed >= 0.5:
            fps = frame_count / elapsed
            frame_count = 0
            fps_timer = time.time()

        draw_hud(frame, fps, show_help, mirror, show_skeleton, effects.palette_index)

        cv2.imshow("Magic Camera", frame)

        # ── controls ─────────────────────────────────────────────────────
        key = cv2.waitKey(1) & 0xFF
        if key in (ord('q'), 27):      # Q or ESC
            break
        elif key == ord('p'):
            effects.cycle_palette()
        elif key == ord('m'):
            mirror = not mirror
        elif key == ord('s'):
            show_skeleton = not show_skeleton
        elif key == ord('h'):
            show_help = not show_help

    cap.release()
    cv2.destroyAllWindows()
    pose.close()
    hands.close()
    print("[INFO] Magic Camera stopped.")


if __name__ == "__main__":
    main()
