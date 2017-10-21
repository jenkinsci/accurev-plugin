package hudson.plugins.accurev.extensions.impl.ReferenceTreeCheckout

import lib.FormTagLib

def f = namespace(FormTagLib)

f.entry(field: "referenceTree", title: _("Reference Tree"), help: "/plugin/accurev/help/project/reftree.html") {
    f.textbox()
}
f.entry(field: "cleanReferenceTree", title: _("Clean Reference Tree"), help: "/plugin/accurev/help/project/cleanreftree.html") {
    f.checkbox()
}

