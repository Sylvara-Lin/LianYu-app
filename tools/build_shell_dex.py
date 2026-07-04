#!/usr/bin/env python3
"""Generate Java stubs for ALL merged-manifest classes and dex them."""
import xml.etree.ElementTree as ET, subprocess, sys, os, shutil, re

PROJECT = r"C:\Users\27194\Desktop\LianYu"
SDK = os.path.join(os.environ["LOCALAPPDATA"], "Android", "Sdk")
ANDROID_JAR = os.path.join(SDK, "platforms", "android-34", "android.jar")
BT = sorted(os.listdir(os.path.join(SDK, "build-tools")))[-1]
D8 = os.path.join(SDK, "build-tools", BT, "d8.bat")
SRC = os.path.join(PROJECT, "app", "build", "tmp", "shell_src")
OUT = os.path.join(PROJECT, "app", "build", "tmp", "shell_classes")
DEX_DIR = os.path.join(PROJECT, "app", "build", "tmp", "minimal_dex_out")
MANIFEST = os.path.join(PROJECT, "app", "build", "intermediates", "merged_manifest", "release", "processReleaseMainManifest", "AndroidManifest.xml")

# Read merged manifest
ns = '{http://schemas.android.com/apk/res/android}'
tree = ET.parse(MANIFEST)
root = tree.getroot()
app = root.find('application')

all_classes = set()
if app is not None:
    for attr in ['name', 'appComponentFactory']:
        v = app.get(ns + attr)
        if v: all_classes.add(v)
    for tag_name in ['activity', 'service', 'receiver', 'provider']:
        for el in root.findall(f'.//{tag_name}'):
            v = el.get(ns + 'name')
            if v: all_classes.add(v)

# Determine superclass from manifest tag
def get_tag(cls):
    if app is not None:
        if app.get(ns + 'name') == cls: return 'Application'
        if app.get(ns + 'appComponentFactory') == cls: return 'AppComponentFactory'
    for tag_name in ['provider', 'service', 'receiver', 'activity']:
        for el in root.findall(f'.//{tag_name}'):
            if el.get(ns + 'name') == cls:
                return {'provider':'ContentProvider','service':'Service','receiver':'BroadcastReceiver','activity':'Activity'}[tag_name]
    return 'Object'

SUPER_MAP = {
    'Application': 'android.app.Application',
    'AppComponentFactory': 'android.app.AppComponentFactory',
    'ContentProvider': 'android.content.ContentProvider',
    'Service': 'android.app.Service',
    'BroadcastReceiver': 'android.content.BroadcastReceiver',
    'Activity': 'android.app.Activity',
    'Object': 'java.lang.Object',
}

CONTENT_PROVIDER_BODY = """    public boolean onCreate() { return true; }
    public android.database.Cursor query(android.net.Uri u, String[] p, String s, String[] a, String o) { return null; }
    public String getType(android.net.Uri u) { return null; }
    public android.net.Uri insert(android.net.Uri u, android.content.ContentValues v) { return null; }
    public int delete(android.net.Uri u, String s, String[] a) { return 0; }
    public int update(android.net.Uri u, android.content.ContentValues v, String s, String[] a) { return 0; }"""

SERVICE_BODY = """    public android.os.IBinder onBind(android.content.Intent i) { return null; }"""

RECEIVER_BODY = """    public void onReceive(android.content.Context c, android.content.Intent i) {}"""

# Clear and generate
shutil.rmtree(SRC, ignore_errors=True)
os.makedirs(SRC)

for cls in sorted(all_classes):
    tag = get_tag(cls)
    super_cls = SUPER_MAP[tag]
    pkg = '.'.join(cls.split('.')[:-1])
    simple = cls.split('.')[-1]
    
    # Handle nested (inner) classes like ConstraintProxy$BatteryChargingProxy
    if '$' in simple:
        outer = simple.split('$')[0]
        inner = simple.split('$')[1]
        path = os.path.join(SRC, pkg.replace('.', '/'), f"{outer}.java")
        # We'll handle inner classes together with their outer class
        continue
    
    path = os.path.join(SRC, pkg.replace('.', '/'), f"{simple}.java")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    
    if tag == 'ContentProvider':
        body = CONTENT_PROVIDER_BODY
    elif tag == 'Service':
        body = SERVICE_BODY
    elif tag == 'BroadcastReceiver':
        body = RECEIVER_BODY
    elif tag == 'Activity' or tag == 'Application' or tag == 'AppComponentFactory':
        body = ""
    else:
        body = ""
    
    src = f"package {pkg};\npublic class {simple} extends {super_cls} {{\n{body}\n}}\n"
    with open(path, 'w') as f:
        f.write(src)

# Handle inner classes (ConstraintProxy subclasses)
# We need to add static inner classes to the outer class files
inner_classes = [c for c in all_classes if '$' in c]
for cls in inner_classes:
    pkg = '.'.join(cls.split('.')[:-1])
    outer = cls.split('.')[-1].split('$')[0]
    inner = cls.split('.')[-1].split('$')[1]
    path = os.path.join(SRC, pkg.replace('.', '/'), f"{outer}.java")
    
    if os.path.exists(path):
        with open(path, 'r') as f:
            content = f.read()
        # Add inner class before closing brace
        inner_def = f"\n    public static class {inner} extends android.content.BroadcastReceiver {{\n        public void onReceive(android.content.Context c, android.content.Intent i) {{}}\n    }}\n"
        content = content.rstrip()[:-1] + inner_def + "}\n"
        with open(path, 'w') as f:
            f.write(content)

print(f"Generated {len(set(os.listdir(SRC)))} source files")

# Compile
os.makedirs(OUT, exist_ok=True)
java_files = []
for root_dir, dirs, files in os.walk(SRC):
    for f in files:
        if f.endswith('.java'):
            java_files.append(os.path.join(root_dir, f))

print(f"Compiling {len(java_files)} files...")
result = subprocess.run(
    ["javac", "-d", OUT, "-cp", ANDROID_JAR, "-source", "1.8", "-target", "1.8"] + java_files,
    capture_output=True, text=True
)
if result.returncode != 0:
    # Try to compile one by one to find errors
    for jf in java_files:
        r = subprocess.run(["javac", "-d", OUT, "-cp", ANDROID_JAR, "-source", "1.8", "-target", "1.8", jf],
                         capture_output=True, text=True)
        if r.returncode != 0:
            print(f"  FAIL: {os.path.basename(jf)}: {r.stderr.strip()[:200]}")
    sys.exit(1)

print("  Compile OK")

# D8
shutil.rmtree(DEX_DIR, ignore_errors=True)
os.makedirs(DEX_DIR)
class_files = []
for root_dir, dirs, files in os.walk(OUT):
    for f in files:
        if f.endswith('.class'):
            class_files.append(os.path.join(root_dir, f))

print(f"D8: {len(class_files)} classes...")
result = subprocess.run(
    ["cmd.exe", "/c", D8, "--lib", ANDROID_JAR, "--output", DEX_DIR, "--min-api", "26"] + class_files,
    capture_output=True, text=True, timeout=60
)
if result.returncode != 0:
    print("D8 ERROR:", result.stderr[-500:])
    sys.exit(1)

dex = os.path.join(DEX_DIR, "classes.dex")
final = os.path.join(PROJECT, "app", "build", "tmp", "minimal.dex")
if os.path.exists(dex):
    shutil.copy(dex, final)
    print(f"  DEX: {os.path.getsize(final)} bytes")
    # Show compressed estimate
    import zlib
    compressed = zlib.compress(open(final,'rb').read(), 9)
    print(f"  Compressed: ~{len(compressed)} bytes")
