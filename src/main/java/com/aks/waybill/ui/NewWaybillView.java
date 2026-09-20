package com.aks.waybill.ui;

import java.util.function.Consumer;

/** New waybill screen backed by the reusable waybill form. */
public final class NewWaybillView extends WaybillFormView {
    public NewWaybillView() {
        this(() -> {}, () -> {});
    }
    public NewWaybillView(Runnable onSavedView) {
        this(onSavedView, () -> {});
    }
    public NewWaybillView(Runnable onSavedView, Runnable onCreateNew) {
        this(onSavedView, onCreateNew, id -> {});
    }

    public NewWaybillView(Runnable onSavedView, Runnable onCreateNew, Consumer<Long> onViewSaved) {
        super(Mode.NEW, 0, onSavedView, () -> {}, onCreateNew, onViewSaved);
    }
}
