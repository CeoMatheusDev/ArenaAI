package com.example

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

class ArenaJsInterface(
    private val context: Context,
    private val state: ArenaWebViewState,
    private val onLoginRequested: (String?) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onGenerationStarted() {
        mainHandler.post {
            state.isGenerating = true
            if (state.backgroundServiceEnabled) {
                NotificationHelper.startBackgroundService(context)
            }
        }
    }

    @JavascriptInterface
    fun onGenerationFinished(preview: String?) {
        mainHandler.post {
            state.isGenerating = false
            state.lastResponseSnippet = preview

            if (state.notifyOnFinish) {
                NotificationHelper.showResponseNotification(context, preview)
            }
        }
    }

    @JavascriptInterface
    fun requestLogin(sourceUrl: String?) {
        mainHandler.post {
            onLoginRequested(sourceUrl)
        }
    }

    @JavascriptInterface
    fun showCustomNotification(title: String, message: String) {
        mainHandler.post {
            NotificationHelper.showResponseNotification(
                context,
                if (title.isNotBlank()) "$title: $message" else message
            )
        }
    }

    companion object {
        const val SCRIPT_INJECTION = """
(function() {
    if (window._arenaMonitorInstalled) return;
    window._arenaMonitorInstalled = true;

    var isGenerating = false;
    var settleTimer = null;
    var checkInterval = null;
    var lastLoginPromptAt = 0;
    var lastChatMutationAt = Date.now();
    var DOM_SETTLE_DEBOUNCE_MS = 1000;

    function isAddonElement(node) {
        if (!node) return false;
        var el = node.nodeType === 1 ? node : node.parentElement;
        if (!el || !el.closest) return false;
        return !!(el.closest('#autoqueue-panel') || el.closest('[data-arena-addon]') || el.closest('[data-arena-ui]'));
    }

    function getChatContainer() {
        return document.querySelector('main, [role="main"], [class*="chat" i], [class*="conversation" i], [class*="messages" i]') || document.body;
    }

    function isStillActive() {
        var stopBtn = document.querySelector(
            'button[aria-label*="stop" i], ' +
            'button[aria-label*="parar" i], ' +
            'button[title*="stop" i], ' +
            'button[title*="parar" i], ' +
            'button[data-testid*="stop" i], ' +
            'button:has(svg rect), ' +
            'button:has(svg [fill="currentColor"][width="14"]), ' +
            'button:has(svg [fill="currentColor"][width="12"])'
        );

        var streamingIndicator = document.querySelector(
            '.streaming, .cursor-blink, .animate-pulse, [data-is-streaming="true"], .result-streaming'
        );

        var thinkingElement = document.querySelector(
            '[data-testid*="thinking"], .thinking, .reasoning, [aria-label*="thinking" i]'
        );

        var submitBtn = document.querySelector('button[type="submit"], button[aria-label*="send" i], button[aria-label*="enviar" i]');
        var submitDisabledWithSpinner = submitBtn && submitBtn.disabled && (submitBtn.querySelector('svg.animate-spin') || submitBtn.querySelector('.spinner'));

        return !!(stopBtn || streamingIndicator || submitDisabledWithSpinner || (thinkingElement && thinkingElement.textContent.trim().length > 0));
    }

    function scheduleSettleCheck() {
        if (settleTimer) {
            clearTimeout(settleTimer);
            settleTimer = null;
        }
        var elapsedSinceMutation = Date.now() - lastChatMutationAt;
        var waitMs = Math.max(DOM_SETTLE_DEBOUNCE_MS - elapsedSinceMutation, DOM_SETTLE_DEBOUNCE_MS);
        settleTimer = setTimeout(function() {
            settleTimer = null;
            if (!isGenerating) return;
            if (isStillActive()) {
                return;
            }
            var quietFor = Date.now() - lastChatMutationAt;
            if (quietFor < DOM_SETTLE_DEBOUNCE_MS) {
                // DOM mutated recently (e.g. React still rendering modal buttons); wait until 1s of full stillness
                scheduleSettleCheck();
                return;
            }

            isGenerating = false;

            var assistantBlocks = document.querySelectorAll(
                '[data-message-author-role="assistant"], .prose, .markdown, .agent-response, [class*="assistant"], [class*="agent-message"]'
            );
            var latestText = "";
            if (assistantBlocks.length > 0) {
                latestText = assistantBlocks[assistantBlocks.length - 1].innerText || "";
            }

            if (window.ArenaAI && window.ArenaAI._emitResponseComplete) {
                window.ArenaAI._emitResponseComplete(latestText);
            }
            if (window.AndroidBridge) {
                window.AndroidBridge.onGenerationFinished(latestText.substring(0, 300));
            }
        }, waitMs);
    }

    function detectGeneratingState() {
        var activeNow = isStillActive();

        if (activeNow && !isGenerating) {
            isGenerating = true;
            if (settleTimer) {
                clearTimeout(settleTimer);
                settleTimer = null;
            }
            if (window.AndroidBridge) {
                window.AndroidBridge.onGenerationStarted();
            }
        } else if (!activeNow && isGenerating) {
            if (!settleTimer) {
                scheduleSettleCheck();
            }
        } else if (activeNow && settleTimer) {
            clearTimeout(settleTimer);
            settleTimer = null;
        }
    }

    function isLoginTriggerElement(el) {
        if (!el) return false;
        var target = el.closest('button, a, [role="button"]');
        if (!target) return false;
        var text = (target.innerText || target.getAttribute('aria-label') || '').toLowerCase();
        var href = (target.getAttribute('href') || '').toLowerCase();
        return text.includes('sign in') ||
               text.includes('log in') ||
               text.includes('login') ||
               text.includes('entrar') ||
               text.includes('continue with google') ||
               text.includes('continuar com google') ||
               href.includes('/login') ||
               href.includes('/signin') ||
               href.includes('/auth');
    }

    document.addEventListener('click', function(e) {
        var el = e.target;
        if (isLoginTriggerElement(el)) {
            var now = Date.now();
            if (now - lastLoginPromptAt > 3000) {
                lastLoginPromptAt = now;
                if (window.AndroidBridge && window.AndroidBridge.requestLogin) {
                    window.AndroidBridge.requestLogin(window.location.href);
                }
            }
        }

        if (el && !isAddonElement(el) && (el.closest('button[type="submit"]') || el.closest('button[aria-label*="send" i]') || el.closest('button[aria-label*="enviar" i]') || el.closest('button:has(svg)'))) {
            var textarea = document.querySelector('textarea');
            if (textarea && textarea.value && window.ArenaAI && window.ArenaAI._emitPromptSubmit) {
                window.ArenaAI._emitPromptSubmit(textarea.value);
            }
            setTimeout(detectGeneratingState, 250);
        }
    }, true);

    document.addEventListener('keydown', function(e) {
        if (e.key === 'Enter' && !e.shiftKey && !isAddonElement(e.target)) {
            var textarea = document.querySelector('textarea');
            if (textarea && textarea.value && window.ArenaAI && window.ArenaAI._emitPromptSubmit) {
                window.ArenaAI._emitPromptSubmit(textarea.value);
            }
            setTimeout(detectGeneratingState, 350);
        }
    }, true);

    var observer = new MutationObserver(function(mutations) {
        var hasChatMutation = false;
        for (var i = 0; i < mutations.length; i++) {
            if (!isAddonElement(mutations[i].target)) {
                hasChatMutation = true;
                break;
            }
        }
        if (hasChatMutation) {
            lastChatMutationAt = Date.now();
            if (isGenerating && settleTimer) {
                // Reset 1s debounce whenever React updates the chat DOM
                scheduleSettleCheck();
            }
            detectGeneratingState();
        }
    });

    function attachObserver() {
        var container = getChatContainer();
        if (container) {
            observer.observe(container, { childList: true, subtree: true, characterData: true, attributes: true });
        }
    }

    if (document.body) {
        attachObserver();
    } else {
        document.addEventListener('DOMContentLoaded', attachObserver);
    }

    checkInterval = setInterval(detectGeneratingState, 800);
})();
        """
    }
}
