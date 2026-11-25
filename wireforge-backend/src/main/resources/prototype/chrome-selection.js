(function () {
  // Recover page-local palettes on saved v1-v3 chrome without changing editable nodes or links.
  var styles = Array.from(document.querySelectorAll('style'));
  var originalStyles = styles.filter(function (style) {
    return !style.hasAttribute('data-wf-chrome-style') && !style.hasAttribute('data-wf-chrome-selection-style');
  });
  var states = ['active', 'selected', 'on', 'current', 'wf-nav-active', 'is-active'];
  function declaration(node, property, decoration) {
    var result = '';
    var pattern = new RegExp('(?:^|;)\\s*' + property + '(?:-color)?\\s*:\\s*([^;]+)', 'ig');
    originalStyles.forEach(function (style) {
      var rules = style.textContent.matchAll(/([^{}]+)\{([^{}]*)\}/g);
      for (var rule of rules) for (var selector of rule[1].split(',')) {
        try {
          var target = selector.trim(), pseudo = /::?(before|after)$/i.test(target);
          if (pseudo !== !!decoration) continue;
          if (pseudo) target = target.replace(/::?(before|after)$/i, '');
          if (!node.matches(target)) continue;
          pattern.lastIndex = 0;
          for (var found of rule[2].matchAll(pattern)) result = found[1];
        } catch (_) { /* Unsupported selectors do not define this component. */ }
      }
    });
    pattern.lastIndex = 0;
    if (!decoration) for (var inline of (node.getAttribute('style') || '').matchAll(pattern)) result = inline[1];
    return result.replace(/!important/g, '').trim();
  }
  function variable(name) {
    var result = '', pattern = new RegExp(name + '\\s*:\\s*([^;}]+)', 'g');
    styles.forEach(function (style) {
      pattern.lastIndex = 0;
      for (var match of style.textContent.matchAll(pattern)) result = match[1];
    });
    return result;
  }
  function visiblePaint(background) {
    return background && !/^(transparent|none|inherit|initial|unset)$/i.test(background)
      && !/rgba\([^)]*,\s*0(?:\.0+)?\s*\)/i.test(background);
  }
  function luminance(value) {
    var match = value.match(/#[0-9a-f]{3,8}\b|rgba?\([0-9.,%\s]+\)|\b(?:white|black)\b/i);
    if (!match) return -1;
    var colour = match[0].toLowerCase(), rgb;
    if (colour === 'white') return 1;
    if (colour === 'black') return 0;
    if (colour[0] === '#') {
      var hex = colour.slice(1);
      if (hex.length === 3 || hex.length === 4) hex = hex.slice(0, 3).split('').map(function (c) { return c + c; }).join('');
      rgb = [0, 2, 4].map(function (i) { return parseInt(hex.slice(i, i + 2), 16); });
    } else rgb = colour.slice(colour.indexOf('(') + 1, -1).split(',').slice(0, 3).map(function (c) { return parseFloat(c) * (c.includes('%') ? 2.55 : 1); });
    return rgb.reduce(function (sum, channel, i) {
      var c = channel / 255;
      return sum + [0.2126, 0.7152, 0.0722][i] * (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
    }, 0);
  }
  document.querySelectorAll('[data-wf-shared-chrome=navigation]').forEach(function (bar) {
    if (bar.getAttribute('data-wf-chrome-palette') === 'local') return;
    var active = bar.querySelector('[data-wf-chrome-part=item][aria-current=page]');
    var normal = Array.from(bar.querySelectorAll('[data-wf-chrome-part=item]')).find(function (node) { return node !== active; });
    var ink = (normal && declaration(normal, 'color')) || declaration(bar, 'color') || '#8b8b96';
    var activeInk = declaration(bar, 'color') || variable('--wf-chrome-active') || '#ff8c27';
    var background = '', radius = '';
    if (active) {
      var original = active.getAttribute('class');
      try {
        states.forEach(function (state) { active.classList.add(state); });
        activeInk = declaration(active, 'color') || activeInk;
        background = declaration(active, 'background');
        radius = declaration(active, 'border-radius');
        if (!visiblePaint(background)) {
          background = declaration(active, 'background', true);
          radius = declaration(active, 'border-radius', true) || radius;
        }
      } finally {
        if (original === null) active.removeAttribute('class'); else active.setAttribute('class', original);
      }
    }
    var visible = visiblePaint(background);
    bar.style.setProperty('--wf-local-nav-ink', ink);
    var border = declaration(bar, 'border-top').match(/#[0-9a-f]{3,8}\b|rgba?\([0-9.,%\s]+\)|\b(?:white|black)\b/i);
    bar.style.setProperty('--wf-local-nav-border', border ? border[0] : 'transparent');
    bar.style.setProperty('--wf-local-active-bg', visible ? background : 'transparent');
    if (!variable('--wf-chrome-active-radius'))
      bar.style.setProperty('--wf-chrome-active-radius', /^\d+(?:\.\d+)?px$/.test(radius) ? radius : '10px');
    if (!visible) {
      var fg = luminance(activeInk), bg = luminance(declaration(bar, 'background') || variable('--wf-chrome-nav-bg'));
      if (fg >= 0 && bg >= 0 && (Math.max(fg, bg) + 0.05) / (Math.min(fg, bg) + 0.05) < 1.05) {
        activeInk = bg < 0.3 ? '#a8c7fa' : '#365ac7';
      }
    }
    bar.style.setProperty('--wf-local-active-ink', activeInk);
    bar.setAttribute('data-wf-chrome-palette', 'local');
  });
})();
