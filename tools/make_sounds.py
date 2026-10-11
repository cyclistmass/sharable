"""Makes the game's sounds and music from scratch (our own synthesis, so nothing to license): dice and the sea-shanty
set list of issue #17, played by a modelled 12-string guitar, accordion and harmonica. Writes OGG files to
app/src/main/res/raw. The melodies are public-domain folk tunes; the arrangements and recordings are ours.

Usage: py tools/make_sounds.py [dice|tunes|all]   (needs numpy and ffmpeg on PATH)
"""
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "app/src/main/res/raw"
SR = 32000
rng = np.random.default_rng(20261011)


# ------------------------------------------------------------------ helpers

def write_ogg(name, x, quality=4):
    """Normalises [x] (mono or (n, 2) stereo) and writes res/raw/<name>.ogg via ffmpeg."""
    x = np.asarray(x, dtype=np.float64)
    if x.ndim == 1:
        x = np.stack([x, x], axis=1)
    peak = np.max(np.abs(x)) or 1.0
    x = np.clip(x / peak * 0.89, -1, 1)
    pcm = (x * 32767).astype("<i2")
    with tempfile.TemporaryDirectory() as d:
        wav = Path(d) / "s.wav"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", str(wav), "-c:a", "libvorbis", "-q:a", str(quality),
                        str(RAW / f"{name}.ogg")], check=True)
    print(f"{name}.ogg  {len(x) / SR:.1f} s  {(RAW / f'{name}.ogg').stat().st_size // 1024} KB")


def one_pole_lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x); acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc; y[i] = acc
    return y


def resonator(x, freq, decay_s):
    """Two-pole resonant filter: a wooden body mode at [freq] ringing for about [decay_s]."""
    r = np.exp(-1.0 / (decay_s * SR)); w = 2 * np.pi * freq / SR
    b1, b2 = 2 * r * np.cos(w), -r * r
    y = np.zeros_like(x); y1 = y2 = 0.0
    for i, v in enumerate(x):
        y0 = v + b1 * y1 + b2 * y2; y[i] = y0; y2, y1 = y1, y0
    return y * (1 - r)


def midi_hz(n):
    return 440.0 * 2 ** ((n - 69) / 12)


# ------------------------------------------------------------------ dice

def click(strength, brightness=1.0):
    """One wooden dice hit: a tick of noise through a few wood resonances."""
    n = int(0.06 * SR)
    burst = rng.standard_normal(n) * np.exp(-np.arange(n) / (0.0018 * SR))
    out = np.zeros(n)
    for f, d, g in ((900, 0.012, 1.0), (1850, 0.008, 0.8), (3200, 0.005, 0.6 * brightness), (5200, 0.003, 0.4 * brightness)):
        out += g * resonator(burst, f * rng.uniform(0.93, 1.07), d)
    return out * strength


def dice_roll():
    """Two dice shaken in a hand (soft, quick clicks, muffled by the fingers), then thrown onto a wooden table:
    a few bouncing hits for each die, getting softer and closer together, and a short roll to rest."""
    length = int(1.35 * SR)
    out = np.zeros(length + SR // 4)
    t = 0.0
    while t < 0.48:                                     # the shake
        i = int(t * SR); c = one_pole_lowpass(click(rng.uniform(0.15, 0.35), 0.4), 2500)
        out[i:i + len(c)] += c; t += rng.uniform(0.025, 0.06)
    for die in range(2):                                # the throw: each die bounces on its own
        t = 0.56 + die * rng.uniform(0.02, 0.06); gap = rng.uniform(0.11, 0.14); s = 1.0
        for _ in range(7):
            i = int(t * SR); c = click(s, 1.0)
            out[i:i + len(c)] += c; t += gap; gap *= rng.uniform(0.55, 0.7); s *= rng.uniform(0.5, 0.65)
        for k in range(10):                             # a last little tumble to rest
            i = int(t * SR); c = click(0.06 * (1 - k / 10), 0.6)
            out[i:i + len(c)] += c; t += rng.uniform(0.012, 0.02)
    return out[:length]


def raiders_clonk():
    """The Raiders' ship lands face up on the event die (Mauro: "a cannon hitting its mount on deck", then "the sound
    of a boat creaking"): a heavy iron-on-oak thud with a short metallic clank, then the hull's timbers creaking."""
    n = int(1.6 * SR); out = np.zeros(n); t = np.arange(n) / SR
    # The cannon: a low body thump (falling pitch), a dull wood knock, a few inharmonic iron partials.
    thump = np.sin(2 * np.pi * (72 * t - 18 * t * t)) * np.exp(-t / 0.16)
    m = int(0.5 * SR); burst = rng.standard_normal(m) * np.exp(-np.arange(m) / (0.004 * SR))
    knock = resonator(burst, 160, 0.12) + 0.6 * resonator(burst, 340, 0.07)
    clank = sum(g * np.sin(2 * np.pi * f * t[:m]) * np.exp(-t[:m] / d)
                for f, d, g in ((830, 0.09, 0.25), (1330, 0.06, 0.18), (2210, 0.04, 0.12), (3170, 0.025, 0.08)))
    out[:n] += 1.0 * thump; out[:m] += 0.7 * knock + clank
    # The creak: stick-slip friction, a stutter of tiny clicks whose rate glides up then down, through hull resonances.
    start = int(0.22 * SR); dur = 1.05; k = start; tt = 0.0
    while tt < dur:
        rate = 55 + 110 * np.sin(np.pi * tt / dur) ** 1.5 + rng.uniform(-6, 6)   # clicks per second
        amp = 0.35 * np.sin(np.pi * tt / dur) ** 0.8 * rng.uniform(0.7, 1.0)
        if k < n: out[k] += amp * (1 if rng.random() < 0.5 else -1)
        step = 1.0 / rate; tt += step; k = start + int(tt * SR)
    creak = out.copy(); creak[:start] = 0; out[start:] = 0
    body = resonator(creak, 420, 0.03) + 0.8 * resonator(creak, 760, 0.025) + 0.5 * resonator(creak, 1250, 0.015)
    out += 2.2 * body
    return one_pole_lowpass(out, 4200)


# ------------------------------------------------------------------ instruments

def guitar12(note, dur, vel=1.0):
    """A plucked 12-string course: Karplus-Strong string plus its octave (or unison on the top courses), slightly
    detuned and delayed, as a 12-string guitar's paired strings ring."""
    n = int((dur + 1.2) * SR)
    def string(freq, gain, delay):
        # Karplus-Strong a period at a time (numpy), not a sample at a time: each pass averages neighbours,
        # which is the string losing its high harmonics as it rings.
        period = max(2, int(round(SR / freq)))
        buf = rng.uniform(-1, 1, period) * gain
        buf = buf - buf.mean()
        start = int(delay * SR); m = n - start
        reps = m // period + 1
        out = np.empty(reps * period)
        for k in range(reps):
            out[k * period:(k + 1) * period] = buf
            buf = 0.4975 * (buf + np.roll(buf, -1))
        res = np.zeros(n); res[start:] = out[:m]
        return res
    f = midi_hz(note)
    pair = f * 2 if note < 62 else f * 1.002
    x = string(f, 1.0, 0) + 0.55 * string(pair, 1.0, 0.012)
    return x * vel


def accordion(note, dur, vel=1.0):
    """Accordion reeds: three slightly detuned reed voices (musette) with bellows swell and a soft attack."""
    n = int((dur + 0.08) * SR); t = np.arange(n) / SR; f = midi_hz(note)
    x = np.zeros(n)
    for det in (0.0, -0.11, 0.13):                      # semitone-cents detune of the three reeds
        ff = f * 2 ** (det / 12)
        ph = 2 * np.pi * ff * t
        x += np.sin(ph) + 0.55 * np.sin(2 * ph) + 0.4 * np.sin(3 * ph) + 0.22 * np.sin(4 * ph) + 0.12 * np.sin(5 * ph)
    env = np.minimum(1, t / 0.035) * np.minimum(1, (dur + 0.08 - t) / 0.06).clip(0, 1)
    swell = 1 + 0.08 * np.sin(2 * np.pi * 0.7 * t)
    return x * env * swell * vel / 4


def harmonica(note, dur, vel=1.0):
    """Harmonica: a reedy, odd-heavy tone with breath noise and a little vibrato."""
    n = int((dur + 0.05) * SR); t = np.arange(n) / SR; f = midi_hz(note)
    vib = 1 + 0.004 * np.sin(2 * np.pi * 5.2 * t) * np.minimum(1, t / 0.25)
    ph = 2 * np.pi * f * np.cumsum(vib) / SR
    x = np.sin(ph) + 0.5 * np.sin(3 * ph) + 0.3 * np.sin(2 * ph) + 0.2 * np.sin(5 * ph)
    x += 0.05 * one_pole_lowpass(rng.standard_normal(n), 3000)
    env = np.minimum(1, t / 0.04) * np.minimum(1, (dur + 0.05 - t) / 0.05).clip(0, 1)
    return x * env * vel / 2


def place(track, sound, at, pan=0.0):
    i = int(at * SR)
    if i >= len(track): return
    s = sound[:len(track) - i]
    track[i:i + len(s), 0] += s * (1 - max(0, pan)); track[i:i + len(s), 1] += s * (1 + min(0, pan))


# ------------------------------------------------------------------ tunes

NOTE = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def parse(melody, octave=5):
    """Melody text: notes like 'A', 'c' (an octave up), "A'" (two up), 'F#', with length in eighths after a colon
    ('A:2' is a quarter); 'z:4' is a rest. Returns (midi or None, eighths)."""
    out = []
    for tok in melody.split():
        name, _, ln = tok.partition(":")
        length = float(ln) if ln else 1.0
        if name == "z": out.append((None, length)); continue
        up = 12 if name[0].islower() else 0
        up += 12 * name.count("'")
        pitch = NOTE[name[0].upper()] + (1 if "#" in name else -1 if name.endswith("b") else 0)
        out.append((12 * (octave + 1) + pitch + up - 12, length))
    return out


def chord_notes(root, minor=False):
    return [root, root + (3 if minor else 4), root + 7]


def drunken_sailor():
    """What Shall We Do with the Drunken Sailor (D Dorian). Verse on accordion, chorus with harmonica on top,
    12-string guitar strumming the two chords throughout; three times round, a bit livelier each time."""
    verse = ("A:2 A A A:2 A A A:2 D:2 F:2 A:2 G:2 G G G:2 G G G:2 C:2 E:2 G:2 "
             "A:2 A A A:2 A A A:2 B:2 c:2 d:2 c:2 A:2 G:2 E:2 D:4 D:4")
    chorus = ("A:4 A:4 A:2 D:2 F:2 A:2 G:4 G:4 G:2 C:2 E:2 G:2 "
              "A:4 A:4 A:2 B:2 c:2 d:2 c:2 A:2 G:2 E:2 D:4 D:4")
    # One chord per bar (8 eighths): Dm Dm C C Dm Dm C Dm, the same for verse and chorus.
    bars = ["Dm", "Dm", "C", "C", "Dm", "Dm", "Dm C", "Dm"]
    chords = {"Dm": (50, True), "C": (48, False)}
    rounds = 3; tempo0 = 0.24   # Mauro: slower, it was stressful
    total_eighths = rounds * 2 * 64
    track = np.zeros((int((total_eighths * tempo0 + 4) * SR), 2))
    t = 0.0
    for r in range(rounds):
        e = tempo0                                      # an even, easy pace all the way through
        for part, mel, lead in (("verse", verse, accordion), ("chorus", chorus, harmonica)):
            start = t
            for midi, ln in parse(mel, octave=4):
                if midi is not None:
                    place(track, lead(midi, ln * e * 0.92, 0.9), t, pan=-0.15)
                    if lead is harmonica and r > 0:   # second time round the accordion joins the chorus an octave down
                        place(track, accordion(midi - 12, ln * e * 0.92, 0.45), t, pan=0.2)
                t += ln * e
            # 12-string strum: down on 1 and 3, a lighter up-strum on the "and" of 2 and 4.
            for b, bar in enumerate(bars):
                names = bar.split()
                for half, nm in enumerate(names if len(names) == 2 else names * 2):
                    root, minor = chords[nm]
                    at = start + (b * 8 + half * 4) * e
                    for k, nn in enumerate(chord_notes(root, minor) + [root + 12]):
                        place(track, guitar12(nn, 2 * e, 0.35), at + k * 0.012, pan=0.25)
                    for k, nn in enumerate(reversed(chord_notes(root, minor) + [root + 12])):
                        place(track, guitar12(nn, e, 0.18), at + 3 * e + k * 0.01, pan=0.25)
    end = int((t + 1.5) * SR)
    track = track[:end]
    fade = np.minimum(1, (end - np.arange(end)) / (1.2 * SR))[:, None]
    return track * fade


def carnival_of_venice():
    """Carnival of Venice (Il carnevale di Venezia), G major, 6/8. Melody after James Kerr, "Merry Melodies" v.3
    p.29 #2 (public domain), ABC by John Chambers:
    https://abcnotation.com/tunePage?a=trillian.mit.edu/~jc/music/book/Kerr/MM3-V1/0525
    Each strain twice (AABB), the whole tune twice. Accordion melody over a gently arpeggiated 12-string; second
    time round a soft harmonica doubles strain A an octave up and the accordion takes strain B an octave up."""
    # Each strain: the one-eighth pickup, then 8 bars of 6 eighths whose last note leaves room for the next pickup.
    strain_a = ("d e:2 d c:2 B d:2 c A:2 B c B c e:2 d B:5 d "
                "e:2 d c:2 B d:2 c A:2 B c B c e:2 d G:5")
    strain_b = ("d d c B B A G F#:2 A e:2 d c#:2 d c:2 A B:5 d "
                "d c B B A G F#:2 A e:2 d f# e d c B A G:5")
    # One chord per bar (two for a split bar), the same both times through each strain.
    bars = {"A": ["C", "D7", "D7", "G", "C", "D7", "D7", "G"],
            "B": ["G", "D7", "A7 D7", "G", "G", "D7", "D7", "G"]}
    chords = {"G": [43, 47, 50, 55], "C": [48, 52, 55, 60], "D7": [50, 54, 57, 60], "A7": [45, 49, 52, 55]}
    e = 0.24                                            # the same easy pace as Drunken Sailor
    form = ["A", "A", "B", "B"]
    total_eighths = 2 * len(form) * 48
    track = np.zeros((int((total_eighths * e + 5) * SR), 2))
    t = 0.0
    for r in range(2):
        for s_i, s in enumerate(form):
            start = t
            octave = 5 if (r == 1 and s == "B") else 4
            notes = parse(strain_a if s == "A" else strain_b, octave=octave)
            for n_i, (midi, ln) in enumerate(notes):
                last = r == 1 and s_i == len(form) - 1 and n_i == len(notes) - 1
                hold = 3.0 if last else 0.92                # the very last note rings on
                if midi is not None:
                    place(track, accordion(midi, ln * e * hold, 0.9), t, pan=-0.15)
                    if r == 1 and s == "A":
                        place(track, harmonica(midi + 12, ln * e * hold, 0.3), t, pan=0.2)
                t += ln * e
            # 12-string: an unhurried arpeggio, one note per eighth (root, 3rd, 5th, octave, 5th, 3rd).
            for b, bar in enumerate(bars[s]):
                names = bar.split()
                for k in range(6):
                    nm = names[0] if len(names) == 1 or k < 3 else names[1]
                    ch = chords[nm]
                    nn = ch[[0, 1, 2, 3, 2, 1][k]]
                    vel = 0.32 if k in (0, 3) else 0.2
                    at = start + (1 + b * 6 + k) * e
                    if r == 1 and s_i == len(form) - 1 and b == 7:   # final bar: one soft strum that rings out
                        if k == 0:
                            for j, cn in enumerate(ch):
                                place(track, guitar12(cn, 6 * e, 0.3), at + j * 0.03, pan=0.25)
                        continue
                    place(track, guitar12(nn, 2 * e, vel), at, pan=0.25)
    end = int((t + 2.5) * SR)
    track = track[:end]
    fade = np.minimum(1, (end - np.arange(end)) / (2.5 * SR))[:, None]
    return track * fade


# ------------------------------------------------------------------ main

def main(what):
    RAW.mkdir(parents=True, exist_ok=True)
    if what in ("dice", "all"):
        write_ogg("dice_roll", dice_roll(), quality=5)
        write_ogg("raiders_clonk", raiders_clonk(), quality=5)
    if what in ("tunes", "all"):
        write_ogg("tune_drunken_sailor", drunken_sailor())
        write_ogg("tune_carnival_of_venice", carnival_of_venice())


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "all")
