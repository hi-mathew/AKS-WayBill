package com.aks.waybill.ui;

/** Read-only waybill screen using the same form layout as New Waybill. */
public final class ViewWaybillView extends WaybillFormView {
    public ViewWaybillView(long waybillId, Runnable onBack) {
        super(Mode.VIEW, waybillId, () -> {}, onBack, () -> {});
    }
}
