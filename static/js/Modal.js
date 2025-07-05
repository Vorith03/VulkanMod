class Modal {
    constructor(contentHtml = '', $trigger = null, $anotherTrigger = null) {
        this.contentHtml = contentHtml
        this.$trigger = $trigger;
        this.$anotherTrigger = $anotherTrigger;
        this.isOpen = false;

        this.$target = this._createModal(contentHtml);
        this.$backdrop = this.$target.find(".modal-backdrop");
        this.$closeBtn = this.$target.find("#close-modal");

        this.init();
    }

    _createModal(contentHtml) {
        const modal = $(`
            <div class="modal hidden">
                <div class="modal-backdrop"></div>
                <div class="modal-content">${contentHtml}</div>
            </div>
            `)

        $("body").append(modal)
        return modal
    }

    init() {
        this.$trigger.on("click", () => this.show("main trigger"));

        if (this.$anotherTrigger) {
            this.$anotherTrigger.on("click", () => this.show("secondary trigger"))
        }

        this.$target.on("click", e => {
            if ($(e.target).is(this.$target)) {
                this.close("click on itself (?)");
            }
        });

        this.$backdrop.on("click", () => this.close("clicking on backdrop"))

        $(document).on("keydown", e => {
            if (e.key === "Escape" && this.isOpen) {
                this.close("escape button");
            }
        });

        this.$closeBtn.on("click", () => {
            this.close("close button")
        })
    }

    show(reason = "unknown") {
        if (!this.isOpen) {
            console.log(`[DEBUG] Opening modal by ${reason}...`);
            this.$target.removeClass("hidden");
            this.$backdrop.removeClass("hidden")
            this.isOpen = true;
        } else {
            console.log("[DEBUG] Somehow the modal is opened, so we can't open it twice.")
        }
    }

    close(reason = "unknown") {
        if (this.isOpen) {
            console.log(`[DEBUG] Closing modal by ${reason}...`);
            this.$target.addClass("hidden");
            this.$backdrop.addClass("hidden")
            this.isOpen = false;
        } else {
            console.log(`[DEBUG] Somehow the modal is closed, so we can't close it twice. By ${reason}`)
        }
    }

    toggle() {
        this.isOpen ? this.close() : this.show();
    }
}