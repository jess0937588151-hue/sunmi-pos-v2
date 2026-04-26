(function () {
  if (window.__sunmiAutoPrintAdapterInjected__) return;
  window.__sunmiAutoPrintAdapterInjected__ = true;

  function safeText(node) {
    return ((node && (node.innerText || node.textContent)) || '').trim();
  }

  function looksLikePrintButton(node) {
    var text = safeText(node);
    if (!text) return false;
    return /列印|打印|顧客單|廚房單|收據|出單/.test(text);
  }

  function bindButton(node) {
    if (!node || node.__sunmiBound__) return;
    if (!(node.tagName === 'BUTTON' || node.tagName === 'A' || node.getAttribute('role') === 'button' || node.onclick)) return;
    if (!looksLikePrintButton(node)) return;

    node.__sunmiBound__ = true;
    node.addEventListener('click', function () {
      setTimeout(function () {
        try {
          if (window.SunmiPrinterBridge && window.SunmiPrinterBridge.isReady()) {
            window.SunmiPrinterBridge.printReceipt(document.title || 'Web Print', document.body ? document.body.innerText : '');
          }
        } catch (e) {
          console.log('Sunmi auto-print adapter error', e);
        }
      }, 250);
    }, true);
  }

  function scan(root) {
    var scope = root || document;
    var nodes = scope.querySelectorAll ? scope.querySelectorAll('button, a, [role="button"], .btn, .button') : [];
    for (var i = 0; i < nodes.length; i++) bindButton(nodes[i]);
  }

  scan(document);

  var observer = new MutationObserver(function (mutations) {
    mutations.forEach(function (mutation) {
      if (mutation.addedNodes) {
        for (var i = 0; i < mutation.addedNodes.length; i++) {
          var node = mutation.addedNodes[i];
          if (node && node.nodeType === 1) {
            bindButton(node);
            scan(node);
          }
        }
      }
    });
  });

  observer.observe(document.documentElement || document.body, { childList: true, subtree: true });
})();
