// Fills in details from config.js and follows the app's look (?theme=dark|light).
(function () {
  var legal = window.LEGAL || {};
  var theme = new URLSearchParams(location.search).get("theme");
  if (theme === "dark" || theme === "light") document.documentElement.setAttribute("data-theme", theme);
  // Inside the app the page sits under the app's own bar, so the site header is hidden.
  if (new URLSearchParams(location.search).get("embed") === "1") document.documentElement.classList.add("embed");

  document.querySelectorAll("[data-legal]").forEach(function (el) {
    var key = el.getAttribute("data-legal");
    var value = (legal[key] || "").trim();
    if (value) {
      el.textContent = value;
    } else {
      el.textContent = "[to fill in: " + key + "]";
      el.classList.add("todo");
    }
  });

  document.querySelectorAll("a[data-legal-mail]").forEach(function (a) {
    var email = (legal.email || "").trim();
    var subject = a.getAttribute("data-legal-mail");
    if (email) {
      a.href = "mailto:" + email + (subject ? "?subject=" + encodeURIComponent(subject) : "");
      if (!a.textContent.trim()) a.textContent = email;
    } else {
      a.removeAttribute("href");
      a.textContent = "[to fill in: email]";
      a.classList.add("todo");
    }
  });
})();
