"""Synthesizes the game's sound effects into app/src/main/res/raw/.

Run from the repo root:  python tools/gen_sfx.py
Pure Python (no numpy). To swap in recorded sounds later, replace the .wav (or drop an .ogg with
the same name and delete the .wav) — the app only refers to the resource names.
"""
import math
import os
import random
import struct
import wave

RATE = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")


def note(name):
    """'A4' -> Hz."""
    names = {"C": -9, "D": -7, "E": -5, "F": -4, "G": -2, "A": 0, "B": 2}
    semis = names[name[0]] + (1 if "#" in name else 0)
    octave = int(name[-1])
    return 440.0 * 2 ** ((semis + (octave - 4) * 12) / 12)


def silence(seconds):
    return [0.0] * int(RATE * seconds)


def bell(freq, seconds, decay=0.25, partials=((1, 1.0), (2, 0.45), (3, 0.2), (4.2, 0.08)), attack=0.004):
    """Mallet/bell tone: harmonic partials, each decaying faster the higher it is."""
    n = int(RATE * seconds)
    out = []
    for i in range(n):
        t = i / RATE
        env = min(1.0, t / attack) * math.exp(-t / decay)
        s = 0.0
        for mult, amp in partials:
            s += amp * math.exp(-t * mult * 0.6 / decay) * math.sin(2 * math.pi * freq * mult * t)
        out.append(s * env)
    return out


def soft_square(freq, seconds, decay=0.15, attack=0.003, glide_to=None):
    """Rounded square (odd harmonics) for retro coin/buzz sounds, with optional pitch glide."""
    n = int(RATE * seconds)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / RATE
        f = freq if glide_to is None else freq + (glide_to - freq) * (i / n)
        phase += 2 * math.pi * f / RATE
        s = math.sin(phase) + math.sin(3 * phase) / 3 * 0.6 + math.sin(5 * phase) / 5 * 0.3
        env = min(1.0, t / attack) * math.exp(-t / decay)
        out.append(s * env)
    return out


def mix(*tracks):
    """Overlays (offset_seconds, samples, gain) tuples."""
    length = max(int(off * RATE) + len(s) for off, s, _ in tracks)
    out = [0.0] * length
    for off, samples, gain in tracks:
        start = int(off * RATE)
        for i, v in enumerate(samples):
            out[start + i] += v * gain
    return out


def write(name, samples, peak=0.8):
    top = max(abs(v) for v in samples) or 1.0
    # Short fade-out so nothing clicks at the end.
    fade = min(len(samples), int(RATE * 0.01))
    for i in range(fade):
        samples[-1 - i] *= i / fade
    frames = b"".join(struct.pack("<h", int(v / top * peak * 32767)) for v in samples)
    os.makedirs(OUT, exist_ok=True)
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(frames)
    print(f"{name}.wav  {len(samples) / RATE:.2f}s")


def tap():
    # Soft wooden "tok": a short high mallet plus a tiny noise transient.
    rnd = random.Random(7)
    click = [rnd.uniform(-1, 1) * math.exp(-i / (RATE * 0.002)) for i in range(int(RATE * 0.01))]
    body = bell(1700, 0.06, decay=0.012, partials=((1, 1.0), (2.7, 0.3)))
    return mix((0, body, 1.0), (0, click, 0.25))


def correct():
    # Rising pentatonic two-note chime (G5 -> D6).
    return mix(
        (0.00, bell(note("G5"), 0.25, decay=0.09), 0.8),
        (0.09, bell(note("D6"), 0.45, decay=0.16), 1.0),
    )


def wrong():
    # Gentle descending "bonk-bonk" — clearly wrong, not harsh.
    return mix(
        (0.00, soft_square(note("D#4"), 0.16, decay=0.07), 0.7),
        (0.13, soft_square(note("A3"), 0.30, decay=0.11, glide_to=note("G#3")), 0.8),
    )


def star():
    # Sparkly ping; the app raises its pitch for the 2nd and 3rd star.
    shimmer = bell(note("E7"), 0.2, decay=0.05, partials=((1, 1.0),))
    return mix((0, bell(note("E6"), 0.4, decay=0.12), 1.0), (0.03, shimmer, 0.35))


def coin():
    # Classic two-step coin.
    return mix(
        (0.00, soft_square(note("B5"), 0.08, decay=0.2), 0.6),
        (0.07, soft_square(note("E6"), 0.35, decay=0.12), 0.6),
    )


def win():
    # Pentatonic run up to a ringing chord.
    run = ["C5", "D5", "E5", "G5", "A5"]
    tracks = [(i * 0.075, bell(note(n), 0.3, decay=0.1), 0.7) for i, n in enumerate(run)]
    chord_at = len(run) * 0.075
    for n in ["C6", "E6", "G6"]:
        tracks.append((chord_at, bell(note(n), 1.2, decay=0.38), 0.6))
    tracks.append((chord_at, bell(note("C5"), 1.2, decay=0.45, partials=((1, 1.0), (2, 0.3))), 0.5))
    return mix(*tracks)


def lose():
    # Soft falling "aww" in three steps.
    return mix(
        (0.00, bell(note("G4"), 0.35, decay=0.14, partials=((1, 1.0), (2, 0.25))), 0.8),
        (0.22, bell(note("E4"), 0.35, decay=0.14, partials=((1, 1.0), (2, 0.25))), 0.8),
        (0.44, bell(note("C4"), 0.7, decay=0.25, partials=((1, 1.0), (2, 0.25))), 0.9),
    )


def unlock():
    # Quick upward swoosh, then a bright open chord.
    sweep = soft_square(note("C5"), 0.16, decay=0.5, glide_to=note("C6"))
    return mix(
        (0.00, sweep, 0.35),
        (0.15, bell(note("G5"), 0.6, decay=0.2), 0.6),
        (0.15, bell(note("C6"), 0.6, decay=0.2), 0.6),
        (0.15, bell(note("E6"), 0.7, decay=0.22), 0.5),
    )


if __name__ == "__main__":
    for name, make in [
        ("sfx_tap", tap), ("sfx_correct", correct), ("sfx_wrong", wrong), ("sfx_star", star),
        ("sfx_coin", coin), ("sfx_win", win), ("sfx_lose", lose), ("sfx_unlock", unlock),
    ]:
        write(name, make(), peak=0.55 if name == "sfx_tap" else 0.8)
