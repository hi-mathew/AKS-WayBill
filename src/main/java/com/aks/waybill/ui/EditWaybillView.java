package com.aks.waybill.ui;

/** Edit screen using the same form layout as New Waybill. */
public final class EditWaybillView extends WaybillFormView {
    public EditWaybillView(long waybillId, Runnable onSaved, Runnable onCancel) {
        super(Mode.EDIT, waybillId, onSaved, onCancel, () -> {});
    }
}
