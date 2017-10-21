package hudson.plugins.accurev.extensions.impl.SnapshotCheckout

import lib.FormTagLib

def f = namespace(FormTagLib)

f.entry(field: "snapshotNameFormat", title: _("Snapshot Name Format"), help: "/plugin/accurev/help/project/snapshot-format.html") {
    f.textbox()
}
