(function () {
  if (window.__wfControls) { window.__wfControls.normalize(); return; }
  var selector = '[data-wf-control],[role="switch"],.wf-sw,[data-wf-toggle],input[type="checkbox"],input[type="radio"]';
  function editing() { return !!document.querySelector('script[data-wf-inject]') && !document.body.classList.contains('wf-interactive'); }
  function native(el) { return el.tagName === 'INPUT' && /^(checkbox|radio)$/.test(el.type); }
  function disabled(el) { return el.disabled || el.getAttribute('aria-disabled') === 'true'; }
  function kind(el) {
    var declared = el.getAttribute('data-wf-control');
    if (/^(switch|checkbox|radio)$/.test(declared || '')) return declared;
    if (el.getAttribute('role') === 'switch' || el.classList.contains('wf-sw')) return 'switch';
    if (native(el)) return el.type;
    // Old generated switch buttons are migrated; favorite/like icon buttons stay unchanged.
    if (el.hasAttribute('data-wf-toggle') && (el.classList.contains('switch') || el.classList.contains('toggle') || /开关/.test(el.getAttribute('aria-label') || ''))) return 'switch';
    return null;
  }
  function checked(el) {
    if (native(el)) return el.checked;
    if (el.hasAttribute('aria-checked')) return el.getAttribute('aria-checked') === 'true';
    if (el.hasAttribute('aria-pressed')) return el.getAttribute('aria-pressed') === 'true';
    if (el.hasAttribute('data-state')) return el.getAttribute('data-state') === 'on';
    return el.classList.contains('on') || el.classList.contains('checked') || el.classList.contains('active');
  }
  function state(el, value) {
    el.setAttribute('aria-checked', String(value));
    if (native(el)) {
      el.checked = value;
      if (value) el.setAttribute('checked', ''); else el.removeAttribute('checked');
    }
    if (el.getAttribute('data-wf-control') === 'switch') {
      el.setAttribute('aria-pressed', String(value));
      el.setAttribute('data-state', value ? 'on' : 'off');
    }
  }
  function normalize() {
    document.querySelectorAll(selector).forEach(function (el) {
      var type = kind(el);
      if (!type) return;
      var value = checked(el);
      el.setAttribute('data-wf-control', type);
      if (!native(el)) {
        el.setAttribute('role', type);
        if (el.tagName === 'BUTTON') el.setAttribute('type', 'button');
        else if (!el.hasAttribute('tabindex')) el.setAttribute('tabindex', '0');
      }
      if (!el.hasAttribute('data-wf-control-version')) {
        // Initialize geometry once. Subsequent editor moves/resizes must survive save/reload.
        el.style.width = type === 'switch' ? '44px' : '20px';
        el.style.height = type === 'switch' ? '25px' : '20px';
        el.setAttribute('data-wf-control-version', '1');
      }
      state(el, value);
    });
  }
  function radioGroup(el) { return el.getAttribute('data-wf-radio-group') || el.getAttribute('name') || ''; }
  function update(el) {
    var type = el.getAttribute('data-wf-control');
    if (type === 'radio') {
      var group = radioGroup(el);
      document.querySelectorAll('[data-wf-control="radio"]').forEach(function (peer) {
        if (peer !== el && group && radioGroup(peer) === group && peer.form === el.form) state(peer, false);
      });
      state(el, true);
    } else state(el, native(el) ? el.checked : !checked(el));
  }
  document.addEventListener('click', function (event) {
    var el = event.target.closest && event.target.closest('[data-wf-control]');
    if (!el) return;
    if (editing()) { if (native(el)) event.preventDefault(); return; }
    if (!native(el)) event.preventDefault();
    // One owner: neither the old .wf-sw handler nor the generic toggle runtime may toggle again.
    event.stopImmediatePropagation();
    if (disabled(el)) { event.preventDefault(); return; }
    update(el);
  }, true);
  document.addEventListener('change', function (event) {
    if (editing() || !native(event.target) || !event.target.hasAttribute('data-wf-control')) return;
    update(event.target);
  }, true);
  document.addEventListener('keydown', function (event) {
    var el = event.target.closest && event.target.closest('[data-wf-control]');
    if (!el || editing() || disabled(el) || native(el) || el.tagName === 'BUTTON') return;
    if (event.key === ' ' || event.key === 'Enter') { event.preventDefault(); el.click(); }
  });
  window.__wfControls = { normalize: normalize };
  function start() {
    normalize();
    // Controls inserted by the canvas or an opened local panel use the same implementation.
    if (typeof MutationObserver !== 'undefined') new MutationObserver(function (changes) {
      var added = changes.some(function (change) {
        return Array.from(change.addedNodes).some(function (node) {
          return node.nodeType === 1 && (node.matches(selector) || node.querySelector(selector));
        });
      });
      if (added) normalize();
    }).observe(document.body, { childList: true, subtree: true });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();
})();
