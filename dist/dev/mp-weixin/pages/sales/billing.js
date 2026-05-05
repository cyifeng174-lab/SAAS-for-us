"use strict";
const common_vendor = require("../../common/vendor.js");
const utils_util = require("../../utils/util.js");
const utils_api = require("../../utils/api.js");
const _sfc_main = {
  __name: "billing",
  setup(__props) {
    const saleType = common_vendor.ref("wholesale");
    const selectedCustomer = common_vendor.ref(null);
    const customerList = common_vendor.ref([]);
    const customerSearchKeyword = common_vendor.ref("");
    const showCustomerPopup = common_vendor.ref(false);
    const inventoryList = common_vendor.ref([]);
    const inventorySearchKeyword = common_vendor.ref("");
    const showInventoryPopup = common_vendor.ref(false);
    const selectedInventory = common_vendor.ref(null);
    const orderItems = common_vendor.ref([]);
    const showKeyboardPopup = common_vendor.ref(false);
    const inputWeight = common_vendor.ref("");
    const inputPrice = common_vendor.ref("");
    const currentInputField = common_vendor.ref("weight");
    const editingProductIndex = common_vendor.ref(-1);
    const showPaymentInputPopup = common_vendor.ref(false);
    const paymentInputValue = common_vendor.ref("");
    const paidAmountFen = common_vendor.ref(0);
    const showSuccessPage = common_vendor.ref(false);
    const successOrderInfo = common_vendor.ref({});
    const filteredCustomers = common_vendor.computed(() => {
      if (!customerSearchKeyword.value) {
        return customerList.value;
      }
      const keyword = customerSearchKeyword.value.toLowerCase();
      return customerList.value.filter((customer) => {
        return customer.name && customer.name.toLowerCase().includes(keyword) || customer.phone && customer.phone.toLowerCase().includes(keyword);
      });
    });
    const filteredInventory = common_vendor.computed(() => {
      if (!inventorySearchKeyword.value) {
        return inventoryList.value;
      }
      const keyword = inventorySearchKeyword.value.toLowerCase();
      return inventoryList.value.filter((inventory) => {
        return inventory.product_name && inventory.product_name.toLowerCase().includes(keyword) || inventory.spec && inventory.spec.toLowerCase().includes(keyword) || inventory.grade && inventory.grade.toLowerCase().includes(keyword);
      });
    });
    const totalWeightJin = common_vendor.computed(() => {
      return orderItems.value.reduce((sum, item) => sum + (parseFloat(item.weight_jin) || 0), 0);
    });
    const totalAmountFen = common_vendor.computed(() => {
      return orderItems.value.reduce((sum, item) => sum + (parseInt(item.subtotal_fen) || 0), 0);
    });
    const estimatedProfitFen = common_vendor.computed(() => {
      return orderItems.value.reduce((sum, item) => {
        const subtotal = parseInt(item.subtotal_fen) || 0;
        const cost = parseInt(item.estimated_cost_fen) || 0;
        return sum + (subtotal - cost);
      }, 0);
    });
    const debtAmountFen = common_vendor.computed(() => {
      return Math.max(0, totalAmountFen.value - paidAmountFen.value);
    });
    const canSubmit = common_vendor.computed(() => {
      if (saleType.value === "wholesale" && !selectedCustomer.value) {
        return false;
      }
      if (orderItems.value.length === 0) {
        return false;
      }
      const hasStockIssue = orderItems.value.some((item) => item.stockWarning);
      if (hasStockIssue) {
        return false;
      }
      return true;
    });
    const switchSaleType = (type) => {
      saleType.value = type;
      selectedCustomer.value = null;
      orderItems.value = [];
      paidAmountFen.value = 0;
    };
    const showCustomerPicker = () => {
      showCustomerPopup.value = true;
      loadCustomers();
    };
    const closeCustomerPopup = () => {
      showCustomerPopup.value = false;
      customerSearchKeyword.value = "";
    };
    const loadCustomers = async () => {
      try {
        utils_util.showLoading("加载客户...");
        const res = await utils_api.customerAPI.list({
          keyword: customerSearchKeyword.value || "",
          page: 0,
          page_size: 100,
          order_by_field: "last_order_time",
          order_by_direction: "desc"
        });
        console.log("开单-客户列表API返回:", res);
        if (res.code === 0) {
          customerList.value = res.data.list || [];
        } else {
          utils_util.showError(res.message || "加载客户列表失败");
        }
        utils_util.hideLoading();
      } catch (error) {
        utils_util.hideLoading();
        utils_util.showError("加载客户失败：" + error.message);
        console.error("加载客户失败：", error);
      }
    };
    let customerSearchTimer = null;
    const searchCustomers = () => {
      if (customerSearchTimer) clearTimeout(customerSearchTimer);
      customerSearchTimer = setTimeout(() => {
        loadCustomers();
      }, 300);
    };
    const selectCustomer = (customer) => {
      selectedCustomer.value = customer;
      closeCustomerPopup();
    };
    const showInventoryPicker = () => {
      showInventoryPopup.value = true;
      loadInventory();
    };
    const closeInventoryPopup = () => {
      showInventoryPopup.value = false;
      inventorySearchKeyword.value = "";
    };
    const loadInventory = async () => {
      try {
        utils_util.showLoading("加载库存...");
        const res = await utils_api.inventoryAPI.list({
          keyword: inventorySearchKeyword.value || "",
          page: 0,
          page_size: 100,
          order_by_field: "update_time",
          order_by_direction: "desc"
        });
        console.log("开单-库存列表API返回:", res);
        if (res.code === 0) {
          inventoryList.value = res.data.list || [];
        } else {
          utils_util.showError(res.message || "加载库存列表失败");
        }
        utils_util.hideLoading();
      } catch (error) {
        utils_util.hideLoading();
        utils_util.showError("加载库存失败：" + error.message);
        console.error("加载库存失败：", error);
      }
    };
    let inventorySearchTimer = null;
    const searchInventory = () => {
      if (inventorySearchTimer) clearTimeout(inventorySearchTimer);
      inventorySearchTimer = setTimeout(() => {
        loadInventory();
      }, 300);
    };
    const selectInventory = (inventory) => {
      selectedInventory.value = inventory;
      inputWeight.value = "";
      inputPrice.value = inventory.suggested_price_fen ? utils_util.fenToYuan(inventory.suggested_price_fen) : "";
      currentInputField.value = "weight";
      editingProductIndex.value = -1;
      closeInventoryPopup();
      showKeyboardPopup.value = true;
    };
    const editProduct = (index) => {
      const item = orderItems.value[index];
      editingProductIndex.value = index;
      const inventory = inventoryList.value.find((inv) => inv._id === item.inventory_id);
      if (inventory) {
        selectedInventory.value = inventory;
      } else {
        selectedInventory.value = {
          _id: item.inventory_id,
          product_name: item.product_name,
          spec: item.spec,
          grade: item.grade,
          current_stock_jin: 999999
          // 编辑时不限制库存
        };
      }
      inputWeight.value = String(item.weight_jin);
      inputPrice.value = utils_util.fenToYuan(item.unit_price_fen);
      currentInputField.value = "weight";
      showKeyboardPopup.value = true;
    };
    const deleteProduct = (index) => {
      common_vendor.index.showModal({
        title: "确认删除",
        content: "确定要删除该商品吗？",
        success: (res) => {
          if (res.confirm) {
            orderItems.value.splice(index, 1);
          }
        }
      });
    };
    const closeKeyboardPopup = () => {
      showKeyboardPopup.value = false;
      selectedInventory.value = null;
      inputWeight.value = "";
      inputPrice.value = "";
      editingProductIndex.value = -1;
    };
    const focusInput = (field) => {
      currentInputField.value = field;
    };
    const inputKey = (key) => {
      const currentValue = currentInputField.value === "weight" ? inputWeight.value : inputPrice.value;
      if (key === ".") {
        if (currentValue.includes(".")) {
          return;
        }
      }
      if (currentValue.includes(".")) {
        const decimalPart = currentValue.split(".")[1];
        if (decimalPart && decimalPart.length >= 2) {
          return;
        }
      }
      if (!currentValue.includes(".") && currentValue.length >= 6 && key !== ".") {
        return;
      }
      const newValue = currentValue + key;
      if (currentInputField.value === "weight") {
        inputWeight.value = newValue;
      } else {
        inputPrice.value = newValue;
      }
    };
    const deleteKey = () => {
      const currentValue = currentInputField.value === "weight" ? inputWeight.value : inputPrice.value;
      const newValue = currentValue.slice(0, -1);
      if (currentInputField.value === "weight") {
        inputWeight.value = newValue;
      } else {
        inputPrice.value = newValue;
      }
    };
    const calculateSubtotal = () => {
      const weight = parseFloat(inputWeight.value) || 0;
      const price = parseFloat(inputPrice.value) || 0;
      const subtotal = weight * price;
      return subtotal.toFixed(2);
    };
    const confirmProduct = () => {
      const weight = parseFloat(inputWeight.value);
      const priceFen = utils_util.yuanToFen(inputPrice.value);
      if (!weight || weight <= 0) {
        utils_util.showError("请输入有效的重量");
        return;
      }
      if (!priceFen || priceFen <= 0) {
        utils_util.showError("请输入有效的单价");
        return;
      }
      if (editingProductIndex.value === -1 && selectedInventory.value) {
        const availableStock = selectedInventory.value.current_stock_jin || 0;
        if (weight > availableStock) {
          utils_util.showError(`库存不足，当前库存 ${availableStock.toFixed(2)} 斤`);
          return;
        }
      }
      const subtotalFen = Math.floor(weight * priceFen);
      const estimatedCostFen = selectedInventory.value && selectedInventory.value.unit_cost_fen ? Math.floor(weight * selectedInventory.value.unit_cost_fen) : 0;
      const orderItem = {
        inventory_id: selectedInventory.value._id,
        product_name: selectedInventory.value.product_name,
        spec: selectedInventory.value.spec || "",
        grade: selectedInventory.value.grade || "",
        weight_jin: weight,
        unit_price_fen: priceFen,
        subtotal_fen: subtotalFen,
        estimated_cost_fen: estimatedCostFen,
        stockWarning: ""
        // 库存警告信息
      };
      if (selectedInventory.value) {
        const availableStock = selectedInventory.value.current_stock_jin || 0;
        const compareWeight = editingProductIndex.value >= 0 ? weight - (orderItems.value[editingProductIndex.value].weight_jin || 0) : weight;
        if (compareWeight > availableStock) {
          orderItem.stockWarning = `库存不足，当前库存 ${availableStock.toFixed(2)} 斤`;
        }
      }
      if (editingProductIndex.value >= 0) {
        orderItems.value[editingProductIndex.value] = orderItem;
      } else {
        orderItems.value.push(orderItem);
      }
      closeKeyboardPopup();
    };
    const showPaymentKeyboard = () => {
      paymentInputValue.value = paidAmountFen.value > 0 ? utils_util.fenToYuan(paidAmountFen.value) : "";
      showPaymentInputPopup.value = true;
    };
    const closePaymentInputPopup = () => {
      showPaymentInputPopup.value = false;
    };
    const inputPaymentKey = (key) => {
      const currentValue = paymentInputValue.value;
      if (key === ".") {
        if (currentValue.includes(".")) {
          return;
        }
      }
      if (currentValue.includes(".")) {
        const decimalPart = currentValue.split(".")[1];
        if (decimalPart && decimalPart.length >= 2) {
          return;
        }
      }
      if (!currentValue.includes(".") && currentValue.length >= 6 && key !== ".") {
        return;
      }
      paymentInputValue.value = currentValue + key;
    };
    const deletePaymentKey = () => {
      paymentInputValue.value = paymentInputValue.value.slice(0, -1);
    };
    const setQuickAmount = (type) => {
      if (type === "all") {
        paymentInputValue.value = utils_util.fenToYuan(totalAmountFen.value);
      } else if (type === "clear") {
        paymentInputValue.value = "";
      }
    };
    const confirmPayment = () => {
      paidAmountFen.value = utils_util.yuanToFen(paymentInputValue.value);
      closePaymentInputPopup();
    };
    const submitOrder = async () => {
      if (!canSubmit.value) {
        if (saleType.value === "wholesale" && !selectedCustomer.value) {
          utils_util.showError("批发模式必须选择客户");
        } else if (orderItems.value.length === 0) {
          utils_util.showError("请添加商品");
        }
        return;
      }
      if (!selectedCustomer.value) {
        utils_util.showError("请先选择客户");
        return;
      }
      try {
        utils_util.showLoading("正在开单...");
        const orderItemsData = orderItems.value.map((item) => ({
          inventory_id: item.inventory_id,
          weight_jin: item.weight_jin,
          unit_price_fen: item.unit_price_fen
        }));
        const res = await utils_api.salesAPI.create({
          customer_id: selectedCustomer.value._id,
          sale_type: saleType.value,
          order_items: orderItemsData,
          paid_amount_fen: paidAmountFen.value
        });
        utils_util.hideLoading();
        if (res.code === 0) {
          utils_util.showSuccess("开单成功");
          const apiData = res.data;
          successOrderInfo.value = {
            order_no: apiData.orderNo || apiData.order_no || "",
            customer_name: apiData.customerName || apiData.customer_name || "",
            sale_type: apiData.saleType || apiData.sale_type || saleType.value,
            total_amount_fen: apiData.totalAmountFen || apiData.total_amount_fen || 0,
            paid_amount_fen: apiData.paidAmountFen || apiData.paid_amount_fen || paidAmountFen.value,
            debt_amount_fen: apiData.debtAmountFen || apiData.debt_amount_fen || 0,
            items: apiData.items || orderItems.value,
            fifo_cost_total_fen: apiData.fifoCostTotalFen || apiData.fifo_cost_total_fen || 0,
            profit_fen: apiData.profitFen || apiData.profit_fen || 0
          };
          showSuccessPage.value = true;
          loadCustomers();
        } else {
          utils_util.showError(res.message || "开单失败");
        }
      } catch (error) {
        utils_util.hideLoading();
        utils_util.showError("开单失败：" + error.message);
        console.error("开单失败：", error);
      }
    };
    const continueBilling = () => {
      orderItems.value = [];
      paidAmountFen.value = 0;
      showSuccessPage.value = false;
      successOrderInfo.value = {};
    };
    const viewOrder = () => {
      common_vendor.index.navigateTo({
        url: "/pages/sales/history"
      });
    };
    common_vendor.onMounted(() => {
      loadCustomers();
      loadInventory();
    });
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: saleType.value === "wholesale" ? 1 : "",
        b: common_vendor.o(($event) => switchSaleType("wholesale")),
        c: saleType.value === "retail" ? 1 : "",
        d: common_vendor.o(($event) => switchSaleType("retail")),
        e: common_vendor.t(saleType.value === "wholesale" ? "客户（必选）" : "客户（可选）"),
        f: selectedCustomer.value
      }, selectedCustomer.value ? common_vendor.e({
        g: common_vendor.t(selectedCustomer.value.name),
        h: selectedCustomer.value.phone
      }, selectedCustomer.value.phone ? {
        i: common_vendor.t(selectedCustomer.value.phone)
      } : {}) : {}, {
        j: common_vendor.o(showCustomerPicker),
        k: selectedCustomer.value
      }, selectedCustomer.value ? {
        l: common_vendor.t(common_vendor.unref(utils_util.fenToYuan)(selectedCustomer.value.total_debt_fen || 0)),
        m: selectedCustomer.value.total_debt_fen > 0 ? 1 : ""
      } : {}, {
        n: orderItems.value.length > 0
      }, orderItems.value.length > 0 ? {
        o: common_vendor.t(orderItems.value.length)
      } : {}, {
        p: orderItems.value.length === 0
      }, orderItems.value.length === 0 ? {} : {
        q: common_vendor.f(orderItems.value, (item, index, i0) => {
          return common_vendor.e({
            a: common_vendor.t(item.product_name),
            b: common_vendor.o(($event) => editProduct(index), index),
            c: common_vendor.o(($event) => deleteProduct(index), index),
            d: common_vendor.t(item.spec || "-"),
            e: common_vendor.t(item.grade || "-"),
            f: common_vendor.t(item.weight_jin),
            g: common_vendor.t(common_vendor.unref(utils_util.fenToYuan)(item.unit_price_fen)),
            h: common_vendor.t(common_vendor.unref(utils_util.fenToYuan)(item.subtotal_fen)),
            i: item.stockWarning
          }, item.stockWarning ? {
            j: common_vendor.t(item.stockWarning)
          } : {}, {
            k: index
          });
        })
      }, {
        r: common_vendor.o(showInventoryPicker),
        s: common_vendor.t(totalWeightJin.value.toFixed(2)),
        t: common_vendor.t(common_vendor.unref(utils_util.fenToYuan)(totalAmountFen.value)),
        v: common_vendor.t(common_vendor.unref(utils_util.fenToYuan)(estimatedProfitFen.value)),
        w: common_vendor.t(common_vendor.unref(utils_util.fenToYuan)(paidAmountFen.value)),
        x: paidAmountFen.value > 0 ? 1 : "",
        y: common_vendor.o(showPaymentKeyboard),
        z: totalAmountFen.value > 0
      }, totalAmountFen.value > 0 ? {
        A: common_vendor.t(common_vendor.unref(utils_util.fenToYuan)(debtAmountFen.value)),
        B: debtAmountFen.value > 0 ? 1 : ""
      } : {}, {
        C: !canSubmit.value ? 1 : "",
        D: common_vendor.o(submitOrder),
        E: showCustomerPopup.value
      }, showCustomerPopup.value ? {
        F: common_vendor.o(closeCustomerPopup)
      } : {}, {
        G: common_vendor.o(closeCustomerPopup),
        H: common_vendor.o([($event) => customerSearchKeyword.value = $event.detail.value, searchCustomers]),
        I: customerSearchKeyword.value,
        J: common_vendor.f(filteredCustomers.value, (customer, k0, i0) => {
          return common_vendor.e({
            a: common_vendor.t(customer.name),
            b: customer.phone
          }, customer.phone ? {
            c: common_vendor.t(customer.phone)
          } : {}, {
            d: customer.total_debt_fen > 0
          }, customer.total_debt_fen > 0 ? {
            e: common_vendor.t(common_vendor.unref(utils_util.fenToYuan)(customer.total_debt_fen))
          } : {}, {
            f: customer._id,
            g: selectedCustomer.value && selectedCustomer.value._id === customer._id ? 1 : "",
            h: common_vendor.o(($event) => selectCustomer(customer), customer._id)
          });
        }),
        K: filteredCustomers.value.length === 0
      }, filteredCustomers.value.length === 0 ? {} : {}, {
        L: showCustomerPopup.value ? 1 : "",
        M: showInventoryPopup.value
      }, showInventoryPopup.value ? {
        N: common_vendor.o(closeInventoryPopup)
      } : {}, {
        O: common_vendor.o(closeInventoryPopup),
        P: common_vendor.o([($event) => inventorySearchKeyword.value = $event.detail.value, searchInventory]),
        Q: inventorySearchKeyword.value,
        R: common_vendor.f(filteredInventory.value, (inventory, k0, i0) => {
          return common_vendor.e({
            a: common_vendor.t(inventory.product_name),
            b: inventory.spec
          }, inventory.spec ? {
            c: common_vendor.t(inventory.spec)
          } : {}, {
            d: inventory.grade
          }, inventory.grade ? {
            e: common_vendor.t(inventory.grade)
          } : {}, {
            f: inventory.current_stock_jin <= 0
          }, inventory.current_stock_jin <= 0 ? {} : {}, {
            g: common_vendor.t(inventory.current_stock_jin.toFixed(2)),
            h: inventory.current_stock_jin <= 0 || inventory.current_stock_jin <= inventory.warning_stock_jin ? 1 : "",
            i: inventory.suggested_price_fen
          }, inventory.suggested_price_fen ? {
            j: common_vendor.t(common_vendor.unref(utils_util.fenToYuan)(inventory.suggested_price_fen))
          } : {}, {
            k: inventory._id,
            l: inventory.current_stock_jin <= 0 ? 1 : "",
            m: common_vendor.o(($event) => selectInventory(inventory), inventory._id)
          });
        }),
        S: filteredInventory.value.length === 0
      }, filteredInventory.value.length === 0 ? {} : {}, {
        T: showInventoryPopup.value ? 1 : "",
        U: showKeyboardPopup.value
      }, showKeyboardPopup.value ? {
        V: common_vendor.o(() => {
        })
      } : {}, {
        W: common_vendor.t(_ctx.editingProduct ? "修改商品" : "添加商品"),
        X: common_vendor.o(closeKeyboardPopup),
        Y: selectedInventory.value
      }, selectedInventory.value ? common_vendor.e({
        Z: common_vendor.t(selectedInventory.value.product_name),
        aa: selectedInventory.value.spec
      }, selectedInventory.value.spec ? {
        ab: common_vendor.t(selectedInventory.value.spec)
      } : {}, {
        ac: selectedInventory.value.grade
      }, selectedInventory.value.grade ? {
        ad: common_vendor.t(selectedInventory.value.grade)
      } : {}, {
        ae: common_vendor.t(selectedInventory.value.current_stock_jin.toFixed(2))
      }) : {}, {
        af: common_vendor.t(inputWeight.value || "0.00"),
        ag: inputWeight.value ? 1 : "",
        ah: common_vendor.o(($event) => focusInput("weight")),
        ai: common_vendor.t(inputPrice.value || "0.00"),
        aj: inputPrice.value ? 1 : "",
        ak: common_vendor.o(($event) => focusInput("price")),
        al: common_vendor.t(calculateSubtotal()),
        am: common_vendor.o(($event) => inputKey("1")),
        an: common_vendor.o(($event) => inputKey("2")),
        ao: common_vendor.o(($event) => inputKey("3")),
        ap: common_vendor.o(($event) => inputKey("4")),
        aq: common_vendor.o(($event) => inputKey("5")),
        ar: common_vendor.o(($event) => inputKey("6")),
        as: common_vendor.o(($event) => inputKey("7")),
        at: common_vendor.o(($event) => inputKey("8")),
        av: common_vendor.o(($event) => inputKey("9")),
        aw: common_vendor.o(($event) => inputKey(".")),
        ax: common_vendor.o(($event) => inputKey("0")),
        ay: common_vendor.o(deleteKey),
        az: common_vendor.o(confirmProduct),
        aA: showKeyboardPopup.value ? 1 : "",
        aB: showPaymentInputPopup.value
      }, showPaymentInputPopup.value ? {
        aC: common_vendor.o(() => {
        })
      } : {}, {
        aD: common_vendor.o(closePaymentInputPopup),
        aE: common_vendor.t(paymentInputValue.value || "0.00"),
        aF: common_vendor.o(($event) => inputPaymentKey("1")),
        aG: common_vendor.o(($event) => inputPaymentKey("2")),
        aH: common_vendor.o(($event) => inputPaymentKey("3")),
        aI: common_vendor.o(($event) => inputPaymentKey("4")),
        aJ: common_vendor.o(($event) => inputPaymentKey("5")),
        aK: common_vendor.o(($event) => inputPaymentKey("6")),
        aL: common_vendor.o(($event) => inputPaymentKey("7")),
        aM: common_vendor.o(($event) => inputPaymentKey("8")),
        aN: common_vendor.o(($event) => inputPaymentKey("9")),
        aO: common_vendor.o(($event) => inputPaymentKey(".")),
        aP: common_vendor.o(($event) => inputPaymentKey("0")),
        aQ: common_vendor.o(deletePaymentKey),
        aR: common_vendor.o(($event) => setQuickAmount("all")),
        aS: common_vendor.o(($event) => setQuickAmount("clear")),
        aT: common_vendor.o(confirmPayment),
        aU: showPaymentInputPopup.value ? 1 : "",
        aV: showSuccessPage.value
      }, showSuccessPage.value ? common_vendor.e({
        aW: common_vendor.t(successOrderInfo.value.order_no),
        aX: common_vendor.t(successOrderInfo.value.customer_name),
        aY: common_vendor.t(common_vendor.unref(utils_util.fenToYuan)(successOrderInfo.value.total_amount_fen)),
        aZ: common_vendor.t(common_vendor.unref(utils_util.fenToYuan)(successOrderInfo.value.paid_amount_fen)),
        ba: successOrderInfo.value.debt_amount_fen > 0
      }, successOrderInfo.value.debt_amount_fen > 0 ? {
        bb: common_vendor.t(common_vendor.unref(utils_util.fenToYuan)(successOrderInfo.value.debt_amount_fen))
      } : {}, {
        bc: common_vendor.o(continueBilling),
        bd: common_vendor.o(viewOrder)
      }) : {});
    };
  }
};
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-47c0c7b2"]]);
wx.createPage(MiniProgramPage);
