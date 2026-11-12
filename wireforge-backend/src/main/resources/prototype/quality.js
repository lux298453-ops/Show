/* Conservative local checks. Aesthetic differences alone never spend a repair call. */
() => {
  const issues = [], seen = new Set(), width = innerWidth, height = innerHeight;
  const add = (kind, el, note) => {
    const id = el.getAttribute('data-wf-id') || el.id || el.getAttribute('aria-label') || el.tagName.toLowerCase();
    const key = kind + ':' + id;
    if (!seen.has(key) && issues.length < 8) { seen.add(key); issues.push(kind + ' [' + id + '] ' + note); }
  };
  const visible = el => {
    if (el.closest('[hidden],[aria-hidden="true"]')) return false;
    for (let n = el; n; n = n.parentElement) {
      const s = getComputedStyle(n);
      if (s.display === 'none' || s.visibility === 'hidden' || Number(s.opacity) === 0) return false;
    }
    const r = el.getBoundingClientRect();
    return r.width > 2 && r.height > 2;
  };
  const scrolls = (el, axis) => {
    for (let n = el.parentElement; n && n !== document.body && n !== document.documentElement; n = n.parentElement) {
      const s = getComputedStyle(n);
      if (/auto|scroll/.test(axis === 'x' ? s.overflowX : s.overflowY)) return true;
    }
    return false;
  };
  const nodes = [...document.querySelectorAll('button,a,input,textarea,select,img,h1,h2,h3,p,[data-wf-role="text"]')]
    .filter(el => !el.closest('svg') && visible(el));
  for (const el of nodes) {
    const r = el.getBoundingClientRect(), s = getComputedStyle(el);
    if (el.tagName === 'IMG' && el.complete && !el.naturalWidth) add('图片加载失败', el, '替换为清单中的有效素材，保留图片容器');
    // Ignore tiny decorative bleeds, carousels and explicit scrollable content areas.
    const clippedX = Math.max(0, -r.left) + Math.max(0, r.right - width);
    const clippedY = Math.max(0, -r.top) + Math.max(0, r.bottom - height);
    if ((clippedX > 8 && clippedX > r.width * .2 && !scrolls(el, 'x')) ||
        (clippedY > 12 && clippedY > r.height * .3 && !scrolls(el, 'y'))) add('内容超出画布', el, '主要内容或操作入口超出目标画布，需调整布局或设置明确的内容滚动区');
    if (el.textContent.trim() && /hidden|clip/.test(s.overflowX) && s.textOverflow !== 'ellipsis' && el.scrollWidth > el.clientWidth + 8) {
      add('文字被裁切', el, '文字容器横向空间不足，调整间距或允许正文换行');
    }
    // Only solid clipping counts; intentional line clamping and small line-height differences do not.
    if (el.textContent.trim() && /hidden|clip/.test(s.overflowY) && s.webkitLineClamp === 'none' && el.scrollHeight > el.clientHeight + 12) {
      add('文字被裁切', el, '文字容器高度不足');
    }
  }
  const controls = nodes.filter(el => el.matches('button,a,input,textarea,select') && !el.closest('[data-wf-artwork-key]'));
  for (let i = 0; i < controls.length; i++) for (let j = i + 1; j < controls.length; j++) {
    const a = controls[i], b = controls[j];
    if (a.contains(b) || b.contains(a)) continue;
    const ar = a.getBoundingClientRect(), br = b.getBoundingClientRect();
    if (ar.bottom < 0 || br.bottom < 0 || ar.top >= height || br.top >= height) continue;
    if (scrolls(a, 'x') || scrolls(a, 'y') || scrolls(b, 'x') || scrolls(b, 'y')) continue;
    const w = Math.min(ar.right, br.right) - Math.max(ar.left, br.left);
    const h = Math.min(ar.bottom, br.bottom) - Math.max(ar.top, br.top);
    if (w > 8 && h > 8 && w * h > Math.min(ar.width * ar.height, br.width * br.height) * .35) {
      add('操作控件重叠', a, '与 ' + (b.getAttribute('data-wf-id') || b.textContent.trim().slice(0, 24) || b.tagName) + ' 大面积重叠');
    }
  }
  return issues;
}
