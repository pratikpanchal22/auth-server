/**
 * nthNode auth-server — theme management
 * Mirrors the store-front's mechanism: localStorage key "theme",
 * data-bs-theme attribute on <html>, system-preference fallback.
 */
(function () {

  function applyTheme(theme) {
    document.documentElement.setAttribute('data-bs-theme', theme);
    updateIcon(theme);
  }

  function updateIcon(theme) {
    var icon = document.getElementById('themeIcon');
    if (!icon) return;
    // Tabler icon element (admin pages)
    if (icon.tagName === 'I') {
      icon.className = theme === 'dark' ? 'ti ti-moon' : 'ti ti-sun';
    } else {
      // Span with emoji (user-facing pages)
      icon.textContent = theme === 'dark' ? '🌙' : '☀️';
    }
  }

  // Exposed globally so onclick="toggleTheme()" works
  window.toggleTheme = function () {
    var current = document.documentElement.getAttribute('data-bs-theme') || 'light';
    var next = current === 'dark' ? 'light' : 'dark';
    localStorage.setItem('theme', next);
    applyTheme(next);
  };

  // Sync icon once DOM is ready (theme itself is already applied via inline <head> script)
  document.addEventListener('DOMContentLoaded', function () {
    var t = document.documentElement.getAttribute('data-bs-theme') || 'light';
    updateIcon(t);
  });

  // Follow system preference changes only when the user hasn't made an explicit choice
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function (e) {
    if (!localStorage.getItem('theme')) {
      applyTheme(e.matches ? 'dark' : 'light');
    }
  });

})();
