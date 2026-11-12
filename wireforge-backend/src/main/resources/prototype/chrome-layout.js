(function () {
  // Mark existing layout branches; keep their DOM boxes, editing identities and event handlers.
  document.querySelectorAll('[data-wf-shared-chrome=navigation]').forEach(function (bar) {
    var selector = '[data-wf-chrome-part=item],[data-nav-item]';
    var items = Array.from(bar.querySelectorAll(selector)).filter(function (item) {
      return item.closest('[data-wf-shared-chrome=navigation]') === bar
        && !item.parentElement.closest(selector);
    });
    if (items.length < 2) return;
    var marked = new Set();
    function mark(node, layout) {
      marked.add(node);
      node.setAttribute('data-wf-chrome-layout', layout);
    }
    function branches(owner, entries, root) {
      mark(owner, root ? 'row' : 'group');
      owner.style.setProperty('--wf-chrome-columns', String(entries.length));
      Array.from(owner.children).forEach(function (child) {
        if (entries.includes(child)) return;
        var nested = entries.filter(function (item) { return child.contains(item); });
        if (nested.length) branches(child, nested, false);
        else if (child.matches('.home-indicator,[data-wf-chrome-part=indicator]')) {
          mark(child, 'indicator');
        } else if (!child.matches('style,script')) mark(child, 'decoration');
      });
    }
    branches(bar, items, true);
    bar.querySelectorAll('[data-wf-chrome-layout]').forEach(function (node) {
      if (!marked.has(node)) {
        node.removeAttribute('data-wf-chrome-layout');
        node.style.removeProperty('--wf-chrome-columns');
      }
    });
    if (!bar.querySelector('[data-wf-chrome-layout=indicator]')) {
      var indicator = document.createElement('span');
      indicator.setAttribute('data-wf-chrome-layout', 'indicator');
      indicator.setAttribute('data-wf-chrome-part', 'indicator');
      indicator.setAttribute('aria-hidden', 'true');
      bar.appendChild(indicator);
    }
  });
})();
