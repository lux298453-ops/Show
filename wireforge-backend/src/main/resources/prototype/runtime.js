(function () {
  // Generated pages contain static markup. This shared runtime changes state only.
  function hosted() { return !!document.querySelector('script[data-wf-inject]'); }
  function editing() { return hosted() && !document.body.classList.contains('wf-interactive'); }
  function feedback(text) {
    var box = document.getElementById('wf-prototype-feedback');
    if (!box) return;
    box.textContent = text;
    box.hidden = false;
    clearTimeout(window.__wfFeedbackTimer);
    window.__wfFeedbackTimer = setTimeout(function () { box.hidden = true; }, 2200);
  }
  function close(modal) { if (modal) modal.classList.remove('wf-show'); }
  document.addEventListener('click', function (event) {
    if (editing()) return;
    var target = event.target.closest && event.target.closest('[data-nav],[data-modal],[data-action],[data-wf-tab],[data-wf-toggle],[data-wf-feedback],.wf-modal');
    if (!target) return;
    // The canvas owns navigation, back and modal events in preview mode.
    var routed = target.matches('[data-nav],[data-modal],[data-action]') || target.classList.contains('wf-modal');
    if (hosted() && routed) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    if (target.hasAttribute('data-modal')) {
      var modal = document.getElementById('wf-modal-' + target.getAttribute('data-modal'));
      if (modal) modal.classList.add('wf-show');
    } else if (target.classList.contains('wf-modal') && event.target === target) {
      close(target);
    } else if (/^(back|close)$/.test(target.getAttribute('data-action') || '')) {
      var owner = target.closest('.wf-modal');
      if (owner) close(owner);
      else if (parent !== window) parent.postMessage({ type: 'wf-back' }, '*');
      else feedback('已返回');
    } else if (target.hasAttribute('data-nav')) {
      if (parent !== window) parent.postMessage({ type: 'wf-nav', page: target.getAttribute('data-nav') }, '*');
      else feedback('前往「' + target.getAttribute('data-nav') + '」：请在项目预览中查看跨页跳转');
    } else if (target.hasAttribute('data-wf-tab')) {
      var group = target.getAttribute('data-wf-tab');
      document.querySelectorAll('[data-wf-tab]').forEach(function (item) {
        if (item.getAttribute('data-wf-tab') !== group) return;
        var active = item === target;
        item.classList.toggle('active', active);
        item.setAttribute('aria-pressed', String(active));
        item.setAttribute('aria-selected', String(active));
        var panel = document.getElementById(item.getAttribute('aria-controls') || '');
        if (panel) panel.hidden = !active;
      });
    } else if (target.hasAttribute('data-wf-toggle')) {
      target.setAttribute('aria-pressed', String(target.getAttribute('aria-pressed') !== 'true'));
    }
    if (target.hasAttribute('data-wf-feedback')) {
      feedback(target.getAttribute('data-wf-feedback'));
      if (target.getAttribute('data-wf-close-modal') === 'true') close(target.closest('.wf-modal'));
    }
  }, true);
  document.addEventListener('input', function (event) {
    if (editing() || !event.target.matches('[data-wf-search]')) return;
    var scope = document.getElementById(event.target.getAttribute('data-wf-search'));
    if (!scope) return;
    var query = event.target.value.trim().toLocaleLowerCase();
    scope.querySelectorAll('[data-wf-search-text]').forEach(function (item) {
      item.hidden = !(item.getAttribute('data-wf-search-text') || item.textContent).toLocaleLowerCase().includes(query);
    });
  });
  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && !editing() && !hosted()) {
      document.querySelectorAll('.wf-modal.wf-show').forEach(close);
    }
  });
})();
