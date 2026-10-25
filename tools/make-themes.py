#!/usr/bin/env python3
"""Generate Demonsaw Qt themes from Omarchy (Ghostty) theme palettes.

Reads every theme's colors.toml from the Omarchy theme directories and emits
a Demonsaw QSS theme per palette, based on resource/style.css:

  * bundled copies -> ds4/code/4_client_desktop/resource/themes/<name>.css
  * user copies    -> ~/.config/demonsaw/themes/<name>.css

Color mapping
-------------
The Omarchy palette is semantic (background/foreground/accent/selection + 16
hues). style.css is a light theme with a documented color map. Neutral greys
(borders, hovers, muted text, disabled) are derived by mixing the theme's
background toward its foreground at fixed ratios, so both light and dark
palettes come out correct with one rule set.

Usage: tools/make-themes.py [--no-user]
"""

import argparse
import os
import re
import shutil
import sys

try:
    import tomllib  # Python >= 3.11
except ImportError:
    tomllib = None

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STYLE = os.path.join(REPO, "ds4/code/4_client_desktop/resource/style.css")
OUT_RES = os.path.join(REPO, "ds4/code/4_client_desktop/resource/themes")
OUT_USER = os.path.expanduser("~/.config/demonsaw/themes")
OMARCHY_SYSTEM = "/usr/share/omarchy/themes"
OMARCHY_USER = os.path.expanduser("~/.config/omarchy/themes")


def hex_to_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    """Mix color a toward b by t (0..1)."""
    return "#%02x%02x%02x" % tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def read_colors(path):
    if tomllib is not None:
        with open(path, "rb") as fp:
            return tomllib.load(fp)
    # Fallback: tiny INI-ish parser good enough for colors.toml
    data = {}
    with open(path) as fp:
        for line in fp:
            line = line.strip()
            m = re.match(r'^(\w+)\s*=\s*"([^"]*)"', line)
            if m:
                data[m.group(1)] = m.group(2)
    return data


def palette_for(theme_dir):
    """Map an Omarchy colors.toml onto Demonsaw's documented roles."""
    c = read_colors(os.path.join(theme_dir, "colors.toml"))
    bg = hex_to_rgb(c.get("background", "#ffffff"))
    fg = hex_to_rgb(c.get("foreground", "#000000"))
    lighter = hex_to_rgb(c.get("lighter_background", mix(bg, fg, 0.10)))
    darker = hex_to_rgb(c.get("darker_background", mix(bg, fg, 0.06)))

    pal = {
        # Core surfaces
        "background": c.get("background", "#ffffff"),
        "foreground": c.get("foreground", "#000000"),
        "highlight": mix(bg, fg, 0.04) if c.get("mode") == "dark" else c.get("lighter_background", "#ffffff"),
        "lighter_background": c.get("lighter_background", mix(bg, fg, 0.10)),
        "darker_background": c.get("darker_background", mix(bg, fg, 0.06)),
        "selection": c.get("selection", mix(bg, fg, 0.15)),
        "selection_fg": c.get("bright_foreground", c.get("foreground", "#000000")),
        "accent": c.get("accent", "#0c9bdc"),
        # Borders: mix background toward foreground
        "border": mix(bg, fg, 0.08),
        "border_medium": mix(bg, fg, 0.15),
        "border_strong": mix(bg, fg, 0.22),
        "border_soft": mix(bg, fg, 0.12),
        "border_input": mix(bg, fg, 0.18),
        # Greys
        "muted": mix(bg, fg, 0.45),
        "icon": mix(bg, fg, 0.55),
        "disabled": mix(bg, fg, 0.35),
        "hover": mix(bg, fg, 0.08),
        "scroll": mix(bg, fg, 0.40),
        "pane_line": mix(bg, fg, 0.22),
        # Status hues
        "red": c.get("red", "#f06b00"),
        "orange": c.get("orange", c.get("red", "#f06b00")),
        "yellow": c.get("yellow", "#e5c736"),
        "green": c.get("green", "#549e6a"),
        "cyan": c.get("cyan", "#2dd5b7"),
        "blue": c.get("blue", c.get("accent", "#0c9bdc")),
        "magenta": c.get("magenta", "#d2689c"),
    }

    # Hover/pressed accent (Demonsaw uses an orange for press feedback)
    pal["accent_hover"] = pal["orange"] if c.get("mode") == "dark" else mix(hex_to_rgb(pal["accent"]), (240, 107, 0), 0.4)

    return pal


# (regex, token) pairs in order; first match wins. Every documented color in
# style.css is covered here. Word-ish boundaries keep 6-digit patterns from
# biting into 8-digit hex values.
SUBSTITUTIONS = [
    # Text
    (r"#000000", "foreground"),
    # Background / highlights
    (r"#FFFFFF", "highlight"),
    (r"#F9F9F9", "background"),
    (r"#F4F4F4", "hover"),
    # Borders
    (r"#EDEDED", "border"),
    (r"#D8D8D8", "border_medium"),
    (r"#C9C9C9", "border_strong"),
    (r"#E0E0E0", "border_soft"),
    (r"#D0D0D1", "border_input"),
    # Greys
    (r"#797979", "muted"),
    (r"#5D5E5E", "icon"),
    (r"#B0B0B0", "disabled"),
    (r"#9D9C9C", "scroll"),
    # Accent
    (r"#0C9BDC", "accent"),
    (r"#f06b00", "accent_hover"),
    (r"#ceebf8", "selection"),
]


def _boundary(pattern):
    return r"(?<![0-9a-fA-F])" + pattern + r"(?![0-9a-fA-F])"


_COMBINED = re.compile(
    "|".join(_boundary(p) for p, _ in SUBSTITUTIONS), re.IGNORECASE
)


def render(template, pal):
    def repl(match):
        text = match.group(0)
        for pattern, token in SUBSTITUTIONS:
            if re.fullmatch(pattern, text, re.IGNORECASE):
                return pal[token]
        return text

    # Single pass: each original occurrence is replaced exactly once, so a
    # substituted value can never be re-matched by a later rule.
    return _COMBINED.sub(repl, template)


def collect_themes():
    found = {}
    for root in (OMARCHY_USER, OMARCHY_SYSTEM):
        if not os.path.isdir(root):
            continue
        for name in sorted(os.listdir(root)):
            colors = os.path.join(root, name, "colors.toml")
            if os.path.isfile(colors):
                found[name] = os.path.dirname(colors)
    return found


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--no-user", action="store_true", help="skip writing ~/.config/demonsaw/themes")
    args = ap.parse_args()

    with open(STYLE) as fp:
        template = fp.read()

    themes = collect_themes()
    if not themes:
        print("no Omarchy themes found", file=sys.stderr)
        return 1

    os.makedirs(OUT_RES, exist_ok=True)
    if not args.no_user:
        os.makedirs(OUT_USER, exist_ok=True)

    count = 0
    for name, theme_dir in themes.items():
        try:
            pal = palette_for(theme_dir)
        except Exception as ex:  # noqa: BLE001
            print(f"skip {name}: {ex}", file=sys.stderr)
            continue
        css = render(template, pal)
        safe = re.sub(r"[^a-z0-9._-]", "-", name.lower())
        with open(os.path.join(OUT_RES, safe + ".css"), "w") as fp:
            fp.write(f"/* Demonsaw theme: {name} (generated from Omarchy palette) */\n")
            fp.write(css)
        if not args.no_user:
            shutil.copyfile(os.path.join(OUT_RES, safe + ".css"), os.path.join(OUT_USER, safe + ".css"))
        print(f"generated {safe}.css")
        count += 1

    print(f"{count} themes written")
    return 0


if __name__ == "__main__":
    sys.exit(main())
