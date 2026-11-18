// AutoBook assistant page. All text from the server is inserted with textContent, never as HTML.
(function () {
    "use strict";
    var page = document.getElementById("assistant-page");
    if (!page) {
        return;
    }
    var csrfHeader = page.dataset.csrfHeader;
    var csrfToken = page.dataset.csrfToken;
    var conversationId = null;
    var pending = null;

    var log = document.getElementById("chat-log");
    var form = document.getElementById("chat-form");
    var input = document.getElementById("chat-input");
    var send = document.getElementById("chat-send");
    var errorBox = document.getElementById("chat-error");
    var pendingCard = document.getElementById("pending-card");

    function post(url, body) {
        var headers = {"Content-Type": "application/json", "Accept": "application/json"};
        headers[csrfHeader] = csrfToken;
        return fetch(url, {method: "POST", headers: headers, credentials: "same-origin", body: JSON.stringify(body)})
            .then(function (response) {
                return response.json().catch(function () { return {}; }).then(function (data) {
                    return {status: response.status, data: data};
                });
            });
    }

    function addMessage(role, text) {
        var div = document.createElement("div");
        div.className = "ab-chat-msg ab-chat-" + role;
        div.textContent = text;
        log.appendChild(div);
        log.scrollTop = log.scrollHeight;
        return div;
    }

    function addSlots(slots) {
        if (!slots || slots.length === 0) {
            return;
        }
        var list = document.createElement("ul");
        list.className = "ab-chat-slots list-unstyled";
        slots.forEach(function (slot) {
            var item = document.createElement("li");
            item.textContent = "Slot #" + slot.slotId + " · " + slot.service + " · " + slot.provider + " · " + slot.start;
            list.appendChild(item);
        });
        log.appendChild(list);
        log.scrollTop = log.scrollHeight;
    }

    function showError(message) {
        errorBox.textContent = message;
        errorBox.classList.toggle("d-none", !message);
    }

    function showPending(action) {
        pending = action;
        pendingCard.classList.toggle("d-none", !action);
        if (action) {
            document.getElementById("pending-summary").textContent = action.summary;
            document.getElementById("pending-expires").textContent = action.expiresAt.replace("T", " ").substring(0, 16);
        }
    }

    function busy(isBusy) {
        send.disabled = isBusy;
        input.disabled = isBusy;
        send.textContent = isBusy ? "Thinking…" : "Send";
    }

    function handleReply(result) {
        if (result.status >= 200 && result.status < 300 || result.status === 409) {
            conversationId = result.data.conversationId || conversationId;
            addMessage("assistant", result.data.reply);
            addSlots(result.data.suggestedSlots);
            showPending(result.data.pendingAction || null);
        } else {
            showError(result.data.message || "Something went wrong. Please try again.");
            if (result.status === 400 || result.status === 403) {
                showPending(null);
            }
        }
    }

    form.addEventListener("submit", function (event) {
        event.preventDefault();
        var text = input.value.trim();
        if (!text) {
            return;
        }
        showError("");
        addMessage("user", text);
        input.value = "";
        busy(true);
        var body = {message: text};
        if (conversationId) {
            body.conversationId = conversationId;
        }
        post("/api/ai/chat", body)
            .then(handleReply)
            .catch(function () { showError("The assistant could not be reached."); })
            .finally(function () { busy(false); input.focus(); });
    });

    function answerPending(confirm) {
        if (!pending) {
            return;
        }
        showError("");
        var buttons = pendingCard.querySelectorAll("button");
        buttons.forEach(function (button) { button.disabled = true; });
        post("/api/ai/chat/confirm", {conversationId: conversationId, token: pending.token, confirm: confirm})
            .then(handleReply)
            .catch(function () { showError("The confirmation could not be sent."); })
            .finally(function () { buttons.forEach(function (button) { button.disabled = false; }); });
    }

    document.getElementById("pending-confirm").addEventListener("click", function () { answerPending(true); });
    document.getElementById("pending-decline").addEventListener("click", function () { answerPending(false); });

    var faqForm = document.getElementById("faq-form");
    var faqAnswer = document.getElementById("faq-answer");
    faqForm.addEventListener("submit", function (event) {
        event.preventDefault();
        var question = document.getElementById("faq-input").value.trim();
        if (!question) {
            return;
        }
        var faqSend = document.getElementById("faq-send");
        faqSend.disabled = true;
        faqAnswer.textContent = "Looking it up…";
        post("/api/ai/faq", {question: question}).then(function (result) {
            faqAnswer.textContent = "";
            if (result.status !== 200) {
                var error = document.createElement("div");
                error.className = "alert alert-danger";
                error.textContent = result.data.message || "The knowledge base could not answer right now.";
                faqAnswer.appendChild(error);
                return;
            }
            var answer = document.createElement("p");
            answer.textContent = result.data.answer;
            faqAnswer.appendChild(answer);
            if (result.data.sources && result.data.sources.length) {
                var sources = document.createElement("p");
                sources.className = "ab-muted small mb-0";
                sources.textContent = "Sources: " + result.data.sources.map(function (source) {
                    return source.ref + " " + source.section;
                }).join("; ");
                faqAnswer.appendChild(sources);
            }
        }).catch(function () {
            faqAnswer.textContent = "The knowledge base could not be reached.";
        }).finally(function () { faqSend.disabled = false; });
    });

    fetch("/api/ai/status", {credentials: "same-origin", headers: {"Accept": "application/json"}})
        .then(function (response) { return response.json(); })
        .then(function (status) {
            if (!status.chatAvailable) {
                document.getElementById("ai-unavailable").classList.remove("d-none");
            }
        })
        .catch(function () { /* the page still works; errors show on send */ });
}());
