package hudson.plugins.accurev.extensions.impl.SparseCheckout

import lib.FormTagLib

def f = namespace(FormTagLib)

f.entry(field: "paths", title: _("Paths")) {
    f.textarea()
}
