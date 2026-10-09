// Small progressive enhancements for AutoBook pages. Every page still works without JavaScript.
document.addEventListener("DOMContentLoaded", () => {

    // Prevent double submissions (for example, double-clicking "Confirm booking").
    document.querySelectorAll("form[data-submit-once]").forEach((form) => {
        form.addEventListener("submit", () => {
            form.querySelectorAll("button[type=submit]").forEach((button) => {
                button.disabled = true;
                if (button.dataset.busyText) {
                    button.textContent = button.dataset.busyText;
                }
            });
        });
    });

    // Provider availability form: suggest an end time from the selected service's duration.
    const slotForm = document.querySelector("form[data-slot-form]");
    if (slotForm) {
        const service = slotForm.querySelector("#serviceId");
        const start = slotForm.querySelector("#startTime");
        const end = slotForm.querySelector("#endTime");

        const suggestEndTime = () => {
            const option = service.options[service.selectedIndex];
            const minutes = option ? parseInt(option.dataset.duration || "0", 10) : 0;
            if (!minutes || !start.value) {
                return;
            }
            const [hours, mins] = start.value.split(":").map(Number);
            const total = hours * 60 + mins + minutes;
            if (total >= 24 * 60) {
                return;
            }
            const pad = (value) => String(value).padStart(2, "0");
            end.value = `${pad(Math.floor(total / 60))}:${pad(total % 60)}`;
        };

        service.addEventListener("change", suggestEndTime);
        start.addEventListener("change", suggestEndTime);
    }
});
