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
            if (state.backgroundServiceEnabled && state.notifyOnFinish) {
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
            } else {
                NotificationHelper.cancelAllNotifications(context)
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
            if (!state.notifyOnFinish) {
                return@post
            }
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
    var sawStopButton = false;
    var settleTimer = null;
    var checkInterval = null;
    var lastLoginPromptAt = 0;
    var lastChatMutationAt = Date.now();
    var lastIdleObservedAt = 0;
    var DOM_SETTLE_DEBOUNCE_MS = 1000;
    var IDLE_CONFIRM_MS = 1800;

    function isAddonElement(node) {
        if (!node) return false;
        var el = node.nodeType === 1 ? node : node.parentElement;
        if (!el || !el.closest) return false;
        return !!(el.closest('#autoqueue-panel') || el.closest('[data-arena-addon]') || el.closest('[data-arena-ui]'));
    }

    function getChatContainer() {
        return document.querySelector('main, [role="main"], [class*="chat" i], [class*="conversation" i], [class*="messages" i]') || document.body;
    }

    /**
     * Exact detection for Arena AI composer button:
     * - While generating: button[data-perf-target="send"] has aria-label="Stop generating" and <svg><rect .../></svg> (square stop icon).
     * - When finished: button[data-perf-target="send"] switches back to aria-label="Send message" with the right-arrow <svg> (no <rect>).
     */
    function isStopButtonActive() {
        var composerBtn = document.querySelector('button[data-perf-target="send"]');
        if (composerBtn) {
            var label = (composerBtn.getAttribute('aria-label') || '').toLowerCase();
            if (label.indexOf('stop') !== -1) return true;
            if (composerBtn.querySelector('svg rect')) return true;
            if (label.indexOf('send') !== -1) return false;
        }
        return !!document.querySelector(
            'button[aria-label="Stop generating"], ' +
            'button[aria-label*="Stop generating" i], ' +
            'button[title*="Stop generating" i], ' +
            'button[data-testid*="stop" i]'
        );
    }

    function isSendButtonIdle() {
        var composerBtn = document.querySelector('button[data-perf-target="send"]');
        if (composerBtn) {
            var label = (composerBtn.getAttribute('aria-label') || '').toLowerCase();
            var hasStopSquare = !!composerBtn.querySelector('svg rect');
            return label.indexOf('send') !== -1 && !hasStopSquare;
        }
        var sendBtn = document.querySelector('button[aria-label="Send message"], button[aria-label*="Send message" i]');
        return !!(sendBtn && !sendBtn.querySelector('svg rect'));
    }

    function isStillActive() {
        if (isStopButtonActive()) return true;
        var explicitStreaming = document.querySelector(
            '[data-is-streaming="true"], .result-streaming'
        );
        return !!explicitStreaming;
    }

    function clearSettleTimer() {
        if (settleTimer) {
            clearTimeout(settleTimer);
            settleTimer = null;
        }
    }

    function scheduleSettleCheck() {
        clearSettleTimer();
        var now = Date.now();
        if (!lastIdleObservedAt) {
            lastIdleObservedAt = now;
        }
        var remainingIdle = Math.max(0, IDLE_CONFIRM_MS - (now - lastIdleObservedAt));
        var remainingDomQuiet = Math.max(0, DOM_SETTLE_DEBOUNCE_MS - (now - lastChatMutationAt));
        var waitMs = Math.max(remainingIdle, remainingDomQuiet, 350);

        settleTimer = setTimeout(function() {
            settleTimer = null;
            if (!isGenerating || !sawStopButton) return;

            if (isStillActive()) {
                lastIdleObservedAt = 0;
                return;
            }

            if (!isSendButtonIdle()) {
                // Composer has not returned to the right-arrow "Send message" button yet; keep waiting
                lastIdleObservedAt = 0;
                scheduleSettleCheck();
                return;
            }

            var currentNow = Date.now();
            var quietFor = currentNow - lastChatMutationAt;
            var idleFor = currentNow - lastIdleObservedAt;
            if (quietFor < DOM_SETTLE_DEBOUNCE_MS || idleFor < IDLE_CONFIRM_MS) {
                scheduleSettleCheck();
                return;
            }

            isGenerating = false;
            sawStopButton = false;
            lastIdleObservedAt = 0;

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
        var stopActive = isStopButtonActive();
        var activeNow = stopActive || isStillActive();

        if (activeNow) {
            if (stopActive) {
                sawStopButton = true;
            }
            lastIdleObservedAt = 0;
            clearSettleTimer();
            if (!isGenerating && stopActive) {
                isGenerating = true;
                if (window.AndroidBridge) {
                    window.AndroidBridge.onGenerationStarted();
                }
            }
        } else if (isGenerating) {
            if (!sawStopButton) {
                // Generation state was never confirmed by the Stop square button; reset quietly
                isGenerating = false;
                lastIdleObservedAt = 0;
                clearSettleTimer();
                return;
            }
            if (isSendButtonIdle()) {
                if (!lastIdleObservedAt) {
                    lastIdleObservedAt = Date.now();
                }
                if (!settleTimer) {
                    scheduleSettleCheck();
                }
            } else {
                // Neither Stop square nor Send arrow is visible yet (e.g. React re-rendering between tool steps)
                lastIdleObservedAt = 0;
                clearSettleTimer();
            }
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

        if (el && !isAddonElement(el) && (el.closest('button[data-perf-target="send"]') || el.closest('button[type="submit"]') || el.closest('button[aria-label*="send" i]'))) {
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
            if (isGenerating && settleTimer && !isStillActive() && isSendButtonIdle()) {
                scheduleSettleCheck();
            }
            detectGeneratingState();
        }
    });

    function attachObserver() {
        var root = document.body || getChatContainer();
        if (root) {
            observer.observe(root, { childList: true, subtree: true, characterData: true, attributes: true });
        }
    }

    if (document.body) {
        attachObserver();
    } else {
        document.addEventListener('DOMContentLoaded', attachObserver);
    }

    checkInterval = setInterval(detectGeneratingState, 600);
})();
        """
    }
}
