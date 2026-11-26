/* 車機Play — offline APK build: aapt2 compile/link -> javac -> d8 -> dex inject -> zipalign -> apksigner */
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const ROOT = path.resolve(__dirname);
const JDK = 'C:/Program Files/Microsoft/jdk-17.0.21.21-hotspot/bin';
const SDK = 'C:/Users/kevih/AppData/Local/Android/Sdk';
const BT = path.join(SDK, 'build-tools/34.0.0');
const AJ = path.join(SDK, 'platforms/android-34/android.jar');
const OUT = path.join(ROOT, 'out');
const BUILD = path.join(ROOT, '.build');
const VERSION_NAME = process.argv[2] || '1.0';
const VERSION_CODE = process.argv[3] || '1';
const MIN_SDK = '24';
const TARGET_SDK = '34';
const KEY_ALIAS = 'chejiplay';
const PASS_FILE = path.join(ROOT, 'keystore.pass');
const KS_FILE = path.join(ROOT, 'chejiplay.p12');

function run(exe, args, opts = {}) {
  const r = spawnSync(exe, args, { encoding: 'utf8', cwd: opts.cwd || ROOT, env: { ...process.env, ...(opts.env || {}) } });
  const out = (r.stdout || '') + (r.stderr || '');
  if (r.error) throw new Error(`${path.basename(exe)} spawn failed: ${r.error.message}`);
  if (r.status !== 0) {
    console.log(out.split(/\r?\n/).slice(-40).join('\n'));
    throw new Error(`${path.basename(exe)} exited ${r.status}`);
  }
  return out;
}

fs.mkdirSync(BUILD, { recursive: true });
fs.mkdirSync(OUT, { recursive: true });

// ---- icons (Java2D) -------------------------------------------------------
run(path.join(JDK, 'javac.exe'), ['-encoding', 'UTF-8', '-d', BUILD, path.join(ROOT, 'tools/MakeIcon.java')]);
run(path.join(JDK, 'java.exe'), ['-cp', BUILD, 'MakeIcon', ROOT.replace(/\\/g, '/') + '/res']);

// ---- keystore -------------------------------------------------------------
if (!fs.existsSync(KS_FILE)) {
  if (!fs.existsSync(PASS_FILE)) {
    fs.writeFileSync(PASS_FILE, Buffer.concat([require('crypto').randomBytes(18)]).toString('base64'));
  }
  run(path.join(JDK, 'keytool.exe'), [
    '-genkeypair', '-keystore', KS_FILE, '-storetype', 'PKCS12',
    '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10950',
    '-alias', KEY_ALIAS, '-dname', 'CN=ChejiPlay, O=community',
    `-storepass:file`, PASS_FILE, `-keypass:file`, PASS_FILE
  ]);
  console.log('keystore created:', KS_FILE, '(password kept in keystore.pass)');
}
const KS_PASS = fs.readFileSync(PASS_FILE, 'utf8').trim();

// ---- resources ------------------------------------------------------------
const flat = path.join(BUILD, 'res.zip');
run(path.join(BT, 'aapt2.exe'), ['compile', '--dir', path.join(ROOT, 'res'), '-o', flat]);
const unaligned = path.join(BUILD, 'app-unsigned.apk');
const gen = path.join(BUILD, 'gen');
fs.rmSync(gen, { recursive: true, force: true });
fs.mkdirSync(gen, { recursive: true });
run(path.join(BT, 'aapt2.exe'), [
  'link', '-I', AJ, '--manifest', path.join(ROOT, 'AndroidManifest.xml'),
  '-R', flat, '--auto-add-overlay', '--java', gen,
  '--min-sdk-version', MIN_SDK, '--target-sdk-version', TARGET_SDK,
  '--version-code', VERSION_CODE, '--version-name', VERSION_NAME,
  '-o', unaligned
]);

// ---- java -----------------------------------------------------------------
const classes = path.join(BUILD, 'classes');
fs.rmSync(classes, { recursive: true, force: true });
fs.mkdirSync(classes, { recursive: true });
function walk(dir, acc = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, acc);
    else if (e.name.endsWith('.java')) acc.push(p);
  }
  return acc;
}
const sources = [...walk(path.join(ROOT, 'src')), path.join(gen, 'com/cheji/play/R.java')];
const javacOut = run(path.join(JDK, 'javac.exe'), [
  '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-nowarn',
  '-bootclasspath', AJ, '-classpath', unaligned, '-d', classes, ...sources
]);
console.log(javacOut.trim().split(/\r?\n/).filter(l => /error|warning: \[options\]/.test(l)).slice(0, 12).join('\n'));

const jar = path.join(BUILD, 'classes.jar');
fs.rmSync(jar, { force: true });
run(path.join(JDK, 'jar.exe'), ['--create', '--file', jar, '-C', classes, '.']);

// ---- dex ------------------------------------------------------------------
const dexdir = path.join(BUILD, 'dex');
fs.rmSync(dexdir, { recursive: true, force: true });
fs.mkdirSync(dexdir, { recursive: true });
run(path.join(JDK, 'java.exe'), [
  '-cp', path.join(BT, 'lib/d8.jar'), 'com.android.tools.r8.D8',
  '--release', '--min-api', MIN_SDK, '--lib', AJ, '--output', dexdir, jar
]);
if (!fs.existsSync(path.join(dexdir, 'classes.dex'))) throw new Error('classes.dex not produced');

// ---- pack -----------------------------------------------------------------
run(path.join(JDK, 'jar.exe'), ['--update', '--no-compress', '--file', unaligned, 'classes.dex'], { cwd: dexdir });
const aligned = path.join(BUILD, 'aligned.apk');
run(path.join(BT, 'zipalign.exe'), ['-f', '4', unaligned, aligned]);

const now = new Date();
const p2 = (n) => String(n).padStart(2, '0');
const localStamp = `${now.getFullYear()}${p2(now.getMonth() + 1)}${p2(now.getDate())}${p2(now.getHours())}${p2(now.getMinutes())}${p2(now.getSeconds())}`;
const stamp = process.env.BUILD_STAMP || localStamp;
const apkName = `車機Play_com.cheji.play_${VERSION_NAME}_${stamp}.apk`;
const finalApk = path.join(OUT, apkName);
fs.rmSync(finalApk, { force: true });
run(path.join(JDK, 'java.exe'), [
  '-cp', path.join(BT, 'lib/apksigner.jar'), 'com.android.apksigner.ApkSignerTool', 'sign',
  '--ks', KS_FILE, '--ks-type', 'PKCS12', '--ks-key-alias', KEY_ALIAS,
  '--ks-pass', 'env:KS_PASS', '--key-pass', 'env:KS_PASS',
  '--v1-signing-enabled', 'true', '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true',
  '--out', finalApk, aligned
], { env: { KS_PASS } });

// ---- verify ---------------------------------------------------------------
console.log(run(path.join(JDK, 'java.exe'), [
  '-cp', path.join(BT, 'lib/apksigner.jar'), 'com.android.apksigner.ApkSignerTool',
  'verify', '--print-certs', finalApk
]).split(/\r?\n/).filter(l => /verified|Signer #1 certificate DN|SHA-256/.test(l)).join('\n'));
const badging = run(path.join(BT, 'aapt2.exe'), ['dump', 'badging', finalApk]);
const pick = (re) => (badging.split(/\r?\n/).filter(l => re.test(l)) || []).join('\n');
console.log('--- badging ---\n' + pick(/package:|application-label:|sdkVersion|targetSdkVersion|launchable-activity|uses-permission/));
const tree = run(path.join(BT, 'aapt2.exe'), ['dump', 'resources', finalApk]);
for (const want of ['mipmap/ic_launcher', 'drawable/ic_carplay', 'drawable/ic_androidauto', 'drawable/ic_logo', 'drawable/ic_maps', 'drawable/ic_add', 'drawable/art_car_side', 'layout/activity_main', 'layout/item_module', 'layout/activity_sites', 'string/app_name']) {
  const hit = tree.includes(want);
  console.log(`${hit ? 'OK  ' : 'MISS'} ${want}`);
  if (!hit) throw new Error('resource missing in apk: ' + want);
}
const st = fs.statSync(finalApk);
console.log('APK:', finalApk, (st.size / 1024 / 1024).toFixed(2) + ' MB', st.mtime.toLocaleString('zh-TW'));
fs.writeFileSync(path.join(OUT, apkName + '.stamp'), stamp);

// 簽章本身比算出時間戳時已經晚了一秒，.idsig 的 mtime 會和檔名對不上。
// 三個檔案一起釘回時間戳那一刻，cp -p 之後下載目錄才不會出現「名字和時間不一樣」的疑義。
const stampDate = new Date(
  +stamp.slice(0, 4), +stamp.slice(4, 6) - 1, +stamp.slice(6, 8),
  +stamp.slice(8, 10), +stamp.slice(10, 12), +stamp.slice(12, 14));
for (const f of [finalApk, finalApk + '.idsig', path.join(OUT, apkName + '.stamp')]) {
  if (fs.existsSync(f)) {
    fs.utimesSync(f, stampDate, stampDate);
  }
}
console.log('mtimes pinned to stamp:', stamp);
