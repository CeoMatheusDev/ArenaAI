package com.example

import android.accounts.AccountManager
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import org.json.JSONObject

object ArenaAccountHelper {

    /**
     * Creates the Android system intent that displays all logged-in email/Google accounts
     * on the device so the user can choose which account to connect to the app and site.
     */
    fun createChooseAccountIntent(): Intent {
        return AccountManager.newChooseAccountIntent(
            null,
            null,
            arrayOf("com.google", "com.android.email", "com.google.android.gm.exchange", "com.microsoft.office.outlook.USER_ACCOUNT"),
            null,
            null,
            null,
            null
        )
    }

    /**
     * Fallback intent showing every account type registered on the device.
     */
    fun createChooseAnyAccountIntent(): Intent {
        return AccountManager.newChooseAccountIntent(
            null,
            null,
            null,
            null,
            null,
            null,
            null
        )
    }

    fun isLoginOrAuthUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("accounts.google.com") ||
            lower.contains("/login") ||
            lower.contains("/signin") ||
            lower.contains("/sign-in") ||
            lower.contains("/auth/") ||
            lower.contains("/oauth") ||
            lower.contains("auth.arena.ai")
    }

    fun appendLoginHintIfGoogleOAuth(url: String, email: String): String {
        if (email.isBlank()) return url
        val lower = url.lowercase()
        if (!lower.contains("accounts.google.com")) return url
        if (lower.contains("login_hint=")) return url
        return try {
            Uri.parse(url).buildUpon()
                .appendQueryParameter("login_hint", email)
                .build()
                .toString()
        } catch (_: Exception) {
            url
        }
    }

    fun injectConnectedEmailIntoWebView(webView: WebView?, email: String) {
        if (webView == null || email.isBlank()) return
        val safeEmail = JSONObject.quote(email)
        val js = """
            (function() {
                var email = $safeEmail;
                if (window.ArenaAI && window.ArenaAI._emitAccountConnected) {
                    window.ArenaAI._emitAccountConnected(email);
                }
                var selectors = [
                    'input[type="email"]',
                    'input[name="email"]',
                    'input[name="identifier"]',
                    'input[id*="email" i]',
                    'input[autocomplete="email"]',
                    'input[autocomplete="username"]'
                ];
                var inputs = document.querySelectorAll(selectors.join(','));
                inputs.forEach(function(input) {
                    if (input && !input.value) {
                        var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                        if (nativeSetter) {
                            nativeSetter.call(input, email);
                        } else {
                            input.value = email;
                        }
                        input.dispatchEvent(new Event('input', { bubbles: true }));
                        input.dispatchEvent(new Event('change', { bubbles: true }));
                    }
                });
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }
}
