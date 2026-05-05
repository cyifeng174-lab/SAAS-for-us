"use strict";
Object.defineProperty(exports, Symbol.toStringTag, { value: "Module" });
const common_vendor = require("./common/vendor.js");
if (!Math) {
  "./pages/login/index.js";
  "./pages/index/index.js";
  "./pages/sales/billing.js";
  "./pages/inventory/list.js";
  "./pages/my/index.js";
  "./pages/inventory/detail.js";
  "./pages/purchase/create.js";
  "./pages/processing/create.js";
  "./pages/customer/list.js";
  "./pages/finance/receive.js";
  "./pages/sales/history.js";
  "./pages/purchase/list.js";
  "./pages/processing/list.js";
  "./pages/finance/index.js";
  "./pages/customer/detail.js";
  "./pages/spec/list.js";
  "./pages/settings/export.js";
  "./pages/dashboard/index.js";
  "./pages/product/list.js";
  "./pages/supplier/list.js";
}
const _sfc_main = {
  onLaunch: function() {
    console.log("App Launch");
  },
  onShow: function() {
    console.log("App Show");
  },
  onHide: function() {
    console.log("App Hide");
  }
};
function createApp() {
  const app = common_vendor.createSSRApp(_sfc_main);
  return {
    app
  };
}
createApp().app.mount("#app");
exports.createApp = createApp;
