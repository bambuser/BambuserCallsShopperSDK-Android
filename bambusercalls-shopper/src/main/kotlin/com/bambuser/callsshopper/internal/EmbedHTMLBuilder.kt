package com.bambuser.callsshopper.internal

import com.bambuser.callsshopper.BambuserCallSubscriptions
import com.bambuser.callsshopper.BambuserJSONValue

/**
 * Builds the HTML the WebView loads. Everything in this file is the
 * Kotlin port of iOS `EmbedHTMLBuilder.swift`: the JS-in-HTML template
 * plus the config-object emitter.
 *
 * Native ↔ JS message names:
 *   - Outgoing (JS → native): `window.__bambuserAndroidBridge.postMessage(json)`
 *     (see [JsBridge.NAME]).
 *   - Incoming (native → JS): raw `evaluateJavascript` calls into the
 *     helpers below (`__bambuserInvokeEmbedMethod`, etc.).
 */
internal object EmbedHTMLBuilder {

    /**
     * All the knobs the embed constructor accepts + which subscriptions
     * to install. Populated from [com.bambuser.callsshopper.BambuserCallConfiguration]
     * plus the frozen [BambuserCallSubscriptions] snapshot.
     */
    data class Options(
        val orgId: String,
        val embedUrl: String,
        val connectId: String?,
        val queue: String?,
        val triggers: List<String>,
        val floatingNavigationMode: String,
        val floatingFillMode: String,

        val locale: String?,
        val data: BambuserJSONValue?,
        val trackingTags: BambuserJSONValue?,

        val dropInEnabled: Boolean?,
        val bookingsEnabled: Boolean?,
        val openBookingPage: Boolean,
        val bookingServiceIds: List<String>?,
        val bookingResourceId: String?,
        val bookingIframeUrl: String?,

        val enableScanning: Boolean,
        val merchantBaseUrl: String?,
        val disableCoBrowsing: Boolean,
        val themeId: String?,
        val allowFirstPartyCookies: Boolean?,
        val disableDataLayerInterceptions: Boolean,

        val subscriptions: BambuserCallSubscriptions,
    )

    fun makeHTML(options: Options): String {
        val configLiteral = buildEmbedConfigLiteral(options)
        val onBlocks = buildOnBlocks(options.subscriptions)
        val handledByCase = handledByCaseLiteral(options.subscriptions)
        val catchAll = if (options.subscriptions.catchAllOther) "true" else "false"
        val embedUrl = escapeJs(options.embedUrl)

        // A raw string keeps the JS readable. Kotlin `$` interpolation
        // is only used for the four variables below; the JS itself
        // uses no `$` template literals.
        return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
        <style>
          /* !important on every dimension: the embed loads its own
             stylesheet after ours and its rules would otherwise win.
             Without these, the iframe collapses to height 0 and the
             widget's `<video>` elements render at 0x0. */
          html, body {
            margin: 0 !important;
            padding: 0 !important;
            width: 100vw !important;
            height: 100vh !important;
            min-height: 100vh !important;
            overflow: hidden !important;
            background: transparent !important;
          }
          body > iframe {
            border: 0 !important;
            width: 100vw !important;
            height: 100vh !important;
            position: fixed !important;
            top: 0 !important;
            left: 0 !important;
          }
        </style>
        </head>
        <body>
        <script>
        window.__bambuserPendingNavigations = window.__bambuserPendingNavigations || [];
        window.notifyBambuserProductNavigation = function(externalId) {
          if (typeof externalId !== 'string' || !externalId) return;
          if (window.oneToOneEmbed && typeof window.oneToOneEmbed.notifyProductNavigation === 'function') {
            try {
              window.oneToOneEmbed.notifyProductNavigation({externalId: externalId});
            } catch (e) {
              console.error('[bambuser] notifyProductNavigation failed', e);
            }
          } else {
            window.__bambuserPendingNavigations.push(externalId);
          }
        };

        window.onBambuserOneToOneReady = function(BambuserOneToOneEmbed) {
          var oneToOneEmbed = new BambuserOneToOneEmbed($configLiteral);
          window.oneToOneEmbed = oneToOneEmbed;
          window.BambuserOneToOneEmbed = BambuserOneToOneEmbed;

          if (window.__bambuserPendingNavigations.length) {
            window.__bambuserPendingNavigations.splice(0).forEach(function(sku) {
              try { oneToOneEmbed.notifyProductNavigation({externalId: sku}); } catch (e) {}
            });
          }

          // 'close' is always subscribed — the SDK needs it to reset native state.
          oneToOneEmbed.on('close', function() {
            __post('close');
          });

        $onBlocks

          // Emitter override — always installed. Forwards call-state
          // events (SDK-internal) and, when catchAllOther is enabled,
          // any event not explicitly subscribed above.
          try {
            var emitter = oneToOneEmbed.events;
            if (emitter && typeof emitter.emit === 'function') {
              var handledByCase = $handledByCase;
              var callStateEvents = {
                'call-started': 1, 'call-starting': 1, 'call-connecting': 1, 'call-ringing': 1,
                'call-connected': 1, 'call-active': 1, 'call-accepted': 1, 'connected': 1,
                'call-ended': 1, 'call-end': 1, 'call-disconnected': 1, 'call-rejected': 1,
                'disconnected': 1, 'ended': 1
              };
              var catchAllOther = $catchAll;
              var originalEmit = emitter.emit.bind(emitter);
              emitter.emit = function(name) {
                var rest = Array.prototype.slice.call(arguments, 1);
                if (!handledByCase[name]) {
                  if (callStateEvents[name] || catchAllOther) {
                    var payload;
                    try {
                      payload = JSON.parse(JSON.stringify(rest.length === 1 ? rest[0] : rest));
                    } catch (e) {
                      payload = String(rest[0]);
                    }
                    __post(name, payload === undefined ? null : payload);
                  }
                }
                return originalEmit.apply(null, arguments);
              };
            }
          } catch (e) {}
        };

        window.__bambuserCartCallbackSeq = 0;
        function __nextCallbackId() {
          window.__bambuserCartCallbackSeq += 1;
          return 'cb_' + window.__bambuserCartCallbackSeq;
        }

        // === Generic factory-spec bridge ==================================
        window.__bambuserApplyFactorySpec = function(factory, spec) {
          if (!spec || !spec.calls) return factory;
          var current = factory;
          spec.calls.forEach(function(call) {
            var args = (call.args || []).slice();
            if (call.factory) {
              (function(sub) {
                args.push(function(f) {
                  return window.__bambuserApplyFactorySpec(f, sub);
                });
              })(call.factory);
            } else if (call.items) {
              (function(items) {
                args.push(function(itemFactory) {
                  return items.map(function(item) {
                    var itemArgs = item.factoryArgs || [];
                    var starting = (typeof itemFactory === 'function')
                      ? itemFactory.apply(null, itemArgs)
                      : itemFactory;
                    return window.__bambuserApplyFactorySpec(
                      starting,
                      item.spec || { calls: [] }
                    );
                  });
                });
              })(call.items);
            }
            if (typeof current[call.method] !== 'function') {
              console.warn('[bambuser] applyFactorySpec: no method', call.method);
              return;
            }
            current = current[call.method].apply(current, args);
          });
          return current;
        };

        window.__bambuserApplyToCallback = function(callbackKey, spec, deleteAfter) {
          var cb = window[callbackKey];
          if (typeof cb !== 'function') {
            console.warn('[bambuser] applyToCallback: no callback', callbackKey);
            return;
          }
          try {
            cb(function(factory) {
              return window.__bambuserApplyFactorySpec(factory, spec);
            });
          } catch (e) {
            console.error('[bambuser] applyToCallback failed', callbackKey, e);
          }
          if (deleteAfter !== false) {
            try { delete window[callbackKey]; } catch (e) { window[callbackKey] = undefined; }
          }
        };

        window.__bambuserApplyToCallbackWithError = function(callbackKey, errorMessage, deleteAfter) {
          var cb = window[callbackKey];
          if (typeof cb !== 'function') {
            console.warn('[bambuser] applyToCallbackWithError: no callback', callbackKey);
            return;
          }
          try {
            cb(function() {
              throw new Error(errorMessage || 'error');
            });
          } catch (e) {
            console.error('[bambuser] applyToCallbackWithError sync failure', callbackKey, e);
          }
          if (deleteAfter !== false) {
            try { delete window[callbackKey]; } catch (e) { window[callbackKey] = undefined; }
          }
        };

        window.__bambuserInvokeEmbedMethod = function(method, primaryArgs, spec) {
          if (!window.oneToOneEmbed || typeof window.oneToOneEmbed[method] !== 'function') {
            console.warn('[bambuser] invokeEmbedMethod: not available', method);
            return;
          }
          var args = (primaryArgs || []).slice();
          if (spec) {
            args.push(function(factory) {
              return window.__bambuserApplyFactorySpec(factory, spec);
            });
          }
          try {
            window.oneToOneEmbed[method].apply(window.oneToOneEmbed, args);
          } catch (e) {
            console.error('[bambuser] invokeEmbedMethod failed', method, e);
          }
        };

        window.__bambuserInvokeEmbedMethodWithError = function(method, primaryArgs, errorMessage) {
          if (!window.oneToOneEmbed || typeof window.oneToOneEmbed[method] !== 'function') {
            console.warn('[bambuser] invokeEmbedMethodWithError: not available', method);
            return;
          }
          var args = (primaryArgs || []).slice();
          args.push(function() {
            throw new Error(errorMessage || 'error');
          });
          try {
            window.oneToOneEmbed[method].apply(window.oneToOneEmbed, args);
          } catch (e) {
            console.error('[bambuser] invokeEmbedMethodWithError sync failure', method, e);
          }
        };

        window.__bambuserNotifyCustomerEvent = function(constantName, payload) {
          if (!window.oneToOneEmbed
              || typeof window.oneToOneEmbed.notifyCustomerEvent !== 'function') {
            console.warn('[bambuser] notifyCustomerEvent not available');
            return;
          }
          var cls = window.BambuserOneToOneEmbed;
          if (!cls || !cls.CUSTOMER_EVENTS) {
            console.warn('[bambuser] CUSTOMER_EVENTS constants not exposed by this embed build');
            return;
          }
          var eventType = cls.CUSTOMER_EVENTS[constantName];
          if (eventType == null) {
            console.warn('[bambuser] unknown CUSTOMER_EVENTS key:', constantName);
            return;
          }
          try {
            window.oneToOneEmbed.notifyCustomerEvent(eventType, payload);
          } catch (e) {
            console.error('[bambuser] notifyCustomerEvent failed', constantName, e);
          }
        };

        window.__bambuserInvokeEmbedAsync = function(requestId, method, primaryArgs) {
          function reply(ok, value, error) {
            __post('__async-response', {
              requestId: requestId,
              ok: ok,
              value: value !== undefined ? value : null,
              error: error != null ? String(error) : null
            });
          }
          if (!window.oneToOneEmbed || typeof window.oneToOneEmbed[method] !== 'function') {
            reply(false, null, 'method-not-available: ' + method);
            return;
          }
          try {
            var result = window.oneToOneEmbed[method].apply(window.oneToOneEmbed, primaryArgs || []);
            if (result && typeof result.then === 'function') {
              result.then(function(v) {
                try { reply(true, JSON.parse(JSON.stringify(v))); }
                catch (e) { reply(true, null); }
              }, function(err) {
                reply(false, null, err && err.message ? err.message : String(err));
              });
            } else {
              try { reply(true, JSON.parse(JSON.stringify(result))); }
              catch (e) { reply(true, null); }
            }
          } catch (e) {
            reply(false, null, e && e.message ? e.message : String(e));
          }
        };

        function __post(event, payload) {
          try {
            window.${JsBridge.NAME}.postMessage(
              JSON.stringify({event: event, payload: payload === undefined ? null : payload})
            );
          } catch (e) {}
        }
        </script>
        <script async src="$embedUrl"></script>
        </body>
        </html>
        """.trimIndent()
    }

    // MARK: - JS `.on(...)` block builder

    private fun buildOnBlocks(subs: BambuserCallSubscriptions): String {
        val out = StringBuilder()

        // Fire-and-forget events — ALWAYS installed. Delegate-mapped
        // on the native side; the SDK just drops them if no delegate.
        out.append("""
          oneToOneEmbed.on('goto-checkout', function(event) {
            __post('goto-checkout', event || null);
          });
          oneToOneEmbed.on('goto-chat', function() {
            __post('goto-chat');
          });
          oneToOneEmbed.on('surf-behind-to', function(url) {
            __post('surf-behind-to', { url: url });
          });
          oneToOneEmbed.on('queue-is-open', function(data) {
            __post('queue-is-open', {
              isOpen: true,
              nextCloseTime: data && data.nextCloseTime != null ? data.nextCloseTime : null
            });
          });
          oneToOneEmbed.on('queue-is-closed', function(data) {
            __post('queue-is-closed', {
              isOpen: false,
              nextOpenTime: data && data.nextOpenTime != null ? data.nextOpenTime : null
            });
          });
          oneToOneEmbed.on('agents-online', function(data) {
            __post('agents-online', {
              numberOfAgentsOnline: data && data.numberOfAgentsOnline != null ? data.numberOfAgentsOnline : 0,
              queueId: data && (data.queueId || data.queue) || null
            });
          });
          oneToOneEmbed.on('queue-estimated-waiting-time', function(data) {
            __post('queue-estimated-waiting-time', {
              estimatedWaitingTime: data && data.estimatedWaitingTime != null ? data.estimatedWaitingTime : 0,
              agents: data && data.agents != null ? data.agents : 0,
              place:  data && data.place  != null ? data.place  : 0,
              queueId: data && (data.queueId || data.queue) || null
            });
          });
          oneToOneEmbed.on('tracking-event', function(payload) {
            try {
              __post('tracking-event', JSON.parse(JSON.stringify(payload || {})));
            } catch (e) {
              __post('tracking-event', {});
            }
          });
        """.trimIndent())

        // Data-source events — only installed when the corresponding
        // handler is set, so the widget doesn't wait 30s for a reply
        // it will never get.
        if (subs.shouldAddToCart) {
            out.append("\n").append("""
              oneToOneEmbed.on('should-add-item-to-cart', function(event, callback) {
                var id = __nextCallbackId();
                window[id] = callback;
                __post('should-add-item-to-cart', { callbackId: id, payload: event });
              });
            """.trimIndent())
        }
        if (subs.shouldUpdateCart) {
            out.append("\n").append("""
              oneToOneEmbed.on('should-update-item-in-cart', function(event, callback) {
                var id = __nextCallbackId();
                window[id] = callback;
                __post('should-update-item-in-cart', { callbackId: id, payload: event });
              });
            """.trimIndent())
        }
        if (subs.provideSearchData) {
            out.append("\n").append("""
              oneToOneEmbed.on('provide-search-data', function(searchRequest, searchResponse) {
                var id = __nextCallbackId();
                window[id] = searchResponse;
                __post('provide-search-data', {
                  callbackId: id,
                  term: (searchRequest && searchRequest.term) || '',
                  page:  (searchRequest && (searchRequest.page != null ? searchRequest.page : 1)) || 1
                });
              });
            """.trimIndent())
        }
        if (subs.provideProductData) {
            out.append("\n").append("""
              oneToOneEmbed.on('provide-product-data', function(event) {
                var products = (event && event.products) || [];
                var refs = [];
                for (var i = 0; i < products.length; i++) {
                  var p = products[i] || {};
                  refs.push({
                    ref: p.ref != null ? String(p.ref) : '',
                    type: p.type != null ? String(p.type) : '',
                    id: p.id != null ? String(p.id) : ''
                  });
                }
                __post('provide-product-data', { products: refs });
              });
            """.trimIndent())
        }

        return out.toString()
    }

    private fun handledByCaseLiteral(subs: BambuserCallSubscriptions): String {
        val names = mutableListOf(
            "close",
            "goto-checkout",
            "goto-chat",
            "surf-behind-to",
            "queue-is-open",
            "queue-is-closed",
            "agents-online",
            "queue-estimated-waiting-time",
            "tracking-event",
        )
        if (subs.shouldAddToCart)    names.add("should-add-item-to-cart")
        if (subs.shouldUpdateCart)   names.add("should-update-item-in-cart")
        if (subs.provideProductData) names.add("provide-product-data")
        if (subs.provideSearchData)  names.add("provide-search-data")
        return names.joinToString(prefix = "{ ", postfix = " }") { "'$it': 1" }
    }

    // MARK: - Embed config literal

    private fun buildEmbedConfigLiteral(o: Options): String {
        val fields = mutableListOf<String>()
        fields.add("orgId: '${escapeJs(o.orgId)}'")
        fields.add("disableDataLayerInterceptions: ${if (o.disableDataLayerInterceptions) "true" else "false"}")
        fields.add("triggers: ${jsArray(o.triggers)}")
        if (!o.connectId.isNullOrEmpty()) fields.add("connectId: '${escapeJs(o.connectId)}'")
        if (!o.queue.isNullOrEmpty())     fields.add("queue: '${escapeJs(o.queue)}'")
        if (!o.locale.isNullOrEmpty())    fields.add("locale: '${escapeJs(o.locale)}'")

        (o.data as? BambuserJSONValue.Obj)?.takeIf { it.entries.isNotEmpty() }?.let {
            fields.add("data: ${it.toJsonString()}")
        }
        (o.trackingTags as? BambuserJSONValue.Arr)?.takeIf { it.values.isNotEmpty() }?.let {
            fields.add("trackingTags: ${it.toJsonString()}")
        }

        o.dropInEnabled?.let    { fields.add("dropInEnabled: ${if (it) "true" else "false"}") }
        o.bookingsEnabled?.let  { fields.add("bookingsEnabled: ${if (it) "true" else "false"}") }
        if (o.openBookingPage)  fields.add("openBookingPage: true")
        o.bookingServiceIds?.takeIf { it.isNotEmpty() }?.let {
            fields.add("bookingServiceIds: ${jsArray(it)}")
        }
        if (!o.bookingResourceId.isNullOrEmpty()) fields.add("bookingResourceId: '${escapeJs(o.bookingResourceId)}'")
        if (!o.bookingIframeUrl.isNullOrEmpty())  fields.add("bookingIframeUrl: '${escapeJs(o.bookingIframeUrl)}'")
        if (o.enableScanning)                     fields.add("enableScanning: true")
        if (!o.merchantBaseUrl.isNullOrEmpty())   fields.add("merchantBaseUrl: '${escapeJs(o.merchantBaseUrl)}'")
        if (o.disableCoBrowsing)                  fields.add("disableCoBrowsing: true")
        if (!o.themeId.isNullOrEmpty())           fields.add("themeId: '${escapeJs(o.themeId)}'")
        o.allowFirstPartyCookies?.let             { fields.add("allowFirstPartyCookies: ${if (it) "true" else "false"}") }

        fields.add(
            "floatingPlayer: { navigationMode: '${escapeJs(o.floatingNavigationMode)}', " +
                "webViewFillMode: '${escapeJs(o.floatingFillMode)}' }"
        )

        return "{ " + fields.joinToString(", ") + " }"
    }

    // MARK: - Document-start shim

    /**
     * Injected via `WebViewCompat.addDocumentStartJavaScript`. Handles
     * the two things the embed's own scripts don't yet do reliably at
     * document start:
     *   1. Polyfills legacy `window.getUserMedia`.
     *   2. Forwards cross-frame `postMessage` containing "viddget" so
     *      the SDK sees PiP viewport-mode changes from within iframes.
     */
    fun makeDocumentStartShim(injectsLegacyGetUserMediaShim: Boolean): String {
        val sb = StringBuilder()
        if (injectsLegacyGetUserMediaShim) {
            sb.append("""
                window.getUserMedia = function(constraints, successCallback, errorCallback) {
                    if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
                        navigator.mediaDevices.getUserMedia(constraints).then(successCallback).catch(errorCallback);
                    } else if (errorCallback) {
                        errorCallback(new DOMException('getUserMedia not supported', 'NotSupportedError'));
                    }
                };
            """.trimIndent()).append('\n')
        }
        sb.append("""
            window.addEventListener('message', function(event) {
                try {
                    var raw = event.data;
                    var str = (typeof raw === 'string') ? raw : JSON.stringify(raw);
                    if (str && str.indexOf('viddget') !== -1) {
                        window.${JsBridge.NAME}.postMessage(
                            JSON.stringify({event: 'iframe-message', detail: str, origin: event.origin})
                        );
                    }
                } catch(e) {}
            });
        """.trimIndent()).append('\n')

        // Force the widget's iframe to fill the WebView. The embed's
        // own stylesheet applies `max-height: 0` to the iframe it
        // inserts into <body>, which clamps every dimension we set
        // (even inline `!important height`) to zero. Result: the
        // agent's <video> elements decode fine but render at 0x0
        // and the shopper sees a white screen.
        //
        // The fix is a document-start MutationObserver that watches
        // <body> for iframe insertions and force-sizes them via
        // inline `!important` on max-height / max-width AND
        // height / width. Explicit pixels because `100vh` doesn't
        // resolve on some Android WebView builds.
        sb.append("""
            (function() {
              function forceFill(el) {
                try {
                  var w = window.innerWidth  || document.documentElement.clientWidth  || 0;
                  var h = window.innerHeight || document.documentElement.clientHeight || 0;
                  if (!w || !h) return;
                  el.style.setProperty('max-height', 'none',   'important');
                  el.style.setProperty('max-width',  'none',   'important');
                  el.style.setProperty('min-height', h + 'px', 'important');
                  el.style.setProperty('min-width',  w + 'px', 'important');
                  el.style.setProperty('height',     h + 'px', 'important');
                  el.style.setProperty('width',      w + 'px', 'important');
                  el.style.setProperty('position',   'fixed',  'important');
                  el.style.setProperty('top',        '0',      'important');
                  el.style.setProperty('left',       '0',      'important');
                } catch(e) {}
              }
              function scan() {
                var iframes = document.getElementsByTagName('iframe');
                for (var i = 0; i < iframes.length; i++) forceFill(iframes[i]);
              }
              function attach() {
                scan();
                try {
                  var mo = new MutationObserver(function() { scan(); });
                  mo.observe(document.documentElement, {
                    childList: true, subtree: true, attributes: true,
                    attributeFilter: ['style']
                  });
                } catch(e) {}
                window.addEventListener('resize', scan);
                window.addEventListener('orientationchange', scan);
              }
              if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', attach);
              } else {
                attach();
              }
            })();
        """.trimIndent())
        return sb.toString()
    }

    fun baseUrl(embedUrl: String): String? {
        val uri = android.net.Uri.parse(embedUrl)
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        val port = uri.port
        return buildString {
            append(scheme).append("://").append(host)
            if (port != -1) append(":").append(port)
        }
    }

    // MARK: - JS emission helpers

    private fun escapeJs(s: String): String = s
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")

    private fun jsArray(items: List<String>): String =
        items.joinToString(prefix = "[", postfix = "]") { "'${escapeJs(it)}'" }
}
