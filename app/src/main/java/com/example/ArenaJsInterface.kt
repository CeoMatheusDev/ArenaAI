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

    function detectGeneratingState() {
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

        var activeNow = !!(stopBtn || streamingIndicator || submitDisabledWithSpinner || (thinkingElement && thinkingElement.textContent.trim().length > 0));

        if (activeNow && !isGenerating) {
            isGenerating = true;
            if (window.AndroidBridge) {
                window.AndroidBridge.onGenerationStarted();
            }
        } else if (!activeNow && isGenerating) {
            if (!settleTimer) {
                settleTimer = setTimeout(function() {
                    settleTimer = null;
                    var recheck = document.querySelector('button[aria-label*="stop" i], button[aria-label*="parar" i], button[title*="stop" i]');
                    if (!recheck && isGenerating) {
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
                    }
                }, 1500);
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

        if (el && (el.closest('button[type="submit"]') || el.closest('button[aria-label*="send" i]') || el.closest('button[aria-label*="enviar" i]') || el.closest('button:has(svg)'))) {
            var textarea = document.querySelector('textarea');
            if (textarea && textarea.value && window.ArenaAI && window.ArenaAI._emitPromptSubmit) {
                window.ArenaAI._emitPromptSubmit(textarea.value);
            }
            setTimeout(detectGeneratingState, 250);
        }
    }, true);

    document.addEventListener('keydown', function(e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            var textarea = document.querySelector('textarea');
            if (textarea && textarea.value && window.ArenaAI && window.ArenaAI._emitPromptSubmit) {
                window.ArenaAI._emitPromptSubmit(textarea.value);
            }
            setTimeout(detectGeneratingState, 350);
        }
    }, true);

    var observer = new MutationObserver(function() {
        detectGeneratingState();
    });

    if (document.body) {
        observer.observe(document.body, { childList: true, subtree: true, characterData: true, attributes: true });
    } else {
        document.addEventListener('DOMContentLoaded', function() {
            observer.observe(document.body, { childList: true, subtree: true, characterData: true, attributes: true });
        });
    }

    checkInterval = setInterval(detectGeneratingState, 1000);
})();
        """
    }
}
