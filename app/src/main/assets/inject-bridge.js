(function () {
  if (window.__sunmiBridgeInjected__) return;
  window.__sunmiBridgeInjected__ = true;

  function bodyText() {
    var text = '';
    if (document && document.body && document.body.innerText) {
      text = document.body.innerText;
    }
    return (text || '').trim();
  }

  window.SunmiPrinterBridge = {
    isReady: function () {
      return !!(window.SunmiPrinter && window.SunmiPrinter.isPrinterReady && window.SunmiPrinter.isPrinterReady());
    },
    printText: function (text) {
      if (window.SunmiPrinter && window.SunmiPrinter.printText) {
        window.SunmiPrinter.printText(String(text || ''));
      }
    },
    printReceipt: function (title, body) {
      if (window.SunmiPrinter && window.SunmiPrinter.printReceipt) {
        window.SunmiPrinter.printReceipt(String(title || document.title || 'Receipt'), String(body || bodyText()));
      }
    },
    printHtml: function (title, selector) {
      var node = selector ? document.querySelector(selector) : document.body;
      var html = node ? node.innerHTML : document.body.innerHTML;
      if (window.SunmiPrinter && window.SunmiPrinter.printHtml) {
        window.SunmiPrinter.printHtml(String(title || document.title || 'Receipt'), String(html || ''));
      }
    },
    printPosReceipt: function (jsonObj) {
      if (window.SunmiPrinter && window.SunmiPrinter.printPosReceipt) {
        window.SunmiPrinter.printPosReceipt(JSON.stringify(jsonObj || {}));
      }
    },
    printReceiptJson: function (payload) {
      if (window.SunmiPrinter && window.SunmiPrinter.printReceiptJson) {
        window.SunmiPrinter.printReceiptJson(JSON.stringify(payload || {}));
      }
    },
    printCurrentPage: function () {
      if (window.SunmiPrinter && window.SunmiPrinter.printCurrentPage) {
        window.SunmiPrinter.printCurrentPage();
      }
    }
  };

  var originalPrint = window.print;
  window.print = function () {
    try {
      if (window.SunmiPrinterBridge) {
        window.SunmiPrinterBridge.printReceipt(document.title || 'Web Print', bodyText());
        return;
      }
    } catch (e) {
      console.log('Sunmi print override failed', e);
    }
    if (typeof originalPrint === 'function') originalPrint();
  };
})();
