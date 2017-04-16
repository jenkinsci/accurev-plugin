package hudson.plugins.accurev.extensions.impl.AccurevWorkspaceCheckout

import lib.FormTagLib

def f = namespace(FormTagLib)

f.entry(field: "workspace", title: _("Accurev workspace"), help: "/plugin/accurev/help/project/workspace.html") {
    f.textbox()
}
