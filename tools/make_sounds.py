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


_REED_TABLES = {}


def _reed_table(f, size=2048):
    """One period of a free reed's tone at [f] Hz: a band-limited sawtooth-like spectrum (harmonics ~1/n up to
    8 kHz) with a bump around 1.5-3 kHz from the reed chamber."""
    key = round(f, 2)
    if key not in _REED_TABLES:
        ph = 2 * np.pi * np.arange(size) / size
        tab = np.zeros(size)
        for n in range(1, max(2, int(8000 / f)) + 1):
            fh = n * f
            amp = (1.0 / n) * (1 + 0.9 * np.exp(-((fh - 2200) / 800) ** 2)) * (1 if fh < 6000 else 1 - (fh - 6000) / 2500)
            tab += amp * np.sin(n * ph)
        _REED_TABLES[key] = tab
    return _REED_TABLES[key]


def _reed(f, n, t):
    """A reed voice at [f] Hz, [n] samples, read from its wavetable with a random starting phase."""
    tab = _reed_table(f); size = len(tab)
    pos = (rng.uniform(0, size) + f * size * t) % size
    i = pos.astype(int); frac = pos - i
    return tab[i] * (1 - frac) + tab[(i + 1) % size] * frac


def accordion(note, dur, vel=1.0, musette=True, low_reed=True):
    """A free-reed accordion: buzzy reeds (band-limited, sawtooth-like), in musette three middle reeds (one in tune,
    two about +-13 cents off, so they beat a few times a second) plus a quieter reed an octave below; a quick soft
    bellows attack with a little reed chiff, a slow swell and a clean release. musette=False is one reed (the
    left-hand buttons); low_reed adds the bassoon reed."""
    n = int((dur + 0.06) * SR); t = np.arange(n) / SR; f = midi_hz(note)
    x = _reed(f, n, t)
    if musette:
        x = x + 0.85 * _reed(f * 2 ** (0.13 / 12), n, t) + 0.85 * _reed(f * 2 ** (-0.14 / 12), n, t)
    if low_reed:
        x = x + 0.4 * _reed(f / 2, n, t)
    a = 0.025
    env = np.minimum(1, t / a) ** 1.5 * np.clip((dur + 0.06 - t) / 0.06, 0, 1)
    env *= 1 + 0.06 * np.minimum(1, t / 0.8)            # the bellows pressing on
    nc = min(n, int(0.03 * SR))                         # reed chiff: a short, soft breathy burst at the start
    chiff = np.zeros(n)
    chiff[:nc] = one_pole_lowpass(rng.standard_normal(nc), 3500) * np.exp(-np.arange(nc) / (0.008 * SR)) * 0.5
    norm = 4.5 if musette else 2.2
    return (x * env + chiff) * vel / norm


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
    """Carnival of Venice (Il carnevale di Venezia, the "Mein Hut, der hat drei Ecken" / "My Hat, It Has Three
    Corners" tune), the 3/4 theme in C major. Melody after Helene Criscio and Tony Santorella, "Italian Favorites
    for Accordion" (1997), ABC by John Chambers (in F there; transposed to C):
    https://abcnotation.com/tunePage?a=trillian.mit.edu/~jc/music/abc/Italy/song/Carnival_of_Venice/0000
    Cross-checked against Frank Nordberg's Musica Viva transcription (the same notes, in C):
    https://abcnotation.com/tunePage?a=trillian.mit.edu/~jc/music/abc/mirror/musicaviva.com/italy/carnival-of-venice/0000
    and Conal O'Grada's 3/4 setting (an ornamented variant): https://thesession.org/tunes/23403
    Accordion right hand on the tune, its left hand an oom-pah-pah waltz (bass on 1, short chords on 2 and 3), a
    soft 12-string under it; both strains twice through, the second time with a quiet harmonica doubling."""
    q = 0.48                                            # seconds per quarter note: Drunken Sailor's beat
    e = q / 2                                           # parse() lengths are in eighths
    # Each half: 8 bars of 3/4 (6 eighths); the last note leaves room for the pickup g into the next half.
    half_a = "a:4 g:2 f:4 e:2 f:2 d:6 d:2 e:2 f:4 g:2 a:4 g:2 {end}:10 g:2"
    half_b = "g:2 f:2 e:2 e:2 d:2 c:2 c:2 B:4 B:2 d:2 e:2 f:4 g:2 a:4 g:2 {end}:10 g:2"
    halves = [(half_a.format(end="e"), "A"), (half_a.format(end="c"), "A"),
              (half_b.format(end="e"), "B"), (half_b.format(end="c"), "B")]
    # Chord per beat (a bar of one name is the same chord on all three beats).
    bars = {"A": ["F", "F", "G7", "G7", "G7", "F", "C", "C"],
            "B": ["C", "C", "C G7 G7", "G7", "G7", "F", "C", "C"]}
    bass = {"C": (36, 43), "F": (41, 36), "G7": (43, 38)}            # root, then the fifth on alternate bars
    chord = {"C": [48, 52, 55], "F": [48, 53, 57], "G7": [47, 50, 53]}  # left-hand chords, below the tune
    passes = 2
    total_bars = 1 + passes * 32 + 2
    track = np.zeros((int((total_bars * 3 * q + 4) * SR), 2))

    def left_hand(at, names, bar_no, final=False):
        names = names.split()
        names = names * 3 if len(names) == 1 else names
        b = bass[names[0]][bar_no % 2]
        place(track, accordion(b, q * (2.6 if final else 0.85), 0.55, musette=False, low_reed=False), at, pan=0.2)
        place(track, accordion(b + 12, q * (2.6 if final else 0.85), 0.3, musette=False, low_reed=False), at, pan=0.2)
        for j, nn in enumerate(chord[names[0]]):              # the 12-string: one soft strum on the downbeat
            place(track, guitar12(nn, 3 * q, 0.13), at + j * 0.02, pan=0.3)
        if final:
            for nn in chord[names[0]]:
                place(track, accordion(nn, q * 2.6, 0.22, musette=False, low_reed=False), at, pan=0.2)
            return
        for beat in (1, 2):                                   # pah, pah: short and light
            for nn in chord[names[beat]]:
                place(track, accordion(nn, q * 0.42, 0.22, musette=False, low_reed=False), at + beat * q, pan=0.2)

    left_hand(0.0, "C", 0)                                    # one bar of oom-pah-pah to set the waltz going
    place(track, accordion(67, q * 0.92, 0.8), 2 * q, pan=-0.15)        # pickup g (G4)
    t = 3 * q; bar_no = 1
    for p in range(passes):
        for h_i, (mel, strain) in enumerate(halves):
            start = t
            notes = parse(mel, octave=4)
            last_half = p == passes - 1 and h_i == len(halves) - 1
            if last_half:
                notes = notes[:-2] + [(notes[-2][0], 12.0)]     # end on a held c, no pickup
            for midi, ln in notes:
                if midi is not None:
                    hold = 1.15 if last_half and ln == 12.0 else 0.92
                    place(track, accordion(midi, ln * e * hold, 0.8), t, pan=-0.15)
                    if p == 1:
                        place(track, harmonica(midi, ln * e * hold, 0.22), t, pan=0.15)
                t += ln * e
            for b, names in enumerate(bars[strain]):
                left_hand(start + b * 3 * q, names, bar_no, final=last_half and b == 7)
                bar_no += 1
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
