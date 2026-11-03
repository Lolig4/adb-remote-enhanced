#!/usr/bin/env python3
"""drive.py <pkg> <steps> <outdir> — drive one app with uiautomator-derived taps and snapshot screens.

Safety: before AND after every action the focused window's package is checked. An action is only
sent while <pkg> (or a package explicitly allowed for that step) has focus; if anything else is in
front the run stops without touching it. Coordinates always come from a fresh uiautomator dump.

Steps file, one per line:
  snap <name>            dump + screenshot into <outdir>/<name>.{txt,png}
  tap <sel> [allow=pkg]  sel = text:<exact> | desc:<exact> | id:<entry-suffix>  (first match in <pkg>)
  tapopt <sel>           like tap, but silently skipped when not found
  find <sel>             scroll the app until <sel> is visible, then tap it
  home <sel>             KEYCODE_BACK until <sel> is visible again (max 4, guarded)
  back                   KEYCODE_BACK (only while <pkg> has focus)
  scroll                 drag inside the app's largest scrollable view (only while <pkg> has focus)
  launch                 force-stop-free launcher start of <pkg>
  wait <s>
"""
import re, subprocess, sys, time, os
import uiautomator2 as u2
PKG, STEPS, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
SERIAL = os.environ.get('SERIAL') or subprocess.run(['adb', 'get-serialno'], capture_output=True, text=True).stdout.strip()
U2 = u2.connect(SERIAL)
os.makedirs(OUT, exist_ok=True)
def sh(*a): return subprocess.run(['adb', 'shell', *a], capture_output=True, text=True).stdout
def focus():
    m = re.search(r'mCurrentFocus=Window\{\S+ \S+ ([^/}\s]+)', sh('dumpsys', 'window', 'displays'))
    return m.group(1) if m else None
LAST = ['']
def dump():
    s = U2.dump_hierarchy(); LAST[0] = s
    nodes = []
    for n in re.findall(r'<node [^>]*>', s):
        a = dict(re.findall(r'([\w-]+)="([^"]*)"', n))
        b = list(map(int, re.findall(r'\d+', a.get('bounds', '0,0,0,0'))))
        a['b'] = b; nodes.append(a)
    return nodes
def find(nodes, sel, pkgs):
    k, v = sel.split(':', 1)
    for a in nodes:
        if a.get('package') not in pkgs: continue
        if (k == 'text' and a.get('text') == v) or (k == 'desc' and a.get('content-desc') == v) or \
           (k == 'id' and a.get('resource-id', '').endswith('/' + v)):
            return a
def settle(timeout=4.0):
    """return as soon as two consecutive trees (0.15 s apart) are identical and differ from before"""
    before, t0, prev = LAST[0], time.time(), None
    while time.time() - t0 < timeout:
        time.sleep(0.15)
        cur = U2.dump_hierarchy()
        if cur == prev and cur != before: break
        prev = cur
    LAST[0] = prev or before
def guard(allowed, what):
    f = focus()
    if f not in allowed:
        print(f'STOP before/after "{what}": focus is {f}, not {allowed} — nothing sent'); sys.exit(1)
for line in open(STEPS):
    line = line.split('#')[0].strip()
    if not line: continue
    op, *rest = line.split(' ', 1); arg = rest[0] if rest else ''
    allow = {PKG}
    m = re.search(r' allow=(\S+)$', arg)
    if m: allow.add(m.group(1)); arg = arg[:m.start()]
    if op == 'launch':
        sh('monkey', '-p', PKG, '-c', 'android.intent.category.LAUNCHER', '1'); settle(6); guard({PKG}, line); continue
    if op == 'wait': time.sleep(float(arg)); continue
    if op == 'snap':
        nodes = dump()
        with open(f'{OUT}/{arg}.txt', 'w') as f:
            for a in nodes:
                if a.get('package') not in allow and a.get('package') != PKG: continue
                rid = a.get('resource-id', '').split('/')[-1]
                f.write(f"{a.get('class','').split('.')[-1]}\t{rid}\t{a.get('text','')!r}\t{a.get('content-desc','')!r}\t{a.get('bounds','')}\n")
        open(f'{OUT}/{arg}.png', 'wb').write(subprocess.run(['adb', 'exec-out', 'screencap', '-p'], capture_output=True).stdout)
        print('snap', arg, 'focus', focus()); continue
    guard(allow, line)
    if op in ('tap', 'tapopt'):
        a = find(dump(), arg, allow)
        if not a:
            if op == 'tapopt': print('skip', arg); continue
            print('NOTFOUND', arg); sys.exit(2)
        x1, y1, x2, y2 = a['b']; sh('input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    elif op == 'find':      # scroll (down, then up) until <sel> is fully visible, then tap it
        def visible(a):
            x1, y1, x2, y2 = a['b']
            return a.get('visible-to-user') != 'false' and y2 > y1 and y1 > 250 and y2 <= 2790
        a, direction, flips = None, 1, 0
        while True:
            nodes = dump(); a = find(nodes, arg, allow)
            if a and visible(a): break
            a = None
            sc = [n for n in nodes if n.get('package') == PKG and n.get('scrollable') == 'true']
            if not sc: break
            x1, y1, x2, y2 = sc[-1]['b']   # innermost scrollable
            cx = (x1 + x2) // 2; lo, hi = y1 + (y2-y1)*3//4, y1 + (y2-y1)//4
            if direction < 0: lo, hi = hi, lo
            before = LAST[0]
            guard(allow, line); sh('input', 'swipe', str(cx), str(lo), str(cx), str(hi), '300'); settle(1.5)
            if LAST[0] == before:          # end of list reached: no movement -> turn around once
                flips += 1; direction = -direction
                if flips > 1: break
        if not a: print('NOTFOUND', arg); sys.exit(2)
        guard(allow, line)
        x1, y1, x2, y2 = a['b']; sh('input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    elif op == 'home':      # back until <sel> (a main-screen marker) is on screen again
        for _ in range(4):
            if find(dump(), arg, {PKG}): break
            guard(allow, line); sh('input', 'keyevent', '4'); settle(2)
        else:
            print('NOT HOME', arg); sys.exit(2)
        print('ok', line); continue
    elif op == 'back':
        sh('input', 'keyevent', '4')
    elif op == 'scroll':
        sc = [a for a in dump() if a.get('package') == PKG and a.get('scrollable') == 'true']
        if not sc: print('no scrollable'); continue
        x1, y1, x2, y2 = sc[-1]['b']   # innermost scrollable
        cx = (x1 + x2) // 2; sh('input', 'swipe', str(cx), str(y1 + (y2-y1)*3//4), str(cx), str(y1 + (y2-y1)//4), '300')
    settle()
    f = focus()
    if f not in allow and f is not None:
        print(f'STOP after "{line}": focus moved to {f} — not touching it'); sys.exit(1)
    print('ok', line)
