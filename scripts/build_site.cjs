/* 社群官網產生器：docs/content.json 是五種語言唯一的文字來源，一份模板渲出五個 index.html。
   跟 L.java 同一個道理——加一门語言是加一組欄位，不是複製一份頁面。

   node scripts/build_site.cjs

   輸出 docs/index.html（繁中，基準）與 docs/<dir>/index.html（其餘四語）。
   GitHub Pages 的來源設成 main 分支的 /docs 資料夾，所以頁面直接沿用 docs/ 裡現成的
   icon.png 與 screenshots/*.png——不複製、不重複進 git，也沒有 CI。
   content.json 裡每個語言的段落數量與條目數量必須完全一致，不對就直接失敗。 */
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const SITE = path.join(ROOT, 'docs');
const C = JSON.parse(fs.readFileSync(path.join(SITE, 'content.json'), 'utf8'));

const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const pngSize = (f) => {
  const b = fs.readFileSync(f);
  if (b.slice(1, 4).toString() !== 'PNG') throw new Error('不是 PNG：' + f);
  return { w: b.readUInt32BE(16), h: b.readUInt32BE(20) };
};

// ---- 圖片：頁面跟圖都在 docs/ 底下，Pages 以 /docs 為根，所以一個位元組都不用複製 ----
const shots = C.shots.map((file) => Object.assign({ file }, pngSize(path.join(SITE, 'screenshots', file))));

const langs = C.langs;
const KEYS = ['title', 'meta', 'brandSub', 'badge', 'h1', 'intro', 'ctaDownload', 'ctaInstall', 'ctaRepo',
  'promise', 'note', 'scope', 'galleryHeading', 'footnote', 'readmeSite', 'readmeBuild', 'footer'];
const SECTION_KEYS = ['h', 'lead', 'items', 'tail', 'linkText'];

function parityCheck() {
  const base = langs[0];
  for (const l of langs) {
    for (const k of KEYS) if (typeof l[k] !== 'string' || !l[k]) throw new Error(l.code + ' 缺 ' + k);
    if (l.shotCaps.length !== shots.length) throw new Error(l.code + ' 圖說數量不符');
    if (l.sections.length !== base.sections.length) throw new Error(l.code + ' 段落數量不符');
    l.sections.forEach((s, i) => {
      const b = base.sections[i];
      for (const k of SECTION_KEYS) {
        const has = (x) => x !== undefined && x !== null && x !== '' && (!Array.isArray(x) || x.length);
        if (has(s[k]) !== has(b[k])) throw new Error(l.code + ' 段落 ' + i + ' 的 ' + k + ' 有無不一致');
      }
      if (s.items.length !== b.items.length) throw new Error(l.code + ' 段落 ' + i + ' 條目數不符');
    });
  }
}
parityCheck();

const url = (l, sub) => C.site + (l.dir ? '/' + l.dir + '/' : '/') + (sub || '');

function head(l) {
  const alts = langs.map((x) => `<link rel="alternate" hreflang="${x.code}" href="${url(x)}">`).join('');
  const caps = l.sections;
  return `<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>${esc(l.title)}</title>
<meta name="description" content="${esc(l.meta)}">
<meta name="theme-color" content="${C.themeColor}">
<link rel="icon" href="${l.p}icon.png">
<link rel="stylesheet" href="${l.p}assets/site.css">
<link rel="canonical" href="${url(l)}">
${alts}
<meta property="og:type" content="website">
<meta property="og:title" content="${esc(l.title)}">
<meta property="og:description" content="${esc(l.meta)}">
<meta property="og:url" content="${url(l)}">
<meta property="og:image" content="${url(langs[0], 'screenshots/' + shots[0].file)}">
<meta name="twitter:card" content="summary_large_image">`;
}

function nav(l) {
  const links = langs.map((x) => `<a href="${l.p}${x.dir ? x.dir + '/' : ''}" lang="${x.code}" hreflang="${x.code}"${
    x === l ? ' aria-current="page"' : ''}>${esc(x.name)}</a>`).join('');
  return `<header>
<a class="brand" href="${l.p}"><img src="${l.p}icon.png" width="56" height="56" alt="">
<span><strong>${esc(C.project)}</strong><small>${esc(l.brandSub)}</small></span></a>
<nav class="languages" aria-label="Language">${links}</nav>
</header>`;
}

function hero(l) {
  return `<section class="hero">
<span class="badge">${esc(l.badge)} · <bdi>${C.version}</bdi></span>
<h1>${l.h1}</h1>
<p class="intro">${l.intro}</p>
<div class="actions">
<a class="button" href="${C.apkUrl}">${esc(l.ctaDownload)} <span aria-hidden="true">↓</span></a>
<a class="button secondary" href="#s0">${esc(l.ctaInstall)}</a>
<a class="button secondary" href="${C.repoUrl}">${esc(l.ctaRepo)}</a>
</div>
<p class="promise">${l.promise}</p>
<p class="note">${l.note}</p>
<p class="note scope"><strong>${l.scope}</strong></p>
</section>`;
}

function gallery(l) {
  const figs = shots.map((s, i) => `<figure>
<a href="${l.p}screenshots/${encodeURIComponent(s.file)}"><img src="${l.p}screenshots/${encodeURIComponent(s.file)}"
width="${s.w}" height="${s.h}" loading="lazy" alt="${esc(l.shotCaps[i])}"></a>
<figcaption>${esc(l.shotCaps[i])}</figcaption></figure>`).join('');
  return `<section class="gallery"><h2>${esc(l.galleryHeading)}</h2>
<div class="screens">${figs}</div>
<p class="note">${l.footnote}</p></section>`;
}

function sections(l) {
  return l.sections.map((s, i) => {
    const list = s.items.map((t) => `<li>${t}</li>`).join('');
    const kind = s.ordered ? 'ol' : 'ul';
    const link = s.linkText ? `<a href="${s.link}" target="_blank" rel="noopener">${esc(s.linkText)} ↗</a>` : '';
    return `<section class="card" id="s${i}"${i === 0 ? ' style="scroll-margin-top:12px"' : ''}>
<span class="eyebrow">${String(i + 1).padStart(2, '0')}</span>
<h2>${s.h}</h2>
${s.lead ? `<p>${s.lead}</p>` : ''}
${list ? `<${kind}>${list}</${kind}>` : ''}${s.tail ? `<p class="note">${s.tail}</p>` : ''}
${link}</section>`;
  }).join('');
}

function footer(l) {
  const nav2 = l.footerLinks.map((f) => `<a href="${f.u}">${esc(f.t)}</a>`).join('');
  return `<footer><nav>${nav2}</nav><p>${l.footer}</p></footer>`;
}

function page(l) {
  return `<!doctype html>
<html lang="${l.code}" dir="${l.dirAttr}"><head>${head(l)}
</head><body><main>
${nav(l)}
${hero(l)}
${gallery(l)}
${sections(l)}
${footer(l)}
</main></body></html>
`;
}

/* ---- 多語言 README：同一份 content.json 也渲出四個語言檔，避免手抄四份再各自漂移。
       繁中的 README.md 是維護者文件（含建置與發佈約定），不在這裡生成。 ---- */
const README_FILES = { 'zh-Hans': 'README.zh-CN.md', en: 'README.en.md', ja: 'README.ja.md', ko: 'README.ko.md' };
const md = (s) => String(s).replace(/<br\s*\/?>/g, ' ').replace(/<\/?bdi>/g, '').replace(/<\/?code>/g, '`');
const switcher = () => langs.map((x) => `[${x.name}](${x.dir ? README_FILES[x.dir] : 'README.md'})`).join(' · ');

function readme(l) {
  const gallery = `## ${l.galleryHeading}\n\n` +
    shots.map((s, i) => `- [${md(l.shotCaps[i])}](docs/screenshots/${encodeURI(s.file)})`).join('\n') +
    `\n\n${md(l.footnote)}\n`;
  const body = l.sections.map((s) => {
    const list = s.items.map((t, i) => `${s.ordered ? String(i + 1) + '.' : '-'} ${md(t)}`).join('\n');
    return `## ${md(s.h)}\n\n${s.lead ? md(s.lead) + '\n\n' : ''}${list}` +
      (s.tail ? `\n\n${md(s.tail)}` : '') + (s.linkText ? `\n\n[${s.linkText}](${s.link})` : '');
  }).join('\n\n');
  return `<img src="docs/icon.png" width="72" height="72" alt="${esc(C.project)}">

# ${C.project}（${C.package}）

${switcher()}

**${l.readmeSite}** <${C.site}>

${md(l.h1)} — ${md(l.intro)}

[${l.ctaDownload}](${C.apkUrl}) ／ [${l.ctaRepo}](${C.repoUrl}) ／ [Releases](${C.releasesUrl})

${md(l.promise)}

${md(l.note)}

**${md(l.scope)}**

${body}

${gallery}
---

${md(l.readmeBuild)}

${md(l.footer)}
`;
}

let written = 0;
let readmes = 0;
for (const l of langs) {
  l.p = l.dir ? '../' : './';
  const out = path.join(SITE, l.dir || '.', 'index.html');
  fs.mkdirSync(path.dirname(out), { recursive: true });
  fs.writeFileSync(out, page(l));
  written++;
  if (README_FILES[l.dir]) {
    fs.writeFileSync(path.join(ROOT, README_FILES[l.dir]), readme(l));
    readmes++;
  }
}
fs.writeFileSync(path.join(SITE, '.nojekyll'), '');
console.log('PAGES', written, 'READMES', readmes,
  'SHOTS', shots.map((s) => s.file + ' ' + s.w + 'x' + s.h).join(' | '),
  'BYTES', langs.map((l) => (l.dir || 'root') + '=' + fs.statSync(path.join(SITE, l.dir || '.', 'index.html')).size).join(' '));
