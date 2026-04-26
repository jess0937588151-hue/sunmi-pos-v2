(function () {
  if (window.__sunmiAutoPrintAdapterInjected__) return;
  window.__sunmiAutoPrintAdapterInjected__ = true;

  /* 此脚本已停用自动拦截功能。
     所有列印操作改由 POS 网页端的 print-service.js 透过
     window.SunmiPrinter JS Bridge 直接呼叫，
     不再自动拦截按钮点击。 */

  console.log('site-autoprint-adapter.js loaded (passive mode)');
})();
