/* XM Arcade standalone webxdc shim (also injected into every app frame).
   Implements the webxdc API subset: selfAddr/selfName, setUpdateListener,
   getAllUpdates, sendUpdate, sendToChat. */
;(function () {
  var listeners = [], updates = [], serial = 0;
  var selfAddr = "device-" + Math.random().toString(36).slice(2, 8), selfName = "you";
  function flush() {
    listeners.forEach(function (l) {
      try { l.cb(updates.filter(function (u) { return u.serial > l.last; })); l.last = serial; } catch (e) {}
    });
  }
  window.webxdc = window.webxdc || {
    selfAddr: selfAddr, selfName: selfName,
    setUpdateListener: function (cb, s) { listeners.push({ cb: cb, last: s || 0 }); flush(); return Promise.resolve(); },
    getAllUpdates: function () { return Promise.resolve(updates.slice()); },
    sendUpdate: function (update, descr) {
      serial++;
      var u = { payload: update, summary: descr || "", serial: serial, max_serial: serial };
      updates.push(u);
      try { parent.postMessage({ __xm: "webxdc-update", update: u }, "*"); } catch (e) {}
      flush(); return Promise.resolve();
    },
    sendToChat: function (msg) {
      try { parent.postMessage({ __xm: "webxdc-chat", text: (msg && msg.text) || String(msg || "") }, "*"); } catch (e) {}
      return Promise.resolve();
    }
  };
  window.addEventListener("message", function (ev) {
    var m = ev.data || {};
    if (m && m.__xm === "webxdc-inject" && m.update) { serial = Math.max(serial, m.update.serial || 0); updates.push(m.update); flush(); }
    if (m && m.__xm === "webxdc-hello") {
      if (m.selfAddr) { selfAddr = m.selfAddr; window.webxdc.selfAddr = m.selfAddr; }
      if (m.selfName) { selfName = m.selfName; window.webxdc.selfName = m.selfName; }
    }
  });
  try { parent.postMessage({ __xm: "webxdc-ready" }, "*"); } catch (e) {}
})();
